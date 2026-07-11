package requests

import java.util.concurrent.{ThreadLocalRandom, Phaser}
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.net.BindException

import utest.TestSuite

abstract class HttpbinTestSuite extends TestSuite {
  // `GO_HTTPBIN_EXE` is defined in build.mill to get the path to go-httpbin cli
  val httpbinPath = sys.env("GO_HTTPBIN_EXE")

  private val host = "127.0.0.1"
  private var port = ThreadLocalRandom.current().nextInt(10000, 65535).toString()

  private val httpbinProcess =
    try {
      val phaser = new Phaser(1)
      @volatile var portNotAvailable = false
      val process =
        os.proc(httpbinPath, "-host", host, "--port", port, "-log-level", "ERROR")
          .spawn(
            stderr = os.ProcessOutput.Readlines(lines => {
              if (lines.contains("bind: address already in use"))
                portNotAvailable = true
              System.err.println("[httpbin stderr] " + lines)
              if (!phaser.isTerminated()) phaser.arrive()
            }),
          )
      if (process.waitFor(500L)) {
        phaser.awaitAdvanceInterruptibly(0, 1000L, MILLISECONDS)
        phaser.forceTermination()
        if (portNotAvailable)
          throw new BindException("Port is already in use")
        else
          throw new IllegalStateException("`httpbin` process failed to start")
      } else {
        phaser.forceTermination()
      }
      process
    } catch {
      case exc: BindException => {
        port = ThreadLocalRandom.current().nextInt(10000, 65535).toString()
        val retriedProc =
          os.proc(httpbinPath, "-host", "127.0.0.1", "--port", port, "-log-level", "ERROR").spawn()
        if (retriedProc.waitFor(500L)) {
          throw new IllegalStateException("`httpbin` process failed to start")
        }
        retriedProc
      }

    }

  val localHttpbin: String = s"${host}:${port}"

  override def utestAfterAll(): Unit = {
    if (httpbinProcess.isAlive()) httpbinProcess.destroy()
  }
}
