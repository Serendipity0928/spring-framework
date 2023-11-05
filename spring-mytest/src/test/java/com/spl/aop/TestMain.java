package com.spl.aop;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class TestMain {

	public static void main(String[] args) {
		ApplicationContext acx =
				new AnnotationConfigApplicationContext(AopConfig.class);
		MathCalculator mathCalculator = (MathCalculator) acx.getBean("mathCalculator");
		mathCalculator.div(2, 1);
	}
}
