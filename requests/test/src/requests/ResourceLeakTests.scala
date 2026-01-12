package requests

import utest._
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}

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
        val requestCount = 10000
        val start = System.currentTimeMillis()
        for (i <- 0 until requestCount) requests.post(url, data = s"request $i")
        val end = System.currentTimeMillis()
        println(s"requests-scala: $requestCount requests in ${end - start}ms")

        val finalThreadCount = Thread.activeCount()
        val threadGrowth = finalThreadCount - initialThreadCount

        // Thread count should stay bounded - no significant leak
        assert(threadGrowth < 20)
      }
    }

    test("rawHttpClientPerformance") {
      // Baseline comparison using raw java.net.http.HttpClient with a shared client
      ServerUtils.usingEchoServer { port =>
        val url = s"http://localhost:$port/echo"

        val initialThreadCount = Thread.activeCount()

        val requestCount = 10000
        val client = HttpClient.newHttpClient()
        val start = System.currentTimeMillis()
        for (i <- 0 until requestCount) {
          val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .POST(HttpRequest.BodyPublishers.ofString(s"request $i"))
            .build()
          client.send(request, HttpResponse.BodyHandlers.ofString())
        }
        val end = System.currentTimeMillis()
        println(s"raw HttpClient (shared): $requestCount requests in ${end - start}ms")

        val finalThreadCount = Thread.activeCount()
        val threadGrowth = finalThreadCount - initialThreadCount
        assert(threadGrowth < 20)
      }
    }
  }
}
