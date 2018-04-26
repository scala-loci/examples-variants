package chat

import util._

import upickle.default._

import akka.actor._

import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.experimental.webrtc._

import scala.concurrent.ExecutionContext.Implicits.global

import WebRTCRemoteActor._


object WebRTCRemoteActor {
  case class Session(id: Int, sdp: RTCSessionDescription)
  case class Candidate(id: Int, candidate: RTCIceCandidate)
  case class UserMessage(id: Int, message: PeerMessage)
  case class UserConnected(id: Int)
  case class UserDisconnected(id: Int)

  case class ApplySession(sdp: RTCSessionDescription, createAnswer: Boolean)
  case class ApplyCandidate(candidate: RTCIceCandidate)
  case object Close
}


class WebRTCRemoteActor(actorRef: ActorRef, id: Int, channelLabel: String, createOffer: Boolean) extends Actor {
  val peerConnection =
    new RTCPeerConnection(RTCConfiguration(iceServers = js.Array[RTCIceServer]()))

  var peerChannel = Option.empty[RTCDataChannel]

  peerConnection.onicecandidate = { event: RTCPeerConnectionIceEvent => // #CB-ActorImpl
    if (event.candidate != null)
      actorRef ! Candidate(id, event.candidate)
  }

  if (createOffer) {
    handleRTCDataChannel(peerConnection createDataChannel (channelLabel, RTCDataChannelInit()))

    peerConnection.createOffer().toFuture foreach { sdp => // #CB-ActorImpl
      (peerConnection setLocalDescription sdp).toFuture foreach { _ =>
        actorRef ! Session(id, sdp)
      }
    }
  }
  else {
    peerConnection.ondatachannel = { event: RTCDataChannelEvent => // #CB-ActorImpl
      if (event.channel.label == channelLabel)
        handleRTCDataChannel(event.channel)
    }
  }

  def handleRTCDataChannel(channel: RTCDataChannel) = {
    peerChannel = Some(channel)

    channel.onmessage = { event: dom.MessageEvent => // #CB-ActorImpl
      actorRef ! UserMessage(id, read[PeerMessage](event.data.toString))
    }

    channel.onclose = { event: dom.Event => disconnect } // #CB-ActorImpl

    channel.onerror = { event: dom.Event => disconnect } // #CB-ActorImpl

    if (channel.readyState == RTCDataChannelState.connecting)
      channel.onopen = { _: dom.Event => connect } // #CB-ActorImpl
    else if (channel.readyState == RTCDataChannelState.open)
      connect

    def connect() = actorRef ! UserConnected(id)

    def disconnect() = actorRef ! UserDisconnected(id)
  }

  def receive = {
    case ApplySession(sdp, createAnswer) => // #CB-ActorImpl
      if (createAnswer)
        (peerConnection setRemoteDescription sdp).toFuture foreach { _ =>
          peerConnection.createAnswer().toFuture foreach { sdp =>
            (peerConnection setLocalDescription sdp).toFuture foreach { _ =>
              actorRef ! Session(id, sdp)
            }
          }
        }
      else
        peerConnection setRemoteDescription sdp

    case ApplyCandidate(candidate) => // #CB-ActorImpl
      peerConnection addIceCandidate candidate

    case Close => // #CB-ActorImpl
      peerChannel foreach { _.close }

    case message: PeerMessage => // #CB-ActorImpl
      peerChannel foreach { _ send write(message) }
  }
}
