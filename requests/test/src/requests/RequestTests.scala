package requests

import utest._
import ujson._

object RequestTests extends HttpbinTestSuite {

  val tests = Tests {
    test("matchingMethodWorks") {
      val requesters = Seq(requests.delete, requests.get, requests.post, requests.put)
      val baseUrl = s"http://$localHttpbin"
      for (r <- requesters) {
        for (r2 <- requesters) {
          val res = r(s"$baseUrl/${r2.verb.toLowerCase()}", check = false)
          if (r.verb == r2.verb) assert(res.statusCode == 200)
          else assert(res.statusCode == 405)

          if (r.verb == r2.verb) {
            val res = r(s"$baseUrl/${r2.verb.toLowerCase()}")
            assert(res.statusCode == 200)
          } else {
            intercept[RequestFailedException] {
              r(s"$baseUrl/${r2.verb.toLowerCase()}")
            }
          }
        }
      }
    }

    test("params") {
      test("get") {
        // All in URL
        val res1 =
          requests.get(s"http://$localHttpbin/get?hello=world&foo=baz").text()
        assert(read(res1).obj("args") == Obj("foo" -> "baz", "hello" -> "world"))

        // All in params
        val res2 = requests.get(
          s"http://$localHttpbin/get",
          params = Map("hello" -> "world", "foo" -> "baz"),
        )
        assert(read(res2).obj("args") == Obj("foo" -> "baz", "hello" -> "world"))

        // Mixed URL and params
        val res3 = requests
          .get(
            s"http://$localHttpbin/get?hello=world",
            params = Map("foo" -> "baz"),
          )
          .text()
        assert(read(res3).obj("args") == Obj("foo" -> "baz", "hello" -> "world"))

        // Needs escaping
        val res4 = requests.get(
          s"http://$localHttpbin/get?hello=world",
          params = Map("++-- lol" -> " !@#$%"),
        )
        assert(read(res4).obj("args") == Obj("++-- lol" -> " !@#$%", "hello" -> "world"))
      }
    }

    test("multipart") {
      for (chunkedUpload <- Seq(true, false)) {
        val response = requests
          .post(
            s"http://$localHttpbin/post",
            data = MultiPart(
              MultiItem("file1", "Hello!".getBytes, "foo.txt"),
              MultiItem("file2", "Goodbye!"),
            ),
            chunkedUpload = chunkedUpload,
          )
          .text()

        assert(read(response).obj("files") == Obj("file1" -> "Hello!"))
        assert(read(response).obj("form") == Obj("file2" -> "Goodbye!"))
      }
    }

    test("cookies") {
      test("session") {
        val s = requests.Session(cookieValues = Map("hello" -> "world"))
        val res1 = s.get(s"http://$localHttpbin/cookies").text().trim
        assert(read(res1) == Obj("cookies" -> Obj("hello" -> "world")))
        s.get(s"http://$localHttpbin/cookies/set?freeform=test")
        val res2 = s.get(s"http://$localHttpbin/cookies").text().trim
        assert(read(res2) == Obj("cookies" -> Obj("freeform" -> "test", "hello" -> "world")))
      }
      test("raw") {
        val res1 = requests.get(s"http://$localHttpbin/cookies").text().trim
        assert(read(res1) == Obj("cookies" -> Obj()))
        requests.get(s"http://$localHttpbin/cookies/set?freeform=test")
        val res2 = requests.get(s"http://$localHttpbin/cookies").text().trim
        assert(read(res2) == Obj("cookies" -> Obj()))
      }
      test("space") {
        val s = requests.Session(cookieValues = Map("hello" -> "hello, world"))
        val res1 = s.get(s"http://$localHttpbin/cookies").text().trim
        assert(read(res1) == Obj("cookies" -> Obj("hello" -> "hello, world")))
        s.get(s"http://$localHttpbin/cookies/set?freeform=test+test")
        val res2 = s.get(s"http://$localHttpbin/cookies").text().trim
        assert(
          read(res2) == Obj("cookies" -> Obj("freeform" -> "test test", "hello" -> "hello, world")),
        )
      }
    }

    test("redirects") {
      test("max") {
        val res1 = requests.get(s"http://$localHttpbin/absolute-redirect/4")
        assert(res1.statusCode == 200)
        val res2 = requests.get(s"http://$localHttpbin/absolute-redirect/5")
        assert(res2.statusCode == 200)
        val res3 = requests.get(s"http://$localHttpbin/absolute-redirect/6", check = false)
        assert(res3.statusCode == 302)
        val res4 = requests.get(s"http://$localHttpbin/absolute-redirect/6", maxRedirects = 10)
        assert(res4.statusCode == 200)
      }
      test("maxRelative") {
        val res1 = requests.get(s"http://$localHttpbin/relative-redirect/4")
        assert(res1.statusCode == 200)
        val res2 = requests.get(s"http://$localHttpbin/relative-redirect/5")
        assert(res2.statusCode == 200)
        val res3 = requests.get(s"http://$localHttpbin/relative-redirect/6", check = false)
        assert(res3.statusCode == 302)
        val res4 = requests.get(s"http://$localHttpbin/relative-redirect/6", maxRedirects = 10)
        assert(res4.statusCode == 200)
      }
    }

    test("test_reproduction") {
      requests.get(s"http://$localHttpbin/status/304").text()
    }

    test("streaming") {
      val res1 = requests.get(s"http://$localHttpbin/stream/5").text()
      assert(res1.linesIterator.length == 5)
      val res2 = requests.get(s"http://$localHttpbin/stream/52").text()
      assert(res2.linesIterator.length == 52)
    }

    test("timeouts") {
      test("read") {
        intercept[TimeoutException] {
          requests.get(s"http://$localHttpbin/delay/1", readTimeout = 10)
        }
        requests.get(s"http://$localHttpbin/delay/1", readTimeout = 2000)
        intercept[TimeoutException] {
          requests.get(s"http://$localHttpbin/delay/3", readTimeout = 2000)
        }
      }
      test("connect") {
        intercept[TimeoutException] {
          // use remote httpbin.org so it needs more time to connect
          requests.get(s"https://httpbin.org/delay/1", connectTimeout = 1)
        }
      }
    }

    test("failures") {
      intercept[UnknownHostException] {
        requests.get("https://doesnt-exist-at-all.com/")
      }
      intercept[InvalidCertException] {
        requests.get("https://expired.badssl.com/")
      }
      requests.get("https://doesnt-exist.com/", verifySslCerts = false)
      intercept[java.net.MalformedURLException] {
        requests.get("://doesnt-exist.com/")
      }
    }

    test("decompress") {
      val res1 = requests.get(s"http://$localHttpbin/gzip")
      assert(read(res1.text()).obj("headers").obj("Host").str == localHttpbin)

      val res2 = requests.get(s"http://$localHttpbin/deflate")
      assert(read(res2).obj("headers").obj("Host").str == localHttpbin)

      val res3 = requests.get(s"http://$localHttpbin/gzip", autoDecompress = false)
      assert(res3.bytes.length < res1.bytes.length)

      val res4 = requests.get(s"http://$localHttpbin/deflate", autoDecompress = false)
      assert(res4.bytes.length < res2.bytes.length)

      (
        res1.bytes.length,
        res2.bytes.length,
        res3.bytes.length,
        res4.bytes.length,
      )
    }

    test("compression") {
      val res1 = requests.post(
        s"http://$localHttpbin/post",
        compress = requests.Compress.None,
        data = new RequestBlob.ByteSourceRequestBlob("Hello World"),
      )
      assert(res1.text().contains(""""Hello World""""))

      val res2 = requests.post(
        s"http://$localHttpbin/post",
        compress = requests.Compress.Gzip,
        data = new RequestBlob.ByteSourceRequestBlob("I am cow"),
      )
      assert(
        read(new String(res2.bytes))("data").toString
          .contains("data:application/octet-stream;base64,H4sIAAAAAA"),
      )

      val res3 = requests.post(
        s"http://$localHttpbin/post",
        compress = requests.Compress.Deflate,
        data = new RequestBlob.ByteSourceRequestBlob("Hear me moo"),
      )
      assert(
        read(new String(res3.bytes))(
          "data",
        ).toString == """"data:application/octet-stream;base64,eJzzSE0sUshNVcjNzwcAFokD3g=="""",
      )
    }

    test("headers") {
      test("default") {
        val res = requests.get(s"http://$localHttpbin/headers").text()
        val hs = read(res)("headers").obj
        assert(hs("User-Agent").str == "requests-scala")
        assert(hs("Accept-Encoding").str == "gzip, deflate")
        assert(hs("Accept").str == "*/*")
        test("hasNoCookie") {
          assert(!hs.contains("Cookie"))
        }
      }
    }

    test("clientCertificate") {
      val base = sys.env("MILL_TEST_RESOURCE_DIR")
      val url = "https://client.badssl.com"
      val instruction =
        "https://github.com/lihaoyi/requests-scala/blob/master/requests/test/resources/badssl.com-client.md"
      val certificateExpiredMessage =
        s"WARNING: Certificate may have expired and needs to be updated. Please check: $instruction and/or file issue"
      test("passwordProtected") {
        val res = requests.get(
          url,
          cert = Cert.implicitP12(s"$base/badssl.com-client.p12", "badssl.com"),
          check = false,
        )
        if (res.statusCode == 400)
          println(certificateExpiredMessage)
        else
          assert(res.statusCode == 200)
      }
      test("noPassword") {
        val res = requests.get(
          "https://client.badssl.com",
          cert = Cert.implicitP12(s"$base/badssl.com-client-nopass.p12"),
          check = false,
        )
        if (res.statusCode == 400)
          println(certificateExpiredMessage)
        else
          assert(res.statusCode == 200)
      }
      test("sslContext") {
        val res = requests.get(
          "https://client.badssl.com",
          sslContext = FileUtils.createSslContext(s"$base/badssl.com-client.p12", "badssl.com"),
          check = false,
        )
        if (res.statusCode == 400)
          println(certificateExpiredMessage)
        else
          assert(res.statusCode == 200)
      }
      test("noCert") {
        val res = requests.get("https://client.badssl.com", check = false)
        assert(res.statusCode == 400)
      }
    }

    test("selfSignedCertificate") {
      val res = requests.get(
        "https://self-signed.badssl.com",
        verifySslCerts = false,
      )
      assert(res.statusCode == 200)
    }

    test("gzipError") {
      val response = requests.head("https://api.github.com/users/lihaoyi")
      assert(response.statusCode == 200)
      assert(response.data.array.isEmpty)
      assert(response.headers.keySet.map(_.toLowerCase).contains("content-length"))
      assert(response.headers.keySet.map(_.toLowerCase).contains("content-type"))
    }

    /**
     * Compress with each compression mode and call server. Server expands and passes it back so we
     * can compare
     */
    test("compressionData") {
      import requests.Compress._
      val str = "I am deflater mouse"
      Seq(None, Gzip, Deflate).foreach { c =>
        ServerUtils.usingEchoServer { port =>
          val response =
            requests.post(
              s"http://localhost:$port/echo",
              compress = c,
              data = new RequestBlob.ByteSourceRequestBlob(str),
            )
          assert(str == response.data.toString)
        }
      }
    }

    // Ensure when duplicate headers are passed to requests, we only pass the last one
    // to the server. This preserves the 0.8.x behavior, and can always be overriden
    // by passing a comma-separated list of headers instead
    test("duplicateHeaders") {
      val res = requests.get(
        s"http://$localHttpbin/get",
        headers = Seq("x-y" -> "a", "x-y" -> "b"),
      )
      // make sure it's not "a,b"
      assert(ujson.read(res)("headers")("X-Y") == Str("b"))
    }
  }
}
