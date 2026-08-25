package requests

import utest._

object ReadTimeoutTests extends TestSuite {
  val tests = Tests {
    test("readBodyStall") {
      ServerUtils.usingStallServer() { port =>
        val start = System.nanoTime()
        intercept[TimeoutException] {
          requests.get(s"http://127.0.0.1:$port/stall", readTimeout = 500)
        }
        val elapsedMs = (System.nanoTime() - start) / 1000000
        assert(elapsedMs >= 400)
        assert(elapsedMs < 3000)
      }
    }
  }
}
