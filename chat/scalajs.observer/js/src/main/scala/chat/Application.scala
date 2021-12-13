package chat

import util._

import upickle.default._

import scala.scalajs.js
import org.scalajs.dom

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global

class Application(ui: FrontEnd) {
  class ChatIndex {
    val connections = mutable.Map.empty[Int, (dom.RTCPeerConnection, Option[dom.RTCDataChannel])]

    def getConnection(id: Int) = connections get id map {
      case (connection, _) => connection
    }

    def getChannel(id: Int) = connections get id flatMap {
      case (_, channel) => channel
    }

    def insert(idConnection: (Int, dom.RTCPeerConnection)) = {
      val (id, connection) = idConnection
      connections += id -> (connection -> None)
    }

    def insert(idChannel: (Int, dom.RTCDataChannel)) = {
      val (id, channel) = idChannel
      connections get id foreach { case (connection, _) =>
        connections += id -> (connection -> Some(channel))
      }
    }

    def remove(id: Int) = connections -= id

    def contains(id: Int) = connections contains id

    def foreach(f: Int => Unit) = connections.keysIterator foreach f
  }

  val chatIndex = new ChatIndex

  val channelLabel = "webrtc-chat-channel"

  val socket = new dom.WebSocket("ws://localhost:8080")


  def sendServer(message: ServerMessage): Unit = {
    if (socket.readyState == dom.WebSocket.OPEN)
      socket.send(write(message))
    else
      socket.addEventListener("open", { _: dom.Event => sendServer(message) })
  }

  socket.onmessage = { event: dom.MessageEvent =>
    read[ClientMessage](event.data.toString) match {
      case Users(users) =>
        ui.updateUsers(users)
      case connect @ Connect(_, _) =>
        chatConnecting(connect)
    }
  }


  ui.name addObserver nameChanged
  nameChanged(ui.name.get)

  def nameChanged(name: String) = {
    sendServer(ChangeName(name))
    chatIndex foreach { sendUser(_, ChangeName(name)) }
  }


  object RTCSessionDescription {
    def toTuple(value: dom.RTCSessionDescription) =
      (value.`type`.asInstanceOf[String], value.sdp)

    def fromTuple(value: (String, String)) = {
      val (descType, descSdp) = value
      new dom.RTCSessionDescription(
        new dom.RTCSessionDescriptionInit {
          `type` = descType.asInstanceOf[dom.RTCSdpType]
          sdp = descSdp
        })
    }
  }

  object RTCIceCandidate {
    def toTuple(value: dom.RTCIceCandidate) =
      (value.candidate, value.sdpMid, value.sdpMLineIndex)

    def fromTuple(value: (String, String, Double)) = {
      val (iceCandidate, iceSdpMid, iceSdpMLineIndex) = value
      new dom.RTCIceCandidate(
        new dom.RTCIceCandidateInit {
          candidate = iceCandidate
          sdpMid = iceSdpMid
          sdpMLineIndex = iceSdpMLineIndex
        })
    }
  }

  ui.chatRequested addObserver { user =>
    if (!(chatIndex contains user.id)) {
      val peerConnection = setupRTCPeerConnection(user.id)

      handleRTCDataChannel(
        user.id,
        peerConnection.createDataChannel(channelLabel, new dom.RTCDataChannelInit { }))

      peerConnection.createOffer().toFuture foreach { sdp =>
        peerConnection.setLocalDescription(sdp).toFuture foreach { _ =>
          sendServer(Connect(user.id, Left(RTCSessionDescription.toTuple(sdp))))
        }
      }
    }

    selectedChatId.set(Some(user.id))
  }

  def chatConnecting(connecting: Connect) = {
    val sdp = connecting.session.left.toOption map RTCSessionDescription.fromTuple
    val ice = connecting.session.toOption map RTCIceCandidate.fromTuple

    val peerConnection =
      chatIndex getConnection connecting.id map { peerConnection =>
        sdp foreach peerConnection.setRemoteDescription
        peerConnection
      } getOrElse {
        val peerConnection = setupRTCPeerConnection(connecting.id)

        peerConnection.ondatachannel = { event: dom.RTCDataChannelEvent =>
          if (event.channel.label == channelLabel)
            handleRTCDataChannel(connecting.id, event.channel)
        }

        sdp foreach { sdp =>
          peerConnection.setRemoteDescription(sdp).toFuture foreach { _ =>
            peerConnection.createAnswer().toFuture foreach { sdp =>
              peerConnection.setLocalDescription(sdp).toFuture foreach { _ =>
                sendServer(Connect(connecting.id, Left(RTCSessionDescription.toTuple(sdp))))
              }
            }
          }
        }

        peerConnection
      }

    ice foreach peerConnection.addIceCandidate
  }

  def setupRTCPeerConnection(id: Int) = {
    val peerConnection =
      new dom.RTCPeerConnection(new dom.RTCConfiguration { iceServers = js.Array[dom.RTCIceServer]() })

    chatIndex.insert(id -> peerConnection)

    peerConnection.onicecandidate = { event: dom.RTCPeerConnectionIceEvent =>
      if (event.candidate != null)
        sendServer(Connect(id, Right(RTCIceCandidate.toTuple(event.candidate))))
    }

    peerConnection
  }

  def handleRTCDataChannel(id: Int, channel: dom.RTCDataChannel) = {
    channel.onmessage = { event: dom.MessageEvent =>
      userMessage(id, read[PeerMessage](event.data.toString))
    }

    channel.onclose = { event: dom.Event => disconnect() }

    channel.onerror = { event: dom.Event => disconnect() }

    if (channel.readyState == dom.RTCDataChannelState.connecting)
      channel.onopen = { _: dom.Event => connect() }
    else if (channel.readyState == dom.RTCDataChannelState.open)
      connect()

    def connect() = {
      chatIndex.insert(id -> channel)
      userConnected(id)
    }

    def disconnect() = {
      if (chatIndex contains id) {
        chatIndex.remove(id)
        userDisconnected(id)
      }
    }
  }

  def sendUser(id: Int, message: PeerMessage) =
    chatIndex getChannel id foreach { _ send write(message) }

  def disconnectUser(id: Int) = chatIndex getChannel id foreach { channel =>
    chatIndex.remove(id)
    channel.close()
    userDisconnected(id)
  }


  val messageSent = Observable(0 -> "")

  val messageReceived = Observable(0 -> "")

  def userMessage(id: Int, message: PeerMessage) = message match {
    case Content(content) =>
      messageReceived.set(id -> content)
    case ChangeName(name) =>
      chats.get find { _.id == id } foreach { _.name.set(name) }
  }

  def sendMessage(message: String) = selectedChatId.get foreach { selectedChatId =>
    messageSent.set(selectedChatId -> message)
    sendUser(selectedChatId, Content(message))
  }

  def messageLog(id: Int) = {
    val messageLog = Observable(Seq.empty[Message])

    messageSent addObserver { case (chatId, message) =>
      if (chatId == id)
        messageLog.set(
          Message(message, own = true) +: (if (ui.storeLog) messageLog.get else Nil))
    }

    messageReceived addObserver { case (chatId, message) =>
      if (chatId == id)
        messageLog.set(
          Message(message, own = false) +: (if (ui.storeLog) messageLog.get else Nil))
    }

    messageLog
  }

  def unreadMessageCount(id: Int) = {
    val unreadMessageCount = Observable(0)

    selectedChatId addObserver { _ =>
      if (selectedChatId.get == Some(id))
        unreadMessageCount.set(0)
    }

    messageReceived addObserver { case (chatId, _) =>
      if (selectedChatId.get != Some(id) && chatId == id)
        unreadMessageCount.set(unreadMessageCount.get + 1)
    }

    unreadMessageCount
  }


  val selectedChatId = Observable(Option.empty[Int])

  val chats = Observable(Seq.empty[ChatLog])

  def userConnected(id: Int) = {
    val joined = ChatLog(id, Observable(""), unreadMessageCount(id), messageLog(id))
    chats.set(chats.get :+ joined)

    sendUser(id, ChangeName(ui.name.get))
  }

  def userDisconnected(id: Int) = {
    chats.set(chats.get filterNot { _.id == id })

    if (selectedChatId.get == Some(id))
      selectedChatId.set(None)
  }


  val updatingChats = mutable.Set.empty[ChatLog]

  def updateChats(): Unit = {
    val updatedChats =
      chats.get map { case chat @ ChatLog(id, name, unread, _) =>
        if (!(updatingChats contains chat)) {
          updatingChats += chat
          name addObserver { _ => updateChats() }
          unread addObserver { _ => updateChats() }
        }
        Chat(id, name.get, unread.get, selectedChatId.get == Some(id))
      } sortBy { _.name }

    ui.updateChats(updatedChats)
  }

  val updatingMessages = mutable.Set.empty[ChatLog]

  def updateMessages(): Unit = {
    val updatedMessages =
      selectedChatId.get flatMap { id =>
        chats.get collectFirst { case chat @ ChatLog(`id`, _, _, log) =>
          if (!(updatingMessages contains chat)) {
            updatingMessages += chat
            log addObserver { _ => updateMessages() }
          }
          log.get
        }
      } getOrElse Seq.empty

    ui.updateMessages(updatedMessages)
  }

  selectedChatId addObserver { _ =>
    updateChats()
    updateMessages()
  }

  chats addObserver { _ =>
    updateChats()
    updateMessages()
  }

  ui.messageSent addObserver { message =>
    sendMessage(message)

    if (selectedChatId.get.nonEmpty)
      ui.clearMessage()
  }

  ui.chatSelected addObserver { chat => selectedChatId.set(Some(chat.id)) }

  ui.chatClosed addObserver { chat => disconnectUser(chat.id) }
}
