package common

import com.typesafe.config.ConfigFactory

package object actor {
  def remoteConfig(host: String, port: Int) = ConfigFactory.parseString(
    s"""
    akka {
      actor {
        provider = remote
        allow-java-serialization = on
      }
      remote {
        artery {
          transport = tcp
          canonical.hostname = "$host"
          canonical.port = $port
        }
      }
    }
    """)
}
