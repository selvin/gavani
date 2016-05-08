package io.gavani.datastore.redis

object KeyScheme {
  val separator = '/'

  def concat(ns: String, src: String, stat: String): String = {
    "gavani" + separator + ns + separator + src + separator + stat
  }

  def concat(ns: String, src: String, stat: String, ts: Int): String = {
    "gavani" + separator + ns + separator + src + separator + stat + separator + ts
  }

  def keyKey(keyScheme: String): (String, String, String) => String = keyScheme match {
    case "concat" => concat
  }

  def keyValue(keyScheme: String): (String, String, String, Int) => String  = keyScheme match {
    case "concat" => concat
  }
}