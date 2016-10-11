package shapes

import util._

import upickle.default._

import scala.util.Random
import scala.collection.mutable.ListBuffer

class Application(connectionEstablished: Observable[WebSocket]) {
  val sockets = ListBuffer.empty[WebSocket]
  var figures = List.empty[Figure]

  val initialOffset = 60
  val offset = 20
  val max = Position(200, 400)
  var initialPosition = Position(60, 60)

  connectionEstablished addObserver { socket =>
    sockets += socket

    socket.received addObserver received
    socket.closed addObserver { _ => sockets -= socket }

    socket send write(initialPosition)
    socket send write(figures)
  }

  def received(message: String) = {
    removeClosedSockets

    val modification = read[Modification](message)
    updateInitialPosition(modification)
    updateFigures(modification)
  }

  def removeClosedSockets =
    sockets --= sockets filterNot { _.isOpen }

  def updateInitialPosition(modification: Modification) = modification match {
    case Create(figure) =>
      initialPosition =
        if (initialPosition.x > max.x && initialPosition.y > max.y)
          Position(initialOffset, initialOffset)
        else if (initialPosition.x > max.x)
          Position(initialOffset, initialPosition.y - max.x + initialOffset)
        else
          Position(initialPosition.x + offset, initialPosition.y + offset)

      sockets foreach { _ send write(initialPosition) }

    case _ =>
  }

  def updateFigures(modification: Modification) = {
    val figuresUpdated = modification match {
      case Create(figure) =>
        figures ::= figure
        true

      case Change(figure) =>
        val cleaned = figures filterNot { _.id == figure.id }
        val updated = figures.size != cleaned.size
        figures =
          if (updated)
            figure :: cleaned
          else
            cleaned

        updated

      case Remove(figure) =>
        val cleaned = figures filterNot { _.id == figure.id }
        val updated = figures.size != cleaned.size
        figures = cleaned

        updated
    }

    if (figuresUpdated)
      sockets foreach { _ send write(figures) }
  }
}
