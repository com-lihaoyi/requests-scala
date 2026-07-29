package requests

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._
import java.io.{ByteArrayInputStream, ByteArrayOutputStream, InputStream}

/**
 * Scala-Native implementation of Transport using libcurl.
 *
 * Platform-specific limitations (vs JVM implementation):
 * - Custom SSL/TLS contexts: not supported (curl uses system CA bundle)
 * - Client certificates: not supported
 * - Connection pooling / keep-alive: handled by curl, but may behave differently
 * - Thread pool: not applicable (Native is single-threaded by default)
 */
class CurlTransport extends Transport {

  def send(
    method: String,
    url: String,
    headers: Iterable[(String, String)],
    data: RequestBlob,
    connectTimeout: Int,
    readTimeout: Int,
    tlsContext: TlsContext,
    proxy: (String, Int),
    verifySslCerts: Boolean
  ): RawTransportResponse = Zone { implicit z =>

    val curl = Curl.curl_easy_init()
    if (curl == null) {
      throw new RequestsException("Failed to initialize libcurl")
    }

    try {
      // Buffer request body
      val requestBodyBuffer = new ByteArrayOutputStream()
      data.write(requestBodyBuffer)
      val requestBodyBytes = requestBodyBuffer.toByteArray

      // Response buffers (will be filled by curl callbacks)
      val responseBody = new ByteArrayOutputStream()
      val responseHeaders = new ByteArrayOutputStream()

      // Set URL
      setOpt(curl, Curl.CURLOPT_URL, toCString(url))

      // Set timeouts
      setOptLong(curl, Curl.CURLOPT_TIMEOUT_MS, readTimeout.toLong)
      setOptLong(curl, Curl.CURLOPT_CONNECTTIMEOUT_MS, connectTimeout.toLong)

      // Set method
      val upperMethod = method.toUpperCase
      if (upperMethod == "HEAD") {
        setOptLong(curl, Curl.CURLOPT_NOBODY, 1L)
      } else if (upperMethod != "GET" && upperMethod != "POST") {
        setOpt(curl, Curl.CURLOPT_CUSTOMREQUEST, toCString(upperMethod))
      }

      // Set request body for POST/PUT/PATCH
      if (requestBodyBytes.nonEmpty) {
        setOpt(curl, Curl.CURLOPT_POSTFIELDS, requestBodyBytes.atUnsafe(0))
        setOptLong(curl, Curl.CURLOPT_POSTFIELDSIZE, requestBodyBytes.length.toLong)
      }

      // Set SSL verification
      if (!verifySslCerts) {
        setOptLong(curl, Curl.CURLOPT_SSL_VERIFYPEER, 0L)
        setOptLong(curl, Curl.CURLOPT_SSL_VERIFYHOST, 0L)
      }

      // Set proxy
      if (proxy != null) {
        setOpt(curl, Curl.CURLOPT_PROXY, toCString(proxy._1))
        setOptLong(curl, Curl.CURLOPT_PROXYPORT, proxy._2.toLong)
      }

      // Set write callback for response body
      val writeCallback = new Curl.curl_write_callback {
        def apply(ptr: Ptr[Byte], size: CSize, nmemb: CSize, userdata: Ptr[Byte]): CSize = {
          val total = (size * nmemb).toInt
          if (total > 0) {
            val bytes = new Array[Byte](total)
            var i = 0
            while (i < total) {
              bytes(i) = !(ptr + i)
              i += 1
            }
            responseBody.write(bytes)
          }
          total.toULong
        }
      }
      setOpt(curl, Curl.CURLOPT_WRITEFUNCTION, writeCallback)

      // Set header callback
      val headerCallback = new Curl.curl_header_callback {
        def apply(ptr: Ptr[Byte], size: CSize, nmemb: CSize, userdata: Ptr[Byte]): CSize = {
          val total = (size * nmemb).toInt
          if (total > 0) {
            val bytes = new Array[Byte](total)
            var i = 0
            while (i < total) {
              bytes(i) = !(ptr + i)
              i += 1
            }
            responseHeaders.write(bytes)
          }
          total.toULong
        }
      }
      setOpt(curl, Curl.CURLOPT_HEADERFUNCTION, headerCallback)

      // Build and set custom headers
      val headerList = buildHeaderList(headers)
      if (headerList != null) {
        setOpt(curl, Curl.CURLOPT_HTTPHEADER, headerList)
      }

      // Execute request
      val result = Curl.curl_easy_perform(curl)

      // Clean up header list
      if (headerList != null) {
        Curl.curl_slist_free_all(headerList)
      }

      // Check for errors
      if (result != Curl.CURLE_OK) {
        val errorMsg = CurlError.message(result)
        result match {
          case Curl.CURLE_OPERATION_TIMEDOUT =>
            throw new TimeoutException(url, readTimeout, connectTimeout)
          case Curl.CURLE_COULDNT_RESOLVE_HOST =>
            throw new UnknownHostException(url, url)
          case Curl.CURLE_SSL_CONNECT_ERROR =>
            throw new InvalidCertException(url, new Exception(s"SSL error: $errorMsg"))
          case _ =>
            throw new RequestsException(s"curl error ($result): $errorMsg")
        }
      }

      // Get response code
      val responseCodePtr = stackalloc[CLong]()
      Curl.curl_easy_getinfo(curl, Curl.CURLINFO_RESPONSE_CODE, responseCodePtr)
      val responseCode = (!responseCodePtr).toInt

      // Parse response headers
      val headerLines = new String(responseHeaders.toByteArray, "UTF-8")
        .split("\r\n")
        .filter(_.nonEmpty)
        .filterNot(_.startsWith("HTTP/")) // Skip status line

      val parsedHeaders: Map[String, Seq[String]] = headerLines
        .map { line =>
          val idx = line.indexOf(':')
          if (idx > 0) {
            val key = line.substring(0, idx).trim.toLowerCase()
            val value = line.substring(idx + 1).trim
            key -> value
          } else {
            line.toLowerCase() -> ""
          }
        }
        .groupBy(_._1)
        .map { case (k, vs) => k -> vs.map(_._2).toSeq }

      RawTransportResponse(
        statusCode = responseCode,
        statusMessage = StatusMessages.byStatusCode.getOrElse(responseCode, ""),
        headers = parsedHeaders,
        body = new ByteArrayInputStream(responseBody.toByteArray),
        finalUrl = url
      )
    } finally {
      Curl.curl_easy_cleanup(curl)
    }
  }

  private def setOpt(curl: Ptr[Curl.CURL], option: Curl.CURLoption, parameter: CVarArg)(implicit z: Zone): Unit = {
    val code = Curl.curl_easy_setopt(curl, option, parameter)
    if (code != Curl.CURLE_OK) {
      throw new RequestsException(s"curl_easy_setopt failed: ${CurlError.message(code)}")
    }
  }

  private def setOptLong(curl: Ptr[Curl.CURL], option: Curl.CURLoption, value: Long)(implicit z: Zone): Unit = {
    val code = Curl.curl_easy_setopt(curl, option, value)
    if (code != Curl.CURLE_OK) {
      throw new RequestsException(s"curl_easy_setopt failed: ${CurlError.message(code)}")
    }
  }

  private def buildHeaderList(headers: Iterable[(String, String)])(implicit z: Zone): Ptr[CurlSlist] = {
    var list: Ptr[CurlSlist] = null
    headers.foreach { case (k, v) =>
      val header = s"$k: $v"
      list = Curl.curl_slist_append(list, toCString(header))
    }
    list
  }
}
