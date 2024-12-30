package requests

import java.io.FileInputStream
import java.net.HttpCookie
import java.util.UUID

import collection.JavaConverters._
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.zip.{DeflaterOutputStream, GZIPOutputStream}

import javax.net.ssl.SSLContext

/**
 * Mechanisms for compressing the upload stream; supports Gzip and Deflate by default
 */
trait Compress {
  def headers: Seq[(String, String)]
  def wrap(x: OutputStream): OutputStream
}
object Compress {
  object Gzip extends Compress {
    def headers = Seq(
      "Content-Encoding" -> "gzip"
    )
    def wrap(x: OutputStream) = new GZIPOutputStream(x)
  }
  object Deflate extends Compress {
    def headers = Seq(
      "Content-Encoding" -> "deflate"
    )
    def wrap(x: OutputStream) = new DeflaterOutputStream(x)
  }
  object None extends Compress {
    def headers = Nil
    def wrap(x: OutputStream) = x
  }
}

/**
 * The equivalent of configuring a [[Requester.apply]] or [[Requester.stream]] call, but without
 * invoking it. Useful if you want to further customize it and make the call later via the overloads
 * of `apply`/`stream` that take a [[Request]].
 */
case class Request(
    url: String,
    auth: RequestAuth = RequestAuth.Empty,
    params: Iterable[(String, String)] = Nil,
    headers: Iterable[(String, String)] = Nil,
    readTimeout: Int = 0,
    connectTimeout: Int = 0,
    proxy: (String, Int) = null,
    cert: Cert = null,
    sslContext: SSLContext = null,
    cookies: Map[String, HttpCookie] = Map(),
    cookieValues: Map[String, String] = Map(),
    maxRedirects: Int = 5,
    verifySslCerts: Boolean = true,
    autoDecompress: Boolean = true,
    compress: Compress = Compress.None,
    keepAlive: Boolean = true,
    check: Boolean = true
)

/**
 * Represents the different things you can upload in the body of a HTTP request. By default, allows
 * form-encoded key-value pairs, arrays of bytes, strings, files, and InputStreams. These types can
 * be passed directly to the `data` parameter of [[Requester.apply]] and will be wrapped
 * automatically by the implicit constructors.
 */
trait RequestBlob {
  def headers: Seq[(String, String)] = Nil
  def write(out: java.io.OutputStream): Unit
}
object RequestBlob {
  object EmptyRequestBlob extends RequestBlob {
    def write(out: java.io.OutputStream): Unit = ()
    override def headers = Seq("Content-Length" -> "0")
  }

  implicit class ByteSourceRequestBlob[T](x: T)(implicit f: T => geny.Writable)
      extends RequestBlob {
    private[this] val s = f(x)
    override def headers =
      super.headers ++
        s.httpContentType.map("Content-Type" -> _) ++
        s.contentLength.map("Content-Length" -> _.toString)
    def write(out: java.io.OutputStream) = s.writeBytesTo(out)
  }
  implicit class FileRequestBlob(x: java.io.File) extends RequestBlob {
    override def headers = super.headers ++ Seq(
      "Content-Type" -> "application/octet-stream",
      "Content-Length" -> x.length().toString
    )
    def write(out: java.io.OutputStream) =
      Util.transferTo(new FileInputStream(x), out)
  }
  implicit class NioFileRequestBlob(x: java.nio.file.Path) extends RequestBlob {
    override def headers = super.headers ++ Seq(
      "Content-Type" -> "application/octet-stream",
      "Content-Length" -> java.nio.file.Files.size(x).toString
    )
    def write(out: java.io.OutputStream) =
      Util.transferTo(java.nio.file.Files.newInputStream(x), out)
  }

  implicit class FormEncodedRequestBlob(val x: Iterable[(String, String)]) extends RequestBlob {
    val serialized = Util.urlEncode(x).getBytes
    override def headers = super.headers ++ Seq(
      "Content-Type" -> "application/x-www-form-urlencoded"
    )
    def write(out: java.io.OutputStream) = {
      out.write(serialized)
    }
  }

  implicit class MultipartFormRequestBlob(val parts: Iterable[MultiItem]) extends RequestBlob {
    val boundary = UUID.randomUUID().toString
    val crlf = "\r\n"
    val pref = "--"

    val ContentDisposition = "Content-Disposition: form-data; name=\""
    val filenameSnippet = "\"; filename=\""

    // encode params up front for the length calculation

    val partBytes = parts.map(p =>
      (
        p.name.getBytes(),
        if (p.filename == null) Array[Byte]() else p.filename.getBytes(),
        p
      )
    )

    override def headers = Seq(
      "Content-Type" -> s"multipart/form-data; boundary=$boundary"
    )
    def write(out: java.io.OutputStream) = {
      def writeBytes(s: String): Unit = out.write(s.getBytes())

      partBytes.foreach { case (name, filename, part) =>
        writeBytes(pref + boundary + crlf)
        part.data.headers.foreach { case (headerName, headerValue) =>
          writeBytes(s"$headerName: $headerValue$crlf")
        }
        writeBytes(ContentDisposition)
        out.write(name)
        if (filename.nonEmpty) {
          writeBytes(filenameSnippet)
          out.write(filename)
        }
        writeBytes("\"" + crlf + crlf)
        part.data.write(out)
        writeBytes(crlf)
      }

      writeBytes(pref + boundary + pref + crlf)

      out.flush()
      out.close()
    }
  }
}

case class MultiPart(items: MultiItem*) extends RequestBlob.MultipartFormRequestBlob(items)
case class MultiItem(name: String, data: RequestBlob, filename: String = null)

/**
 * Wraps the array of bytes returned in the body of a HTTP response
 */
class ResponseBlob(val bytes: Array[Byte]) {
  override def toString = s"ResponseBlob(${bytes.length} bytes)"
  def text = new String(bytes)

  override def hashCode() = java.util.Arrays.hashCode(bytes)

  override def equals(obj: scala.Any) = obj match {
    case r: ResponseBlob => java.util.Arrays.equals(bytes, r.bytes)
    case _               => false
  }
}

/**
 * Represents a HTTP response
 *
 * @param url
 *   the URL that the original request was made to
 * @param statusCode
 *   the status code of the response
 * @param statusMessage
 *   a string that describes the status code. This is not the reason phrase sent by the server, but
 *   a string describing [[statusCode]], as hardcoded in this library
 * @param headers
 *   the raw headers the server sent back with the response
 * @param data
 *   the response body; may contain HTML, JSON, or binary or textual data
 * @param history
 *   the response of any redirects that were performed before arriving at the current response
 */
case class Response(
    url: String,
    statusCode: Int,
    @deprecated("Value is inferred from `statusCode`", "0.9.0")
    statusMessage: String,
    data: geny.Bytes,
    headers: Map[String, Seq[String]],
    history: Option[Response]
) extends geny.ByteData
    with geny.Readable {

  def bytes = data.array

  @deprecated("Use `.bytes`")
  def contents = data.array

  /**
   * Returns the cookies set by this response, and by any redirects that lead up to it
   */
  val cookies: Map[String, HttpCookie] =
    history.toSeq.flatMap(_.cookies).toMap ++ headers
      .get("set-cookie")
      .iterator
      .flatten
      .flatMap(java.net.HttpCookie.parse(_).asScala)
      .map(x => x.getName -> x)
      .toMap

  def contentType = headers.get("content-type").flatMap(_.headOption)

  def location = headers.get("location").flatMap(_.headOption)

  def is2xx = statusCode.toString.charAt(0) == '2'
  def is3xx = statusCode.toString.charAt(0) == '3'
  def is4xx = statusCode.toString.charAt(0) == '4'
  def is5xx = statusCode.toString.charAt(0) == '5'

  def readBytesThrough[T](f: java.io.InputStream => T): T = {
    f(new java.io.ByteArrayInputStream(data.array))
  }
  override def httpContentType: Option[String] = contentType
  override def contentLength: Option[Long] = Some(data.array.length)
}

case class StreamHeaders(
    url: String,
    statusCode: Int,
    @deprecated("Value is inferred from `statusCode`", "0.9.0")
    statusMessage: String,
    headers: Map[String, Seq[String]],
    history: Option[Response]
) {
  def is2xx = statusCode.toString.charAt(0) == '2'
  def is3xx = statusCode.toString.charAt(0) == '3'
  def is4xx = statusCode.toString.charAt(0) == '4'
  def is5xx = statusCode.toString.charAt(0) == '5'
}

/**
 * Different ways you can authorize a HTTP request; by default, HTTP Basic auth and Proxy auth are
 * supported
 */
trait RequestAuth {
  def header: Option[String]
}

object RequestAuth {
  object Empty extends RequestAuth {
    def header = None
  }
  implicit def implicitBasic(x: (String, String)): Basic = new Basic(x._1, x._2)
  class Basic(username: String, password: String) extends RequestAuth {
    def header = Some(
      "Basic " + java.util.Base64.getEncoder
        .encodeToString((username + ":" + password).getBytes())
    )
  }
  case class Proxy(username: String, password: String) extends RequestAuth {
    def header = Some(
      "Proxy-Authorization " + java.util.Base64.getEncoder
        .encodeToString((username + ":" + password).getBytes())
    )
  }
  case class Bearer(token: String) extends RequestAuth {
    def header = Some(s"Bearer $token")
  }
}

sealed trait Cert
object Cert {
  implicit def implicitP12(path: String): P12 = P12(path, None)
  implicit def implicitP12(x: (String, String)): P12 = P12(x._1, Some(x._2))
  case class P12(p12: String, pwd: Option[String] = None) extends Cert
}
