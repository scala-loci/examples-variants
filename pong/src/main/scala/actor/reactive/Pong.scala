package actor
package reactive

import common._
import common.actor._
import common.reactive._

import rescala.default._

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.ActorSelection
import akka.actor.Props

case object AddPlayer
case class MouseYChanged(y: Int)
case class UpdateAreas(areas: List[Area])
case class UpdateBall(ball: Point)
case class UpdateScore(score: String)

class Server extends Actor {
  def receive = addPlayer orElse mouseYChanged

  val clients = Var(Seq.empty[ActorRef])

  val isPlaying = Signal { clients().size >= 2 }

  val ball: Signal[Point] =
    tick.fold(initPosition) { (ball, _) =>
      if (isPlaying.readValueOnce) ball + speed.readValueOnce else ball
    }

  def addPlayer: Receive = { case AddPlayer =>
    clients transform { _ :+ sender }
  }

  val players = Signal {
    clients() match {
      case left :: right :: _ => Seq(Some(left), Some(right))
      case _ => Seq(None, None)
    }
  }

  val mousePositions = Var(Map.empty[ActorRef, Int])

  def mouseYChanged: Receive = { case MouseYChanged(y) =>
    mousePositions transform { _ + (sender -> y) }
  }

  val areas = {
    val racketY = Signal {
      players() map {
        _ flatMap { mousePositions() get _ } getOrElse initPosition.y }
    }

    val leftRacket = new Racket(leftRacketPos, Signal { racketY()(0) })
    val rightRacket = new Racket(rightRacketPos, Signal { racketY()(1) })

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
    val x = xBounce toggle (Signal { initSpeed.x }, Signal { -initSpeed.x })
    val y = yBounce toggle (Signal { initSpeed.y }, Signal { -initSpeed.y })
    Signal { Point(x(), y()) }
  }

  val score = {
    val leftPlayerPoints = rightWall.iterate(0) { _ + 1 }
    val rightPlayerPoints = leftWall.iterate(0) { _ + 1 }
    Signal { leftPlayerPoints() + " : " + rightPlayerPoints() }
  }

  areas observe { areas => clients.readValueOnce foreach { _ ! UpdateAreas(areas) } }
  ball observe { ball => clients.readValueOnce foreach { _ ! UpdateBall(ball) } }
  score observe { score => clients.readValueOnce foreach { _ ! UpdateScore(score) } }

  clients observe { clients =>
    clients foreach { _ ! UpdateAreas(areas.readValueOnce) }
    clients foreach { _ ! UpdateBall(ball.readValueOnce) }
    clients foreach { _ ! UpdateScore(score.readValueOnce) }
  }

  tickStart
}

abstract class Client(server: ActorSelection) extends Actor with FrontEndHolder {
  val areas = Var(List.empty[Area])
  val ball = Var(Point(0, 0))
  val score = Var("0 : 0")

  mousePosition observe { pos =>
    server ! MouseYChanged(pos.y)
  }

  val frontEnd = createFrontEnd(areas, ball, score)

  def receive = {
    case UpdateAreas(areas) => this.areas set areas
    case UpdateBall(ball) => this.ball set ball
    case UpdateScore(score) => this.score set score
  }

  server ! AddPlayer
}

object PongServer extends App {
  val system = ActorSystem("server-system", remoteConfig("localhost", 1099))
  system.actorOf(Props(new Server), "server")
}

object PongClient extends App {
  val system = ActorSystem("client-system", remoteConfig("localhost", 0))
  val server = system actorSelection "akka.tcp://server-system@localhost:1099/user/server"
  system.actorOf(Props(new Client(server) with UI.FrontEnd))
}

object PongClientBenchmark extends App {
  val system = ActorSystem("client-system", remoteConfig("localhost", 0))
  val server = system actorSelection "akka.tcp://server-system@localhost:1099/user/server"
  system.actorOf(Props(new Client(server) with Benchmark.FrontEnd {
    def arguments = args
  }))
}
