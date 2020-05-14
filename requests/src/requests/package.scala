import java.net.HttpCookie

import javax.net.ssl.SSLContext

import scala.collection.mutable

package object requests extends _root_.requests.BaseSession {
  def cookies = mutable.Map.empty[String, HttpCookie]

  val headers = BaseSession.defaultHeaders

  def auth = RequestAuth.Empty

  def proxy = null

  def cert: Cert = null

  def sslContext: SSLContext = null

  def maxRedirects: Int = 5

  def persistCookies = false

  def readTimeout: Int = 10 * 1000

  def connectTimeout: Int = 10 * 1000

  def verifySslCerts: Boolean = true

  def autoDecompress: Boolean = true

  def compress: Compress = Compress.None

  def chunkedUpload: Boolean = false

  def check: Boolean = true
}