package io.gavani.api

import io.gavani.thriftscala.StatReader
import io.gavani.thriftscala.StatReadRequest
import io.gavani.thriftscala.StatReadResponse
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.util.{Duration, Future, Time}
import com.twitter.util.TimeConversions._

import javax.inject.Inject

case class StatPath(namespace: String, source: String, statName: String)

object StatPath{
  val STAT_PATH_SOURCE_PREFIX = ":source/"
  val STAT_PATH_STAT_PREFIX = ":stat/"
  val STAT_PATH_NAMESPACE_PREFIX = ":namespace/"

  def fromString(s: String): Option[StatPath] = {
    try {
      val p1 = s.split(STAT_PATH_STAT_PREFIX)
      val (p2, statName) = (p1(0), p1(1))
      val p3 = p2.split(STAT_PATH_SOURCE_PREFIX)
      val (p4, sourceName) = (p3(0), p3(1))
      val p5 = p4.split(STAT_PATH_NAMESPACE_PREFIX)
      val (_, namespace) = (p5(0), p5(1))

      Some(StatPath(namespace, sourceName, statName))
    } catch {
      case t: IndexOutOfBoundsException =>
        None
    }
  }
}

class GavaniReaderController @Inject()(
    gavaniReader: StatReader.FutureIface
  ) extends Controller {
  def getFromTimestamp(request: Request): Int = {
    request.getIntParam("from", (Time.now.floor(1.minute) - 2.hours).inSeconds)
  }

  def getToTimestamp(request: Request): Int = {
    request.getIntParam("to", Time.now.floor(1.minute).inSeconds)
  }

  def getTimeDelta(request: Request): Int = {
    request.getIntParam("delta", 60)
  }

  get("/gavani/api/stats/:*") { request: Request =>
    request.params.get("*") match {
      case Some(s) =>
        StatPath.fromString(s) match {
          case Some(statPath) =>
            val statReadRequest = StatReadRequest(
              statPath.namespace,
              statPath.source,
              statPath.statName,
              getFromTimestamp(request),
              getToTimestamp(request),
              request.getIntParam("delta", 60)
            )

            gavaniReader.read(statReadRequest).map { statReadResponse =>
              response.ok.json(Map(
                "values" -> statReadResponse.values,
                "failedTimestamps" -> statReadResponse.failedTimestamps
              ))
            }
          case None =>
            Future.value(response.notFound)
        }
      case None =>
        Future.value(response.notFound)
    }
  }
}