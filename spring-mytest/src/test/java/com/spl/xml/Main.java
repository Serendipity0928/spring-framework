package com.spl.xml;

import com.spl.xml.pojo.Company;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class Main {

	public static void main(String[] args) {
		ApplicationContext applicationContext = new ClassPathXmlApplicationContext("xml/spring-context.xml");
		Company company = (Company) applicationContext.getBean("company");
		System.out.println(company.getBoss().getName());
	}
}