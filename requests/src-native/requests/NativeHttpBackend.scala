package requests

import java.io._
import java.net.{HttpCookie, URL}
import javax.net.ssl.SSLContext

/**
 * Scala Native implementation of HttpBackend.
 * 
 * NOTE: This is a placeholder implementation. Full Scala Native support requires:
 * 1. java.net.http.HttpClient implementation for Scala Native (scala-native/scala-native#4104)
 * 2. java.net.HttpCookie implementation for Scala Native (scala-native/scala-native#3927)  
 * 3. javax.net.ssl.SSLContext support via scala-native-crypto
 *
 * Once these dependencies are available, this can be implemented using either:
 * - HttpClient (preferred, matches JVM implementation)
 * - libcurl bindings (interim solution, see domaspoliakas's WIP branch)
 */
private[requests] class NativeHttpBackend extends HttpBackend {
  
  def execute(
      verb: String,
      url: String,
      auth: RequestAuth,
      params: Iterable[(String, String)],
      headers: Map[String, String],
      data: RequestBlob,
      readTimeout: Int,
      connectTimeout: Int,
      proxy: (String, Int),
      cert: Cert,
      sslContext: SSLContext,
      cookies: Map[String, HttpCookie],
      cookieValues: Map[String, String],
      maxRedirects: Int,
      verifySslCerts: Boolean,
      autoDecompress: Boolean,
      compress: Compress,
      keepAlive: Boolean,
      check: Boolean,
      chunkedUpload: Boolean,
      redirectedFrom: Option[Response],
      onHeadersReceived: StreamHeaders => Unit,
      sess: BaseSession,
  ): geny.Readable = {
    throw new NotImplementedError(
      """Scala Native support for requests-scala is not yet available.
        |
        |This implementation requires:
        |1. java.net.http.HttpClient for Scala Native (see scala-native/scala-native#4104)
        |2. java.net.HttpCookie for Scala Native (see scala-native/scala-native#3927)
        |3. Full javax.net.ssl.SSLContext support via scala-native-crypto
        |
        |These features are currently under development. For updates, see:
        |https://github.com/com-lihaoyi/requests-scala/issues/156
        |
        |Alternative interim solutions being explored:
        |- libcurl-based backend (see @domaspoliakas's WIP branch)
        |- Waiting for scala-native-http (see @lqhuang's work)
        |""".stripMargin
    )
  }
}

/**
 * Scala Native-specific PlatformHttpBackend implementation.
 */
private[requests] object PlatformHttpBackend {
  val instance: HttpBackend = new NativeHttpBackend()
}
