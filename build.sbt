name := "play-rediscala"

organization := "net.skylve"

version := "0.1"

scalaVersion := "2.11.2"

crossScalaVersions := Seq("2.11.2", "2.10.3")

javacOptions ++= Seq("-source", "1.6", "-target", "1.6")

scalacOptions ++= Seq("-unchecked", "-deprecation")

resolvers ++= Seq(
  "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
  "Rediscala repository" at "http://dl.bintray.com/etaty/maven"
)

libraryDependencies ++= Seq(
  "com.typesafe.play"    %% "play"            % "2.3.0"     % "provided",
  "com.typesafe.akka"    %% "akka-actor"      % "2.3.4",
  "com.etaty.rediscala"  %% "rediscala"       % "1.3.1",
  "org.scalatest"        %  "scalatest_2.11"  % "2.2.1"     % "test",
  "org.scalatestplus"    %% "play"            % "1.1.0"     % "test"
)

instrumentSettings

CoverallsPlugin.coverallsSettings