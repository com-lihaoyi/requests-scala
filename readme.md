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

## JSON Content

```scala
val r = requests.get("https://api.github.com/events")

val json = ujson.read(r.data.text)

json.arr.length
// 30

json.arr(0).obj.keys
// Set("id", "type", "actor", "repo", "payload", "public", "created_at")
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

## Posting JSON

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

## Multipart Uploads

```scala
val r = requests.post(
  "http://httpbin.org/post",
  data = requests.MultiPart(
    requests.MultiItem("report.xls", new java.io.File("build.sc"), "file.txt")
  )
)
```