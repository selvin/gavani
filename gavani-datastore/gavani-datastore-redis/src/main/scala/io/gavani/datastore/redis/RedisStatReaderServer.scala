package io.gavani.datastore.redis

import java.net.InetSocketAddress

import com.twitter.server.TwitterServer
import com.twitter.finagle.Redis
import com.twitter.finagle.Thrift
import com.twitter.util.Await

object RedisStatReaderServer extends TwitterServer {
  val flagRedistDest = flag("redisDest", "/$/inet/localhost/6379", "redis dest")
  val flagServeAddr = flag("serveAddr", new InetSocketAddress(6472), "service address")
  val flagKeyKeyFunction = flag("keyKey", "concat", "Method used to generate a storage key per stat stored: (concat, concat+sha1)")

  override def defaultHttpPort = 9472

  def main() {
    val server = Thrift.serveIface(
      flagServeAddr(),
      new RedisStatReaderService(
        Redis.client.newRichClient(flagRedistDest()),
        KeyScheme.keyValue(flagKeyKeyFunction()),
        statsReceiver.scope("gavani_reader")
      )
    )

    onExit {
      server.close()
    }

    Await.ready(server)
  }
}