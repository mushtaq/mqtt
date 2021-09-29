package sub

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.stream.OverflowStrategy
import akka.stream.alpakka.mqtt.streaming._
import akka.stream.alpakka.mqtt.streaming.scaladsl.{ActorMqttClientSession, Mqtt}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, SourceQueueWithComplete, Tcp}
import akka.util.ByteString

import scala.concurrent.Future

object Subscriber {

  def subscription(subscriberId: Int): Unit = {
    implicit val actorSystem: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, s"sub-$subscriberId-actor-system")
    import actorSystem.executionContext

    val settings = MqttSessionSettings()
    val session  = ActorMqttClientSession(settings)

    val connection: Flow[ByteString, ByteString, Future[Tcp.OutgoingConnection]] =
      Tcp()(actorSystem.toClassic).outgoingConnection("localhost", 1773)

    val mqttFlow: Flow[Command[Nothing], Either[MqttCodec.DecodeError, Event[Nothing]], NotUsed] =
      Mqtt
        .clientSessionFlow(session, ByteString(s"sub-$subscriberId"))
        .join(connection)

    val sink = Sink
      .foreach[Any] {
        case Right(Event(x: Publish, _)) => println((x, x.payload.utf8String))
        case x                           => println(x)
      }
      .mapMaterializedValue(_.onComplete(println))

    val commands: SourceQueueWithComplete[Command[Nothing]] =
      Source
        .queue(10, OverflowStrategy.fail)
        .via(mqttFlow)
        .toMat(sink)(Keep.left)
        .run()

    val clientId = s"subscriber-$subscriberId"
    val topic    = "topic-4"

    commands.offer(Command(Connect(clientId, ConnectFlags.CleanSession, "streamsheets", "H0hLZ1HiCZ")))
    commands.offer(Command(Subscribe(topic)))

    commands.watchCompletion().onComplete(println)
  }
}
