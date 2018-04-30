package shapes

import util._

import rescala._
import upickle.default._

import scala.collection.mutable.ListBuffer

class Application(connectionEstablished: Observable[WebSocket]) {
  val sockets = ListBuffer.empty[WebSocket]

  val modified = Evt[Modification]

  connectionEstablished addObserver { socket =>
    sockets += socket

    socket.received addObserver received
    socket.closed addObserver { _ => sockets -= socket }

    socket send write[Update](InitialPosition(figureInitialPosition.readValueOnce))
    socket send write[Update](Figures(figures.readValueOnce))
  }

  def received(message: String) = {
    removeClosedSockets

    modified fire read[Modification](message)
  }

  def send(message: Update) = sockets foreach { _ send write(message) }

  def removeClosedSockets() =
    sockets --= sockets filterNot { _.isOpen }

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

  figureInitialPosition observe { pos => send(InitialPosition(pos)) }
  figures observe { figures => send(Figures(figures)) }
}
