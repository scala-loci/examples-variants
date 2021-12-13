package common
package reactive

import rescala.default._

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

  private val mousePositionChanged = Evt[Point]()

  val react: Reaction =  {
    case e: MouseMoved =>
      mousePositionChanged.fire(Point(e.point.x, e.point.y))
    case e: MouseDragged =>
      mousePositionChanged.fire(Point(e.point.x, e.point.y))
  }

  val currentMousePosition = mousePositionChanged latest Point(0, 0)

  val mousePosition = tick map { _ => currentMousePosition() } latest Point(0, 0)

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
    score: Signal[String]) extends FrontEnd {
  lazy val window = {
    val window = new Window(
      (areas withDefault List.empty).readValueOnce,
      (ball withDefault Point(0, 0)).readValueOnce,
      (score withDefault "").readValueOnce)
    window.panel.listenTo(window.panel.mouse.moves, window.panel.mouse.clicks)
    window.panel.reactions += UI.react

    tick += { _ => window.frame.repaint() }

    window
  }

  areas.changed += { areas => Swing onEDT { window.areas = areas } }
  ball.changed += { ball => Swing onEDT { window.ball = ball } }
  score.changed += { score => Swing onEDT { window.score = score } }

  Swing onEDT {
    window.frame.visible = true
    tickStart()
  }
}
