package distributed
package observer

import common._
import common.distributed._
import common.observer._

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
  val clients = Observable(Seq.empty[Client])

  def isPlaying = clients.get.size >= 2

  val ball = Observable(initPosition)

  tick addObserver { _ =>
    if (isPlaying) ball set (ball.get + speed.get)
  }

  def addPlayer(client: Client) = synchronized {
    clients set (clients.get :+ client)
  }

  val players = Observable(Seq(Option.empty[Client], Option.empty[Client]))

  clients addObserver { clients =>
    players set (clients match {
      case left :: right :: _ => Seq(Some(left), Some(right))
      case _ => Seq(None, None)
    })
  }

  val mousePositions = Observable(Map.empty[Client, Int])

  def mouseYChanged(client: Client, y: Int) = synchronized {
    mousePositions set (mousePositions.get + (client -> y))
  }

  val areas = Observable(List.empty[Area])

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

  def updateAreas(mouseY: Seq[Option[Int]]) = {
    val racketY = mouseY map { _ getOrElse initPosition.y }
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

  def checkBallInRacket(areas: List[Area], ball: Point) = {
    if(areas exists { _ contains ball })
      xBounce
  }

  def xBounce() = speed set Point(-speed.get.x, speed.get.y)
  def yBounce() = speed set Point(speed.get.x, -speed.get.y)

  val speed = Observable(initSpeed)

  val leftPoints = Observable(0)
  val rightPoints = Observable(0)

  leftPoints addObserver { updateScore(_, rightPoints.get) }
  rightPoints addObserver { updateScore(leftPoints.get, _) }

  def updateScore(leftPoints: Int, rightPoints: Int) = {
    score set s"$leftPoints : $rightPoints"
  }

  val score = Observable("0 : 0")

  areas addObserver { updateAreasClients(clients.get, _) }
  ball addObserver { updateBallClients(clients.get, _) }
  score addObserver { updateScoreClients(clients.get, _) }

  clients addObserver { clients =>
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
        clients set (clients.get filterNot { _ == client })
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

  mousePosition addObserver { pos =>
    nonblocking { server mouseYChanged (self, pos.y) }
  }

  val frontEnd = createFrontEnd

  def updateAreas(areas: List[Area]) = synchronized { frontEnd updateAreas areas }
  def updateBall(ball: Point) = synchronized { frontEnd updateBall ball }
  def updateScore(score: String) = synchronized { frontEnd updateScore score }

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
