package io.gavani.exporter.companion

import java.util.concurrent.atomic.AtomicReference

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Method}
import com.twitter.logging.Logger
import com.twitter.util.{Await, Timer, Duration, Time}

import io.gavani.thriftscala.StatWriter
import io.gavani.thriftscala.StatWriter.Write.{Args => StatWriterArgs}
import io.gavani.thriftscala.StatWriteRequest

import scala.collection.mutable

class CompanionStatExporter(
    statWriter: StatWriter.ServiceIface,
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
      log.info("Fetching stats for cfg: " + statCollectionConfig)

      val req = Request.apply(Method.Get, "admin/metrics.json")
      val futureStatWrite = statsHttp.apply(req)
        .map { rep => getStatWriteRequest(getStats(rep), thisPeriodEnd) }
        .flatMap { sw: StatWriteRequest => statWriter.write(StatWriterArgs(sw)) }
        .raiseWithin(statsWriteDeadline, new StatsWritingDeadlineExceeded)(timer)

      try {
        Await.result(futureStatWrite)
      } catch {
        case _: StatsWritingDeadlineExceeded =>
          log.error("Stats writing deadline exceeded: " + statCollectionConfig +
            ": at: " + lastExported.get())
        case e: Throwable =>
          log.error(e.toString)
      }

      lastExported.set(thisPeriodEnd)
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

  def getStatWriteRequest(kv: Map[String, Double], thisPeriodEnd: Time): StatWriteRequest = {
    StatWriteRequest(
      statCollectionConfig.namespace,
      statCollectionConfig.source,
      thisPeriodEnd.inSeconds,
      kv
    )
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