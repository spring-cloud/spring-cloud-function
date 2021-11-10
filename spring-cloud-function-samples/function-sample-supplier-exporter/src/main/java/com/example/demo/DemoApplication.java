package com.example.demo;

import java.util.function.Function;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionalSpringApplication;
import org.springframework.cloud.function.context.catalog.FunctionTypeUtils;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;

@SpringBootConfiguration(proxyBeanMethods = false)
public class DemoApplication
		implements ApplicationContextInitializer<GenericApplicationContext> {

	public static void main(String[] args) {
		FunctionalSpringApplication.run(DemoApplication.class, args);
	}

	@Override
	public void initialize(GenericApplicationContext context) {
		context.registerBean("foobar", FunctionRegistration.class,
				() -> new FunctionRegistration<>(new Foobar())
						.type(FunctionTypeUtils.functionType(Foo.class, Foo.class)));
	}

}

class Foo {
	private String name;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Foo(String name) {
		this.name = name;
	}

	Foo() {
	}
}

class Foobar implements Function<Foo, Foo> {

	@Override
	public Foo apply(Foo input) {
		System.err.println("HI: " + input.getName());
		return new Foo("hi " + input.getName() + "!");
	}
}