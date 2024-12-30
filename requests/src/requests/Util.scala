package requests

import java.io.{FileInputStream, InputStream, OutputStream}
import java.net.URLEncoder
import java.security.cert.X509Certificate

import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManager, X509TrustManager}

object Util {
  def transferTo(
      is: InputStream,
      os: OutputStream,
      bufferSize: Int = 8 * 1024
  ) = {
    val buffer = new Array[Byte](bufferSize)
    while ({
      is.read(buffer) match {
        case -1 => false
        case n =>
          os.write(buffer, 0, n)
          true
      }
    }) ()
  }

  def urlEncode(x: Iterable[(String, String)]) = {
    x.map { case (k, v) =>
      URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
    }.mkString("&")
  }

  private[requests] val noVerifySSLContext = {
    // Install the all-trusting trust manager

    val sc = SSLContext.getInstance("SSL")
    sc.init(null, trustAllCerts, new java.security.SecureRandom())

    sc
  }

  @deprecated("No longer used", "0.9.0")
  private[requests] val noVerifySocketFactory =
    noVerifySSLContext.getSocketFactory

  private[requests] def clientCertSSLContext(
      cert: Cert,
      verifySslCerts: Boolean
  ) = cert match {
    case Cert.P12(path, password) =>
      val pass = password.map(_.toCharArray).getOrElse(Array.emptyCharArray)

      val keyManagers = {
        val ks = java.security.KeyStore.getInstance("PKCS12")
        ks.load(new FileInputStream(path), pass)
        val keyManager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
        keyManager.init(ks, pass)
        keyManager.getKeyManagers
      }

      val sc = SSLContext.getInstance("SSL")

      val trustManagers = if (verifySslCerts) null else trustAllCerts

      sc.init(keyManagers, trustManagers, new java.security.SecureRandom())
      sc
  }

  @deprecated("No longer used", "0.9.0")
  private[requests] def clientCertSocketFactory(
      cert: Cert,
      verifySslCerts: Boolean
  ) = clientCertSSLContext(cert, verifySslCerts).getSocketFactory

  private lazy val trustAllCerts = Array[TrustManager](new X509TrustManager() {
    def getAcceptedIssuers = new Array[X509Certificate](0)

    def checkClientTrusted(chain: Array[X509Certificate], authType: String) = {}

    def checkServerTrusted(chain: Array[X509Certificate], authType: String) = {}
  })
}
