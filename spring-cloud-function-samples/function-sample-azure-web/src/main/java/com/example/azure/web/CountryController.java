/*
 * Copyright 2023-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.azure.web;

import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author Christian Tzolov
 */
@RestController
public class CountryController {

	@Autowired
	private CountryRepository countryRepository;

	@GetMapping("/")
	public String index() {
		return "Country Count: " + countryRepository.count();
	}

	@GetMapping("/countries")
	public String allCountries() {
		String countries = this.countryRepository.findAll().stream()
				.map(country -> country.toString())
				.collect(Collectors.joining());

		return "Countries: " + countries;
	}

	@PostMapping(path = "/countries")
	public Country addCountry(@RequestBody Country country) {
		if (!StringUtils.hasText(country.getName())) {
			return null;
		}
		return this.countryRepository.save(country);
	}

	@GetMapping("/countries/{id}")
	public Country countryById(@PathVariable Integer id) {
		return this.countryRepository.findById(id).get();
	}
}
