organization in ThisBuild := "de.tuda.stg"

version in ThisBuild := "0.0.0"

scalaVersion in ThisBuild := "2.11.8"

scalacOptions in ThisBuild ++= Seq("-feature", "-deprecation", "-unchecked")


val repoRescala =
  resolvers += Resolver.bintrayRepo("rmgk", "maven")

val librariesRescala = libraryDependencies +=
  "de.tuda.stg" %%% "rescala" % "0.19.0"

val librariesUpickle = libraryDependencies +=
  "com.lihaoyi" %%% "upickle" % "0.4.4"

val librariesAkkaHttp = libraryDependencies +=
  "com.typesafe.akka" %% "akka-http" % "10.0.5"

val librariesAkkaJs = libraryDependencies +=
  "org.akka-js" %%% "akkajsactor" % "1.2.5.2"

val librariesDom = libraryDependencies +=
  "org.scala-js" %%% "scalajs-dom" % "0.9.1"

val librariesMultitier = libraryDependencies ++= Seq(
  "de.tuda.stg" %%% "retier-core" % "0+",
  "de.tuda.stg" %%% "retier-architectures-basic" % "0+",
  "de.tuda.stg" %%% "retier-serializable-upickle" % "0+",
  "de.tuda.stg" %%% "retier-network-ws-akka" % "0+",
  "de.tuda.stg" %%% "retier-network-webrtc" % "0+",
  "de.tuda.stg" %%% "retier-transmitter-basic" % "0+",
  "de.tuda.stg" %%% "retier-transmitter-rescala" % "0+")

val librariesClientServed = Seq(
  dependencyOverrides += "org.webjars.bower" % "jquery" % "1.12.0",
  libraryDependencies += "org.webjars.bower" % "bootstrap" % "3.3.6",
  libraryDependencies += "org.webjars.bower" % "webrtc-adapter" % "0.2.5")

val macroparadise = addCompilerPlugin(
  "org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)


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
  sharedMultitierDirectories ++ Seq(macroparadise, librariesMultitier)


lazy val chat = (project in file(".")
  aggregate (chatTraditional, chatScalajsObserve, chatMultiReact, chatMultiObserve))


lazy val chatTraditional = (project in file("traditional")
  settings (commonDirectoriesScala, commonDirectoriesJS)
  settings (librariesAkkaHttp, librariesUpickle)
  settings (librariesClientServed: _*))


lazy val chatScalajsObserve = (project in file("scalajs.observer") / ".all"
  settings (run in Compile :=
    ((run in Compile in chatScalajsObserveJVM) dependsOn
     (fullOptJS in Compile in chatScalajsObserveJS)).evaluated)
  aggregate (chatScalajsObserveJVM, chatScalajsObserveJS))

lazy val chatScalajsObserveJVM = (project in file("scalajs.observer") / "jvm"
  settings (sharedDirectories, commonDirectoriesScala)
  settings (librariesUpickle, librariesAkkaHttp)
  settings (librariesClientServed: _*)
  settings (resources in Compile ++=
    ((crossTarget in Compile in chatScalajsObserveJS).value ** "*.js").get))

lazy val chatScalajsObserveJS = (project in file("scalajs.observer") / "js"
  settings (sharedDirectories, commonDirectoriesScala, commonDirectoriesScalaJS)
  settings (librariesUpickle, librariesAkkaHttp, librariesDom)
  settings (scalaJSUseMainModuleInitializer in Compile := true)
  enablePlugins ScalaJSPlugin)


lazy val chatScalajsReact = (project in file("scalajs.reactive") / ".all"
  settings (run in Compile :=
    ((run in Compile in chatScalajsReactJVM) dependsOn
     (fullOptJS in Compile in chatScalajsReactJS)).evaluated)
  aggregate (chatScalajsReactJVM, chatScalajsReactJS))

lazy val chatScalajsReactJVM = (project in file("scalajs.reactive") / "jvm"
  settings (sharedDirectories, commonDirectoriesScala)
  settings (librariesUpickle, librariesAkkaHttp, repoRescala, librariesRescala)
  settings (librariesClientServed: _*)
  settings (resources in Compile ++=
    ((crossTarget in Compile in chatScalajsReactJS).value ** "*.js").get))

lazy val chatScalajsReactJS = (project in file("scalajs.reactive") / "js"
  settings (sharedDirectories, commonDirectoriesScala, commonDirectoriesScalaJS)
  settings (librariesUpickle, librariesAkkaHttp, repoRescala, librariesRescala, librariesDom)
  settings (scalaJSUseMainModuleInitializer in Compile := true)
  enablePlugins ScalaJSPlugin)


lazy val chatActorObserve = (project in file("actor.observer") / ".all"
  settings (run in Compile :=
    ((run in Compile in chatActorObserveJVM) dependsOn
     (fullOptJS in Compile in chatActorObserveJS)).evaluated)
  aggregate (chatActorObserveJVM, chatActorObserveJS))

lazy val chatActorObserveJVM = (project in file("actor.observer") / "jvm"
  settings (sharedDirectories, commonDirectoriesScala)
  settings (librariesUpickle, librariesAkkaHttp)
  settings (librariesClientServed: _*)
  settings (resources in Compile ++=
    ((crossTarget in Compile in chatActorObserveJS).value ** "*.js").get))

lazy val chatActorObserveJS = (project in file("actor.observer") / "js"
  settings (sharedDirectories, commonDirectoriesScala, commonDirectoriesScalaJS)
  settings (librariesUpickle, librariesAkkaHttp, librariesAkkaJs, librariesDom)
  settings (scalaJSUseMainModuleInitializer in Compile := true)
  enablePlugins ScalaJSPlugin)


lazy val chatActorReact = (project in file("actor.reactive") / ".all"
  settings (run in Compile :=
    ((run in Compile in chatActorReactJVM) dependsOn
     (fullOptJS in Compile in chatActorReactJS)).evaluated)
  aggregate (chatActorReactJVM, chatActorReactJS))

lazy val chatActorReactJVM = (project in file("actor.reactive") / "jvm"
  settings (sharedDirectories, commonDirectoriesScala)
  settings (librariesUpickle, librariesAkkaHttp, repoRescala, librariesRescala)
  settings (librariesClientServed: _*)
  settings (resources in Compile ++=
    ((crossTarget in Compile in chatActorReactJS).value ** "*.js").get))

lazy val chatActorReactJS = (project in file("actor.reactive") / "js"
  settings (sharedDirectories, commonDirectoriesScala, commonDirectoriesScalaJS)
  settings (librariesUpickle, librariesAkkaHttp, librariesAkkaJs, repoRescala, librariesRescala, librariesDom)
  settings (scalaJSUseMainModuleInitializer in Compile := true)
  enablePlugins ScalaJSPlugin)


lazy val chatMultiObserve = (project in file("multitier.observer") / ".all"
  settings (run in Compile :=
    ((run in Compile in chatMultiObserveJVM) dependsOn
     (fullOptJS in Compile in chatMultiObserveJS)).evaluated)
  aggregate (chatMultiObserveJVM, chatMultiObserveJS))

lazy val chatMultiObserveJVM = (project in file("multitier.observer") / ".jvm"
  settings (commonDirectoriesScala, commonDirectoriesScalaJS)
  settings (settingsMultitier: _*)
  settings (librariesClientServed: _*)
  settings (resources in Compile ++=
    ((crossTarget in Compile in chatMultiObserveJS).value ** "*.js").get))

lazy val chatMultiObserveJS = (project in file("multitier.observer") / ".js"
  settings (commonDirectoriesScala, commonDirectoriesScalaJS)
  settings (settingsMultitier: _*)
  settings (scalaJSUseMainModuleInitializer in Compile := true)
  enablePlugins ScalaJSPlugin)


lazy val chatMultiReact = (project in file("multitier.reactive") / ".all"
  settings (run in Compile :=
    ((run in Compile in chatMultiReactJVM) dependsOn
     (fullOptJS in Compile in chatMultiReactJS)).evaluated)
  aggregate (chatMultiReactJVM, chatMultiReactJS))

lazy val chatMultiReactJVM = (project in file("multitier.reactive") / ".jvm"
  settings (commonDirectoriesScala, commonDirectoriesScalaJS)
  settings (settingsMultitier: _*)
  settings (librariesClientServed: _*)
  settings (resources in Compile ++=
    ((crossTarget in Compile in chatMultiReactJS).value ** "*.js").get))

lazy val chatMultiReactJS = (project in file("multitier.reactive") / ".js"
  settings (commonDirectoriesScala, commonDirectoriesScalaJS)
  settings (settingsMultitier: _*)
  settings (scalaJSUseMainModuleInitializer in Compile := true)
  enablePlugins ScalaJSPlugin)
