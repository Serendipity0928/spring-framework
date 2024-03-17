package com.spl.context;

import org.springframework.context.annotation.CommonAnnotationBeanPostProcessor;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Main {

	public static void main(String[] args) {

		ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext("context/spring-context.xml");
		Object postConstruct = ac.getBean("postConstruct");
		System.out.println(postConstruct);
	}

}
