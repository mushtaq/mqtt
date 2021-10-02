package stream

import akka.NotUsed
import akka.stream.scaladsl.{Keep, RunnableGraph, Sink, Source}

object StreamExtensions {
  def sinkToSource[M]: RunnableGraph[(Sink[M, NotUsed], Source[M, NotUsed])] =
    Source
      .asSubscriber[M]
      .toMat(Sink.asPublisher[M](fanout = false))(Keep.both)
      .mapMaterializedValue { case (sub, pub) ⇒
        (Sink.fromSubscriber(sub), Source.fromPublisher(pub))
      }
}
