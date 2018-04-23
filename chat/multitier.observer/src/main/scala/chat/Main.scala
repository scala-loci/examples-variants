package chat

import util._

import loci._
import loci.ws.akka._
import loci.contexts.Pooled.Implicits.global

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{ global => jsGlobal }

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
        getFromResource("chatmultiobservejs-opt.js")
      } ~
      pathPrefix("lib") {
        getFromResourceDirectory("META-INF/resources/webjars")
      }
    }

  HttpServer start (route, "localhost", 8080) foreach { server =>
    (multitier setup new Application.Registry {
      def connect = listen[Application.Node] { webSocket }
    })
    .terminated onComplete { _ =>
      server.stop
    }
  }
}

object Node extends js.JSApp {
  def main() = multitier setup new Application.Node {
    def connect = request[Application.Registry] { WS("ws://localhost:8080") }
    val ui =
      if (jsGlobal.location.search.toString == "?benchmark") new Benchmark
      else new UI
  }
}
