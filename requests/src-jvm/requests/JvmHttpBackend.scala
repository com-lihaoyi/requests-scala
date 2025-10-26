package requests

import java.io._
import java.net.http._
import java.net._
import java.nio.ByteBuffer
import java.time.Duration
import java.util.concurrent.Flow
import java.util.function.Supplier
import java.util.zip.{GZIPInputStream, InflaterInputStream}
import javax.net.ssl.SSLContext

import scala.collection.JavaConverters._
import scala.collection.immutable.ListMap
import scala.concurrent.ExecutionException

/**
 * JVM implementation of HttpBackend using java.net.http.HttpClient (Java 11+).
 */
private[requests] class JvmHttpBackend extends HttpBackend {
  
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
  ): geny.Readable = new geny.Readable {
    def readBytesThrough[T](f: java.io.InputStream => T): T = {
      val upperCaseVerb = verb.toUpperCase
      val blobHeaders = data.headers

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
              case e: java.net.UnknownHostException => new requests.UnknownHostException(url, e.getMessage)
              case e: java.net.ConnectException     => new requests.UnknownHostException(url, e.getMessage)
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
        HttpBackend.platform.execute(
          verb = verb,
          url = new URL(url1, newUrl).toString,
          auth = auth,
          params = params,
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
          sess = sess,
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

  private def usingOutputStream[T](os: OutputStream)(fn: OutputStream => T): Unit =
    try fn(os)
    finally os.close()
}

/**
 * JVM-specific PlatformHttpBackend implementation.
 */
private[requests] object PlatformHttpBackend {
  val instance: HttpBackend = new JvmHttpBackend()
}
