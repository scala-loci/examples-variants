package shapes

import util._

import rescala._

import loci._
import loci.transmitter.rescala._
import loci.serializer.upickle._

import scala.util.Random

@multitier
object Application {
  trait Server extends Peer { type Tie <: Multiple[Client] }
  trait Client extends Peer { type Tie <: Single[Server] }

  val ui = placed[Client].local { implicit! => new UI }

  val figureChanged = placed[Client] { implicit! =>
    val figureTransformed =
      ui.figureTransformed map { case (position, transformation) =>
        ui.selectedFigure.readValueOnce map {
          _.copy(position = position, transformation = transformation)
        }
      }

    val figureColorChanged = ui.color.changed map { color =>
      ui.selectedFigure.readValueOnce map { _.copy(color = color) }
    }

    (figureTransformed || figureColorChanged) collect {
      case Some(figure) => figure
    }
  }

  val figureCreated = placed[Client] { implicit! =>
    val rectangleCreated = ui.addRectangle map { _ => Rect(50, 50) }
    val circleCreated = ui.addCircle map { _ => Circle(25) }
    val triangleCreated = ui.addTriangle map { _ => Triangle(50, 50) }

    val transformation = Transformation(1, 1, 0)
    val position = figureInitialPosition.asLocal withDefault Position(0, 0)

    (rectangleCreated || circleCreated || triangleCreated) map { shape =>
      val id = Random.nextInt
      Figure(id, shape, ui.color.readValueOnce, position.readValueOnce, transformation)
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

  val figureRemoved = placed[Client] { implicit! =>
    Event.dynamic { ui.removeFigure() flatMap { _ => ui.selectedFigure() } }
  }

  placed[Client] { implicit! =>
    ui.figures = figures.asLocal
    ui.changeColor = ui.figureSelected map { _.color }
  }

  val figures = placed[Server] { implicit! =>
    Events.foldAll(List.empty[Figure])(figures => Events.Match(
      figureCreated.asLocalFromAllSeq >> { case (_, figure) =>
        figure :: figures
      },
      figureChanged.asLocalFromAllSeq >> { case (_, figure) =>
        val cleaned = figures filterNot { _.id == figure.id }
        if (figures.size == cleaned.size)
          cleaned
        else
          figure :: cleaned
      },
      figureRemoved.asLocalFromAllSeq >> { case (_, figure) =>
        figures filterNot { _.id == figure.id }
      }
    ))
  }
}
