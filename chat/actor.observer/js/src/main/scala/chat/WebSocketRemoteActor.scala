package chat

import util._

import upickle.default._

import akka.actor._

import org.scalajs.dom

import WebSocketRemoteActor._


object WebSocketRemoteActor {
  case class RegistryMessage(message: ClientMessage)
}

class WebSocketRemoteActor(actorRef: ActorRef, url: String) extends Actor {
  val socket = new dom.WebSocket(url)

  socket.onmessage = { event: dom.MessageEvent =>
    actorRef ! RegistryMessage(read[ClientMessage](event.data.toString))
  }

  def receive = {
    case message: ServerMessage =>
      if (socket.readyState == dom.WebSocket.OPEN)
        socket send write(message)
      else
        socket addEventListener ("open", { _: dom.Event => self ! message })
  }
}
