package com.spl.context.pojo;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

//@Component
public class PostConstructBaseTest {

	@PostConstruct
	private void postConstruct() {
		System.out.println("PostConstructBaseTest#postConstruct...");
	}


//	@Override
//	public void afterPropertiesSet() throws Exception {
//		System.out.println("PostConstructBaseTest#afterPropertiesSet...");
//	}
}
