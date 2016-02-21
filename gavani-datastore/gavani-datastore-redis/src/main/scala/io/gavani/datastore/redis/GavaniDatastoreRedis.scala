package io.gavani.datastore.redis

object GavaniDatastoreRedis {
  def main(args: Array[String]): Unit = {
    args.headOption match {
      case Some("-reader") => RedisStatWriterServer.main(args.tail)
      case Some("-writer") => RedisStatReaderServer.main(args.tail)
      case _ =>
        System.err.println("Must specify -server or -client as the first parameter")
    }
  }
}