package common

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import java.rmi.Remote
import java.rmi.server.UnicastRemoteObject

package object distributed {
  def makeStub[T](obj: AnyRef) =
    UnicastRemoteObject.exportObject(obj.asInstanceOf[Remote], 0).asInstanceOf[T]

  def nonblocking[T](call: => T)(
      implicit executor: ExecutionContext = ExecutionContext.global) =
    Future { call }
}
