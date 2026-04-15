package requests

import java.io._
import java.net.{HttpCookie, URI}
import java.util.zip.{GZIPInputStream, InflaterInputStream}

import scala.collection.JavaConverters._
import scala.collection.immutable.ListMap
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
  lazy val patch = Requester("PATCH", this)

  def send(method: String) = Requester(method, this)

  def close(): Unit = Platform.closeSession(this)
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

      val url0 = new URI(url)

      val url1 = if (params.nonEmpty) {
        val encodedParams = Util.urlEncode(params)
        val firstSep = if (url0.getRawQuery != null) "&" else "?"
        new URI(url + firstSep + encodedParams)
      } else url0

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
      val headersSeq = lastOfEachHeader.values.toSeq

      val requestBodyBuffer = new ByteArrayOutputStream()
      usingOutputStream(compress.wrap(requestBodyBuffer)) { os => data.write(os) }
      val requestBodyBytes = requestBodyBuffer.toByteArray

      val resp = Platform.send(
        method = upperCaseVerb,
        url = url1.toString,
        headers = headersSeq,
        body = requestBodyBytes,
        readTimeout = readTimeout,
        connectTimeout = connectTimeout,
        proxy = proxy,
        cert = cert,
        sslContext = sslContext,
        verifySslCerts = verifySslCerts,
      )

      val responseCode = resp.statusCode
      val headerFields = resp.headers

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
        if (sess.persistCookies) sess.cookies.synchronized {
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
        Util.transferTo(resp.body, out)

        val current = Response(
          url = url,
          statusCode = responseCode,
          statusMessage = StatusMessages.byStatusCode.getOrElse(responseCode, ""),
          data = new geny.Bytes(out.toByteArray),
          headers = headerFields,
          history = redirectedFrom,
        )
        persistCookies()
        val newUrl = current.headers("location").head
        stream(
          url = url1.resolve(newUrl).toString,
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

        val stream = resp.body

        def processWrappedStream[V](f: java.io.InputStream => V): V = {
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
