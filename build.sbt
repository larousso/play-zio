name := """play-zio-sample"""
organization := "fr.adelegue"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.1"

libraryDependencies ++= Seq(
  "org.iq80.leveldb"       % "leveldb"             % "0.12",
  "org.typelevel"          %% "cats-effect"        % "2.1.3",
  "dev.zio"                %% "zio"                % "1.0.0-RC21",
  "dev.zio"                %% "zio-interop-cats"   % "2.1.3.0-RC16",
  "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test
)

addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full)

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "fr.adelegue.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "fr.adelegue.binders._"
