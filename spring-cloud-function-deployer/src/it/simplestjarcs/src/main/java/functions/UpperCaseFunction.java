package functions;

import java.util.Locale;
import java.util.function.Function;

public class UpperCaseFunction implements Function<String, String> {

	@Override
	public String apply(String value) {
		System.out.println("Uppercasing " + value);
		return value.toUpperCase(Locale.ROOT);
	}

}
