package com.spl.xml.xmlBeanFactoryTest.bean;

import java.util.Map;

public class MyTestBean {

	private String testStr = "testStr";

	private Person person;

	private Map<String, String> testMap;

	public MyTestBean() {

	}

	public String getTestStr() {
		return testStr;
	}

	public void setTestStr(String testStr) {
		this.testStr = testStr;
	}

	public Person getPerson() {
		return person;
	}

	public void setPerson(Person person) {
		this.person = person;
	}

	public Map<String, String> getTestMap() {
		return testMap;
	}

	public void setTestMap(Map<String, String> testMap) {
		this.testMap = testMap;
	}
}
