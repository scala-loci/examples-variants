package shapes

import util._

import akka.actor._
import rescala._
import upickle.default._

import scala.util.Random
import scala.collection.mutable.ListBuffer

class Application(connectionEstablished: Observable[WebSocket]) extends Actor {
  val clients = ListBuffer.empty[ActorRef]

  val modified = Evt[Modification]

  def receive = {
    case WebSocketRemoteActor.UserDisconnected => // #CB
      clients -= sender // #CB #IMP-STATE

    case modification: Modification => // #CB
      modified fire modification
  }

  connectionEstablished addObserver { socket => // #CB
    val client = context actorOf Props(new WebSocketRemoteActor(self, socket))
    clients += client // #IMP-STATE

    client ! InitialPosition(figureInitialPosition.now)
    client ! Figures(figures.now)
  }

  def send(message: Update) = clients foreach { _ ! message }

  def removeClosedSockets() = clients foreach {
    _ ! WebSocketRemoteActor.DisconnectClosed
  }

  val figureInitialPosition = {
    val initialOffset = 60
    val offset = 20
    val max = Position(200, 400)

    (modified collect { case Create(_) => () }).fold(Position(60, 60)) { (pos, _) =>
      if (pos.x > max.x && pos.y > max.y)
        Position(initialOffset, initialOffset)
      else if (pos.x > max.x)
        Position(initialOffset, pos.y - max.x + initialOffset)
      else
        Position(pos.x + offset, pos.y + offset)
    }
  }

  val figures = modified.fold(List.empty[Figure]) {
    case (figures, Create(figure)) =>
      figure :: figures

    case (figures, Change(figure)) =>
      val cleaned = figures filterNot { _.id == figure.id }
      if (figures.size == cleaned.size)
        cleaned
      else
        figure :: cleaned

    case (figures, Remove(figure)) =>
      figures filterNot { _.id == figure.id }
  }

  figureInitialPosition observe { pos => send(InitialPosition(pos)) } // #CB
  figures observe { figures => send(Figures(figures)) } // #CB
}
