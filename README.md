# [ScalaLoci](http://scala-loci.github.io): Variants of Example Applications

This repository contains a comparison of different variants of the same software.


## Applications

* [Pong](pong) implements the arcade Pong game. Additionally to the distributed
  multiplayer versions, a local baseline is provided (where the user plays
  against the computer). The distributed versions adopt a client–server model.
  Both the server and the clients run on the JVM.

* [Shapes](shapes) is a collaborative drawing web application, where clients
  connect to a central server. The server runs on the JVM while clients run in
  the web browser.

* [P2P Chat](chat) is a P2P web chat application, which supports multiple
  one-to-one chat sessions. Peers communicate directly in a P2P fashion after
  discovering via a registry. The registry runs on the JVM while peers run in
  the web browser.


## Variants

The communication mechanism is in the left column and the event processing strategy is in top row.

              | **reactive** ¹                                   | **observer** ¹                                   | **observer (JS)** ²
------------- | ------------------------------------------------ | ------------------------------------------------ | ----------------------------
**(local)**   | [Pong](pong/src/main/scala/local/reactive)       | [Pong](pong/src/main/scala/local/observer)       |
**RMI**       | [Pong](pong/src/main/scala/distributed/reactive) | [Pong](pong/src/main/scala/distributed/observer) |
**WebSocket** | [Shapes](shapes/scalajs.reactive)                | [Shapes](shapes/scalajs.observer)                | [Shapes](shapes/traditional)
**WebRTC**    | [P2P Chat](chat/scalajs.reactive)                | [P2P Chat](chat/scalajs.observer)                | [P2P Chat](chat/traditional)
**Akka**      | [Pong](pong/src/main/scala/actor/reactive)       | [Pong](pong/src/main/scala/actor/observer)       |
              | [Shapes](shapes/actor.reactive)                  | [Shapes](shapes/actor.observer)                  |
              | [P2P Chat](chat/actor.reactive)                  | [P2P Chat](chat/actor.observer)                  |
**ScalaLoci** | [Pong](pong/src/main/scala/multitier/reactive)   | [Pong](pong/src/main/scala/multitier/observer)   |
              | [Shapes](shapes/multitier.reactive)              | [Shapes](shapes/multitier.observer)              |
              | [P2P Chat](chat/multitier.reactive)              | [P2P Chat](chat/multitier.observer)              |

¹ All code is in Scala or ScalaLoci. The client is compiled to JavaScript via [Scala.js](http://www.scala-js.org/).

² Uses handwritten JavaScript for the client-side, Scala for the server side.
