package io.gavani.datastore.redis

import com.twitter.finagle.redis
import com.twitter.finagle.redis.util.{CBToString, StringToChannelBuffer}
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.io.Charsets
import com.twitter.logging.Logger
import io.gavani.thriftscala.StatReader
import io.gavani.thriftscala.StatReadRequest
import io.gavani.thriftscala.StatReadResponse
import com.twitter.util._

case class ValueNotFoundAtTimestamp(val timestamp: Int) extends Throwable

class RedisStatReaderService(
    val redisClient: redis.Client,
    val fStoreKey: (String, String, String, Int) => String,
    val statReceiver: StatsReceiver
) extends StatReader[Future] {

  private val log = Logger.get
  private val counterRequests = statReceiver.counter("requests")
  private val counterSuccess = statReceiver.counter("success")
  private val counterFailures = statReceiver.counter("failures")

  private[this] def readOneStat(
      statNamespace: String,
      statSource: String,
      statName: String,
      timestamp: Int
  ): Future[(Int, Double)] = {
    val key = fStoreKey(statNamespace, statSource, statName, timestamp)
    redisClient.get(StringToChannelBuffer.apply(key, Charsets.Utf8)).map {
      case Some(cbValue) =>
        val value = java.lang.Double.valueOf(CBToString(cbValue, Charsets.Utf8))
        (timestamp, value)
      case _ =>
        throw new ValueNotFoundAtTimestamp(timestamp)
    }
  }

  def collect(
      fs: Seq[Future[(Int, Double)]],
      ex: Map[Int, Either[Throwable, Double]]
  ): Future[Map[Int, Either[Throwable, Double]]]= {
    if(fs.isEmpty) {
      Future.value(ex)
    } else {
      Future.select(fs).flatMap { t =>
        val (tryCompleted, futureRemaining) = t
        tryCompleted match {
          case Return((timestamp, value)) =>
            collect(futureRemaining, ex + (timestamp -> Right(value)))
          case Throw(e) => e match {
            case ValueNotFoundAtTimestamp(timestamp) =>
              val leftEx: Either[Throwable, Double] = Left(e)
              collect(futureRemaining, ex + (timestamp -> leftEx))
            case _ =>
              collect(futureRemaining, ex)
          }
          case _ =>
            collect(futureRemaining, ex)
        }
      }
    }
  }

  override def read(req: StatReadRequest): Future[StatReadResponse] = {
    counterRequests.incr()
    val timestamps: Seq[Int] = (req.fromTimestamp until req.toTimestamp by req.timestampIncrement).toIndexedSeq
    val futureReads: Seq[Future[(Int, Double)]] = timestamps.map { timestamp =>
      readOneStat(req.statNamespace, req.statSource, req.statName, timestamp)
    }
    val ex: Map[Int, Either[Throwable, Double]] = Map.empty
    collect(futureReads, ex).map { m =>
      val keys = m.keys.toIndexedSeq.sorted
      val values: Seq[Double] = keys.map {
        m(_).fold({_ => Double.NaN}, {v: Double => v})
      }
      val failedTimestamps: Seq[Int] = keys.flatMap { k =>
        m(k).fold({_ => Some(k)}, {_ => None})
      }
      StatReadResponse(values, failedTimestamps)
    }
    .onSuccess { _ => counterSuccess.incr() }
    .onFailure { _ => counterFailures.incr() }
  }
}