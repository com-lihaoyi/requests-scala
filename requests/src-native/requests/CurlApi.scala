package requests

import scala.scalanative.unsafe._

@link("curl")
@extern
object CurlApi {
  type CURL = Ptr[Byte]
  type CURLcode = Int
  type CURLoption = Int
  type CURLinfo = Int
  type curl_slist = Ptr[Byte]

  def curl_easy_init(): CURL = extern
  
  @name("curl_easy_setopt")
  def curl_easy_setopt_ptr(curl: CURL, option: CURLoption, parameter: Ptr[Byte]): CURLcode = extern
  
  @name("curl_easy_setopt")
  def curl_easy_setopt_str(curl: CURL, option: CURLoption, parameter: CString): CURLcode = extern
  
  @name("curl_easy_setopt")
  def curl_easy_setopt_long(curl: CURL, option: CURLoption, parameter: Long): CURLcode = extern

  @name("curl_easy_setopt")
  def curl_easy_setopt_func(curl: CURL, option: CURLoption, parameter: CFuncPtr): CURLcode = extern

  def curl_easy_perform(curl: CURL): CURLcode = extern
  def curl_easy_cleanup(curl: CURL): Unit = extern

  def curl_easy_getinfo(curl: CURL, info: CURLinfo, parameter: Ptr[Long]): CURLcode = extern

  def curl_slist_append(list: curl_slist, string: CString): curl_slist = extern
  def curl_slist_free_all(list: curl_slist): Unit = extern

  def curl_easy_strerror(code: CURLcode): CString = extern
}

object CurlConstants {
  val CURLE_OK = 0

  val CURLOPT_WRITEDATA = 10001
  val CURLOPT_URL = 10002
  val CURLOPT_PROXY = 10004
  val CURLOPT_USERPWD = 10005
  val CURLOPT_PROXYUSERPWD = 10006
  val CURLOPT_WRITEFUNCTION = 20011
  val CURLOPT_READFUNCTION = 20012
  val CURLOPT_TIMEOUT_MS = 155
  val CURLOPT_CONNECTTIMEOUT_MS = 156
  val CURLOPT_POSTFIELDS = 10015
  val CURLOPT_USERAGENT = 10018
  val CURLOPT_COOKIE = 10022
  val CURLOPT_HTTPHEADER = 10023
  val CURLOPT_CUSTOMREQUEST = 10036
  val CURLOPT_FOLLOWLOCATION = 52
  val CURLOPT_MAXREDIRS = 68
  val CURLOPT_SSL_VERIFYPEER = 64
  val CURLOPT_SSL_VERIFYHOST = 81
  val CURLOPT_HEADERFUNCTION = 20079
  val CURLOPT_HEADERDATA = 10029
  val CURLOPT_NOSIGNAL = 99
  val CURLOPT_POST = 47
  val CURLOPT_UPLOAD = 46
  val CURLOPT_INFILESIZE_LARGE = 30115

  val CURLINFO_RESPONSE_CODE = 2097154
}
