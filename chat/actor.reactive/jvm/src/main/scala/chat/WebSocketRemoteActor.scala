package chat

import util._
import upickle.default._
import akka.actor._

import WebSocketRemoteActor._

object WebSocketRemoteActor {
  case object DisconnectClosed
  case object UserDisconnected
}

class WebSocketRemoteActor(actorRef: ActorRef, socket: WebSocket) extends Actor {
  socket.received addObserver { message => actorRef ! read[ServerMessage](message) }
  socket.closed addObserver { _ => actorRef ! UserDisconnected }

  def receive = {
    case DisconnectClosed =>
      if (!socket.isOpen)
        actorRef ! UserDisconnected

    case message: ClientMessage =>
      socket send write(message)
  }
}
