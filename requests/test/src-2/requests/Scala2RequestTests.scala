package requests

import utest._
import ujson._

object Scala2RequestTests extends TestSuite{
  val tests = Tests{

    test("params"){

      test("post"){
        for(chunkedUpload <- Seq(true, false)) {
          val res1 = requests.post(
            "https://httpbin.org/post",
            data = Map("hello" -> "world", "foo" -> "baz"),
            chunkedUpload = chunkedUpload
          ).text()
          assert(read(res1).obj("form") == Obj("foo" -> "baz", "hello" -> "world"))
        }
      }
      test("put") {
        for (chunkedUpload <- Seq(true, false)) {
          val res1 = requests.put(
            "https://httpbin.org/put",
            data = Map("hello" -> "world", "foo" -> "baz"),
            chunkedUpload = chunkedUpload
          ).text()
          assert(read(res1).obj("form") == Obj("foo" -> "baz", "hello" -> "world"))
        }
      }
      test("send"){
        requests.send("get")("https://httpbin.org/get?hello=world&foo=baz")

        val res1 = requests.send("put")(
          "https://httpbin.org/put",
          data = Map("hello" -> "world", "foo" -> "baz"),
          chunkedUpload = true
        ).text

        assert(read(res1).obj("form") == Obj("foo" -> "baz", "hello" -> "world"))
      }
    }
  }
}
