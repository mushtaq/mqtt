package pub

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.stream.alpakka.mqtt.streaming._
import akka.stream.alpakka.mqtt.streaming.scaladsl.{ActorMqttClientSession, Mqtt}
import akka.stream.scaladsl.{Flow, Source, Tcp}
import akka.util.ByteString

import scala.concurrent.duration.DurationInt

object PublisherMain {
  def main(args: Array[String]): Unit = {
    implicit val actorSystem: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "pub-actor-system")
    import actorSystem.executionContext

    val settings = MqttSessionSettings()
    val session  = ActorMqttClientSession(settings)

    val connection = Tcp()(actorSystem.toClassic).outgoingConnection("localhost", 1883)

    val mqttFlow: Flow[Command[Nothing], Either[MqttCodec.DecodeError, Event[Nothing]], NotUsed] =
      Mqtt
        .clientSessionFlow(session, ByteString("pub-11"))
        .join(connection)

    val clientId = "publisher-11"
    val topic    = "topic-4"

    Source
      .queue(10)
      .via(mqttFlow)
      .mapMaterializedValue { q =>
        q.offer(Command(Connect(clientId, ConnectFlags.CleanSession, "streamsheets", "H0hLZ1HiCZ")))
        Source
          .fromIterator(() => Iterator.from(1))
          .throttle(1, 1.second)
          .runForeach { x =>
            session ! Command(
              Publish(ControlPacketFlags.None, topic, ByteString(s"ohi-1-$x"))
            )
          }
      }
      .runForeach {
        case Right(Event(x: Publish, _)) => println((x, x.payload.utf8String))
        case x                           => println(x)
      }
      .onComplete(println)
  }
}
