package function.example;

import reactor.core.publisher.Flux;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.util.Collections;
import java.util.function.Function;

public class Repeater implements Function<Tuple2<Flux<String>, Flux<Integer>>, Tuple2<Flux<String>, Flux<Integer>>> {

    @Override
    public Tuple2<Flux<String>, Flux<Integer>> apply(Tuple2<Flux<String>, Flux<Integer>> inputs) {
        Flux<String> stringFlux = inputs.getT1();
        Flux<Integer> integerFlux = inputs.getT2();
        Flux<Integer> sharedIntFlux = integerFlux.publish().autoConnect(2);

        Flux<String> repeated = stringFlux.zipWith(sharedIntFlux)
                .flatMap(t -> Flux.fromIterable(Collections.nCopies(t.getT2(), t.getT1())));

        Flux<Integer> sum = sharedIntFlux.buffer(2, 1)
                .map(l -> l.stream().mapToInt(Integer::intValue).sum())
                ;

        return Tuples.of(repeated, sum);
    }
}
