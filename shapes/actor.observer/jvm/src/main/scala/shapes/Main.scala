package shapes

import util._

import akka.actor._

import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.HttpCharsets._
import akka.http.scaladsl.server.Directives._

object Server extends App {
  val connectionEstablished = Observable(WebSocket.closed)

  val webSocket =
    extractWebSocketUpgrade { webSocket =>
      extractMaterializer { implicit materializer =>
        val socket = WebSocket()
        connectionEstablished.set(socket)
        complete(webSocket handleMessages socket.handleWebSocket)
      }
    }

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

  HttpServer.start(route, "localhost", 8080)

  val system = ActorSystem("server-system")
  system.actorOf(Props(new Application(connectionEstablished)))
}
