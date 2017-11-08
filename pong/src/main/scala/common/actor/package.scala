package common

import com.typesafe.config.ConfigFactory

package object actor {
  def remoteConfig(host: String, port: Int) = ConfigFactory parseString
    s"""
    akka {
      actor {
        provider = remote
      }
      remote {
        enabled-transports = ["akka.remote.netty.tcp"]
        netty.tcp {
          hostname = "$host"
          port = $port
        }
      }
    }
    """
}
