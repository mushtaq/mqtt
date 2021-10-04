package proxy

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{BinaryMessage, Message}
import akka.http.scaladsl.server.Directives._
import akka.stream.alpakka.mqtt.streaming._
import akka.stream.alpakka.mqtt.streaming.scaladsl.{ActorMqttServerSession, Mqtt}
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}
import stream.StreamExtensions

import scala.concurrent.Promise
import scala.util.{Failure, Success}

object WebsocketServer {

  def main(args: Array[String]): Unit = {
    implicit val actorSystem: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "server-actor-system")
    import actorSystem.executionContext

    val settings = MqttSessionSettings()
    val session  = ActorMqttServerSession(settings)

    val route =
      extractClientIP { remoteAddress =>
        val address = remoteAddress.toOption.getOrElse(throw new RuntimeException).getAddress

        val (incomingSink, incomingSource) = StreamExtensions.sinkToSource[Message].preMaterialize()
        val (outgoingSink, outgoingSource) = StreamExtensions.sinkToSource[ByteString].preMaterialize()

        val mqttFlow: Flow[Command[Nothing], Either[MqttCodec.DecodeError, Event[Nothing]], NotUsed] =
          Mqtt
            .serverSessionFlow(session, ByteString(address))
            .join(
              Flow.fromSinkAndSourceCoupled(
                outgoingSink,
                incomingSource.flatMapConcat(_.asBinaryMessage.getStreamedData)
              )
            )

        val (queue, source) = Source.queue[Command[Nothing]](64).via(mqttFlow).preMaterialize()

        val subscribed = Promise[Done]

        source
          .runForeach {
            case Right(Event(_: Connect, _))    =>
              queue.offer(Command(ConnAck(ConnAckFlags.None, ConnAckReturnCode.ConnectionAccepted)))
            case Right(Event(cp: Subscribe, _)) =>
              queue.offer(Command(SubAck(cp.packetId, cp.topicFilters.map(_._2)), Some(subscribed), None))
            case Right(Event(publish @ Publish(flags, _, Some(packetId), _), _))
                if flags.contains(ControlPacketFlags.RETAIN) =>
              queue.offer(Command(PubAck(packetId)))
              subscribed.future.foreach(_ => session ! Command(publish))
            case _                              => // Ignore everything else
          }
          .onComplete {
            case Failure(exception) => exception.printStackTrace()
            case Success(value)     => println(value)
          }

        get {
          pathSingleSlash {
            handleWebSocketMessagesForProtocol(
              Flow.fromSinkAndSourceCoupled(
                incomingSink,
                outgoingSource.map(BinaryMessage.Strict)
              ),
              "mqtt"
            )
          }
        }
      }

    Http()
      .newServerAt("127.0.0.1", 8001)
      .bind(route)
      .onComplete(println)
  }
}
