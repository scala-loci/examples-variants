package shapes

import util._

import rescala._
import upickle.default._

import scala.util.Random
import scala.collection.mutable.ListBuffer

class Application(connectionEstablished: Observable[WebSocket]) {
  val sockets = ListBuffer.empty[WebSocket]

  val modified = Evt[Modification]

  connectionEstablished addObserver { socket => // #CB
    sockets += socket // #IMP-STATE

    socket.received addObserver received // #REMOTE-RECV #CB
    socket.closed addObserver { _ => sockets -= socket } // #CB #IMP-STATE

    socket send write[Update](InitialPosition(figureInitialPosition.now)) // #REMOTE-SEND
    socket send write[Update](Figures(figures.now)) // #REMOTE-SEND
  }

  def received(message: String) = {
    removeClosedSockets

    modified fire read[Modification](message)
  }

  def send(message: Update) = sockets foreach { _ send write(message) }

  def removeClosedSockets() =
    sockets --= sockets filterNot { _.isOpen } // #IMP-STATE

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
