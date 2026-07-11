package requests

import java.net.http.HttpClient

private object CompatUtil {
  // Java < 21: use reflection to access internal selectorManager and close its selector
  // HttpClient.newBuilder().build() returns HttpClientFacade which wraps HttpClientImpl
  @inline
  final def closeHttpClient(httpClient: HttpClient): Unit =
    try {
      val facadeClass = httpClient.getClass()
      val implField = facadeClass.getDeclaredField("impl")
      implField.setAccessible(true)
      val impl = implField.get(httpClient)
      val selectorManagerField = impl.getClass.getDeclaredField("selmgr")
      selectorManagerField.setAccessible(true)
      val selectorManager = selectorManagerField.get(impl)
      // SelectorManager has a 'selector' field we can close
      val selectorField = selectorManager.getClass.getDeclaredField("selector")
      selectorField.setAccessible(true)
      val selector = selectorField.get(selectorManager)
      val closeMethod = selector.getClass.getMethod("close")
      closeMethod.invoke(selector)
    } catch {
      case _: Exception =>
        System.err.println(
          "requests: Unable to close HttpClient SelectorManager thread. " +
            "To fix thread leaks on Java <21, add JVM arg: " +
            "--add-opens java.net.http/jdk.internal.net.http=ALL-UNNAMED",
        )
    }

}
