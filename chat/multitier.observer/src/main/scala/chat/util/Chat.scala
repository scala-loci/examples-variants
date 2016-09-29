package chat
package util

import retier.Peer
import retier.Remote

final case class User(id: Int, name: String)

final case class Chat(id: Int, name: String, unread: Int, active: Boolean)

final case class ChatLog(node: Remote[Peer], id: Int, name: Observable[String],
  unread: Observable[Int], log: Observable[Seq[Message]])

final case class Message(content: String, own: Boolean)
