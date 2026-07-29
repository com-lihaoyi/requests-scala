package requests

/**
 * Scala-Native implementation of TransportFactory.
 * Creates CurlTransport wrapping libcurl.
 */
object TransportFactory extends _root_.requests.TransportFactory {
  def create(): Transport = new CurlTransport()
}
