package function.example;

import java.util.function.Function;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

public class SimpleFunctionAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(SimpleFunctionAppApplication.class, args);
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
