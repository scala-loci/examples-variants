package shapes

import util._

import loci._
import loci.transmitter.basic._
import loci.serializer.upickle._

import scala.util.Random
import scala.concurrent.ExecutionContext.Implicits.global

@multitier
object Application {
  trait Server extends Peer { type Tie <: Multiple[Client] }
  trait Client extends Peer { type Tie <: Single[Server] }

  val ui = placed[Client].local { implicit! => new UI }

  placed[Client] { implicit! =>
    ui.figureTransformed addObserver {
      case (position, transformation) =>
        ui.selectedFigure.get foreach { selectedFigure =>
          remote call figureChanged(selectedFigure.copy(
            position = position, transformation = transformation))
        }
    }

    ui.color addObserver { color =>
      ui.selectedFigure.get foreach { selectedFigure =>
        if (selectedFigure.color != color)
          remote call figureChanged(selectedFigure.copy(color = color))
      }
    }

    ui.addRectangle addObserver { _ => createFigure(Rect(50, 50)) }
    ui.addCircle addObserver { _ => createFigure(Circle(25)) }
    ui.addTriangle addObserver { _ => createFigure(Triangle(50, 50)) }

    def createFigure(shape: Shape) = {
      figureInitialPosition.asLocal foreach { position =>
        val transformation = Transformation(1, 1, 0)
        val id = Random.nextInt

        remote call figureCreated(
          Figure(id, shape, ui.color.get, position, transformation))
      }
    }

    ui.removeFigure addObserver { _ =>
      ui.selectedFigure.get foreach { selectedFigure =>
        remote call figureRemoved(selectedFigure)
      }
    }
  }

  var figures: List[Figure] on Server = List.empty[Figure]

  var figureInitialPosition: Position on Server = Position(60, 60)

  def figureCreated(figure: Figure): Unit on Server = placed { implicit! =>
    val initialOffset = 60
    val offset = 20
    val max = Position(200, 400)

    figureInitialPosition =
      if (figureInitialPosition.x > max.x && figureInitialPosition.y > max.y)
        Position(initialOffset, initialOffset)
      else if (figureInitialPosition.x > max.x)
        Position(initialOffset, figureInitialPosition.y - max.x + initialOffset)
      else
        Position(figureInitialPosition.x + offset, figureInitialPosition.y + offset)

    figures ::= figure
    remote call figuresChanged(figures)
  }

  def figureChanged(figure: Figure): Unit on Server = placed { implicit! =>
    val cleaned = figures filterNot { _.id == figure.id }
    val updated = figures.size != cleaned.size
    figures =
      if (figures.size == cleaned.size)
        cleaned
      else
        figure :: cleaned

    if (updated)
      remote call figuresChanged(figures)
  }

  def figureRemoved(figure: Figure): Unit on Server = placed { implicit! =>
    val cleaned = figures filterNot { _.id == figure.id }
    val updated = figures.size != cleaned.size
    figures = cleaned

    if (updated)
      remote call figuresChanged(figures)
  }

  def figuresChanged(figures: List[Figure]): Unit on Client = placed { implicit! =>
    ui updateFigures figures
  }

  placed[Client] { implicit! =>
    figures.asLocal foreach { ui updateFigures _ }

    ui.selectedFigure addObserver {
      case Some(selectedFigure) =>
        ui updateColor selectedFigure.color
      case _ =>
    }
  }
}
