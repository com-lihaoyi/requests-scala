package requests

import java.io.ByteArrayOutputStream
import java.net.http._
import java.net.{InetSocketAddress, ProxySelector, URI}
import java.time.Duration
import java.util.concurrent.{ExecutorService, Executors, ThreadFactory}

import scala.collection.JavaConverters._

class JvmTransport extends Transport {

  private val executor: ExecutorService = Executors.newCachedThreadPool(new ThreadFactory {
    override def newThread(r: Runnable): Thread = {
      val t = new Thread(r, "requests-scala-http")
      t.setDaemon(true)
      t
    }
  })

  def send(
    method: String, url: String, headers: Iterable[(String, String)],
    data: RequestBlob, connectTimeout: Int, readTimeout: Int,
    sslContext: Any, cert: Cert, proxy: (String, Int), verifySslCerts: Boolean
  ): RawTransportResponse = {
    val uri = URI.create(url)
    val builder = HttpClient.newBuilder()
      .executor(executor)
      .followRedirects(HttpClient.Redirect.NEVER)
      .connectTimeout(Duration.ofMillis(connectTimeout))

    builder.proxy(proxy match {
      case null       => ProxySelector.getDefault
      case (ip, port) => ProxySelector.of(new InetSocketAddress(ip, port))
    })

    val jvmSsl = sslContext.asInstanceOf[javax.net.ssl.SSLContext]
    if (cert != null) {
      builder.sslContext(SslUtil.clientCertSSLContext(cert, verifySslCerts))
    } else if (jvmSsl != null) {
      builder.sslContext(jvmSsl)
    } else if (!verifySslCerts) {
      builder.sslContext(SslUtil.noVerifySSLContext)
    }

    val httpClient = builder.build()
    try {
      val buf = new ByteArrayOutputStream()
      data.write(buf)
      val bodyBytes = buf.toByteArray
      val bodyPub: HttpRequest.BodyPublisher =
        if (bodyBytes.isEmpty) HttpRequest.BodyPublishers.noBody()
        else HttpRequest.BodyPublishers.ofByteArray(bodyBytes)

      val reqBuilder = HttpRequest.newBuilder()
        .uri(uri).timeout(Duration.ofMillis(readTimeout))
        .method(method.toUpperCase, bodyPub)
      headers.foreach { case (k, v) => reqBuilder.header(k, v) }

      val response = try {
        httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofInputStream())
      } catch { case e: Throwable => translateError(url, readTimeout, connectTimeout, e) }

      val code = response.statusCode()
      val hdrs = response.headers().map().asScala
        .filter(_._1 != null)
        .map { case (k, v) => (k.toLowerCase(), v.asScala.toList) }.toMap

      RawTransportResponse(code, StatusMessages.byStatusCode.getOrElse(code, ""), hdrs, response.body(), url)
    } finally { JvmTransport.closeHttpClient(httpClient) }
  }

  private def translateError(url: String, rt: Int, ct: Int, e: Throwable): Nothing = e match {
    case _: javax.net.ssl.SSLException => throw new InvalidCertException(url, e)
    case _: HttpConnectTimeoutException | _: HttpTimeoutException =>
      throw new TimeoutException(url, rt, ct)
    case _: java.net.UnknownHostException => throw new UnknownHostException(url, e.getMessage)
    case _: java.nio.channels.UnresolvedAddressException => throw new UnknownHostException(url, e.getMessage)
    case _: java.security.cert.CertificateException => throw new InvalidCertException(url, e)
    case _: java.security.cert.CertPathValidatorException => throw new InvalidCertException(url, e)
    case _ =>
      val cause = e.getCause
      if (cause != null) translateError(url, rt, ct, cause)
      else throw new RequestsException(e.getMessage, Some(e))
  }

  def close(): Unit = { executor.shutdown() }
}

object JvmTransport {
  def closeHttpClient(c: HttpClient): Unit = {
    try { classOf[HttpClient].getMethod("close").invoke(c) } catch {
      case _: NoSuchMethodException =>
        try {
          val impl = { val f = c.getClass.getDeclaredField("impl"); f.setAccessible(true); f.get(c) }
          val mgr = { val f = impl.getClass.getDeclaredField("selmgr"); f.setAccessible(true); f.get(impl) }
          val sel = { val f = mgr.getClass.getDeclaredField("selector"); f.setAccessible(true); f.get(mgr) }
          sel.getClass.getMethod("close").invoke(sel)
        } catch { case _: Exception => System.err.println(
          "requests: Unable to close HttpClient thread. Add JVM arg: --add-opens java.net.http/jdk.internal.net.http=ALL-UNNAMED") }
    }
  }
}
