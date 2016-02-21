package io.gavani.datastore.redis

import java.net.InetSocketAddress

import com.twitter.server.TwitterServer
import com.twitter.finagle.Redis
import com.twitter.finagle.Thrift
import com.twitter.util.Await

object RedisStatReaderServer extends TwitterServer {
  val flagRedistDest = flag("redisDest", "/$/inet/localhost:6739", "redis dest")
  val flagServeAddr = flag("serveAddr", new InetSocketAddress(0), "service address")
  val flagStorageKeyFunction = flag("storageKeyFunction", "concat", "Method used to generate a storage key per stat stored: (concat, concat+sha1)")

  def main() {
    val fStorageKey = flagStorageKeyFunction() match {
      case "concat" =>
        (ns: String, src: String, stat: String, ts: Int) =>
          ns + ":" + src + ":" + stat + ":" + ts
    }
    val server = Thrift.serveIface(
      flagServeAddr(),
      new RedisStatReaderService(
        Redis.client.newRichClient(flagRedistDest()),
        fStorageKey
      )
    )

    onExit {
      server.close()
    }

    Await.ready(server)
  }
}