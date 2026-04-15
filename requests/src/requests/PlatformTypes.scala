package requests

import java.io.InputStream

private[requests] case class PlatformResponse(
    statusCode: Int,
    headers: Map[String, Seq[String]],
    body: InputStream,
)
