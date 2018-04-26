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

  tick addObserver { _ => // #CB
    if (isPlaying) ball set (ball.get + speed.get) // #IMP-STATE
  }

  def addPlayer(client: Client) = synchronized {
    clients set (clients.get :+ client) // #IMP-STATE
  }

  val players = Observable(Seq(Option.empty[Client], Option.empty[Client]))

  clients addObserver { clients => // #CB
    players set (clients match { // #IMP-STATE
      case left :: right :: _ => Seq(Some(left), Some(right))
      case _ => Seq(None, None)
    })
  }

  val mousePositions = Observable(Map.empty[Client, Int])

  def mouseYChanged(client: Client, y: Int) = synchronized {
    mousePositions set (mousePositions.get + (client -> y)) // #IMP-STATE
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

  leftPoints addObserver { updateScore(_, rightPoints.get) } // #CB
  rightPoints addObserver { updateScore(leftPoints.get, _) } // #CB

  def updateScore(leftPoints: Int, rightPoints: Int) = {
    score set (leftPoints + " : " + rightPoints) // #IMP-STATE
  }

  val score = Observable("0 : 0")

  areas addObserver { updateAreasClients(clients.get, _) } // #CB
  ball addObserver { updateBallClients(clients.get, _) }   // #CB
  score addObserver { updateScoreClients(clients.get, _) } // #CB

  clients addObserver { clients => // #CB
    updateAreasClients(clients, areas.get)
    updateBallClients(clients, ball.get)
    updateScoreClients(clients, score.get)
  }

  def updateAreasClients(clients: Seq[Client], areas: List[Area]) =
    clients foreach { client =>
      removeClientOnFailure(client) { nonblocking { client updateAreas areas } } // #REMOTE #CB
    }
  def updateBallClients(clients: Seq[Client], ball: Point) =
    clients foreach { client =>
      removeClientOnFailure(client) { nonblocking { client updateBall ball } } // #REMOTE #CB
    }
  def updateScoreClients(clients: Seq[Client], score: String) =
    clients foreach { client =>
      removeClientOnFailure(client) { nonblocking { client updateScore score } } // #REMOTE #CB
    }

  def removeClientOnFailure(client: Client)(body: => Unit) =
    try body
    catch {
      case _: ConnectException =>
        clients set (clients.get filterNot { _ == client }) // #IMP-STATE
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

  mousePosition addObserver { pos => // #CB
    nonblocking { server mouseYChanged (self, pos.y) } // #REMOTE
  }

  val frontEnd = createFrontEnd

  def updateAreas(areas: List[Area]) = synchronized { frontEnd updateAreas areas } // #IMP-STATE
  def updateBall(ball: Point) = synchronized { frontEnd updateBall ball }          // #IMP-STATE
  def updateScore(score: String) = synchronized { frontEnd updateScore score }     // #IMP-STATE

  server addPlayer self // #REMOTE
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
