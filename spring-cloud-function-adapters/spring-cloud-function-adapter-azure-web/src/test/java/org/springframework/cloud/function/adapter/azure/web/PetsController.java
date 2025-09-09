/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.cloud.function.adapter.azure.web;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@RestController
@EnableWebMvc
public class PetsController {
	@RequestMapping(path = "/pets", method = RequestMethod.POST)
	public Pet createPet(@RequestBody Pet newPet) {
		if (newPet.getName() == null || newPet.getBreed() == null) {
			return null;
		}

		Pet dbPet = newPet;
		dbPet.setId(UUID.randomUUID().toString());
		return dbPet;
	}

	@RequestMapping(path = "/pets", method = RequestMethod.GET)
	public Pet[] listPets(@RequestParam("limit") Optional<Integer> limit, Principal principal) {
		System.out.println("=====> EXECUTING");
		int queryLimit = 10;
		if (limit.isPresent()) {
			queryLimit = limit.get();
		}

		Pet[] outputPets = new Pet[queryLimit];

		for (int i = 0; i < queryLimit; i++) {
			Pet newPet = new Pet();
			newPet.setId(UUID.randomUUID().toString());
			newPet.setName(PetData.getRandomName());
			newPet.setBreed(PetData.getRandomBreed());
			newPet.setDateOfBirth(PetData.getRandomDoB());
			outputPets[i] = newPet;
		}

		return outputPets;
	}

	@RequestMapping(path = "/pets/{petId}", method = RequestMethod.GET)
	public Pet listPets() {
		System.out.println("=====> Getting pet by id");
		Pet newPet = new Pet();
		newPet.setId(UUID.randomUUID().toString());
		newPet.setBreed(PetData.getRandomBreed());
		newPet.setDateOfBirth(PetData.getRandomDoB());
		newPet.setName(PetData.getRandomName());
		return newPet;
	}
}
