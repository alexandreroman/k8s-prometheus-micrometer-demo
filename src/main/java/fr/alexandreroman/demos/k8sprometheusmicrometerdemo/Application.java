/*
 * Copyright (c) 2019 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.alexandreroman.demos.k8sprometheusmicrometerdemo;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;

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
        return Counter.builder("hello_counter")
                .description("Access counter")
                .register(registry);
    }
}
