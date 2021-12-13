package chat

import util._

import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.HttpCharsets._
import akka.http.scaladsl.server.Directives._

object Registry extends App {
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
      path("chattraditional.js") {
        getFromResource("main.js")
      } ~
      path("chattraditional" / "ui.js") {
        getFromResource("util/ui.js")
      } ~
      path("chattraditional" / "benchmark.js") {
        getFromResource("util/benchmark.js")
      } ~
      path("chattraditional" / "list.js") {
        getFromResource("util/list.js")
      } ~
      path("chattraditional" / "observable.js") {
        getFromResource("util/observable.js")
      } ~
      path("chattraditional" / "chat.js") {
        getFromResource("util/chat.js")
      } ~
      pathPrefix("lib") {
        getFromResourceDirectory("META-INF/resources/webjars")
      }
    }

  HttpServer.start(route, "localhost", 8080)

  new Application(connectionEstablished)
}
