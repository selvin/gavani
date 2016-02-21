package io.gavani.datastore.redis

import java.nio.ByteBuffer

import com.twitter.finagle.redis
import com.twitter.finagle.redis.util.{StringToChannelBuffer, CBToString}
import com.twitter.io.Charsets
import io.gavani.thriftscala.StatWriter
import io.gavani.thriftscala.StatWriteRequest
import com.twitter.util._
import org.jboss.netty.buffer.ChannelBuffer

class RedisStatWriterService(
    val redisClient: redis.Client,
    val fStoreKey: (String, String, String, Int) => String
) extends StatWriter[Future] {
  private[this] def writeOneStat(
      statNamespace: String,
      statSource: String,
      statName: String,
      timestamp: Int,
      value: Double
  ): Future[Unit] = {
    val key = fStoreKey(statNamespace, statSource, statName, timestamp)
    redisClient.set(
      StringToChannelBuffer.apply(key, Charsets.Utf8),
      StringToChannelBuffer.apply(value.toString, Charsets.Utf8))
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

  def write(req: StatWriteRequest): Future[Map[String, String]] = {
    val mapFutureStoreRecords = req.statRecords.map {
      case (k, v) => k -> writeOneStat(req.statNamespace, req.statSource, k, req.timestamp, v)
    }
    val ks = mapFutureStoreRecords.keys.toIndexedSeq
    val vs = ks.map(mapFutureStoreRecords(_))
    val ex: Map[String, Either[Throwable, Unit]] = Map.empty
    collect(ks, vs, ex).map { exMap =>
      exMap.flatMap {
        case(k, v) => v match {
          case Left(t) => Some(k -> t.toString)
          case _ => None
        }
      }
    }
  }
}