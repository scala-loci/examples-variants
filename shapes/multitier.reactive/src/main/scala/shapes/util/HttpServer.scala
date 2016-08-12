package shapes
package util

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import akka.http.scaladsl.HttpExt
import akka.http.scaladsl.ConnectionContext
import akka.http.scaladsl.server.Route

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

trait HttpServer {
  def stop: Unit
}

object HttpServer {
  def start(
      route: Route, interface: String, port: Int = 1,
      connectionContext: Option[ConnectionContext] = None): Future[HttpServer] = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()

    val binding =
      connectionContext map {
        Http() bindAndHandle (route, interface, port, _)
      } getOrElse {
        Http() bindAndHandle (route, interface, port)
      }

    def shutdown = {
      system.terminate
      Await.result(system.whenTerminated, Duration.Inf)
      materializer.shutdown
    }

    binding.failed foreach { _ => shutdown }

    binding map { binding =>
      new HttpServer {
        def stop = binding.unbind onComplete { _ => shutdown }
      }
    }
  }
}
