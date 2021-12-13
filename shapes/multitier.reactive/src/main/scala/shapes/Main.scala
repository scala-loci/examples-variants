package shapes

import util._

import loci.language._
import loci.communicator.ws.akka.WebSocketListener
import loci.communicator.ws.webnative.WS
import loci.contexts.Pooled.Implicits.global
import loci.platform

import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.HttpCharsets._
import akka.http.scaladsl.server.Directives._

object Server extends App {
  platform(platform.jvm) {
    val webSocket = WebSocketListener()

    val route =
      get {
        pathSingleSlash {
          webSocket ~
          getFromResource("index.xhtml", ContentType(`application/xhtml+xml`, `UTF-8`))
        } ~
        pathPrefix("lib") {
          getFromResourceDirectory("META-INF/resources/webjars")
        } ~
        path(".*\\.js".r) { _ =>
          getFromResource("main.js")
        }
      }

    HttpServer.start(route, "localhost", 8080) foreach { server =>
      val runtime = multitier start new Instance[Application.Server](
        listen[Application.Client] { webSocket })

      runtime.terminated foreach { _ =>
        server.stop()
      }
    }
  }
}

object Client {
  def main(args: Array[String]): Unit =
    platform(platform.js) {
      multitier start new Instance[Application.Client](
        connect[Application.Server] { WS("ws://localhost:8080") })
    }
}
