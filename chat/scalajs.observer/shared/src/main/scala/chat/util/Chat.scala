package chat
package util

import upickle.default._

final case class User(id: Int, name: String)

final case class Chat(id: Int, name: String, unread: Int, active: Boolean)

final case class ChatLog(id: Int, name: Observable[String],
  unread: Observable[Int], log: Observable[Seq[Message]])

final case class Message(content: String, own: Boolean)


sealed trait ServerMessage
sealed trait ClientMessage
sealed trait PeerMessage

@key("Content") final case class Content(content: String)
  extends PeerMessage

@key("ChangeName") final case class ChangeName(name: String)
  extends PeerMessage with ServerMessage

@key("Connect") final case class Connect(id: Int, session: Either[(String, String), (String, String, Double)])
  extends ServerMessage with ClientMessage

@key("Users") final case class Users(users: Seq[User])
  extends ClientMessage
