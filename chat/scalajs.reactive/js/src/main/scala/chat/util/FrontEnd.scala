package chat
package util

import rescala.default._

trait FrontEnd {
  val storeLog: Boolean

  val name: Signal[String]
  val chatRequested: Event[User]
  val chatSelected: Event[Chat]
  val chatClosed: Event[Chat]
  val messageSent: Event[String]

  def users: Signal[Seq[User]]
  def users_=(users: Signal[Seq[User]]): Unit

  def chats: Signal[Seq[Chat]]
  def chats_=(chats: Signal[Seq[Chat]]): Unit

  def messages: Signal[Seq[Message]]
  def messages_=(messages: Signal[Seq[Message]]): Unit

  def clearMessage: Event[Unit]
  def clearMessage_=(clearMessage: Event[Unit]): Unit
}
