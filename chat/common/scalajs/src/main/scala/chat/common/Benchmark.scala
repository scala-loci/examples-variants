package chat
package common

import util._

import scala.scalajs.js.timers
import scala.scalajs.js.Dynamic
import scala.scalajs.js.Dynamic.global

class Benchmark(
    chatRequested: User => Unit,
    chatSelected: Chat => Unit,
    messageSent: String => Unit) {
  sealed trait State
  case object Initial extends State
  case object Waiting extends State
  case object Requesting extends State
  case object Configuring extends State
  case object Pinging extends State
  case object Ponging extends State

  private[this] var state: State = Initial
  private[this] var totalMessagesPerIteration: Int = _
  private[this] var totalIterations: Int = _
  private[this] var warmupIterations: Int = _

  private[this] var iteration: Int = _
  private[this] var count: Int = _
  private[this] var pingMessage: Boolean = true
  private[this] var pongMessage: Boolean = false
  private[this] var time: Long = _

  private[this] var results: Array[Long] = _

  private object ui {
    var username: Dynamic = _
    var chats: Dynamic = _
    var users: Dynamic = _
    var nousers: Dynamic = _
    var chatlog: Dynamic = _
    var message: Dynamic = _
    var send: Dynamic = _
    def log(message: String) =
      chatlog.append($("<li/>").text(message))
  }

  private val $ = global.$

  def updateUsers(users: Seq[User]): Unit = $ { () =>
    if (state == Initial) {
      if (users.size == 0) {
        state = Waiting
        ui.username.`val`("Ponging")
        ui.log("[Benchmark mode]")
        ui.log("Waiting for chat request ...")
      }
      else if (users.size == 1) {
        state = Requesting
        ui.username.`val`("Pinging")
        ui.log("[Benchmark mode]")
        ui.log("Requesting chat ...")
        chatRequested(users.head)
      }
      else
        ui.log("[Benchmark mode]: Two users already connected")
    }
  }

  def updateChats(chats: Seq[Chat]): Unit = $ { () =>
    if (chats.size == 1) {
      if (state == Requesting) {
        state = Configuring
        ui.log("Please enter the message and iteration count in the message field")
        ui.message.`val`("[message count] [iteration count]")
        ui.message.prop("disabled", false)
        ui.send.prop("disabled", false)
        chatSelected(chats.head)
      }
      else if (state == Waiting) {
        state = Ponging
        ui.log("Running benchmark ...")
        chatSelected(chats.head)
      }
    }
  }

  def updateMessages(messages: Seq[Message]): Unit = $ { () =>
    if (messages.nonEmpty) {
      if (state == Pinging) {
        pingMessage = !pingMessage
        if (pingMessage) {
          count += 1
          if (count >= totalMessagesPerIteration) {
            iteration += 1

            if (iteration < totalIterations) {
              if (iteration >= 0)
                results(iteration) = System.nanoTime - time

              count = 0
              timers.setTimeout(1) { ping() }
            }
            else if (iteration == totalIterations) {
              val min = results.min / totalMessagesPerIteration / 1000
              val max = results.max / totalMessagesPerIteration / 1000
              val mean = results.sum.toDouble / results.size / totalMessagesPerIteration / 1000
              val variance = (results.foldLeft(0.0) { (variance, element) =>
                val diff = element / totalMessagesPerIteration / 1000 - mean
                variance + diff * diff
              }) / (results.size - 1)
              val standardDeviation = math.sqrt(variance)
              val standardErrorMean = standardDeviation / math.sqrt(results.size)

              ui.log(s"MIN: ${min}μs")
              ui.log(s"MAX: ${max}μs")
              ui.log(s"AVG: ${mean.toLong}μs")
              ui.log(s"SEM: ${standardErrorMean.toLong}μs")
              ui.log(s"SD:  ${standardDeviation.toLong}μs")
            }
          }
        }
      }
      else if (state == Ponging) {
        pongMessage = !pongMessage
        if (pongMessage)
          messageSent(s"pong for ${messages.head.content}")
      }
    }
  }

  private def ping(): Unit = {
    time = System.nanoTime
    for (i <- 1 to totalMessagesPerIteration)
      messageSent(s"ping $i")
  }

  $ { () =>
    ui.username = global $ "#username"
    ui.chats = global $ "#chats"
    ui.users = global $ "#users"
    ui.nousers = global $ "#nousers"
    ui.chatlog = global $ "#chatlog"
    ui.message = global $ "#message"
    ui.send = global $ "#send"

    ui.username.`val`("")
    ui.message.`val`("")
    ui.message.attr("placeholder", "")
    ui.message.prop("disabled", true)
    ui.send.prop("disabled", true)
    ui.username.prop("disabled", true)

    ui.send.on("click", { () =>
      val userInput = """\s*(\d+)\s+(\d+)\s*""".r

      ui.message.`val`().toString match {
        case userInput(messages, iterations) =>
          totalMessagesPerIteration = messages.toInt
          totalIterations = iterations.toInt
          warmupIterations = math.max(5, (5000 / totalMessagesPerIteration))
          iteration = -warmupIterations

          results = new Array(totalIterations)

          ui.message.`val`("")
          ui.message.prop("disabled", true)
          ui.send.prop("disabled", true)

          ui.log(
            s"$totalIterations iterations with " +
            s"$totalMessagesPerIteration messages each " +
            s"(plus $warmupIterations warm-up iterations)")

          ui.log("Running benchmark ...")

          state = Pinging

          timers.setTimeout(1) { ping() }

        case _ =>
          global.alert("Cannot parse message and iteration count")
      }
    })

    ui.message.on("keyup", { event: Dynamic =>
      if (event.keyCode.asInstanceOf[Int] == 13) {
        event.preventDefault()
        ui.send trigger "click"
      }
    })
  }
}
