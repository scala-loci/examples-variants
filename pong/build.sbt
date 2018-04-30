name := "pong"

organization := "de.tuda.stg"

version := "0.0.0"

scalaVersion := "2.12.6"

scalacOptions ++= Seq("-feature", "-deprecation", "-unchecked")

resolvers += Resolver.bintrayRepo("stg-tud", "maven")

libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-swing" % "2.0.3",
  "com.typesafe.akka" %% "akka-remote" % "2.5.12",
  "de.tuda.stg" %% "scala-loci-lang" % "0.2.0",
  "de.tuda.stg" %% "scala-loci-serializer-upickle" % "0.2.0",
  "de.tuda.stg" %% "scala-loci-communicator-tcp" % "0.2.0",
  "de.tuda.stg" %% "scala-loci-lang-transmitter-basic" % "0.2.0",
  "de.tuda.stg" %% "scala-loci-lang-transmitter-rescala" % "0.2.0")

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.patch)
