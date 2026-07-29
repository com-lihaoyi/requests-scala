package requests

import scala.collection.mutable

case class Session(
    headers: Map[String, String] = BaseSession.defaultHeaders,
    cookieValues: Map[String, String] = Map(),
    cookies: mutable.Map[String, BaseCookie] = mutable.LinkedHashMap.empty[String, BaseCookie],
    auth: RequestAuth = RequestAuth.Empty,
    proxy: (String, Int) = null,
    cert: Cert = null,
    sslContext: Any = null,
    persistCookies: Boolean = true,
    maxRedirects: Int = 5,
    readTimeout: Int = 10 * 1000,
    connectTimeout: Int = 10 * 1000,
    verifySslCerts: Boolean = true,
    autoDecompress: Boolean = true,
    compress: Compress = Compress.None,
    chunkedUpload: Boolean = false,
    check: Boolean = true,
) extends BaseSession {
  for ((k, v) <- cookieValues) cookies(k) = BaseCookie(k, v, "", "/", -1L, false, false)
  lazy val transport: Transport = TransportFactory.create()
}
