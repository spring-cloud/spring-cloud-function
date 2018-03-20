package org.springframework.cloud.function.web.flux.request;

import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;

import java.util.Arrays;
import java.util.Map;

public class FluxFormRequest<K, V> {

	private Map<K, V[]> map;

	public FluxFormRequest(Map<K, V[]> map) {
		this.map = map;
	}

	public static <K, V> FluxFormRequest<K, V> from(Map<K, V[]> map) {
		return new FluxFormRequest<>(map);
	}

	public Flux<MultiValueMap<K, V>> flux() {
		return Flux.just(buildMap());
	}

	public MultiValueMap<K, V> body() {
		return buildMap();
	}

	private MultiValueMap<K, V> buildMap() {

		if (map == null)
			return null;

		MultiValueMap<K, V> result = new LinkedMultiValueMap<>();
		map.forEach((key, values) -> result.put(key, Arrays.asList(values)));
		return result;

	}

}
