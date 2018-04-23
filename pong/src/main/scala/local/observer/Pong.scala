package local
package observer

import common._
import common.observer._

object Pong extends App {
  val ball = Observable(initPosition)

  tick addObserver { _ => ball set (ball.get + speed.get) }

  val areas = Observable(List.empty[Area])

  UI.mousePosition addObserver { pos =>
    updateAreas(Seq(pos.y, ball.get.y))
  }
  ball addObserver { ball =>
    updateAreas(Seq(UI.mousePosition.get.y, ball.y))
  }

  def updateAreas(racketY: Seq[Int]) = {
    val leftRacket = Racket(leftRacketPos, racketY(0))
    val rightRacket = Racket(rightRacketPos, racketY(1))
    val rackets = List(leftRacket, rightRacket)
    areas set (rackets map { _.area.get })
  }

  ball addObserver { ball =>
    if (ball.x < 0) leftWall
    if (ball.x > maxX) rightWall
    if (ball.y < 0 || ball.y > maxY) yBounce
  }

  def leftWall() = {
    rightPoints set (rightPoints.get + 1)
    xBounce
  }

  def rightWall() = {
    leftPoints set (leftPoints.get + 1)
    xBounce
  }

  ball addObserver { checkBallInRacket(areas.get, _) }

  def checkBallInRacket(areas: List[Area], ball: Point) =
    if (areas exists { _ contains ball })
      xBounce

  def xBounce() = speed set Point(-speed.get.x, speed.get.y)
  def yBounce() = speed set Point(speed.get.x, -speed.get.y)

  val speed = Observable(initSpeed)

  val leftPoints = Observable(0)
  val rightPoints = Observable(0)

  leftPoints addObserver { updateScore(_, rightPoints.get) }
  rightPoints addObserver { updateScore(leftPoints.get, _) }

  def updateScore(leftPoints: Int, rightPoints: Int) = {
    score set (leftPoints + " : " + rightPoints)
  }

  val score = Observable("0 : 0")

  val ui = new UI(areas.get, ball.get, score.get)

  areas addObserver { ui updateAreas _ }
  ball addObserver { ui updateBall _ }
  score addObserver { ui updateScore _ }

  tickStart
}
