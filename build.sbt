name := """play-zio-sample"""
organization := "fr.adelegue"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.1"

libraryDependencies ++= Seq(
  "org.iq80.leveldb"         % "leveldb"                              % "0.12",
  "dev.zio"                  %% "zio"                                 % "1.0.0-RC18-2",
  "dev.zio"                  %% "zio-interop-cats"                    % "2.0.0.0-RC12",
  "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test
)

// Adds additional packages into Twirl
//TwirlKeys.templateImports += "fr.adelegue.controllers._"

// Adds additional packages into conf/routes
// play.sbt.routes.RoutesKeys.routesImport += "fr.adelegue.binders._"
