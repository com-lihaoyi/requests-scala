package requests

/**
 * Platform-agnostic HTTP transport layer.
 */
trait Transport {
  def send(
    method: String, url: String, headers: Iterable[(String, String)],
    data: RequestBlob, connectTimeout: Int, readTimeout: Int,
    sslContext: Any, cert: Cert, proxy: (String, Int), verifySslCerts: Boolean
  ): RawTransportResponse

  /** Release transport resources. Override in platforms that need cleanup (JVM). */
  def close(): Unit = ()
}

/** Factory for platform-specific Transport instances. */
trait TransportFactory {
  def create(): Transport
}

case class RawTransportResponse(
  statusCode: Int, statusMessage: String,
  headers: Map[String, Seq[String]], body: java.io.InputStream, finalUrl: String
)

/** Platform-agnostic HTTP cookie. */
case class BaseCookie(
  name: String, value: String, domain: String, path: String,
  maxAge: Long, secure: Boolean, httpOnly: Boolean
) {
  def hasExpired: Boolean = maxAge == 0
  def getDomain: String = domain
  def getPath: String = path
  def getName: String = name
  def getValue: String = value
}

object BaseCookie {
  def parse(setCookieHeader: String): Seq[BaseCookie] = {
    // Handle comma-separated cookies in a single Set-Cookie header
    val results = scala.collection.mutable.ListBuffer[BaseCookie]()
    val parts = setCookieHeader.split(",(?=[^;]*=)")
    if (parts.isEmpty) return Nil
    for (part <- parts) {
      val attrs = part.trim.split(";").map(_.trim)
      if (attrs.nonEmpty) {
        val nameValue = attrs.head.split("=", 2)
        if (nameValue.length >= 2) {
          val name = nameValue(0)
          val value = nameValue(1)
          var domain = ""
          var path = "/"
          var maxAge = -1L
          var secure = false
          var httpOnly = false
          attrs.tail.foreach { attr =>
            val kv = attr.split("=", 2)
            kv(0).toLowerCase match {
              case "domain"  => if (kv.length > 1) domain = kv(1)
              case "path"    => if (kv.length > 1) path = kv(1)
              case "max-age" => if (kv.length > 1) maxAge = kv(1).toLong
              case "secure"  => secure = true
              case "httponly" => httpOnly = true
              case _ =>
            }
          }
          results += BaseCookie(name, value, domain, path, maxAge, secure, httpOnly)
        }
      }
    }
    if (results.isEmpty) Nil else results.toSeq
  }
}
