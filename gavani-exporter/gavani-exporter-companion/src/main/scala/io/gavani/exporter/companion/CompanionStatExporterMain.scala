package io.gavani.exporter.companion

import java.io.File
import java.util.concurrent.atomic.AtomicReference

import com.twitter.finagle.{Http, Thrift}
import com.twitter.logging.Logger
import com.twitter.server.TwitterServer
import com.twitter.util.{TimerTask, Timer, JavaTimer}
import com.twitter.util.TimeConversions._

import io.gavani.thriftscala.StatWriter

object CompanionStatExporterMain extends TwitterServer {
  val timer: Timer = new JavaTimer()

  val flagStatWriterDestination = flag("statWriterDestination",
    "/$/inet/127.0.0.1/6473", "dest for StatWriter")

  val flagPeriod = flag("period", 1.minute, "Period for exporting stats")

  val flagPerPeriodDelay = flag("perPeriodDelay", 1.second,
    "Per Period delay when exporting stats")

  val flagSchedulePeriod = flag("schedulePeriod", 1.second,
    "Period to wake up task and decide if it is time for the next export; period/2,3,4 etc.,")

  val flagStatsWriteDeadline = flag("statsWriteDeadline", 45.seconds,
    "Deadline for completion of stats write")

  val flagStatCollectionConfig = flag[File]("statCollectionConfig",
    { throw new IllegalArgumentException },
    "stat collection configs for each source whose stats are being exported")

  override def failfastOnFlagsNotParsed: Boolean = true

  val exporterTimerTasks = new AtomicReference[Seq[TimerTask]](Nil)

  override def defaultHttpPort = 9471

  private[this] def makeExporters(): Seq[CompanionStatExporter] = {
    val statWriter = Thrift.newIface[StatWriter.FutureIface](
      flagStatWriterDestination(),
      "exporter_stat_writer"
    )

    val exporters = StatCollectionConfig.fromJSONFile(
      flagStatCollectionConfig()).map { statCollectionConfig: StatCollectionConfig =>
        log.info("Creating an exporter for cfg: " + statCollectionConfig.toString)

        val hostSlashPort = statCollectionConfig.addr.getHostName + "/" +
          statCollectionConfig.addr.getPort
        val dest = "/$/inet/" + hostSlashPort
        val label = "statsHTTP" + hostSlashPort

        val httpClient = Http.client.newService(dest, label)

        new CompanionStatExporter(
          statWriter,
          httpClient,
          statCollectionConfig,
          flagPeriod(),
          flagPerPeriodDelay(),
          flagStatsWriteDeadline(),
          timer)
      }

    exporters
  }

  def main(): Unit = {
    val exporters = makeExporters()
    exporterTimerTasks.set(exporters.map { exporter =>
      val exporterTimerTask = timer.schedule(flagSchedulePeriod()) { exporter.process() }
      closeOnExit(exporterTimerTask)
      exporterTimerTask
    })
  }

  onExit {
    exporterTimerTasks.get().foreach {
      _.close(1.second)
    }
  }
}