package chat

import util._

import loci._
import loci.communicator.ws.akka._
import loci.contexts.Pooled.Implicits.global

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
        getFromResource("chatmultireactjs-opt.js")
      } ~
      pathPrefix("lib") {
        getFromResourceDirectory("META-INF/resources/webjars")
      }
    }

  HttpServer start (route, "localhost", 8080) foreach { server =>
    val runtime = multitier start new Instance[Application.Registry](
      listen[Application.Node] { webSocket })

    runtime.terminated foreach { _ =>
      server.stop
    }
  }
}

object Node {
  def main(args: Array[String]): Unit =
    multitier start new Instance[Application.Node](
        connect[Application.Registry] { WS("ws://localhost:8080") }) {
      val ui =
        if (jsGlobal.location.search.toString == "?benchmark") new Benchmark
        else new UI
    }
}
