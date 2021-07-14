name := "pong"

organization := "de.tuda.stg"

version := "0.0.0"

scalaVersion := "2.13.2"

scalacOptions ++= Seq("-feature", "-deprecation", "-unchecked", "-Ymacro-annotations")

resolvers += ("STG old bintray repo" at "http://www.st.informatik.tu-darmstadt.de/maven/").withAllowInsecureProtocol(true)

libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-swing" % "2.1.1",
  "com.typesafe.akka" %% "akka-remote" % "2.5.23",
  "de.tuda.stg" %% "scala-loci-lang" % "0.4.0",
  "de.tuda.stg" %% "scala-loci-serializer-upickle" % "0.4.0",
  "de.tuda.stg" %% "scala-loci-communicator-tcp" % "0.4.0",
  "de.tuda.stg" %% "scala-loci-lang-transmitter-rescala" % "0.4.0")
