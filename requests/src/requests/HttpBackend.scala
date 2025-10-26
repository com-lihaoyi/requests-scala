package requests

import java.io._
import java.net.{HttpCookie, URL}
import javax.net.ssl.SSLContext
import scala.collection.immutable.ListMap

/**
 * Platform-specific HTTP backend interface.
 * This allows requests-scala to use different implementations for JVM and Scala Native.
 */
trait HttpBackend {
  /**
   * Executes an HTTP request and returns a streaming response.
   *
   * @return A geny.Readable that provides access to the response stream
   */
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
  ): geny.Readable
}

/**
 * Platform-specific implementation selector.
 * The actual implementation is provided by platform-specific code in src-jvm/ or src-native/.
 */
object HttpBackend {
  /**
   * Returns the platform-specific HTTP backend implementation.
   * This is implemented in platform-specific source folders (JvmHttpBackend or NativeHttpBackend).
   */
  def platform: HttpBackend = PlatformHttpBackend.instance
}
