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

  /**
   * Creates a mock proxy server that captures headers from incoming requests.
   * The server doesn't actually proxy requests - it just captures headers and returns a 502.
   */
  def usingProxyServer(f: (Int, scala.collection.mutable.Map[String, String]) => Unit): Unit = {
    val receivedHeaders = scala.collection.mutable.Map[String, String]()
    val server = new MockProxyServer(receivedHeaders)
    try f(server.getPort(), receivedHeaders)
    finally server.stop()
  }

  /**
   * Creates a simple HTTP server that captures headers and returns a 200 response.
   */
  def usingHeaderCaptureServer(f: (Int, scala.collection.mutable.Map[String, String]) => Unit): Unit = {
    val receivedHeaders = scala.collection.mutable.Map[String, String]()
    val server = new HeaderCaptureServer(receivedHeaders)
    try f(server.getPort(), receivedHeaders)
    finally server.stop()
  }

  private class MockProxyServer(receivedHeaders: scala.collection.mutable.Map[String, String]) extends HttpHandler {
    private val server: HttpServer =
      HttpServer.create(new InetSocketAddress(0), 0)
    server.createContext("/", this)
    server.setExecutor(null)
    server.start()

    def getPort(): Int = server.getAddress.getPort

    def stop(): Unit = server.stop(0)

    override def handle(t: HttpExchange): Unit = try {
      // Capture all headers (convert to lowercase for consistent lookup)
      val headers = t.getRequestHeaders
      headers.forEach { (key, values) =>
        if (values != null && !values.isEmpty) {
          receivedHeaders.put(key.toLowerCase, values.get(0))
        }
      }

      // Return 502 Bad Gateway (simulating a proxy that can't reach the target)
      val response = "Mock proxy - request captured"
      t.sendResponseHeaders(502, response.length)
      t.getResponseBody.write(response.getBytes())
      t.getResponseBody.close()
    } catch {
      case e: Exception =>
        e.printStackTrace()
        t.sendResponseHeaders(500, -1)
    } finally {
      t.close()
    }
  }

  private class HeaderCaptureServer(receivedHeaders: scala.collection.mutable.Map[String, String]) extends HttpHandler {
    private val server: HttpServer =
      HttpServer.create(new InetSocketAddress(0), 0)
    server.createContext("/", this)
    server.setExecutor(null)
    server.start()

    def getPort(): Int = server.getAddress.getPort

    def stop(): Unit = server.stop(0)

    override def handle(t: HttpExchange): Unit = try {
      // Capture all headers (convert to lowercase for consistent lookup)
      val headers = t.getRequestHeaders
      headers.forEach { (key, values) =>
        if (values != null && !values.isEmpty) {
          receivedHeaders.put(key.toLowerCase, values.get(0))
        }
      }

      // Return 200 OK
      val response = "OK"
      t.sendResponseHeaders(200, response.length)
      t.getResponseBody.write(response.getBytes())
      t.getResponseBody.close()
    } catch {
      case e: Exception =>
        e.printStackTrace()
        t.sendResponseHeaders(500, -1)
    } finally {
      t.close()
    }
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
