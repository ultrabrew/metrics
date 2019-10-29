# Example of using Ultrabrew Metrics in Undertow handlers

This example demonstrates how the library can be used to gather metrics from applications using
embedded [Undertow](http://undertow.io) HTTP server.

## How to run

Run the application:
```
$ ./gradlew :examples:undertow-httphandler:run
```

Access the application:

```
$ curl http://localhost:8080/hello
Hello World!
$ curl http://localhost:8080/hello
Hello World!
$ curl http://localhost:8080/foobar
$ curl http://localhost:8080/foobar
$ curl http://localhost:8080/foobar
```

Observe statistics in application console output:

```
14:04:30 [INFO ] [example] lastUpdated=1547471069043 method=GET handler=DEFAULT status=404 count=3 sum=565833 min=139596 max=217509 http.request
14:04:30 [INFO ] [example] lastUpdated=1547471067061 method=GET handler=HelloWorldHandler status=200 count=2 sum=2483431 min=370978 max=2112453 http.request
14:04:30 [INFO ] [example] lastUpdated=1547471067061  sum=2 hello
```