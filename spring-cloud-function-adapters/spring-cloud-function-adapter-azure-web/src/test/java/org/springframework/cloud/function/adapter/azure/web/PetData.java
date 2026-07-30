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


import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class PetData {
	private PetData() {

	}
	private static final List<String> BREEDS = new ArrayList<>();
	static {
		BREEDS.add("Afghan Hound");
		BREEDS.add("Beagle");
		BREEDS.add("Bernese Mountain Dog");
		BREEDS.add("Bloodhound");
		BREEDS.add("Dalmatian");
		BREEDS.add("Jack Russell Terrier");
		BREEDS.add("Norwegian Elkhound");
	}

	private static final List<String> NAMES = new ArrayList<>();
	static {
		NAMES.add("Bailey");
		NAMES.add("Bella");
		NAMES.add("Max");
		NAMES.add("Lucy");
		NAMES.add("Charlie");
		NAMES.add("Molly");
		NAMES.add("Buddy");
		NAMES.add("Daisy");
		NAMES.add("Rocky");
		NAMES.add("Maggie");
		NAMES.add("Jake");
		NAMES.add("Sophie");
		NAMES.add("Jack");
		NAMES.add("Sadie");
		NAMES.add("Toby");
		NAMES.add("Chloe");
		NAMES.add("Cody");
		NAMES.add("Bailey");
		NAMES.add("Buster");
		NAMES.add("Lola");
		NAMES.add("Duke");
		NAMES.add("Zoe");
		NAMES.add("Cooper");
		NAMES.add("Abby");
		NAMES.add("Riley");
		NAMES.add("Ginger");
		NAMES.add("Harley");
		NAMES.add("Roxy");
		NAMES.add("Bear");
		NAMES.add("Gracie");
		NAMES.add("Tucker");
		NAMES.add("Coco");
		NAMES.add("Murphy");
		NAMES.add("Sasha");
		NAMES.add("Lucky");
		NAMES.add("Lily");
		NAMES.add("Oliver");
		NAMES.add("Angel");
		NAMES.add("Sam");
		NAMES.add("Princess");
		NAMES.add("Oscar");
		NAMES.add("Emma");
		NAMES.add("Teddy");
		NAMES.add("Annie");
		NAMES.add("Winston");
		NAMES.add("Rosie");
	}

	public static List<String> getBreeds() {
		return BREEDS;
	}

	public static List<String> getNames() {
		return NAMES;
	}

	public static String getRandomBreed() {
		return BREEDS.get(ThreadLocalRandom.current().nextInt(0, BREEDS.size() - 1));
	}

	public static String getRandomName() {
		return NAMES.get(ThreadLocalRandom.current().nextInt(0, NAMES.size() - 1));
	}

	public static Date getRandomDoB() {
		GregorianCalendar gc = new GregorianCalendar();

		int year = ThreadLocalRandom.current().nextInt(Calendar.getInstance().get(Calendar.YEAR) - 15,
				Calendar.getInstance().get(Calendar.YEAR));

		gc.set(Calendar.YEAR, year);

		int dayOfYear = ThreadLocalRandom.current().nextInt(1, gc.getActualMaximum(Calendar.DAY_OF_YEAR));

		gc.set(Calendar.DAY_OF_YEAR, dayOfYear);
		return gc.getTime();
	}
}
