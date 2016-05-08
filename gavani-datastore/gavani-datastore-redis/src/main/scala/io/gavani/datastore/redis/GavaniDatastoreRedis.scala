package io.gavani.datastore.redis

object GavaniDatastoreRedis {
  def helpfullyExit(): Unit = {
    sys.error("Must specify -server or -client as the first parameter")
    sys.exit(-1)
  }

  def main(args: Array[String]): Unit = {
    if(args.length >= 2) {
      val app = args(0)
      val clientOrServer = args(1)
      val remainingArgs = new Array[String](args.length - 2)
      (2 until args.length).foreach { i =>
        remainingArgs(i - 2) = args(i)
      }
      (app, clientOrServer) match {
        case ("-reader", "-client") => (new StatReaderClient).main(remainingArgs)
        case ("-reader", "-server") => RedisStatReaderServer.main(remainingArgs)
        case ("-writer", "-client") => (new StatReaderClient).main(remainingArgs)
        case ("-writer", "-server") => RedisStatWriterServer.main(remainingArgs)
      }
    } else {
      helpfullyExit()
    }
  }
}