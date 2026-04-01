package requests

import com.dimafeng.testcontainers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import utest._

abstract class HttpbinTestSuite extends TestSuite {
  // CI fallback: use HTTPBIN_URL env var (set by GitHub Actions service container)
  // If not set, fall back to testcontainers (for local development)
  private val useTestcontainers: Boolean = sys.env.get("HTTPBIN_URL").isEmpty

  private val (container, localHttpbinHost) = if (useTestcontainers) {
    val containerDef = GenericContainer.Def(
      "kennethreitz/httpbin",
      exposedPorts = Seq(80),
      waitStrategy = Wait.forHttp("/"),
    )
    val c = containerDef.start()
    val host = s"${c.containerIpAddress}:${c.mappedPort(80)}"
    (Some(c), host)
  } else {
    val httpbinUrl = sys.env("HTTPBIN_URL")
    (None, httpbinUrl.replace("http://", "").replace("https://", ""))
  }

  val localHttpbin: String = localHttpbinHost

  override def utestAfterAll(): Unit = {
    container.foreach(_.stop())
  }
}
