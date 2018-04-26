package actor
package observer

import common._
import common.actor._
import common.observer._

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

  val clients = Observable(Seq.empty[ActorRef])

  def isPlaying = clients.get.size >= 2

  val ball = Observable(initPosition)

  tick addObserver { _ => // #CB
    if (isPlaying) ball set (ball.get + speed.get) // #IMP-STATE
  }

  def addPlayer: Receive = { case AddPlayer => // #CB
    clients set (clients.get :+ sender) // #IMP-STATE
  }

  val players = Observable(Seq(Option.empty[ActorRef], Option.empty[ActorRef]))

  clients addObserver { clients => // #CB
    players set (clients match { // #IMP-STATE
      case left :: right :: _ => Seq(Some(left), Some(right))
      case _ => Seq(None, None)
    })
  }

  val mousePositions = Observable(Map.empty[ActorRef, Int])

  def mouseYChanged: Receive = { case MouseYChanged(y) => // #CB
    mousePositions set (mousePositions.get + (sender -> y)) // #IMP-STATE
  }

  val areas = Observable(List.empty[Area])

  players addObserver { players => // #CB
    updateAreas(players map {
      _ flatMap { mousePositions.get get _ }
    })
  }

  mousePositions addObserver { mousePositions => // #CB
    updateAreas(players.get map {
      _ flatMap { mousePositions get _ }
    })
  }

  def updateAreas(mouseY: Seq[Option[Int]]) = {
    val racketY = mouseY map { _ getOrElse initPosition.y }
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

  def checkBallInRacket(areas: List[Area], ball: Point) = {
    if(areas exists { _ contains ball })
      xBounce
  }

  def xBounce() = speed set Point(-speed.get.x, speed.get.y) // #IMP-STATE
  def yBounce() = speed set Point(speed.get.x, -speed.get.y) // #IMP-STATE

  val speed = Observable(initSpeed)

  val leftPoints = Observable(0)
  val rightPoints = Observable(0)

  def updateScore(leftPoints: Int, rightPoints: Int) = {
    score set (leftPoints + " : " + rightPoints) // #IMP-STATE
  }

  val score = Observable("0 : 0")

  areas addObserver { areas => clients.get foreach { _ ! UpdateAreas(areas) } } // #CB
  ball addObserver { ball => clients.get foreach { _ ! UpdateBall(ball) } }     // #CB
  score addObserver { score => clients.get foreach { _ ! UpdateScore(score) } } // #CB

  clients addObserver { clients => // #CB
    clients foreach { _ ! UpdateAreas(areas.get) }
    clients foreach { _ ! UpdateBall(ball.get) }
    clients foreach { _ ! UpdateScore(score.get) }
  }

  tickStart
}

abstract class Client(server: ActorSelection) extends Actor with FrontEndHolder {
  mousePosition addObserver { pos => // #CB
    server ! MouseYChanged(pos.y)
  }

  val frontEnd = createFrontEnd

  def receive = {
    case UpdateAreas(areas) => frontEnd updateAreas areas // #CB #IMP-STATE
    case UpdateBall(ball) => frontEnd updateBall ball     // #CB #IMP-STATE
    case UpdateScore(score) => frontEnd updateScore score // #CB #IMP-STATE
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
