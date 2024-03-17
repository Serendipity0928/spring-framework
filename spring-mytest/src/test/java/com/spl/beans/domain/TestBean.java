package com.spl.beans.domain;

import org.springframework.beans.factory.InitializingBean;

import javax.annotation.PostConstruct;


public class TestBean  {

	private String msg;

//	public TestBean() {
//		System.out.println("TestBean。。。");
//	}

	@PostConstruct
	public void PostConstruct() {
		System.out.println("PostConstruct运行...");
	}

	public String getMsg() {
		return msg;
	}

	public void setMsg(String msg) {
		this.msg = msg;
	}

//	@Override
//	public void afterPropertiesSet() throws Exception {
//		System.out.println("afterPropertiesSet运行...");
//	}
}
