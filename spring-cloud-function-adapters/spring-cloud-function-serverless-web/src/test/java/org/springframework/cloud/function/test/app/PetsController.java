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

package org.springframework.cloud.function.test.app;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;


@RestController
@EnableWebMvc
public class PetsController {

	@RequestMapping(path = "/petsAsync/", method = RequestMethod.POST)
	public DeferredResult<Pet>  createPetAsync(@RequestBody Pet newPet) {
		if (newPet.getName() == null || newPet.getBreed() == null) {
			return null;
		}

		Pet dbPet = newPet;
		dbPet.setId(UUID.randomUUID().toString());
		DeferredResult<Pet> result = new DeferredResult<Pet>();
		result.setResult(dbPet);
		return result;
	}

	@RequestMapping(path = "/pets/", method = RequestMethod.POST)
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
	public Pet listPets(@PathVariable String petId) {
		if (petId.equals("2")) {
			throw new DogNotFoundException();
		}
		Pet newPet = new Pet();
		newPet.setId(UUID.randomUUID().toString());
		newPet.setBreed(PetData.getRandomBreed());
		newPet.setDateOfBirth(PetData.getRandomDoB());
		newPet.setName(PetData.getRandomName());
		return newPet;
	}

	@GetMapping("/foo/deny")
	public Pet foo() {
		Pet newPet = new Pet();
		newPet.setId(UUID.randomUUID().toString());
		newPet.setBreed(PetData.getRandomBreed());
		newPet.setDateOfBirth(PetData.getRandomDoB());
		newPet.setName(PetData.getRandomName());
		return newPet;
	}

	@ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "No such Dog") // 404
	public class DogNotFoundException extends RuntimeException {
		// ...
	}
}
