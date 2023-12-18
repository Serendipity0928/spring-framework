package com.spl.context;

import com.spl.context.pojo.Person;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.SpringProperties;

public class Main {

	public static void main(String[] args) {

		ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("context/spring-context.xml");
//		Person person = (Person) ac.getBean("person");
//		System.out.println(person.getAge());
//
//		ac.afterPropertiesSet();


	}

}
