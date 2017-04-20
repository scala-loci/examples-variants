package chat
package util

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.http.scaladsl.model.ws
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable.Queue
import java.util.concurrent.atomic.AtomicBoolean

trait WebSocket {
  val closed: Observable[Unit]
  val received: Observable[String]

  def isOpen: Boolean
  def send(data: String): Unit
  def handleWebSocket(implicit materializer: Materializer): Flow[ws.Message, ws.Message, NotUsed]
}

object WebSocket {
  def apply(): WebSocket = new WebSocketImpl
  def closed: WebSocket = new WebSocket {
    val closed = Observable(())
    val received = Observable("")
    def isOpen = false
    def send(data: String) = { }
    def handleWebSocket(implicit materializer: Materializer) = Flow[ws.Message]
  }
}

class WebSocketImpl extends WebSocket {
  private val promises = Queue.empty[Promise[Option[(Unit, ws.Message)]]]
  private val open = new AtomicBoolean(true)

  val closed = Observable(())
  val received = Observable("")

  def isOpen = open.get

  def send(data: String) = promises synchronized {
    if (isOpen) {
      val message = Some(((), ws.TextMessage(data)))
      if (!promises.isEmpty && !promises.head.isCompleted)
        promises.dequeue success message
      else
        promises enqueue (Promise successful message)
    }
  }

  def handleWebSocket(implicit materializer: Materializer) = {
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
            val promise = Promise[Option[(Unit, ws.Message)]]
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

    val sink = Sink foreach[ws.Message] {
      case ws.TextMessage.Strict(data) =>
        WebSocket synchronized {
          received set data
        }

      case message: ws.TextMessage =>
        message.textStream.runFold(new StringBuilder) {
          case (builder, data) => builder append data
        } foreach { builder =>
          WebSocket synchronized { received set builder.toString }
        }

      case _ =>
        close
    }

    val flow = Flow.fromSinkAndSourceMat(sink, source) { (future, _) =>
      future onComplete { _ => close }
    }

    Flow[ws.Message] via flow
  }
}
