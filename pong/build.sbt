name := "pong"

organization := "de.tuda.stg"

version := "0.0.0"

scalaVersion := "2.11.8"

scalacOptions ++= Seq("-feature", "-deprecation", "-unchecked")

resolvers += Resolver.bintrayRepo("stg-tud", "maven")

libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-swing" % "2.0.3",
  "com.typesafe.akka" %% "akka-remote" % "2.5.6",
  "de.tuda.stg" %% "scala-loci-core" % "0.1.0",
  "de.tuda.stg" %% "scala-loci-serializable-upickle" % "0.1.0",
  "de.tuda.stg" %% "scala-loci-network-tcp" % "0.1.0",
  "de.tuda.stg" %% "scala-loci-transmitter-basic" % "0.1.0",
  "de.tuda.stg" %% "scala-loci-transmitter-rescala" % "0.1.0")

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.patch)
