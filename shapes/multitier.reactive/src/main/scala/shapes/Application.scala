package shapes

import util._

import loci.language._
import loci.language.transmitter.rescala._
import loci.serializer.upickle._

import rescala.default._

import scala.util.Random

@multitier object Application {
  @peer type Server <: { type Tie <: Multiple[Client] }
  @peer type Client <: { type Tie <: Single[Server] }

  val ui = on[Client] local { implicit! => new UI }

  val figureChanged = on[Client] { implicit! =>
    val figureTransformed =
      ui.figureTransformed map { case (position, transformation) =>
        ui.selectedFigure() map {
          _.copy(position = position, transformation = transformation)
        }
      }

    val figureColorChanged = ui.color.changed map { color =>
      ui.selectedFigure() map { _.copy(color = color) }
    }

    (figureTransformed || figureColorChanged) collect {
      case Some(figure) => figure
    }
  }

  val figureCreated = on[Client] { implicit! =>
    val rectangleCreated = ui.addRectangle map { _ => Rect(50, 50) }
    val circleCreated = ui.addCircle map { _ => Circle(25) }
    val triangleCreated = ui.addTriangle map { _ => Triangle(50, 50) }

    val transformation = Transformation(1, 1, 0)
    val position = figureInitialPosition.asLocal withDefault Position(0, 0)

    (rectangleCreated || circleCreated || triangleCreated) map { shape =>
      val id = Random.nextInt()
      Figure(id, shape, ui.color(), position(), transformation)
    }
  }

  val figureInitialPosition: Signal[Position] on Server = placed { implicit! =>
    val initialOffset = 60
    val offset = 20
    val max = Position(200, 400)

    figureCreated.asLocalFromAllSeq.fold(Position(60, 60)) { (pos, _) =>
      if (pos.x > max.x && pos.y > max.y)
        Position(initialOffset, initialOffset)
      else if (pos.x > max.x)
        Position(initialOffset, pos.y - max.x + initialOffset)
      else
        Position(pos.x + offset, pos.y + offset)
    }
  }

  val figureRemoved = on[Client] { implicit! =>
    (ui.removeFigure map { _ => ui.selectedFigure() }).flatten
  }

  on[Client] { implicit! =>
    ui.figures = figures.asLocal
    ui.changeColor = ui.figureSelected map { _.color }
  }

  val figures = on[Server] { implicit! =>
    Events.foldAll(List.empty[Figure])(figures => Seq(
      figureCreated.asLocalFromAllSeq act { case (_, figure) =>
        figure :: figures
      },
      figureChanged.asLocalFromAllSeq act { case (_, figure) =>
        val cleaned = figures filterNot { _.id == figure.id }
        if (figures.size == cleaned.size)
          cleaned
        else
          figure :: cleaned
      },
      figureRemoved.asLocalFromAllSeq act { case (_, figure) =>
        figures filterNot { _.id == figure.id }
      }
    ))
  }
}
