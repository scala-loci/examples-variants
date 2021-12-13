package chat
package util

class UI extends FrontEnd {
  val storeLog = true

  val name = Observable("Anonymous")
  val chatRequested = Observable(User(0, ""))
  val chatSelected = Observable(Chat(0, "", 0, false))
  val chatClosed = Observable(Chat(0, "", 0, false))
  val messageSent = Observable("")

  private val ui = new common.UI(name.get, name.set,
    chatRequested.set, chatSelected.set, chatClosed.set, messageSent.set)

  def updateUsers(users: Seq[User]) = ui.updateUsers(users)
  def updateChats(chats: Seq[Chat]) = ui.updateChats(chats)
  def updateMessages(messages: Seq[Message]) = ui.updateMessages(messages)
  def clearMessage() = ui.clearMessage()
}
