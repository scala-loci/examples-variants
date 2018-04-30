package multitier
package observer

import common._
import common.multitier._
import common.observer._
import loci._
import loci.serializer.upickle._
import loci.communicator.tcp._

@multitier
object PingPong {
  trait Server extends Peer { type Tie <: Multiple[Client] }
  trait Client extends Peer with FrontEndHolder { type Tie <: Single[Server] }

  val clients = placed[Server].local { implicit! => Observable(Seq.empty[Remote[Client]]) }

  val mousePositions = placed[Server].local { implicit! => Observable(Map.empty[Remote[Client], Int]) }

  def mouseYChanged(y: Int) = placed[Server].sbj { implicit! =>
    client: Remote[Client] =>
      mousePositions set (mousePositions.get + (client -> y))
  }

  def isPlaying = placed[Server].local { implicit! => clients.get.size >= 2 }

  val ball = placed[Server].local { implicit! => Observable(initPosition) }

  placed[Server] { implicit! =>
    tick addObserver { _ =>
      if (isPlaying) ball set (ball.get + speed.get)
    }

    remote[Client].joined notify { client =>
      clients set (clients.get :+ client)
      players set
        (clients.get match {
          case left :: right :: _ => Seq(Some(left), Some(right))
          case _ => Seq(None, None)
        })
    }

    remote[Client].left notify { client =>
      clients set (clients.get filterNot { _ == client })
    }
  }

  val players = placed[Server].local { implicit! => Observable(Seq(Option.empty[Remote[Client]], Option.empty[Remote[Client]])) }

  val areas = placed[Server].local { implicit! => Observable(List.empty[Area]) }

  placed[Server] { implicit! =>
    players addObserver { players =>
      updateAreas(players map {
        _ flatMap { mousePositions.get get _ }
      })
    }

    mousePositions addObserver { mousePositions =>
      updateAreas(players.get map {
        _ flatMap { mousePositions get _ }
      })
    }

    def updateAreas(pos: Seq[Option[Int]]) = {
      val mouseY = pos map { _ getOrElse initPosition.y }
      val leftRacket = Racket(leftRacketPos, mouseY(0))
      val rightRacket = Racket(rightRacketPos, mouseY(1))
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

    def checkBallInRacket(areas: List[Area], ball: Point) = {
      if(areas exists { _ contains ball })
        xBounce
    }

    def xBounce() = speed set Point(-speed.get.x, speed.get.y)
    def yBounce() = speed set Point(speed.get.x, -speed.get.y)
  }

  val speed = placed[Server].local { implicit! => Observable(initSpeed) }

  val leftPoints = placed[Server].local { implicit! => Observable(0) }
  val rightPoints = placed[Server].local { implicit! => Observable(0) }

  placed[Server].local { implicit! =>
    leftPoints addObserver { updateScore(_, rightPoints.get) }
    rightPoints addObserver { updateScore(leftPoints.get, _) }

    def updateScore(leftPoints: Int, rightPoints: Int) = {
      score set (leftPoints + " : " + rightPoints)
    }
  }

  val score = placed[Server].local { implicit! => Observable("0 : 0") }

  placed[Server] { implicit! =>
    areas addObserver { remote call updateAreasClients(_) }
    ball addObserver { remote call updateBallClients(_) }
    score addObserver { remote call updateScoreClients(_) }

    clients addObserver { _ =>
      remote call updateAreasClients(areas.get)
      remote call updateBallClients(ball.get)
      remote call updateScoreClients(score.get)
    }
  }

  placed[Client] { implicit! =>
    peer.mousePosition addObserver { pos => remote call mouseYChanged(pos.y) }
  }

  val frontEnd = placed[Client].local { implicit! => peer.createFrontEnd }

  def updateAreasClients(areas: List[Area]) =
    placed[Client] { implicit! => frontEnd updateAreas areas }
  def updateBallClients(ball: Point) =
    placed[Client] { implicit! => frontEnd updateBall ball }
  def updateScoreClients(score: String) =
    placed[Client] { implicit! => frontEnd updateScore score }

  tickStart
}

object PongServer extends App {
  loci.multitier setup new PingPong.Server {
    def connect = listen[PingPong.Client] { TCP(1099) }
  }
}

object PongClient extends App {
  loci.multitier setup new PingPong.Client with UI.FrontEnd {
    def connect = connect[PingPong.Server] { TCP("localhost", 1099) }
  }
}

object PongClientBenchmark extends App {
  loci.multitier setup new PingPong.Client with Benchmark.FrontEnd {
    def connect = connect[PingPong.Server] { TCP("localhost", 1099) }
    def arguments = args
  }
}
