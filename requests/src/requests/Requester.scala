package requests

import java.io._
import java.net.{UnknownHostException => _, HttpCookie, _}

import scala.collection.mutable

import javax.net.ssl.SSLContext

trait BaseSession {
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
}

object BaseSession {
  val defaultHeaders = Map(
    "User-Agent" -> "requests-scala",
    "Accept-Encoding" -> "gzip, deflate",
    "Accept" -> "*/*",
  )
}

object Requester {
  val officialHttpMethods = Set("GET", "POST", "HEAD", "OPTIONS", "PUT", "DELETE", "TRACE")
}

case class Requester(verb: String, sess: BaseSession) {
  private val upperCaseVerb = verb.toUpperCase

  /**
   * Makes a single HTTP request, and returns a [[Response]] object. Requires all uploaded request
   * `data` to be provided up-front, and aggregates all downloaded response `data` before returning
   * it in the response. If you need streaming access to the upload and download, use the
   * [[Requester.stream]] function instead.
   *
   * @param url
   *   The URL to which you want to make this HTTP request
   * @param auth
   *   HTTP authentication you want to use with this request; defaults to none
   * @param params
   *   URL params to pass to this request, for `GET`s and `DELETE`s
   * @param headers
   *   Custom headers to use, in addition to the defaults
   * @param data
   *   Body data to pass to this request, for POSTs and PUTs. Can be a Map[String, String] of form
   *   data, bulk data as a String or Array[Byte], or MultiPart form data.
   * @param readTimeout
   *   How many milliseconds to wait for data to be read before timing out
   * @param connectTimeout
   *   How many milliseconds to wait for a connection before timing out
   * @param proxy
   *   Host and port of a proxy you want to use
   * @param cert
   *   Client certificate configuration
   * @param sslContext
   *   Client sslContext configuration
   * @param cookies
   *   Custom cookies to send up with this request
   * @param maxRedirects
   *   How many redirects to automatically resolve; defaults to 5. You can also set it to 0 to
   *   prevent Requests from resolving redirects for you
   * @param verifySslCerts
   *   Set this to false to ignore problems with SSL certificates
   * @param check
   *   Throw an exception on a 4xx or 5xx response code. Defaults to `true`
   */
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

  /**
   * Performs a streaming HTTP request. Most of the parameters are the same as [[apply]], except
   * that the `data` parameter is missing, and no [[Response]] object is returned. Instead, the
   * caller gets access via three callbacks (described below). This provides a lower-level API than
   * [[Requester.apply]], allowing the caller fine-grained access to the upload/download streams so
   * they can direct them where-ever necessary without first aggregating all the data into memory.
   *
   * @param onHeadersReceived
   *   the second callback to be called, this provides access to the response's status code, status
   *   message, headers, and any previous re-direct responses. Returns a boolean, where `false` can
   *   be used to
   *
   * @return
   *   a `Writable` that can be used to write the output data to any destination
   */
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
  ): geny.Readable = {
    // Merge blob headers with provided headers
    val mergedHeaders = (sess.headers ++ blobHeaders ++ headers).toMap

    // Delegate to platform-specific HTTP backend
    HttpBackend.platform.execute(
      verb = upperCaseVerb,
      url = url,
      auth = auth,
      params = params,
      headers = mergedHeaders,
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
      sess = sess,
    )
  }

  /**
   * Overload of [[Requester.apply]] that takes a [[Request]] object as configuration
   */
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

  /**
   * Overload of [[Requester.stream]] that takes a [[Request]] object as configuration
   */
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
