package function.example;

import java.util.function.Function;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SimpleFunctionAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(SimpleFunctionAppApplication.class, args);
	}

	@Bean
	public Function<String, String> uppercase() {
		System.out.println("==> CREATING 'uppercase' FUNCTION bean");
		return new UpperCaseFunction();
	}

	@Bean
	public Function<Person, Person> uppercasePerson() {
		System.out.println("==> CREATING 'uppercasePerson' FUNCTION bean");
		return person -> {
			Person p = new Person();
			p.setId(person.getId());
			p.setName(person.getName().toUpperCase());
			return p;
		};
	}



	public static class Person {
		private String name;

		private int id;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public int getId() {
			return id;
		}

		public void setId(int id) {
			this.id = id;
		}

	}
}
