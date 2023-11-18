/*
 * Copyright 2002-2012 the original author or authors.
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
import org.w3c.dom.Node;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.lang.Nullable;

/**
 * Base interface used by the {@link DefaultBeanDefinitionDocumentReader}
 * for handling custom namespaces in a Spring XML configuration file.
 * 注：DefaultBeanDefinitionDocumentReader-默认bean定义DOM树读取器
 * NamespaceHandler-命名空间处理器是在解析XML DOM树中的bean定义时需要解析自定义标签的基本接口。
 *
 * <p>Implementations are expected to return implementations of the
 * {@link BeanDefinitionParser} interface for custom top-level tags and
 * implementations of the {@link BeanDefinitionDecorator} interface for
 * custom nested tags.
 * 注：命名空间处理器需要返回自定义的bean标签的解析器，即BeanDefinitionParser，
 * 以及自定义内嵌标签的装饰器，即BeanDefinitionDecorator;
 *
 * <p>The parser will call {@link #parse} when it encounters a custom tag
 * directly under the {@code <beans>} tags and {@link #decorate} when
 * it encounters a custom tag directly under a {@code <bean>} tag.
 * 注：当遇到<beans>标签内的自定义标签时，命名空间处理器会调用其parse方法返回bean定义实例。
 * 当遇到<bean>标签内嵌的自定义标签是，命名空间处理器会调用其decorate方法来对指定的bean定义进行装饰。
 *
 * <p>Developers writing their own custom element extensions typically will
 * not implement this interface directly, but rather make use of the provided
 * {@link NamespaceHandlerSupport} class.
 * 注：开发人员增加自定义标签时通常不会直接实现这个接口，
 * 而是利用spring提供的NamespaceHandlerSupport类，即继承NamespaceHandlerSupport类的能力，而仅需要实现Init方法即可。
 * 在init方法中可初始化NamespaceHandlerSupport中的注册三种解析器：parsers、decorators、attributeDecorators
 *
 * @author Rob Harrop
 * @author Erik Wiersma
 * @since 2.0
 * @see DefaultBeanDefinitionDocumentReader
 * @see NamespaceHandlerResolver
 */
public interface NamespaceHandler {

	/**
	 * Invoked by the {@link DefaultBeanDefinitionDocumentReader} after
	 * construction but before any custom elements are parsed.
	 * 注：DefaultBeanDefinitionDocumentReader：通过DOM树来解析bean定义文件
	 * 其中在解析自定义标签之前需要获取对应自定义标签的命名空间处理器实例。
	 * 而在实例化命名空间之后就会调用该初始化方法。---参见：DefaultNamespaceHandlerResolver#resolve
	 * - 还有个问题是，命名空间处理器初始化什么呢？初始化自定义bean标签的解析器。即下面这个方法：
	 * @see NamespaceHandlerSupport#registerBeanDefinitionParser(String, BeanDefinitionParser)
	 * - 实际上很少会有解析器直接实现当前接口，而都是继承NamespaceHandlerSupport抽象类，开发者自定义处理器时只需要实现init方法即可，
	 * 在init方法中可初始化NamespaceHandlerSupport中的注册三种解析器：parsers、decorators、attributeDecorators
	 */
	void init();

	/**
	 * Parse the specified {@link Element} and register any resulting
	 * {@link BeanDefinition BeanDefinitions} with the
	 * {@link org.springframework.beans.factory.support.BeanDefinitionRegistry}
	 * that is embedded in the supplied {@link ParserContext}.
	 * 注：解析指定的自定义的bean标签，并且将解析后的bean定义注册到bean定义注册中心去。(相关上下文数据存储在ParserContext中)
	 * <p>Implementations should return the primary {@code BeanDefinition}
	 * that results from the parse phase if they wish to be used nested
	 * inside (for example) a {@code <property>} tag.
	 * 注：如果自定义标签希望嵌套含有值标签(比如<property>)的内部使用时，对应的命名空间实现类的parse方法应在解析阶段返回最初的bean定义(非BeanDefinitionHolder)。
	 * <p>Implementations may return {@code null} if they will
	 * <strong>not</strong> be used in a nested scenario.
	 * 注：如果自定义标签不会再嵌套场景中作为值来使用，该解析方法返回的bean定义可能是个null值。
	 * @param element the element that is to be parsed into one or more {@code BeanDefinitions}
	 * @param parserContext the object encapsulating the current state of the parsing process
	 * @return the primary {@code BeanDefinition} (can be {@code null} as explained above)
	 * 注：大部分自定义标签的解析器都直接继承NamespaceHandlerSupport，该支持类实现了解析(parse)逻辑。
	 */
	@Nullable
	BeanDefinition parse(Element element, ParserContext parserContext);

	/**
	 * Parse the specified {@link Node} and decorate the supplied
	 * {@link BeanDefinitionHolder}, returning the decorated definition.
	 * 注：解析指定的节点，并且装饰提供的bean定义后返回。
	 * <p>The {@link Node} may be either an {@link org.w3c.dom.Attr} or an
	 * {@link Element}, depending on whether a custom attribute or element
	 * is being parsed.
	 * <p>Implementations may choose to return a completely new definition,
	 * which will replace the original definition in the resulting
	 * {@link org.springframework.beans.factory.BeanFactory}.
	 * <p>The supplied {@link ParserContext} can be used to register any
	 * additional beans needed to support the main definition.
	 * @param source the source element or attribute that is to be parsed
	 * @param definition the current bean definition
	 * @param parserContext the object encapsulating the current state of the parsing process
	 * @return the decorated definition (to be registered in the BeanFactory),
	 * or simply the original bean definition if no decoration is required.
	 * A {@code null} value is strictly speaking invalid, but will be leniently
	 * treated like the case where the original bean definition gets returned.
	 */
	@Nullable
	BeanDefinitionHolder decorate(Node source, BeanDefinitionHolder definition, ParserContext parserContext);

}
