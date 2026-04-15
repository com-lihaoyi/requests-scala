package java.net

import scala.collection.mutable

class HttpCookie(val name: String, var value: String) extends Cloneable {

  private var _domain: String = null
  private var _path: String = null
  private var _maxAge: Long = -1
  private var _secure: Boolean = false
  private var _httpOnly: Boolean = false
  private var _version: Int = 1
  private var _comment: String = null
  private var _commentURL: String = null
  private var _discard: Boolean = false
  private var _portlist: String = null

  def getDomain(): String = _domain
  def setDomain(domain: String): Unit = _domain = domain

  def getPath(): String = _path
  def setPath(path: String): Unit = _path = path

  def getMaxAge(): Long = _maxAge
  def setMaxAge(maxAge: Long): Unit = _maxAge = maxAge

  def getSecure(): Boolean = _secure
  def setSecure(secure: Boolean): Unit = _secure = secure

  def isHttpOnly(): Boolean = _httpOnly
  def setHttpOnly(httpOnly: Boolean): Unit = _httpOnly = httpOnly

  def getVersion(): Int = _version
  def setVersion(version: Int): Unit = _version = version

  def getComment(): String = _comment
  def setComment(comment: String): Unit = _comment = comment

  def getCommentURL(): String = _commentURL
  def setCommentURL(commentURL: String): Unit = _commentURL = commentURL

  def getDiscard(): Boolean = _discard
  def setDiscard(discard: Boolean): Unit = _discard = discard

  def getPortlist(): String = _portlist
  def setPortlist(portlist: String): Unit = _portlist = portlist

  def hasExpired(): Boolean = _maxAge == 0

  override def clone(): AnyRef = {
    val c = new HttpCookie(name, value)
    c._domain = _domain
    c._path = _path
    c._maxAge = _maxAge
    c._secure = _secure
    c._httpOnly = _httpOnly
    c._version = _version
    c._comment = _comment
    c._commentURL = _commentURL
    c._discard = _discard
    c._portlist = _portlist
    c
  }

  override def toString: String = {
    if (_path != null) s"$name=$value; Path=$_path"
    else s"$name=$value"
  }

  override def equals(obj: Any): Boolean = obj match {
    case that: HttpCookie =>
      this.name.equalsIgnoreCase(that.name) &&
      (this._domain == that._domain || (this._domain != null && this._domain.equalsIgnoreCase(that._domain)))
    case _ => false
  }

  override def hashCode(): Int = {
    name.toLowerCase.hashCode * 31 + (if (_domain != null) _domain.toLowerCase.hashCode else 0)
  }
}

object HttpCookie {

  def parse(header: String): java.util.List[HttpCookie] = {
    val cookies = new java.util.ArrayList[HttpCookie]()
    if (header == null || header.isEmpty) return cookies

    val cookieStrings = header.split(";")
    for (cs <- cookieStrings) {
      val trimmed = cs.trim
      if (trimmed.nonEmpty) {
        val eqIdx = trimmed.indexOf('=')
        if (eqIdx > 0) {
          val name = trimmed.substring(0, eqIdx).trim
          var value = trimmed.substring(eqIdx + 1).trim
          if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length - 1)
          }
          if (name.nonEmpty) {
            cookies.add(new HttpCookie(name, value))
          }
        }
      }
    }
    cookies
  }
}
