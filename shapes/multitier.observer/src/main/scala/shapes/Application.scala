package shapes

import util._

import loci._
import loci.basicTransmitter._
import loci.serializable.upickle._

import scala.util.Random
import scala.concurrent.ExecutionContext.Implicits.global

@multitier
object Application {
  trait Server extends Peer { type Tie <: Multiple[Client] }
  trait Client extends Peer { type Tie <: Single[Server] }

  val ui = placed[Client].local { implicit! => new UI }

  placed[Client] { implicit! =>
    ui.figureTransformed addObserver { // #CB
      case (position, transformation) =>
        ui.selectedFigure.get foreach { selectedFigure =>
          remote call figureChanged(selectedFigure.copy( // #REMOTE
            position = position, transformation = transformation))
        }
    }

    ui.color addObserver { color => // #CB
      ui.selectedFigure.get foreach { selectedFigure =>
        if (selectedFigure.color != color)
          remote call figureChanged(selectedFigure.copy(color = color)) // #REMOTE
      }
    }

    ui.addRectangle addObserver { _ => createFigure(Rect(50, 50)) }    // #CB
    ui.addCircle addObserver { _ => createFigure(Circle(25)) }         // #CB
    ui.addTriangle addObserver { _ => createFigure(Triangle(50, 50)) } // #CB

    def createFigure(shape: Shape) = {
      figureInitialPosition.asLocal foreach { position => // #REMOTE #CROSS-COMP
        val transformation = Transformation(1, 1, 0)
        val id = Random.nextInt

        remote call figureCreated( // #REMOTE
          Figure(id, shape, ui.color.get, position, transformation))
      }
    }

    ui.removeFigure addObserver { _ => // #CB
      ui.selectedFigure.get foreach { selectedFigure =>
        remote call figureRemoved(selectedFigure) // #REMOTE
      }
    }
  }

  var figures: List[Figure] on Server = List.empty[Figure]

  var figureInitialPosition: Position on Server = Position(60, 60)

  def figureCreated(figure: Figure): Unit on Server = placed { implicit! =>
    val initialOffset = 60
    val offset = 20
    val max = Position(200, 400)

    figureInitialPosition = // #IMP-STATE
      if (figureInitialPosition.x > max.x && figureInitialPosition.y > max.y)
        Position(initialOffset, initialOffset)
      else if (figureInitialPosition.x > max.x)
        Position(initialOffset, figureInitialPosition.y - max.x + initialOffset)
      else
        Position(figureInitialPosition.x + offset, figureInitialPosition.y + offset)

    figures ::= figure // #IMP-STATE
    remote call figuresChanged(figures) // #REMOTE
  }

  def figureChanged(figure: Figure): Unit on Server = placed { implicit! =>
    val cleaned = figures filterNot { _.id == figure.id }
    val updated = figures.size != cleaned.size
    figures = // #IMP-STATE
      if (figures.size == cleaned.size)
        cleaned
      else
        figure :: cleaned

    if (updated)
      remote call figuresChanged(figures) // #REMOTE
  }

  def figureRemoved(figure: Figure): Unit on Server = placed { implicit! =>
    val cleaned = figures filterNot { _.id == figure.id }
    val updated = figures.size != cleaned.size
    figures = cleaned // #IMP-STATE

    if (updated)
      remote call figuresChanged(figures) // #REMOTE
  }

  def figuresChanged(figures: List[Figure]): Unit on Client = placed { implicit! =>
    ui updateFigures figures // #IMP-STATE
  }

  placed[Client] { implicit! =>
    figures.asLocal foreach { ui updateFigures _ } // #REMOTE #CROSS-COMP #IMP-STATE

    ui.selectedFigure addObserver { // #CB
      case Some(selectedFigure) =>
        ui updateColor selectedFigure.color // #IMP-STATE
      case _ =>
    }
  }
}
