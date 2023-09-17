package com.spl.aop;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * AOP配置类
 */
@Configuration
@ComponentScan(basePackages = "com.spl.aop")
// 开启AOP支持，该注解会使用Import注解导入后置处理器及注册自定义Bean用来完成AOP功能
@EnableAspectJAutoProxy
public class AopConfig {

}
