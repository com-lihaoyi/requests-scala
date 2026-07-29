package requests

import scala.scalanative.unsafe._
import scala.scalanative.unsigned._

/**
 * Minimal libcurl @extern bindings for Scala-Native.
 * Only the curl_easy API is needed for basic HTTP requests.
 */
@extern
object Curl {
  // Curl handle type
  type CURL
  type CURLcode = CInt
  type CURLoption = CInt

  // Callback types
  type curl_write_callback = CFuncPtr3[Ptr[Byte], CSize, CSize, Ptr[Byte], CSize]
  type curl_header_callback = CFuncPtr3[Ptr[Byte], CSize, CSize, Ptr[Byte], CSize]
  type curl_debug_callback = CFuncPtr4[CURLINFO, Ptr[Byte], CSize, Ptr[Byte], CInt]

  // Options
  final val CURLOPT_URL = 10002
  final val CURLOPT_WRITEFUNCTION = 20011
  final val CURLOPT_WRITEDATA = 10001
  final val CURLOPT_HEADERFUNCTION = 20079
  final val CURLOPT_HEADERDATA = 10029
  final val CURLOPT_HTTPHEADER = 10023
  final val CURLOPT_CUSTOMREQUEST = 10036
  final val CURLOPT_POSTFIELDS = 10015
  final val CURLOPT_POSTFIELDSIZE = 60
  final val CURLOPT_TIMEOUT_MS = 155
  final val CURLOPT_CONNECTTIMEOUT_MS = 156
  final val CURLOPT_FOLLOWLOCATION = 52
  final val CURLOPT_SSL_VERIFYPEER = 64
  final val CURLOPT_SSL_VERIFYHOST = 81
  final val CURLOPT_PROXY = 10004
  final val CURLOPT_PROXYPORT = 59
  final val CURLOPT_VERBOSE = 41
  final val CURLOPT_NOBODY = 44
  final val CURLOPT_USERAGENT = 10018

  // Info
  type CURLINFO = CInt
  final val CURLINFO_RESPONSE_CODE = 2097154
  final val CURLINFO_TOTAL_TIME = 3145731

  // Return codes
  final val CURLE_OK = 0
  final val CURLE_UNSUPPORTED_PROTOCOL = 1
  final val CURLE_URL_MALFORMAT = 3
  final val CURLE_COULDNT_RESOLVE_HOST = 6
  final val CURLE_COULDNT_CONNECT = 7
  final val CURLE_OPERATION_TIMEDOUT = 28
  final val CURLE_SSL_CONNECT_ERROR = 35

  // Functions
  def curl_easy_init(): Ptr[CURL] = extern
  def curl_easy_cleanup(curl: Ptr[CURL]): Unit = extern
  def curl_easy_setopt(curl: Ptr[CURL], option: CURLoption, parameter: CVarArg): CURLcode = extern
  def curl_easy_perform(curl: Ptr[CURL]): CURLcode = extern
  def curl_easy_getinfo(curl: Ptr[CURL], info: CURLINFO, parameter: CVarArg): CURLcode = extern
  def curl_easy_strerror(code: CURLcode): CString = extern
  def curl_slist_append(list: Ptr[CurlSlist], string: CString): Ptr[CurlSlist] = extern
  def curl_slist_free_all(list: Ptr[CurlSlist]): Unit = extern
}

/**
 * Opaque type for curl_slist linked list used for custom headers.
 */
type CurlSlist = Any

/**
 * Simple error message lookup for curl error codes.
 */
object CurlError {
  def message(code: Int): String = Zone { implicit z =>
    fromCString(Curl.curl_easy_strerror(code))
  }
}
