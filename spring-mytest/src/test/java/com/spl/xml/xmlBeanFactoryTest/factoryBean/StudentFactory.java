package com.spl.xml.xmlBeanFactoryTest.factoryBean;

import com.spl.xml.xmlBeanFactoryTest.customEle.Student;
import org.springframework.beans.factory.FactoryBean;

public class StudentFactory implements FactoryBean {

	private int age;

	public StudentFactory(String age) {
		this.age = Integer.valueOf(age);
	}

	public StudentFactory(int age) {
		this.age = age;
	}

	@Override
	public Object getObject() throws Exception {
		return new Student();
	}

	@Override
	public Class<?> getObjectType() {
		return Student.class;
	}
}
