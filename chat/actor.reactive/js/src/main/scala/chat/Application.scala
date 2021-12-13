package chat

import util._

import rescala.default._

import akka.actor._

import org.scalajs.dom

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class Application(ui: FrontEnd) extends Actor {
  class ChatIndex {
    val connections = mutable.Map.empty[Int, ActorRef]

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
      server ! Connect(id, Left(RTCSessionDescription.toTuple(sdp)))

    case WebRTCRemoteActor.Candidate(id, candidate) =>
      server ! Connect(id, Right(RTCIceCandidate.toTuple(candidate)))

    case WebRTCRemoteActor.UserConnected(id) =>
      userConnected(id)

    case WebRTCRemoteActor.UserDisconnected(id) =>
      if (chatIndex contains id) {
        chatIndex.remove(id)
        userDisconnected(id)
      }

    case WebRTCRemoteActor.UserMessage(id, message) =>
      userMessage(id, message)

    case WebSocketRemoteActor.RegistryMessage(message @ Users(_)) =>
      users.set(message.users)

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
      val userActor = context actorOf Props(
        new WebRTCRemoteActor(self, user.id, channelLabel, createOffer = true))
      chatIndex.insert(user.id -> userActor)
    }
  }

  def chatConnecting(connecting: Connect) = {
    val sdp = connecting.session.left.toOption map RTCSessionDescription.fromTuple
    val ice = connecting.session.toOption map RTCIceCandidate.fromTuple

    val user =
      chatIndex getActorRef connecting.id map { user =>
        sdp foreach {
          sdp => user ! WebRTCRemoteActor.ApplySession(sdp, createAnswer = false)
        }
        user
      } getOrElse {
        val user = context actorOf Props(
          new WebRTCRemoteActor(self, connecting.id, channelLabel, createOffer = false))
        chatIndex.insert(connecting.id -> user)

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
    chatIndex.remove(id)
    user ! WebRTCRemoteActor.Close
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

  val messageSent = (ui.messageSent map { message => selectedChatId() map { _-> message } }).flatten

  messageSent observe { case (id, message) =>
    chatIndex getActorRef id foreach { _ ! Content(message) }
  }

  val messageReceived = Evt[(Int, String)]()

  val chats = Var(Seq.empty[ChatLog])

  def userConnected(id: Int) = {
    val joined = ChatLog(id, Var(""), unreadMessageCount(id), messageLog(id))
    chats transform { _ :+ joined }

    chatIndex getActorRef id foreach { _ ! ChangeName(ui.name.readValueOnce) }
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
