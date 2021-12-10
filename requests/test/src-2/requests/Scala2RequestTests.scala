package requests

import utest._
import ujson._

object Scala2RequestTests extends TestSuite{
  val tests = Tests{
    test("send"){
      requests.send("get")("https://httpbin.org/get?hello=world&foo=baz")

      // This doesn't compile right in Scala3 for some reason
      val res1 = requests.send("put")(
        "https://httpbin.org/put",
        data = Map("hello" -> "world", "foo" -> "baz"),
        chunkedUpload = true
      ).text

      assert(read(res1).obj("form") == Obj("foo" -> "baz", "hello" -> "world"))
    }
  }
}
