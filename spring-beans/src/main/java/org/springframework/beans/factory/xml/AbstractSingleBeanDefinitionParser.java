/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.beans.factory.xml;

import org.w3c.dom.Element;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.lang.Nullable;

/**
 * Base class for those {@link BeanDefinitionParser} implementations that
 * need to parse and define just a <i>single</i> {@code BeanDefinition}.
 * 注：AbstractSingleBeanDefinitionParser是许多bean定义解析器实现类的基本抽象类。【基本自定义解析器也是继承该抽象类】
 * 该抽象类仅需要关注如何解析以及定义bean定义。【至于后续bean定义封装、注册、事件触发都不需要感知，在AbstractBeanDefinitionParser已存在】
 *
 * <p>Extend this parser class when you want to create a single bean definition
 * from an arbitrarily complex XML element. You may wish to consider extending
 * the {@link AbstractSimpleBeanDefinitionParser} when you want to create a
 * single bean definition from a relatively simple custom XML element.
 * 注：当你想从任意一个复杂的XML元素中解析创建一个bean定义可以继承该解析类。【注意这里是一个哦...】
 * 当你向从一个相对简单的自定义XML元素中解析创建一个bean定义可以考虑扩展AbstractSimpleBeanDefinitionParser类。
 *
 * <p>The resulting {@code BeanDefinition} will be automatically registered
 * with the {@link org.springframework.beans.factory.support.BeanDefinitionRegistry}.
 * Your job simply is to {@link #doParse parse} the custom XML {@link Element}
 * into a single {@code BeanDefinition}.
 * 注：bean定义实例结果将会被自动注册到bean定义注册中心。
 * 你的任务仅需关注如何将自定义的xml节点解析为一个bean定义，即doParse方法。
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Rick Evans
 * @since 2.0
 * @see #getBeanClass
 * @see #getBeanClassName
 * @see #doParse
 */
// 注：实现当前类的子类必须指定bean定义的类型或类名
public abstract class AbstractSingleBeanDefinitionParser extends AbstractBeanDefinitionParser {

	/**
	 * Creates a {@link BeanDefinitionBuilder} instance for the
	 * {@link #getBeanClass bean Class} and passes it to the
	 * {@link #doParse} strategy method.
	 * 注：创建一个bean类型对应的BeanDefinitionBuilder实例，并且传给doParse方法策略方法。
	 * - AbstractSingleBeanDefinitionParser也是继承了AbstractBeanDefinitionParser类。
	 * - 因此这里实现了parseInternal方法。注意该方法使用final修饰，因此这也是一个模版方法。
	 * @param element the element that is to be parsed into a single BeanDefinition
	 * @param parserContext the object encapsulating the current state of the parsing process
	 * @return the BeanDefinition resulting from the parsing of the supplied {@link Element}
	 * @throws IllegalStateException if the bean {@link Class} returned from
	 * {@link #getBeanClass(org.w3c.dom.Element)} is {@code null}
	 * @see #doParse
	 */
	@Override
	protected final AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
		/**
		 * 注：创建一个BeanDefinitionBuilder实例，并且内置一个空bean定义实例(类型为GenericBeanDefinition)。
		 * - 后续对于bean定义数据的注入都是通过BeanDefinitionBuilder来进行。【建造者模式】
		 */
		BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition();
		// 注：获取当前bean定义的父bean定义名
		String parentName = getParentName(element);
		if (parentName != null) {
			builder.getRawBeanDefinition().setParentName(parentName);
		}
		// 注：获取当前bean定义的bean类型
		Class<?> beanClass = getBeanClass(element);
		if (beanClass != null) {
			builder.getRawBeanDefinition().setBeanClass(beanClass);
		}
		else {
			// 注：在未指定bean实例类型时，会获取当前bean类型名
			String beanClassName = getBeanClassName(element);
			if (beanClassName != null) {
				builder.getRawBeanDefinition().setBeanClassName(beanClassName);
			}
		}
		// 注：设置bean定义的来源
		builder.getRawBeanDefinition().setSource(parserContext.extractSource(element));
		BeanDefinition containingBd = parserContext.getContainingBeanDefinition();
		if (containingBd != null) {
			// Inner bean definition must receive same scope as containing bean.
			// 注：内部bean定义需和外部bean的作用域保持一致
			builder.setScope(containingBd.getScope());
		}
		if (parserContext.isDefaultLazyInit()) {
			// Default-lazy-init applies to custom bean definitions as well.
			// 注：<beans>标签上的懒加载默认配置也会应用到自定义bean定义上。【默认情况下为false】
			builder.setLazyInit(true);
		}
		// 注：其他bean定义相关内容由子类来实现注入。
		doParse(element, parserContext, builder);
		// 注：返回bean定义(内部会对自定义的bean定义进行验证)
		return builder.getBeanDefinition();
	}

	/**
	 * Determine the name for the parent of the currently parsed bean,
	 * in case of the current bean being defined as a child bean.
	 * 注：该方法返回当前解析bean的父bean名称
	 * <p>The default implementation returns {@code null},
	 * indicating a root bean definition.
	 * 注：默认的实现返回null，即是根bean定义，不存在父bean定义
	 * @param element the {@code Element} that is being parsed
	 * @return the name of the parent bean for the currently parsed bean,
	 * or {@code null} if none
	 */
	@Nullable
	protected String getParentName(Element element) {
		return null;
	}

	/**
	 * Determine the bean class corresponding to the supplied {@link Element}.
	 * 注：返回对应表示bean定义的自定义节点对应的bean类型
	 * <p>Note that, for application classes, it is generally preferable to
	 * override {@link #getBeanClassName} instead, in order to avoid a direct
	 * dependence on the bean implementation class. The BeanDefinitionParser
	 * and its NamespaceHandler can be used within an IDE plugin then, even
	 * if the application classes are not available on the plugin's classpath.
	 * 注意：对于应用程序类，为了避免直接依赖bean实现类，通常更推荐重写getBeanClassName方法。
	 * 应用程序类可能在IDE插件的类路径上找不到，此处IDE插件在使用BeanDefinitionParser时可能会存在问题。
	 * @param element the {@code Element} that is being parsed
	 * @return the {@link Class} of the bean that is being defined via parsing
	 * the supplied {@code Element}, or {@code null} if none
	 * @see #getBeanClassName
	 */
	@Nullable
	protected Class<?> getBeanClass(Element element) {
		return null;
	}

	/**
	 * Determine the bean class name corresponding to the supplied {@link Element}.
	 * 注：返回对应表示bean定义的自定义节点对应的bean类名
	 * @param element the {@code Element} that is being parsed
	 * @return the class name of the bean that is being defined via parsing
	 * the supplied {@code Element}, or {@code null} if none
	 * @see #getBeanClass
	 */
	@Nullable
	protected String getBeanClassName(Element element) {
		return null;
	}

	/**
	 * Parse the supplied {@link Element} and populate the supplied
	 * {@link BeanDefinitionBuilder} as required.
	 * 注：解析指定的自定义标签并向BeanDefinitionBuilder中的bean定义填充数据
	 * <p>The default implementation delegates to the {@code doParse}
	 * version without ParserContext argument.
	 * - 默认的实现会委托给没有解析上下文的doParse重载方法。
	 * - 【如果开发者熟悉解析上下文可以重写该方法，否则重写其重载方法】
	 * @param element the XML element being parsed
	 * @param parserContext the object encapsulating the current state of the parsing process
	 * @param builder used to define the {@code BeanDefinition}
	 * @see #doParse(Element, BeanDefinitionBuilder)
	 */
	protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
		doParse(element, builder);
	}

	/**
	 * Parse the supplied {@link Element} and populate the supplied
	 * {@link BeanDefinitionBuilder} as required.
	 * 注：解析指定的自定义标签并向BeanDefinitionBuilder中的bean定义填充数据
	 * <p>The default implementation does nothing.
	 * 注：默认的实现什么也不会做
	 * 【注意】子类未必就必须实现doParse方法，但子类至少得bean类型或bean类型名，否则会报错。
	 * - 在doParse方法中也可以直接指定bean类型名。因此要么实现getBeanClass或getBeanClassName，要么实现doParse方法并指定bean类型。
	 * @param element the XML element being parsed
	 * @param builder used to define the {@code BeanDefinition}
	 */
	protected void doParse(Element element, BeanDefinitionBuilder builder) {
	}

}
