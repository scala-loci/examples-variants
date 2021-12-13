package chat
package util

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.HttpsConnectionContext
import akka.http.scaladsl.server.Route

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

trait HttpServer {
  def stop(): Unit
}

object HttpServer {
  def start(
      route: Route, interface: String, port: Int = 1,
      connectionContext: Option[HttpsConnectionContext] = None): Future[HttpServer] = {
    implicit val system = ActorSystem()

    val builder = Http().newServerAt(interface, port)

    connectionContext foreach builder.enableHttps

    val binding = builder.bindFlow(route)

    def shutdown() = {
      system.terminate()
      Await.result(system.whenTerminated, Duration.Inf)
    }

    binding.failed foreach { _ => shutdown() }

    binding map { binding =>
      new HttpServer {
        def stop() = binding.unbind() onComplete { _ => shutdown() }
      }
    }
  }
}
