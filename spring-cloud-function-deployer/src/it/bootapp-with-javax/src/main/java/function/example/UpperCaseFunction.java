package function.example;

import java.util.function.Function;

import javax.mail.Address;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

public class UpperCaseFunction implements Function<String, String> {

	@Override
	public String apply(String value) {
		System.out.println("Uppercasing " + value);
		try {
			Address address = new InternetAddress(value);
		}
		catch (AddressException e) {
			throw new IllegalStateException("Failed to create and address: ", e);
		}
		return value.toUpperCase();
	}

}
