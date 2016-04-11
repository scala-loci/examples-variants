organization in ThisBuild := "de.tuda.stg"

version in ThisBuild := "0.0.0"

scalaVersion in ThisBuild := "2.11.7"

scalacOptions in ThisBuild ++= Seq("-feature", "-deprecation", "-unchecked")


val librariesUpickle = libraryDependencies +=
  "com.lihaoyi" %%% "upickle" % "0.3.6"

val librariesAkkaHttp = libraryDependencies +=
  "com.typesafe.akka" %% "akka-http-experimental" % "2.0.2"

val librariesMultitier = libraryDependencies ++= Seq(
  "de.tuda.stg" %%% "retier-core" % "0+",
  "de.tuda.stg" %%% "retier-architectures-basic" % "0+",
  "de.tuda.stg" %%% "retier-serializable-upickle" % "0+",
  "de.tuda.stg" %%% "retier-network-ws-akka" % "0+",
  "de.tuda.stg" %%% "retier-transmitter-basic" % "0+",
  "de.tuda.stg" %%% "retier-transmitter-rescala" % "0+",
  "org.scala-js" %%%! "scalajs-dom" % "0.9.0")

val librariesClientServed = Seq(
  dependencyOverrides += "org.webjars.bower" % "jquery" % "1.12.0",
  dependencyOverrides += "org.webjars.bower" % "bootstrap" % "3.3.6",
  libraryDependencies += "org.webjars.bower" % "mjolnic-bootstrap-colorpicker" % "2.3.0",
  libraryDependencies += "org.webjars.bower" % "fabric" % "1.5.0")

val macroparadise = addCompilerPlugin(
  "org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)


val sharedDirectories = Seq(
  unmanagedSourceDirectories in Compile +=
    baseDirectory.value.getParentFile / "src" / "main" / "scala",
  unmanagedResourceDirectories in Compile +=
    baseDirectory.value.getParentFile / "src" / "main" / "resources",
  unmanagedSourceDirectories in Test +=
    baseDirectory.value.getParentFile / "src" / "test" / "scala",
  unmanagedResourceDirectories in Test +=
    baseDirectory.value.getParentFile / "src" / "test" / "resources")

val settingsMultitier =
  sharedDirectories ++ Seq(macroparadise, librariesMultitier)


lazy val shapes = (project in file(".")
  aggregate (shapesTraditional, shapesMultiReact, shapesMultiObserve))


lazy val shapesTraditional = (project in file("traditional")
  settings (librariesAkkaHttp, librariesUpickle)
  settings (librariesClientServed: _*))


lazy val shapesMultiObserve = (project in file("multitier.observer") / ".all"
  settings (run in Compile <<=
    (run in Compile in shapesMultiObserveJVM) dependsOn
    (fastOptJS in Compile in shapesMultiObserveJS))
  aggregate (shapesMultiObserveJVM, shapesMultiObserveJS))

lazy val shapesMultiObserveJVM = (project in file("multitier.observer") / ".jvm"
  settings (settingsMultitier: _*)
  settings (librariesClientServed: _*)
  settings (resources in Compile ++=
    ((crossTarget in Compile in shapesMultiObserveJS).value ** "*.js").get))

lazy val shapesMultiObserveJS = (project in file("multitier.observer") / ".js"
  settings (settingsMultitier: _*)
  settings (persistLauncher in Compile := true)
  enablePlugins ScalaJSPlugin)


lazy val shapesMultiReact = (project in file("multitier.reactive") / ".all"
  settings (run in Compile <<=
    (run in Compile in shapesMultiReactJVM) dependsOn
    (fastOptJS in Compile in shapesMultiReactJS))
  aggregate (shapesMultiReactJVM, shapesMultiReactJS))

lazy val shapesMultiReactJVM = (project in file("multitier.reactive") / ".jvm"
  settings (settingsMultitier: _*)
  settings (librariesClientServed: _*)
  settings (resources in Compile ++=
    ((crossTarget in Compile in shapesMultiReactJS).value ** "*.js").get))


lazy val shapesMultiReactJS = (project in file("multitier.reactive") / ".js"
  settings (settingsMultitier: _*)
  settings (persistLauncher in Compile := true)
  enablePlugins ScalaJSPlugin)
