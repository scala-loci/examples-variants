$(function() {
  "use strict"

  window.Benchmark = function(
      nameChanged, chatRequested, chatSelected, chatClosed, messageSent) {
    var self = this
    self.storeLog = false

    self.name = "Anonymous"

    var State = {
      Initial: 0,
      Waiting: 1,
      Requesting: 2,
      Configuring: 3,
      Pinging: 4,
      Ponging: 5
    }

    var state = State.Initial
    var totalMessagesPerIteration = 0
    var totalIterations = 0
    var warmupIterations = 0

    var iteration = 0
    var count = 0
    var pingMessage = true
    var pongMessage = false
    var time = 0

    var results = null

    self.updateUsers = function(users) {
      if (state == State.Initial) {
        if (users.length == 0) {
          state = State.Waiting
          ui.username.val("Ponging")
          ui.log("[Benchmark mode]")
          ui.log("Waiting for chat request ...")
        }
        else if (users.length == 1) {
          state = State.Requesting
          ui.username.val("Pinging")
          ui.log("[Benchmark mode]")
          ui.log("Requesting chat ...")
          chatRequested(users[0])
        }
        else
          ui.log("[Benchmark mode]: Two users already connected")
      }
    }

    self.updateChats = function(chats) {
      if (chats.length == 1) {
        if (state == State.Requesting) {
          state = State.Configuring
          ui.log("Please enter the message and iteration count in the message field")
          ui.message.val("[message count] [iteration count]")
          ui.message.prop("disabled", false)
          ui.send.prop("disabled", false)
          chatSelected(chats[0])
        }
        else if (state == State.Waiting) {
          state = State.Ponging
          ui.log("Running benchmark ...")
          chatSelected(chats[0])
        }
      }
    }

    self.updateMessages = function(messages) {
      if (messages instanceof Cons) {
        if (state == State.Pinging) {
          if (pingMessage = !pingMessage) {
            count++
            if (count >= totalMessagesPerIteration) {
              iteration++

              if (iteration < totalIterations) {
                if (iteration >= 0)
                  results[iteration] = ((1000 * performance.now()) | 0) - time

                count = 0
                setTimeout(function() { ping() }, 1)
              }
              else if (iteration == totalIterations) {
                var min = (Math.min.apply(null, results) / totalMessagesPerIteration) | 0
                var max = (Math.max.apply(null, results) / totalMessagesPerIteration) | 0
                var mean = results.reduce(function(a, b) { return a + b }) / results.length / totalMessagesPerIteration
                var variance = results.reduce(function(variance, element) {
                  var diff = element / totalMessagesPerIteration - mean
                  return variance + diff * diff
                }, 0) / (results.length - 1)
                var standardDeviation = Math.sqrt(variance)
                var standardErrorMean = standardDeviation / Math.sqrt(results.length)

                ui.log("MIN: " + min + "μs")
                ui.log("MAX: " + max + "μs")
                ui.log("AVG: " + (mean | 0) + "μs")
                ui.log("SEM: " + (standardErrorMean | 0) + "μs")
                ui.log("SD:  " + (standardDeviation | 0) + "μs")
              }
            }
          }
        }
        else if (state == State.Ponging) {
          if (pongMessage = !pongMessage)
            messageSent("pong for " + messages.head.content)
        }
      }
    }

    function ping() {
      time = (1000 * performance.now()) | 0
      for (var i = 0; i < totalMessagesPerIteration; i++)
        messageSent("ping " + i)
    }

    self.clearMessage = function() { }

    var ui = {
      username: $("#username"),
      chats: $("#chats"),
      users: $("#users"),
      nousers: $("#nousers"),
      chatlog: $("#chatlog"),
      message: $("#message"),
      send: $("#send"),
      log: function(message) {
        ui.chatlog.append($("<li/>").text(message))
      }
    }

    ui.message.attr("placeholder", "")
    ui.message.prop("disabled", true)
    ui.send.prop("disabled", true)
    ui.username.prop("disabled", true)

    ui.send.on("click", function() {
      var matched = ui.message.val().match(/^\s*(\d+)\s+(\d+)\s*$/)

      if (matched != null) {
        totalMessagesPerIteration = +matched[1]
        totalIterations = +matched[2]
        warmupIterations = Math.max(5, (5000 / totalMessagesPerIteration))
        iteration = -warmupIterations

        results = new Array(totalIterations)

        ui.message.val("")
        ui.message.prop("disabled", true)
        ui.send.prop("disabled", true)

        ui.log(
          totalIterations + " iterations with " +
          totalMessagesPerIteration + " messages each " +
          "(plus " + warmupIterations + " warm-up iterations)")

        ui.log("Running benchmark ...")

        state = State.Pinging

        setTimeout(function() { ping() }, 1)
      }
      else
        alert("Cannot parse message and iteration count")
    })

    ui.message.on("keyup", function(event) {
      if (event.keyCode == 13) {
        event.preventDefault()
        ui.send.trigger("click")
      }
    })
  }
})
