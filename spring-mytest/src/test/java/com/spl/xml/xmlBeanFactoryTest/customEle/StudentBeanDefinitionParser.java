package com.spl.xml.xmlBeanFactoryTest.customEle;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

public class StudentBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {

	// Element对应的类
	protected  Class getBeanClass(Element element) {
		return Student.class;
	}

	// 从element中解析并提取对应的元素
	protected void doParse(Element element, BeanDefinitionBuilder beanDefinitionBuilder) {
		String name = element.getAttribute("name");
		String age = element.getAttribute("age");
		// 将提取的数据放到BeanDefinitionBuilder中，待完成所有bean的解析后统一注册到BeanFactory中
		if (StringUtils.hasText(name)) {
			beanDefinitionBuilder.addPropertyValue("name",name);
		}
		if (StringUtils.hasText(age)) {
			beanDefinitionBuilder.addPropertyValue("age",age);
		}
		beanDefinitionBuilder.getRawBeanDefinition().setBeanClass(Student.class);
	}

}
