package com.spl;

import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class MyTest {
	@Test
	public void test1() {
		ClassPathXmlApplicationContext applicationContext = new ClassPathXmlApplicationContext("spring-test.xml");
		Student student = (Student)applicationContext.getBean("student");
		System.out.println(student.getUsername());
		System.out.println("我获取用户名了："+student.getUsername());
	}
}