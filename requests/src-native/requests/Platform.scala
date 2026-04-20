package requests

import java.io._
import java.net.HttpCookie
import java.net.URL
import javax.net.ssl.SSLContext
import scala.collection.mutable
import scala.collection.immutable.ListMap
import scala.scalanative.unsafe._
import scala.scalanative.libc._
import scala.collection.JavaConverters._

private[requests] object Platform {
  import CurlApi._
  import CurlConstants._

  type Cookie = HttpCookie
  type SslContext = Any

  private[this] var globalResponseHeaders: mutable.Buffer[String] = _
  private[this] var globalResponseBody: ByteArrayOutputStream = _

  def makeRequest[T](
      sess: BaseSession,
      url: String,
      verb: String,
      auth: RequestAuth,
      params: Iterable[(String, String)],
      blobHeaders: Iterable[(String, String)],
      headers: Iterable[(String, String)],
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
      f: java.io.InputStream => T,
      streamRecurse: (String, Option[Response]) => T,
  ): T = Zone { implicit z =>
    val curl = curl_easy_init()
    if (curl == null) throw new RequestsException("Could not initialize curl")

    val url0 = new URL(url)
    val url1 = if (params.nonEmpty) {
      val encodedParams = Util.urlEncode(params)
      val firstSep = if (url0.getQuery != null) "&" else "?"
      new URL(url + firstSep + encodedParams)
    } else url0

    curl_easy_setopt_str(curl, CURLOPT_URL, toCString(url1.toString))
    curl_easy_setopt_str(curl, CURLOPT_CUSTOMREQUEST, toCString(verb))
    curl_easy_setopt_long(curl, CURLOPT_TIMEOUT_MS, readTimeout.toLong)
    curl_easy_setopt_long(curl, CURLOPT_CONNECTTIMEOUT_MS, connectTimeout.toLong)
    curl_easy_setopt_long(curl, CURLOPT_FOLLOWLOCATION, 0L)
    curl_easy_setopt_long(curl, CURLOPT_NOSIGNAL, 1L)

    if (!verifySslCerts) {
      curl_easy_setopt_long(curl, CURLOPT_SSL_VERIFYPEER, 0L)
      curl_easy_setopt_long(curl, CURLOPT_SSL_VERIFYHOST, 0L)
    }

    if (proxy != null) {
      curl_easy_setopt_str(curl, CURLOPT_PROXY, toCString(s"${proxy._1}:${proxy._2}"))
    }

    // Set headers
    var headerList: curl_slist = null
    
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

    for ((_, (k, v)) <- lastOfEachHeader) {
      headerList = curl_slist_append(headerList, toCString(s"$k: $v"))
    }
    curl_easy_setopt_ptr(curl, CURLOPT_HTTPHEADER, headerList)

    // Body
    val requestBodyBuffer = new ByteArrayOutputStream()
    try {
      val wrapped = compress.wrap(requestBodyBuffer)
      data.write(wrapped)
      wrapped.close()
    } finally {
      requestBodyBuffer.close()
    }
    val requestBodyBytes = requestBodyBuffer.toByteArray

    if (requestBodyBytes.nonEmpty) {
      if (verb == "POST") curl_easy_setopt_long(curl, CURLOPT_POST, 1L)
      else if (verb == "PUT") curl_easy_setopt_long(curl, CURLOPT_UPLOAD, 1L)

      curl_easy_setopt_ptr(curl, CURLOPT_POSTFIELDS, requestBodyBytes.at(0))
      curl_easy_setopt_long(curl, CURLOPT_INFILESIZE_LARGE, requestBodyBytes.length.toLong)
    }

    // Capture response using global state (Single-threaded for now)
    globalResponseHeaders = mutable.Buffer.empty[String]
    globalResponseBody = new ByteArrayOutputStream()

    val headerCallback = CFuncPtr4.fromScalaFunction { (ptr: Ptr[Byte], size: CSize, nmemb: CSize, userdata: Ptr[Byte]) =>
      val totalSize = (size * nmemb).toInt
      val arr = new Array[Byte](totalSize)
      var i = 0
      while (i < totalSize) {
        arr(i) = ptr(i)
        i += 1
      }
      val header = new String(arr)
      globalResponseHeaders.append(header)
      (size * nmemb)
    }

    val writeCallback = CFuncPtr4.fromScalaFunction { (ptr: Ptr[Byte], size: CSize, nmemb: CSize, userdata: Ptr[Byte]) =>
      val totalSize = (size * nmemb).toInt
      val arr = new Array[Byte](totalSize)
      var i = 0
      while (i < totalSize) {
        arr(i) = ptr(i)
        i += 1
      }
      globalResponseBody.write(arr)
      (size * nmemb)
    }

    curl_easy_setopt_func(curl, CURLOPT_HEADERFUNCTION, headerCallback)
    curl_easy_setopt_func(curl, CURLOPT_WRITEFUNCTION, writeCallback)

    val res = curl_easy_perform(curl)

    if (res != CURLE_OK) {
      val error = fromCString(curl_easy_strerror(res))
      curl_easy_cleanup(curl)
      if (headerList != null) curl_slist_free_all(headerList)
      throw new RequestsException(s"Curl request failed: $error")
    }

    val responseCodePtr = alloc[Long]()
    curl_easy_getinfo(curl, CURLINFO_RESPONSE_CODE, responseCodePtr)
    val responseCode = (!responseCodePtr).toInt

    val headerMap = globalResponseHeaders
      .flatMap { h =>
        val split = h.split(":", 2)
        if (split.length == 2) Some(split(0).trim.toLowerCase -> split(1).trim)
        else None
      }
      .groupBy(_._1)
      .mapValues(_.map(_._2).toList)
      .toMap

    val deGzip = autoDecompress && headerMap.get("content-encoding").toSeq.flatten.exists(_.contains("gzip"))
    val deDeflate = autoDecompress && headerMap.get("content-encoding").toSeq.flatten.exists(_.contains("deflate"))

    def persistCookies() = {
      if (sess.persistCookies) {
        headerMap
          .get("set-cookie")
          .iterator
          .flatten
          .flatMap(c => HttpCookie.parse(c).asScala)
          .foreach(c => sess.cookies(c.getName) = c)
      }
    }

    try {
      if (
        responseCode.toString.startsWith("3") &&
        responseCode.toString != "304" &&
        maxRedirects > 0
      ) {
        val current = Response(
          url = url,
          statusCode = responseCode,
          statusMessage = StatusMessages.byStatusCode.getOrElse(responseCode, ""),
          data = new geny.Bytes(globalResponseBody.toByteArray),
          headers = headerMap,
          history = redirectedFrom,
        )
        persistCookies()
        val newUrl = headerMap("location").head
        streamRecurse(new URL(url1, newUrl).toString, Some(current))
      } else {
        persistCookies()
        val streamHeaders = StreamHeaders(
          url = url,
          statusCode = responseCode,
          statusMessage = StatusMessages.byStatusCode.getOrElse(responseCode, ""),
          headers = headerMap,
          history = redirectedFrom,
        )
        if (onHeadersReceived != null) onHeadersReceived(streamHeaders)

        def processWrappedStream[V](f: java.io.InputStream => V): V = {
          val is = new ByteArrayInputStream(globalResponseBody.toByteArray)
          if (verb.toUpperCase == "HEAD") f(new ByteArrayInputStream(Array()))
          else {
            val wrapped = if (deGzip) new java.util.zip.GZIPInputStream(is)
            else if (deDeflate) new java.util.zip.InflaterInputStream(is)
            else is
            try f(wrapped) finally wrapped.close()
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
    } finally {
      curl_easy_cleanup(curl)
      if (headerList != null) curl_slist_free_all(headerList)
    }
  }

  def createExecutor(): java.util.concurrent.ExecutorService = null
  def buildHttpClient(
    proxy: (String, Int),
    cert: Cert,
    sslContext: SSLContext,
    verifySslCerts: Boolean,
    connectTimeout: Int,
    executor: java.util.concurrent.ExecutorService
  ): Any = null
  def closeHttpClient(httpClient: Any) = ()
}
