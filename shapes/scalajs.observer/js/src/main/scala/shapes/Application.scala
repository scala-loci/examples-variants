package shapes

import util._

import upickle.default._

import org.scalajs.dom

import scala.util.Random

class Application {
  val socket = new dom.WebSocket("ws://localhost:8080")

  var figureInitialPosition = Position(0, 0)

  val ui = new UI

  def sendServer(modification: Modification): Unit = {
    if (socket.readyState == dom.WebSocket.OPEN)
      socket send write(modification) // #REMOTE-SEND
    else
      socket addEventListener ("open", { _: dom.Event => sendServer(modification) })
  }

  socket.onmessage = { event: dom.MessageEvent => // #REMOTE-RECV #CB
    read[Update](event.data.toString) match {
      case InitialPosition(position) =>
        figureInitialPosition = position // #IMP-STATE
      case Figures(figures) =>
        ui updateFigures figures // #IMP-STATE
    }
  }

  ui.figureTransformed addObserver { // #CB
    case (position, transformation) =>
      ui.selectedFigure.get foreach { selectedFigure =>
        sendServer(Change(selectedFigure.copy(
          position = position, transformation = transformation)))
      }
  }

  ui.color addObserver { color => // #CB
    ui.selectedFigure.get foreach { selectedFigure =>
      if (selectedFigure.color != color)
        sendServer(Change(selectedFigure.copy(color = color)))
    }
  }

  ui.selectedFigure addObserver { // #CB
    case Some(selectedFigure) =>
      ui updateColor selectedFigure.color // #IMP-STATE
    case _ =>
  }

  ui.addRectangle addObserver { _ => createFigure(Rect(50, 50)) }    // #CB
  ui.addCircle addObserver { _ => createFigure(Circle(25)) }         // #CB
  ui.addTriangle addObserver { _ => createFigure(Triangle(50, 50)) } // #CB

  def createFigure(shape: Shape) = {
    val transformation = Transformation(1, 1, 0)
    val id = Random.nextInt

    sendServer(
      Create(Figure(id, shape, ui.color.get, figureInitialPosition, transformation)))
  }

  ui.removeFigure addObserver { _ => // #CB
    ui.selectedFigure.get foreach { selectedFigure =>
      sendServer(Remove(selectedFigure))
    }
  }
}
