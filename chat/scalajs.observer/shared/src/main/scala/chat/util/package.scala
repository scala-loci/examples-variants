package chat

import upickle.default._

package object util {
  implicit val serverMessagePickler: ReadWriter[ServerMessage] = macroRW[ServerMessage]
  implicit val clientMessagePickler: ReadWriter[ClientMessage] = macroRW[ClientMessage]
  implicit val peerMessagePickler: ReadWriter[PeerMessage] = macroRW[PeerMessage]
}
