package requests

import java.io.{FileInputStream, InputStream}
import java.security.{KeyStore, SecureRandom}

import javax.net.ssl.{KeyManagerFactory, SSLContext}

object FileUtils {

  def createSslContext(
      keyStorePath: String,
      keyStorePassword: String,
  ): SSLContext = {
    val stream: InputStream = new FileInputStream(keyStorePath)
    val sslContext = SSLContext.getInstance("TLS")

    val keyManagerFactory =
      KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    val keyStore = KeyStore.getInstance("PKCS12")
    keyStore.load(stream, keyStorePassword.toCharArray)
    keyManagerFactory.init(keyStore, keyStorePassword.toCharArray)
    sslContext.init(keyManagerFactory.getKeyManagers, null, new SecureRandom())
    stream.close()

    sslContext
  }

}
