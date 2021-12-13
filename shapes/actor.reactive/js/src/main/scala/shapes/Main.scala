package shapes

import akka.actor._

object Client {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("client-system")
    system.actorOf(Props(new Application))
  }
}
