package shapes

import util._
import util.shapes._

import retier._
import retier.architectures.MultiClientServer._
import retier.basicTransmitter._
import retier.serializable.upickle._

import scala.util.Random
import scala.concurrent.ExecutionContext.Implicits.global


@multitier
object Application {
  trait Server extends ServerPeer[Client]
  trait Client extends ClientPeer[Server]

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
  }

  placed[Client] { implicit! =>
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
  }

  placed[Client] { implicit! =>
    ui.removeFigure addObserver { _ =>
      ui.selectedFigure.get foreach { selectedFigure =>
        remote call figureRemoved(selectedFigure)
      }
    }
  }

  var figures: List[Figure] on Server = List.empty[Figure]

  var figureInitialPosition: Position on Server = Position(60, 60)

  def figureCreated(figure: Figure) = placed[Server] { implicit! =>
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

    figures :+= figure
    remote call figuresChanged(figures)
  }

  def figureChanged(figure: Figure) = placed[Server] { implicit! =>
    val cleaned = figures filterNot { _.id == figure.id }
    figures =
      if (figures.size == cleaned.size)
        cleaned
      else
        cleaned :+ figure

    remote call figuresChanged(figures)
  }

  def figureRemoved(figure: Figure) = placed[Server] { implicit! =>
    figures = figures filterNot { _.id == figure.id }
    remote call figuresChanged(figures)
  }

  def figuresChanged(figures: List[Figure]) = placed[Client] { implicit! =>
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
