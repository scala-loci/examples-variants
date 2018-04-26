package chat

import util._

import upickle.default._

import scala.collection.mutable.Map
import scala.util.Random


sealed trait Message
@key("ChangeName") final case class ChangeName(name: String) extends Message
@key("Connect") final case class Connect(id: Int, sdp: String, ice: String) extends Message


class Application(connectionEstablished: Observable[WebSocket]) {
  object nodeIndex {
    val sockets = Map.empty[WebSocket, User]

    def nodes = sockets.keys

    def get(id: Int) = sockets collectFirst {
      case (socket, user) if user.id == id => socket
    }

    def get(socket: WebSocket) = sockets get socket

    def getOrInsert(socket: WebSocket) =
      sockets getOrElseUpdate (socket, User(Random.nextInt, ""))

    def insert(socketUser: (WebSocket, User)) = sockets += socketUser

    def remove(socket: WebSocket) = sockets -= socket
  }

  connectionEstablished addObserver { socket => // #CB
    nodeIndex getOrInsert socket // #IMP-STATE

    socket.received addObserver { received(socket, _) } // #REMOTE-RECV #CB
    socket.closed addObserver { _ => // #CB
      nodeIndex remove socket // #IMP-STATE
      updateNodeList
    }

    updateNodeList
  }

  def updateNodeList() = {
    nodeIndex.nodes foreach { targetSocket =>
      val users = nodeIndex.nodes collect {
        case socket if socket != targetSocket => nodeIndex getOrInsert socket // #IMP-STATE
      }
      targetSocket send write(users.toSeq sortBy { _.name }) // #REMOTE-SEND
    }
  }

  def received(socket: WebSocket, message: String) = {
    removeClosedSockets

    read[Message](message) match {
      case ChangeName(name) =>
        val User(id, _) = nodeIndex getOrInsert socket // #IMP-STATE
        nodeIndex insert socket -> User(id, name) // #IMP-STATE
        updateNodeList

      case Connect(id, sdp, ice) =>
        nodeIndex get socket foreach { user =>
          nodeIndex get id foreach { _ send write(Connect(user.id, sdp, ice)) } // #REMOTE-SEND
        }
    }
  }

  def removeClosedSockets() = {
    val nodes = nodeIndex.nodes filterNot { _.isOpen }
    if (nodes.nonEmpty) {
      nodes foreach { nodeIndex remove _ } // #IMP-STATE
      updateNodeList
    }
  }
}
