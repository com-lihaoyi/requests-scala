package requests

import java.io.FileInputStream
import java.net.http._
import java.net.{InetSocketAddress, ProxySelector, URL}
import java.security.cert.X509Certificate
import java.time.Duration

import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManager, X509TrustManager}

import scala.collection.JavaConverters._

private[requests] object Platform {

  private val noVerifySSLContext: SSLContext = {
    val sc = SSLContext.getInstance("SSL")
    sc.init(null, trustAllCerts, new java.security.SecureRandom())
    sc
  }

  private lazy val trustAllCerts = Array[TrustManager](new X509TrustManager() {
    def getAcceptedIssuers = new Array[X509Certificate](0)
    def checkClientTrusted(chain: Array[X509Certificate], authType: String) = {}
    def checkServerTrusted(chain: Array[X509Certificate], authType: String) = {}
  })

  private def clientCertSSLContext(cert: Cert, verifySslCerts: Boolean): SSLContext = {
    cert match {
      case Cert.P12(path, password) =>
        val pass = password.map(_.toCharArray).getOrElse(Array.emptyCharArray)
        val keyManagers = {
          val ks = java.security.KeyStore.getInstance("PKCS12")
          ks.load(new FileInputStream(path), pass)
          val keyManager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
          keyManager.init(ks, pass)
          keyManager.getKeyManagers
        }
        val sc = SSLContext.getInstance("SSL")
        val trustManagers = if (verifySslCerts) null else trustAllCerts
        sc.init(keyManagers, trustManagers, new java.security.SecureRandom())
        sc
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
    val httpClient = buildHttpClient(proxy, cert, sslContext, verifySslCerts, connectTimeout)

    try {
      val headersKeyValueAlternating = headers.flatMap { case (k, v) => Seq(k, v) }

      val bodyPublisher: HttpRequest.BodyPublisher =
        if (body.isEmpty) HttpRequest.BodyPublishers.noBody()
        else HttpRequest.BodyPublishers.ofByteArray(body)

      val requestBuilder =
        HttpRequest
          .newBuilder()
          .uri(new URL(url).toURI)
          .timeout(Duration.ofMillis(readTimeout))
          .headers(headersKeyValueAlternating: _*)
          .method(method, bodyPublisher)

      def wrapError: PartialFunction[Throwable, Nothing] = {
        case e: javax.net.ssl.SSLException => throw new InvalidCertException(url, e)
        case _: HttpConnectTimeoutException | _: HttpTimeoutException =>
          throw new TimeoutException(url, readTimeout, connectTimeout)
        case e: java.net.UnknownHostException => throw new UnknownHostException(url, e.getMessage)
        case e: java.nio.channels.UnresolvedAddressException =>
          throw new UnknownHostException(url, e.getMessage)
        case e: java.security.cert.CertificateException => throw new InvalidCertException(url, e)
        case e: java.security.cert.CertPathValidatorException =>
          throw new InvalidCertException(url, e)
      }

      val response =
        try httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream())
        catch {
          case e: Throwable =>
            wrapError.lift(e)
              .orElse(wrapError.lift(e.getCause))
              .orElse(Option(e.getCause).flatMap(c => wrapError.lift(c.getCause)))
              .getOrElse(throw new RequestsException(e.getMessage, Some(e)))
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

      PlatformResponse(responseCode, headerFields, response.body())
    } finally {
      closeHttpClient(httpClient)
    }
  }

  def closeSession(sess: BaseSession): Unit = ()

  private def buildHttpClient(
      proxy: (String, Int),
      cert: Cert,
      sslContext: SSLContext,
      verifySslCerts: Boolean,
      connectTimeout: Int,
  ): HttpClient = {
    HttpClient
      .newBuilder()
      .followRedirects(HttpClient.Redirect.NEVER)
      .proxy(proxy match {
        case null       => ProxySelector.getDefault
        case (ip, port) => ProxySelector.of(new InetSocketAddress(ip, port))
      })
      .sslContext(
        if (cert != null)
          clientCertSSLContext(cert, verifySslCerts)
        else if (sslContext != null)
          sslContext
        else if (!verifySslCerts)
          noVerifySSLContext
        else
          SSLContext.getDefault,
      )
      .connectTimeout(Duration.ofMillis(connectTimeout))
      .build()
  }

  private def closeHttpClient(httpClient: HttpClient): Unit = {
    try {
      val closeMethod = classOf[HttpClient].getMethod("close")
      closeMethod.invoke(httpClient)
    } catch {
      case _: NoSuchMethodException =>
        try {
          val facadeClass = httpClient.getClass
          val implField = facadeClass.getDeclaredField("impl")
          implField.setAccessible(true)
          val impl = implField.get(httpClient)
          val selectorManagerField = impl.getClass.getDeclaredField("selmgr")
          selectorManagerField.setAccessible(true)
          val selectorManager = selectorManagerField.get(impl)
          val selectorField = selectorManager.getClass.getDeclaredField("selector")
          selectorField.setAccessible(true)
          val selector = selectorField.get(selectorManager)
          val closeMethod = selector.getClass.getMethod("close")
          closeMethod.invoke(selector)
        } catch {
          case _: Exception =>
            System.err.println(
              "requests: Unable to close HttpClient SelectorManager thread. " +
              "To fix thread leaks on Java <21, add JVM arg: " +
              "--add-opens java.net.http/jdk.internal.net.http=ALL-UNNAMED"
            )
        }
    }
  }
}
