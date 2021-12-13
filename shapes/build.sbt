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
  "io.github.scala-loci" %%% "scala-loci-communicator-ws-webnative" % "0.5.0")

val librariesClientServed = Seq(
  dependencyOverrides += "org.webjars.bower" % "jquery" % "3.6.0",
  libraryDependencies += "org.webjars.bower" % "bootstrap" % "3.4.1",
  libraryDependencies += "org.webjars.bower" % "mjolnic-bootstrap-colorpicker" % "2.4.0",
  libraryDependencies += "org.webjars.bower" % "fabric" % "1.6.7")

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


lazy val shapes = (project in file(".")
  aggregate (
    shapesTraditional,
    shapesScalajsObserve, shapesScalajsReact,
    shapesActorObserve, shapesActorReact,
    shapesMultiReact, shapesMultiObserve))


lazy val shapesTraditional = (project in file("traditional")
  settings (commonDirectoriesScala, commonDirectoriesJS)
  settings (librariesAkkaHttp, librariesUpickle)
  settings (librariesClientServed: _*))


lazy val shapesScalajsObserve = (project in file("scalajs.observer") / ".all"
  settings (Compile / run :=
    ((shapesScalajsObserveJVM / Compile / run) dependsOn
     (shapesScalajsObserveJS / Compile / fastLinkJS)).evaluated)
  aggregate (shapesScalajsObserveJVM, shapesScalajsObserveJS))

lazy val shapesScalajsObserveJVM = (project in file("scalajs.observer") / "jvm"
  settings (sharedDirectories, commonDirectoriesScala)
  settings (librariesUpickle, librariesAkkaHttp)
  settings (librariesClientServed: _*)
  settings (
    Compile / mainClass := Some("shapes.Server"),
    Compile / resources ++=
      ((shapesScalajsObserveJS / Compile / crossTarget).value ** "*.js").get))

lazy val shapesScalajsObserveJS = (project in file("scalajs.observer") / "js"
  settings (sharedDirectories, commonDirectoriesScala, commonDirectoriesScalaJS)
  settings (librariesUpickle, librariesAkkaHttp, librariesDom)
  settings (
    Compile / mainClass := Some("shapes.Client"),
    Compile / scalaJSUseMainModuleInitializer := true)
  enablePlugins ScalaJSPlugin)


lazy val shapesScalajsReact = (project in file("scalajs.reactive") / ".all"
  settings (Compile / run :=
    ((shapesScalajsReactJVM / Compile / run) dependsOn
     (shapesScalajsReactJS / Compile / fastLinkJS)).evaluated)
  aggregate (shapesScalajsReactJVM, shapesScalajsReactJS))

lazy val shapesScalajsReactJVM = (project in file("scalajs.reactive") / "jvm"
  settings (sharedDirectories, commonDirectoriesScala)
  settings (librariesUpickle, librariesAkkaHttp, librariesRescala)
  settings (librariesClientServed: _*)
  settings (
    Compile / mainClass := Some("shapes.Server"),
    Compile / resources ++=
      ((shapesScalajsReactJS / Compile / crossTarget).value ** "*.js").get))

lazy val shapesScalajsReactJS = (project in file("scalajs.reactive") / "js"
  settings (sharedDirectories, commonDirectoriesScala, commonDirectoriesScalaJS)
  settings (librariesUpickle, librariesAkkaHttp, librariesRescala, librariesDom)
  settings (
    Compile / mainClass := Some("shapes.Client"),
    Compile / scalaJSUseMainModuleInitializer := true)
  enablePlugins ScalaJSPlugin)


lazy val shapesActorObserve = (project in file("actor.observer") / ".all"
  settings (Compile / run :=
    ((shapesActorObserveJVM / Compile / run) dependsOn
     (shapesActorObserveJS / Compile / fastLinkJS)).evaluated)
  aggregate (shapesActorObserveJVM, shapesActorObserveJS))

lazy val shapesActorObserveJVM = (project in file("actor.observer") / "jvm"
  settings (sharedDirectories, commonDirectoriesScala)
  settings (librariesUpickle, librariesAkkaHttp)
  settings (librariesClientServed: _*)
  settings (
    Compile / mainClass := Some("shapes.Server"),
    Compile / resources ++=
      ((shapesActorObserveJS / Compile / crossTarget).value ** "*.js").get))

lazy val shapesActorObserveJS = (project in file("actor.observer") / "js"
  settings (sharedDirectories, commonDirectoriesScala, commonDirectoriesScalaJS)
  settings (librariesUpickle, librariesAkkaHttp, librariesAkkaJs, librariesDom)
  settings (
    Compile / mainClass := Some("shapes.Client"),
    Compile / scalaJSUseMainModuleInitializer := true)
  enablePlugins ScalaJSPlugin)


lazy val shapesActorReact = (project in file("actor.reactive") / ".all"
  settings (Compile / run :=
    ((shapesActorReactJVM / Compile / run) dependsOn
     (shapesActorReactJS / Compile / fastLinkJS)).evaluated)
  aggregate (shapesActorReactJVM, shapesActorReactJS))

lazy val shapesActorReactJVM = (project in file("actor.reactive") / "jvm"
  settings (sharedDirectories, commonDirectoriesScala)
  settings (librariesUpickle, librariesAkkaHttp, librariesRescala)
  settings (librariesClientServed: _*)
  settings (
    Compile / mainClass := Some("shapes.Server"),
    Compile / resources ++=
      ((shapesActorReactJS / Compile / crossTarget).value ** "*.js").get))

lazy val shapesActorReactJS = (project in file("actor.reactive") / "js"
  settings (sharedDirectories, commonDirectoriesScala, commonDirectoriesScalaJS)
  settings (librariesUpickle, librariesAkkaHttp, librariesAkkaJs, librariesRescala, librariesDom)
  settings (
    Compile / mainClass := Some("shapes.Client"),
    Compile / scalaJSUseMainModuleInitializer := true)
  enablePlugins ScalaJSPlugin)


lazy val shapesMultiObserve = (project in file("multitier.observer") / ".all"
  settings (Compile / run :=
    ((shapesMultiObserveJVM / Compile / run) dependsOn
     (shapesMultiObserveJS / Compile / fastLinkJS)).evaluated)
  aggregate (shapesMultiObserveJVM, shapesMultiObserveJS))

lazy val shapesMultiObserveJVM = (project in file("multitier.observer") / ".jvm"
  settings (commonDirectoriesScala, commonDirectoriesScalaJS)
  settings (settingsMultitier: _*)
  settings (librariesClientServed: _*)
  settings (
    Compile / mainClass := Some("shapes.Server"),
    Compile / resources ++=
      ((shapesMultiObserveJS / Compile / crossTarget).value ** "*.js").get))

lazy val shapesMultiObserveJS = (project in file("multitier.observer") / ".js"
  settings (commonDirectoriesScala, commonDirectoriesScalaJS)
  settings (settingsMultitier: _*)
  settings (
    Compile / mainClass := Some("shapes.Client"),
    Compile / scalaJSUseMainModuleInitializer := true)
  enablePlugins ScalaJSPlugin)


lazy val shapesMultiReact = (project in file("multitier.reactive") / ".all"
  settings (Compile / run :=
    ((shapesMultiReactJVM / Compile / run) dependsOn
     (shapesMultiReactJS / Compile / fastLinkJS)).evaluated)
  aggregate (shapesMultiReactJVM, shapesMultiReactJS))

lazy val shapesMultiReactJVM = (project in file("multitier.reactive") / ".jvm"
  settings (commonDirectoriesScala, commonDirectoriesScalaJS)
  settings (settingsMultitier: _*)
  settings (librariesClientServed: _*)
  settings (
    Compile / mainClass := Some("shapes.Server"),
    Compile / resources ++=
      ((shapesMultiReactJS / Compile / crossTarget).value ** "*.js").get))


lazy val shapesMultiReactJS = (project in file("multitier.reactive") / ".js"
  settings (commonDirectoriesScala, commonDirectoriesScalaJS)
  settings (settingsMultitier: _*)
  settings (
    Compile / mainClass := Some("shapes.Client"),
    Compile / scalaJSUseMainModuleInitializer := true)
  enablePlugins ScalaJSPlugin)
