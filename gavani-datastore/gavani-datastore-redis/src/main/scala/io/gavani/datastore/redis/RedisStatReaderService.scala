package io.gavani.datastore.redis

import com.twitter.finagle.redis
import com.twitter.finagle.redis.util.{CBToString, StringToChannelBuffer}
import com.twitter.io.Charsets
import io.gavani.thriftscala.StatReader
import io.gavani.thriftscala.StatReadRequest
import io.gavani.thriftscala.StatReadResponse
import com.twitter.util._

class RedisStatReaderService(
    val redisClient: redis.Client,
    val fStoreKey: (String, String, String, Int) => String
) extends StatReader[Future] {
  private[this] def readOneStat(
      statNamespace: String,
      statSource: String,
      statName: String,
      timestamp: Int
  ): Future[Double] = {
    val key = fStoreKey(statNamespace, statSource, statName, timestamp)
    redisClient.get(StringToChannelBuffer.apply(key, Charsets.Utf8)).map { cbOption =>
      if(cbOption.isEmpty) {
        throw new NoSuchElementException(key)
      } else {
        java.lang.Double.valueOf(CBToString(cbOption.get, Charsets.Utf8))
      }
    }
  }

  def collect[T1, T2](
      ks: IndexedSeq[T1],
      vs: IndexedSeq[Future[T2]],
      ex: Map[T1, Either[Throwable, T2]]
  ): Future[Map[T1, Either[Throwable, T2]]]= {
    if(ks.isEmpty) {
      Future.value(Map.empty)
    } else {
      Future.selectIndex(vs).flatMap { i: Int =>
        val ksNew = ks.drop(i) ++ ks.dropRight(ks.length - 1 - i)
        val vsNew = vs.drop(i) ++ vs.dropRight(vs.length - 1 - i)
        Await.result(vs(i).liftToTry) match {
          case Return(v) =>
            collect(ksNew, vsNew, ex + (ks(i) -> Right(v)))
          case Throw(t) =>
            collect(ksNew, vsNew, ex + (ks(i) -> Left(t)))
        }
      }
    }
  }

  def read(req: StatReadRequest): Future[StatReadResponse] = {
    val timestamps: IndexedSeq[Int] = (req.fromTimestamp until req.toTimestamp by req.timestampIncrement).toIndexedSeq
    val futureReads: IndexedSeq[Future[Double]] = timestamps.map { timestamp =>
      readOneStat(req.statNamespace, req.statSource, req.statName, timestamp)
    }
    val ex: Map[Int, Either[Throwable, Double]] = Map.empty
    collect(timestamps, futureReads, ex).map { m =>
      val keys = m.keys.toIndexedSeq.sorted
      val values: Seq[Double] = keys.map {
        m(_).fold({_ => Double.NaN}, {v: Double => v})
      }
      val failedTimestamps: Seq[Int] = keys.flatMap { k =>
        m(k).fold({_ => Some(k)}, {_ => None})
      }
      StatReadResponse(values, failedTimestamps)
    }
  }
}