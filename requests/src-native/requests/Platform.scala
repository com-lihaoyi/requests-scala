package requests

import java.io._
import java.net.HttpCookie

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.scalanative.libc.stdlib
import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

import javax.net.ssl.SSLContext

private[requests] object Platform {

  private var globalInitialized = false

  private def ensureGlobalInit(): Unit = {
    if (!globalInitialized) {
      val code = libcurl.curl_global_init(0L)
      if (code != 0) {
        throw new RequestsException(s"curl_global_init failed with code $code")
      }
      globalInitialized = true
    }
  }

  def send(
      method: String,
      url: String,
      headers: Seq[(String, String)],
      body: Array[Byte],
      readTimeout: Int,
      connectTimeout: Int,
      proxy: (String, Int),
      cert: Cert,
      sslContext: SSLContext,
      verifySslCerts: Boolean,
  ): PlatformResponse = {
    ensureGlobalInit()

    val curl = libcurl.curl_easy_init()
    if (curl == null) {
      throw new RequestsException("Failed to initialize curl handle")
    }

    val bodyCollector = new ByteArrayOutputStream()
    val headerLines = mutable.ListBuffer.empty[String]
    var bodyPtr: Ptr[Byte] = null

    try {
      Zone { implicit z =>
        libcurl.curl_easy_setopt_string(curl, libcurl.CURLOPT_URL, toCString(url))
        libcurl.curl_easy_setopt_long(curl, libcurl.CURLOPT_FOLLOWLOCATION, 0L)
        libcurl.curl_easy_setopt_long(curl, libcurl.CURLOPT_TIMEOUT_MS, readTimeout.toLong)
        libcurl.curl_easy_setopt_long(
          curl,
          libcurl.CURLOPT_CONNECTTIMEOUT_MS,
          connectTimeout.toLong,
        )

        if (!verifySslCerts) {
          libcurl.curl_easy_setopt_long(curl, libcurl.CURLOPT_SSL_VERIFYPEER, 0L)
          libcurl.curl_easy_setopt_long(curl, libcurl.CURLOPT_SSL_VERIFYHOST, 0L)
        }

        if (proxy != null) {
          val proxyStr = s"${proxy._1}:${proxy._2}"
          libcurl.curl_easy_setopt_string(curl, libcurl.CURLOPT_PROXY, toCString(proxyStr))
        }

        method match {
          case "GET" =>
            libcurl.curl_easy_setopt_long(curl, libcurl.CURLOPT_HTTPGET, 1L)
          case "POST" =>
            libcurl.curl_easy_setopt_long(curl, libcurl.CURLOPT_POST, 1L)
          case "HEAD" =>
            libcurl.curl_easy_setopt_long(curl, libcurl.CURLOPT_NOBODY, 1L)
          case _ =>
            libcurl.curl_easy_setopt_string(
              curl,
              libcurl.CURLOPT_CUSTOMREQUEST,
              toCString(method),
            )
        }

        var slist: Ptr[Byte] = null
        try {
          for ((k, v) <- headers) {
            val headerStr = s"$k: $v"
            slist = libcurl.curl_slist_append(slist, toCString(headerStr))
          }
          if (slist != null) {
            libcurl.curl_easy_setopt_ptr(curl, libcurl.CURLOPT_HTTPHEADER, slist)
          }

          if (body.nonEmpty) {
            bodyPtr = stdlib.malloc(body.length.toCSize)
            var i = 0
            while (i < body.length) {
              !(bodyPtr + i) = body(i)
              i += 1
            }
            libcurl.curl_easy_setopt_ptr(curl, libcurl.CURLOPT_POSTFIELDS, bodyPtr)
            libcurl.curl_easy_setopt_long(
              curl,
              libcurl.CURLOPT_POSTFIELDSIZE,
              body.length.toLong,
            )
          }

          val writeCb: libcurl.CurlWriteCallback =
            (data: Ptr[Byte], size: CSize, nmemb: CSize, _: Ptr[Byte]) => {
              val total = (size * nmemb).toInt
              if (total > 0 && data != null) {
                val chunk = new Array[Byte](total)
                var i = 0
                while (i < total) {
                  chunk(i) = !(data + i)
                  i += 1
                }
                bodyCollector.write(chunk)
              }
              size * nmemb
            }
          libcurl.curl_easy_setopt_write_cb(curl, libcurl.CURLOPT_WRITEFUNCTION, writeCb)

          val headerCb: libcurl.CurlHeaderCallback =
            (data: Ptr[Byte], size: CSize, nmemb: CSize, _: Ptr[Byte]) => {
              val total = (size * nmemb).toInt
              if (total > 0 && data != null) {
                val bytes = new Array[Byte](total)
                var i = 0
                while (i < total) {
                  bytes(i) = !(data + i)
                  i += 1
                }
                val line = new String(bytes, "UTF-8").trim
                if (line.nonEmpty) {
                  headerLines += line
                }
              }
              size * nmemb
            }
          libcurl.curl_easy_setopt_header_cb(
            curl,
            libcurl.CURLOPT_HEADERFUNCTION,
            headerCb,
          )

          val code = libcurl.curl_easy_perform(curl)
          if (code != libcurl.CURLE_OK) {
            val errMsg = fromCString(libcurl.curl_easy_strerror(code))
            code match {
              case libcurl.CURLE_OPERATION_TIMEDOUT =>
                throw new TimeoutException(url, readTimeout, connectTimeout)
              case libcurl.CURLE_SSL_CONNECT_ERROR |
                  libcurl.CURLE_SSL_CERTPROBLEM |
                  libcurl.CURLE_SSL_CIPHER |
                  libcurl.CURLE_PEER_FAILED_VERIFICATION =>
                throw new InvalidCertException(url, new Exception(errMsg))
              case libcurl.CURLE_COULDNT_RESOLVE_HOST =>
                throw new UnknownHostException(url, errMsg)
              case libcurl.CURLE_COULDNT_CONNECT =>
                val host = try new java.net.URI(url).getHost catch {
                  case _: Exception => url
                }
                throw new UnknownHostException(url, host)
              case _ =>
                throw new RequestsException(s"curl error: $errMsg (code=$code)")
            }
          }

          val responseCodePtr = alloc[CLong]()
          !responseCodePtr = 0L
          libcurl.curl_easy_getinfo_long(
            curl,
            libcurl.CURLINFO_RESPONSE_CODE.toUInt,
            responseCodePtr,
          )
          val statusCode = (!responseCodePtr).toInt

          val headerMap = parseHeaders(headerLines)

          PlatformResponse(
            statusCode = statusCode,
            headers = headerMap,
            body = new ByteArrayInputStream(bodyCollector.toByteArray),
          )
        } finally {
          if (slist != null) libcurl.curl_slist_free_all(slist)
        }
      }
    } finally {
      if (bodyPtr != null) stdlib.free(bodyPtr)
      libcurl.curl_easy_cleanup(curl)
    }
  }

  def closeSession(sess: BaseSession): Unit = ()

  private def parseHeaders(
      lines: mutable.ListBuffer[String],
  ): Map[String, Seq[String]] = {
    val map = mutable.LinkedHashMap.empty[String, Seq[String]]
    for (line <- lines) {
      if (!line.startsWith("HTTP/") && line.contains(":")) {
        val colonIdx = line.indexOf(':')
        val key = line.substring(0, colonIdx).trim.toLowerCase
        val value = line.substring(colonIdx + 1).trim
        map(key) = map.getOrElse(key, Seq.empty) :+ value
      }
    }
    map.toMap
  }
}
