package multitier
package reactive

import common._
import common.multitier._
import common.reactive._
import loci._
import loci.transmitter.rescala._
import loci.serializer.upickle._
import loci.communicator.tcp._

import rescala.default._

@multitier object PingPong {
  @peer type Server <: { type Tie <: Multiple[Client] }
  @peer type Client <: { type Tie <: Single[Server] }

  val ui: Local[FrontEndHolder] on Client

  val clientMouseY = on[Client] { implicit! => Signal { ui.mousePosition().y } }

  val isPlaying = on[Server] local { implicit! =>
    Signal { remote[Client].connected().size >= 2 }
  }

  val ball: Signal[Point] on Server = placed { implicit! =>
    Events.foldOne(tick, initPosition) { (ball, _) =>
      if (isPlaying.readValueOnce) ball + speed.readValueOnce else ball
    }
  }

  val players = on[Server] local { implicit! =>
    Signal {
      remote[Client].connected() match {
        case left :: right :: _ => Seq(Some(left), Some(right))
        case _ => Seq(None, None)
      }
    }
  }

  val areas = on[Server] { implicit! =>
    val racketY = Signal.dynamic {
      players() map { _ map { client =>
        (clientMouseY from client).asLocal() } getOrElse initPosition.y }
    }

    val leftRacket = new Racket(leftRacketPos, Signal { racketY()(0) })
    val rightRacket = new Racket(rightRacketPos, Signal { racketY()(1) })

    val rackets = List(leftRacket, rightRacket)
    Signal.dynamic { rackets map { _.area() } }
  }

  val leftWall = on[Server] local { implicit! => ball.changed && { _.x < 0 } }
  val rightWall = on[Server] local { implicit! => ball.changed && { _.x > maxX } }

  val xBounce = on[Server] local { implicit! =>
    val ballInRacket = Signal.dynamic { areas() exists { _ contains ball() } }
    val collisionRacket = ballInRacket changedTo true
    leftWall || rightWall || collisionRacket
  }

  val yBounce = on[Server] local { implicit! =>
    ball.changed && { ball => ball.y < 0 || ball.y > maxY }
  }

  val speed = on[Server] local { implicit! =>
    val x = xBounce toggle (Signal { initSpeed.x }, Signal { -initSpeed.x })
    val y = yBounce toggle (Signal { initSpeed.y }, Signal { -initSpeed.y })
    Signal { Point(x(), y()) }
  }

  val score = on[Server] { implicit! =>
    val leftPlayerPoints = rightWall.iterate(0) { _ + 1 }
    val rightPlayerPoints = leftWall.iterate(0) { _ + 1 }
    Signal { s"${leftPlayerPoints()} : ${rightPlayerPoints()}" }
  }

  val frontEnd = on[Client] local { implicit! =>
    ui.createFrontEnd(areas.asLocal, ball.asLocal, score.asLocal)
  }

  tickStart
}

object PongServer extends App {
  loci.multitier start new Instance[PingPong.Server](
    listen[PingPong.Client] { TCP(1099) })
}

object PongClient extends App {
  loci.multitier start new Instance[PingPong.Client](
      connect[PingPong.Server] { TCP("localhost", 1099) }) {
    val ui = new UI.FrontEnd { }
  }
}

object PongClientBenchmark extends App {
  loci.multitier start new Instance[PingPong.Client](
      connect[PingPong.Server] { TCP("localhost", 1099) }) {
    val ui = new Benchmark.FrontEnd {
      def arguments = args
    }
  }
}
