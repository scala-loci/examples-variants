package common
package reactive

import rescala._

import scala.swing.Swing
import scala.swing.Reactions.Reaction
import scala.swing.event.MouseMoved
import scala.swing.event.MouseDragged
import java.awt.MouseInfo
import java.awt.Robot

object UI {
  trait FrontEnd extends FrontEndHolder {
    def createFrontEnd(
      areas: Signal[List[Area]],
      ball: Signal[Point],
      score: Signal[String]) = new UI(areas, ball, score)

    lazy val mousePosition = UI.mousePosition
  }

  private val mousePositionChanged = Evt[Point]

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
  tick += { _ => // #CB
    val p = MouseInfo.getPointerInfo.getLocation
    robot.mouseMove(p.x, p.y)
  }
}

class UI(
    areas: Signal[List[Area]],
    ball: Signal[Point],
    score: Signal[String]) extends FrontEnd {
  lazy val window = {
    val window = new Window(
      (areas withDefault List.empty).now,
      (ball withDefault Point(0, 0)).now,
      (score withDefault "").now)
    window.panel.listenTo(window.panel.mouse.moves, window.panel.mouse.clicks)
    window.panel.reactions += UI.react // #CB

    tick += { _ => window.frame.repaint } // #CB

    window
  }

  areas.changed += { areas => Swing onEDT { window.areas = areas } } // #CB
  ball.changed += { ball => Swing onEDT { window.ball = ball } } // #CB
  score.changed += { score => Swing onEDT { window.score = score } } // #CB

  Swing onEDT {
    window.frame.visible = true
    tickStart
  }
}
