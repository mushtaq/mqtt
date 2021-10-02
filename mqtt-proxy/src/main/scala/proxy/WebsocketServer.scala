package proxy

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.model.ws.{BinaryMessage, Message}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler
import akka.stream.alpakka.mqtt.streaming._
import akka.stream.alpakka.mqtt.streaming.scaladsl.{ActorMqttServerSession, Mqtt}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import akka.{Done, NotUsed}

import scala.concurrent.Promise
import scala.util.{Failure, Success}

object WebsocketServer {

  def main(args: Array[String]): Unit = {
    implicit val actorSystem: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "proxy-actor-system")
    import actorSystem.executionContext

    val settings = MqttSessionSettings()
    val session  = ActorMqttServerSession(settings)

    val route =
      extractClientIP { remoteAddress =>
        val address = remoteAddress.toOption.getOrElse(throw new RuntimeException).getAddress

        val (incomingSink, incomingSource) =
          Source.asSubscriber[Message].mapMaterializedValue(Sink.fromSubscriber).preMaterialize()

        val (outgoingSink, outgoingSource) =
          Source.asSubscriber[ByteString].mapMaterializedValue(Sink.fromSubscriber).preMaterialize()

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
              println("********************")
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
            handleWebSocketMessages(
              Flow.fromSinkAndSourceCoupled(
                incomingSink,
                outgoingSource.map(BinaryMessage.Strict)
              )
            )
          }
        }
      }

    val myExceptionHandler = ExceptionHandler { case ex =>
      ex.printStackTrace()
      complete(HttpResponse(InternalServerError, entity = "error"))
    }

    Http().newServerAt("127.0.0.1", 8001).bind(handleExceptions(myExceptionHandler)(route)).onComplete(println)

    println(s"Server now online. Please navigate to http://127.0.0.1:8001/")
  }
}
