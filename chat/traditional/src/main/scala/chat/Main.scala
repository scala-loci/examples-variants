package chat

import util._

import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.HttpCharsets._
import akka.http.scaladsl.server.Directives._

object Registry extends App {
  val connectionEstablished = Observable(WebSocket.closed)

  val webSocket =
    extractUpgradeToWebSocket { webSocket =>
      extractMaterializer { implicit materializer =>
        val socket = WebSocket()
        connectionEstablished set socket
        complete(webSocket handleMessages socket.handleWebSocket)
      }
    }

  val route =
    get {
      pathSingleSlash {
        webSocket ~
        getFromResource("index.xhtml", ContentType(`application/xhtml+xml`, `UTF-8`))
      } ~
      path("app.js") {
        getFromResource("app.js")
      } ~
      path("util" / "ui.js") {
        getFromResource("util/ui.js")
      } ~
      path("util" / "list.js") {
        getFromResource("util/list.js")
      } ~
      path("util" / "observable.js") {
        getFromResource("util/observable.js")
      } ~
      path("util" / "chat.js") {
        getFromResource("util/chat.js")
      } ~
      pathPrefix("lib") {
        getFromResourceDirectory("META-INF/resources/webjars")
      }
    }

  HttpServer start (route, "localhost", 8080)

  new Application(connectionEstablished)
}
