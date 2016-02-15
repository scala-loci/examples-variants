organization in ThisBuild := "de.tuda.stg"

version in ThisBuild := "0.0.0"

scalaVersion in ThisBuild := "2.11.7"

scalacOptions in ThisBuild ++= Seq("-feature", "-deprecation", "-unchecked")


val libraries = libraryDependencies ++= Seq(
  "de.tuda.stg" %%% "retier-core" % "0+",
  "de.tuda.stg" %%% "retier-architectures-basic" % "0+",
  "de.tuda.stg" %%% "retier-serializable-upickle" % "0+",
  "de.tuda.stg" %%% "retier-network-ws-akka" % "0+",
  "de.tuda.stg" %%% "retier-transmitter-rescala" % "0+",
  "org.scala-js" %%%! "scalajs-dom" % "0.8.2")

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

val sharedSettings =
  sharedDirectories ++ Seq(macroparadise, libraries)


lazy val shapes = (project in file(".")
  settings (baseDirectory := file(".all"))
  aggregate (shapesJVM, shapesJS))

lazy val shapesJVM = (project in file(".jvm")
  settings (sharedSettings: _*)
  settings (librariesClientServed: _*)
  settings (resources in Compile ++= ((crossTarget in Compile in shapesJS).value ** "*.js").get))

lazy val shapesJS = (project in file(".js")
  settings (sharedSettings: _*)
  settings (persistLauncher in Compile := true)
  enablePlugins ScalaJSPlugin)


run in Compile <<=
  (run in Compile in shapesJVM) dependsOn (fastOptJS in Compile in shapesJS)
