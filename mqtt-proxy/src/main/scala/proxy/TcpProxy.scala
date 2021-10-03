package proxy

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.stream.alpakka.mqtt.streaming.MqttCodec.{MqttByteIterator, MqttConnAck}
import akka.stream.alpakka.mqtt.streaming._
import akka.stream.scaladsl.{Flow, Sink, Source, Tcp}
import akka.util.ByteString

object TcpProxy {
  def main(args: Array[String]): Unit = {
    implicit val actorSystem: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "proxy-actor-system")
    import actorSystem.executionContext

    val maxPacketSize = 4096
    val outgoingFlow  = Tcp()(actorSystem.toClassic).outgoingConnection("localhost", 1883)

    Tcp()(actorSystem.toClassic)
      .bind("0.0.0.0", 1773)
      .runForeach { incomingConnection =>
        val framedBytes         = incomingConnection.flow.via(AlpakkaUtil.mqttFraming(maxPacketSize))
        val validConnectionFlow = framedBytes.flatMapPrefix(1) { frames =>
          frames.head.iterator.decodeControlPacket(maxPacketSize) match {
            case Right(x: Connect) if x.clientId != "subscriber-2" =>
              Flow[ByteString].prepend(Source(frames)).via(outgoingFlow)
            case x                                                 =>
              val connAck    = ConnAck(ConnAckFlags.None, ConnAckReturnCode.ConnectionRefusedNotAuthorized)
              val byteString = connAck.encode(ByteString.newBuilder).result()
              Flow.fromSinkAndSourceCoupled(Sink.cancelled, Source.single(byteString))
          }
        }
        validConnectionFlow.join(Flow[ByteString]).run()
      }
      .onComplete(println)
  }
}
