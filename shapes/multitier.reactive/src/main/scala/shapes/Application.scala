package shapes

import util.UI
import util.shapes._

import rescala._

import retier._
import retier.architectures.MultiClientServer._
import retier.rescalaTransmitter._
import retier.serializable.upickle._

import scala.util.Random

@multitier
object Application {
  trait Server extends ServerPeer[Client]
  trait Client extends ClientPeer[Server]

  val ui = placed[Client].local { implicit! => new UI }

  val figureChanged = placed[Client] { implicit! =>
    val figureTransformed =
      ui.figureTransformed map { case (position, transformation) =>
        ui.selectedFigure.now map {
          _.copy(position = position, transformation = transformation)
        }
      }

    val figureColorChanged = ui.color.changed map { color =>
      ui.selectedFigure.now map { _.copy(color = color) }
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
      Figure(id, shape, ui.color.now, position.now, transformation)
    }
  }

  val figureInitialPosition: Signal[Position] on Server = placed { implicit! =>
    val initialOffset = 60
    val offset = 20
    val max = Position(200, 400)

    figureCreated.asLocalSeq.fold(Position(60, 60)) { (pos, _) =>
      if (pos.x > max.x && pos.y > max.y)
        Position(initialOffset, initialOffset)
      else if (pos.x > max.x)
        Position(initialOffset, pos.y - max.x + initialOffset)
      else
        Position(pos.x + offset, pos.y + offset)
    }
  }

  val figureRemoved = placed[Client] { implicit! =>
    Event { ui.removeFigure() flatMap { _ => ui.selectedFigure() } }
  }

  placed[Client] { implicit! =>
    ui.figures = figures.asLocal
    ui.changeColor = ui.figureSelected map { _.color }
  }

  val figures = placed[Server] { implicit! =>
    sealed trait Modification
    case class Create(figure: Figure) extends Modification
    case class Change(figure: Figure) extends Modification
    case class Remove(figure: Figure) extends Modification

    val modified =
      (figureCreated.asLocalSeq map { case (_, figure) => Create(figure) }) ||
      (figureChanged.asLocalSeq map { case (_, figure) => Change(figure) }) ||
      (figureRemoved.asLocalSeq map { case (_, figure) => Remove(figure) })

    modified.fold(List.empty[Figure]) {
      case (figures, Create(figure)) =>
        figures :+ figure

      case (figures, Change(figure)) =>
        val cleaned = figures filterNot { _.id == figure.id }
        if (figures.size == cleaned.size)
          cleaned
        else
          cleaned :+ figure

      case (figures, Remove(figure)) =>
        figures filterNot { _.id == figure.id }
    }
  }
}
