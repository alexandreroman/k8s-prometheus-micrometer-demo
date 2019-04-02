# Export metrics from your Spring Boot app to Prometheus

This project shows how to use
[Micrometer](https://micrometer.io) to export metrics from a Spring Boot
app to Prometheus.

Micrometer is integrated with Spring Boot: adding metrics to your app
is really easy. Here is the full source code for a REST controller
exposing a simple gauge:
```java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}

@RestController
@RequiredArgsConstructor
class HelloController {
    private final HelloMetrics metrics;

    @GetMapping("/")
    String hello() throws UnknownHostException {
        final String hostName = InetAddress.getLocalHost().getCanonicalHostName();
        metrics.increment();
        return "Hello world from " + hostName + "!\nCounter value: " + metrics.value();
    }
}

@Component
@RequiredArgsConstructor
class HelloMetrics {
    @Qualifier("helloCounter")
    private final Counter counter;

    void increment() {
        counter.increment();
    }

    long value() {
        return (long) counter.count();
    }
}

@Configuration
@RequiredArgsConstructor
class HelloMetricsConfig {
    @Bean
    @Qualifier("helloCounter")
    Counter helloCounter(MeterRegistry registry) {
        // This is all you need to create a Micrometer metric.
        return Counter.builder("hello_counter")
                .description("Access counter")
                .register(registry);
    }
}
```

In this app, we choose to decouple our business code (`HelloController`)
from Micrometer API, by using a metrics facade (`HelloMetrics`).
As you can see, metrics exposed by Micrometer do not rely on Prometheus:
Micrometer is acting as a bridge for a large number of metrics systems.

# How to use it?

Compile this app using a JDK 8:
```bash
$ ./mvnw clean package
```

You now have an executable app:
```bash
$ java -jar target/k8s-prometheus-micrometer-demo.jar
```

Hit this endpoint to increment our counter:
```bash
$ curl localhost:8080
Hello world from localhost!
Counter value: 1%
```

Micrometer metrics are available under this endpoint:
```bash
$ curl http://localhost:8080/actuator/metrics
{"names":["jvm.memory.max","jvm.threads.states","process.files.max","jvm.gc.memory.promoted","system.load.average.1m","jvm.memory.used","jvm.gc.max.data.size","jvm.gc.pause","jvm.memory.committed","system.cpu.count","logback.events","tomcat.global.sent","hello_counter","jvm.buffer.memory.used","tomcat.sessions.created","jvm.threads.daemon","system.cpu.usage","jvm.gc.memory.allocated","tomcat.global.request.max","tomcat.global.request","tomcat.sessions.expired","jvm.threads.live","jvm.threads.peak","tomcat.global.received","process.uptime","tomcat.sessions.rejected","process.cpu.usage","tomcat.threads.config.max","jvm.classes.loaded","jvm.classes.unloaded","tomcat.global.error","tomcat.sessions.active.current","tomcat.sessions.alive.max","jvm.gc.live.data.size","tomcat.threads.current","process.files.open","jvm.buffer.count","jvm.buffer.total.capacity","tomcat.sessions.active.max","tomcat.threads.busy","process.start.time"]}%
```

Get metric details:
```bash
$ curl localhost:8080/actuator/metrics/hello_counter
{"name":"hello_counter","description":"Access counter","baseUnit":null,"measurements":[{"statistic":"COUNT","value":2.0}],"availableTags":[]}%
```

Adding Prometheus support to your app only requires a single dependency in `pom.xml`:
```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

Make sure you enable the Prometheus actuator in `application.yml`:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, metrics, prometheus
```

Prometheus metrics are published under this endpoint:
```bash
$ curl localhost:8080/actuator/prometheus
# HELP process_cpu_usage The "recent cpu usage" for the Java Virtual Machine process
# TYPE process_cpu_usage gauge
process_cpu_usage 0.0
# HELP jvm_memory_committed_bytes The amount of memory in bytes that is committed for the Java virtual machine to use
# TYPE jvm_memory_committed_bytes gauge
jvm_memory_committed_bytes{area="heap",id="PS Survivor Space",} 1.9398656E7
jvm_memory_committed_bytes{area="heap",id="PS Old Gen",} 1.89792256E8
jvm_memory_committed_bytes{area="heap",id="PS Eden Space",} 1.37363456E8
jvm_memory_committed_bytes{area="nonheap",id="Metaspace",} 4.2467328E7
jvm_memory_committed_bytes{area="nonheap",id="Code Cache",} 9371648.0
jvm_memory_committed_bytes{area="nonheap",id="Compressed Class Space",} 6029312.0
# HELP hello_counter_total Access counter
# TYPE hello_counter_total counter
hello_counter_total 2.0
```

## Deploy this app to Kubernetes

First, you need to deploy Prometheus to your Kubernetes cluster. You may also
want to deploy Grafana to get fancy graphs:
[follow these instructions](https://github.com/pivotal-cf/charts-grafana)
to install these tools. You will get a ready-to-use Prometheus/Grafana installation:
<img src="https://i.imgur.com/Tbrw1ST.png"/>

Use the provided Kubernetes descriptors to deploy this app to your cluster:
```bash
$ kubectl apply -f k8s
namespace/k8s-prometheus-micrometer-demo created
deployment.apps/app created
service/app-lb created
```
Just add these annotations to the deployment descriptor to configure Prometheus,
so that it automatically starts to scrape metrics from our app:
```yaml
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: app
  namespace: k8s-prometheus-micrometer-demo
spec:
  replicas: 3
  selector:
    matchLabels:
      role: app
  template:
    metadata:
      labels:
        role: app
      annotations:
        # Just add these annotations to configure Prometheus.
        prometheus.io/scrape: "true"
        prometheus.io/path: "/actuator/prometheus"
        prometheus.io/port:  "8080"
    spec:
      containers:
        - name: app
          image: alexandreroman/k8s-prometheus-micrometer-demo
          imagePullPolicy: Always
          ports:
            - containerPort: 8080
          livenessProbe:
            httpGet:
              port: 8080
              path: /actuator/health
            initialDelaySeconds: 30
            periodSeconds: 2
          readinessProbe:
            httpGet:
              port: 8080
              path: /actuator/health
            initialDelaySeconds: 10
```

Check the `LoadBalancer` allocated IP address to get the public app endpoint:
```bash
$ kubectl -n k8s-prometheus-micrometer-demo get svc
NAME     TYPE           CLUSTER-IP      EXTERNAL-IP     PORT(S)        AGE
app-lb   LoadBalancer   10.100.200.61   34.76.186.242   80:32741/TCP   67m
```

Increment the counter:
```bash
$ curl 34.76.186.242
Hello world from app-6cb69b6c9-z5sfq!
Counter value: 3%
```

Prometheus is already scraping metric values.

## View app metrics using Grafana

Now it's time to view app metrics using Grafana.

Create a new dashboard:
<img src="https://i.imgur.com/fse6YPO.png"/>

Add a graph, and bind it to the `hello_counter_total` metric:
<img src="https://i.imgur.com/dKPbxql.png"/>

You're done!
<img src="https://i.imgur.com/hkKCW1k.png"/>

## Contribute

Contributions are always welcome!

Feel free to open issues & send PR.

## License

Copyright &copy; 2019 [Pivotal Software, Inc](https://pivotal.io).

This project is licensed under the [Apache Software License version 2.0](https://www.apache.org/licenses/LICENSE-2.0).
