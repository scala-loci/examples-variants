package distributed
package reactive

import common._
import common.distributed._
import common.reactive._

import rescala.Var
import rescala.Signal
import makro.SignalMacro.{SignalM => Signal}

import java.rmi.Remote
import java.rmi.RemoteException
import java.rmi.ConnectException
import java.rmi.registry.LocateRegistry
import java.rmi.registry.Registry

@remote trait Server {
  def addPlayer(client: Client): Unit
  def mouseYChanged(client: Client, y: Int): Unit
}

class ServerImpl extends Server {
  import rescala.conversions.SignalConversions._

  val clients = Var(Seq.empty[Client])

  val isPlaying = Signal { clients().size >= 2 }

  val ball: Signal[Point] =
    tick.fold(initPosition) { (ball, _) =>
      if (isPlaying.get) ball + speed.get else ball
    }

  def addPlayer(client: Client) = {
    clients() = clients.get :+ client
    players() =
      clients.get match {
        case left :: right :: _ => Seq(Some(left), Some(right))
        case _ => Seq(None, None)
      }
  }

  val players = Var(Seq(Option.empty[Client], Option.empty[Client]))

  val mousePositions = Var(Map.empty[Client, Int])

  def mouseYChanged(client: Client, y: Int) =
    mousePositions() = mousePositions.get + (client -> y)

  val areas = {
    val racketY = Signal {
      players() map { _ flatMap { c =>
        mousePositions() get c } getOrElse initPosition.y }
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
    val x = xBounce toggle (initSpeed.x, -initSpeed.x)
    val y = yBounce toggle (initSpeed.y, -initSpeed.y)
    Signal { Point(x(), y()) }
  }

  val score = {
    val leftPlayerPoints = rightWall.iterate(0) { _ + 1 }
    val rightPlayerPoints = leftWall.iterate(0) { _ + 1 }
    Signal { leftPlayerPoints() + " : " + rightPlayerPoints() }
  }

  areas.changed += { updateAreasClients(clients.get, _) }
  ball.changed += { updateBallClients(clients.get, _) }
  score.changed += { updateScoreClients(clients.get, _) }

  clients.changed += { clients =>
    updateAreasClients(clients, areas.get)
    updateBallClients(clients, ball.get)
    updateScoreClients(clients, score.get)
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
        clients() = clients.get filterNot { _ == client }
    }

  tickStart
}

@remote trait Client {
  def updateAreas(areas: List[Area]): Unit
  def updateBall(ball: Point): Unit
  def updateScore(score: String): Unit
}

class ClientImpl(server: Server) extends Client {
  val self = makeStub[Client](this)

  val areas = Var(List.empty[Area])
  val ball = Var(Point(0, 0))
  val score = Var("0 : 0")

  UI.mousePosition.changed += { pos =>
    nonblocking { server mouseYChanged (self, pos.y) }
  }

  val ui = new UI(areas, ball, score)

  def updateAreas(areas: List[Area]) = this.areas() = areas
  def updateBall(ball: Point) = this.ball() = ball
  def updateScore(score: String) = this.score() = score

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

  new ClientImpl(server)
}
