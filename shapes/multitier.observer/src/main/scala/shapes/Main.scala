package shapes

import util._

import retier._
import retier.ws.akka._
import retier.contexts.Pooled.Implicits.global

import scala.scalajs.js

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
      path("launcher.js") {
        getFromResource("shapesmultiobservejs-launcher.js")
      } ~
      pathPrefix("lib") {
        getFromResourceDirectory("META-INF/resources/webjars")
      }
    }

  HttpServer start (route, "localhost", 8080) foreach { server =>
    (multitier setup new Application.Server {
      def connect = webSocket
    })
    .terminated onComplete { _ =>
      server.stop
    }
  }
}

object Client extends js.JSApp {
  def main() = multitier setup new Application.Client {
    def connect = WS("ws://localhost:8080")
  }
}
