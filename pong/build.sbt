name := "pong"

organization := "io.github.scala-loci"

version := "0.0.0"

scalaVersion := "2.13.7"

scalacOptions ++= Seq("-feature", "-deprecation", "-unchecked", "-Ymacro-annotations")

libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-swing" % "3.0.0",
  "com.typesafe.akka" %% "akka-remote" % "2.6.17",
  "io.github.scala-loci" %% "scala-loci-language" % "0.5.0" % "compile-internal",
  "io.github.scala-loci" %% "scala-loci-language-runtime" % "0.5.0",
  "io.github.scala-loci" %% "scala-loci-language-transmitter-rescala" % "0.5.0",
  "io.github.scala-loci" %% "scala-loci-serializer-upickle" % "0.5.0",
  "io.github.scala-loci" %% "scala-loci-communicator-tcp" % "0.5.0")
