package requests

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

private[requests] object libcurl {

  final val CURLOPT_URL = 10002
  final val CURLOPT_WRITEFUNCTION = 10011
  final val CURLOPT_POSTFIELDS = 10015
  final val CURLOPT_USERAGENT = 10018
  final val CURLOPT_HTTPHEADER = 10023
  final val CURLOPT_HEADERFUNCTION = 10079
  final val CURLOPT_POSTFIELDSIZE = 60
  final val CURLOPT_FOLLOWLOCATION = 52
  final val CURLOPT_CONNECTTIMEOUT_MS = 156
  final val CURLOPT_TIMEOUT_MS = 155
  final val CURLOPT_PROXY = 10004
  final val CURLOPT_SSL_VERIFYPEER = 64
  final val CURLOPT_SSL_VERIFYHOST = 81
  final val CURLOPT_HTTPGET = 80
  final val CURLOPT_POST = 47
  final val CURLOPT_NOBODY = 44
  final val CURLOPT_CUSTOMREQUEST = 10036

  final val CURLINFO_RESPONSE_CODE = 0x100022

  final val CURLE_OK = 0
  final val CURLE_OPERATION_TIMEDOUT = 28
  final val CURLE_SSL_CONNECT_ERROR = 35
  final val CURLE_COULDNT_RESOLVE_HOST = 6
  final val CURLE_COULDNT_CONNECT = 7
  final val CURLE_SSL_CERTPROBLEM = 58
  final val CURLE_SSL_CIPHER = 59
  final val CURLE_PEER_FAILED_VERIFICATION = 60

  type CurlWriteCallback = CFuncPtr4[Ptr[Byte], CSize, CSize, Ptr[Byte], CSize]
  type CurlHeaderCallback = CFuncPtr4[Ptr[Byte], CSize, CSize, Ptr[Byte], CSize]

  @name("curl_easy_init")
  def curl_easy_init(): Ptr[Byte] = extern

  @name("curl_easy_setopt")
  def curl_easy_setopt_long(curl: Ptr[Byte], option: CInt, value: CLong): CInt = extern

  @name("curl_easy_setopt")
  def curl_easy_setopt_string(curl: Ptr[Byte], option: CInt, value: CString): CInt = extern

  @name("curl_easy_setopt")
  def curl_easy_setopt_ptr(curl: Ptr[Byte], option: CInt, value: Ptr[Byte]): CInt = extern

  @name("curl_easy_setopt")
  def curl_easy_setopt_write_cb(
      curl: Ptr[Byte],
      option: CInt,
      cb: CurlWriteCallback,
  ): CInt = extern

  @name("curl_easy_setopt")
  def curl_easy_setopt_header_cb(
      curl: Ptr[Byte],
      option: CInt,
      cb: CurlHeaderCallback,
  ): CInt = extern

  @name("curl_easy_perform")
  def curl_easy_perform(curl: Ptr[Byte]): CInt = extern

  @name("curl_easy_cleanup")
  def curl_easy_cleanup(curl: Ptr[Byte]): Unit = extern

  @name("curl_easy_getinfo")
  def curl_easy_getinfo_long(
      curl: Ptr[Byte],
      info: CUnsignedInt,
      out: Ptr[CLong],
  ): CInt = extern

  @name("curl_easy_strerror")
  def curl_easy_strerror(code: CInt): CString = extern

  @name("curl_slist_append")
  def curl_slist_append(list: Ptr[Byte], string: CString): Ptr[Byte] = extern

  @name("curl_slist_free_all")
  def curl_slist_free_all(list: Ptr[Byte]): Unit = extern

  @name("curl_global_init")
  def curl_global_init(flags: CLong): CInt = extern

  @name("curl_global_cleanup")
  def curl_global_cleanup(): Unit = extern
}
