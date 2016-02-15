package shapes
package util

import rescala.Var
import rescala.Signal
import rescala.events.Event
import rescala.events.ImperativeEvent
import rescala.events.emptyevent
import makro.SignalMacro.{SignalM => Signal}

import scala.scalajs.js.Array
import scala.scalajs.js.Function1
import scala.scalajs.js.Dynamic
import scala.scalajs.js.Dynamic.global
import scala.scalajs.js.Dynamic.literal
import scala.scalajs.js.Dynamic.newInstance

class UI {
  private var _figures: Signal[List[Figure]] = Signal { List.empty[Figure] }
  private var _changeColor: Event[String] = emptyevent

  private val colorVar = Var("#000000")
  private val selectedFigureVar = Var(Option.empty[Figure])
  private val figureSelectedEvent = new ImperativeEvent[Figure]
  private val figureTransformedEvent = new ImperativeEvent[(Position, Transformation)]
  private val addRectangleEvent = new ImperativeEvent[Unit]
  private val addCircleEvent = new ImperativeEvent[Unit]
  private val addTriangleEvent = new ImperativeEvent[Unit]
  private val removeFigureEvent = new ImperativeEvent[Unit]

  val color: Signal[String] = colorVar
  val selectedFigure: Signal[Option[Figure]] = selectedFigureVar
  val figureSelected: Event[Figure] = figureSelectedEvent
  val figureTransformed: Event[(Position, Transformation)] = figureTransformedEvent
  val addRectangle: Event[Unit] = addRectangleEvent
  val addCircle: Event[Unit] = addCircleEvent
  val addTriangle: Event[Unit] = addTriangleEvent
  val removeFigure: Event[Unit] = removeFigureEvent

  private def applyGeneralFigureProperties(figure: Figure, obj: Dynamic) = {
    obj set literal(
      originX = "center",
      originY = "center",
      fill = figure.color,
      left = figure.position.x,
      top = figure.position.y,
      scaleX = figure.transformation.scaleX,
      scaleY = figure.transformation.scaleY,
      angle = figure.transformation.angle)
  }

  private def applyFigureProperties(figure: Figure, obj: Dynamic) = {
    figure.shape match {
      case Rect(width, height) =>
        obj set literal(width = width, height = height)
      case Circle(radius) =>
        obj set literal(radius = radius)
      case Triangle(width, height) =>
        obj set literal(width = width, height = height)
    }

    applyGeneralFigureProperties(figure, obj)
    obj.setCoords()
  }


  private def render(figures: List[Figure]) = {
    val ids = (figures map { _.id }).toSet[Long]
    val map = collection.mutable.Map.empty[Long, Dynamic]

    global.ui.canvas forEachObject { obj: Dynamic =>
      val figure = obj.figure.asInstanceOf[Figure]
      if (ids contains figure.id)
        map += figure.id -> obj
      else
        global.ui.canvas remove obj
    }

    figures foreach { figure =>
      (map get figure.id) match {
        case Some(obj) =>
          if (obj.figure.asInstanceOf[Figure] != figure) {
            applyFigureProperties(figure, obj)
            obj.figure = figure.asInstanceOf[Dynamic]
          }

        case _ =>
          val obj = figure.shape match {
            case Rect(_, _) => newInstance(global.fabric.Rect)()
            case Circle(_) => newInstance(global.fabric.Circle)()
            case Triangle(_, _) => newInstance(global.fabric.Triangle)()
          }

          applyFigureProperties(figure, obj)
          obj.figure = figure.asInstanceOf[Dynamic]

          global.ui.canvas add obj
      }
    }

    global.ui.canvas.renderAll()
  }

  private def createPosition(obj: Dynamic) =
    Position(
      obj.left.asInstanceOf[Double],
      obj.top.asInstanceOf[Double])

  private def createTransformation(obj: Dynamic) =
    Transformation(
      obj.scaleX.asInstanceOf[Double],
      obj.scaleY.asInstanceOf[Double],
      obj.angle.asInstanceOf[Double])

  private def objectsModified(options: Dynamic) = {
    val obj = options.target
    val figure = obj.figure.asInstanceOf[Figure]

    val position = createPosition(obj)
    val transformation = createTransformation(obj)

    if (figure.position != position || figure.transformation != transformation)
      figureTransformedEvent((position, transformation))
  }

  private def selectionChanged(options: Dynamic) = {
    val figure = options.target.figure.asInstanceOf[Figure]
    selectedFigureVar() = Some(figure)
    figureSelectedEvent(figure)
  }

  private def selectionCleared(options: Dynamic) =
    selectedFigureVar() = None


  global $ { () =>
    (global $ global) keyup { event: Dynamic =>
      if (event.keyCode == 46)
        removeFigureEvent(())
    }


    global.ui.colorpicker on ("changeColor", { event: Dynamic =>
      colorVar() = event.color.toHex().toString()
    })

    colorVar() = (global.ui.colorpicker colorpicker "getValue").toString()


    global.ui.addRectangle on ("click", () => addRectangleEvent(()))
    global.ui.addCircle on ("click", () => addCircleEvent(()))
    global.ui.addTriangle on ("click", () => addTriangleEvent(()))


    global.ui.canvas.renderOnAddRemove = false

    global.ui.canvas on literal(
      "object:modified" -> objectsModified _,
      "object:selected" -> selectionChanged _,
      "selection:cleared" -> selectionCleared _
    )
  }


  def changeColor = _changeColor

  def changeColor_=(changeColor: Event[String]) = global $ { () =>
    _changeColor = changeColor
    _changeColor += { global.ui.colorpicker colorpicker ("setValue", _) }
  }


  def figures = _figures
  
  def figures_=(figures: Signal[List[Figure]]) = global $ { () =>
    _figures = figures

    figures.changed += { figures =>
      selectedFigureVar() = selectedFigureVar.get match {
        case Some(selectedFigure) =>
          figures collectFirst {
            case figure if figure.id == selectedFigure.id => figure
          }
        case _ =>
          None
      }

      render(figures)
    }

    render(figures.get)
  }
}
