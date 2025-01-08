package requests

import java.io._
import java.net.http._
import java.net.{UnknownHostException => _, _}
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.Flow
import java.util.function.Supplier
import java.util.zip.{GZIPInputStream, InflaterInputStream}

import scala.collection.JavaConverters._
import scala.collection.immutable.ListMap
import scala.collection.mutable
import scala.concurrent.{ExecutionException, Future}

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
  private lazy val methodField: java.lang.reflect.Field = {
    val m = classOf[HttpURLConnection].getDeclaredField("method")
    m.setAccessible(true)
    m
  }
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
  ): geny.Readable = new geny.Readable {
    def readBytesThrough[T](f: java.io.InputStream => T): T = {

      val url0 = new java.net.URL(url)

      val url1 = if (params.nonEmpty) {
        val encodedParams = Util.urlEncode(params)
        val firstSep = if (url0.getQuery != null) "&" else "?"
        new java.net.URL(url + firstSep + encodedParams)
      } else url0

      val httpClient: HttpClient =
        HttpClient
          .newBuilder()
          .followRedirects(HttpClient.Redirect.NEVER)
          .proxy(proxy match {
            case null       => ProxySelector.getDefault
            case (ip, port) => ProxySelector.of(new InetSocketAddress(ip, port))
          })
          .sslContext(
            if (cert != null)
              Util.clientCertSSLContext(cert, verifySslCerts)
            else if (sslContext != null)
              sslContext
            else if (!verifySslCerts)
              Util.noVerifySSLContext
            else
              SSLContext.getDefault,
          )
          .connectTimeout(Duration.ofMillis(connectTimeout))
          .build()

      val sessionCookieValues = for {
        c <- (sess.cookies ++ cookies).valuesIterator
        if !c.hasExpired
        if c.getDomain == null || c.getDomain == url1.getHost
        if c.getPath == null || url1.getPath.startsWith(c.getPath)
      } yield (c.getName, c.getValue)

      val allCookies = sessionCookieValues ++ cookieValues

      val (contentLengthHeader, otherBlobHeaders) =
        blobHeaders.partition(_._1.equalsIgnoreCase("Content-Length"))

      val allHeaders =
        otherBlobHeaders ++
          sess.headers ++
          headers ++
          compress.headers ++
          auth.header.map("Authorization" -> _) ++
          (if (allCookies.isEmpty) None
           else
             Some(
               "Cookie" -> allCookies
                 .map { case (k, v) => s"""$k="$v"""" }
                 .mkString("; "),
             ))
      val lastOfEachHeader =
        allHeaders.foldLeft(ListMap.empty[String, (String, String)]) {
          case (acc, (k, v)) =>
            acc.updated(k.toLowerCase, k -> v)
        }
      val headersKeyValueAlternating = lastOfEachHeader.values.toList.flatMap {
        case (k, v) => Seq(k, v)
      }

      val requestBodyInputStream = new PipedInputStream()
      val requestBodyOutputStream = new PipedOutputStream(requestBodyInputStream)

      val bodyPublisher: HttpRequest.BodyPublisher =
        HttpRequest.BodyPublishers.ofInputStream(new Supplier[InputStream] {
          override def get() = requestBodyInputStream
        })

      val requestBuilder =
        HttpRequest
          .newBuilder()
          .uri(url1.toURI)
          .timeout(Duration.ofMillis(readTimeout))
          .headers(headersKeyValueAlternating: _*)
          .method(
            upperCaseVerb,
            (contentLengthHeader.headOption.map(_._2), compress) match {
              case (Some("0"), _) => HttpRequest.BodyPublishers.noBody()
              case (Some(n), Compress.None) =>
                HttpRequest.BodyPublishers.fromPublisher(bodyPublisher, n.toInt)
              case _ => bodyPublisher
            },
          )

      val fut = httpClient.sendAsync(
        requestBuilder.build(),
        HttpResponse.BodyHandlers.ofInputStream(),
      )

      usingOutputStream(compress.wrap(requestBodyOutputStream)) { os => data.write(os) }

      val response =
        try
          fut.get()
        catch {
          case e: ExecutionException =>
            throw e.getCause match {
              case e: javax.net.ssl.SSLHandshakeException => new InvalidCertException(url, e)
              case _: HttpConnectTimeoutException | _: HttpTimeoutException =>
                new TimeoutException(url, readTimeout, connectTimeout)
              case e: java.net.UnknownHostException => new UnknownHostException(url, e.getMessage)
              case e: java.net.ConnectException     => new UnknownHostException(url, e.getMessage)
              case e                                => new RequestsException(e.getMessage, Some(e))
            }
        }

      val responseCode = response.statusCode()
      val headerFields =
        response
          .headers()
          .map
          .asScala
          .filter(_._1 != null)
          .map { case (k, v) => (k.toLowerCase(), v.asScala.toList) }
          .toMap

      val deGzip = autoDecompress && headerFields
        .get("content-encoding")
        .toSeq
        .flatten
        .exists(_.contains("gzip"))
      val deDeflate =
        autoDecompress && headerFields
          .get("content-encoding")
          .toSeq
          .flatten
          .exists(_.contains("deflate"))
      def persistCookies() = {
        if (sess.persistCookies) {
          headerFields
            .get("set-cookie")
            .iterator
            .flatten
            .flatMap(HttpCookie.parse(_).asScala)
            .foreach(c => sess.cookies(c.getName) = c)
        }
      }

      if (
        responseCode.toString.startsWith("3") &&
        responseCode.toString != "304" &&
        maxRedirects > 0
      ) {
        val out = new ByteArrayOutputStream()
        Util.transferTo(response.body, out)
        val bytes = out.toByteArray

        val current = Response(
          url = url,
          statusCode = responseCode,
          statusMessage = StatusMessages.byStatusCode.getOrElse(responseCode, ""),
          data = new geny.Bytes(bytes),
          headers = headerFields,
          history = redirectedFrom,
        )
        persistCookies()
        val newUrl = current.headers("location").head
        stream(
          url = new URL(url1, newUrl).toString,
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
          redirectedFrom = Some(current),
          onHeadersReceived = onHeadersReceived,
        ).readBytesThrough(f)
      } else {
        persistCookies()
        val streamHeaders = StreamHeaders(
          url = url,
          statusCode = responseCode,
          statusMessage = StatusMessages.byStatusCode.getOrElse(responseCode, ""),
          headers = headerFields,
          history = redirectedFrom,
        )
        if (onHeadersReceived != null) onHeadersReceived(streamHeaders)

        val stream = response.body()

        def processWrappedStream[V](f: java.io.InputStream => V): V = {
          // The HEAD method is identical to GET except that the server
          // MUST NOT return a message-body in the response.
          // https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html section 9.4
          if (upperCaseVerb == "HEAD") f(new ByteArrayInputStream(Array()))
          else if (stream != null) {
            try
              f(
                if (deGzip) new GZIPInputStream(stream)
                else if (deDeflate) new InflaterInputStream(stream)
                else stream,
              )
            finally if (!keepAlive) stream.close()
          } else {
            f(new ByteArrayInputStream(Array()))
          }
        }

        if (streamHeaders.statusCode == 304 || streamHeaders.is2xx || !check)
          processWrappedStream(f)
        else {
          val errorOutput = new ByteArrayOutputStream()
          processWrappedStream(geny.Internal.transfer(_, errorOutput))
          throw new RequestFailedException(
            Response(
              url = streamHeaders.url,
              statusCode = streamHeaders.statusCode,
              statusMessage = streamHeaders.statusMessage,
              data = new geny.Bytes(errorOutput.toByteArray),
              headers = streamHeaders.headers,
              history = streamHeaders.history,
            ),
          )
        }
      }
    }
  }

  private def usingOutputStream[T](os: OutputStream)(
      fn: OutputStream => T,
  ): Unit =
    try fn(os)
    finally os.close()

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
