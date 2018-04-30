package chat

import util._
import scala.scalajs.js.Dynamic.{ global => jsGlobal }

object Node {
  def main(args: Array[String]): Unit = new Application(
    if (jsGlobal.location.search.toString == "?benchmark") new Benchmark
    else new UI)
}
