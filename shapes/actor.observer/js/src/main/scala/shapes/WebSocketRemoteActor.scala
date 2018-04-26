package shapes

import util._

import upickle.default._

import akka.actor._

import org.scalajs.dom

class WebSocketRemoteActor(actorRef: ActorRef, url: String) extends Actor {
  val socket = new dom.WebSocket(url)

  socket.onmessage = { event: dom.MessageEvent => // #CB-ActorImpl
    actorRef ! read[Update](event.data.toString)
  }

  def receive = {
    case message: Modification =>
      if (socket.readyState == dom.WebSocket.OPEN)
        socket send write(message)
      else
        socket addEventListener ("open", { _: dom.Event => self ! message })
  }
}
