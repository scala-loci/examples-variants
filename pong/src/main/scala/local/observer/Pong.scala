package local
package observer

import common._
import common.observer._

object Pong extends App {
  val ball = Observable(initPosition)

  tick addObserver { _ => ball set (ball.get + speed.get) } // #CB #IMP-STATE

  val areas = Observable(List.empty[Area])

  UI.mousePosition addObserver { pos => // #CB
    updateAreas(Seq(pos.y, ball.get.y))
  }
  ball addObserver { ball => // #CB
    updateAreas(Seq(UI.mousePosition.get.y, ball.y))
  }

  def updateAreas(racketY: Seq[Int]) = {
    val leftRacket = Racket(leftRacketPos, racketY(0))
    val rightRacket = Racket(rightRacketPos, racketY(1))
    val rackets = List(leftRacket, rightRacket)
    areas set (rackets map { _.area.get }) // #IMP-STATE
  }

  ball addObserver { ball => // #CB
    if (ball.x < 0) leftWall
    if (ball.x > maxX) rightWall
    if (ball.y < 0 || ball.y > maxY) yBounce
  }

  def leftWall() = {
    rightPoints set (rightPoints.get + 1) // #IMP-STATE
    xBounce
  }

  def rightWall() = {
    leftPoints set (leftPoints.get + 1) // #IMP-STATE
    xBounce
  }

  ball addObserver { checkBallInRacket(areas.get, _) } // #CB

  def checkBallInRacket(areas: List[Area], ball: Point) =
    if (areas exists { _ contains ball })
      xBounce

  def xBounce() = speed set Point(-speed.get.x, speed.get.y) // #IMP-STATE
  def yBounce() = speed set Point(speed.get.x, -speed.get.y) // #IMP-STATE

  val speed = Observable(initSpeed)

  val leftPoints = Observable(0)
  val rightPoints = Observable(0)

  leftPoints addObserver { updateScore(_, rightPoints.get) } // #CB
  rightPoints addObserver { updateScore(leftPoints.get, _) } // #CB

  def updateScore(leftPoints: Int, rightPoints: Int) = {
    score set (leftPoints + " : " + rightPoints) // #IMP-STATE
  }

  val score = Observable("0 : 0")

  val ui = new UI(areas.get, ball.get, score.get)

  areas addObserver { ui updateAreas _ } // #CB #IMP-STATE
  ball addObserver { ui updateBall _ }   // #CB #IMP-STATE
  score addObserver { ui updateScore _ } // #CB #IMP-STATE

  tickStart
}
