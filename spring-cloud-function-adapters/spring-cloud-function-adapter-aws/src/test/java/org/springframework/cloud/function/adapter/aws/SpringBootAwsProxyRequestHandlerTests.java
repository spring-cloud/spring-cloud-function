package org.springframework.cloud.function.adapter.aws;

import com.amazonaws.serverless.proxy.internal.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.internal.model.AwsProxyResponse;
import org.junit.Test;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.cloud.function.context.config.ContextFunctionCatalogAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

public class SpringBootAwsProxyRequestHandlerTests {

    private SpringBootAwsProxyRequestHandler handler;

    @Test
    public void functionBean() {
        handler = new SpringBootAwsProxyRequestHandler(FunctionConfig.class);
        handler.initialize();

        AwsProxyRequest request = new AwsProxyRequest();
        request.setBody("{\"value\":\"foo\"}");

        Object output = handler.handleRequest(request, null);
        assertThat(output).isInstanceOf(AwsProxyResponse.class);
        assertThat(((AwsProxyResponse) output).getStatusCode()).isEqualTo(200);
        assertThat(((AwsProxyResponse) output).getBody()).isEqualTo("{\"value\":\"FOO\"}");
    }

    @Configuration
    @Import({ContextFunctionCatalogAutoConfiguration.class,
            JacksonAutoConfiguration.class})
    protected static class FunctionConfig {
        @Bean
        public Function<Foo, Bar> function() {
            return foo -> new Bar(foo.getValue().toUpperCase());
        }
    }

    @Test
    public void functionMessageBean() {
        handler = new SpringBootAwsProxyRequestHandler(FunctionConfig.class);
        handler.initialize();

        AwsProxyRequest request = new AwsProxyRequest();
        request.setBody("{\"value\":\"foo\"}");

        Object output = handler.handleRequest(request, null);
        assertThat(output).isInstanceOf(AwsProxyResponse.class);
        assertThat(((AwsProxyResponse) output).getStatusCode()).isEqualTo(200);
        assertThat(((AwsProxyResponse) output).getBody()).isEqualTo("{\"value\":\"FOO\"}");
    }

    @Configuration
    @Import({ContextFunctionCatalogAutoConfiguration.class,
            JacksonAutoConfiguration.class})
    protected static class FunctionMessageConfig {
        @Bean
        public Function<Message<Foo>, Message<Bar>> function() {
            return foo -> new GenericMessage<>(new Bar(foo.getPayload().getValue().toUpperCase()));
        }
    }

    protected static class Foo {
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    protected static class Bar {
        private String value;

        public Bar() {
        }

        public Bar(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
