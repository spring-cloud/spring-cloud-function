package io.spring.cloudevent;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

public class Employee {

	private Person person;

	private int id;

	public Employee() {

	}

	public Employee(Person person) {
		this.person = person;
		this.id = new Random().nextInt(1000);
	}

	public Person getPerson() {
		return person;
	}

	public void setPerson(Person person) {
		this.person = person;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getMessage() {
		return "Employee " + id + " was hired on " + new SimpleDateFormat("dd-MM-yyyy").format(new Date());
	}
}
