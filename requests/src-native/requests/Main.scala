package requests

import scala.scalanative.unsafe._

object Main {
  def main(args: Array[String]): Unit = {
    println("Initializing Curl...")
    val curl = CurlApi.curl_easy_init()
    if (curl == null) {
      println("Failed to initialize Curl")
    } else {
      println("Curl initialized successfully")
      CurlApi.curl_easy_cleanup(curl)
    }
  }
}
