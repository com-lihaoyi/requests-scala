package java.net

class HttpCookie(name: String, value: String) {
  def getName = name
  def getValue = value
  def hasExpired = false
  def getDomain: String = null
  def getPath: String = null
}

object HttpCookie {
  def parse(s: String): java.util.List[HttpCookie] = {
    val parts = s.split(";")
    val first = parts(0).split("=", 2)
    if (first.length == 2) {
      java.util.Collections.singletonList(new HttpCookie(first(0).trim, first(1).trim))
    } else java.util.Collections.emptyList()
  }
}
