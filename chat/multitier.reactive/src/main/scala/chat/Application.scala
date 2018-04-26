package chat

import util._

import rescala._

import loci._
import loci.rescalaTransmitter._
import loci.serializable.upickle._
import loci.experimental.webrtc._

import scala.collection.mutable.Map
import scala.collection.mutable.WeakHashMap
import scala.util.Random


@multitier
object Application {
  trait Registry extends Peer { type Tie <: Multiple[Node] }
  trait Node extends Peer { type Tie <: Multiple[Node] with Optional[Registry]
    val ui: FrontEnd }


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


  val users = placed[Registry].sbj { implicit! => node: Remote[Node] =>
    Signal {
      (remote[Node].connected()
        collect { case remote if remote != node =>
          User(nodeIndex getId remote, (name from remote).asLocal()) // #REMOTE #IMP-STATE #CROSS-COMP
        }
        sortBy { _.name })
    }
  }

  val name = placed[Node] { implicit! => peer.ui.name }

  val selectedChatId = placed[Node].local { implicit! =>
    sealed trait ChatSelectionChanged
    case class Selected(selected: Int) extends ChatSelectionChanged
    case class Closed(closed: Int) extends ChatSelectionChanged

    ((peer.ui.chatRequested map { requested => Selected(requested.id) }) ||
     (peer.ui.chatSelected map { selected => Selected(selected.id) }) ||
     (peer.ui.chatClosed map { closed => Closed(closed.id) }))
    .fold(Option.empty[Int]) {
      case (_, Selected(selected)) => Some(selected)
      case (Some(selected), Closed(closed)) if selected == closed => None
      case (selected, _) => selected
    }
  }

  val messageSent = placed[Node].sbj { implicit! => node: Remote[Node] =>
    peer.ui.messageSent collect {
      case message if (chatIndex getId node) == selectedChatId.now => message
    }
  }

  def messageLog(node: Remote[Node]) = placed[Node].local { implicit! =>
    val messages =
      ((messageSent to node) map { Message(_, own = true) }) ||
      ((messageSent from node).asLocal map { Message(_, own = false) }) // #REMOTE #CROSS-COMP

    if (peer.ui.storeLog) messages.list else messages.latestOption map { _.toSeq }
  }

  def unreadMessageCount(node: Remote[Node], id: Int) = placed[Node].local { implicit! =>
    sealed trait ReadMessageChanged
    case object SelectionChanged extends ReadMessageChanged
    case object MessageArrived extends ReadMessageChanged

    ((selectedChatId.changed map { _ => SelectionChanged }) ||
     ((messageSent from node).asLocal map { _ => MessageArrived })) // #REMOTE #CROSS-COMP
    .fold(0) {
      case (_, SelectionChanged) if selectedChatId.now == Some(id) =>
        0
      case (count, MessageArrived) if selectedChatId.now != Some(id) =>
        count + 1
      case (count, _) =>
        count
    }
  }

  val chats = placed[Node].local { implicit! =>
    sealed trait ConnectedNodeChanged
    case class Joined(node: ChatLog) extends ConnectedNodeChanged
    case class Left(node: Remote[Node]) extends ConnectedNodeChanged

    val left = remote[Node].left map Left

    val joined = (remote[Node].joined
      map { node =>
        chatIndex getId node map { id =>
          ChatLog(node, id,
            (name from node).asLocal, // #REMOTE #CROSS-COMP
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
    Event {
      peer.ui.chatClosed() flatMap { case Chat(id, _, _, _) =>
        chats() collectFirst { case ChatLog(node, `id`, _, _, _) => node }
      }
    } observe { _.disconnect } // #CB

    peer.ui.users = Signal { users.asLocal() getOrElse Var.empty() } // #REMOTE #CROSS-COMP

    peer.ui.chats = Signal {
      chats() map { case ChatLog(node, id, name, unread, _) =>
        Chat(id, name(), unread(), selectedChatId() == Some(id))
      } sortBy { _.name }
    }

    peer.ui.messages = Signal {
      selectedChatId() flatMap { id =>
        chats() collectFirst {
          case ChatLog(_, `id`, _, _, log) => log()
        }
      } getOrElse Seq.empty
    }

    peer.ui.clearMessage = Event {
      peer.ui.messageSent() filter { _ => selectedChatId().nonEmpty }
    }.dropParam

    peer.ui.chatRequested observe { user => // #CB
      if ((chatIndex getConnector user.id).isEmpty) {
        val offer = WebRTC.offer() incremental propagateUpdate(user.id)
        chatIndex insert user.id -> offer // #IMP-STATE
        remote[Node] connect offer
      }
    }

    remote[Node].left observe chatIndex.remove // #CB
  }

  def propagateUpdate
      (requestedId: Int)
      (update: WebRTC.IncrementalUpdate): Unit localOn Node = placed { implicit! =>
    remote[Registry].sbj.capture(requestedId, update) { implicit! => requesting: Remote[Node] => // #REMOTE
      (nodeIndex getNode requestedId) foreach { requested =>
        val requestingId = nodeIndex getId requesting // #IMP-STATE

        remote.on(requested).capture(update, requestingId) { implicit! => // #REMOTE #CROSS-COMP
          chatIndex getConnectorOrElse (requestingId, {
            val answer = WebRTC.answer() incremental propagateUpdate(requestingId)
            chatIndex insert requestingId -> answer // #IMP-STATE
            remote[Node] connect answer
            answer
          }) use update
        }
      }
    }
  }
}
