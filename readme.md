# Requests-Scala

Requests-Scala is a Scala port of the popular Python
[Requests](http://docs.python-requests.org/) HTTP client. Requests-Scala aims to
provide the same API and user-experience as the original Requests: flexible,
intuitive, and incredible straightforward to use.

## Making a Request
```scala
val r = requests.get("https://api.github.com/users/lihaoyi")

r.statusCode
// 200

r.headers("content-type")
// Buffer("application/json; charset=utf-8")

r.data.text
// {"login":"lihaoyi","id":934140,"node_id":"MDQ6VXNlcjkzNDE0MA==",...
```

```scala
val r = requests.post("http://httpbin.org/post", data = Map("key" -> "value"))

val r = requests.put("http://httpbin.org/put", data = Map("key" -> "value"))

val r = requests.delete("http://httpbin.org/delete")

val r = requests.head("http://httpbin.org/head")

val r = requests.options("http://httpbin.org/get")
```

## Passing URL parameters

```scala

val r = requests.get(
    "http://httpbin.org/get", 
    params = Map("key1" -> "value1", "key2" -> "value2")
)
```

## Response Content


```scala
val r = requests.get("https://api.github.com/events")

r.data.text
// [{"id":"7990061484","type":"PushEvent","actor":{"id":6242317,"login":...
```

## Binary Content

```scala
r.data.bytes.take(10)
// Array(91, 123, 34, 105, 100, 34, 58, 34, 55, 57,  57, 48, 48, 54, 49, ...
```


## Streaming Requests

```scala
requests.get.stream("https://api.github.com/events")(
  onDownload = inputStream => {
    inputStream.transferTo(new java.io.FileOutputStream("file.json"))
  }
)
```

```scala
requests.get.stream("https://api.github.com/events")(
  onUpload = outputStream => {...},
  onHeadersReceived = streamHeaders => {...}
  onDownload = inputStream => {...}
)
```

## Custom Headers

```scala
requests.get(
  "https://api.github.com/some/endpoint",
  headers = Map("user-agent" -> "my-app/0.0.1")
)
```

## Handling JSON

```scala
requests.get(
  "https://api.github.com/some/endpoint",
  data = ujson.write(Map("user-agent" -> "my-app/0.0.1"))
)

requests.get(
  "https://api.github.com/some/endpoint",
  data = ujson.Js.Obj("user-agent" -> "my-app/0.0.1")
)
```

```scala
val r = requests.get("https://api.github.com/events")

val json = ujson.read(r.data.text)

json.arr.length
// 30

json.arr(0).obj.keys
// Set("id", "type", "actor", "repo", "payload", "public", "created_at")
```

## Multipart Uploads

```scala
val r = requests.post(
  "http://httpbin.org/post",
  data = requests.MultiPart(
    requests.MultiItem("name", new java.io.File("build.sc"), "file.txt"),
    requests.MultiItem("name2", "Hello"), // file name is optional
    requests.MultiItem("name3", Array[Byte](1, 2, 3, 4)) // bytes are ok too
  )
)
```

## Response Headers

```scala
r.headers
// Map(
//   "server" -> Buffer("gunicorn/19.8.1"),
//   "access-control-allow-origin" -> Buffer("*"),
//   "content-length" -> Buffer("725"),
//   "access-control-allow-credentials" -> Buffer("true"),
//   "date" -> Buffer("Thu, 19 Jul 2018 16:33:03 GMT"),
//   "content-type" -> Buffer("application/json"),
//   "via" -> Buffer("1.1 vegur"),
//   "connection" -> Buffer("keep-alive")
// )
```

## Cookies

```scala
val r = requests.get("https://httpbin.org/cookies/set?freeform=test")

r.cookies
// Map("freeform" -> freeform=test)
```
```scala

val r2 = requests.get("https://httpbin.org/cookies", cookies = r.cookies)

r2.data.text
// {"cookies":{"freeform":"test"}}
```
```scala
val s = requests.Session()

val r = s.get("https://httpbin.org/cookies/set?freeform=test")

val r2 = s.get("https://httpbin.org/cookies", cookies = r.cookies)

r2.data.text
// {"cookies":{"freeform":"test"}}
```

## Session

```scala
val s = requests.Session(
  headers = Map("x-special-header" -> "omg"), 
  cookieValues = Map("cookie" -> "vanilla")
)

val r1 = requests.get("https://httpbin.org/cookies")

r1.data.text
// {"cookies":{"cookie":"vanilla"}}

val r2 = requests.get("https://httpbin.org/cookies")

r1.data.text
// {"cookies":{"cookie":"vanilla"}}

val r3 = s.get("https://httpbin.org/headers")

r3.data.text
// {"headers":{"X-Special-Header":"omg", ...}}

val r4 = s.get("https://httpbin.org/headers")

r4.data.text
// {"headers":{"X-Special-Header":"omg", ...}}
"""
```
## Redirects

```scala
val r = requests.get("http://www.github.com")

r.url
// https://github.com/

r.history
// Some(Response("https://www.github.com/?", 301, "Moved Permanently", ...

r.history.get.history
// Some(Response("http://www.github.com", 301, "Moved Permanently", ...

r.history.get.history.get.history
// None
```

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

## Timeouts
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

## Compression

```scala
requests.get("https://httpbin.org/gzip").data.bytes.length
// 250

requests.get("https://httpbin.org/gzip", autoDecompress=false).data.bytes.length
// 201


requests.get("https://httpbin.org/deflate").data.bytes.length
// 251

requests.get("https://httpbin.org/deflate", autoDecompress=false).data.bytes.length
// 188
```

