import sbt._

object Dependencies {
  val AkkaVersion = "2.6.14"

  val scalatest                            = "org.scalatest"      %% "scalatest"                          % "3.2.8"
  val `akka-stream-alpakka-mqtt-streaming` = "com.lightbend.akka" %% "akka-stream-alpakka-mqtt-streaming" % "3.0.3"
  val `akka-stream`                        = "com.typesafe.akka"  %% "akka-stream"                        % AkkaVersion
  val `akka-actor-typed`                   = "com.typesafe.akka"  %% "akka-actor-typed"                   % AkkaVersion
}
