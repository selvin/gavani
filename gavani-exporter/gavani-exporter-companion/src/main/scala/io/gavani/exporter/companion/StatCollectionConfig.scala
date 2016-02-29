package io.gavani.exporter.companion

import java.net.InetSocketAddress
import com.twitter.app.Flaggable

case class StatCollectionConfig(addr: InetSocketAddress, namespace: String, source: String)

object StatCollectionConfig extends Flaggable[Seq[StatCollectionConfig]] {
  def parse(s: String): Seq[StatCollectionConfig] = {
    val cfgsStr = s.split(",")
    cfgsStr.map { cfgStr =>
      val cfgParts = cfgStr.split(":")
      if(cfgParts.length == 4) {
        StatCollectionConfig(
          new InetSocketAddress(cfgParts(0), Integer.valueOf(cfgParts(1))),
          cfgParts(2),
          cfgParts(3)
        )
      } else {
        throw new IllegalArgumentException
      }
    }
  }

  override def show(cfgs: Seq[StatCollectionConfig]): String = {
    cfgs.map { cfg =>
      cfg.toString + ":" + cfg.namespace + ":" + cfg.source
    }.mkString(",")
  }
}