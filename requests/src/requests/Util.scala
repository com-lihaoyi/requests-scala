package requests

import java.io.{InputStream, OutputStream}
import java.net.URLEncoder
import java.security.cert.X509Certificate

import javax.net.ssl.{SSLContext, TrustManager, X509TrustManager}

object Util {
  def transferTo(is: InputStream,
                 os: OutputStream,
                 bufferSize: Int = 8 * 1024) = {
    val buffer = new Array[Byte](bufferSize)
    while ( {
      is.read(buffer) match {
        case -1 => false
        case n =>
          os.write(buffer, 0, n)
          true
      }
    }) ()
  }

  def urlEncode(x: Iterable[(String, String)]) = {
    x.map{case (k, v) => URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")}
      .mkString("&")
  }

  private[requests] val noVerifySocketFactory = {
    val trustAllCerts = Array[TrustManager](new X509TrustManager() {
      def getAcceptedIssuers() = new Array[X509Certificate](0)

      def checkClientTrusted(chain: Array[X509Certificate], authType: String) = {}

      def checkServerTrusted(chain: Array[X509Certificate], authType: String) = {}
    })

    // Install the all-trusting trust manager

    val sc = SSLContext.getInstance("SSL")
    sc.init(null, trustAllCerts, new java.security.SecureRandom())

    sc.getSocketFactory
  }
}
