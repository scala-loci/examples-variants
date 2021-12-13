package chat
package util

import loci.language._
import rescala.default._

final case class User(id: Int, name: String)

final case class Chat(id: Int, name: String, unread: Int, active: Boolean)

final case class ChatLog(node: Remote[_], id: Int, name: Signal[String],
  unread: Signal[Int], log: Signal[Seq[Message]])

final case class Message(content: String, own: Boolean)
