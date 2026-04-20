package requests

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import java.io._
import java.net.InetSocketAddress
import java.util.zip.{GZIPInputStream, InflaterInputStream}
import requests.Compress._
import scala.annotation.tailrec
import scala.collection.mutable.StringBuilder

object ServerUtils {
  def usingEchoServer(f: Int => Unit): Unit = {
    val server = new EchoServer
    try f(server.getPort())
    finally server.stop()
  }

  private class EchoServer extends HttpHandler {
    private val server: HttpServer =
      HttpServer.create(new InetSocketAddress(0), 0)
    server.createContext("/echo", this)
    server.setExecutor(null); // default executor
    server.start()

    def getPort(): Int = server.getAddress.getPort

    def stop(): Unit = server.stop(0)

    override def handle(t: HttpExchange): Unit = try {
      val h: java.util.List[String] =
        t.getRequestHeaders.get("Content-encoding")
      val c: Compress =
        if (h == null) None
        else if (h.contains("gzip")) Gzip
        else if (h.contains("deflate")) Deflate
        else None
      val msg = new Plumper(c).decompress(t.getRequestBody)
      t.sendResponseHeaders(200, msg.length)
      t.getResponseBody.write(msg.getBytes())
      t.getResponseBody.close()
    } catch {
      case e: Exception =>
        e.printStackTrace()
        t.sendResponseHeaders(500, -1)
    } finally {
      t.close()
    }
  }

  /**
   * Stream uncompresser
   * @param c
   *   Compression mode
   */
  private class Plumper(c: Compress) {

    private def wrap(is: InputStream): InputStream =
      c match {
        case None    => is
        case Gzip    => new GZIPInputStream(is)
        case Deflate => new InflaterInputStream(is)
      }

    def decompress(compressed: InputStream): String = {
      val gis = wrap(compressed)
      val br = new BufferedReader(new InputStreamReader(gis, "UTF-8"))
      val sb = new StringBuilder()

      @tailrec
      def read(): Unit = {
        val line = br.readLine
        if (line != null) {
          sb.append(line)
          read()
        }
      }

      read()
      br.close()
      gis.close()
      compressed.close()
      sb.toString()
    }
  }
}
