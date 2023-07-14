package com.example.azure.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories
public class AzureWebDemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(AzureWebDemoApplication.class, args);
	}

}
