package requests

// base class for all custom exceptions thrown by requests.
class RequestsException(
    val message: String,
    val cause: Option[Throwable] = None,
) extends Exception(message, cause.getOrElse(null))

class TimeoutException(
    val url: String,
    val readTimeout: Int,
    val connectTimeout: Int,
) extends RequestsException(
      s"Request to $url timed out. (readTimeout: $readTimeout, connectTimout: $connectTimeout)",
    )

class UnknownHostException(val url: String, val host: String)
    extends RequestsException(s"Unknown host $host in url $url")

class InvalidCertException(val url: String, cause: Throwable)
    extends RequestsException(
      s"Unable to validate SSL certificates for $url",
      Some(cause),
    )

class RequestFailedException(val response: Response)
    extends RequestsException(
      s"Request to ${response.url} failed with status code ${response.statusCode}\n${response.text()}",
    )
