package shapes

import akka.actor._
import scala.scalajs.js

object Node extends js.JSApp {
  def main() = {
    val system = ActorSystem("client-system")
    system actorOf Props(new Application)
  }
}
