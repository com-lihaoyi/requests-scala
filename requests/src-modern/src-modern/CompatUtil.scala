package requests

import java.net.http.HttpClient

private object CompatUtil {
  // For JDK 21+ and Scala Native
  @inline
  final def closeHttpClient(httpClient: HttpClient): Unit =
    httpClient.close()
}
