ThisBuild / organization := "io.github.scala-loci"

ThisBuild / version := "0.0.0"

ThisBuild / scalaVersion := "2.13.7"

ThisBuild / scalacOptions ++= Seq("-feature", "-deprecation", "-unchecked", "-Xlint")


val librariesRescala = libraryDependencies +=
  "de.tu-darmstadt.stg" %%% "rescala" % "0.31.0"

val librariesUpickle = libraryDependencies +=
  "com.lihaoyi" %%% "upickle" % "1.4.2"

val librariesAkkaHttp = libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % "10.2.7",
  "com.typesafe.akka" %% "akka-stream" % "2.6.17")

val librariesAkkaJs = Seq(
  dependencyOverrides += "org.scala-lang.modules" %% "scala-java8-compat" % "1.0.0",
  libraryDependencies += "org.akka-js" %%% "akkajsactor" % "2.2.6.14")

val librariesDom = libraryDependencies +=
  "org.scala-js" %%% "scalajs-dom" % "2.0.0"

val librariesMultitier = libraryDependencies ++= Seq(
  "io.github.scala-loci" %%% "scala-loci-language" % "0.5.0" % "compile-internal",
  "io.github.scala-loci" %%% "scala-loci-language-runtime" % "0.5.0",
  "io.github.scala-loci" %%% "scala-loci-language-transmitter-rescala" % "0.5.0",
  "io.github.scala-loci" %%% "scala-loci-serializer-upickle" % "0.5.0",
  "io.github.scala-loci" %%% "scala-loci-communicator-ws-akka" % "0.5.0",
  "io.github.scala-loci" %%% "scala-loci-communicator-ws-webnative" % "0.5.0",
  "io.github.scala-loci" %%% "scala-loci-communicator-webrtc" % "0.5.0")

val librariesClientServed = Seq(
  dependencyOverrides += "org.webjars.bower" % "jquery" % "3.6.0",
  libraryDependencies += "org.webjars.bower" % "bootstrap" % "3.4.1")

val macroparadise = scalacOptions += "-Ymacro-annotations"


def standardDirectoryLayout(directory: File): Seq[Def.Setting[_]] =
  standardDirectoryLayout(Def.setting { directory })

def standardDirectoryLayout(directory: Def.Initialize[File]): Seq[Def.Setting[_]] = Seq(
  Compile / unmanagedSourceDirectories += directory.value / "src" / "main" / "scala",
  Compile / unmanagedResourceDirectories += directory.value / "src" / "main" / "resources",
  Test / unmanagedSourceDirectories += directory.value / "src" / "test" / "scala",
  Test / unmanagedResourceDirectories += directory.value / "src" / "test" / "resources")

val commonDirectoriesScala =
  standardDirectoryLayout(file("common").getAbsoluteFile / "scala")

val commonDirectoriesJS =
  standardDirectoryLayout(file("common").getAbsoluteFile / "js")

val commonDirectoriesScalaJS =
  standardDirectoryLayout(file("common").getAbsoluteFile / "scalajs")

val sharedDirectories =
  standardDirectoryLayout(Def.setting { baseDirectory.value.getParentFile / "shared" })

val sharedMultitierDirectories =
  standardDirectoryLayout(Def.setting { baseDirectory.value.getParentFile })

val settingsMultitier =
  sharedMultitierDirectories ++ Seq(macroparadise, librariesMultitier, librariesAkkaHttp)


lazy val chat = (project in file(".")
  aggregate (
    chatTraditional,
    chatScalajsObserve, chatScalajsReact,
    chatActorObserve, chatActorReact,
    chatMultiReact, chatMultiObserve))


lazy val chatTraditional = (project in file("traditional")
  settings (commonDirectoriesScala, commonDirectoriesJS)
  settings (librariesAkkaHttp, librariesUpickle)
  settings (librariesClientServed: _*))


lazy val chatScalajsObserve = (project in file("scalajs.observer") / ".all"
  settings (Compile / run :=
    ((chatScalajsObserveJVM / Compile / run) dependsOn
     (chatScalajsObserveJS / Compile / fastLinkJS)).evaluated)
  aggregate (chatScalajsObserveJVM, chatScalajsObserveJS))

lazy val chatScalajsObserveJVM = (project in file("scalajs.observer") / "jvm"
  settings (sharedDirectories, commonDirectoriesScala)
  settings (librariesUpickle, librariesAkkaHttp)
  settings (librariesClientServed: _*)
  settings (
    Compile / mainClass := Some("chat.Registry"),
    Compile / resources ++=
      ((chatScalajsObserveJS / Compile / crossTarget).value ** "*.js").get))

lazy val chatScalajsObserveJS = (project in file("scalajs.observer") / "js"
  settings (sharedDirectories, commonDirectoriesScala, commonDirectoriesScalaJS)
  settings (librariesUpickle, librariesAkkaHttp, librariesDom)
  settings (
    Compile / mainClass := Some("chat.Node"),
    Compile / scalaJSUseMainModuleInitializer := true)
  enablePlugins ScalaJSPlugin)


lazy val chatScalajsReact = (project in file("scalajs.reactive") / ".all"
  settings (Compile / run :=
    ((chatScalajsReactJVM / Compile / run) dependsOn
     (chatScalajsReactJS / Compile / fastLinkJS)).evaluated)
  aggregate (chatScalajsReactJVM, chatScalajsReactJS))

lazy val chatScalajsReactJVM = (project in file("scalajs.reactive") / "jvm"
  settings (sharedDirectories, commonDirectoriesScala)
  settings (librariesUpickle, librariesAkkaHttp, librariesRescala)
  settings (librariesClientServed: _*)
  settings (
    Compile / mainClass := Some("chat.Registry"),
    Compile / resources ++=
      ((chatScalajsReactJS / Compile / crossTarget).value ** "*.js").get))

lazy val chatScalajsReactJS = (project in file("scalajs.reactive") / "js"
  settings (sharedDirectories, commonDirectoriesScala, commonDirectoriesScalaJS)
  settings (librariesUpickle, librariesAkkaHttp, librariesRescala, librariesDom)
  settings (
    Compile / mainClass := Some("chat.Node"),
    Compile / scalaJSUseMainModuleInitializer := true)
  enablePlugins ScalaJSPlugin)


lazy val chatActorObserve = (project in file("actor.observer") / ".all"
  settings (Compile / run :=
    ((chatActorObserveJVM / Compile / run) dependsOn
     (chatActorObserveJS / Compile / fastLinkJS)).evaluated)
  aggregate (chatActorObserveJVM, chatActorObserveJS))

lazy val chatActorObserveJVM = (project in file("actor.observer") / "jvm"
  settings (sharedDirectories, commonDirectoriesScala)
  settings (librariesUpickle, librariesAkkaHttp)
  settings (librariesClientServed: _*)
  settings (
    Compile / mainClass := Some("chat.Registry"),
    Compile / resources ++=
      ((chatActorObserveJS / Compile / crossTarget).value ** "*.js").get))

lazy val chatActorObserveJS = (project in file("actor.observer") / "js"
  settings (sharedDirectories, commonDirectoriesScala, commonDirectoriesScalaJS)
  settings (librariesUpickle, librariesAkkaHttp, librariesAkkaJs, librariesDom)
  settings (
    Compile / mainClass := Some("chat.Node"),
    Compile / scalaJSUseMainModuleInitializer := true)
  enablePlugins ScalaJSPlugin)


lazy val chatActorReact = (project in file("actor.reactive") / ".all"
  settings (Compile / run :=
    ((chatActorReactJVM / Compile / run) dependsOn
     (chatActorReactJS / Compile / fastLinkJS)).evaluated)
  aggregate (chatActorReactJVM, chatActorReactJS))

lazy val chatActorReactJVM = (project in file("actor.reactive") / "jvm"
  settings (sharedDirectories, commonDirectoriesScala)
  settings (librariesUpickle, librariesAkkaHttp, librariesRescala)
  settings (librariesClientServed: _*)
  settings (
    Compile / mainClass := Some("chat.Registry"),
    Compile / resources ++=
      ((chatActorReactJS / Compile / crossTarget).value ** "*.js").get))

lazy val chatActorReactJS = (project in file("actor.reactive") / "js"
  settings (sharedDirectories, commonDirectoriesScala, commonDirectoriesScalaJS)
  settings (librariesUpickle, librariesAkkaHttp, librariesAkkaJs, librariesRescala, librariesDom)
  settings (
    Compile / mainClass := Some("chat.Node"),
    Compile / scalaJSUseMainModuleInitializer := true)
  enablePlugins ScalaJSPlugin)


lazy val chatMultiObserve = (project in file("multitier.observer") / ".all"
  settings (Compile / run :=
    ((chatMultiObserveJVM / Compile / run) dependsOn
     (chatMultiObserveJS / Compile / fastLinkJS)).evaluated)
  aggregate (chatMultiObserveJVM, chatMultiObserveJS))

lazy val chatMultiObserveJVM = (project in file("multitier.observer") / ".jvm"
  settings (commonDirectoriesScala, commonDirectoriesScalaJS)
  settings (settingsMultitier: _*)
  settings (librariesClientServed: _*)
  settings (
    Compile / mainClass := Some("chat.Registry"),
    Compile / resources ++=
      ((chatMultiObserveJS / Compile / crossTarget).value ** "*.js").get))

lazy val chatMultiObserveJS = (project in file("multitier.observer") / ".js"
  settings (commonDirectoriesScala, commonDirectoriesScalaJS)
  settings (settingsMultitier: _*)
  settings (
    Compile / mainClass := Some("chat.Node"),
    Compile / scalaJSUseMainModuleInitializer := true)
  enablePlugins ScalaJSPlugin)


lazy val chatMultiReact = (project in file("multitier.reactive") / ".all"
  settings (Compile / run :=
    ((chatMultiReactJVM / Compile / run) dependsOn
     (chatMultiReactJS / Compile / fastLinkJS)).evaluated)
  aggregate (chatMultiReactJVM, chatMultiReactJS))

lazy val chatMultiReactJVM = (project in file("multitier.reactive") / ".jvm"
  settings (commonDirectoriesScala, commonDirectoriesScalaJS)
  settings (settingsMultitier: _*)
  settings (librariesClientServed: _*)
  settings (
    Compile / mainClass := Some("chat.Registry"),
    Compile / resources ++=
      ((chatMultiReactJS / Compile / crossTarget).value ** "*.js").get))

lazy val chatMultiReactJS = (project in file("multitier.reactive") / ".js"
  settings (commonDirectoriesScala, commonDirectoriesScalaJS)
  settings (settingsMultitier: _*)
  settings (
    Compile / mainClass := Some("chat.Node"),
    Compile / scalaJSUseMainModuleInitializer := true)
  enablePlugins ScalaJSPlugin)
