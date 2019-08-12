package function.example;

import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.util.Collections;
import java.util.function.Function;

public class MyFn implements Function<Tuple2<Flux<String>, Flux<Integer>>, Tuple2<Flux<Double>, Flux<String>>> {


    @Override
    public Tuple2<Flux<Double>, Flux<String>> apply(Tuple2<Flux<String>, Flux<Integer>> inputs) {
        Flux<String> words = inputs.getT1();
        Flux<Integer> numbers = inputs.getT2().publish().autoConnect(2);


        Flux<Double> avg = numbers.buffer(2, 1)
                .map(l -> l.stream().mapToInt(Integer::intValue).average().getAsDouble())
                .take(3);

        Flux<String> repeated = words.zipWith(numbers)
                .flatMap(t -> Flux.fromIterable(Collections.nCopies(t.getT2(), t.getT1())));

        return Tuples.of(avg, repeated);

    }
}
