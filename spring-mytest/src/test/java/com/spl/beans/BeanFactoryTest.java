package com.spl.beans;

import com.spl.beans.domain.TestBean;
import org.springframework.beans.factory.support.AbstractBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanFactory;
import org.springframework.core.io.ClassPathResource;

/**
 * bean工厂测试
 */
public class BeanFactoryTest {

	public static void main(String[] args) {
		AbstractBeanFactory bf = new XmlBeanFactory(new ClassPathResource("beans/beanFactoryTest.xml"));

//		Object bean = bf.getBean("&testBean");
		TestBean testBean = (TestBean) bf.getBean("test");
		System.out.println(testBean.getMsg());

	}

}
