package multitier
package observer

import common._
import common.multitier._
import common.observer._
import loci.language._
import loci.serializer.upickle._
import loci.communicator.tcp._

@multitier object PingPong {
  @peer type Server <: { type Tie <: Multiple[Client] }
  @peer type Client <: { type Tie <: Single[Server] }

  val ui: Local[FrontEndHolder] on Client

  val clients = on[Server] local { implicit! => Observable(Seq.empty[Remote[Client]]) }

  val mousePositions = on[Server] local { implicit! => Observable(Map.empty[Remote[Client], Int]) }

  def mouseYChanged(y: Int) = on[Server] sbj { implicit! =>
    client: Remote[Client] =>
      mousePositions.set(mousePositions.get + (client -> y))
  }

  def isPlaying = on[Server] local { implicit! => clients.get.size >= 2 }

  val ball = on[Server] local { implicit! => Observable(initPosition) }

  on[Server] { implicit! =>
    tick addObserver { _ =>
      if (isPlaying) ball.set(ball.get + speed.get)
    }

    remote[Client].joined foreach { client =>
      clients.set(clients.get :+ client)
      players.set(
        clients.get match {
          case left :: right :: _ => Seq(Some(left), Some(right))
          case _ => Seq(None, None)
        })
    }

    remote[Client].left foreach { client =>
      clients.set(clients.get filterNot { _ == client })
    }
  }

  val players = on[Server] local { implicit! =>
    Observable(Seq(Option.empty[Remote[Client]], Option.empty[Remote[Client]]))
  }

  val areas = on[Server] local { implicit! => Observable(List.empty[Area]) }

  on[Server] { implicit! =>
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
      areas.set(rackets map { _.area.get })
    }

    ball addObserver { ball =>
      if (ball.x < 0) leftWall()
      if (ball.x > maxX) rightWall()
      if (ball.y < 0 || ball.y > maxY) yBounce()
    }

    def leftWall() = {
      rightPoints.set(rightPoints.get + 1)
      xBounce()
    }

    def rightWall() = {
      leftPoints.set(leftPoints.get + 1)
      xBounce()
    }

    ball addObserver { checkBallInRacket(areas.get, _) }

    def checkBallInRacket(areas: List[Area], ball: Point) = {
      if(areas exists { _ contains ball })
        xBounce()
    }

    def xBounce() = speed.set(Point(-speed.get.x, speed.get.y))
    def yBounce() = speed.set(Point(speed.get.x, -speed.get.y))
  }

  val speed = on[Server] local { implicit! => Observable(initSpeed) }

  val leftPoints = on[Server] local { implicit! => Observable(0) }
  val rightPoints = on[Server] local { implicit! => Observable(0) }

  on[Server] { implicit! =>
    leftPoints addObserver { updateScore(_, rightPoints.get) }
    rightPoints addObserver { updateScore(leftPoints.get, _) }

    def updateScore(leftPoints: Int, rightPoints: Int) = {
      score.set(s"$leftPoints : $rightPoints")
    }
  }

  val score = on[Server] local { implicit! => Observable("0 : 0") }

  on[Server] { implicit! =>
    areas addObserver { remote call updateAreasClients(_) }
    ball addObserver { remote call updateBallClients(_) }
    score addObserver { remote call updateScoreClients(_) }

    clients addObserver { _ =>
      remote call updateAreasClients(areas.get)
      remote call updateBallClients(ball.get)
      remote call updateScoreClients(score.get)
    }
  }

  on[Client] { implicit! =>
    ui.mousePosition addObserver { pos => remote call mouseYChanged(pos.y) }
  }

  val frontEnd = on[Client] local { implicit! => ui.createFrontEnd }

  def updateAreasClients(areas: List[Area]) =
    on[Client] { implicit! => frontEnd.updateAreas(areas) }
  def updateBallClients(ball: Point) =
    on[Client] { implicit! => frontEnd.updateBall(ball) }
  def updateScoreClients(score: String) =
    on[Client] { implicit! => frontEnd.updateScore(score) }

  tickStart()
}

object PongServer extends App {
  loci.language.multitier start new Instance[PingPong.Server](
    listen[PingPong.Client] { TCP(1099) })
}

object PongClient extends App {
  loci.language.multitier start new Instance[PingPong.Client](
      connect[PingPong.Server] { TCP("localhost", 1099) }) {
    val ui = new UI.FrontEnd { }
  }
}

object PongClientBenchmark extends App {
  loci.language.multitier start new Instance[PingPong.Client](
      connect[PingPong.Server] { TCP("localhost", 1099) }) {
    val ui = new Benchmark.FrontEnd {
      def arguments = args
    }
  }
}
