package requests

import java.io.FileInputStream
import java.security.cert.X509Certificate
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManager, X509TrustManager}

/**
 * JVM-specific SSL utility methods.
 * Only available on JVM platforms.
 */
object SslUtil {
  private[requests] val noVerifySSLContext: SSLContext = {
    val sc = SSLContext.getInstance("SSL")
    sc.init(null, trustAllCerts, new java.security.SecureRandom())
    sc
  }

  private[requests] def clientCertSSLContext(
      cert: Cert,
      verifySslCerts: Boolean,
  ): SSLContext = cert match {
    case Cert.P12(path, password) =>
      val pass = password.map(_.toCharArray).getOrElse(Array.emptyCharArray)
      val ks = java.security.KeyStore.getInstance("PKCS12")
      ks.load(new FileInputStream(path), pass)
      val keyManager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
      keyManager.init(ks, pass)
      val sc = SSLContext.getInstance("SSL")
      val trustManagers = if (verifySslCerts) null else trustAllCerts
      sc.init(keyManager.getKeyManagers, trustManagers, new java.security.SecureRandom())
      sc
  }

  private lazy val trustAllCerts = Array[TrustManager](new X509TrustManager() {
    def getAcceptedIssuers = new Array[X509Certificate](0)
    def checkClientTrusted(chain: Array[X509Certificate], authType: String) = {}
    def checkServerTrusted(chain: Array[X509Certificate], authType: String) = {}
  })
}
