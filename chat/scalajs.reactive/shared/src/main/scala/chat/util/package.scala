package chat

import upickle.default._

package object util {
  implicit val userPickler: ReadWriter[User] = macroRW[User]

  implicit val contentMessagePickler: ReadWriter[Content] = macroRW[Content]
  implicit val changeNamePickler: ReadWriter[ChangeName] = macroRW[ChangeName]
  implicit val connectPickler: ReadWriter[Connect] = macroRW[Connect]
  implicit val usersPickler: ReadWriter[Users] = macroRW[Users]

  implicit val serverMessagePickler: ReadWriter[ServerMessage] = macroRW[ServerMessage]
  implicit val clientMessagePickler: ReadWriter[ClientMessage] = macroRW[ClientMessage]
  implicit val peerMessagePickler: ReadWriter[PeerMessage] = macroRW[PeerMessage]
}
