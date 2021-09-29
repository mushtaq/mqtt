package proxy

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.stream.alpakka.mqtt.streaming.MqttCodec.MqttByteIterator
import akka.stream.alpakka.mqtt.streaming.{AlpakkaUtil, Connect}
import akka.stream.scaladsl.{Flow, Sink, Source, Tcp}
import akka.util.ByteString

object ProxyMain {
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
            case Right(x: Connect) if x.clientId != "subscriber-2" => Flow[ByteString].prepend(Source(frames))
            case x                                                 => Flow.fromSinkAndSourceCoupled(Sink.ignore, Source.empty)
          }
        }
        validConnectionFlow.join(outgoingFlow).run()
      }
      .onComplete(println)
  }
}
