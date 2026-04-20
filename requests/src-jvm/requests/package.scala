package object requests extends requests.RequesterPackageObject {
  lazy val executor: java.util.concurrent.ExecutorService = Platform.createExecutor()
  lazy val sharedHttpClient: java.net.http.HttpClient = Platform.buildHttpClient(
    proxy, cert, sslContext, verifySslCerts, connectTimeout, executor
  )
  def close(): Unit = {
    Platform.closeHttpClient(sharedHttpClient)
    executor.shutdown()
  }
}
