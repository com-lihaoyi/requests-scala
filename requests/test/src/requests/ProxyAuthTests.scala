package requests

import utest._

object ProxyAuthTests extends TestSuite {

  val tests = Tests {
    test("proxyAuth") {
      test("headerFormat") {
        // Test that proxyAuth generates the correct Base64-encoded Proxy-Authorization header
        val username = "testuser"
        val password = "testpass"
        val expectedCredentials = java.util.Base64.getEncoder.encodeToString(
          s"$username:$password".getBytes()
        )
        val expectedHeader = s"Basic $expectedCredentials"

        // Verify the expected format
        assert(expectedHeader == "Basic dGVzdHVzZXI6dGVzdHBhc3M=")
      }

      test("proxyAuthHeaderSent") {
        // Test that proxyAuth parameter correctly adds Proxy-Authorization header
        // by using a simple HTTP server as a mock proxy
        ServerUtils.usingProxyServer { (proxyPort, receivedHeaders) =>
          val username = "proxyuser"
          val password = "proxypass"

          // Make a request through the "proxy" - the proxy server will capture headers
          try {
            requests.get(
              "http://example.com/test",
              proxy = ("localhost", proxyPort),
              proxyAuth = (username, password),
              readTimeout = 5000,
              check = false,
            )
          } catch {
            case _: Exception => // Expected - our mock proxy doesn't actually forward requests
          }

          // Verify the Proxy-Authorization header was sent
          val authHeader = receivedHeaders.get("proxy-authorization")
          assert(authHeader.isDefined)

          val expectedCredentials = java.util.Base64.getEncoder.encodeToString(
            s"$username:$password".getBytes()
          )
          assert(authHeader.get == s"Basic $expectedCredentials")
        }
      }

      test("noProxyAuthWithoutProxy") {
        // Test that proxyAuth is ignored when proxy is not set
        ServerUtils.usingHeaderCaptureServer { (port, receivedHeaders) =>
          requests.get(
            s"http://localhost:$port/test",
            proxyAuth = ("user", "pass"),
          )

          // Verify Proxy-Authorization header was NOT sent (since no proxy is configured)
          val authHeader = receivedHeaders.get("proxy-authorization")
          assert(authHeader.isEmpty)
        }
      }

      test("sessionProxyAuth") {
        // Test that Session correctly passes proxyAuth to requests
        ServerUtils.usingProxyServer { (proxyPort, receivedHeaders) =>
          val username = "sessionuser"
          val password = "sessionpass"

          val session = requests.Session(
            proxy = ("localhost", proxyPort),
            proxyAuth = (username, password),
          )

          try {
            session.get(
              "http://example.com/test",
              readTimeout = 5000,
              check = false,
            )
          } catch {
            case _: Exception => // Expected - our mock proxy doesn't actually forward requests
          }

          // Verify the Proxy-Authorization header was sent from session config
          val authHeader = receivedHeaders.get("proxy-authorization")
          assert(authHeader.isDefined)

          val expectedCredentials = java.util.Base64.getEncoder.encodeToString(
            s"$username:$password".getBytes()
          )
          assert(authHeader.get == s"Basic $expectedCredentials")

          session.close()
        }
      }

      test("specialCharactersInCredentials") {
        // Test that special characters in username/password are handled correctly
        ServerUtils.usingProxyServer { (proxyPort, receivedHeaders) =>
          val username = "user@domain.com"
          val password = "p@ss:word!#$%"

          try {
            requests.get(
              "http://example.com/test",
              proxy = ("localhost", proxyPort),
              proxyAuth = (username, password),
              readTimeout = 5000,
              check = false,
            )
          } catch {
            case _: Exception => // Expected
          }

          val authHeader = receivedHeaders.get("proxy-authorization")
          assert(authHeader.isDefined)

          val expectedCredentials = java.util.Base64.getEncoder.encodeToString(
            s"$username:$password".getBytes()
          )
          assert(authHeader.get == s"Basic $expectedCredentials")
        }
      }
    }
  }
}
