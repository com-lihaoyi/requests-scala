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
 * The fix reuses a shared HttpClient when possible, and properly cleans up
 * non-shared clients after each request.
 */
object ResourceLeakTests extends TestSuite {

  private def runBenchmark(
    name: String,
    requestCount: Int = 100,
    maxThreadGrowth: Int = 20
  )(makeRequest: (String, Int) => Unit): Unit = {
    ServerUtils.usingEchoServer { port =>
      val url = s"http://localhost:$port/echo"
      val initialThreadCount = Thread.activeCount()

      val start = System.currentTimeMillis()
      for (i <- 0 until requestCount) makeRequest(url, i)
      val end = System.currentTimeMillis()

      println(s"$name: $requestCount requests in ${end - start}ms")

      val finalThreadCount = Thread.activeCount()
      val threadGrowth = finalThreadCount - initialThreadCount
      assert(threadGrowth < maxThreadGrowth)
    }
  }

  val tests = Tests {
    test("warmup") {
      // Uses the shared HttpClient from the default session (requests object)
      runBenchmark("requests-scala (shared client)") { (url, i) =>
        requests.post(url, data = s"request $i")
      }
    }
    test("reusedClient") {
      // Uses the shared HttpClient from the default session (requests object)
      runBenchmark("requests-scala (shared client)") { (url, i) =>
        requests.post(url, data = s"request $i")
      }
    }

    test("reusedSessionClient") {
      // Uses a shared HttpClient from a custom Session
      val session = requests.Session()
      runBenchmark("requests-scala (session client)") { (url, i) =>
        session.post(url, data = s"request $i")
      }
    }

    test("customClient") {
      // Uses a new HttpClient per request (custom config triggers non-shared path)
      runBenchmark("requests-scala (per-request client)") { (url, i) =>
        // type ascription necessary due to bug in Scala 3.3.x that we use for compatibility,
        // it's fixed in later versions
        requests.post(url, data = (s"request": requests.RequestBlob), connectTimeout = 31337)
      }
    }

    test("rawHttpClient") {
      // Baseline: raw java.net.http.HttpClient with a shared client
      val client = HttpClient.newHttpClient()
      runBenchmark("raw HttpClient (shared)") { (url, i) =>
        val request = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .POST(HttpRequest.BodyPublishers.ofString(s"request $i"))
          .build()
        client.send(request, HttpResponse.BodyHandlers.ofString())
      }
    }
  }
}
