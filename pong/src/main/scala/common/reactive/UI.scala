package common
package reactive

import rescala.Signal
import rescala.events.ImperativeEvent
import makro.SignalMacro.{SignalM => Signal}

import scala.swing.Swing
import scala.swing.Reactions.Reaction
import scala.swing.event.MouseMoved
import scala.swing.event.MouseDragged
import java.awt.MouseInfo
import java.awt.Robot

case class Racket(x: Int, y: Signal[Int]) {
  val height = 80
  val width = 10

  val boundedYPos = Signal {
   math.min(maxY - height / 2,
     math.max(height / 2,  y()))
  }

  val area = Signal {
    new Area(
      x - width / 2,
      boundedYPos() - height / 2,
      width,
      height)
  }
}

object UI {
  private val mousePositionChanged = new ImperativeEvent[Point]

  val react: Reaction =  {
    case e: MouseMoved =>
      mousePositionChanged(Point(e.point.x, e.point.y))
    case e: MouseDragged =>
      mousePositionChanged(Point(e.point.x, e.point.y))
  }

  val currentMousePosition = mousePositionChanged latest Point(0, 0)

  val mousePosition = tick snapshot currentMousePosition

  // hack to update the Swing interface without animation stuttering
  val robot = new Robot
  tick += { _ =>
    val p = MouseInfo.getPointerInfo.getLocation
    robot.mouseMove(p.x, p.y)
  }
}

class UI(
    areas: Signal[List[Area]],
    ball: Signal[Point],
    score: Signal[String]) {
  lazy val window = {
    val window = new Window(areas.get, ball.get, score.get)
    window.panel.listenTo(window.panel.mouse.moves, window.panel.mouse.clicks)
    window.panel.reactions += UI.react

    tick += { _ => window.frame.repaint }

    window
  }

  areas.changed += { areas => Swing onEDT { window.areas = areas } }
  ball.changed += { ball => Swing onEDT { window.ball = ball } }
  score.changed += { score => Swing onEDT { window.score = score } }

  Swing onEDT {
    window.frame.visible = true
    tickStart
  }
}
