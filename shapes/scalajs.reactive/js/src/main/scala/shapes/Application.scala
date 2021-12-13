package shapes

import util._

import rescala.default._
import upickle.default._

import org.scalajs.dom

import scala.util.Random

class Application {
  val socket = new dom.WebSocket("ws://localhost:8080")

  var figureInitialPosition = Position(0, 0)

  val figures = Var.empty[Seq[Figure]]

  val ui = new UI

  def sendServer(modification: Modification): Unit = {
    if (socket.readyState == dom.WebSocket.OPEN)
      socket send write(modification)
    else
      socket.addEventListener("open", { _: dom.Event => sendServer(modification) })
  }

  socket.onmessage = { event: dom.MessageEvent =>
    read[Update](event.data.toString) match {
      case message @ InitialPosition(_) =>
        figureInitialPosition = message.position
      case message @ Figures(_) =>
        figures.set(message.figures)
    }
  }

  val figureTransformed =
    ui.figureTransformed map { case (position, transformation) =>
      ui.selectedFigure() map {
        _.copy(position = position, transformation = transformation)
      }
    }

  val figureColorChanged = ui.color.changed map { color =>
    ui.selectedFigure() map { _.copy(color = color) }
  }

  val figureCreated = {
    val rectangleCreated = ui.addRectangle map { _ => Rect(50, 50) }
    val circleCreated = ui.addCircle map { _ => Circle(25) }
    val triangleCreated = ui.addTriangle map { _ => Triangle(50, 50) }

    val transformation = Transformation(1, 1, 0)

    (rectangleCreated || circleCreated || triangleCreated) map { shape =>
      val id = Random.nextInt()
      Figure(id, shape, ui.color(), figureInitialPosition, transformation)
    }
  }

  val figureRemoved = Event { ui.removeFigure() flatMap { _ => ui.selectedFigure() } }

  ((figureTransformed collect { case Some(figure) => Change(figure) }) ||
   (figureColorChanged collect { case Some(figure) => Change(figure) }) ||
   (figureCreated map Create) ||
   (figureRemoved map Remove)) observe { sendServer(_) }

  ui.figures = figures
  ui.changeColor = ui.figureSelected map { _.color }
}
