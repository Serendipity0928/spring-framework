package com.spl;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;

public class Test {
	public static void main(String[] args) {
		AbstractApplicationContext context = new AnnotationConfigApplicationContext(JavaConfig.class);
		User user = (User)context.getBean("user");
		System.out.println(user.toString());

		System.out.println(context.getId());
		System.out.println(context.getApplicationName());
		System.out.println(context.getDisplayName());
		System.out.println(context.getStartupDate());
		System.out.println(context.getParent());
		System.out.println(context.getAutowireCapableBeanFactory());

		ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
//		beanFactory.getBeanDefinition()

		context.close();
	}
}
