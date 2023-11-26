package com.spl.xml.xmlBeanFactoryTest;

import com.spl.xml.xmlBeanFactoryTest.bean.Person;
import org.springframework.beans.factory.support.AbstractBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.context.expression.StandardBeanExpressionResolver;
import org.springframework.core.io.ClassPathResource;

public class Main {

	public static void main(String[] args) throws ClassNotFoundException {
//		System.setProperty("spring.profiles.active", "test");
//		System.setProperty("spring.profiles.default", "spl");

//		System.setProperty("spl.tmp", "spl1.xml");

		AbstractBeanFactory bf = new XmlBeanFactory(new ClassPathResource("xml/xmlBeanFactoryTest/spring.xml"));

//		Object bean = bf.getBean("&testBean");
		Object bean1 = bf.getBean("testBean");
		System.out.println("??");
	}

}
