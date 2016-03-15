package chat
package util

import retier.Peer
import retier.Remote
import rescala.Signal

import upickle.Js
import upickle.default._


final case class User(id: Int, name: String)

final case class Chat(id: Int, name: String, unread: Int, active: Boolean)

final case class ChatLog(node: Remote[Peer], id: Int, name: Signal[String],
  unread: Signal[Int], log: Signal[Seq[Message]])

final case class Message(content: String, own: Boolean)


/*
 * the code commented out below should suffice but fails at runtime
 * so we need to use some workarounds
 * this should probably be fixed in the upickle library
 */

object User {
//  implicit val reader: Reader[User] = implicitly[Reader[User]]
//  implicit val writer: Writer[User] = implicitly[Writer[User]]

  implicit val reader: Reader[User] = Reader { case value =>
    val (id, name) = readJs[(Int, String)](value)
    User(id, name)
  }
  implicit val writer: Writer[User] = Writer {
    case User(id, name) => writeJs((id, name))
  }
}
