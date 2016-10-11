package shapes
package common

import util._

import scala.scalajs.js.Dynamic
import scala.scalajs.js.Dynamic.global
import scala.scalajs.js.Dynamic.literal
import scala.scalajs.js.Dynamic.newInstance

class UI(
    colorChanged: String => Unit,
    selectedFigureChanged: Option[Figure] => Unit,
    figureSelected: Figure => Unit,
    figureTransformed: ((Position, Transformation)) => Unit,
    addRectangle: () => Unit,
    addCircle: () => Unit,
    addTriangle: () => Unit,
    removeFigure: () => Unit) {
  private var selectedFigure = Option.empty[Figure]

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


  private def render(figures: Seq[Figure]) = {
    val ids = (figures map { _.id }).toSet
    val map = collection.mutable.Map.empty[Int, Dynamic]

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
      figureTransformed((position, transformation))
  }

  private def selectionChanged(options: Dynamic) = {
    val figure = options.target.figure.asInstanceOf[Figure]
    selectedFigure = Some(figure)
    selectedFigureChanged(selectedFigure)
    figureSelected(figure)
  }

  private def selectionCleared(options: Dynamic) = {
    selectedFigure = None
    selectedFigureChanged(selectedFigure)
  }


  global $ { () =>
    (global $ global) keyup { event: Dynamic =>
      if (event.keyCode == 46)
        removeFigure()
    }


    global.ui.colorpicker on ("changeColor", { event: Dynamic =>
      colorChanged(event.color.applyDynamic("toString")("hsla").toString())
    })

    colorChanged((global.ui.colorpicker colorpicker "getValue").toString())


    global.ui.addRectangle on ("click", () => addRectangle())
    global.ui.addCircle on ("click", () => addCircle())
    global.ui.addTriangle on ("click", () => addTriangle())


    global.ui.canvas.renderOnAddRemove = false

    global.ui.canvas on literal(
      "object:modified" -> objectsModified _,
      "object:selected" -> selectionChanged _,
      "selection:cleared" -> selectionCleared _
    )
  }


  def updateColor(color: String): Unit = global $ { () =>
    global.ui.colorpicker colorpicker ("setValue", color)
  }

  def updateFigures(figures: Seq[Figure]): Unit = global $ { () =>
    selectedFigure = selectedFigure match {
      case Some(selectedFigure) =>
        figures collectFirst {
          case figure if figure.id == selectedFigure.id => figure
        }
      case _ =>
        None
    }

    selectedFigureChanged(selectedFigure)

    render(figures)
  }
}
