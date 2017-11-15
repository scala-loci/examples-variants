package shapes

import util._

import akka.actor._

import upickle.default._

import org.scalajs.dom

import scala.util.Random

class Application extends Actor {
  val server = context actorOf Props(
    new WebSocketRemoteActor(self, "ws://localhost:8080"))

  var figureInitialPosition = Position(0, 0)

  val ui = new UI

  def receive = {
    case InitialPosition(position) =>
      figureInitialPosition = position
    case Figures(figures) =>
      ui updateFigures figures
  }

  ui.figureTransformed addObserver {
    case (position, transformation) =>
      ui.selectedFigure.get foreach { selectedFigure =>
        server ! Change(selectedFigure.copy(
          position = position, transformation = transformation))
      }
  }

  ui.color addObserver { color =>
    ui.selectedFigure.get foreach { selectedFigure =>
      if (selectedFigure.color != color)
        server ! Change(selectedFigure.copy(color = color))
    }
  }

  ui.selectedFigure addObserver {
    case Some(selectedFigure) =>
      ui updateColor selectedFigure.color
    case _ =>
  }

  ui.addRectangle addObserver { _ => createFigure(Rect(50, 50)) }
  ui.addCircle addObserver { _ => createFigure(Circle(25)) }
  ui.addTriangle addObserver { _ => createFigure(Triangle(50, 50)) }

  def createFigure(shape: Shape) = {
    val transformation = Transformation(1, 1, 0)
    val id = Random.nextInt

    println("created: " + shape)

    server !
      Create(Figure(id, shape, ui.color.get, figureInitialPosition, transformation))
  }

  ui.removeFigure addObserver { _ =>
    ui.selectedFigure.get foreach { selectedFigure =>
      server ! Remove(selectedFigure)
    }
  }
}
