package requests

import java.io.FileInputStream
import java.net.HttpCookie
import java.util.UUID

import collection.JavaConverters._
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.zip.{DeflaterOutputStream, GZIPOutputStream}

/**
  * Mechanisms for compressing the upload stream; supports Gzip and Deflate
  * by default
  */
trait Compress{
  def headers: Seq[(String, String)]
  def wrap(x: OutputStream): OutputStream
}
object Compress{
  object Gzip extends Compress{
    def headers = Seq(
      "Content-Encoding" -> "gzip"
    )
    def wrap(x: OutputStream) = new GZIPOutputStream(x)
  }
  object Deflate extends Compress{
    def headers = Seq(
      "Content-Encoding" -> "deflate"
    )
    def wrap(x: OutputStream) = new DeflaterOutputStream(x)
  }
  object None extends Compress{
    def headers = Nil
    def wrap(x: OutputStream) = x
  }
}

/**
  * The equivalent of configuring a [[Requester.apply]] or [[Requester.stream]]
  * call, but without invoking it. Useful if you want to further customize it
  * and make the call later via the overloads of `apply`/`stream` that take a
  * [[Request]].
  */
case class Request(url: String,
                   auth: RequestAuth = null,
                   params: Iterable[(String, String)] = Nil,
                   headers: Iterable[(String, String)] = Nil,
                   readTimeout: Int = 0,
                   connectTimeout: Int = 0,
                   proxy: (String, Int) = null,
                   cookies: Map[String, HttpCookie] = Map(),
                   cookieValues: Map[String, String] = Map(),
                   maxRedirects: Int = 5,
                   verifySslCerts: Boolean = true,
                   autoDecompress: Boolean = true,
                   compress: Compress = Compress.None,
                   keepAlive: Boolean = true)

/**
  * Represents the different things you can upload in the body of a HTTP
  * request. By default allows form-encoded key-value pairs, arrays of bytes,
  * strings, files, and inputstreams. These types can be passed directly to
  * the `data` parameter of [[Requester.apply]] and will be wrapped automatically
  * by the implicit constructors.
  */
trait RequestBlob{
  def headers: Seq[(String, String)] = Nil
  def inMemory: Boolean
  def write(out: java.io.OutputStream): Unit
}
object RequestBlob{
  trait SizedBlob extends RequestBlob{
    override def headers: Seq[(String, String)] = Seq(
      "Content-Length" -> length.toString
    )
    def length: Long
  }
  object EmptyRequestBlob extends RequestBlob{
    def inMemory = true
    def write(out: java.io.OutputStream): Unit = ()
  }
  implicit def BytesRequestBlob(x: Array[Byte]) = SizedBlob.BytesRequestBlob(x)
  implicit def StringRequestBlob(x: String) = SizedBlob.StringRequestBlob(x)
  implicit def FileRequestBlob(x: java.io.File) = SizedBlob.FileRequestBlob(x)
  implicit def NioFileRequestBlob(x: java.nio.file.Path) = SizedBlob.NioFileRequestBlob(x)
  object SizedBlob{
    implicit class BytesRequestBlob(val x: Array[Byte]) extends SizedBlob{
      def inMemory = true
      override def headers = super.headers ++ Seq(
        "Content-Type" -> "application/octed-stream"
      )
      def length = x.length
      def write(out: java.io.OutputStream) = out.write(x)
    }
    implicit class StringRequestBlob(val x: String) extends SizedBlob{
      def inMemory = true
      override def headers = super.headers ++ Seq(
        "Content-Type" -> "text/plain"
      )
      val serialized = x.getBytes()
      def length = serialized.length
      def write(out: java.io.OutputStream) = out.write(serialized)
    }
    implicit class FileRequestBlob(val x: java.io.File) extends SizedBlob{
      def inMemory = false
      override def headers = super.headers ++ Seq(
        "Content-Type" -> "application/octed-stream"
      )
      def length = x.length()
      def write(out: java.io.OutputStream) = Util.transferTo(new FileInputStream(x), out)
    }
    implicit class NioFileRequestBlob(val x: java.nio.file.Path) extends SizedBlob{
      def inMemory = false
      override def headers = super.headers ++ Seq(
        "Content-Type" -> "application/octed-stream"
      )
      def length = java.nio.file.Files.size(x)
      def write(out: java.io.OutputStream) = Util.transferTo(java.nio.file.Files.newInputStream(x), out)
    }
  }

  implicit class InputStreamRequestBlob(val x: java.io.InputStream) extends RequestBlob{
    def inMemory = false
    override def headers = super.headers ++ Seq(
      "Content-Type" -> "application/octed-stream"
    )
    def write(out: java.io.OutputStream) = Util.transferTo(x, out)
  }
  implicit class FormEncodedRequestBlob(val x: Iterable[(String, String)]) extends SizedBlob {
    def inMemory = true
    val serialized = Util.urlEncode(x).getBytes
    def length = serialized.length
    override def headers = super.headers ++ Seq(
      "Content-Type" -> "application/x-www-form-urlencoded"
    )
    def write(out: java.io.OutputStream) = {
      out.write(serialized)
    }
  }

  implicit class MultipartFormRequestBlob(val parts: Iterable[MultiItem]) extends RequestBlob{
    def inMemory = true
    val boundary = UUID.randomUUID().toString
    val crlf = "\r\n"
    val pref = "--"

    val ContentDisposition = "Content-Disposition: form-data; name=\""
    val filenameSnippet = "\"; filename=\""

    // encode params up front for the length calculation

    val partBytes = parts.map(p => (p.name.getBytes(), if (p.filename == null) Array[Byte]() else p.filename.getBytes(), p))

    // we need to pre-calculate the Content-Length of this HttpRequest because most servers don't
    // support chunked transfer
    val totalBytesToSend: Long = {

      val partsLength = partBytes.map{
        case (name, filename, part) =>
          pref.length + boundary.length + crlf.length +
          ContentDisposition.length +
          name.length +
          (if(filename.nonEmpty) filenameSnippet.length + filename.length else 0) +
          "\"".length + crlf.length + crlf.length +
          part.data.length +
          crlf.length
      }
      val finaleBoundaryLength = (pref.length * 2) + boundary.length + crlf.length

      partsLength.sum + finaleBoundaryLength
    }

    override def headers = Seq(
      "Content-Type" -> s"multipart/form-data; boundary=$boundary",
      "Content-Length" -> totalBytesToSend.toString
    )

    def write(out: java.io.OutputStream) = {
      def writeBytes(s: String): Unit = out.write(s.getBytes())

      partBytes.foreach {
        case(name, filename, part) =>
          writeBytes(pref + boundary + crlf)
          writeBytes(ContentDisposition)
          out.write(name)
          if (filename.nonEmpty){
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
case class MultiItem(name: String,
                     data: RequestBlob.SizedBlob,
                     filename: String = null)

/**
  * Wraps the array of bytes returned in the body of a HTTP response
  */
class ResponseBlob(val bytes: Array[Byte]){
  override def toString = s"ResponseBlob(${bytes.length} bytes)"
  def text = new String(bytes)

  override def hashCode() = java.util.Arrays.hashCode(bytes)

  override def equals(obj: scala.Any) = obj match{
    case r: ResponseBlob => java.util.Arrays.equals(bytes, r.bytes)
    case _ => false
  }
}


/**
  * Represents a HTTP response
  *
  * @param url the URL that the original request was made to
  * @param statusCode the status code of the response
  * @param statusMessage the status message of the response
  * @param headers the raw headers the server sent back with the response
  * @param data the response body; may contain HTML, JSON, or binary or textual data
  * @param history the response of any redirects that were performed before
  *                arriving at the current response
  */
case class Response(url: String,
                    statusCode: Int,
                    statusMessage: String,
                    headers: Map[String, Seq[String]],
                    data: ResponseBlob,
                    history: Option[Response]){

  /**
    * Decodes the byte contents of this response as a String using the default charset
    */
  def text() = new String(data.bytes)
  /**
    * Decodes the byte contents of this response as a String using the given charset
    */
  def text(cs: Charset) = new String(data.bytes, cs)
  /**
    * Returns the byte contents of this response
    */
  def contents = data.bytes

  /**
    * Returns the cookies set by this response, and by any redirects that lead up to it
    */
  val cookies: Map[String, HttpCookie] = history.toSeq.flatMap(_.cookies).toMap ++ headers
    .get("set-cookie")
    .iterator
    .flatten
    .flatMap(java.net.HttpCookie.parse(_).asScala)
    .map(x => x.getName -> x)
    .toMap

  def contentType = headers.get("content-type").flatMap(_.headOption)

  def contentLength = headers.get("content-length").flatMap(_.headOption)

  def location = headers.get("location").flatMap(_.headOption)

  def is2xx = statusCode.toString.head == '2'
  def is3xx = statusCode.toString.head == '3'
  def is4xx = statusCode.toString.head == '4'
  def is5xx = statusCode.toString.head == '5'
}

case class StreamHeaders(url: String,
                         statusCode: Int,
                         statusMessage: String,
                         headers: Map[String, Seq[String]],
                         history: Option[Response])
/**
  * Different ways you can authorize a HTTP request; by default, HTTP Basic
  * auth and Proxy auth are supported
  */
trait RequestAuth{
  def header: Option[String]
}

object RequestAuth{
  object Empty extends RequestAuth{
    def header = None
  }
  implicit def implicitBasic(x: (String, String)) = new Basic(x._1, x._2)
  class Basic(username: String, password: String) extends RequestAuth{
    def header = Some("Basic " + java.util.Base64.getEncoder.encodeToString((username + ":" + password).getBytes()))
  }
  case class Proxy(username: String, password: String) extends RequestAuth{
    def header = Some("Proxy-Authorization " + java.util.Base64.getEncoder.encodeToString((username + ":" + password).getBytes()))
  }
  case class Bearer(token: String) extends RequestAuth {
    def header = Some(s"Bearer $token")
  }
}
