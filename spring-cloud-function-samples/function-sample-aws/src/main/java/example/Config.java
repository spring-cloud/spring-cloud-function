/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package example;

import java.util.function.Function;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.function.context.FunctionRegistration;
import org.springframework.cloud.function.context.FunctionType;
import org.springframework.cloud.function.context.FunctionalSpringApplication;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.GenericApplicationContext;

@SpringBootApplication
@EnableConfigurationProperties(Properties.class)
public class Config implements ApplicationContextInitializer<GenericApplicationContext> {

	private Properties props;

	public Config() {
	}

	public Config(Properties props) {
		this.props = props;
	}

	@Bean
	public Function<Foo, Bar> function() {
		return value -> new Bar(
				value.uppercase() + (props.getFoo() != null ? "-" + props.getFoo() : ""));
	}

	public static void main(String[] args) throws Exception {
		FunctionalSpringApplication.run(Config.class, args);
	}

	@Override
	public void initialize(GenericApplicationContext context) {
		Properties properties = new Properties();
		this.props = properties;
		context.registerBean(Properties.class, () -> properties);
		context.registerBean("function", FunctionRegistration.class,
				() -> new FunctionRegistration<Function<Foo, Bar>>(function())
						.type(FunctionType.from(Foo.class).to(Bar.class).getType()));
	}

}

class Foo {

	private String value;

	Foo() {
	}

	public String lowercase() {
		return value.toLowerCase();
	}

	public Foo(String value) {
		this.value = value;
	}

	public String uppercase() {
		return value.toUpperCase();
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}
}

class Bar {

	private String value;

	Bar() {
	}

	public Bar(String value) {
		this.value = value;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}

}
