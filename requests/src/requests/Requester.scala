package requests

import java.io._
import java.net.URL
import java.util.zip.{GZIPInputStream, InflaterInputStream}

import scala.collection.immutable.ListMap
import scala.collection.mutable

trait BaseSession extends AutoCloseable {
  def headers: Map[String, String]
  def cookies: mutable.Map[String, BaseCookie]
  def readTimeout: Int
  def connectTimeout: Int
  def auth: RequestAuth
  def proxy: (String, Int)
  def cert: Cert
  def sslContext: Any
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
  def transport: Transport
  def close(): Unit = transport.close()
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
      url: String, auth: RequestAuth = sess.auth,
      params: Iterable[(String, String)] = Nil,
      headers: Iterable[(String, String)] = Nil,
      data: RequestBlob = RequestBlob.EmptyRequestBlob,
      readTimeout: Int = sess.readTimeout,
      connectTimeout: Int = sess.connectTimeout,
      proxy: (String, Int) = sess.proxy,
      cert: Cert = sess.cert,
      sslContext: Any = sess.sslContext,
      cookies: Map[String, BaseCookie] = Map(),
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
    var sh: StreamHeaders = null
    val w = stream(url = url, auth = auth, params = params,
      blobHeaders = data.headers, headers = headers, data = data,
      readTimeout = readTimeout, connectTimeout = connectTimeout,
      proxy = proxy, cert = cert, sslContext = sslContext,
      cookies = cookies, cookieValues = cookieValues,
      maxRedirects = maxRedirects, verifySslCerts = verifySslCerts,
      autoDecompress = autoDecompress, compress = compress,
      keepAlive = keepAlive, check = check, chunkedUpload = chunkedUpload,
      onHeadersReceived = h => sh = h)
    w.writeBytesTo(out)
    Response(url = sh.url, statusCode = sh.statusCode,
      statusMessage = sh.statusMessage, data = new geny.Bytes(out.toByteArray),
      headers = sh.headers, history = sh.history)
  }

  def stream(
      url: String, auth: RequestAuth = sess.auth,
      params: Iterable[(String, String)] = Nil,
      blobHeaders: Iterable[(String, String)] = Nil,
      headers: Iterable[(String, String)] = Nil,
      data: RequestBlob = RequestBlob.EmptyRequestBlob,
      readTimeout: Int = sess.readTimeout,
      connectTimeout: Int = sess.connectTimeout,
      proxy: (String, Int) = sess.proxy,
      cert: Cert = sess.cert,
      sslContext: Any = sess.sslContext,
      cookies: Map[String, BaseCookie] = Map(),
      cookieValues: Map[String, String] = Map(),
      maxRedirects: Int = sess.maxRedirects,
      verifySslCerts: Boolean = sess.verifySslCerts,
      autoDecompress: Boolean = sess.autoDecompress,
      compress: Compress = sess.compress,
      keepAlive: Boolean = true, check: Boolean = true,
      chunkedUpload: Boolean = false,
      redirectedFrom: Option[Response] = None,
      onHeadersReceived: StreamHeaders => Unit = null,
  ): geny.Readable = new geny.Readable {
    def readBytesThrough[T](f: java.io.InputStream => T): T = {
      val url0 = new URL(url)
      val url1 = if (params.nonEmpty) {
        val ep = Util.urlEncode(params)
        new URL(url + (if (url0.getQuery != null) "&" else "?") + ep)
      } else url0

      val useShared =
        (proxy == sess.proxy) && (cert == sess.cert) &&
        (ssContextRefEq(sslContext, sess.sslContext)) &&
        (verifySslCerts == sess.verifySslCerts) &&
        (connectTimeout == sess.connectTimeout)
      val t: Transport = if (useShared) sess.transport else TransportFactory.create()

      try {
        val sessionCookieValues = for {
          c <- (sess.cookies ++ cookies).valuesIterator
          if !c.hasExpired
          if c.getDomain.isEmpty || c.getDomain == url1.getHost
          if c.getPath.isEmpty || url1.getPath.startsWith(c.getPath)
        } yield (c.getName, c.getValue)
        val allCookies = sessionCookieValues ++ cookieValues

        val allHeaders =
          blobHeaders.filterNot(_._1.equalsIgnoreCase("Content-Length")) ++
          sess.headers ++ headers ++ compress.headers ++
          auth.header.map("Authorization" -> _) ++
          (if (allCookies.isEmpty) None
           else Some("Cookie" -> allCookies.map { case (k, v) => k + "=" + "\"" + v + "\"" }.mkString("; ")))

        val lastOfEachHeader = allHeaders.foldLeft(
          ListMap.empty[String, (String, String)]
        ) { case (acc, (k, v)) => acc.updated(k.toLowerCase, k -> v) }

        val rawResp = try {
          t.send(method = upperCaseVerb, url = url1.toString,
            headers = lastOfEachHeader.values.toList, data = data,
            connectTimeout = connectTimeout, readTimeout = readTimeout,
            sslContext = sslContext, cert = cert,
            proxy = proxy, verifySslCerts = verifySslCerts)
        } catch {
          case e: RequestsException => throw e
          case e: Throwable => throw new RequestsException(e.getMessage, Some(e))
        }

        val code = rawResp.statusCode
        val hdrFields = rawResp.headers
        val deGzip = autoDecompress && hdrFields.get("content-encoding").toSeq.flatten.exists(_.contains("gzip"))
        val deDeflate = autoDecompress && hdrFields.get("content-encoding").toSeq.flatten.exists(_.contains("deflate"))

        def persistC(): Unit = if (sess.persistCookies) sess.cookies.synchronized {
          hdrFields.get("set-cookie").iterator.flatten
            .flatMap(h => BaseCookie.parse(h))
            .foreach(c => sess.cookies(c.getName) = c)
        }

        if (code.toString.startsWith("3") && code.toString != "304" && maxRedirects > 0) {
          val out = new ByteArrayOutputStream()
          Util.transferTo(rawResp.body, out)
          val current = Response(url = url, statusCode = code,
            statusMessage = StatusMessages.byStatusCode.getOrElse(code, ""),
            data = new geny.Bytes(out.toByteArray),
            headers = hdrFields, history = redirectedFrom)
          persistC()
          val newUrl = current.headers("location").head
          stream(url = new URL(url1, newUrl).toString, auth = auth,
            params = params, blobHeaders = blobHeaders, headers = headers,
            data = data, readTimeout = readTimeout, connectTimeout = connectTimeout,
            proxy = proxy, cert = cert, sslContext = sslContext,
            cookies = cookies, cookieValues = cookieValues,
            maxRedirects = maxRedirects - 1, verifySslCerts = verifySslCerts,
            autoDecompress = autoDecompress, compress = compress,
            keepAlive = keepAlive, check = check, chunkedUpload = chunkedUpload,
            redirectedFrom = Some(current), onHeadersReceived = onHeadersReceived
          ).readBytesThrough(f)
        } else {
          persistC()
          val sHeaders = StreamHeaders(url = url, statusCode = code,
            statusMessage = StatusMessages.byStatusCode.getOrElse(code, ""),
            headers = hdrFields, history = redirectedFrom)
          if (onHeadersReceived != null) onHeadersReceived(sHeaders)

          def process[V](g: java.io.InputStream => V): V = {
            if (upperCaseVerb == "HEAD") g(new ByteArrayInputStream(Array()))
            else if (rawResp.body != null) {
              try g(if (deGzip) new GZIPInputStream(rawResp.body)
                    else if (deDeflate) new InflaterInputStream(rawResp.body)
                    else rawResp.body)
              finally if (!keepAlive) rawResp.body.close()
            } else g(new ByteArrayInputStream(Array()))
          }

          if (sHeaders.statusCode == 304 || sHeaders.is2xx || !check) process(f)
          else {
            val errOut = new ByteArrayOutputStream()
            process(geny.Internal.transfer(_, errOut))
            throw new RequestFailedException(Response(url = sHeaders.url,
              statusCode = sHeaders.statusCode, statusMessage = sHeaders.statusMessage,
              data = new geny.Bytes(errOut.toByteArray),
              headers = sHeaders.headers, history = sHeaders.history))
          }
        }
      } finally { if (!useShared) t.close() }
    }
  }

  private def ssContextRefEq(a: Any, b: Any): Boolean =
    a.asInstanceOf[AnyRef] eq b.asInstanceOf[AnyRef]

  def apply(r: Request, data: RequestBlob, chunkedUpload: Boolean): Response = apply(
    r.url, r.auth, r.params, r.headers, data,
    r.readTimeout, r.connectTimeout, r.proxy, r.cert, r.sslContext,
    r.cookies, r.cookieValues, r.maxRedirects,
    r.verifySslCerts, r.autoDecompress, r.compress,
    r.keepAlive, r.check, chunkedUpload)

  def stream(r: Request, data: RequestBlob, chunkedUpload: Boolean,
             onHeadersReceived: StreamHeaders => Unit): geny.Writable =
    stream(url = r.url, auth = r.auth, params = r.params,
      blobHeaders = Seq.empty, headers = r.headers, data = data,
      readTimeout = r.readTimeout, connectTimeout = r.connectTimeout,
      proxy = r.proxy, cert = r.cert, sslContext = r.sslContext,
      cookies = r.cookies, cookieValues = r.cookieValues,
      maxRedirects = r.maxRedirects, verifySslCerts = r.verifySslCerts,
      autoDecompress = r.autoDecompress, compress = r.compress,
      keepAlive = r.keepAlive, check = r.check, chunkedUpload = chunkedUpload,
      redirectedFrom = None, onHeadersReceived = onHeadersReceived)
}
