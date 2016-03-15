package chat

import util._

import retier._
import retier.architectures.P2PRegistry._
import retier.rescalaTransmitter._
import retier.serializable.upickle._
import retier.experimental.webrtc._

import rescala.Signal
import rescala.events.Event
import rescala.events.emptyevent
import makro.SignalMacro.{SignalM => Signal}

import scala.collection.mutable.Map
import scala.collection.mutable.WeakHashMap
import scala.util.Random


@multitier
object Application {
  trait Registry extends RegistryPeer[Node]
  trait Node extends NodePeer[Node, Registry]


  class NodeIndex {
    val nodes = WeakHashMap.empty[Remote[Node], Int]

    def getId(remote: Remote[Node]) =
      nodes getOrElseUpdate (remote, Random.nextInt)

    def getNode(id: Int) = nodes collectFirst {
      case (remote, nodeId) if nodeId == id => remote
    }
  }

  val nodeIndex = placed[Registry].local { implicit! => new NodeIndex }


  class ChatIndex {
    val connectors = Map.empty[Int, WebRTC.Connector]

    def getConnector(id: Int) = connectors get id

    def getConnectorOrElse(id: Int, connector: => WebRTC.Connector) =
      connectors getOrElse (id, connector)

    def insert(idConnector: (Int, WebRTC.Connector)) = connectors += idConnector

    def remove(node: Remote[Peer]) = getId(node) map { connectors -= _ }

    def getId(node: Remote[Peer]) = connectors collectFirst {
      case (id, connector) if node.protocol establishedBy connector => id
    }
  }

  val chatIndex = placed[Node].local { implicit! => new ChatIndex }


  val users = placed[Registry].issued { implicit! => node: Remote[Node] =>
    Signal {
      (remote[Node].connected()
        collect { case remote if remote != node =>
          User(nodeIndex getId remote, (name from remote).asLocal())
        }
        sortBy { _.name })
    }
  }

  val ui = placed[Node].local { implicit! => new UI }

  val name = placed[Node] { implicit! => ui.name }

  val selectedChatId = placed[Node].local { implicit! =>
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

  val messageSent = placed[Node].issued { implicit! => node: Remote[Node] =>
    ui.messageSent collect {
      case message if (chatIndex getId node) == selectedChatId.get => message
    }
  }

  def messageLog(node: Remote[Node]) = placed[Node].local { implicit! =>
    Signal {
      (((messageSent to node) map {
         message => Message(message, own = true)
       }) ||
       ((messageSent from node).asLocal map {
         message => Message(message, own = false)
       }))
      .list()().reverse
    }
  }

  def unreadMessageCount(node: Remote[Node], id: Int) = placed[Node].local { implicit! =>
    trait ReadMessageChanged
    case object SelectionChanged extends ReadMessageChanged
    case object MessageArrived extends ReadMessageChanged

    ((selectedChatId.changed map { _ => SelectionChanged }) ||
     ((messageSent from node).asLocal map { _ => MessageArrived }))
    .fold(0) {
      case (_, SelectionChanged) if selectedChatId.get == Some(id) =>
        0
      case (count, MessageArrived) if selectedChatId.get != Some(id) =>
        count + 1
      case (count, _) =>
        count
    }
  }

  val chats = placed[Node].local { implicit! =>
    trait ConnectedNodeChanged
    case class Joined(node: ChatLog) extends ConnectedNodeChanged
    case class Left(node: Remote[Node]) extends ConnectedNodeChanged

    val left = remote[Node].left map { Left(_) }

    val joined = (remote[Node].joined
      map { node =>
        chatIndex getId node map { id =>
          ChatLog(node, id,
            (name from node).asLocal,
            unreadMessageCount(node, id),
            messageLog(node))
        }
      }
      collect { case Some(chat) => Joined(chat) })

    (left || joined).fold(Seq.empty[ChatLog]) {
      case (chats, Left(left)) =>
        chats filterNot { case ChatLog(node, _, _, _, _) => node == left }
      case (chats, Joined(joined)) =>
        chats :+ joined
    }
  }

  placed[Node] { implicit! =>
    ui.chatClosed += { case Chat(id, _, _, _) =>
      chats.get collectFirst { case ChatLog(node, `id`, _, _, _) => node.disconnect }
    }

    ui.users = Signal { users.asLocal() getOrElse Seq.empty }

    ui.chats = Signal {
      chats() map { case ChatLog(node, id, name, unread, _) =>
        Chat(id, name(), unread(), selectedChatId() == Some(id))
      } sortBy { _.name }
    }

    ui.messages = Signal {
      selectedChatId() flatMap { id =>
        chats() collectFirst {
          case ChatLog(_, `id`, _, _, chatLog) => chatLog()
        }
      } getOrElse Seq.empty
    }

    ui.clearMessage = (ui.messageSent && selectedChatId.get.nonEmpty).dropParam

    ui.chatRequested += { user =>
      val offer = WebRTC.offer() incremental propagateUpdate(user.id)
      chatIndex insert user.id -> offer
      remote[Node] connect offer
    }

    remote[Node].left += chatIndex.remove
  }

  def propagateUpdate
      (requestedId: Int)
      (update: WebRTC.IncrementalUpdate): Unit localOn Node = placed { implicit! =>
    remote[Registry].issued.capture(requestedId, update) { implicit! => requesting: Remote[Node] =>
      (nodeIndex getNode requestedId) foreach { requested =>
        val requestingId = nodeIndex getId requesting

        remote.on(requested).capture(update, requestingId) { implicit! =>
          chatIndex getConnectorOrElse (requestingId, {
            val answer = WebRTC.answer() incremental propagateUpdate(requestingId)
            chatIndex insert requestingId -> answer
            remote[Node] connect answer
            answer
          }) use update
        }
      }
    }
  }
}
