package chat

import util._

import rescala.default._
import upickle.default._

import scala.scalajs.js
import org.scalajs.dom
import scala.collection.mutable
import scala.concurrent.Future
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
      case message @ Users(_) =>
        users.set(message.users)
      case message @ Connect(_, _) =>
        chatConnecting(message)
    }
  }


  ui.name observe { name =>
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

  ui.chatRequested observe { user =>
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


  def userMessage(id: Int, message: PeerMessage) = message match {
    case Content(content) =>
      messageReceived.fire(id -> content)
    case ChangeName(name) =>
      chats.readValueOnce find { _.id == id } foreach { _.name.set(name) }
  }

  def messageLog(id: Int) = {
    val messages =
      (messageSent collect { case (`id`, content) => Message(content, own = true) }) ||
      (messageReceived collect { case (`id`, content) => Message(content, own = false) })

    if (ui.storeLog) messages.list() else messages.latestOption() map { _.toSeq }
  }

  def unreadMessageCount(id: Int) = {
    Events.foldAll(0)(count => Seq(
      selectedChatId.changed act { selected =>
        if (selected == Some(id)) 0 else count
      },
      (messageReceived collect { case (`id`, _) => selectedChatId() }) act { selected =>
        if (selected != Some(id)) count + 1 else count
      }
    ))
  }

  val selectedChatId = {
    Events.foldAll(Option.empty[Int])(selected => Seq(
      ui.chatRequested act { requested => Some(requested.id) },
      ui.chatSelected act { selected => Some(selected.id) },
      ui.chatClosed act { closed =>
        if (Some(closed.id) == selected) None else selected
      }
    ))
  }

  val messageSent = (ui.messageSent map { message => selectedChatId() map { _ -> message } }).flatten

  messageSent observe { case (id, message) => sendUser(id, Content(message)) }

  val messageReceived = Evt[(Int, String)]()

  val chats = Var(Seq.empty[ChatLog])

  def userConnected(id: Int) = {
    val joined = ChatLog(id, Var(""), unreadMessageCount(id), messageLog(id))
    chats transform { _ :+ joined }

    sendUser(id, ChangeName(ui.name.readValueOnce))
  }

  def userDisconnected(id: Int) = Future {
    chats transform { _ filterNot { _.id == id } }
  }


  val users = Var.empty[Seq[User]]

  ui.users = users

  ui.chats = Signal.dynamic {
    chats() map { case ChatLog(id, name, unread, _) =>
      Chat(id, name(), unread(), selectedChatId() == Some(id))
    } sortBy { _.name }
  }

  ui.messages = Signal.dynamic {
    selectedChatId() flatMap { id =>
      chats() collectFirst {
        case ChatLog(`id`, _, _, log) => log()
      }
    } getOrElse Seq.empty
  }

  ui.clearMessage = (ui.messageSent filter { _ => selectedChatId().nonEmpty }).dropParam

  ui.chatClosed observe { chat => disconnectUser(chat.id) }
}
