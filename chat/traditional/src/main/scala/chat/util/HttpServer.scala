package chat
package util

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import akka.http.scaladsl.HttpsContext
import akka.http.scaladsl.server.Route

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait HttpServer {
  def stop: Unit
}

object HttpServer {
  def start(
      route: Route, interface: String, port: Int = 1,
      httpsContext: Option[HttpsContext] = None): Future[HttpServer] = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()

    val binding = Http() bindAndHandle (
      route, interface, port, httpsContext = httpsContext)

    def shutdown = {
      system.shutdown
      system.awaitTermination
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
