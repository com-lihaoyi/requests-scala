package requests

import com.dimafeng.testcontainers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import utest._

abstract class HttpbinTestSuite extends TestSuite {

  private val containerDef = GenericContainer.Def(
    "kennethreitz/httpbin",
    exposedPorts = Seq(80),
    waitStrategy = Wait.forHttp("/")
  )
  private val container = containerDef.start()

  val localHttpbin: String = s"${container.containerIpAddress}:${container.mappedPort(80)}"

  override def utestAfterAll(): Unit = {
    container.stop()
  }
}
