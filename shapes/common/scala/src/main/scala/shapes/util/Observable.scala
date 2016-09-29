package shapes
package util

import scala.collection.mutable.ListBuffer

class Observable[T](var init: T) {
  val handlers = new ListBuffer[T => Unit]
  def addObserver(handler: T => Unit) = handlers += handler
  def set(v: T) = {
    init = v
    handlers foreach { _(init) }
  }
  def get: T = init
}

object Observable {
  def apply[T](v: T) = new Observable[T](v)
}
