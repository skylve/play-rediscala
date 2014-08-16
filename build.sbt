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

// Coverage settings
instrumentSettings

CoverallsPlugin.coverallsSettings

// Publish settings
publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

licenses := Seq("Apache 2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))

homepage := Some(url("https://github.com/skylve/play-rediscala"))

pomExtra :=
  <scm>
    <url>git@github.com:skylve/play-rediscala.git</url>
    <connection>scm:git@github.com:skylve/play-rediscala.git</connection>
  </scm>
  <developers>
    <developer>
      <id>skylve</id>
      <name>Remy Sornette</name>
      <url>https://github.com/skylve</url>
    </developer>
  </developers>
