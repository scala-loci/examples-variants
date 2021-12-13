package chat

import util._

import loci.language._
import loci.serializer.upickle._
import loci.communicator.webrtc._
import loci.platform

import scala.collection.mutable.Set
import scala.collection.mutable.Map
import scala.collection.mutable.WeakHashMap
import scala.util.Random
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


@multitier object Application {
  @peer type Registry <: { type Tie <: Multiple[Node] }
  @peer type Node <: { type Tie <: Multiple[Node] with Optional[Registry] }

  val ui: Local[FrontEnd] on Node


  class NodeIndex {
    val nodes = WeakHashMap.empty[Remote[Node], Int]

    def getId(remote: Remote[Node]) =
      nodes.getOrElseUpdate(remote, Random.nextInt())

    def getNode(id: Int) = nodes collectFirst {
      case (remote, nodeId) if nodeId == id => remote
    }
  }

  val nodeIndex = on[Registry] local { implicit! => new NodeIndex }


  class ChatIndex {
    val connectors = Map.empty[Int, WebRTC.Connector]

    def getConnector(id: Int) = connectors get id

    def getConnectorOrElse(id: Int, connector: => WebRTC.Connector) =
      connectors.getOrElse(id, connector)

    def insert(idConnector: (Int, WebRTC.Connector)) = connectors += idConnector

    def remove(node: Remote[_]) = getId(node) map { connectors -= _ }

    def getId(node: Remote[_]) = connectors collectFirst {
      case (id, connector) if node.protocol setupBy connector => id
    }
  }

  val chatIndex = on[Node] local { implicit! => new ChatIndex }


  def usersChanged() = on[Registry] { implicit! =>
    val (nodes, names) = (remote[Node].connected map { node =>
      node -> (name from node).asLocal
    }).unzip

    Future sequence names foreach { names =>
      val users = nodes zip names map { case (node, name) =>
        User(nodeIndex getId node, name)
      }

      nodes.zipWithIndex foreach { case (node, index) =>
        remote(node) call updateUsers(
          users.patch(index, Seq.empty, 1) sortBy { _.name })
      }
    }
  }

  on[Registry] { implicit! =>
    remote[Node].left foreach { _ => usersChanged() }
    remote[Node].joined foreach { _ => usersChanged() }
  }

  var name = on[Node] { implicit! => ui.name.get }

  on[Node] { implicit! =>
    ui.name addObserver { userName =>
      name = userName
      remote call usersChanged()
      remote call updateName(name)
    }
  }

  val selectedChatId = on[Node] local { implicit! => Observable(Option.empty[Int]) }

  on[Node] { implicit! =>
    ui.chatRequested addObserver { user =>
      selectedChatId.set(Some(user.id))
    }

    ui.chatSelected addObserver { chat =>
      selectedChatId.set(Some(chat.id))
    }

    ui.chatClosed addObserver { chat =>
      if (selectedChatId.get contains chat.id)
        selectedChatId.set(None)
    }
  }

  val messageSent = on[Node] local { implicit! => Observable(0 -> "") }

  val messageReceived = on[Node] local { implicit! => Observable(0 -> "") }

  def sendMessage(message: String) = on[Node] local { implicit! =>
    selectedChatId.get foreach { selectedChatId =>
      val node = remote[Node].connected collectFirst {
        case node if chatIndex getId node contains selectedChatId => node
      }

      node foreach { node =>
        messageSent.set(selectedChatId -> message)
        remote(node) call receiveMessage(message)
      }
    }
  }

  def receiveMessage(message: String) = on[Node] sbj { implicit! => node: Remote[Node] =>
    chatIndex getId node foreach { id =>
      messageReceived.set(id -> message)
    }
  }

  def messageLog(id: Int) = on[Node] local { implicit! =>
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

  def unreadMessageCount(id: Int) = on[Node] local { implicit! =>
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

  val chats = on[Node] local { implicit! => Observable(Seq.empty[ChatLog]) }

  on[Node] { implicit! =>
    remote[Node].left foreach { left =>
      chats.set(chats.get filterNot { case ChatLog(node, _, _, _, _) => node == left })
    }

    remote[Node].joined foreach { node =>
      chatIndex getId node foreach { id =>
        val chatName = Observable("")
        (name from node).asLocal foreach chatName.set

        val joined = ChatLog(node, id,
          chatName,
          unreadMessageCount(id),
          messageLog(id))

        chats.set(chats.get :+ joined)
      }
    }
  }

  def updateUsers(users: Seq[User]) = on[Node] { implicit! =>
    ui.updateUsers(users)
  }

  def updateName(name: String) = on[Node] sbj { implicit! => node: Remote[Node] =>
    chatIndex getId node foreach { id =>
      chats.get find { _.id == id } foreach { _.name.set(name) }
    }
  }


  on[Node] { implicit! =>
    ui.chatClosed addObserver { case Chat(id, _, _, _) =>
      chats.get collectFirst { case ChatLog(node, `id`, _, _, _) => node.disconnect() }
    }

    val updatingChats = Set.empty[ChatLog]

    def updateChats(): Unit = {
      val updatedChats =
        chats.get map { case chat @ ChatLog(_, id, name, unread, _) =>
          if (!(updatingChats contains chat)) {
            updatingChats += chat
            name addObserver { _ => updateChats() }
            unread addObserver { _ => updateChats() }
          }
          Chat(id, name.get, unread.get, selectedChatId.get == Some(id))
        } sortBy { _.name }

      ui.updateChats(updatedChats)
    }

    val updatingMessages = Set.empty[ChatLog]

    def updateMessages(): Unit = {
      val updatedMessages =
        selectedChatId.get flatMap { id =>
          chats.get collectFirst { case chat @ ChatLog(_, `id`, _, _, log) =>
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

    ui.chatRequested addObserver { user =>
      platform(platform.js) {
        if ((chatIndex getConnector user.id).isEmpty) {
          val offer = WebRTC.offer() incremental propagateUpdate(user.id)
          chatIndex.insert(user.id -> offer)
          remote[Node] connect offer
        }
      }
    }

    remote[Node].left foreach chatIndex.remove
  }

  def propagateUpdate
      (requestedId: Int)
      (update: WebRTC.IncrementalUpdate): Local[Unit] on Node = placed { implicit! =>
    on[Registry].run.capture(requestedId, update) sbj { implicit! => requesting: Remote[Node] =>
      (nodeIndex getNode requestedId) foreach { requested =>
        val requestingId = nodeIndex getId requesting

        on(requested).run.capture(update, requestingId) { implicit! =>
          platform(platform.js) {
            chatIndex.getConnectorOrElse(requestingId, {
              val answer = WebRTC.answer() incremental propagateUpdate(requestingId)
              chatIndex.insert(requestingId -> answer)
              remote[Node] connect answer
              answer
            }) use update
          }
        }
      }
    }
  }
}
