package io.gavani.exporter.companion

import java.util.concurrent.atomic.AtomicReference

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Method}
import com.twitter.logging.Logger
import com.twitter.util.{Await, Timer, Duration, Time}
import com.twitter.util.Future

import io.gavani.thriftscala._

import scala.collection.mutable

class CompanionStatExporter(
    statWriter: StatWriter.FutureIface,
    statsHttp: Service[Request, Response],
    statCollectionConfig: StatCollectionConfig,
    period: Duration,
    perPeriodDelay: Duration,
    statsWriteDeadline: Duration,
    timer: Timer
) {
  class InvalidStatsException extends Exception
  class StatsWritingDeadlineExceeded extends Exception

  val log = Logger.get
  val mapper = new ObjectMapper()

  var lastExported: AtomicReference[Time] = new AtomicReference(Time.now.floor(period))

  def process(): Unit = synchronized {
    val currTime = Time.now
    if(currTime - lastExported.get() >= period + perPeriodDelay) {
      val thisPeriodEnd = currTime.floor(period)
      log.info("Fetching stats for period: " + thisPeriodEnd.toString() + " for cfg: " + statCollectionConfig)

      val req = Request.apply(Method.Get, "/admin/metrics.json")
      val futureStatWrite =
        statsHttp(req)
          .flatMap { rep => writeStats(getStats(rep), thisPeriodEnd) }
          // .raiseWithin(statsWriteDeadline, new StatsWritingDeadlineExceeded)(timer)
          .onSuccess { _ =>
            log.info("Wrote metrics for cfg: " + statCollectionConfig)
            lastExported.set(thisPeriodEnd)
          }
          .onFailure { _ =>
            log.info("Failed writing metrics for cfg: " + statCollectionConfig)
          }

      try {
        Await.result(futureStatWrite)
        log.info("Wrote metrics for cfg: " + statCollectionConfig)
      } catch {
        case _: StatsWritingDeadlineExceeded =>
          log.error("Stats writing deadline exceeded: " + statCollectionConfig +
            ": at: " + lastExported.get())
        case e: Throwable =>
          log.error(e.toString)
      }
    }
  }

  def getStats(response: Response): Map[String, Double] = {
    val json = response.contentString
    val rootNode = mapper.readTree(json)
    if(rootNode.isContainerNode) {
      val it = rootNode.fieldNames()
      val statMap = new mutable.HashMap[String, Double]()
      while(it.hasNext) {
        val key = it.next()
        extractDouble(rootNode.get(key)).map {
          statMap.put(key, _)
        }
      }
      statMap.toMap
    } else {
      throw new InvalidStatsException
    }
  }

  def writeStats(kv: Map[String, Double], thisPeriodEnd: Time): Future[Unit] = {
    statWriter.write(StatWriteRequest(
      statCollectionConfig.namespace,
      statCollectionConfig.source,
      thisPeriodEnd.inSeconds,
      kv
    )).unit
  }

  def extractDouble(node: JsonNode): Option[Double] = {
    if(node.isInt) {
      Some(node.asInt.toDouble)
    } else if(node.isLong) {
      Some(node.asLong.toDouble)
    } else if(node.isDouble) {
      Some(node.asDouble)
    } else if(node.isBoolean) {
      val d: Double = if(node.asBoolean) { 1.0 } else { 0.0 }
      Some(d)
    } else {
      None
    }
  }
}