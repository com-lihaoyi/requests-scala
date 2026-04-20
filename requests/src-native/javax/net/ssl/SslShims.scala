package javax.net.ssl

class SSLContext {
  def getSocketFactory(): SSLSocketFactory = new SSLSocketFactory
  def init(km: Array[KeyManager], tm: Array[TrustManager], sr: java.security.SecureRandom): Unit = ()
}
object SSLContext {
  def getDefault: SSLContext = new SSLContext
  def getInstance(s: String) = new SSLContext
}
class TrustManager
class X509TrustManager extends TrustManager
class KeyManager
class SSLSocketFactory
