package shapes

import util._

import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.HttpCharsets._
import akka.http.scaladsl.server.Directives._

object Server extends App {
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
        getFromResource("shapesscalajsobservejs-opt.js")
      } ~
      path("launcher.js") {
        getFromResource("shapesscalajsobservejs-launcher.js")
      } ~
      pathPrefix("lib") {
        getFromResourceDirectory("META-INF/resources/webjars")
      }
    }

  HttpServer start (route, "localhost", 8080)

  new Application(connectionEstablished)
}