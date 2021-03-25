package requests

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, OutputStream}
import java.net.{HttpCookie, HttpURLConnection, InetSocketAddress}
import java.util.zip.{GZIPInputStream, InflaterInputStream}

import javax.net.ssl._

import collection.JavaConverters._
import scala.collection.mutable

trait BaseSession{
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
}

object BaseSession{
  val defaultHeaders = Map(
    "User-Agent" -> "requests-scala",
    "Accept-Encoding" -> "gzip, deflate",
    "Connection" -> "keep-alive",
    "Accept" -> "*/*"
  )
}
object Requester{
  val officialHttpMethods = Set("GET", "POST", "HEAD", "OPTIONS", "PUT", "DELETE", "TRACE")
  private lazy val methodField: java.lang.reflect.Field = {
    val m = classOf[HttpURLConnection].getDeclaredField("method")
    m.setAccessible(true)
    m
  }
}
case class Requester(verb: String,
                     sess: BaseSession){


  /**
    * Makes a single HTTP request, and returns a [[Response]] object. Requires
    * all uploaded request `data` to be provided up-front, and aggregates all
    * downloaded response `data` before returning it in the response. If you
    * need streaming access to the upload and download, use the [[Requester.stream]]
    * function instead.
    *
    * @param url The URL to which you want to make this HTTP request
    * @param auth HTTP authentication you want to use with this request; defaults to none
    * @param params URL params to pass to this request, for GETs and DELETEs
    * @param headers Custom headers to use, in addition to the defaults
    * @param data Body data to pass to this request, for POSTs and PUTs. Can be a
    *             Map[String, String] of form data, bulk data as a String or Array[Byte],
    *             or MultiPart form data.
    * @param readTimeout How long to wait for data to be read before timing out
    * @param connectTimeout How long to wait for a connection before timing out
    * @param proxy Host and port of a proxy you want to use
    * @param cert Client certificate configuration
    * @param sslContext Client sslContext configuration
    * @param cookies Custom cookies to send up with this request
    * @param maxRedirects How many redirects to automatically resolve; defaults to 5.
    *                     You can also set it to 0 to prevent Requests from resolving
    *                     redirects for you
    * @param verifySslCerts Set this to false to ignore problems with SSL certificates
    * @param check Throw an exception on a 4xx or 5xx response code. Defaults to `true`
    */
  def apply(url: String,
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
            chunkedUpload: Boolean = sess.chunkedUpload): Response = {
    val out = new ByteArrayOutputStream()

    var streamHeaders: StreamHeaders = null
    val w = stream(
      url, auth, params, data.headers, headers, data, readTimeout,
      connectTimeout, proxy, cert, sslContext, cookies, cookieValues, maxRedirects,
      verifySslCerts, autoDecompress, compress, keepAlive, check, chunkedUpload,
      onHeadersReceived = sh => streamHeaders = sh
    )

    w.writeBytesTo(out)

    Response(
      streamHeaders.url,
      streamHeaders.statusCode,
      streamHeaders.statusMessage,
      new geny.Bytes(out.toByteArray),
      streamHeaders.headers,
      streamHeaders.history
    )
  }

  /**
    * Performs a streaming HTTP request. Most of the parameters are the same as
    * [[apply]], except that the `data` parameter is missing, and no [[Response]]
    * object is returned. Instead, the caller gets access via three callbacks
    * (described below). This provides a lower-level API than [[Requester.apply]],
    * allowing the caller fine-grained access to the upload/download streams
    * so they can direct them where-ever necessary without first aggregating all
    * the data into memory.
    *
    * @param onHeadersReceived the second callback to be called, this provides
    *                          access to the response's status code, status
    *                          message, headers, and any previous re-direct
    *                          responses. Returns a boolean, where `false` can
    *                          be used to
    *
    * @return a `Writable` that can be used to write the output data to any
    *         destination
    */
  def stream(url: String,
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
             onHeadersReceived: StreamHeaders => Unit = null): geny.Readable = new geny.Readable {
    def readBytesThrough[T](f: java.io.InputStream => T): T = {

      val url0 = new java.net.URL(url)

      val url1 = if (params.nonEmpty) {
        val encodedParams = Util.urlEncode(params)
        val firstSep = if (url0.getQuery != null) "&" else "?"
        new java.net.URL(url + firstSep + encodedParams)
      } else url0

      var connection: HttpURLConnection = null

      try {

        val conn =
          if (proxy == null) url1.openConnection
          else {
            val (ip, port) = proxy
            val p = new java.net.Proxy(
              java.net.Proxy.Type.HTTP, new InetSocketAddress(ip, port)
            )
            url1.openConnection(p)
          }

        connection = conn match{
          case c: HttpsURLConnection =>
            if (cert != null) {
              c.setSSLSocketFactory(Util.clientCertSocketFactory(cert, verifySslCerts))
              if (!verifySslCerts) c.setHostnameVerifier(new HostnameVerifier { def verify(h: String, s: SSLSession) = true })
            } else if (sslContext != null) {
              c.setSSLSocketFactory(sslContext.getSocketFactory)
              if (!verifySslCerts) c.setHostnameVerifier(new HostnameVerifier { def verify(h: String, s: SSLSession) = true })
            } else if (!verifySslCerts) {
              c.setSSLSocketFactory(Util.noVerifySocketFactory)
              c.setHostnameVerifier(new HostnameVerifier { def verify(h: String, s: SSLSession) = true })
            }
            c
          case c: HttpURLConnection => c
        }

        connection.setInstanceFollowRedirects(false)
        val upperCaseVerb = verb.toUpperCase
        if (Requester.officialHttpMethods.contains(upperCaseVerb)) {
          connection.setRequestMethod(upperCaseVerb)
        } else {
          // HttpURLConnection enforces a list of official http METHODs, but not everyone abides by the spec
          // this hack allows us set an unofficial http method
          connection match {
            case cs: HttpsURLConnection =>
              cs.getClass.getDeclaredFields.find(_.getName == "delegate").foreach{ del =>
                del.setAccessible(true)
                Requester.methodField.set(del.get(cs), upperCaseVerb)
              }
            case c =>
              Requester.methodField.set(c, upperCaseVerb)
          }
        }

        for((k, v) <- blobHeaders) connection.setRequestProperty(k, v)

        for((k, v) <- sess.headers) connection.setRequestProperty(k, v)

        for((k, v) <- headers) connection.setRequestProperty(k, v)

        for((k, v) <- compress.headers) connection.setRequestProperty(k, v)

        connection.setReadTimeout(readTimeout)
        auth.header.foreach(connection.setRequestProperty("Authorization", _))
        connection.setConnectTimeout(connectTimeout)
        connection.setUseCaches(false)
        connection.setDoOutput(true)

        val sessionCookieValues = for{
          c <- (sess.cookies ++ cookies).valuesIterator
          if !c.hasExpired
          if c.getDomain == null || c.getDomain == url1.getHost
          if c.getPath == null || url1.getPath.startsWith(c.getPath)
        } yield (c.getName, c.getValue)

        val allCookies = sessionCookieValues ++ cookieValues
        if (allCookies.nonEmpty){
          connection.setRequestProperty(
            "Cookie",
            allCookies
              .map{case (k, v) => s"""$k="$v""""}
              .mkString("; ")
          )
        }
        if (verb.toUpperCase == "POST" || verb.toUpperCase == "PUT" || verb.toUpperCase == "PATCH" || verb.toUpperCase == "DELETE") {
          if (!chunkedUpload) {
            val bytes = new ByteArrayOutputStream()
            data.write(compress.wrap(bytes))
            val byteArray = bytes.toByteArray
            connection.setFixedLengthStreamingMode(byteArray.length)
            if (byteArray.nonEmpty) connection.getOutputStream.write(byteArray)
          } else {
            connection.setChunkedStreamingMode(0)
            data.write(compress.wrap(connection.getOutputStream))
          }
        }

        val (responseCode, responseMsg, headerFields) = try {(
          connection.getResponseCode,
          connection.getResponseMessage,
          connection.getHeaderFields.asScala
            .filter(_._1 != null)
            .map{case (k, v) => (k.toLowerCase(), v.asScala.toSeq)}.toMap
        )} catch{
          case e: java.net.SocketTimeoutException =>
            throw new TimeoutException(url, readTimeout, connectTimeout)
          case e: java.net.UnknownHostException =>
            throw new UnknownHostException(url, e.getMessage)
          case e: javax.net.ssl.SSLHandshakeException =>
            throw new InvalidCertException(url, e)
        }

        val deGzip = autoDecompress && headerFields.get("content-encoding").toSeq.flatten.exists(_.contains("gzip"))
        val deDeflate = autoDecompress && headerFields.get("content-encoding").toSeq.flatten.exists(_.contains("deflate"))

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

        if (responseCode.toString.startsWith("3") && maxRedirects > 0){
          val out = new ByteArrayOutputStream()
          Util.transferTo(connection.getInputStream, out)
          val bytes = out.toByteArray

          val current = Response(
            url,
            responseCode,
            responseMsg,
            new geny.Bytes(bytes),
            headerFields,
            redirectedFrom
          )
          persistCookies()
          val newUrl = current.headers("location").head
          stream(
            new java.net.URL(url1, newUrl).toString, auth, params, blobHeaders,
            headers, data, readTimeout, connectTimeout, proxy, cert, sslContext, cookies,
            cookieValues, maxRedirects - 1, verifySslCerts, autoDecompress,
            compress, keepAlive, check, chunkedUpload, Some(current),
            onHeadersReceived
          ).readBytesThrough(f)
        }else{
          persistCookies()
          val streamHeaders = StreamHeaders(
            url,
            responseCode,
            responseMsg,
            headerFields,
            redirectedFrom
          )
          if (onHeadersReceived != null) onHeadersReceived(streamHeaders)

          val stream =
            if (connection.getResponseCode.toString.startsWith("2")) connection.getInputStream
            else connection.getErrorStream

          def processWrappedStream[V](f: java.io.InputStream => V): V = {
            if (stream != null) {
              try f(
                if (deGzip) new GZIPInputStream(stream)
                else if (deDeflate) new InflaterInputStream(stream)
                else stream
              ) finally if (!keepAlive) stream.close()
            }else{
              f(new ByteArrayInputStream(Array()))
            }
          }

          if (streamHeaders.is2xx || !check) processWrappedStream(f)
          else {
            val errorOutput = new ByteArrayOutputStream()
            processWrappedStream(geny.Internal.transfer(_, errorOutput))
            throw new RequestFailedException(
              Response(
                streamHeaders.url,
                streamHeaders.statusCode,
                streamHeaders.statusMessage,
                new geny.Bytes(errorOutput.toByteArray),
                streamHeaders.headers,
                streamHeaders.history
              )
            )
          }
        }
      } finally if (!keepAlive && connection != null) {
        connection.disconnect()
      }
    }
  }

  /**
    * Overload of [[Requester.apply]] that takes a [[Request]] object as configuration
    */
  def apply(r: Request, data: RequestBlob, chunkedUpload: Boolean): Response = apply(
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
    chunkedUpload
  )

  /**
    * Overload of [[Requester.stream]] that takes a [[Request]] object as configuration
    */
  def stream(r: Request,
             data: RequestBlob,
             chunkedUpload: Boolean,
             onHeadersReceived: StreamHeaders => Unit): geny.Writable = stream(
    r.url,
    r.auth,
    r.params,
    Seq.empty[(String, String)],
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
    None,
    onHeadersReceived
  )
}
