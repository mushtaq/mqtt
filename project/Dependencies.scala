import sbt._

object Dependencies {
  val scalatest                                = "org.scalatest"      %% "scalatest"                          % "3.2.8"
  val `akka-stream-alpakka-mqtt-streaming`     = "com.lightbend.akka" %% "akka-stream-alpakka-mqtt-streaming" % "3.0.3"
  val `akka-stream-alpakka-unix-domain-socket` =
    "com.lightbend.akka" %% "akka-stream-alpakka-unix-domain-socket" % "3.0.3"
  val `akka-stream`      = "com.typesafe.akka" %% "akka-stream"      % "2.6.16"
  val `akka-actor-typed` = "com.typesafe.akka" %% "akka-actor-typed" % "2.6.16"
  val `akka-http`        = "com.typesafe.akka" %% "akka-http"        % "10.2.6"
}
