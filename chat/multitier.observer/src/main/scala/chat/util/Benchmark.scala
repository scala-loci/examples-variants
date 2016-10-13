package chat
package util

class Benchmark extends FrontEnd {
  val storeLog = false

  val name = Observable("Anonymous")
  val chatRequested = Observable(User(0, ""))
  val chatSelected = Observable(Chat(0, "", 0, false))
  val chatClosed = Observable(Chat(0, "", 0, false))
  val messageSent = Observable("")

  private val benchmark = new common.Benchmark(
  	chatRequested.set, chatSelected.set, messageSent.set)

  def updateUsers(users: Seq[User]) = benchmark updateUsers users
  def updateChats(chats: Seq[Chat]) = benchmark updateChats chats
  def updateMessages(messages: Seq[Message]) = benchmark updateMessages messages
  def clearMessage = { }
}
