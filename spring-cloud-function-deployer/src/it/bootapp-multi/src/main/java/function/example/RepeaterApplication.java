package function.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;

import java.util.function.Function;

@SpringBootApplication
public class RepeaterApplication {

    @Bean
    public Function<Tuple2<Flux<String>, Flux<Integer>>,
            Tuple2<Flux<Double>, Flux<String>>
            > fn() {
        return new MyFn();
    }

    public static void main(String[] args) {
        SpringApplication.run(RepeaterApplication.class, args);
    }
}
