package chat
package util

trait FrontEnd {
  val storeLog: Boolean

  val name: Observable[String]
  val chatRequested: Observable[User]
  val chatSelected: Observable[Chat]
  val chatClosed: Observable[Chat]
  val messageSent: Observable[String]

  def updateUsers(users: Seq[User]): Unit
  def updateChats(chats: Seq[Chat]): Unit
  def updateMessages(messages: Seq[Message]): Unit
  def clearMessage: Unit
}
