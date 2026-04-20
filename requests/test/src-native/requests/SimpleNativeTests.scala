package requests

import utest._

object SimpleNativeTests extends TestSuite {
  val tests = Tests {
    test("simple get") {
      val resp = requests.get("https://httpbin.org/get")
      assert(resp.statusCode == 200)
      assert(resp.text.contains("httpbin.org"))
    }
    test("simple post") {
      val resp = requests.post("https://httpbin.org/post", data = Map("hello" -> "world"))
      assert(resp.statusCode == 200)
      assert(resp.text.contains("world"))
    }
  }
}
