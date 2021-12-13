package chat
package util

import rescala.default._

class Benchmark extends FrontEnd {
  val storeLog = false

  val name = Var("Anonymous")
  val chatRequested = Evt[User]()
  val chatSelected = Evt[Chat]()
  val chatClosed = Evt[Chat]()
  val messageSent = Evt[String]()

  private val benchmark = new common.Benchmark(
    chatRequested.fire, chatSelected.fire, messageSent.fire)

  def users: Signal[Seq[User]] = ???
  def users_=(users: Signal[Seq[User]]) = { users observe benchmark.updateUsers }

  def chats: Signal[Seq[Chat]] = ???
  def chats_=(chats: Signal[Seq[Chat]]) = { chats observe benchmark.updateChats }

  def messages: Signal[Seq[Message]] = ???
  def messages_=(messages: Signal[Seq[Message]]) = { messages observe benchmark.updateMessages }

  def clearMessage: Event[Unit] = ???
  def clearMessage_=(clearMessage: Event[Unit]) = { }
}
