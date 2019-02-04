# Example webapp using Ultrabrew Metrics

This is a simple webapp that demonstrates how Ultrabrew Metrics can be used to gather metrics from
webapps. To run a Servlet 3.0 compatible application server such as
[Jetty](https://www.eclipse.org/jetty/) or [WildFly](http://wildfly.org/) is required.

The application provides one servlet that simulates slow processing and a filter that provides data
about request processing time. The metrics are aggregated over 10 second period and printed to
stderr.

## How to run

The example can be built into a WAR by running `./gradlew :examples:webapp:war` from the project top
level directory. The resulting archive will be named `examples/webapp/build/libs/metrics.war`.

The exact way to deploy the applications as WARs depends on the application server, please see
its documentation for details.

### Run on Jetty

Assuming you have built the WAR archive as detailed above and have downloaded and extracted Jetty,
in the extracted Jetty directory:

1. Deploy the WAR: `cp $METRICS_ROOT/examples/webapp/build/libs/metrics.war webapps/`
1. Start Jetty: `java -jar start.jar`
1. Run some requests against the demo servlet: `curl http://localhost:8080/metrics/slow`

You should be able to observe some metrics printed to the Jetty console, for example:

```
15:07:30 [INFO ] [example] lastUpdated=1547215648555  count=3 sum=7317174587 min=1826107576 max=2922074643 MyApp.Servlet.requestDuration
15:07:40 [INFO ] [example] lastUpdated=1547215653763  count=1 sum=2154418145 min=2154418145 max=2154418145 MyApp.Servlet.requestDuration
```

### Run on WildFly

Assuming you have built the WAR archive as detailed above and have downloaded and extracted WildFly,
in the extracted WildFly directory:

1. Deploy the WAR: `cp ~/metrics/examples/webapp/build/libs/metrics.war standalone/deployments/`
1. Start WildFly: `bin/standalone.sh`
1. Run some requests against the demo servlet: `curl http://localhost:8080/metrics/slow`

You should be able to observe some metrics printed to the WildFly console, for example:

```
15:11:40,105 INFO  [example] (example-1) lastUpdated=1547215897655  count=1 sum=2298432552 min=2298432552 max=2298432552 MyApp.Servlet.requestDuration
15:11:50,104 INFO  [example] (example-1) lastUpdated=1547215907333  count=3 sum=7955554341 min=2468268241 max=2970018942 MyApp.Servlet.requestDuration
```
