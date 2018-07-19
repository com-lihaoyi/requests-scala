package requests

import utest._

object HelloTests extends TestSuite{
  val tests = Tests{
    'matchingMethodWorks - {
      val requesters = Seq(
        requests.delete,
        requests.get,
        requests.post,
        requests.put
      )

      for(protocol <- Seq("http", "https")){
        for(r <- requesters){
          for(r2 <- requesters){
            val res = r(s"$protocol://httpbin.org/${r2.verb.toLowerCase()}")
            if (r.verb == r2.verb) assert(res.statusCode == 200)
            else assert(res.statusCode == 405)
          }
        }
      }
    }
    'params - {
      'get - {
        // All in URL
        val res1 = requests.get("https://httpbin.org/get?hello=world&foo=baz").data.text
        assert(res1.contains(""""args":{"foo":"baz","hello":"world"}"""))

        // All in params
        val res2 = requests.get(
          "https://httpbin.org/get",
          params = Map("hello" -> "world", "foo" -> "baz")
        ).data.text
        assert(res2.contains(""""args":{"foo":"baz","hello":"world"}"""))

        // Mixed URL and params
        val res3 = requests.get(
          "https://httpbin.org/get?hello=world",
          params = Map("foo" -> "baz")
        ).data.text
        assert(res3.contains(""""args":{"foo":"baz","hello":"world"}"""))

        // Needs escaping
        val res4 = requests.get(
          "https://httpbin.org/get?hello=world",
          params = Map("++-- lol" -> " !@#$%")
        ).data.text
        assert(res4.contains(""""args":{"++-- lol":" !@#$%","hello":"world""""))
      }
      'post - {
        val res1 = requests.post(
          "https://httpbin.org/post",
          data = Map("hello" -> "world", "foo" -> "baz")
        ).data.text
        assert(res1.contains(""""form":{"foo":"baz","hello":"world"}"""))
      }
      'put - {
        val res1 = requests.put(
          "https://httpbin.org/put",
          data = Map("hello" -> "world", "foo" -> "baz")
        ).data.text
        assert(res1.contains(""""form":{"foo":"baz","hello":"world"}"""))
      }
    }
    'multipart - {
      val response = requests.post(
        "http://httpbin.org/post",
        data = MultiPart(
          MultiItem("file1", "Hello!".getBytes, "foo.txt"),
          MultiItem("file2", "Goodbye!")
        )
      ).data.text

      assert(response.contains(""""files":{"file1":"Hello!"},"form":{"file2":"Goodbye!"}"""))
    }
    'cookies - {

      'session - {
        val s = requests.Session(cookieValues = Map("hello" -> "world"))
        val res1 = s.get("https://httpbin.org/cookies").data.text.trim
        assert(res1 == """{"cookies":{"hello":"world"}}""")
        s.get("https://httpbin.org/cookies/set?freeform=test")
        val res2 = s.get("https://httpbin.org/cookies").data.text.trim
        assert(res2 == """{"cookies":{"freeform":"test","hello":"world"}}""")
      }
      'raw - {
        val res1 = requests.get("https://httpbin.org/cookies").data.text.trim
        assert(res1 == """{"cookies":{}}""")
        requests.get("https://httpbin.org/cookies/set?freeform=test")
        val res2 = requests.get("https://httpbin.org/cookies").data.text.trim
        assert(res2 == """{"cookies":{}}""")
      }
    }
    'redirects - {
      'max - {
        val res1 = requests.get("https://httpbin.org/absolute-redirect/4")
        assert(res1.statusCode == 200)
        val res2 = requests.get("https://httpbin.org/absolute-redirect/5")
        assert(res2.statusCode == 200)
        val res3 = requests.get("https://httpbin.org/absolute-redirect/6")
        assert(res3.statusCode == 302)
        val res4 = requests.get("https://httpbin.org/absolute-redirect/6", maxRedirects = 10)
        assert(res4.statusCode == 200)
      }
      'maxRelative - {
        val res1 = requests.get("https://httpbin.org/relative-redirect/4")
        assert(res1.statusCode == 200)
        val res2 = requests.get("https://httpbin.org/relative-redirect/5")
        assert(res2.statusCode == 200)
        val res3 = requests.get("https://httpbin.org/relative-redirect/6")
        assert(res3.statusCode == 302)
        val res4 = requests.get("https://httpbin.org/relative-redirect/6", maxRedirects = 10)
        assert(res4.statusCode == 200)
      }
    }
    'streaming - {
      val res1 = requests.get("http://httpbin.org/stream/5").data.text
      assert(res1.lines.length == 5)
      val res2 = requests.get("http://httpbin.org/stream/52").data.text
      assert(res2.lines.length == 52)
    }
    'timeouts - {
      'read - {
        intercept[TimeoutException] {
          requests.get("https://httpbin.org/delay/1", readTimeout = 10)
        }
        requests.get("https://httpbin.org/delay/1", readTimeout = 1500)
        intercept[TimeoutException] {
          requests.get("https://httpbin.org/delay/3", readTimeout = 1500)
        }
      }
      'connect - {
        intercept[TimeoutException] {
          requests.get("https://httpbin.org/delay/1", connectTimeout = 10)
        }
        requests.get("https://httpbin.org/delay/1", connectTimeout = 1500)
        requests.get("https://httpbin.org/delay/3", connectTimeout = 1500)
      }
    }
    'failures - {
      intercept[UnknownHostException]{
        requests.get("https://doesnt-exist-at-all.com/")
      }
      intercept[InvalidCertException]{
        requests.get("https://doesnt-exist.com/")
      }
      requests.get("https://doesnt-exist.com/", verifySslCerts = false)
      intercept[java.net.MalformedURLException]{
        requests.get("://doesnt-exist.com/")
      }
    }
    'decompress - {
      val res1 = requests.get("https://httpbin.org/gzip").data
      assert(res1.text.contains(""""Host":"httpbin.org""""))

      val res2 = requests.get("https://httpbin.org/deflate").data
      assert(res2.text.contains(""""Host":"httpbin.org""""))

      val res3 = requests.get("https://httpbin.org/gzip", autoDecompress = false).data
      assert(res3.bytes.length < res1.bytes.length)

      val res4 = requests.get("https://httpbin.org/deflate", autoDecompress = false).data
      assert(res4.bytes.length < res2.bytes.length)

      (res1.bytes.length, res2.bytes.length, res3.bytes.length, res4.bytes.length)
    }
    'compression - {
      val res1 = requests.get("https://httpbin.org/gzip", compress = requests.Compress.None).data
      assert(res1.text.contains(""""Host":"httpbin.org""""))

      val res2 = requests.get("https://httpbin.org/gzip", compress = requests.Compress.Gzip).data
      assert(res2.text.contains(""""Host":"httpbin.org""""))

      val res3 = requests.get("https://httpbin.org/gzip", compress = requests.Compress.Deflate).data
      assert(res3.text.contains(""""Host":"httpbin.org""""))
    }
  }
}
