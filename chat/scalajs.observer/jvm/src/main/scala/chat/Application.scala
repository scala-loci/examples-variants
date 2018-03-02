package chat

import util._

import upickle.default._

import scala.collection.mutable.Map
import scala.util.Random


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

  connectionEstablished addObserver { socket =>
    nodeIndex getOrInsert socket

    socket.received addObserver { received(socket, _) }
    socket.closed addObserver { _ =>
      nodeIndex remove socket
      updateNodeList
    }

    updateNodeList
  }

  def updateNodeList = {
    nodeIndex.nodes foreach { targetSocket =>
      val users = nodeIndex.nodes collect {
        case socket if socket != targetSocket => nodeIndex getOrInsert socket
      }
      targetSocket send write(Users(users.toSeq sortBy { _.name }))
    }
  }

  def received(socket: WebSocket, message: String) = {
    removeClosedSockets

    read[ServerMessage](message) match {
      case ChangeName(name) =>
        val User(id, _) = nodeIndex getOrInsert socket
        nodeIndex insert socket -> User(id, name)
        updateNodeList

      case Connect(id, session) =>
        nodeIndex get socket foreach { user =>
          nodeIndex get id foreach { _ send write(Connect(user.id, session)) }
        }
    }
  }

  def removeClosedSockets = {
    val nodes = nodeIndex.nodes filterNot { _.isOpen }
    if (nodes.nonEmpty) {
      nodes foreach { nodeIndex remove _ }
      updateNodeList
    }
  }
}
