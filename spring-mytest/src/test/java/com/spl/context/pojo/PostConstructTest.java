package com.spl.context.pojo;

import javax.annotation.PostConstruct;

public class PostConstructTest {

	@PostConstruct
	public void postConstruct1() {
		System.out.println("postConstruct...");
	}

}
