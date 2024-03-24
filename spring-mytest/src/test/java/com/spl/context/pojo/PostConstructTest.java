package com.spl.context.pojo;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

public class PostConstructTest extends PostConstructBaseTest implements InitializingBean {

	@PostConstruct
	public Object postConstruct1() {
		System.out.println("PostConstructTest#postConstruct...");
		return 1;
	}

	@Override
	public void afterPropertiesSet() throws Exception {

	}
}
