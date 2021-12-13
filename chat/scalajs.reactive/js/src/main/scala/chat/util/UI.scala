package chat
package util

import rescala.default._

class UI extends FrontEnd {
  val storeLog = true

  val name = Var("Anonymous")
  val chatRequested = Evt[User]()
  val chatSelected = Evt[Chat]()
  val chatClosed = Evt[Chat]()
  val messageSent = Evt[String]()

  private val ui = new common.UI(name.readValueOnce, name.set,
    chatRequested.fire, chatSelected.fire,
    chatClosed.fire, messageSent.fire)

  def users: Signal[Seq[User]] = ???
  def users_=(users: Signal[Seq[User]]) = { users observe ui.updateUsers }

  def chats: Signal[Seq[Chat]] = ???
  def chats_=(chats: Signal[Seq[Chat]]) = { chats observe ui.updateChats }

  def messages: Signal[Seq[Message]] = ???
  def messages_=(messages: Signal[Seq[Message]]) = { messages observe ui.updateMessages }

  def clearMessage: Event[Unit] = ???
  def clearMessage_=(clearMessage: Event[Unit]) = { clearMessage observe { _ => ui.clearMessage() } }
}
