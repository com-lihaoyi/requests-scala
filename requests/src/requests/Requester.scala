package requests

import java.io._
import java.net.HttpCookie
import scala.collection.mutable
import javax.net.ssl.SSLContext

trait BaseSession extends AutoCloseable {
  def headers: Map[String, String]
  def cookies: mutable.Map[String, HttpCookie]
  def readTimeout: Int
  def connectTimeout: Int
  def auth: RequestAuth
  def proxy: (String, Int)
  def cert: Cert
  def sslContext: SSLContext
  def maxRedirects: Int
  def persistCookies: Boolean
  def verifySslCerts: Boolean
  def autoDecompress: Boolean
  def compress: Compress
  def chunkedUpload: Boolean
  def check: Boolean
  lazy val get = Requester("GET", this)
  lazy val post = Requester("POST", this)
  lazy val put = Requester("PUT", this)
  lazy val delete = Requester("DELETE", this)
  lazy val head = Requester("HEAD", this)
  lazy val options = Requester("OPTIONS", this)
  // unofficial
  lazy val patch = Requester("PATCH", this)

  def send(method: String) = Requester(method, this)

  def executor: java.util.concurrent.ExecutorService
  def sharedHttpClient: Any
}

object BaseSession {
  val defaultHeaders = Map(
    "User-Agent" -> "requests-scala",
    "Accept-Encoding" -> "gzip, deflate",
    "Accept" -> "*/*",
  )
}

case class Requester(verb: String, sess: BaseSession) {
  private val upperCaseVerb = verb.toUpperCase

  def apply(
      url: String,
      auth: RequestAuth = sess.auth,
      params: Iterable[(String, String)] = Nil,
      headers: Iterable[(String, String)] = Nil,
      data: RequestBlob = RequestBlob.EmptyRequestBlob,
      readTimeout: Int = sess.readTimeout,
      connectTimeout: Int = sess.connectTimeout,
      proxy: (String, Int) = sess.proxy,
      cert: Cert = sess.cert,
      sslContext: SSLContext = sess.sslContext,
      cookies: Map[String, HttpCookie] = Map(),
      cookieValues: Map[String, String] = Map(),
      maxRedirects: Int = sess.maxRedirects,
      verifySslCerts: Boolean = sess.verifySslCerts,
      autoDecompress: Boolean = sess.autoDecompress,
      compress: Compress = sess.compress,
      keepAlive: Boolean = true,
      check: Boolean = sess.check,
      chunkedUpload: Boolean = sess.chunkedUpload,
  ): Response = {
    val out = new ByteArrayOutputStream()

    var streamHeaders: StreamHeaders = null
    val w =
      stream(
        url = url,
        auth = auth,
        params = params,
        blobHeaders = data.headers,
        headers = headers,
        data = data,
        readTimeout = readTimeout,
        connectTimeout = connectTimeout,
        proxy = proxy,
        cert = cert,
        sslContext = sslContext,
        cookies = cookies,
        cookieValues = cookieValues,
        maxRedirects = maxRedirects,
        verifySslCerts = verifySslCerts,
        autoDecompress = autoDecompress,
        compress = compress,
        keepAlive = keepAlive,
        check = check,
        chunkedUpload = chunkedUpload,
        onHeadersReceived = sh => streamHeaders = sh,
      )

    w.writeBytesTo(out)

    Response(
      url = streamHeaders.url,
      statusCode = streamHeaders.statusCode,
      statusMessage = streamHeaders.statusMessage,
      data = new geny.Bytes(out.toByteArray),
      headers = streamHeaders.headers,
      history = streamHeaders.history,
    )
  }

  def stream(
      url: String,
      auth: RequestAuth = sess.auth,
      params: Iterable[(String, String)] = Nil,
      blobHeaders: Iterable[(String, String)] = Nil,
      headers: Iterable[(String, String)] = Nil,
      data: RequestBlob = RequestBlob.EmptyRequestBlob,
      readTimeout: Int = sess.readTimeout,
      connectTimeout: Int = sess.connectTimeout,
      proxy: (String, Int) = sess.proxy,
      cert: Cert = sess.cert,
      sslContext: SSLContext = sess.sslContext,
      cookies: Map[String, HttpCookie] = Map(),
      cookieValues: Map[String, String] = Map(),
      maxRedirects: Int = sess.maxRedirects,
      verifySslCerts: Boolean = sess.verifySslCerts,
      autoDecompress: Boolean = sess.autoDecompress,
      compress: Compress = sess.compress,
      keepAlive: Boolean = true,
      check: Boolean = true,
      chunkedUpload: Boolean = false,
      redirectedFrom: Option[Response] = None,
      onHeadersReceived: StreamHeaders => Unit = null,
  ): geny.Readable = new geny.Readable {
    def readBytesThrough[T](f: java.io.InputStream => T): T = {
      Platform.makeRequest(
        sess = sess,
        url = url,
        verb = upperCaseVerb,
        auth = auth,
        params = params,
        blobHeaders = blobHeaders,
        headers = headers,
        data = data,
        readTimeout = readTimeout,
        connectTimeout = connectTimeout,
        proxy = proxy,
        cert = cert,
        sslContext = sslContext,
        cookies = cookies,
        cookieValues = cookieValues,
        maxRedirects = maxRedirects,
        verifySslCerts = verifySslCerts,
        autoDecompress = autoDecompress,
        compress = compress,
        keepAlive = keepAlive,
        check = check,
        chunkedUpload = chunkedUpload,
        redirectedFrom = redirectedFrom,
        onHeadersReceived = onHeadersReceived,
        f = f,
        streamRecurse = (newUrl, redirectedFrom) => stream(
          url = newUrl,
          auth = auth,
          params = params,
          blobHeaders = blobHeaders,
          headers = headers,
          data = data,
          readTimeout = readTimeout,
          connectTimeout = connectTimeout,
          proxy = proxy,
          cert = cert,
          sslContext = sslContext,
          cookies = cookies,
          cookieValues = cookieValues,
          maxRedirects = maxRedirects - 1,
          verifySslCerts = verifySslCerts,
          autoDecompress = autoDecompress,
          compress = compress,
          keepAlive = keepAlive,
          check = check,
          chunkedUpload = chunkedUpload,
          redirectedFrom = redirectedFrom,
          onHeadersReceived = onHeadersReceived,
        ).readBytesThrough(f)
      )
    }
  }

  def apply(r: Request, data: RequestBlob, chunkedUpload: Boolean): Response =
    apply(
      r.url,
      r.auth,
      r.params,
      r.headers,
      data,
      r.readTimeout,
      r.connectTimeout,
      r.proxy,
      r.cert,
      r.sslContext,
      r.cookies,
      r.cookieValues,
      r.maxRedirects,
      r.verifySslCerts,
      r.autoDecompress,
      r.compress,
      r.keepAlive,
      r.check,
      chunkedUpload,
    )

  def stream(
      r: Request,
      data: RequestBlob,
      chunkedUpload: Boolean,
      onHeadersReceived: StreamHeaders => Unit,
  ): geny.Writable =
    stream(
      url = r.url,
      auth = r.auth,
      params = r.params,
      blobHeaders = Seq.empty[(String, String)],
      headers = r.headers,
      data = data,
      readTimeout = r.readTimeout,
      connectTimeout = r.connectTimeout,
      proxy = r.proxy,
      cert = r.cert,
      sslContext = r.sslContext,
      cookies = r.cookies,
      cookieValues = r.cookieValues,
      maxRedirects = r.maxRedirects,
      verifySslCerts = r.verifySslCerts,
      autoDecompress = r.autoDecompress,
      compress = r.compress,
      keepAlive = r.keepAlive,
      check = r.check,
      chunkedUpload = chunkedUpload,
      redirectedFrom = None,
      onHeadersReceived = onHeadersReceived,
    )
}
