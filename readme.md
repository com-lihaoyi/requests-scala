# Requests-Scala 0.9.0

Requests-Scala is a Scala port of the popular Python
[Requests](http://docs.python-requests.org/) HTTP client. Requests-Scala aims to
provide the same API and user-experience as the original Requests: flexible,
intuitive, and straightforward to use.

If you use Requests-Scala and like it, you will probably enjoy the following book by the Author:

- [*Hands-on Scala Programming*](https://www.handsonscala.com/)

*Hands-on Scala* has uses Requests-Scala extensively throughout the book, and has
the entirety of *Chapter 12: Working with HTTP APIs* dedicated to
the library. *Hands-on Scala* is a great way to level up your skills in Scala
in general and Requests-Scala in particular.

You can also support it by donating to our Patreon:

- [https://www.patreon.com/lihaoyi](https://www.patreon.com/lihaoyi)

For a hands-on introduction to this library, take a look at the following blog post:

- [How to work with HTTP JSON APIs in Scala](http://www.lihaoyi.com/post/HowtoworkwithHTTPJSONAPIsinScala.html)

## Contents

- [Requests-Scala 0.9.0](#requests-scala-081)
  - [Contents](#contents)
  - [Getting Started](#getting-started)
  - [Making a Request](#making-a-request)
    - [Passing in Parameters](#passing-in-parameters)
    - [Response Content](#response-content)
  - [Streaming Requests](#streaming-requests)
  - [Handling JSON](#handling-json)
  - [Multipart Uploads](#multipart-uploads)
  - [Misc Configuration](#misc-configuration)
    - [Custom Headers](#custom-headers)
    - [Timeouts](#timeouts)
    - [Compression](#compression)
    - [Cookies](#cookies)
    - [Redirects](#redirects)
    - [Client Side Certificates](#client-side-certificates)
  - [Sessions](#sessions)
  - [Why Requests-Scala?](#why-requests-scala)
  - [Changelog](#changelog)
    - [0.9.0](#090)
    - [0.8.0](#080)
    - [0.7.1](#071)
    - [0.7.0](#070)
    - [0.6.7](#067)
    - [0.6.5](#065)
    - [0.5.1](#051)
    - [0.4.7](#047)
    - [0.3.0](#030)
    - [0.2.0](#020)
    - [0.1.9](#019)
    - [0.1.8](#018)
    - [0.1.7](#017)
    - [0.1.6](#016)
    - [0.1.5](#015)

## Getting Started

Use the following import to get you started:

```scala
ivy"com.lihaoyi::requests:0.9.0" // mill
"com.lihaoyi" %% "requests" % "0.9.0" // sbt
compile "com.lihaoyi:requests_2.12:0.9.0" //gradle
```

## Making a Request
```scala
val r = requests.get("https://api.github.com/users/lihaoyi")

r.statusCode
// 200

r.headers("content-type")
// Buffer("application/json; charset=utf-8")

r.text()
// {"login":"lihaoyi","id":934140,"node_id":"MDQ6VXNlcjkzNDE0MA==",...
```

Making your first HTTP request is simple: simply call `requests.get` with the
URL you want, and requests will fetch it for you.

You can also call `requests.post`, `requests.put`, etc. to make other kinds of
HTTP requests:

```scala
val r = requests.post("http://httpbin.org/post", data = Map("key" -> "value"))

val r = requests.put("http://httpbin.org/put", data = Map("key" -> "value"))

val r = requests.delete("http://httpbin.org/delete")

val r = requests.head("http://httpbin.org/head")

val r = requests.options("http://httpbin.org/get")

// dynamically choose what HTTP method to use
val r = requests.send("put")("http://httpbin.org/put", data = Map("key" -> "value"))

```

### Passing in Parameters

```scala
val r = requests.get(
    "http://httpbin.org/get",
    params = Map("key1" -> "value1", "key2" -> "value2")
)
```
You can pass in URL parameters to GET requests via the `params` argument; simply
pass in a `Map[String, String]`. As seen earlier, when passing in POST or PUT
parameters, you instead need the `data` argument:

```scala
val r = requests.post("http://httpbin.org/post", data = Map("key" -> "value"))

val r = requests.put("http://httpbin.org/put", data = Map("key" -> "value"))
```

Apart from POSTing key-value pairs, you can also POST `String`s, `Array[Byte]`s,
`java.io.File`s, `java.nio.file.Path`s, and `requests.MultiPart` uploads:

```scala
requests.post("https://httpbin.org/post", data = "Hello World")
requests.post("https://httpbin.org/post", data = Array[Byte](1, 2, 3))
requests.post("https://httpbin.org/post", data = new java.io.File("thing.json"))
requests.post("https://httpbin.org/post", data = java.nio.file.Paths.get("thing.json"))
```

The `data` parameter also supports anything that implements the
[Writable](https://github.com/com-lihaoyi/geny#writable) interface, such as
[ujson.Value](http://com-lihaoyi.github.io/upickle/#uJson)s,
[uPickle](http://com-lihaoyi.github.io/upickle)'s `upickle.default.writable` values,
or [Scalatags](http://com-lihaoyi.github.io/scalatags/)'s `Tag`s

### Response Content

```scala
val r = requests.get("https://api.github.com/events")

r.statusCode
// 200

r.headers("content-type")
// Buffer("application/json; charset=utf-8")
```

As seen earlier, you can use `.statusCode` and `.headers` to see the relevant
metadata of your HTTP response. The response data is in the `.data` field of the
`Response` object. Most often, it's text, which you can decode using the `.text()`
property as shown below:

```scala
r.text()
// [{"id":"7990061484","type":"PushEvent","actor":{"id":6242317,"login":...
```

If you want the raw bytes of the response, use `r.contents`


```scala
r.contents
// Array(91, 123, 34, 105, 100, 34, 58, 34, 55, 57,  57, 48, 48, 54, 49, ...
```


## Streaming Requests

```scala
os.write(
  os.pwd / "file.json",
  requests.get.stream("https://api.github.com/events")
)
```

Requests exposes the `requests.get.stream` (and equivalent
`requests.post.stream`, `requests.put.stream`, etc.) functions for you to
perform streaming uploads/downloads without needing to load the entire
request/response into memory. This is useful if you are upload/downloading large
files or data blobs. `.stream` returns a
[Readable](https://github.com/com-lihaoyi/geny#readable) value, that can be then
passed to methods like [os.write](https://github.com/com-lihaoyi/os-lib#oswrite),
`fastparse.parse` or `upickle.default.read` to handle the received data in a
streaming fashion:

```scala
ujson.read(requests.get.stream("https://api.github.com/events"))
```

Since `requests.post` and `requests.put` both take a `data: geny.Writable`
parameter, you can even chain requests together, taking the data returned from
one HTTP request and feeding it into another:

```scala
os.write(
  os.pwd / "chained.json",
  requests.post.stream(
    "https://httpbin.org/post",
    data = requests.get.stream("https://api.github.com/events")
  )
)
```

`requests.*.stream` should make it easy for you to work with data
too big to fit in memory, while still benefiting from most of Requests' friendly
& intuitive API.

## Handling JSON

Requests does not provide any built-in JSON support, but you can easily use a
third-party JSON library to work with it. This example shows how to use
[uJson](https://com-lihaoyi.github.io/upickle/) talk to a HTTP endpoint that requires a
JSON-formatted body, either using `upickle.default.stream`:

```scala
requests.post(
  "https://api.github.com/some/endpoint",
  data = upickle.default.stream(Map("user-agent" -> "my-app/0.0.1"))
)
```

Or by constructing `ujson.Value`s directly

```scala
requests.post(
  "https://api.github.com/some/endpoint",
  data = ujson.Obj("user-agent" -> "my-app/0.0.1")
)
```

In both cases, the upload occurs efficiently in a streaming fashion, without
materializing the entire JSON blob in memory.

It is equally easy ot use uJson to deal with JSON returned in the response from
the server:

```scala
val r = requests.get("https://api.github.com/events")

val json = ujson.read(r.text())

json.arr.length
// 30

json.arr(0).obj.keys
// Set("id", "type", "actor", "repo", "payload", "public", "created_at")
```

While Requests-Scala doesn't come bundled with JSON functionality, it is trivial
to use it together with any other 3rd party JSON library (I like
[uJson](https://github.com/com-lihaoyi/upickle)) So just pick whatever library you
want.

## Multipart Uploads

```scala
val r = requests.post(
  "http://httpbin.org/post",
  data = requests.MultiPart(
    requests.MultiItem("name", new java.io.File("build.sc"), "file.txt"),
    // you can upload strings, and file name is optional
    requests.MultiItem("name2", "Hello"),
    // bytes arrays are ok too
    requests.MultiItem("name3", Array[Byte](1, 2, 3, 4))
  )
)
```

Multipart uploads are done by passing `requests.MultiPart`/`requests.MultiItem`
to the `data` parameter. Each `MultiItem` needs a name and a data-source, which
can be a `String`, `Array[Byte]`, `java.io.File`, or `java.nio.file.Path`. Each
`MultiItem` can optionally take a file name that will get sent to the server

## Misc Configuration

Earlier you already saw how to use the `params` and `data` arguments. Apart from
those, the `requests.get` method takes in a lot of arguments you can use to
configure it, e.g. passing in custom headers:

### Custom Headers

```scala
requests.get(
  "https://api.github.com/some/endpoint",
  headers = Map("user-agent" -> "my-app/0.0.1")
)
```

To pass in a single header multiple times, you can pass them as a comma separated list:

```scala
requests.get(
  "https://api.github.com/some/endpoint",
  headers = Map("user-agent" -> "my-app/0.0.1,other-app/0.0.2")
)
```

### Timeouts

`readTimeout`s and `connectTimeout`s:

```scala
requests.get("https://httpbin.org/delay/1", readTimeout = 10)
// TimeoutException

requests.get("https://httpbin.org/delay/1", readTimeout = 1500)
// ok

requests.get("https://httpbin.org/delay/3", readTimeout = 1500)
// TimeoutException
```

```scala
requests.get("https://httpbin.org/delay/1", connectTimeout = 10)
// TimeoutException

requests.get("https://httpbin.org/delay/1", connectTimeout = 1500)
// ok

requests.get("https://httpbin.org/delay/3", connectTimeout = 1500)
// ok
```

### Compression

Configuration for compressing the request `data` upload with Gzip or Deflate via
the `compress` parameter:

```scala
requests.post(
  "https://httpbin.org/post",
  compress = requests.Compress.None,
  data = "Hello World"
)

requests.post(
  "https://httpbin.org/post",
  compress = requests.Compress.Gzip,
  data = "I am cow"
)

requests.post(
  "https://httpbin.org/post",
  compress = requests.Compress.Deflate,
  data = "Hear me moo"
)
```

Or to disabling the de-compression of the response `data` being downloaded via
the `autoCompress` parameter, in case you want the un-compressed data blob for
whatever reason:

```scala
requests.get("https://httpbin.org/gzip").contents.length
// 250

requests.get("https://httpbin.org/gzip", autoDecompress=false).contents.length
// 201


requests.get("https://httpbin.org/deflate").contents.length
// 251

requests.get("https://httpbin.org/deflate", autoDecompress=false).contents.length
// 188
```

Note that by default, compression of fixed-size in-memory input (`String`s,
`Array[Byte]`s, ...) buffers up the compressed data in memory before uploading
it. Compression of unknown-length/not-in-memory data (files, `InputStream`s,
...) doesn't perform this buffering and uses chunked transfer encoding, as
normal. If you want to avoid buffering in memory and are willing to use chunked
transfer encoding for in-memory data, wrap it in an inputstream (e.g.
`Array[Byte]` can be wrapped in a `ByteArrayInputStream`)

### Cookies

You can take the cookies that result from one HTTP request and pass them into a
subsequent HTTP request:

```scala
val r = requests.get("https://httpbin.org/cookies/set?freeform=test")

r.cookies
// Map("freeform" -> freeform=test)
```
```scala

val r2 = requests.get("https://httpbin.org/cookies", cookies = r.cookies)

r2.text()
// {"cookies":{"freeform":"test"}}
```

This is a common pattern, e.g. to maintain an authentication/login session
across multiple requests. However, it may be easier to instead use Sessions...


### Redirects

Requests handles redirects automatically for you, up to a point:

```scala
val r = requests.get("http://www.github.com")

r.url
// https://github.com/

r.history
// Some(Response("https://www.github.com", 301, "Moved Permanently", ...

r.history.get.history
// Some(Response("http://www.github.com", 301, "Moved Permanently", ...

r.history.get.history.get.history
// None
```

As you can see, the request to `http://www.github.com` was first redirected to
`https://www.github.com`, and then to `https://github.com/`. Requests by default
only follows up to 5 redirects in a row, though this is configurable via the
`maxRedirects` parameter:

```scala
val r0 = requests.get("http://www.github.com", maxRedirects = 0)
// Response("http://www.github.com", 301, "Moved Permanently", ...

r0.history
// None

val r1 = requests.get("http://www.github.com", maxRedirects = 1)
// Response("http://www.github.com", 301, "Moved Permanently", ...

r1.history
// Some(Response("http://www.github.com", 301, "Moved Permanently", ...

r1.history.get.history
// None
```

As you can see, you can use `maxRedirects = 0` to disable redirect handling
completely, or use another number to control how many redirects Requests follows
before giving up.

All of the intermediate responses in a redirect chain are available in a
Response's `.history` field; each `.history` points 1 response earlier, forming
a linked list of `Response` objects until the earliest response has a value of
`None`. You can crawl up this linked list if you want to inspect the headers or
other metadata of the intermediate redirects that brought you to your final value.

### Client Side Certificates

To use client certificate you need a PKCS 12 archive with private key and certificate.

```scala
requests.get(
  "https://client.badssl.com",
  cert = "./badssl.com-client.p12"
)
```

If the p12 archive is password protected you can provide a second parameter:

```scala
requests.get(
  "https://client.badssl.com",
  cert = ("./badssl.com-client.p12", "password")
)
```

For test environments you may want to combine `cert` with the `verifySslCerts = false` option (if you have self signed SSL certificates on test servers).

```scala
requests.get(
  "https://client.badssl.com",
  cert = ("./badssl.com-client.p12", "password"),
  verifySslCerts = false
)
```

You can also use a sslContext to provide a more customized ssl configuration

```scala
val sslContext: SSLContext = //initialized sslContext

requests.get(
  "https://client.badssl.com",
  sslContext = sslContext
)
```

## Sessions

A `requests.Session` automatically handles sending/receiving/persisting cookies
for you across multiple requests:

```scala
val s = requests.Session()

val r = s.get("https://httpbin.org/cookies/set?freeform=test")

val r2 = s.get("https://httpbin.org/cookies")

r2.text()
// {"cookies":{"freeform":"test"}}
```

If you want to deal with a website that uses cookies, it's usually easier to use
a `requests.Session` rather than passing around `cookie` variables manually.

Apart from persisting cookies, sessions are also useful for consolidating common
configuration that you want to use across multiple requests, e.g. custom
headers, cookies or other things:

```scala
val s = requests.Session(
  headers = Map("x-special-header" -> "omg"),
  cookieValues = Map("cookie" -> "vanilla")
)

val r1 = s.get("https://httpbin.org/cookies")

r1.text()
// {"cookies":{"cookie":"vanilla"}}

val r2 = s.get("https://httpbin.org/headers")

r2.text()
// {"headers":{"X-Special-Header":"omg", ...}}
```

## Why Requests-Scala?

There is a whole zoo of HTTP clients in the Scala ecosystem. Akka-http, Play-WS,
STTP, HTTP4S, Scalaj-HTTP, RosHTTP, Dispatch. Nevertheless, none of them come
close to the ease and weightlessness of using Kenneth Reitz's
[Requests](https://requests.readthedocs.io/en/latest/) library: too many implicits,
operators, builders, monads, and other things.

When I want to make a HTTP request, I do not want to know about
`.unsafeRunSync`, infix methods like `svc OK as.String`, or define implicit
`ActorSystem`s, `ActorMaterializer`s, and `ExecutionContext`s. So far
[sttp](https://github.com/softwaremill/sttp) and
[scalaj-http](https://github.com/scalaj/scalaj-http) come closest to what I
want, but still fall short: both still use a pattern of fluent builders that to
me doesn't fit how I think when making a HTTP request. I just want to call one
function to make a HTTP request, and get back my HTTP response.

Most people will never reach the scale that asynchrony matters, and most of
those who do reach that scale will only need it in a small number of specialized
places, not everywhere.

Compare the getting-started code necessary for Requests-Scala against some other
common Scala HTTP clients:
```scala
// Requests-Scala
val r = requests.get(
  "https://api.github.com/search/repositories",
  params = Map("q" -> "http language:scala", "sort" -> "stars")
)

r.text()
// {"login":"lihaoyi","id":934140,"node_id":"MDQ6VXNlcjkzNDE0MA==",...
```
```scala
// Akka-Http
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer

import scala.concurrent.Future
import scala.util.{ Failure, Success }

implicit val system = ActorSystem()
implicit val materializer = ActorMaterializer()
// needed for the future flatMap/onComplete in the end
implicit val executionContext = system.dispatcher

val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = "http://akka.io"))

responseFuture
  .onComplete {
    case Success(res) => println(res)
    case Failure(_)   => sys.error("something wrong")
  }

```

```scala
// Play-WS

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import play.api.libs.ws._
import play.api.libs.ws.ahc._

import scala.concurrent.Future

import DefaultBodyReadables._
import scala.concurrent.ExecutionContext.Implicits._

// Create Akka system for thread and streaming management
implicit val system = ActorSystem()
implicit val materializer = ActorMaterializer()

// Create the standalone WS client
// no argument defaults to a AhcWSClientConfig created from
// "AhcWSClientConfigFactory.forConfig(ConfigFactory.load, this.getClass.getClassLoader)"
val wsClient = StandaloneAhcWSClient()

wsClient.url("http://www.google.com").get()
  .map { response ⇒
    val statusText: String = response.statusText
    val body = response.body[String]
    println(s"Got a response $statusText")
  }.
  andThen { case _ => wsClient.close() }
  andThen { case _ => system.terminate() }

```
```scala
// Http4s
import org.http4s.client.dsl.io._
import org.http4s.headers._
import org.http4s.MediaType

val request = GET(
  Uri.uri("https://my-lovely-api.com/"),
  Authorization(Credentials.Token(AuthScheme.Bearer, "open sesame")),
  Accept(MediaType.application.json)
)

httpClient.expect[String](request)
```
```scala
// sttp
import sttp.client3._

val request = basicRequest.response(asStringAlways)
  .get(uri"https://api.github.com/search"
    .addParams(Map("q" -> "http language:scala", "sort" -> "stars")))

val backend = HttpURLConnectionBackend()
val response = backend.send(request)

println(response.body)
```
```scala
// Dispatch
import dispatch._, Defaults._
val svc = url("http://api.hostip.info/country.php")
val country = Http.default(svc OK as.String)
```

The existing clients require a complex mix of imports, implicits, operators, and
DSLs. The goal of Requests-Scala is to do away with all of that: your HTTP
request is just a function call that takes parameters; that is all you need to
know.

As it turns out, Kenneth Reitz's Requests is
[not a lot of code](https://github.com/requests/requests/tree/main/requests).
Most of the heavy lifting is done in other libraries, and his library is a just
thin-shim that makes the API 10x better. Similarly, it turns out on the JVM most of the
heavy lifting is also done for you. There have always been options, but
since JDK 11 a decent HTTP client is provided in the standard library.

Given that's the case, how hard can it be to port over a dozen Python files to
Scala? This library attempts to do that: class by class, method by method,
keyword-argument by keyword-argument. Not everything has been implemented yet,
some things differ (some avoidably, some unavoidably), and it's nowhere near as
polished, but you should definitely try it out as the HTTP client for your next
codebase or project!

## Changelog

### 0.9.0

- Use JDK 11 HttpClient ([#158](https://github.com/com-lihaoyi/requests-scala/pull/158)). Note
  that this means we are dropping compatibility with JDK 8, and will require JDK 11 and above
  going forward. People who need to use JDK 8 can continue using version 0.8.3

### 0.8.3

- Fix handling of HTTP 304 ([#159](https://github.com/com-lihaoyi/requests-scala/pull/159))

### 0.8.2

- fix: content type header not present in multipart item ([#154](https://github.com/com-lihaoyi/requests-scala/pull/154))

### 0.8.0

- Update Geny to 1.0.0 [#120](https://github.com/com-lihaoyi/requests-scala/pull/120)

### 0.7.1

- Fix issue with data buffers not being flushed when compression is enabled [#108](https://github.com/com-lihaoyi/requests-scala/pull/108)

### 0.7.0

- Allow `requests.send(method)(...)` to dynamically choose a HTTP method [#94](https://github.com/com-lihaoyi/requests-scala/pull/94)
- Avoid crashing on gzipped HEAD requests [#95](https://github.com/com-lihaoyi/requests-scala/pull/95)
- All exceptions now inherit from a `RequestsException` base class

### 0.6.7

- Add support for Scala 3.0.0-RC2

### 0.6.5

- `requests.Response` now implements the `geny.Readable` interface, and can be
  directly passed to compatible APIs like `ujson.read` or `os.write`

- Add support for custom SSL certs

- Allow body content for DELETE requests

### 0.5.1

- Made `requests.{get,post,put,delete,head,options,patch}.stream` return a
  [Readable](https://github.com/lihaoyi/geny#readable), allowing upickle and
  fastparse to operate directly on the streaming input

### 0.4.7

- `requests.{get,post,put,delete,head,options,patch}` now throw a
  `requests.RequestFailedException(val response: Response)` if a non-2xx status
  code is received. You can disable throwing the exception by passing in
  `check = false`
- `requests.{get,post,put,delete,head,options,patch}.stream` now returns a
  [Writable](https://github.com/lihaoyi/geny#writable) instead of taking
  callbacks.

### 0.3.0

- Support for uploading
  [geny.Writable](https://github.com/lihaoyi/geny#writable) data types in
  request bodies.

### 0.2.0

- Support for Scala 2.13.0 final

### 0.1.9

- Support `PATCH` and other verbs

### 0.1.8

- Support for `Bearer` token auth

### 0.1.7

- `RequestBlob` headers no longer over-write session headers

### 0.1.6

- Allow POSTs to take URL parameters
- Return response body for all 2xx response codes
- Always set `Content-Length` to 0 when request body is empty

### 0.1.5

- First Release
