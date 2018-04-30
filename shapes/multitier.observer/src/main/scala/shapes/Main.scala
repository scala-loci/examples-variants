package shapes

import util._

import loci._
import loci.communicator.ws.akka._
import loci.contexts.Pooled.Implicits.global

import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.HttpCharsets._
import akka.http.scaladsl.server.Directives._

object Server extends App {
  val webSocket = WebSocketListener()

  val route =
    get {
      pathSingleSlash {
        webSocket ~
        getFromResource("index.xhtml", ContentType(`application/xhtml+xml`, `UTF-8`))
      } ~
      path("app.js") {
        getFromResource("shapesmultiobservejs-opt.js")
      } ~
      pathPrefix("lib") {
        getFromResourceDirectory("META-INF/resources/webjars")
      }
    }

  HttpServer start (route, "localhost", 8080) foreach { server =>
    (multitier setup new Application.Server {
      def connect = listen[Application.Client] { webSocket }
    })
    .terminated onComplete { _ =>
      server.stop
    }
  }
}

object Client {
  def main(args: Array[String]): Unit = multitier setup new Application.Client {
    def connect = connect[Application.Server] { WS("ws://localhost:8080") }
  }
}
