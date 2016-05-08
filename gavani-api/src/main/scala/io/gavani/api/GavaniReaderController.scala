package io.gavani.api

import io.gavani.thriftscala.StatReader
import io.gavani.thriftscala.StatReadRequest
import io.gavani.thriftscala.StatReadResponse
import com.twitter.finagle.http.Request
import com.twitter.finatra.http.Controller
import com.twitter.util.{Duration, Future, Time}
import com.twitter.util.TimeConversions._

import javax.inject.Inject

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

  get("/gavani/api/stats.json") { request: Request =>
    val statReadRequest = StatReadRequest(
      request.getParam("namespace"),
      request.getParam("source"),
      request.getParam("stat"),
      getFromTimestamp(request),
      getToTimestamp(request),
      request.getIntParam("delta", 60)
    )

    val futureStatReadResponse = gavaniReader.read(statReadRequest)
    futureStatReadResponse.map { statReadResponse =>
        System.err.println("got response, making json")
        response.ok.json(Map(
          "values" -> statReadResponse.values,
          "failedTimestamps" -> statReadResponse.failedTimestamps
        ))
      }
  }
}