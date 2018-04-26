package shapes

import util._

import akka.actor._

import upickle.default._

import scala.util.Random
import scala.collection.mutable.ListBuffer

class Application(connectionEstablished: Observable[WebSocket]) extends Actor {
  val clients = ListBuffer.empty[ActorRef]
  var figures = List.empty[Figure]

  val initialOffset = 60
  val offset = 20
  val max = Position(200, 400)
  var initialPosition = Position(60, 60)

  def receive = {
    case WebSocketRemoteActor.UserDisconnected => // #CB
      clients -= sender // #CB #IMP-STATE

    case modification: Modification => // #CB
      removeClosedSockets
      updateInitialPosition(modification)
      updateFigures(modification)
  }

  connectionEstablished addObserver { socket => // #CB
    val client = context actorOf Props(new WebSocketRemoteActor(self, socket))
    clients += client // #IMP-STATE

    client ! InitialPosition(initialPosition)
    client ! Figures(figures)
  }

  def removeClosedSockets() = clients foreach {
    _ ! WebSocketRemoteActor.DisconnectClosed
  }

  def updateInitialPosition(modification: Modification) = modification match {
    case Create(figure) =>
      initialPosition = // #IMP-STATE
        if (initialPosition.x > max.x && initialPosition.y > max.y)
          Position(initialOffset, initialOffset)
        else if (initialPosition.x > max.x)
          Position(initialOffset, initialPosition.y - max.x + initialOffset)
        else
          Position(initialPosition.x + offset, initialPosition.y + offset)

      clients foreach { _ ! InitialPosition(initialPosition) }

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
      clients foreach { _ ! Figures(figures) }
  }
}
