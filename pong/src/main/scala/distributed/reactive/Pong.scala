package distributed
package reactive

import common._
import common.distributed._
import common.reactive._

import rescala._

import java.rmi.Remote
import java.rmi.RemoteException
import java.rmi.ConnectException
import java.rmi.registry.LocateRegistry
import java.rmi.registry.Registry

trait Server extends Remote {
  @throws[RemoteException] def addPlayer(client: Client): Unit
  @throws[RemoteException] def mouseYChanged(client: Client, y: Int): Unit
}

class ServerImpl extends Server {
  val clients = Var(Seq.empty[Client])

  val isPlaying = Signal { clients().size >= 2 }

  val ball: Signal[Point] =
    tick.fold(initPosition) { (ball, _) =>
      if (isPlaying.now) ball + speed.now else ball
    }

  def addPlayer(client: Client) = synchronized {
    clients transform { _ :+ client }
  }

  val players = Signal {
    clients() match {
      case left :: right :: _ => Seq(Some(left), Some(right))
      case _ => Seq(None, None)
    }
  }

  val mousePositions = Var(Map.empty[Client, Int])

  def mouseYChanged(client: Client, y: Int) = synchronized {
    mousePositions transform { _ + (client -> y) }
  }

  val areas = {
    val racketY = Signal {
      players() map {
        _ flatMap { mousePositions() get _ } getOrElse initPosition.y }
    }

    val leftRacket = new Racket(leftRacketPos, Signal { racketY()(0) })
    val rightRacket = new Racket(rightRacketPos, Signal { racketY()(1) })

    val rackets = List(leftRacket, rightRacket)
    Signal { rackets map { _.area() } }
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

  areas observe { updateAreasClients(clients.now, _) }
  ball observe { updateBallClients(clients.now, _) }
  score observe { updateScoreClients(clients.now, _) }

  clients observe { clients =>
    updateAreasClients(clients, areas.now)
    updateBallClients(clients, ball.now)
    updateScoreClients(clients, score.now)
  }

  def updateAreasClients(clients: Seq[Client], areas: List[Area]) =
    clients foreach { client =>
      removeClientOnFailure(client) { nonblocking { client updateAreas areas } }
    }
  def updateBallClients(clients: Seq[Client], ball: Point) =
    clients foreach { client =>
      removeClientOnFailure(client) { nonblocking { client updateBall ball } }
    }
  def updateScoreClients(clients: Seq[Client], score: String) =
    clients foreach { client =>
      removeClientOnFailure(client) { nonblocking { client updateScore score } }
    }

  def removeClientOnFailure(client: Client)(body: => Unit) =
    try body
    catch {
      case _: ConnectException =>
        clients transform { _ filterNot { _ == client } }
    }

  tickStart
}

trait Client extends Remote {
  @throws[RemoteException] def updateAreas(areas: List[Area]): Unit
  @throws[RemoteException] def updateBall(ball: Point): Unit
  @throws[RemoteException] def updateScore(score: String): Unit
}

abstract class ClientImpl(server: Server) extends Client with FrontEndHolder {
  val self = makeStub[Client](this)

  val areas = Var(List.empty[Area])
  val ball = Var(Point(0, 0))
  val score = Var("0 : 0")

  mousePosition observe { pos =>
    nonblocking { server mouseYChanged (self, pos.y) }
  }

  val frontEnd = createFrontEnd(areas, ball, score)

  def updateAreas(areas: List[Area]) = synchronized { this.areas set areas }
  def updateBall(ball: Point) = synchronized { this.ball set ball }
  def updateScore(score: String) = synchronized { this.score set score }

  server addPlayer self
}

object PongServer extends App {
  val server = new ServerImpl

  val registry =
    try LocateRegistry.createRegistry(1099)
    catch {
      case _: RemoteException => LocateRegistry.getRegistry
    }
  registry.rebind("PongServer", makeStub[Remote](server))
}

object PongClient extends App {
  val registry = LocateRegistry.getRegistry("localhost")
  val server = registry.lookup("PongServer").asInstanceOf[Server]

  new ClientImpl(server) with UI.FrontEnd
}

object PongClientBenchmark extends App {
  val registry = LocateRegistry.getRegistry("localhost")
  val server = registry.lookup("PongServer").asInstanceOf[Server]

  new ClientImpl(server) with Benchmark.FrontEnd {
    def arguments = args
  }
}
