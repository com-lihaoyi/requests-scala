package java.net

class URL(s: String) {
  def this(base: URL, relative: String) = this(base.toString + relative)
  def getQuery: String = null
  def getHost: String = {
    val start = s.indexOf("://") + 3
    val end = s.indexOf("/", start)
    if (end == -1) s.substring(start) else s.substring(start, end)
  }
  def getPath: String = {
    val start = s.indexOf("://") + 3
    val end = s.indexOf("/", start)
    if (end == -1) "/" else s.substring(end)
  }
  override def toString = s
  def toURI: URI = new URI(s)
}
