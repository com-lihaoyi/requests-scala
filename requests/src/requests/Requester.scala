package requests

import java.io.{ByteArrayOutputStream, OutputStream}
import java.net.{HttpCookie, HttpURLConnection, InetSocketAddress}
import java.util.zip.{GZIPInputStream, InflaterInputStream}

import geny.Writable
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
  def maxRedirects: Int
  def persistCookies: Boolean
  def verifySslCerts: Boolean
  def autoDecompress: Boolean
  def compress: Compress
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
            cookies: Map[String, HttpCookie] = Map(),
            cookieValues: Map[String, String] = Map(),
            maxRedirects: Int = sess.maxRedirects,
            verifySslCerts: Boolean = sess.verifySslCerts,
            autoDecompress: Boolean = sess.autoDecompress,
            compress: Compress = sess.compress,
            keepAlive: Boolean = true,
            check: Boolean = true,
            inMemory: java.lang.Boolean = null): Response = {
    val out = new ByteArrayOutputStream()

    var streamHeaders: StreamHeaders = null
    val w = stream(
      url, auth, params, data.headers, headers, data, readTimeout,
      connectTimeout, proxy, cookies, cookieValues, maxRedirects,
      verifySslCerts, autoDecompress, compress, keepAlive, check, inMemory,
      onHeadersReceived = sh => streamHeaders = sh
    )

    w.writeBytesTo(out)

    Response(
      streamHeaders.url,
      streamHeaders.statusCode,
      streamHeaders.statusMessage,
      streamHeaders.headers,
      new geny.Bytes(out.toByteArray),
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
             cookies: Map[String, HttpCookie] = Map(),
             cookieValues: Map[String, String] = Map(),
             maxRedirects: Int = sess.maxRedirects,
             verifySslCerts: Boolean = sess.verifySslCerts,
             autoDecompress: Boolean = sess.autoDecompress,
             compress: Compress = sess.compress,
             keepAlive: Boolean = true,
             check: Boolean = true,
             inMemory: java.lang.Boolean = null,
             redirectedFrom: Option[Response] = None,
             onHeadersReceived: StreamHeaders => Unit = null): geny.Writable = new Writable {
    def writeBytesTo(out: OutputStream): Unit = {

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
            if (!verifySslCerts) {
              c.setSSLSocketFactory(Util.noVerifySocketFactory)
              c.setHostnameVerifier(
                new javax.net.ssl.HostnameVerifier {
                  override def verify(s: String, sslSession: SSLSession) = true
                }
              )
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
              .map{case (k, v) => k + "=" + v}
              .mkString("; ")
          )
        }
        val totalSize = data.length.getOrElse(-1L)
        if (data != RequestBlob.EmptyRequestBlob) {
          if (Option(inMemory).fold(data.inMemory)(_.booleanValue()) && compress != Compress.None){
            // For in-memory data inputs we want to compress, we buffer the
            // compressed output in-memory as well before uploading it. This lets
            // us avoid using chunked transfer encoding, for data which we already
            // had in memory and can probably pay the cost of buffering.
            //
            // For non-in-memory data inputs, we were already probably going to use
            // chunked transfer encoding anyway, so we just use that and can skip
            // the buffering.
            //
            // The only exception is known-size on-disk files, which uncompressed
            // get uploaded via content-length upload but compressed used chunked
            // transfer encoding. There isn't really any good way around this,
            // short of pre-compressing the file on disk, if we assume some files
            // are too big to fit in memory
            val bytes = new ByteArrayOutputStream()
            data.write(compress.wrap(bytes))
            val byteArray = bytes.toByteArray
            connection.setFixedLengthStreamingMode(byteArray.length)
            connection.getOutputStream.write(byteArray)
          }else{

            if (totalSize >= 0) connection.setFixedLengthStreamingMode(totalSize)
            else if (totalSize < 0) connection.setChunkedStreamingMode(0)

            data.write(compress.wrap(connection.getOutputStream))
          }
        }else if(verb.toUpperCase == "POST" || verb.toUpperCase == "PUT"){
            require(totalSize <= 0, "totalSize should not be greater than zero if onUpload is null")
            connection.setFixedLengthStreamingMode(0)
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
            headerFields,
            new geny.Bytes(bytes),
            redirectedFrom
          )
          persistCookies()
          val newUrl = current.headers("location").head
          stream(
            new java.net.URL(url1, newUrl).toString, auth, params, blobHeaders,
            headers, data, readTimeout, connectTimeout, proxy, cookies,
            cookieValues, maxRedirects - 1, verifySslCerts, autoDecompress,
            compress, keepAlive, check, inMemory, Some(current),
            onHeadersReceived
          ).writeBytesTo(out)
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

          def streamTo(target: OutputStream) = {
            if (stream != null) {
              try geny.Internal.transfer(
                if (deGzip) new GZIPInputStream(stream)
                else if (deDeflate) new InflaterInputStream(stream)
                else stream,
                target
              ) finally if (!keepAlive) stream.close()
            }
          }
          if (streamHeaders.is2xx || !check) streamTo(out)
          else {
            val errorOutput = new ByteArrayOutputStream()
            streamTo(errorOutput)
            throw new RequestFailedException(
              Response(
                streamHeaders.url,
                streamHeaders.statusCode,
                streamHeaders.statusMessage,
                streamHeaders.headers,
                new geny.Bytes(errorOutput.toByteArray),
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
  def apply(r: Request, data: RequestBlob, inMemory: java.lang.Boolean): Response = apply(
    r.url,
    r.auth,
    r.params,
    r.headers,
    data,
    r.readTimeout,
    r.connectTimeout,
    r.proxy,
    r.cookies,
    r.cookieValues,
    r.maxRedirects,
    r.verifySslCerts,
    r.autoDecompress,
    r.compress,
    r.keepAlive,
    r.check,
    inMemory
  )

  /**
    * Overload of [[Requester.stream]] that takes a [[Request]] object as configuration
    */
  def stream(r: Request,
             data: RequestBlob,
             inMemory: java.lang.Boolean,
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
    r.cookies,
    r.cookieValues,
    r.maxRedirects,
    r.verifySslCerts,
    r.autoDecompress,
    r.compress,
    r.keepAlive,
    r.check,
    inMemory,
    None,
    onHeadersReceived
  )
}
