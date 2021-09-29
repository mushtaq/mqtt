import Dependencies._

ThisBuild / scalaVersion     := "2.13.6"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"

lazy val `mqtt-root`  = project
  .in(file("."))
  .aggregate(`mqtt-pub`, `mqtt-sub`, `mqtt-proxy`)

lazy val `mqtt-pub`   = project
  .settings(
    libraryDependencies ++= List(
      `akka-stream-alpakka-mqtt-streaming`,
      scalatest % Test
    )
  )

lazy val `mqtt-sub`   = project
  .settings(
    libraryDependencies ++= List(
      `akka-stream-alpakka-mqtt-streaming`,
      scalatest % Test
    )
  )

lazy val `mqtt-proxy` = project
  .settings(
    libraryDependencies ++= List(
      `akka-stream-alpakka-mqtt-streaming`,
      scalatest % Test
    )
  )
