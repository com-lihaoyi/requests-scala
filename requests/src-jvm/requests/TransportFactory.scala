package requests

object TransportFactory extends _root_.requests.TransportFactory {
  def create(): Transport = new JvmTransport()
}

/** Implicit conversions between java.net.HttpCookie and BaseCookie. */
object CookieConverters {
  implicit def httpCookieToBaseCookie(c: java.net.HttpCookie): BaseCookie = BaseCookie(
    name = c.getName, value = c.getValue,
    domain = if (c.getDomain != null) c.getDomain else "",
    path = if (c.getPath != null) c.getPath else "/",
    maxAge = c.getMaxAge, secure = c.getSecure, httpOnly = c.isHttpOnly
  )

  implicit def baseCookieToHttpCookie(c: BaseCookie): java.net.HttpCookie = {
    val hc = new java.net.HttpCookie(c.name, c.value)
    hc.setDomain(c.domain)
    hc.setPath(c.path)
    hc.setMaxAge(c.maxAge)
    hc.setSecure(c.secure)
    hc.setHttpOnly(c.httpOnly)
    hc
  }
}
