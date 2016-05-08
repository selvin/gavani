package io.gavani.api

import com.google.inject.Provides
import com.google.inject.Singleton
import com.twitter.app.Flag
import com.twitter.finagle.Thrift
import com.twitter.inject.TwitterModule
import com.twitter.util.Future

import io.gavani.thriftscala.StatReader

class GavaniReaderModule extends TwitterModule {
  val gavaniReaderDestination: Flag[String] = flag(
    "gavaniReaderDest",
    "/$/inet/localhost/6472",
    "Destination of Gavani Reader")

  @Provides
  @Singleton
  def providesPubSubClientWrapper(): StatReader.FutureIface = {
    Thrift.newIface[StatReader.FutureIface](gavaniReaderDestination(), "gavani_reader")
  }
}