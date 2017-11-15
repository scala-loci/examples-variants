package chat

import util._

import rescala._

import akka.actor._

import org.scalajs.dom.experimental.webrtc._

import scala.concurrent.Future
import scala.collection.mutable.Map
import scala.collection.mutable.Set

import scala.concurrent.ExecutionContext.Implicits.global

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

    case WebSocketRemoteActor.RegistryMessage(message @ Users(_)) =>
      users set message.users

    case WebSocketRemoteActor.RegistryMessage(connect @ Connect(_, _)) =>
      chatConnecting(connect)
  }


  ui.name observe { name =>
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

  ui.chatRequested observe { user =>
    if (!(chatIndex contains user.id)) {
      val userActor = context actorOf Props(
        new WebRTCRemoteActor(self, user.id, channelLabel, createOffer = true))
      chatIndex insert user.id -> userActor
    }
  }

  def chatConnecting(connecting: Connect) = {
    val sdp = connecting.session.left.toOption map RTCSessionDescription.fromTuple
    val ice = connecting.session.right.toOption map RTCIceCandidate.fromTuple

    val user =
      chatIndex getActorRef connecting.id map { user =>
        sdp foreach {
          sdp => user ! WebRTCRemoteActor.ApplySession(sdp, createAnswer = false)
        }
        user
      } getOrElse {
        val user = context actorOf Props(
          new WebRTCRemoteActor(self, connecting.id, channelLabel, createOffer = false))
        chatIndex insert connecting.id -> user

        sdp foreach {
          sdp => user ! WebRTCRemoteActor.ApplySession(sdp, createAnswer = true)
        }
        user
      }

    ice foreach {
      candidate => user ! WebRTCRemoteActor.ApplyCandidate(candidate)
    }
  }

  def disconnectUser(id: Int) = chatIndex getActorRef id foreach { user =>
    chatIndex remove id
    user ! WebRTCRemoteActor.Close
    userDisconnected(id)
  }


  val messageSent = Event {
    ui.messageSent() flatMap { message => selectedChatId() map { ((_, message)) } }
  }

  messageSent observe { case (id, message) =>
    chatIndex getActorRef id foreach { _ ! Content(message) }
  }

  val messageReceived = Evt[(Int, String)]

  def userMessage(id: Int, message: PeerMessage) = message match {
    case Content(content) =>
      messageReceived fire ((id, content))
    case ChangeName(name) =>
      chats.now find { _.id == id } foreach { _.name set name }
  }

  def messageLog(id: Int) = {
    val messages =
      (messageSent collect { case (`id`, content) => Message(content, own = true) }) ||
      (messageReceived collect { case (`id`, content) => Message(content, own = false) })

    if (ui.storeLog) messages.list else messages.latestOption map { _.toSeq }
  }

  def unreadMessageCount(id: Int) = {
    trait ReadMessageChanged
    case object SelectionChanged extends ReadMessageChanged
    case object MessageArrived extends ReadMessageChanged

    ((selectedChatId.changed map { _ => SelectionChanged }) ||
     ((messageReceived collect { case (`id`, _) => MessageArrived })))
    .fold(0) {
      case (_, SelectionChanged) if selectedChatId.now == Some(id) =>
        0
      case (count, MessageArrived) if selectedChatId.now != Some(id) =>
        count + 1
      case (count, _) =>
        count
    }
  }

  val selectedChatId = {
    trait ChatSelectionChanged
    case class Selected(selected: Int) extends ChatSelectionChanged
    case class Closed(closed: Int) extends ChatSelectionChanged

    ((ui.chatRequested map { requested => Selected(requested.id) }) ||
     (ui.chatSelected map { selected => Selected(selected.id) }) ||
     (ui.chatClosed map { closed => Closed(closed.id) }))
    .fold(Option.empty[Int]) {
      case (_, Selected(selected)) => Some(selected)
      case (Some(selected), Closed(closed)) if selected == closed => None
      case (selected, _) => selected
    }
  }

  val chats = Var(Seq.empty[ChatLog])

  def userConnected(id: Int) = Future {
    val joined = ChatLog(id, Var(""), unreadMessageCount(id), messageLog(id))
    chats transform { _ :+ joined }

    chatIndex getActorRef id foreach { _ ! ChangeName(ui.name.now) }
  }

  def userDisconnected(id: Int) = Future {
    chats transform { _ filterNot { _.id == id } }
  }


  val users = Var.empty[Seq[User]]

  ui.users = users

  ui.chats = Signal {
    chats() map { case ChatLog(id, name, unread, _) =>
      Chat(id, name(), unread(), selectedChatId() == Some(id))
    } sortBy { _.name }
  }

  ui.messages = Signal {
    selectedChatId() flatMap { id =>
      chats() collectFirst {
        case ChatLog(`id`, _, _, log) => log()
      }
    } getOrElse Seq.empty
  }

  ui.clearMessage = Event {
    ui.messageSent() filter { _ => selectedChatId().nonEmpty }
  }.dropParam

  ui.chatClosed observe { chat => disconnectUser(chat.id) }
}
