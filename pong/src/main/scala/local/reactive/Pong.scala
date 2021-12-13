package local
package reactive

import common._
import common.reactive._

import rescala.default._

object Pong extends App {
  val ball: Signal[Point] = tick.fold(initPosition) { (ball, _) =>
    ball + speed.readValueOnce
  }

  val areas = {
    val racketY = Seq(
      Signal { UI.mousePosition().y },
      Signal { ball().y })
    val leftRacket = new Racket(leftRacketPos, racketY(0))
    val rightRacket = new Racket(rightRacketPos, racketY(1))
    val rackets = List(leftRacket, rightRacket)
    Signal.dynamic { rackets map { _.area() } }
  }

  val leftWall = ball.changed && { _.x < 0 }
  val rightWall = ball.changed && { _.x > maxX }

  val xBounce = {
    val ballInRacket = Signal { areas() exists { _ contains ball() } }
    val collisionRacket = ballInRacket changedTo true
    leftWall || rightWall || collisionRacket
  }

  val yBounce = ball.changed && { ball => ball.y < 0 || ball.y > maxY }

  val speed = {
    val x = xBounce.toggle(Signal { initSpeed.x }, Signal { -initSpeed.x })
    val y = yBounce.toggle(Signal { initSpeed.y }, Signal { -initSpeed.y })
    Signal { Point(x(), y()) }
  }

  val score = {
    val leftPoints = rightWall.iterate(0) { _ + 1 }
    val rightPoints = leftWall.iterate(0) { _ + 1 }
    Signal { s"${leftPoints()} : ${rightPoints()}" }
  }

  val ui = new UI(areas, ball, score)

  tickStart()
}
