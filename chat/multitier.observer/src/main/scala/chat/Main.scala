package chat

import util._

import retier._
import retier.ws.akka._
import retier.contexts.Pooled.Implicits.global

import scala.scalajs.js

import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.HttpCharsets._
import akka.http.scaladsl.server.Directives._

object Registry extends App {
  val webSocket = WebSocketListener()

  val route =
    get {
      pathSingleSlash {
        webSocket ~
        getFromResource("index.xhtml", ContentType(`application/xhtml+xml`, `UTF-8`))
      } ~
      path("app.js") {
        getFromResource("chatmultiobservejs-fastopt.js")
      } ~
      path("launcher.js") {
        getFromResource("chatmultiobservejs-launcher.js")
      } ~
      pathPrefix("lib") {
        getFromResourceDirectory("META-INF/resources/webjars")
      }
    }

  HttpServer start (route, "localhost", 8080) foreach { server =>
    (multitier setup new Application.Registry {
      def connect = webSocket
      override def context = contexts.Queued.create
    })
    .terminated onComplete { _ =>
      server.stop
    }
  }
}

object Node extends js.JSApp {
  def main() = multitier setup new Application.Node {
    def connect = request[Application.Registry] { WS("ws://localhost:8080") }
    override def context = contexts.Queued.create
  }
}
