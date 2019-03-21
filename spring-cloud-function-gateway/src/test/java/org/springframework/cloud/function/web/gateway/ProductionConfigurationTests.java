/*
 * Copyright 2016-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.function.web.gateway;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.function.gateway.ProxyExchange;
import org.springframework.cloud.function.web.gateway.ProductionConfigurationTests.TestApplication;
import org.springframework.cloud.function.web.gateway.ProductionConfigurationTests.TestApplication.Bar;
import org.springframework.cloud.function.web.gateway.ProductionConfigurationTests.TestApplication.Foo;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ContextConfiguration(classes = TestApplication.class)
public class ProductionConfigurationTests {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private TestApplication application;

    @LocalServerPort
    private int port;

    @Before
    public void init() throws Exception {
        application.setHome(new URI("http://localhost:" + port));
    }

    @Test
    public void get() throws Exception {
        assertThat(rest.getForObject("/proxy/0", Foo.class).getName()).isEqualTo("bye");
    }

    @Test
    public void path() throws Exception {
        assertThat(rest.getForObject("/proxy/path/1", Foo.class).getName())
                .isEqualTo("foo");
    }

    @Test
    public void resource() throws Exception {
        assertThat(rest.getForObject("/proxy/html/test.html", String.class))
                .contains("<body>Test");
    }

    @Test
    public void resourceWithNoType() throws Exception {
        assertThat(rest.getForObject("/proxy/typeless/test.html", String.class))
                .contains("<body>Test");
    }

    @Test
    public void missing() throws Exception {
        assertThat(rest.getForEntity("/proxy/missing/0", Foo.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void uri() throws Exception {
        assertThat(rest.getForObject("/proxy/0", Foo.class).getName()).isEqualTo("bye");
    }

    @Test
    public void post() throws Exception {
        assertThat(rest.postForObject("/proxy/0", Collections.singletonMap("name", "foo"),
                Bar.class).getName()).isEqualTo("host=localhost;foo");
    }

    @Test
    public void forward() throws Exception {
        assertThat(rest.getForObject("/forward/foos/0", Foo.class).getName())
                .isEqualTo("bye");
    }

    @Test
    public void forwardHeader() throws Exception {
        ResponseEntity<Foo> result = rest.getForEntity("/forward/special/foos/0",
                Foo.class);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getName()).isEqualTo("FOO");
    }

    @Test
    public void postForwardHeader() throws Exception {
        ResponseEntity<List<Bar>> result = rest.exchange(
                RequestEntity
                        .post(rest.getRestTemplate().getUriTemplateHandler().expand(
                                "/forward/special/bars"))
                .body(Collections.singletonList(Collections.singletonMap("name", "foo"))),
                new ParameterizedTypeReference<List<Bar>>() {
                });
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().iterator().next().getName()).isEqualTo("FOOfoo");
    }

    @Test
    public void postForwardBody() throws Exception {
        ResponseEntity<String> result = rest.exchange(
                RequestEntity
                        .post(rest.getRestTemplate().getUriTemplateHandler().expand(
                                "/forward/body/bars"))
                .body(Collections.singletonList(Collections.singletonMap("name", "foo"))),
                String.class);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).contains("foo");
    }

    @Test
    public void postForwardForgetBody() throws Exception {
        ResponseEntity<String> result = rest.exchange(
                RequestEntity
                        .post(rest.getRestTemplate().getUriTemplateHandler().expand(
                                "/forward/forget/bars"))
                .body(Collections.singletonList(Collections.singletonMap("name", "foo"))),
                String.class);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).contains("foo");
    }

    @Test
    public void postForwardBodyFoo() throws Exception {
        ResponseEntity<List<Bar>> result = rest.exchange(
                RequestEntity
                        .post(rest.getRestTemplate().getUriTemplateHandler().expand(
                                "/forward/body/bars"))
                .body(Collections.singletonList(Collections.singletonMap("name", "foo"))),
                new ParameterizedTypeReference<List<Bar>>() {
                });
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().iterator().next().getName()).isEqualTo("foo");
    }

    @Test
    public void list() throws Exception {
        assertThat(rest.exchange(
                RequestEntity
                        .post(rest.getRestTemplate().getUriTemplateHandler().expand(
                                "/proxy"))
                .body(Collections.singletonList(Collections.singletonMap("name", "foo"))),
                new ParameterizedTypeReference<List<Bar>>() {
                }).getBody().iterator().next().getName()).isEqualTo("host=localhost;foo");
    }

    @Test
    public void bodyless() throws Exception {
        assertThat(rest.postForObject("/proxy/0", Collections.singletonMap("name", "foo"),
                Bar.class).getName()).isEqualTo("host=localhost;foo");
    }

    @Test
    public void entity() throws Exception {
        assertThat(rest.exchange(
                RequestEntity
                        .post(rest.getRestTemplate().getUriTemplateHandler()
                                .expand("/proxy/entity"))
                        .body(Collections.singletonMap("name", "foo")),
                new ParameterizedTypeReference<List<Bar>>() {
                }).getBody().iterator().next().getName()).isEqualTo("host=localhost;foo");
    }

    @Test
    public void entityWithType() throws Exception {
        assertThat(rest.exchange(
                RequestEntity
                        .post(rest.getRestTemplate().getUriTemplateHandler()
                                .expand("/proxy/type"))
                        .body(Collections.singletonMap("name", "foo")),
                new ParameterizedTypeReference<List<Bar>>() {
                }).getBody().iterator().next().getName()).isEqualTo("host=localhost;foo");
    }

    @Test
    public void single() throws Exception {
        assertThat(rest.postForObject("/proxy/single",
                Collections.singletonMap("name", "foobar"), Bar.class).getName())
                        .isEqualTo("host=localhost;foobar");
    }

    @Test
    public void converter() throws Exception {
        assertThat(rest.postForObject("/proxy/converter",
                Collections.singletonMap("name", "foobar"), Bar.class).getName())
                        .isEqualTo("host=localhost;foobar");
    }

    @SpringBootApplication
    static class TestApplication {

        @RestController
        static class ProxyController {

            private URI home;

            public void setHome(URI home) {
                this.home = home;
            }

            @GetMapping("/proxy/{id}")
            public ResponseEntity<?> proxyFoos(@PathVariable Integer id,
                    ProxyExchange<?> proxy) throws Exception {
                return proxy.uri(home.toString() + "/foos/" + id).get();
            }

            @GetMapping("/proxy/path/**")
            public ResponseEntity<?> proxyPath(ProxyExchange<?> proxy,
                    UriComponentsBuilder uri) throws Exception {
                String path = proxy.path("/proxy/path/");
                return proxy.uri(home.toString() + "/foos/" + path).get();
            }

            @GetMapping("/proxy/html/**")
            public ResponseEntity<String> proxyHtml(ProxyExchange<String> proxy,
                    UriComponentsBuilder uri) throws Exception {
                String path = proxy.path("/proxy/html");
                return proxy.uri(home.toString() + path).get();
            }

            @GetMapping("/proxy/typeless/**")
            public ResponseEntity<?> proxyTypeless(ProxyExchange<?> proxy,
                    UriComponentsBuilder uri) throws Exception {
                String path = proxy.path("/proxy/typeless");
                return proxy.uri(home.toString() + path).get();
            }

            @GetMapping("/proxy/missing/{id}")
            public ResponseEntity<?> proxyMissing(@PathVariable Integer id,
                    ProxyExchange<?> proxy) throws Exception {
                return proxy.uri(home.toString() + "/missing/" + id).get();
            }

            @GetMapping("/proxy")
            public ResponseEntity<?> proxyUri(ProxyExchange<?> proxy) throws Exception {
                return proxy.uri(home.toString() + "/foos").get();
            }

            @PostMapping("/proxy/{id}")
            public ResponseEntity<?> proxyBars(@PathVariable Integer id,
                    @RequestBody Map<String, Object> body,
                    ProxyExchange<List<Object>> proxy) throws Exception {
                body.put("id", id);
                return proxy.uri(home.toString() + "/bars").body(Arrays.asList(body))
                        .post(this::first);
            }

            @PostMapping("/proxy")
            public ResponseEntity<?> barsWithNoBody(ProxyExchange<?> proxy)
                    throws Exception {
                return proxy.uri(home.toString() + "/bars").post();
            }

            @PostMapping("/proxy/entity")
            public ResponseEntity<?> explicitEntity(@RequestBody Foo foo,
                    ProxyExchange<?> proxy) throws Exception {
                return proxy.uri(home.toString() + "/bars").body(Arrays.asList(foo))
                        .post();
            }

            @PostMapping("/proxy/type")
            public ResponseEntity<List<Bar>> explicitEntityWithType(@RequestBody Foo foo,
                    ProxyExchange<List<Bar>> proxy) throws Exception {
                return proxy.uri(home.toString() + "/bars").body(Arrays.asList(foo))
                        .post();
            }

            @PostMapping("/proxy/single")
            public ResponseEntity<?> implicitEntity(@RequestBody Foo foo,
                    ProxyExchange<List<Object>> proxy) throws Exception {
                return proxy.uri(home.toString() + "/bars").body(Arrays.asList(foo))
                        .post(this::first);
            }

            @PostMapping("/proxy/converter")
            public ResponseEntity<Bar> implicitEntityWithConverter(@RequestBody Foo foo,
                    ProxyExchange<List<Bar>> proxy) throws Exception {
                return proxy.uri(home.toString() + "/bars").body(Arrays.asList(foo))
                        .post(response -> ResponseEntity.status(response.getStatusCode())
                                .headers(response.getHeaders())
                                .body(response.getBody().iterator().next()));
            }

            @GetMapping("/forward/**")
            public void forward(ProxyExchange<?> proxy) throws Exception {
                String path = proxy.path("/forward");
                if (path.startsWith("/special")) {
                    proxy.header("X-Custom", "FOO");
                    path = proxy.path("/forward/special");
                }
                proxy.forward(path);
            }

            @PostMapping("/forward/**")
            public void postForward(ProxyExchange<?> proxy) throws Exception {
                String path = proxy.path("/forward");
                if (path.startsWith("/special")) {
                    proxy.header("X-Custom", "FOO");
                    path = proxy.path("/forward/special");
                }
                proxy.forward(path);
            }

            @PostMapping("/forward/body/**")
            public void postForwardBody(@RequestBody byte[] body, ProxyExchange<?> proxy)
                    throws Exception {
                String path = proxy.path("/forward/body");
                proxy.body(body).forward(path);
            }

            @PostMapping("/forward/forget/**")
            public void postForwardForgetBody(@RequestBody byte[] body,
                    ProxyExchange<?> proxy) throws Exception {
                String path = proxy.path("/forward/forget");
                proxy.forward(path);
            }

            private <T> ResponseEntity<T> first(ResponseEntity<List<T>> response) {
                return ResponseEntity.status(response.getStatusCode())
                        .headers(response.getHeaders())
                        .body(response.getBody().iterator().next());
            }

        }

        @Autowired
        private ProxyController controller;

        public void setHome(URI home) {
            controller.setHome(home);
        }

        @RestController
        static class TestController {

            @GetMapping("/foos")
            public List<Foo> foos() {
                return Arrays.asList(new Foo("hello"));
            }

            @GetMapping("/foos/{id}")
            public Foo foo(@PathVariable Integer id, @RequestHeader HttpHeaders headers) {
                String custom = headers.getFirst("X-Custom");
                return new Foo(id == 1 ? "foo" : custom != null ? custom : "bye");
            }

            @PostMapping("/bars")
            public List<Bar> bars(@RequestBody List<Foo> foos,
                    @RequestHeader HttpHeaders headers) {
                String custom = headers.getFirst("X-Custom");
                custom = custom == null ? "" : custom;
                custom = headers.getFirst("forwarded")==null ? custom : headers.getFirst("forwarded") + ";" + custom;
                return Arrays.asList(new Bar(custom + foos.iterator().next().getName()));
            }

        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        static class Foo {
            private String name;

            public Foo() {
            }

            public Foo(String name) {
                this.name = name;
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        static class Bar {
            private String name;

            public Bar() {
            }

            public Bar(String name) {
                this.name = name;
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }
        }

    }

}