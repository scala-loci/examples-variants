package common

import rescala.default._

package object reactive {
  private lazy val event = Evt[Unit]()

  private lazy val thread = {
    val thread = new Thread {
      override def run = while (true) {
        event.fire()
        Thread.sleep(20)
      }
    }

    thread.setDaemon(true)
    thread.start()
    thread
  }

  val tick: Event[Unit] = event

  def tickStart(): Unit = thread
}
