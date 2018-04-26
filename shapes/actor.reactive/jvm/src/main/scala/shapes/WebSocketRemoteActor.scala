package shapes

import util._
import upickle.default._
import akka.actor._

import WebSocketRemoteActor._

object WebSocketRemoteActor {
  case object DisconnectClosed
  case object UserDisconnected
}

class WebSocketRemoteActor(actorRef: ActorRef, socket: WebSocket) extends Actor {
  socket.received addObserver { message => actorRef ! read[Modification](message) } // #CB-ActorImpl
  socket.closed addObserver { _ => actorRef ! UserDisconnected } // #CB-ActorImpl

  def receive = {
    case DisconnectClosed => // #CB-ActorImpl
      if (!socket.isOpen)
        actorRef ! UserDisconnected

    case message: Update => // #CB-ActorImpl
      socket send write(message)
  }
}
