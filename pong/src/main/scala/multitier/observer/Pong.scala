package multitier
package observer

import common._
import common.observer._
import loci._
import loci.serializable.upickle._
import loci.tcp._

@multitier
object PingPong {
  trait Server extends Peer { type Tie <: Multiple[Client] }
  trait Client extends Peer with FrontEndHolder { type Tie <: Single[Server] }

  val clients = placed[Server].local { implicit! => Observable(Seq.empty[Remote[Client]]) }

  val mousePositions = placed[Server].local { implicit! => Observable(Map.empty[Remote[Client], Int]) }

  def mouseYChanged(y: Int) = placed[Server].sbj { implicit! =>
    client: Remote[Client] =>
      mousePositions set (mousePositions.get + (client -> y)) // #IMP-STATE
  }

  def isPlaying = placed[Server].local { implicit! => clients.get.size >= 2 }

  val ball = placed[Server].local { implicit! => Observable(initPosition) }

  placed[Server] { implicit! =>
    tick addObserver { _ => // #CB
      if (isPlaying) ball set (ball.get + speed.get) // #IMP-STATE
    }

    remote[Client].joined += { client => // #CB
      clients set (clients.get :+ client) // #IMP-STATE
      players set
        (clients.get match {
          case left :: right :: _ => Seq(Some(left), Some(right))
          case _ => Seq(None, None)
        })
    }

    remote[Client].left += { client => // #CB
      clients set (clients.get filterNot { _ == client }) // #IMP-STATE
    }
  }

  val players = placed[Server].local { implicit! => Observable(Seq(Option.empty[Remote[Client]], Option.empty[Remote[Client]])) }

  val areas = placed[Server].local { implicit! => Observable(List.empty[Area]) }

  placed[Server] { implicit! =>
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

    def updateAreas(pos: Seq[Option[Int]]) = {
      val mouseY = pos map { _ getOrElse initPosition.y }
      val leftRacket = Racket(leftRacketPos, mouseY(0))
      val rightRacket = Racket(rightRacketPos, mouseY(1))
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
  }

  val speed = placed[Server].local { implicit! => Observable(initSpeed) }

  val leftPoints = placed[Server].local { implicit! => Observable(0) }
  val rightPoints = placed[Server].local { implicit! => Observable(0) }

  placed[Server].local { implicit! =>
    leftPoints addObserver { updateScore(_, rightPoints.get) } // #CB
    rightPoints addObserver { updateScore(leftPoints.get, _) } // #CB

    def updateScore(leftPoints: Int, rightPoints: Int) = {
      score set (leftPoints + " : " + rightPoints) // #IMP-STATE
    }
  }

  val score = placed[Server].local { implicit! => Observable("0 : 0") }

  placed[Server] { implicit! =>
    areas addObserver { remote call updateAreasClients(_) } // #REMOTE #CB
    ball addObserver { remote call updateBallClients(_) }   // #REMOTE #CB
    score addObserver { remote call updateScoreClients(_) } // #REMOTE #CB

    clients addObserver { _ => // #CB
      remote call updateAreasClients(areas.get) // #REMOTE
      remote call updateBallClients(ball.get)   // #REMOTE
      remote call updateScoreClients(score.get) // #REMOTE
    }
  }

  placed[Client] { implicit! =>
    peer.mousePosition addObserver { pos => remote call mouseYChanged(pos.y) } // #REMOTE #CB
  }

  val frontEnd = placed[Client].local { implicit! => peer.createFrontEnd }

  def updateAreasClients(areas: List[Area]) =
    placed[Client] { implicit! => frontEnd updateAreas areas } // #IMP-STATE
  def updateBallClients(ball: Point) =
    placed[Client] { implicit! => frontEnd updateBall ball } // #IMP-STATE
  def updateScoreClients(score: String) =
    placed[Client] { implicit! => frontEnd updateScore score } // #IMP-STATE

  tickStart
}

object PongServer extends App {
  loci.multitier setup new PingPong.Server {
    def connect = listen[PingPong.Client] { TCP(1099) }
  }
}

object PongClient extends App {
  loci.multitier setup new PingPong.Client with UI.FrontEnd {
    def connect = request[PingPong.Server] { TCP("localhost", 1099) }
  }
}

object PongClientBenchmark extends App {
  loci.multitier setup new PingPong.Client with Benchmark.FrontEnd {
    def connect = request[PingPong.Server] { TCP("localhost", 1099) }
    def arguments = args
  }
}
