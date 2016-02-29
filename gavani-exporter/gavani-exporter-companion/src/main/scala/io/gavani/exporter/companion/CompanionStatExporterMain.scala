package io.gavani.exporter.companion

import com.twitter.finagle.{Http, Thrift}
import com.twitter.logging.Logger
import com.twitter.app.App
import com.twitter.util.{Timer, JavaTimer}
import com.twitter.util.TimeConversions._

import io.gavani.thriftscala.StatWriter

object CompanionStatExporterMain extends App {
  val log = Logger.get
  val timer: Timer = new JavaTimer()

  val flagStatWriterDestination = flag("statWriterDestination",
    "/$/inet/localhost/8600", "dest for StatWriter")

  val flagPeriod = flag("period", 1.minute, "Period for exporting stats")

  val flagPerPeriodDelay = flag("perPeriodDelay", 1.second,
    "Per Period delay when exporting stats")

  val flagSchedulePeriod = flag("schedulePeriod", 1.second,
    "Period to wake up task and decide if it is time for the next export; period/2,3,4 etc.,")

  val flagStatsWriteDeadline = flag("statsWriteDeadline", 45.seconds,
    "Deadline for completion of stats write")

  val flagStatCollectionConfigs = flag[Seq[StatCollectionConfig]]("statCollectionConfigs",
    Seq.empty,
    "stat collection configs for each source whose stats are being exported")(StatCollectionConfig)

  override def failfastOnFlagsNotParsed: Boolean = true

  def main(): Unit = {
    val statWriter = Thrift.newServiceIface[StatWriter.ServiceIface](
      flagStatWriterDestination(),
      "exporter_stat_writer"
    )
    val exporters = flagStatCollectionConfigs().map { statCollectionConfig: StatCollectionConfig =>
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

    log.info("Scheduling processing...")
    val exporterTimerTasks = exporters.map { exporter =>
      val exporterTimerTask = timer.schedule(flagSchedulePeriod()) { exporter.process() }
      closeOnExit(exporterTimerTask)
      exporterTimerTask
    }

    while(true) { Thread.sleep(1000) }
  }
}