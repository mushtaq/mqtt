package akka.stream.alpakka.mqtt.streaming

import akka.stream.alpakka.mqtt.streaming.impl.MqttFrameStage

object AlpakkaUtil {
  def mqttFraming(maxPacketSize: Int) = new MqttFrameStage(maxPacketSize)
}
