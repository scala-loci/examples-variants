package chat

import util._

import rescala._

import loci._
import loci.transmitter.rescala._
import loci.serializer.upickle._
import loci.communicator.experimental.webrtc._

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
      case (id, connector) if node.protocol setupBy connector => id
    }
  }

  val chatIndex = placed[Node].local { implicit! => new ChatIndex }


  val users = placed[Registry].sbj { implicit! => node: Remote[Node] =>
    Signal.dynamic {
      (remote[Node].connected()
        collect { case remote if remote != node =>
          User(nodeIndex getId remote, (name from remote).asLocal())
        }
        sortBy { _.name })
    }
  }

  val name = placed[Node] { implicit! => peer.ui.name }

  val selectedChatId = placed[Node].local { implicit! =>
    Events.foldAll(Option.empty[Int])(selected => Events.Match(
      peer.ui.chatRequested >> { requested => Some(requested.id) },
      peer.ui.chatSelected >> { selected => Some(selected.id) },
      peer.ui.chatClosed >> { closed =>
        if (Some(closed.id) == selected) None else selected
      }
    ))
  }

  val messageSent = placed[Node].sbj { implicit! => node: Remote[Node] =>
    peer.ui.messageSent collect {
      case message if (chatIndex getId node) == selectedChatId.readValueOnce => message
    }
  }

  def messageLog(node: Remote[Node]) = placed[Node].local { implicit! =>
    val messages =
      ((messageSent to node) map { Message(_, own = true) }) ||
      ((messageSent from node).asLocal map { Message(_, own = false) })

    if (peer.ui.storeLog) messages.list else messages.latestOption map { _.toSeq }
  }

  def unreadMessageCount(node: Remote[Node], id: Int) = placed[Node].local { implicit! =>
    Events.foldAll(0)(count => Events.Match(
      selectedChatId.changed >> { selected =>
        if (selected == Some(id)) 0 else count
      },
      ((messageSent from node).asLocal map { _ => selectedChatId() }) >> { selected =>
        if (selected != Some(id)) count + 1 else count
      }
    ))
  }

  val chats = placed[Node].local { implicit! =>
    val joined = remote[Node].joined map { node =>
      chatIndex getId node map { id =>
        ChatLog(node, id,
          (name from node).asLocal,
          unreadMessageCount(node, id),
          messageLog(node))
      }
    }

    Events.foldAll(Seq.empty[ChatLog])(chats => Events.Match(
      (joined collect { case Some(chat) => chat }) >> { joined =>
        chats :+ joined
      },
      remote[Node].left >> { left =>
        chats filterNot { case ChatLog(node, _, _, _, _) => node == left }
      }
    ))
  }

  placed[Node] { implicit! =>
    Event.dynamic {
      peer.ui.chatClosed() flatMap { case Chat(id, _, _, _) =>
        chats() collectFirst { case ChatLog(node, `id`, _, _, _) => node }
      }
    } observe { _.disconnect }

    peer.ui.users = Signal { users.asLocal() getOrElse Var.empty[Seq[User]].value }

    peer.ui.chats = Signal.dynamic {
      chats() map { case ChatLog(node, id, name, unread, _) =>
        Chat(id, name(), unread(), selectedChatId() == Some(id))
      } sortBy { _.name }
    }

    peer.ui.messages = Signal.dynamic {
      selectedChatId() flatMap { id =>
        chats() collectFirst {
          case ChatLog(_, `id`, _, _, log) => log()
        }
      } getOrElse Seq.empty
    }

    peer.ui.clearMessage = Event.dynamic {
      peer.ui.messageSent() filter { _ => selectedChatId().nonEmpty }
    }.dropParam

    peer.ui.chatRequested observe { user =>
      if ((chatIndex getConnector user.id).isEmpty) {
        val offer = WebRTC.offer() incremental propagateUpdate(user.id)
        chatIndex insert user.id -> offer
        remote[Node] connect offer
      }
    }

    remote[Node].left observe chatIndex.remove
  }

  def propagateUpdate
      (requestedId: Int)
      (update: WebRTC.IncrementalUpdate): Unit localOn Node = placed { implicit! =>
    remote[Registry].sbj.capture(requestedId, update) { implicit! => requesting: Remote[Node] =>
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
