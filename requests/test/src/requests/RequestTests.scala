package requests

import utest._
import ujson._

object RequestTests extends TestSuite{
  val tests = Tests{
    test("matchingMethodWorks"){
      val requesters = Seq(
        requests.delete,
        requests.get,
        requests.post,
        requests.put
      )

      for(protocol <- Seq("http", "https")){
        for(r <- requesters){
          for(r2 <- requesters){
            val res = r(s"$protocol://httpbin.org/${r2.verb.toLowerCase()}", check = false)
            if (r.verb == r2.verb) assert(res.statusCode == 200)
            else assert(res.statusCode == 405)

            if (r.verb == r2.verb){
              val res = r(s"$protocol://httpbin.org/${r2.verb.toLowerCase()}")
              assert(res.statusCode == 200)
            }else intercept[RequestFailedException]{
              r(s"$protocol://httpbin.org/${r2.verb.toLowerCase()}")
            }
          }
        }
      }
    }
    test("params"){
      test("get"){
        // All in URL
        val res1 = requests.get("https://httpbin.org/get?hello=world&foo=baz").text
        assert(read(res1).obj("args") == Obj("foo" -> "baz", "hello" -> "world"))

        // All in params
        val res2 = requests.get(
          "https://httpbin.org/get",
          params = Map("hello" -> "world", "foo" -> "baz")
        ).text
        assert(read(res2).obj("args") == Obj("foo" -> "baz", "hello" -> "world"))

        // Mixed URL and params
        val res3 = requests.get(
          "https://httpbin.org/get?hello=world",
          params = Map("foo" -> "baz")
        ).text
        assert(read(res3).obj("args") == Obj("foo" -> "baz", "hello" -> "world"))

        // Needs escaping
        val res4 = requests.get(
          "https://httpbin.org/get?hello=world",
          params = Map("++-- lol" -> " !@#$%")
        ).text
        assert(read(res4).obj("args") == Obj("++-- lol" -> " !@#$%", "hello" -> "world"))
      }
      test("post"){
        for(chunkedUpload <- Seq(true, false)) {
          val res1 = requests.post(
            "https://httpbin.org/post",
            data = Map("hello" -> "world", "foo" -> "baz"),
            chunkedUpload = chunkedUpload
          ).text
          assert(read(res1).obj("form") == Obj("foo" -> "baz", "hello" -> "world"))
        }
      }
      test("put") {
        for (chunkedUpload <- Seq(true, false)) {
          val res1 = requests.put(
            "https://httpbin.org/put",
            data = Map("hello" -> "world", "foo" -> "baz"),
            chunkedUpload = chunkedUpload
          ).text
          assert(read(res1).obj("form") == Obj("foo" -> "baz", "hello" -> "world"))
        }
      }
    }
    test("multipart"){
      for(chunkedUpload <- Seq(true, false)) {
        val response = requests.post(
          "http://httpbin.org/post",
          data = MultiPart(
            MultiItem("file1", "Hello!".getBytes, "foo.txt"),
            MultiItem("file2", "Goodbye!")
          ),
          chunkedUpload = chunkedUpload
        ).text

        assert(read(response).obj("files") == Obj("file1" -> "Hello!"))
        assert(read(response).obj("form") == Obj("file2" -> "Goodbye!"))
      }
    }
    test("cookies"){

      test("session"){
        val s = requests.Session(cookieValues = Map("hello" -> "world"))
        val res1 = s.get("https://httpbin.org/cookies").text.trim
        assert(read(res1) == Obj("cookies" -> Obj("hello" -> "world")))
        s.get("https://httpbin.org/cookies/set?freeform=test")
        val res2 = s.get("https://httpbin.org/cookies").text.trim
        assert(read(res2) == Obj("cookies" -> Obj("freeform" -> "test", "hello" -> "world")))
      }
      test("raw"){
        val res1 = requests.get("https://httpbin.org/cookies").text.trim
        assert(read(res1) == Obj("cookies" -> Obj()))
        requests.get("https://httpbin.org/cookies/set?freeform=test")
        val res2 = requests.get("https://httpbin.org/cookies").text.trim
        assert(read(res2) == Obj("cookies" -> Obj()))
      }
    }
    test("redirects"){
      test("max"){
        val res1 = requests.get("https://httpbin.org/absolute-redirect/4")
        assert(res1.statusCode == 200)
        val res2 = requests.get("https://httpbin.org/absolute-redirect/5")
        assert(res2.statusCode == 200)
        val res3 = requests.get("https://httpbin.org/absolute-redirect/6", check = false)
        assert(res3.statusCode == 302)
        val res4 = requests.get("https://httpbin.org/absolute-redirect/6", maxRedirects = 10)
        assert(res4.statusCode == 200)
      }
      test("maxRelative"){
        val res1 = requests.get("https://httpbin.org/relative-redirect/4")
        assert(res1.statusCode == 200)
        val res2 = requests.get("https://httpbin.org/relative-redirect/5")
        assert(res2.statusCode == 200)
        val res3 = requests.get("https://httpbin.org/relative-redirect/6", check = false)
        assert(res3.statusCode == 302)
        val res4 = requests.get("https://httpbin.org/relative-redirect/6", maxRedirects = 10)
        assert(res4.statusCode == 200)
      }
    }
    test("streaming"){
      val res1 = requests.get("http://httpbin.org/stream/5").text
      assert(res1.linesIterator.length == 5)
      val res2 = requests.get("http://httpbin.org/stream/52").text
      assert(res2.linesIterator.length == 52)
    }
    test("timeouts"){
      test("read"){
        intercept[TimeoutException] {
          requests.get("https://httpbin.org/delay/1", readTimeout = 10)
        }
        requests.get("https://httpbin.org/delay/1", readTimeout = 2000)
        intercept[TimeoutException] {
          requests.get("https://httpbin.org/delay/3", readTimeout = 2000)
        }
      }
      test("connect"){
        intercept[TimeoutException] {
          requests.get("https://httpbin.org/delay/1", connectTimeout = 10)
        }
        requests.get("https://httpbin.org/delay/1", connectTimeout = 1500)
        requests.get("https://httpbin.org/delay/3", connectTimeout = 1500)
      }
    }
    test("failures"){
      intercept[UnknownHostException]{
        requests.get("https://doesnt-exist-at-all.com/")
      }
      intercept[InvalidCertException]{
        requests.get("https://expired.badssl.com/")
      }
      requests.get("https://doesnt-exist.com/", verifySslCerts = false)
      intercept[java.net.MalformedURLException]{
        requests.get("://doesnt-exist.com/")
      }
    }
    test("decompress"){
      val res1 = requests.get("https://httpbin.org/gzip")
      assert(read(res1.text).obj("headers").obj("Host").str == "httpbin.org")

      val res2 = requests.get("https://httpbin.org/deflate")
      assert(read(res2.text).obj("headers").obj("Host").str == "httpbin.org")

      val res3 = requests.get("https://httpbin.org/gzip", autoDecompress = false)
      assert(res3.bytes.length < res1.bytes.length)

      val res4 = requests.get("https://httpbin.org/deflate", autoDecompress = false)
      assert(res4.bytes.length < res2.bytes.length)

      (res1.bytes.length, res2.bytes.length, res3.bytes.length, res4.bytes.length)
    }
    test("compression"){
      val res1 = requests.post(
        "https://httpbin.org/post",
        compress = requests.Compress.None,
        data = "Hello World"
      )
      assert(res1.text.contains(""""Hello World""""))

      val res2 = requests.post(
        "https://httpbin.org/post",
        compress = requests.Compress.Gzip,
        data = "I am cow"
      )
      assert(res2.text.contains("data:application/octet-stream;base64,H4sIAAAAAAAAAA=="))

      val res3 = requests.post(
        "https://httpbin.org/post",
        compress = requests.Compress.Deflate,
        data = "Hear me moo"
      )
      assert(res3.text.contains("data:application/octet-stream;base64,eJw="))
      res3.text
    }
    test("headers"){
      test("default"){
        val res = requests.get("https://httpbin.org/headers").text
        val hs = read(res)("headers").obj
        assert(hs("User-Agent").str == "requests-scala")
        assert(hs("Accept-Encoding").str == "gzip, deflate")
        assert(hs("Pragma").str == "no-cache")
        assert(hs("Accept").str == "*/*")
        test("hasNoCookie"){
          assert(hs.get("Cookie").isEmpty)
        }
      }
    }
    test("clientCertificate"){
      val base = "./requests/test/resources"
      val instruction = "https://github.com/lihaoyi/requests-scala/blob/master/requests/test/resources/badssl.com-client.md"
      test("passwordProtected"){
        val res = requests.get(
          "https://client.badssl.com",
          cert = (s"$base/badssl.com-client.p12", "badssl.com"),
          check = false
        )
        if (res.statusCode == 400)
          println(s"WARNING: Certificate may have expired and needs to be updated. Please check: $instruction and/or file issue")
        else
          assert(res.statusCode == 200)
      }
      test("noPassword"){
        val res = requests.get(
          "https://client.badssl.com",
          cert = s"$base/badssl.com-client-nopass.p12",
          check = false
        )
        if (res.statusCode == 400)
          println(s"WARNING: Certificate may have expired and needs to be updated. Please check: $instruction and/or file issue")
        else
          assert(res.statusCode == 200)
      }
      test("noCert"){
        val res = requests.get(
          "https://client.badssl.com",
          check = false
        )
        assert(res.statusCode == 400)
      }
    }
    test("selfSignedCertificate"){
      val res = requests.get(
        "https://self-signed.badssl.com",
        verifySslCerts = false
      )
      assert(res.statusCode == 200)
    }
  }
}
