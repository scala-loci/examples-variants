package chat
package util

import upickle.implicits.key

final case class User(id: Int, name: String)


sealed trait Message

@key("ChangeName") final case class ChangeName(name: String) extends Message

@key("Connect") final case class Connect(id: Int, sdp: String, ice: String) extends Message
