package requests

import java.io._
import java.util.zip.{GZIPInputStream, InflaterInputStream}

import scala.annotation.tailrec
import scala.collection.mutable.StringBuilder

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s._
import org.http4s.HttpRoutes
import org.http4s.dsl.io._
import org.http4s.ember.server.EmberServerBuilder

object ServerUtils {
  def usingEchoServer(f: Int => Unit): Unit = {
    val server = new EchoServer
    try f(server.getPort())
    finally server.stop()
  }

  private class EchoServer {
    private val ember = EmberServerBuilder
      .default[IO]
      .withHost(ipv4"127.0.0.1")
      .withPort(port"0")
      .withHttpApp(echoRoutes.orNotFound)
      .build
      .allocated
      .unsafeRunSync()
    private val server = ember._1
    private val release = ember._2

    def getPort(): Int = server.addressIp4s.port.value

    def stop(): Unit = release.unsafeRunSync()
  }

  private val echoRoutes = HttpRoutes.of[IO] {
    case req @ POST -> Root / "echo" =>
      val c: Compress =
        req.headers.headers
          .find(_.name.toString.equalsIgnoreCase("content-encoding"))
          .map(_.value) match {
          case Some("gzip")    => Compress.Gzip
          case Some("deflate") => Compress.Deflate
          case _               => Compress.None
        }
      req.body.compile.to(Array).flatMap { compressed =>
        IO(new Plumper(c).decompress(new ByteArrayInputStream(compressed))).flatMap(Ok(_))
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
        case Compress.None    => is
        case Compress.Gzip    => new GZIPInputStream(is)
        case Compress.Deflate => new InflaterInputStream(is)
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
