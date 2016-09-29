$(function() {
  "use strict"

  function changeName(name) { return { $type: "ChangeName", name: name } }
  function connect(id, sdp, ice) { return { $type: "Connect", id: id, sdp: sdp, ice: ice } }

  var socket = new WebSocket("ws://localhost:8080")

  var chatIndex = new Map

  var channelLabel = "webrtc-chat-channel"


  var ui = new UI(
    nameChanged, chatRequested, chatSelected, chatClosed, messageSent)


  function sendServer(message) {
    if (socket.readyState == WebSocket.OPEN)
      socket.send(JSON.stringify(message))
    else
      socket.addEventListener("open", function() { sendServer(message) })
  }

  socket.onmessage = function(event) {
    var message = JSON.parse(event.data)
    if (Array.isArray(message))
      ui.setUsers(message)
    else
      chatConnecting(message)
  }


  nameChanged(ui.name)

  function nameChanged(name) {
    sendServer(changeName(name))

    chatIndex.forEach(function(peerConnectionAndChannel, id) {
      sendUser(id, { name: name })
    })
  }


  function chatRequested(user) {
    if (!chatIndex.has(user.id)) {
      var peerConnection = setupRTCPeerConnection(user.id)

      handleRTCDataChannel(user.id, peerConnection.createDataChannel(channelLabel))

      peerConnection.createOffer().then(function(sdp) {
        peerConnection.setLocalDescription(sdp).then(function() {
          sendServer(connect(user.id, JSON.stringify(sdp), null))
        })
      })
    }

    selectChat([user.id])
  }

  function chatConnecting(connecting) {
    var peerConnection, peerConnectionAndChannel = chatIndex.get(connecting.id)

    var sdp = connecting.sdp && new RTCSessionDescription(JSON.parse(connecting.sdp))
    var ice = connecting.ice && new RTCIceCandidate(JSON.parse(connecting.ice))

    if (!peerConnectionAndChannel) {
      peerConnection = setupRTCPeerConnection(connecting.id)

      peerConnection.ondatachannel = function(event) {
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

    chatIndex.set(id, { connection: peerConnection, channel: null })

    peerConnection.onicecandidate = function(event) {
      if(event.candidate != null)
        sendServer(connect(id, null, JSON.stringify(event.candidate)))
    }

    return peerConnection
  }

  function handleRTCDataChannel(id, channel) {
    channel.onmessage = function(event) {
      userMessage(id, JSON.parse(event.data))
    }

    channel.onclose = function() { disconnect() }

    channel.onerror = function() { disconnect() }

    if (channel.readyState == "connecting") {
      channel.onopen = function() { connect() }
    }
    else if (channel.readyState == "open") {
      connect()
    }

    function connect() {
      chatIndex.get(id).channel = channel
      userConnected(id)
    }

    function disconnect() {
      if (chatIndex.has(id)) {
        chatIndex.delete(id)
        userDisconnected(id)
      }
    }
  }

  function sendUser(id, message) {
    var peerConnectionAndChannel = chatIndex.get(id)
    if (peerConnectionAndChannel)
      peerConnectionAndChannel.channel.send(JSON.stringify(message))
  }

  function disconnectUser(id) {
    var peerConnectionAndChannel = chatIndex.get(id)
    if (peerConnectionAndChannel) {
      chatIndex.delete(id)
      peerConnectionAndChannel.channel.close()
      userDisconnected(id)
    }
  }


  function userMessage(id, message) {
    var chat = findChat(id)
    if (chat) {
      if (message.message) {
        chat.log.push(createMessage(message.message, false))
        if (id != selectedChatId[0]) {
          chat.unread++
          updateChatsUI()
        }
        else
          updateMessagesUI()
      }
      else if (message.name) {
        chat.name = message.name
        updateChatsUI()
      }
    }
  }


  var chats = []

  function findChat(id) {
    for (var i = 0, l = chats.length; i < l; ++i)
      if (chats[i].id == id)
        return chats[i]
    return null
  }

  function userConnected(id) {
    sendUser(id, { name: ui.name })

    chats.push(createChatLog(id, "", 0, []))

    chats.sort(function(a, b) {
      a.name.localeCompare(b.name)
    })

    updateChatsUI()
  }

  function userDisconnected(id) {
    chats = chats.filter(function(chatLog) {
      return chatLog.id != id
    })

    if (selectedChatId[0] == id)
      selectChat([])

    updateChatsUI()
  }

  function updateChatsUI() {
    ui.setChats(chats.map(function(chatLog) {
      return createChat(chatLog.id, chatLog.name, chatLog.unread,
        selectedChatId[0] == chatLog.id)
    }))
  }


  function chatClosed(chat) {
    disconnectUser(chat.id)
    selectChat([])
  }

  function messageSent(message) {
    var chat = selectedChatId.length && findChat(selectedChatId[0])
    if (chat) {
      sendUser(selectedChatId[0], { message: message })

      chat.log.push(createMessage(message, true))

      ui.clearMessage()
      updateMessagesUI()
    }
  }


  var selectedChatId = []

  function selectChat(id) {
    if (selectedChatId[0] != id[0]) {
      var chat = id.length && findChat(id[0])
      if (chat)
        chat.unread = 0

      selectedChatId = id
      updateChatsUI()
      updateMessagesUI()
    }
  }

  function chatSelected(chat) {
    selectChat([chat.id])
  }

  function updateMessagesUI() {
    var messages = []
    var chat = selectedChatId.length && findChat(selectedChatId[0])
    if (chat)
      messages = chat.log
    ui.setMessages(messages)
  }
})
