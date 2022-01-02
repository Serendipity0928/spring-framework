package com.spl;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan
public class JavaConfig {
	@Bean
	public User user(){
		return new User("1108","孙大哥");
	}
}
