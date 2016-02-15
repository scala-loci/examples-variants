package shapes

import util._

import upickle.default._

import scala.util.Random
import scala.collection.mutable.ListBuffer


sealed trait Modification
@key("Create") final case class Create(figure: Figure) extends Modification
@key("Change") final case class Change(figure: Figure) extends Modification
@key("Remove") final case class Remove(figure: Figure) extends Modification


class Application(connectionEstablished: Observable[WebSocket]) {
  val sockets = ListBuffer.empty[WebSocket]
  val figures = ListBuffer.empty[Figure]

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
      
      val message = write(initialPosition)
      sockets foreach { _ send message }

    case _ =>
  }

  def updateFigures(modification: Modification) = {
    val figuresUpdated = modification match {
      case Create(figure) =>
        figures += figure
        true

      case Change(figure) =>
        val size = figures.size
        val obsoleteFigures = figures filter { el =>
          el.id == figure.id && el != figure
        }

        figures --= obsoleteFigures
        val updated = size != figures.size

        if (updated)
          figures += figure

        updated

      case Remove(figure) =>
        val size = figures.size
        val obsoleteFigures = figures filter { _.id == figure.id }

        figures --= obsoleteFigures
        size != figures.size
    }

    if (figuresUpdated) {
      val message = write(figures)
      sockets foreach { _ send message }
    }
  }
}
