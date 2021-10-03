package sub

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{BinaryMessage, WebSocketRequest}
import akka.stream.alpakka.mqtt.streaming.scaladsl.{ActorMqttClientSession, Mqtt}
import akka.stream.alpakka.mqtt.streaming._
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString

object WebsocketSubscriber {

  def subscription(subscriberId: Int): Unit = {
    implicit val actorSystem: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, s"sub-$subscriberId-actor-system")
    import actorSystem.executionContext

    val settings = MqttSessionSettings()
    val session  = ActorMqttClientSession(settings)

    val webSocketFlow =
      Http()
        .webSocketClientFlow(WebSocketRequest("ws://127.0.0.1:8001", subprotocol = Some("mqtt")))
        .mapMaterializedValue(_.onComplete(println))

    val connection = Flow[ByteString]
      .map(x => BinaryMessage.Strict(x))
      .via(webSocketFlow)
      .flatMapConcat(_.asBinaryMessage.getStreamedData)

    val mqttFlow: Flow[Command[Nothing], Either[MqttCodec.DecodeError, Event[Nothing]], NotUsed] =
      Mqtt
        .clientSessionFlow(session, ByteString(s"sub-$subscriberId"))
        .join(connection)

    val clientId = s"subscriber-$subscriberId"
    val topic    = "topic-4"

    Source
      .queue(10)
      .via(mqttFlow)
      .mapMaterializedValue { q =>
        q.offer(Command(Connect(clientId, ConnectFlags.CleanSession, "streamsheets", "H0hLZ1HiCZ")))
        q.offer(Command(Subscribe(topic)))
      }
      .runForeach {
        case Right(Event(x: Publish, _)) => println((x, x.payload.utf8String))
        case x                           => println(x)
      }
      .onComplete(println)
  }
}
