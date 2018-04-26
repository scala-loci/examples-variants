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

  connectionEstablished addObserver { socket => // #CB
    sockets += socket // #IMP-STATE

    socket.received addObserver received // #REMOTE-RECV #CB
    socket.closed addObserver { _ => sockets -= socket } // #CB #IMP-STATE

    socket send write[Update](InitialPosition(initialPosition)) // #REMOTE-SEND
    socket send write[Update](Figures(figures)) // #REMOTE-SEND
  }

  def received(message: String) = {
    removeClosedSockets

    val modification = read[Modification](message)
    updateInitialPosition(modification)
    updateFigures(modification)
  }

  def removeClosedSockets() =
    sockets --= sockets filterNot { _.isOpen } // #IMP-STATE

  def updateInitialPosition(modification: Modification) = modification match {
    case Create(figure) =>
      initialPosition = // #IMP-STATE
        if (initialPosition.x > max.x && initialPosition.y > max.y)
          Position(initialOffset, initialOffset)
        else if (initialPosition.x > max.x)
          Position(initialOffset, initialPosition.y - max.x + initialOffset)
        else
          Position(initialPosition.x + offset, initialPosition.y + offset)

      sockets foreach { _ send write[Update](InitialPosition(initialPosition)) } // #REMOTE-SEND

    case _ =>
  }

  def updateFigures(modification: Modification) = {
    val figuresUpdated = modification match {
      case Create(figure) =>
        figures ::= figure // #IMP-STATE
        true

      case Change(figure) =>
        val cleaned = figures filterNot { _.id == figure.id }
        val updated = figures.size != cleaned.size
        figures = // #IMP-STATE
          if (updated)
            figure :: cleaned
          else
            cleaned

        updated

      case Remove(figure) =>
        val cleaned = figures filterNot { _.id == figure.id }
        val updated = figures.size != cleaned.size
        figures = cleaned // #IMP-STATE

        updated
    }

    if (figuresUpdated)
      sockets foreach { _ send write[Update](Figures(figures)) } // #REMOTE-SEND
  }
}
