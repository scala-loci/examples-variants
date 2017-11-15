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
    case WebSocketRemoteActor.UserDisconnected =>
      clients -= sender

    case modification: Modification =>
      removeClosedSockets
      updateInitialPosition(modification)
      updateFigures(modification)
  }

  connectionEstablished addObserver { socket =>
    val client = context actorOf Props(new WebSocketRemoteActor(self, socket))
    clients += client

    client ! InitialPosition(initialPosition)
    client ! Figures(figures)
  }

  def removeClosedSockets = clients foreach {
    _ ! WebSocketRemoteActor.DisconnectClosed
  }

  def updateInitialPosition(modification: Modification) = modification match {
    case Create(figure) =>
      initialPosition =
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
      clients foreach { _ ! Figures(figures) }
  }
}
