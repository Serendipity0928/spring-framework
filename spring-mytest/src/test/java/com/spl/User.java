package com.spl;

import javax.annotation.PostConstruct;

public class User {
	private String id;
	private String userName;

	@PostConstruct
	public void initPostConstruct() {
		System.out.println("初始化User：");
		System.out.println("userName：" + userName);
	}

	public User() {
	}

	public User(String id, String userName) {
		this.id = id;
		this.userName = userName;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	@Override
	public String toString() {
		return "User{" +
				"id='" + id + '\'' +
				", userName='" + userName + '\'' +
				'}';
	}
}
