package io.gavani.datastore.redis

import com.twitter.finagle.Thrift
import com.twitter.util.Await
import io.gavani.thriftscala
import com.twitter.app.App

class StatReaderClient extends App {

  def helpfullyExit() = {
    sys.error("Usage: statReader dest service source statName fromTimestamp toTimestamp")
  }

  def main: Unit = {
    if(this.args.length == 6 || this.args.length == 7) {
      val args = this.args
      val fromTimestamp = args(4).toInt
      val toTimestamp = args(5).toInt
      val timestampIncrement = args(6).toInt
      val statReadRequest = thriftscala.StatReadRequest(
        args(1), args(2), args(3), fromTimestamp, toTimestamp, timestampIncrement
      )
      val statReader = Thrift.newIface[thriftscala.StatReader.FutureIface](args(0), "gavani_reader")
      val statReadResponse = Await.result(statReader.read(statReadRequest))
      ((fromTimestamp until toTimestamp).by(timestampIncrement), statReadResponse.values)
        .zipped
        .foreach { (timestamp: Int, value: Double) =>
          println("%10d: %f".format(timestamp, value))
        }
      statReadResponse.failedTimestamps.size match {
        case 0 =>
        case _ => sys.error("Failed timestamps: " + statReadResponse.failedTimestamps.mkString(","))
      }
    } else {
      helpfullyExit()
    }
  }
}