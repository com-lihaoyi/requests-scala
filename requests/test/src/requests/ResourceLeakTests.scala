package requests

import utest._

/**
 * Tests for resource/thread leaks when making many HTTP requests.
 *
 * The issue was that each request created a new HttpClient with its own
 * internal executor/thread pool that was never shut down, leading to
 * thread leaks and eventual resource exhaustion.
 *
 * The fix creates an executor for each request, tracks spawned threads,
 * and joins them at the end of the request to ensure cleanup.
 */
object ResourceLeakTests extends TestSuite {

  val tests = Tests {
    test("noThreadLeakWithManyRequests") {
      // This test verifies that making many requests does not leak threads.
      // Previously, each request created an HttpClient with a thread pool
      // that was never shut down.
      ServerUtils.usingEchoServer { port =>
        val url = s"http://localhost:$port/echo"

        // Get initial thread count
        val initialThreadCount = Thread.activeCount()

        // Make many requests - each should clean up its threads
        val requestCount = 100
        for (i <- 0 until requestCount) {
          requests.post(url, data = s"request $i")
        }

        val finalThreadCount = Thread.activeCount()
        val threadGrowth = finalThreadCount - initialThreadCount

        // Thread count should stay bounded - no significant leak
        assert(threadGrowth < 20)
      }
    }
  }
}
