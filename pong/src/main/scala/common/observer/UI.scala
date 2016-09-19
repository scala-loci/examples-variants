package common
package observer

import scala.swing.Swing
import scala.swing.Reactions.Reaction
import scala.swing.event.MouseMoved
import scala.swing.event.MouseDragged
import java.awt.MouseInfo
import java.awt.Robot

object UI {
  trait FrontEnd extends FrontEndHolder {
    def createFrontEnd(areas: List[Area], ball: Point, score: String) =
      new UI(areas, ball, score)

    def createFrontEnd = new UI

    lazy val mousePosition = UI.mousePosition
  }

  val react: Reaction =  {
    case e: MouseMoved =>
      val point = Point(e.point.x, e.point.y)
      if (currentMousePosition.get != point)
        currentMousePosition set point
    case e: MouseDragged =>
      val point = Point(e.point.x, e.point.y)
      if (currentMousePosition.get != point)
        currentMousePosition set point
  }

  val currentMousePosition = Observable(Point(0, 0))

  tick addObserver { _ =>
    if (mousePosition.get != currentMousePosition.get)
      mousePosition set currentMousePosition.get
  }

  val mousePosition = Observable(Point(0, 0))

  // hack to update the Swing interface without animation stuttering
  val robot = new Robot
  tick addObserver { _ =>
    val p = MouseInfo.getPointerInfo.getLocation
    robot.mouseMove(p.x, p.y)
  }
}

class UI(
    areas: List[Area],
    ball: Point,
    score: String) extends FrontEnd {
  def this() = this(List.empty, Point(0, 0), "")

  val window = {
    val window = new Window(areas, ball, score)
    window.panel.listenTo(window.panel.mouse.moves, window.panel.mouse.clicks)
    window.panel.reactions += UI.react

    tick addObserver { _ => window.frame.repaint }

    window
  }

  def updateAreas(areas: List[Area]) = Swing onEDT { window.areas = areas }
  def updateBall(ball: Point) = Swing onEDT { window.ball = ball }
  def updateScore(score: String) = Swing onEDT { window.score = score }

  Swing onEDT {
    window.frame.visible = true
    tickStart
  }
}
