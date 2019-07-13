package chat

import util._

import loci._
import loci.transmitter.rescala._
import loci.serializer.upickle._
import loci.communicator.experimental.webrtc._

import rescala.default._

import scala.collection.mutable.Map
import scala.collection.mutable.WeakHashMap
import scala.util.Random


@multitier object Application {
  @peer type Registry <: { type Tie <: Multiple[Node] }
  @peer type Node <: { type Tie <: Multiple[Node] with Optional[Registry] }

  val ui: Local[FrontEnd] on Node


  class NodeIndex {
    val nodes = WeakHashMap.empty[Remote[Node], Int]

    def getId(remote: Remote[Node]) =
      nodes getOrElseUpdate (remote, Random.nextInt)

    def getNode(id: Int) = nodes collectFirst {
      case (remote, nodeId) if nodeId == id => remote
    }
  }

  val nodeIndex = on[Registry] local { implicit! => new NodeIndex }


  class ChatIndex {
    val connectors = Map.empty[Int, WebRTC.Connector]

    def getConnector(id: Int) = connectors get id

    def getConnectorOrElse(id: Int, connector: => WebRTC.Connector) =
      connectors getOrElse (id, connector)

    def insert(idConnector: (Int, WebRTC.Connector)) = connectors += idConnector

    def remove(node: Remote[_]) = getId(node) map { connectors -= _ }

    def getId(node: Remote[_]) = connectors collectFirst {
      case (id, connector) if node.protocol setupBy connector => id
    }
  }

  val chatIndex = on[Node] local { implicit! => new ChatIndex }


  val users = on[Registry] sbj { implicit! => node: Remote[Node] =>
    Signal.dynamic {
      (remote[Node].connected()
        collect { case remote if remote != node =>
          User(nodeIndex getId remote, (name from remote).asLocal())
        }
        sortBy { _.name })
    }
  }

  val name = on[Node] { implicit! => ui.name }

  val selectedChatId = on[Node] local { implicit! =>
    Events.foldAll(Option.empty[Int])(selected => Events.Match(
      ui.chatRequested >> { requested => Some(requested.id) },
      ui.chatSelected >> { selected => Some(selected.id) },
      ui.chatClosed >> { closed =>
        if (Some(closed.id) == selected) None else selected
      }
    ))
  }

  val messageSent = on[Node] sbj { implicit! => node: Remote[Node] =>
    ui.messageSent collect {
      case message if (chatIndex getId node) == selectedChatId() => message
    }
  }

  def messageLog(node: Remote[Node]) = on[Node] local { implicit! =>
    val messages =
      ((messageSent to node) map { Message(_, own = true) }) ||
      ((messageSent from node).asLocal map { Message(_, own = false) })

    if (ui.storeLog) messages.list else messages.latestOption map { _.toSeq }
  }

  def unreadMessageCount(node: Remote[Node], id: Int) = on[Node] local { implicit! =>
    Events.foldAll(0)(count => Events.Match(
      selectedChatId.changed >> { selected =>
        if (selected == Some(id)) 0 else count
      },
      ((messageSent from node).asLocal map { _ => selectedChatId() }) >> { selected =>
        if (selected != Some(id)) count + 1 else count
      }
    ))
  }

  val chats = on[Node] local { implicit! =>
    val joined = remote[Node].joined map { node =>
      chatIndex getId node map { id =>
        ChatLog(node, id,
          (name from node).asLocal,
          unreadMessageCount(node, id),
          messageLog(node))
      }
    }

    Events.foldAll(Seq.empty[ChatLog])(chats => Events.Match(
      joined.flatten >> { joined =>
        chats :+ joined
      },
      remote[Node].left >> { left =>
        chats filterNot { case ChatLog(node, _, _, _, _) => node == left }
      }
    ))
  }

  on[Node] { implicit! =>
    (ui.chatClosed map { case Chat(id, _, _, _) =>
      chats() collectFirst { case ChatLog(node, `id`, _, _, _) => node }
    }).flatten observe { _.disconnect }

    val empty = Var.empty[Seq[User]]
    ui.users = Signal { users.asLocal() getOrElse empty() }

    ui.chats = Signal.dynamic {
      chats() map { case ChatLog(node, id, name, unread, _) =>
        Chat(id, name(), unread(), selectedChatId() == Some(id))
      } sortBy { _.name }
    }

    ui.messages = Signal.dynamic {
      selectedChatId() flatMap { id =>
        chats() collectFirst {
          case ChatLog(_, `id`, _, _, log) => log()
        }
      } getOrElse Seq.empty
    }

    ui.clearMessage = (ui.messageSent filter { _ => selectedChatId().nonEmpty }).dropParam

    ui.chatRequested observe { user =>
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
      (update: WebRTC.IncrementalUpdate): Local[Unit] on Node = placed { implicit! =>
    on[Registry].run.capture(requestedId, update) sbj { implicit! => requesting: Remote[Node] =>
      (nodeIndex getNode requestedId) foreach { requested =>
        val requestingId = nodeIndex getId requesting

        on(requested).run.capture(update, requestingId) { implicit! =>
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
