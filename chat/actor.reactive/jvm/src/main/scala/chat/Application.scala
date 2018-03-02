package chat

import util._

import akka.actor._

import scala.collection.mutable.Map
import scala.util.Random


class Application(connectionEstablished: Observable[WebSocket]) extends Actor {
  object nodeIndex {
    val actorRefs = Map.empty[ActorRef, User]

    def nodes = actorRefs.keys

    def get(id: Int) = actorRefs collectFirst {
      case (actorRef, user) if user.id == id => actorRef
    }

    def get(actorRef: ActorRef) = actorRefs get actorRef

    def getOrInsert(actorRef: ActorRef) =
      actorRefs getOrElseUpdate (actorRef, User(Random.nextInt, ""))

    def insert(socketUser: (ActorRef, User)) = actorRefs += socketUser

    def remove(actorRef: ActorRef) = actorRefs -= actorRef
  }

  def receive = {
    case WebSocketRemoteActor.UserDisconnected =>
      nodeIndex remove sender
      updateNodeList

    case ChangeName(name) =>
      removeClosedSockets
      val User(id, _) = nodeIndex getOrInsert sender
      nodeIndex insert sender -> User(id, name)
      updateNodeList

    case Connect(id, session) =>
      removeClosedSockets
      nodeIndex get sender foreach { user =>
        nodeIndex get id foreach { _ ! Connect(user.id, session) }
      }
  }

  connectionEstablished addObserver { socket =>
    val actorRef = context actorOf Props(new WebSocketRemoteActor(self, socket))
    nodeIndex getOrInsert actorRef

    updateNodeList
  }

  def updateNodeList = {
    nodeIndex.nodes foreach { targetActorRef =>
      val users = nodeIndex.nodes collect {
        case actorRef if actorRef != targetActorRef => nodeIndex getOrInsert actorRef
      }
      targetActorRef ! Users(users.toSeq sortBy { _.name })
    }
  }

  def removeClosedSockets = nodeIndex.nodes foreach {
    _ ! WebSocketRemoteActor.DisconnectClosed
  }
}
