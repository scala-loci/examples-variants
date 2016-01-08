package common

package object observer {
  private lazy val event = Observable(())

  private lazy val thread = {
    val thread = new Thread {
      override def run = while (true) {
        event set (())
        Thread sleep 20
      }
    }

    thread setDaemon true
    thread.start
    thread
  }

  val tick: Observable[Unit] = event

  def tickStart: Unit = thread
}
