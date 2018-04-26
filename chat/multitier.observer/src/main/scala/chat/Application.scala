package chat

import util._

import loci._
import loci.basicTransmitter._
import loci.serializable.upickle._
import loci.experimental.webrtc._

import scala.collection.mutable.Set
import scala.collection.mutable.Map
import scala.collection.mutable.WeakHashMap
import scala.util.Random
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


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


  def usersChanged() = placed[Registry] { implicit! =>
    val (nodes, names) = (remote[Node].connected map { node =>
      node -> (name from node).asLocal // #REMOTE #CROSS-COMP
    }).unzip

    Future sequence names foreach { names =>
      val users = nodes zip names map { case (node, name) =>
        User(nodeIndex getId node, name)
      }

      nodes.zipWithIndex foreach { case (node, index) =>
        remote.on(node) call updateUsers( // #REMOTE
          users patch (index, Seq.empty, 1) sortBy { _.name })
      }
    }
  }

  placed[Registry] { implicit! =>
    remote[Node].left += { _ => usersChanged } // #CB
    remote[Node].joined += { _ => usersChanged } // #CB
  }

  var name = placed[Node] { implicit! => peer.ui.name.get }

  placed[Node] { implicit! =>
    peer.ui.name addObserver { userName => // #CB
      name = userName
      remote call usersChanged // #REMOTE
      remote call updateName(name) // #REMOTE
    }
  }

  val selectedChatId = placed[Node].local { implicit! => Observable(Option.empty[Int]) }

  placed[Node].local { implicit! =>
    peer.ui.chatRequested addObserver { user => // #CB
      selectedChatId set Some(user.id) // #IMP-STATE
    }

    peer.ui.chatSelected addObserver { chat => // #CB
      selectedChatId set Some(chat.id) // #IMP-STATE
    }

    peer.ui.chatClosed addObserver { chat => // #CB
      if (selectedChatId.get contains chat.id)
        selectedChatId set None // #IMP-STATE
    }
  }

  val messageSent = placed[Node].local { implicit! => Observable((0, "")) }

  val messageReceived = placed[Node].local { implicit! => Observable((0, "")) }

  def sendMessage(message: String) = placed[Node].local { implicit! =>
    selectedChatId.get foreach { selectedChatId =>
      val node = remote[Node].connected collectFirst {
        case node if chatIndex getId node contains selectedChatId => node
      }

      node foreach { node =>
        messageSent set ((selectedChatId, message)) // #IMP-STATE
        remote.on(node) call receiveMessage(message) // #REMOTE
      }
    }
  }

  def receiveMessage(message: String) = placed[Node].sbj { implicit! => node: Remote[Node] =>
    chatIndex getId node foreach { id =>
      messageReceived set ((id, message)) // #IMP-STATE
    }
  }

  def messageLog(id: Int) = placed[Node].local { implicit! =>
    val messageLog = Observable(Seq.empty[Message])

    messageSent addObserver { case (chatId, message) => // #CB
      if (chatId == id)
        messageLog set ( // #IMP-STATE
          Message(message, own = true) +: (if (peer.ui.storeLog) messageLog.get else Nil))
    }

    messageReceived addObserver { case (chatId, message) => // #CB
      if (chatId == id)
        messageLog set ( // #IMP-STATE
          Message(message, own = false) +: (if (peer.ui.storeLog) messageLog.get else Nil))
    }

    messageLog
  }

  def unreadMessageCount(id: Int) = placed[Node].local { implicit! =>
    val unreadMessageCount = Observable(0)

    selectedChatId addObserver { _ => // #CB
      if (selectedChatId.get == Some(id))
        unreadMessageCount set 0 // #IMP-STATE
    }

    messageReceived addObserver { case (chatId, _) => // #CB
      if (selectedChatId.get != Some(id) && chatId == id)
        unreadMessageCount set (unreadMessageCount.get + 1) // #IMP-STATE
    }

    unreadMessageCount
  }

  val chats = placed[Node].local { implicit! => Observable(Seq.empty[ChatLog]) }

  placed[Node].local { implicit! =>
    remote[Node].left += { left => // #CB
      chats set (chats.get filterNot { case ChatLog(node, _, _, _, _) => node == left }) // #IMP-STATE
    }

    remote[Node].joined += { node => // #CB
      chatIndex getId node foreach { id =>
        val chatName = Observable("")
        (name from node).asLocal foreach { chatName set _ } // #REMOTE #CROSS-COMP #IMP-STATE

        val joined = ChatLog(node, id,
          chatName,
          unreadMessageCount(id),
          messageLog(id))

        chats set (chats.get :+ joined) // #IMP-STATE
      }
    }
  }

  def updateUsers(users: Seq[User]) = placed[Node] { implicit! =>
    peer.ui updateUsers users // #IMP-STATE
  }

  def updateName(name: String) = placed[Node].sbj { implicit! => node: Remote[Node] =>
    chatIndex getId node foreach { id =>
      chats.get find { _.id == id } foreach { _.name set name } // #IMP-STATE
    }
  }


  placed[Node] { implicit! =>
    peer.ui.chatClosed addObserver { case Chat(id, _, _, _) => // #CB
      chats.get collectFirst { case ChatLog(node, `id`, _, _, _) => node.disconnect }
    }

    val updatingChats = Set.empty[ChatLog]

    def updateChats(): Unit = {
      val updatedChats =
        chats.get map { case chat @ ChatLog(_, id, name, unread, _) =>
          if (!(updatingChats contains chat)) {
            updatingChats += chat
            name addObserver { _ => updateChats } // #CB
            unread addObserver { _ => updateChats } // #CB
          }
          Chat(id, name.get, unread.get, selectedChatId.get == Some(id))
        } sortBy { _.name }

      peer.ui updateChats updatedChats // #IMP-STATE
    }

    val updatingMessages = Set.empty[ChatLog]

    def updateMessages(): Unit = {
      val updatedMessages =
        selectedChatId.get flatMap { id =>
          chats.get collectFirst { case chat @ ChatLog(_, `id`, _, _, log) =>
            if (!(updatingMessages contains chat)) {
              updatingMessages += chat
              log addObserver { _ => updateMessages } // #CB
            }
            log.get
          }
        } getOrElse Seq.empty

      peer.ui updateMessages updatedMessages // #IMP-STATE
    }

    selectedChatId addObserver { _ => // #CB
      updateChats
      updateMessages
    }

    chats addObserver { _ => // #CB
      updateChats
      updateMessages
    }

    peer.ui.messageSent addObserver { message => // #CB
      sendMessage(message)

      if (selectedChatId.get.nonEmpty)
        peer.ui.clearMessage
    }

    peer.ui.chatRequested addObserver { user => // #CB
      if ((chatIndex getConnector user.id).isEmpty) {
        val offer = WebRTC.offer() incremental propagateUpdate(user.id)
        chatIndex insert user.id -> offer // #IMP-STATE
        remote[Node] connect offer
      }
    }

    remote[Node].left += chatIndex.remove // #CB
  }

  def propagateUpdate
      (requestedId: Int)
      (update: WebRTC.IncrementalUpdate): Unit localOn Node = placed { implicit! =>
    remote[Registry].sbj.capture(requestedId, update) { implicit! => requesting: Remote[Node] => // #REMOTE #CROSS-COMP
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
