$(function() {
  "use strict"

  function changeName(name) { return { $type: "ChangeName", name: name } }
  function connect(id, sdp, ice) { return { $type: "Connect", id: id, sdp: sdp, ice: ice } }

  var socket = new WebSocket("ws://localhost:8080")

  var chatIndex = new Map()

  var channelLabel = "webrtc-chat-channel"


  var ui = location.search == "?benchmark" ?
    new Benchmark(nameChanged, chatRequested, chatSelected, chatClosed, messageSent) :
    new UI(nameChanged, chatRequested, chatSelected, chatClosed, messageSent)


  function sendServer(message) {
    if (socket.readyState == WebSocket.OPEN)
      socket.send(JSON.stringify(message)) // #REMOTE-SEND
    else
      socket.addEventListener("open", function() { sendServer(message) })
  }

  socket.onmessage = function(event) { // #REMOTE-RECV #CB
    var message = JSON.parse(event.data)
    if (Array.isArray(message))
      ui.updateUsers(message)
    else
      chatConnecting(message)
  }


  nameChanged(ui.name)

  function nameChanged(name) { // #CB
    sendServer(changeName(name))

    chatIndex.forEach(function(peerConnectionAndChannel, id) {
      sendUser(id, { name: name })
    })
  }


  function chatRequested(user) { // #CB
    if (!chatIndex.has(user.id)) {
      var peerConnection = setupRTCPeerConnection(user.id)

      handleRTCDataChannel(user.id, peerConnection.createDataChannel(channelLabel))

      peerConnection.createOffer().then(function(sdp) {
        peerConnection.setLocalDescription(sdp).then(function() {
          sendServer(connect(user.id, JSON.stringify(sdp), null))
        })
      })
    }

    selectedChatId.set([user.id]) // #IMP-STATE
  }

  function chatConnecting(connecting) {
    var peerConnection, peerConnectionAndChannel = chatIndex.get(connecting.id)

    var sdp = connecting.sdp && new RTCSessionDescription(JSON.parse(connecting.sdp))
    var ice = connecting.ice && new RTCIceCandidate(JSON.parse(connecting.ice))

    if (!peerConnectionAndChannel) {
      peerConnection = setupRTCPeerConnection(connecting.id)

      peerConnection.ondatachannel = function(event) { // #CB
        if (event.channel.label == channelLabel)
          handleRTCDataChannel(connecting.id, event.channel)
      }

      if (sdp)
        peerConnection.setRemoteDescription(sdp).then(function() {
          peerConnection.createAnswer().then(function(sdp) {
            peerConnection.setLocalDescription(sdp).then(function() {
              sendServer(connect(connecting.id, JSON.stringify(sdp), null))
            })
          })
        })
    }
    else {
      peerConnection = peerConnectionAndChannel.connection

      if (sdp)
        peerConnection.setRemoteDescription(sdp)
    }

    if (ice)
      peerConnection.addIceCandidate(ice)
  }

  function setupRTCPeerConnection(id) {
    var peerConnection = new RTCPeerConnection({ iceServers: [] })

    chatIndex.set(id, { connection: peerConnection, channel: null }) // #IMP-STATE

    peerConnection.onicecandidate = function(event) { // #CB
      if (event.candidate != null)
        sendServer(connect(id, null, JSON.stringify(event.candidate)))
    }

    return peerConnection
  }

  function handleRTCDataChannel(id, channel) {
    channel.onmessage = function(event) { // #REMOTE-RECV #CB
      userMessage(id, JSON.parse(event.data))
    }

    channel.onclose = function() { disconnect() } // #CB

    channel.onerror = function() { disconnect() } // #CB

    if (channel.readyState == "connecting")
      channel.onopen = function() { connect() } // #CB
    else if (channel.readyState == "open")
      connect()

    function connect() {
      chatIndex.get(id).channel = channel
      userConnected(id)
    }

    function disconnect() {
      if (chatIndex.has(id)) {
        chatIndex.delete(id) // #IMP-STATE
        userDisconnected(id)
      }
    }
  }

  function sendUser(id, message) {
    var peerConnectionAndChannel = chatIndex.get(id)
    if (peerConnectionAndChannel)
      peerConnectionAndChannel.channel.send(JSON.stringify(message)) // #REMOTE-SEND
  }

  function disconnectUser(id) {
    var peerConnectionAndChannel = chatIndex.get(id)
    if (peerConnectionAndChannel) {
      chatIndex.delete(id) // #IMP-STATE
      peerConnectionAndChannel.channel.close()
      userDisconnected(id)
    }
  }


  var messageSent = new Observable([0, ""])

  var messageReceived = new Observable([0, ""])

  function userMessage(id, message) {
    if (message.content) {
      messageReceived.set([id, message.content]) // #IMP-STATE
    }
    else if (message.name) {
      var chat = chats.get().find(function(chat) { return chat.id == id })
      if (chat)
        chat.name.set(message.name) // #IMP-STATE
    }
  }

  function sendMessage(message) {
    if (selectedChatId.get().length) {
      messageSent.set([selectedChatId.get()[0], message]) // #IMP-STATE
      sendUser(selectedChatId.get()[0], { content: message })
    }
  }

  function messageLog(id) {
    var messageLog = new Observable(nil)

    messageSent.addObserver(function(chatIdMessage) {
      if (chatIdMessage[0] == id)
        messageLog.set(cons( // #IMP-STATE
          createMessage(chatIdMessage[1], true), ui.storeLog ? messageLog.get() : nil))
    })

    messageReceived.addObserver(function(chatIdMessage) {
      if (chatIdMessage[0] == id)
        messageLog.set(cons( // #IMP-STATE
          createMessage(chatIdMessage[1], false), ui.storeLog ? messageLog.get() : nil))
    })

    return messageLog
  }

  function unreadMessageCount(id) {
    var unreadMessageCount = new Observable(0)

    selectedChatId.addObserver(function() { // #CB
      if (selectedChatId.get().length && selectedChatId.get()[0] == id)
        unreadMessageCount.set(0) // #IMP-STATE
    })

    messageReceived.addObserver(function(chatIdMessage) { // #CB
      if ((!selectedChatId.get().length || selectedChatId.get()[0] != id) &&
          chatIdMessage[0] == id)
        unreadMessageCount.set(unreadMessageCount.get() + 1) // #IMP-STATE
    })

    return unreadMessageCount
  }


  var selectedChatId = new Observable([])

  var chats = new Observable(nil)

  function userConnected(id) {
    chats.set(
      cons(
        createChatLog(id, new Observable(""), unreadMessageCount(id), messageLog(id)),
        chats.get()))

    sendUser(id, { name: ui.name })
  }

  function userDisconnected(id) {
    chats.set(chats.get().filter(function(chat) {
      return chat.id != id
    }))

    if (selectedChatId.get().length && selectedChatId.get()[0] == id)
      selectedChatId.set([])
  }


  var updatingChats = new Set()

  function updateChats() {
    var updatedChats =
      chats.get().toArray().map(function(chat) {
        if (!updatingChats.has(chat)) {
          updatingChats.add(chat)
          chat.name.addObserver(updateChats)
          chat.unread.addObserver(updateChats)
        }
        return createChat(chat.id, chat.name.get(), chat.unread.get(),
          selectedChatId.get().length && selectedChatId.get()[0] == chat.id)
      })

    updatedChats.sort(function(a, b) {
      return a.name.localeCompare(b.name)
    })

    ui.updateChats(updatedChats)
  }

  var updatingMessages = new Set

  function updateMessages() {
    if (selectedChatId.get().length) {
      var id = selectedChatId.get()[0]
      var chat = chats.get().find(function(chat) { return chat.id == id })
      if (chat) {
        if (!updatingMessages.has(chat)) {
          updatingMessages.add(chat)
          chat.log.addObserver(updateMessages)
        }
        ui.updateMessages(chat.log.get())
      }
      else
        ui.updateMessages(nil)
    }
    else
      ui.updateMessages(nil)
  }

  selectedChatId.addObserver(function() {
    updateChats()
    updateMessages()
  })

  chats.addObserver(function() {
    updateChats()
    updateMessages()
  })

  function messageSent(message) {
    sendMessage(message)

    if (selectedChatId.get().length)
      ui.clearMessage()
  }

  function chatSelected(chat) { selectedChatId.set([chat.id]) }

  function chatClosed(chat) { disconnectUser(chat.id) }
})
