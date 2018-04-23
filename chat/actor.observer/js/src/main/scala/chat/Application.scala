package chat

import util._

import akka.actor._

import org.scalajs.dom.experimental.webrtc._

import scala.collection.mutable.Map
import scala.collection.mutable.Set

class Application(ui: FrontEnd) extends Actor {
  class ChatIndex {
    val connections = Map.empty[Int, ActorRef]

    def getActorRef(id: Int) = connections get id

    def insert(idActorRef: (Int, ActorRef)) = connections += idActorRef

    def remove(id: Int) = connections -= id

    def contains(id: Int) = connections contains id

    def foreach(f: Int => Unit) = connections.keysIterator foreach f
  }

  val chatIndex = new ChatIndex

  val channelLabel = "webrtc-chat-channel"

  val server = context actorOf Props(
    new WebSocketRemoteActor(self, "ws://localhost:8080"))


  def receive = {
    case WebRTCRemoteActor.Session(id, sdp) =>
      server ! Connect(id, Left(RTCSessionDescription toTuple sdp))

    case WebRTCRemoteActor.Candidate(id, candidate) =>
      server ! Connect(id, Right(RTCIceCandidate toTuple candidate))

    case WebRTCRemoteActor.UserConnected(id) =>
      userConnected(id)

    case WebRTCRemoteActor.UserDisconnected(id) =>
      if (chatIndex contains id) {
        chatIndex remove id
        userDisconnected(id)
      }

    case WebRTCRemoteActor.UserMessage(id, message) =>
      userMessage(id, message)

    case WebSocketRemoteActor.RegistryMessage(Users(users)) =>
      ui updateUsers users

    case WebSocketRemoteActor.RegistryMessage(connect @ Connect(_, _)) =>
      chatConnecting(connect)
  }


  ui.name addObserver nameChanged
  nameChanged(ui.name.get)

  def nameChanged(name: String) = {
    server ! ChangeName(name)
    chatIndex foreach {
      chatIndex getActorRef _ foreach { _ ! ChangeName(name) }
    }
  }


  object RTCSessionDescription {
    def toTuple(value: RTCSessionDescription) =
      (value.`type`.asInstanceOf[String], value.sdp)

    def fromTuple(value: (String, String)) = {
      val (descType, sdp) = value
      new RTCSessionDescription(
        RTCSessionDescriptionInit(descType.asInstanceOf[RTCSdpType], sdp))
    }
  }

  object RTCIceCandidate {
    def toTuple(value: RTCIceCandidate) =
      (value.candidate, value.sdpMid, value.sdpMLineIndex)

    def fromTuple(value: (String, String, Double)) = {
      val (candidate, sdpMid, sdpMLineIndex) = value
      new RTCIceCandidate(
        RTCIceCandidateInit(candidate, sdpMid, sdpMLineIndex))
    }
  }

  ui.chatRequested addObserver { user =>
    if (!(chatIndex contains user.id)) {
      val userActor = context actorOf Props(
        new WebRTCRemoteActor(self, user.id, channelLabel, createOffer = true))
      chatIndex insert user.id -> userActor
    }

    selectedChatId set Some(user.id)
  }

  def chatConnecting(connecting: Connect) = {
    val sdp = connecting.session.left.toOption map RTCSessionDescription.fromTuple
    val ice = connecting.session.right.toOption map RTCIceCandidate.fromTuple

    val user =
      chatIndex getActorRef connecting.id map { user =>
        sdp foreach { sdp =>
          user ! WebRTCRemoteActor.ApplySession(sdp, createAnswer = false)
        }
        user
      } getOrElse {
        val user = context actorOf Props(
          new WebRTCRemoteActor(self, connecting.id, channelLabel, createOffer = false))
        chatIndex insert connecting.id -> user

        sdp foreach { sdp =>
          user ! WebRTCRemoteActor.ApplySession(sdp, createAnswer = true)
        }
        user
      }

    ice foreach { candidate =>
      user ! WebRTCRemoteActor.ApplyCandidate(candidate)
    }
  }

  def disconnectUser(id: Int) = chatIndex getActorRef id foreach { user =>
    chatIndex remove id
    user ! WebRTCRemoteActor.Close
    userDisconnected(id)
  }


  val messageSent = Observable((0, ""))

  val messageReceived = Observable((0, ""))

  def userMessage(id: Int, message: PeerMessage) = message match {
    case Content(content) =>
      messageReceived set ((id, content))
    case ChangeName(name) =>
      chats.get find { _.id == id } foreach { _.name set name }
  }

  def sendMessage(message: String) = selectedChatId.get foreach { selectedChatId =>
    messageSent set ((selectedChatId, message))
    chatIndex getActorRef selectedChatId foreach { _ ! Content(message) }
  }

  def messageLog(id: Int) = {
    val messageLog = Observable(Seq.empty[Message])

    messageSent addObserver { case (chatId, message) =>
      if (chatId == id)
        messageLog set (
          Message(message, own = true) +: (if (ui.storeLog) messageLog.get else Nil))
    }

    messageReceived addObserver { case (chatId, message) =>
      if (chatId == id)
        messageLog set (
          Message(message, own = false) +: (if (ui.storeLog) messageLog.get else Nil))
    }

    messageLog
  }

  def unreadMessageCount(id: Int) = {
    val unreadMessageCount = Observable(0)

    selectedChatId addObserver { _ =>
      if (selectedChatId.get == Some(id))
        unreadMessageCount set 0
    }

    messageReceived addObserver { case (chatId, _) =>
      if (selectedChatId.get != Some(id) && chatId == id)
        unreadMessageCount set (unreadMessageCount.get + 1)
    }

    unreadMessageCount
  }


  val selectedChatId = Observable(Option.empty[Int])

  val chats = Observable(Seq.empty[ChatLog])

  def userConnected(id: Int) = {
    val joined = ChatLog(id, Observable(""), unreadMessageCount(id), messageLog(id))
    chats set (chats.get :+ joined)

    chatIndex getActorRef id foreach { _ ! ChangeName(ui.name.get) }
  }

  def userDisconnected(id: Int) = {
    chats set (chats.get filterNot { _.id == id })

    if (selectedChatId.get == Some(id))
      selectedChatId set None
  }


  val updatingChats = Set.empty[ChatLog]

  def updateChats(): Unit = {
    val updatedChats =
      chats.get map { case chat @ ChatLog(id, name, unread, _) =>
        if (!(updatingChats contains chat)) {
          updatingChats += chat
          name addObserver { _ => updateChats }
          unread addObserver { _ => updateChats }
        }
        Chat(id, name.get, unread.get, selectedChatId.get == Some(id))
      } sortBy { _.name }

    ui updateChats updatedChats
  }

  val updatingMessages = Set.empty[ChatLog]

  def updateMessages(): Unit = {
    val updatedMessages =
      selectedChatId.get flatMap { id =>
        chats.get collectFirst { case chat @ ChatLog(`id`, _, _, log) =>
          if (!(updatingMessages contains chat)) {
            updatingMessages += chat
            log addObserver { _ => updateMessages }
          }
          log.get
        }
      } getOrElse Seq.empty

    ui updateMessages updatedMessages
  }

  selectedChatId addObserver { _ =>
    updateChats
    updateMessages
  }

  chats addObserver { _ =>
    updateChats
    updateMessages
  }

  ui.messageSent addObserver { message =>
    sendMessage(message)

    if (selectedChatId.get.nonEmpty)
      ui.clearMessage
  }

  ui.chatSelected addObserver { chat => selectedChatId set Some(chat.id) }

  ui.chatClosed addObserver { chat => disconnectUser(chat.id) }
}
