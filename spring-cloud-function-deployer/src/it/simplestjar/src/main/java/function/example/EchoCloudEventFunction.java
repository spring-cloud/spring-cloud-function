package function.example;

import java.util.Map;
import java.util.function.Function;

import io.cloudevents.CloudEvent;

public class EchoCloudEventFunction implements Function<CloudEvent, CloudEvent> {

	@Override
	public CloudEvent apply(CloudEvent value) {
		System.out.println("Received " + value);
		return value;
	}

}
