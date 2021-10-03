import Dependencies._

ThisBuild / scalaVersion     := "2.13.6"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"

lazy val `mqtt-root` = project
  .in(file("."))
  .aggregate(`mqtt-pub`, `mqtt-sub`, `mqtt-proxy`, `akka-utils`)

lazy val `mqtt-pub`  = project.dependsOn(`akka-utils`)

lazy val `mqtt-sub` = project.dependsOn(`akka-utils`)

lazy val `mqtt-proxy` = project.dependsOn(`akka-utils`)

lazy val `mqtt-server` = project.dependsOn(`akka-utils`)

lazy val `akka-utils` = project
  .settings(
    libraryDependencies ++= List(
      `akka-stream-alpakka-mqtt-streaming`,
      `akka-http`,
      scalatest % Test
    )
  )
