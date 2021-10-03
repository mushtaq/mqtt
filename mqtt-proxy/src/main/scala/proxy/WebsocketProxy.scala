package proxy

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, WebSocketRequest}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler
import akka.stream.alpakka.mqtt.streaming.MqttCodec.{MqttByteIterator, MqttConnAck}
import akka.stream.alpakka.mqtt.streaming._
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString

import scala.concurrent.duration.DurationInt

object WebsocketProxy {
  def main(args: Array[String]): Unit = {
    implicit val actorSystem: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "proxy-actor-system")
    import actorSystem.executionContext

    val maxPacketSize = 4096

    val flow = Flow[Message].flatMapPrefix(1) { frames =>
      val messageF = frames.head.asBinaryMessage.asScala.toStrict(100.millis)
      val flowF    = messageF.map { msg =>
        msg.data.iterator.decodeControlPacket(maxPacketSize) match {
          case Right(x: Connect) if x.clientId != "subscriber-2" =>
            val webSocketRequest = WebSocketRequest("ws://127.0.0.1:9001", subprotocol = Some("mqtt"))
            val outgoingFlow     = Http().webSocketClientFlow(webSocketRequest)
            Flow[Message].prepend(Source.single(msg)).via(outgoingFlow)
          case x                                                 =>
            val connAck    = ConnAck(ConnAckFlags.None, ConnAckReturnCode.ConnectionRefusedNotAuthorized)
            val byteString = connAck.encode(ByteString.newBuilder).result()
            Flow.fromSinkAndSourceCoupled(Sink.cancelled, Source.single(BinaryMessage.Strict(byteString)))
        }
      }
      Flow.futureFlow(flowF)
    }

    val myExceptionHandler = ExceptionHandler { case ex =>
      ex.printStackTrace()
      complete(HttpResponse(InternalServerError, entity = "error"))
    }

    val route = handleExceptions(myExceptionHandler) {
      get {
        pathSingleSlash {
          handleWebSocketMessagesForProtocol(flow, "mqtt")
        }
      }
    }

    Http().newServerAt("127.0.0.1", 8001).bind((route)).onComplete(println)

    println(s"Server now online. Please navigate to http://127.0.0.1:8001/")
  }

}
