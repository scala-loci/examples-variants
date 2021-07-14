organization in ThisBuild := "de.tuda.stg"

version in ThisBuild := "0.0.0"

scalaVersion in ThisBuild := "2.13.2"

scalacOptions in ThisBuild ++= Seq("-feature", "-deprecation", "-unchecked", "-Xlint")

resolvers in ThisBuild += ("STG old bintray repo" at "http://www.st.informatik.tu-darmstadt.de/maven/").withAllowInsecureProtocol(true)


val librariesRescala = libraryDependencies +=
  "de.tuda.stg" %%% "rescala" % "0.30.0"

val librariesUpickle = libraryDependencies +=
  "com.lihaoyi" %%% "upickle" % "1.1.0"

val librariesAkkaHttp = libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % "10.1.8",
  "com.typesafe.akka" %% "akka-stream" % "2.5.23")

val librariesAkkaJs = libraryDependencies +=
  "org.akka-js" %%% "akkajsactor" % "2.2.6.5"

val librariesDom = libraryDependencies +=
  "org.scala-js" %%% "scalajs-dom" % "1.0.0"

val librariesMultitier = libraryDependencies ++= Seq(
  "de.tuda.stg" %%% "scala-loci-lang" % "0.4.0",
  "de.tuda.stg" %%% "scala-loci-serializer-upickle" % "0.4.0",
  "de.tuda.stg" %%% "scala-loci-communicator-ws-akka" % "0.4.0",
  "de.tuda.stg" %%% "scala-loci-lang-transmitter-rescala" % "0.4.0")

val librariesClientServed = Seq(
  dependencyOverrides += "org.webjars.bower" % "jquery" % "1.12.4",
  dependencyOverrides += "org.webjars.bower" % "bootstrap" % "3.3.7",
  libraryDependencies += "org.webjars.bower" % "mjolnic-bootstrap-colorpicker" % "2.3.0",
  libraryDependencies += "org.webjars.bower" % "fabric" % "1.6.7")

val macroparadise = scalacOptions += "-Ymacro-annotations"


def standardDirectoryLayout(directory: File): Seq[Def.Setting[_]] =
  standardDirectoryLayout(Def.setting { directory })

def standardDirectoryLayout(directory: Def.Initialize[File]): Seq[Def.Setting[_]] = Seq(
  unmanagedSourceDirectories in Compile += directory.value / "src" / "main" / "scala",
  unmanagedResourceDirectories in Compile += directory.value / "src" / "main" / "resources",
  unmanagedSourceDirectories in Test += directory.value / "src" / "test" / "scala",
  unmanagedResourceDirectories in Test += directory.value / "src" / "test" / "resources")

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
  settings (run in Compile :=
    ((run in Compile in shapesScalajsObserveJVM) dependsOn
     (fullOptJS in Compile in shapesScalajsObserveJS)).evaluated)
  aggregate (shapesScalajsObserveJVM, shapesScalajsObserveJS))

lazy val shapesScalajsObserveJVM = (project in file("scalajs.observer") / "jvm"
  settings (sharedDirectories, commonDirectoriesScala)
  settings (librariesUpickle, librariesAkkaHttp)
  settings (librariesClientServed: _*)
  settings (
    mainClass in Compile := Some("shapes.Server"),
    resources in Compile ++=
      ((crossTarget in Compile in shapesScalajsObserveJS).value ** "*.js").get))

lazy val shapesScalajsObserveJS = (project in file("scalajs.observer") / "js"
  settings (sharedDirectories, commonDirectoriesScala, commonDirectoriesScalaJS)
  settings (librariesUpickle, librariesAkkaHttp, librariesDom)
  settings (
    mainClass in Compile := Some("shapes.Client"),
    scalaJSUseMainModuleInitializer in Compile := true)
  enablePlugins ScalaJSPlugin)


lazy val shapesScalajsReact = (project in file("scalajs.reactive") / ".all"
  settings (run in Compile :=
    ((run in Compile in shapesScalajsReactJVM) dependsOn
     (fullOptJS in Compile in shapesScalajsReactJS)).evaluated)
  aggregate (shapesScalajsReactJVM, shapesScalajsReactJS))

lazy val shapesScalajsReactJVM = (project in file("scalajs.reactive") / "jvm"
  settings (sharedDirectories, commonDirectoriesScala)
  settings (librariesUpickle, librariesAkkaHttp, librariesRescala)
  settings (librariesClientServed: _*)
  settings (
    mainClass in Compile := Some("shapes.Server"),
    resources in Compile ++=
      ((crossTarget in Compile in shapesScalajsReactJS).value ** "*.js").get))

lazy val shapesScalajsReactJS = (project in file("scalajs.reactive") / "js"
  settings (sharedDirectories, commonDirectoriesScala, commonDirectoriesScalaJS)
  settings (librariesUpickle, librariesAkkaHttp, librariesRescala, librariesDom)
  settings (
    mainClass in Compile := Some("shapes.Client"),
    scalaJSUseMainModuleInitializer in Compile := true)
  enablePlugins ScalaJSPlugin)


lazy val shapesActorObserve = (project in file("actor.observer") / ".all"
  settings (run in Compile :=
    ((run in Compile in shapesActorObserveJVM) dependsOn
     (fullOptJS in Compile in shapesActorObserveJS)).evaluated)
  aggregate (shapesActorObserveJVM, shapesActorObserveJS))

lazy val shapesActorObserveJVM = (project in file("actor.observer") / "jvm"
  settings (sharedDirectories, commonDirectoriesScala)
  settings (librariesUpickle, librariesAkkaHttp)
  settings (librariesClientServed: _*)
  settings (
    mainClass in Compile := Some("shapes.Server"),
    resources in Compile ++=
      ((crossTarget in Compile in shapesActorObserveJS).value ** "*.js").get))

lazy val shapesActorObserveJS = (project in file("actor.observer") / "js"
  settings (sharedDirectories, commonDirectoriesScala, commonDirectoriesScalaJS)
  settings (librariesUpickle, librariesAkkaHttp, librariesAkkaJs, librariesDom)
  settings (
    mainClass in Compile := Some("shapes.Client"),
    scalaJSUseMainModuleInitializer in Compile := true)
  enablePlugins ScalaJSPlugin)


lazy val shapesActorReact = (project in file("actor.reactive") / ".all"
  settings (run in Compile :=
    ((run in Compile in shapesActorReactJVM) dependsOn
     (fullOptJS in Compile in shapesActorReactJS)).evaluated)
  aggregate (shapesActorReactJVM, shapesActorReactJS))

lazy val shapesActorReactJVM = (project in file("actor.reactive") / "jvm"
  settings (sharedDirectories, commonDirectoriesScala)
  settings (librariesUpickle, librariesAkkaHttp, librariesRescala)
  settings (librariesClientServed: _*)
  settings (
    mainClass in Compile := Some("shapes.Server"),
    resources in Compile ++=
      ((crossTarget in Compile in shapesActorReactJS).value ** "*.js").get))

lazy val shapesActorReactJS = (project in file("actor.reactive") / "js"
  settings (sharedDirectories, commonDirectoriesScala, commonDirectoriesScalaJS)
  settings (librariesUpickle, librariesAkkaHttp, librariesAkkaJs, librariesRescala, librariesDom)
  settings (
    mainClass in Compile := Some("shapes.Client"),
    scalaJSUseMainModuleInitializer in Compile := true)
  enablePlugins ScalaJSPlugin)


lazy val shapesMultiObserve = (project in file("multitier.observer") / ".all"
  settings (run in Compile :=
    ((run in Compile in shapesMultiObserveJVM) dependsOn
     (fullOptJS in Compile in shapesMultiObserveJS)).evaluated)
  aggregate (shapesMultiObserveJVM, shapesMultiObserveJS))

lazy val shapesMultiObserveJVM = (project in file("multitier.observer") / ".jvm"
  settings (commonDirectoriesScala, commonDirectoriesScalaJS)
  settings (settingsMultitier: _*)
  settings (librariesClientServed: _*)
  settings (
    mainClass in Compile := Some("shapes.Server"),
    resources in Compile ++=
      ((crossTarget in Compile in shapesMultiObserveJS).value ** "*.js").get))

lazy val shapesMultiObserveJS = (project in file("multitier.observer") / ".js"
  settings (commonDirectoriesScala, commonDirectoriesScalaJS)
  settings (settingsMultitier: _*)
  settings (
    mainClass in Compile := Some("shapes.Client"),
    scalaJSUseMainModuleInitializer in Compile := true)
  enablePlugins ScalaJSPlugin)


lazy val shapesMultiReact = (project in file("multitier.reactive") / ".all"
  settings (run in Compile :=
    ((run in Compile in shapesMultiReactJVM) dependsOn
     (fullOptJS in Compile in shapesMultiReactJS)).evaluated)
  aggregate (shapesMultiReactJVM, shapesMultiReactJS))

lazy val shapesMultiReactJVM = (project in file("multitier.reactive") / ".jvm"
  settings (commonDirectoriesScala, commonDirectoriesScalaJS)
  settings (settingsMultitier: _*)
  settings (librariesClientServed: _*)
  settings (
    mainClass in Compile := Some("shapes.Server"),
    resources in Compile ++=
      ((crossTarget in Compile in shapesMultiReactJS).value ** "*.js").get))


lazy val shapesMultiReactJS = (project in file("multitier.reactive") / ".js"
  settings (commonDirectoriesScala, commonDirectoriesScalaJS)
  settings (settingsMultitier: _*)
  settings (
    mainClass in Compile := Some("shapes.Client"),
    scalaJSUseMainModuleInitializer in Compile := true)
  enablePlugins ScalaJSPlugin)
