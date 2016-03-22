package chat
package util

import akka.stream.Materializer
import akka.stream.stage.PushStage
import akka.stream.stage.Context
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.ws.TextMessage
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable.Queue
import java.lang.StringBuilder
import java.util.concurrent.atomic.AtomicBoolean

object WebSocket {
  def apply(): WebSocket = new WebSocketImpl
  def closed: WebSocket = new WebSocket {
    val closed = Observable(())
    val received = Observable("")
    def isOpen = false
    def send(data: String) = { }
    def handleWebsocket(implicit materializer: Materializer) = Flow[Message]
  }
}

trait WebSocket {
  val closed: Observable[Unit]
  val received: Observable[String]

  def isOpen: Boolean
  def send(data: String): Unit
  def handleWebsocket(implicit materializer: Materializer): Flow[Message, Message, Unit]
}

class WebSocketImpl extends WebSocket {
  private val promises = Queue.empty[Promise[Option[(Unit, Message)]]]
  private val open = new AtomicBoolean(true)

  val closed = Observable(())
  val received = Observable("")

  def isOpen = open.get

  def send(data: String) = promises synchronized {
    if (isOpen) {
      val message = Some(((), TextMessage(data)))
      if (!promises.isEmpty && !promises.head.isCompleted)
        promises.dequeue success message
      else
        promises enqueue (Promise successful message)
    }
  }

  def handleWebsocket(implicit materializer: Materializer) = {
    def close() = promises synchronized {
      if (isOpen) {
        open set false
        promises foreach { _ trySuccess None }
        promises.clear
        closed set (())
      }
    }

    val source = Source.unfoldAsync(()) { _ =>
      promises synchronized {
        if (isOpen) {
          if (promises.isEmpty) {
            val promise = Promise[Option[(Unit, Message)]]
            promises enqueue promise
            promise.future
          }
          else
            promises.dequeue.future
        }
        else
          Future successful None
      }
    }

    val sink = Sink foreach[Message] {
      case TextMessage.Strict(data) =>
        WebSocket synchronized {
          received set data
        }

      case message: TextMessage =>
        message.textStream.runFold(new StringBuilder) {
          case (builder, data) => builder append data
        } onSuccess {
          case builder => WebSocket synchronized {
            received set builder.toString
          }
        }

      case _ =>
        close
    }

    val flow = Flow.fromSinkAndSourceMat(sink, source) { (future, _) =>
      future onComplete { _ => close }
    }

    def closeConnectionOnFailure[T]() = new PushStage[T, T] {
      def onPush(elem: T, ctx: Context[T]) = ctx push elem

      override def onUpstreamFailure(cause: Throwable, ctx: Context[T]) = {
        close
        super.onUpstreamFailure(cause, ctx)
      }
    }

    Flow[Message] transform closeConnectionOnFailure via flow
  }
}
