package io.gavani.exporter.companion

import java.io.{FileInputStream, File}
import java.net.InetSocketAddress

import com.fasterxml.jackson.databind.ObjectMapper

import scala.collection.JavaConversions

case class StatCollectionConfig(
  addr: InetSocketAddress,
  namespace: String,
  source: String
)

object StatCollectionConfig {
  val objectReader = (new ObjectMapper).reader()

  def fromJSONFile(file: File): Seq[StatCollectionConfig] = {
    val rootNode = objectReader.readTree(new FileInputStream(file))
    val cfgNodes = JavaConversions.asScalaIterator(rootNode.elements()).toSeq
    cfgNodes.map { cfgNode =>
      StatCollectionConfig(
        new InetSocketAddress(cfgNode.get("host").asText(), cfgNode.get("port").asInt()),
        cfgNode.get("namespace").asText(),
        cfgNode.get("source").asText()
      )
    }
  }
}