package chat

import util._
import akka.actor._
import scala.scalajs.js
import scala.scalajs.js.Dynamic.{ global => jsGlobal }

object Node extends js.JSApp {
  def main() = {
    val system = ActorSystem("node-system")
    system actorOf Props(new Application(
      if (jsGlobal.location.search.toString == "?benchmark") new Benchmark
      else new UI))
  }
}
