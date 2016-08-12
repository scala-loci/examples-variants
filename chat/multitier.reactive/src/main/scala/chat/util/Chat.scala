package chat
package util

import rescala._
import retier.Peer
import retier.Remote

final case class User(id: Int, name: String)

final case class Chat(id: Int, name: String, unread: Int, active: Boolean)

final case class ChatLog(node: Remote[Peer], id: Int, name: Signal[String],
  unread: Signal[Int], log: Signal[Seq[Message]])

final case class Message(content: String, own: Boolean)
