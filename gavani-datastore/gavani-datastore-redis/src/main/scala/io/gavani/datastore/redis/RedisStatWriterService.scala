package io.gavani.datastore.redis

import java.nio.ByteBuffer

import com.twitter.finagle.redis
import com.twitter.finagle.redis.util.{StringToChannelBuffer, CBToString}
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.io.Charsets
import com.twitter.logging.Logger
import io.gavani.thriftscala.StatWriter
import io.gavani.thriftscala.StatWriteRequest
import com.twitter.util._
import org.jboss.netty.buffer.ChannelBuffer

class RedisStatWriterService(
    val redisClient: redis.Client,
    val fStoreKey: (String, String, String, Int) => String,
    val statReceiver: StatsReceiver
) extends StatWriter[Future] {
  private val log = Logger.get
  private val counterRequests = statReceiver.counter("requests")
  private val counterSuccess = statReceiver.counter("success")
  private val counterFailures = statReceiver.counter("failures")

  private[this] def writeOneStat(
      statNamespace: String,
      statSource: String,
      statName: String,
      timestamp: Int,
      value: Double
  ): Future[Unit] = {
    val key = fStoreKey(statNamespace, statSource, statName, timestamp)
    redisStore(key, value.toString)
  }

  def redisStore(k: String, v: String): Future[Unit] = {
    redisClient.set(
      StringToChannelBuffer(k, Charsets.Utf8),
      StringToChannelBuffer(v, Charsets.Utf8))
  }

  def collect[T1, T2](
      ks: IndexedSeq[T1],
      vs: IndexedSeq[Future[T2]],
      ex: Map[T1, Either[Throwable, T2]]
  ): Future[Map[T1, Either[Throwable, T2]]] = {
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

  override def write(req: StatWriteRequest): Future[Map[String, String]] = {
    counterRequests.incr()
    val mapFutureStoreRecords = req.statRecords.map {
      case (k, v) => k -> writeOneStat(req.statNamespace, req.statSource, k, req.timestamp, v)
    }
    /*val ks = mapFutureStoreRecords.keys.toIndexedSeq
    val vs = ks.map(mapFutureStoreRecords(_))
    val ex: Map[String, Either[Throwable, Unit]] = Map.empty
    collect(ks, vs, ex).map { exMap =>
      exMap.flatMap {
        case(k, v) => v match {
          case Left(t) => Some(k -> t.toString)
          case _ => None
        }
      }
    }*/
    Future.collect(mapFutureStoreRecords.values.toSeq)
      .flatMap { _ =>
        Future.join(
          redisStore("gavani_ns/" + req.statNamespace, "1"),
          redisStore("gavani_src/" + req.statNamespace + "/" + req.statSource, "1")
        )
      }
      .map { _ => Map.empty[String,String] }
      .onSuccess { _ => counterSuccess.incr() }
      .onFailure {
        case t: Throwable =>
          counterFailures.incr()
      }
  }
}