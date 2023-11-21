package requests
import java.net.HttpCookie

import javax.net.ssl.SSLContext

import scala.collection.mutable

/**
  * A long-lived session; this can be used to automatically persist cookies
  * from one request to the next, or to set default configuration that will
  * be shared between requests. These configuration flags can all be
  * over-ridden by the parameters on [[Requester.apply]] or [[Requester.stream]]
  *
  * @param auth HTTP authentication you want to use with this request; defaults to none
  * @param headers Custom headers to use, in addition to the defaults
  * @param readTimeout How long to wait for data to be read before timing out
  * @param connectTimeout How long to wait for a connection before timing out
  * @param proxy Host and port of a proxy you want to use
  * @param proxyAuth Username and password of HTTP proxy
  * @param cookies Custom cookies to send up with this request
  * @param maxRedirects How many redirects to automatically resolve; defaults to 5.
  *                     You can also set it to 0 to prevent Requests from resolving
  *                     redirects for you
  * @param verifySslCerts Set this to false to ignore problems with SSL certificates
  */
case class Session(headers: Map[String, String] = BaseSession.defaultHeaders,
                   cookieValues: Map[String, String] = Map(),
                   cookies: mutable.Map[String, HttpCookie] = mutable.LinkedHashMap.empty[String, HttpCookie],
                   auth: RequestAuth = RequestAuth.Empty,
                   proxy: (String, Int) = null,
                   proxyAuth: (String, String) = null,
                   cert: Cert = null,
                   sslContext: SSLContext = null,
                   persistCookies: Boolean = true,
                   maxRedirects: Int = 5,
                   readTimeout: Int = 10 * 1000,
                   connectTimeout: Int = 10 * 1000,
                   verifySslCerts: Boolean = true,
                   autoDecompress: Boolean = true,
                   compress: Compress = Compress.None,
                   chunkedUpload: Boolean = false,
                   check: Boolean = true)
  extends BaseSession{

  for((k, v) <- cookieValues) cookies(k) = new HttpCookie(k, v)
}
