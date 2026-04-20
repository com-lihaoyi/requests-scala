package requests

import java.util.concurrent.ThreadLocalRandom
import java.net.BindException

import utest.TestSuite

abstract class HttpbinTestSuite extends TestSuite {
  // `GO_HTTPBIN_EXE` is defined in build.mill to get the path to go-httpbin cli
  val httpbinPath = sys.env("GO_HTTPBIN_EXE")

  private val host = "127.0.0.1"
  private var port = ThreadLocalRandom.current().nextInt(10000, 65535).toString()

  private val httpbinProcess =
    try {
      val process =
        os.proc(httpbinPath, "-host", host, "--port", port, "-log-level", "ERROR").spawn()
      if (process.waitFor(500L)) {
        if (process.stderr.lines().mkString.contains("address already in use"))
          throw new BindException("Port is already in use")
        else
          throw new IllegalThreadStateException("`httpbin` process failed to start")
      }
      process
    } catch {
      case exc: BindException => {
        port = ThreadLocalRandom.current().nextInt(10000, 65535).toString()
        os.proc(httpbinPath, "-host", "127.0.0.1", "--port", port).spawn()
      }
    }

  val localHttpbin: String = s"${host}:${port}"

  override def utestAfterAll(): Unit = {
    httpbinProcess.destroy()
  }
}
