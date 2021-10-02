package stream

import akka.NotUsed
import akka.stream.scaladsl.{Sink, Source}

object StreamExtensions {
  def sinkToSource[M]: Source[M, Sink[M, NotUsed]] = Source.asSubscriber[M].mapMaterializedValue(Sink.fromSubscriber)
}
