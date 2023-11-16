package com.spl.xml.xmlBeanFactoryTest;

import com.spl.xml.xmlBeanFactoryTest.bean.MyTestBean;
import com.spl.xml.xmlBeanFactoryTest.customEle.Student;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.ClassPathResource;

public class Main {

	public static void main(String[] args) {
//		System.setProperty("spring.profiles.active", "test");
//		System.setProperty("spring.profiles.default", "spl");

		System.setProperty("spl.tmp", "spl1.xml");

		BeanFactory bf = new XmlBeanFactory(new ClassPathResource("xml/xmlBeanFactoryTest/spring.xml"));
		Student bean = (Student) bf.getBean("student");
		System.out.println(bean.getName());

	}

}
