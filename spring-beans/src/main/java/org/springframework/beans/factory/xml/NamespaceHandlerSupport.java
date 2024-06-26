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

import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.lang.Nullable;

/**
 * Support class for implementing custom {@link NamespaceHandler NamespaceHandlers}.
 * Parsing and decorating of individual {@link Node Nodes} is done via {@link BeanDefinitionParser}
 * and {@link BeanDefinitionDecorator} strategy interfaces, respectively.
 * 注：NamespaceHandlerSupport是自定义命名空间处理器的支持类。自定义处理器通过继承该类只需要实现init方法，并注册不同自定义标签的解析器即可。
 * NamespaceHandlerSupport分别通过BeanDefinitionParser、BeanDefinitionDecorator两个解析策略来完成单个DOM节点的bean解析以及装饰功能。
 *
 * <p>Provides the {@link #registerBeanDefinitionParser} and {@link #registerBeanDefinitionDecorator}
 * methods for registering a {@link BeanDefinitionParser} or {@link BeanDefinitionDecorator}
 * to handle a specific element.
 * 注：NamespaceHandlerSupport提供了registerBeanDefinitionParser以及registerBeanDefinitionDecorator方法来注册bean定义处理器
 * 或者bean定义装饰器去处理一个指定DOM元素。
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @since 2.0
 * @see #registerBeanDefinitionParser(String, BeanDefinitionParser)
 * @see #registerBeanDefinitionDecorator(String, BeanDefinitionDecorator)
 */
public abstract class NamespaceHandlerSupport implements NamespaceHandler {

	/**
	 * Stores the {@link BeanDefinitionParser} implementations keyed by the
	 * local name of the {@link Element Elements} they handle.
	 * 注：缓存DOM元素的基础名称(冒号之后)到bean定义解析器的映射
	 * - 这个map的key表示的标签都表示了一个bean定义。换句话说，key表示的标签名会处于<beans>标签下。
	 */
	private final Map<String, BeanDefinitionParser> parsers = new HashMap<>();

	/**
	 * Stores the {@link BeanDefinitionDecorator} implementations keyed by the
	 * local name of the {@link Element Elements} they handle.
	 * 注：缓存DOM元素的基础名称(冒号之后)到bean定义装饰器的映射
	 * - 这个map的key表示的标签都表示增加bean定义数据。换句话说，key表示的标签名会处于<bean>标签下。
	 */
	private final Map<String, BeanDefinitionDecorator> decorators = new HashMap<>();

	/**
	 * Stores the {@link BeanDefinitionDecorator} implementations keyed by the local
	 * name of the {@link Attr Attrs} they handle.
	 * 注：缓存DOM属性的基础名称到bean定义装饰器的映射
	 * - 这个map的key表示的属性都表示增加bean定义数据。换句话说，key表示的属性名会处于<bean>标签或其下某个标签的属性下。
	 */
	private final Map<String, BeanDefinitionDecorator> attributeDecorators = new HashMap<>();


	/**
	 * Parses the supplied {@link Element} by delegating to the {@link BeanDefinitionParser} that is
	 * registered for that {@link Element}.
	 * 注：将解析指定自定义标签的任务委托给已注册的自定义bean定义解析器。
	 * 自定义bean定义解析器由registerBeanDefinitionParser方法注册，该方法权限类型为protected，基本由Support子类的init方法注入。
	 */
	@Override
	@Nullable
	public BeanDefinition parse(Element element, ParserContext parserContext) {
		// 注：根据当前DOM节点标签的基础名(冒号后面)来找到bean定义解析器。
		BeanDefinitionParser parser = findParserForElement(element, parserContext);
		/**
		 * 注：通过自定义bean定义解析器来解析对应的标签内容。
		 * 每一个自定义的标签都必须有对应的解析器。
		 */
		return (parser != null ? parser.parse(element, parserContext) : null);
	}

	/**
	 * Locates the {@link BeanDefinitionParser} from the register implementations using
	 * the local name of the supplied {@link Element}.
	 * 注：根据当前DOM节点标签的基础名(冒号后面)来找到注册的自定义bean定义解析器。
	 */
	@Nullable
	private BeanDefinitionParser findParserForElement(Element element, ParserContext parserContext) {
		// 注：通过BeanDefinitionParserDelegate来获取当前DOM节点标签的基础名
		String localName = parserContext.getDelegate().getLocalName(element);
		// 注：从this.parsers缓存中找到bean定义解析器
		BeanDefinitionParser parser = this.parsers.get(localName);
		if (parser == null) {
			// 注：不存在就无法解析对应的标签，抛出异常
			parserContext.getReaderContext().fatal(
					"Cannot locate BeanDefinitionParser for element [" + localName + "]", element);
		}
		return parser;
	}

	/**
	 * Decorates the supplied {@link Node} by delegating to the {@link BeanDefinitionDecorator} that
	 * is registered to handle that {@link Node}.
	 * 注：将解析指定自定义标签并装饰bean定义的任务委托给已注册的自定义bean定义装饰器。
	 * 自定义bean定义装饰器由registerBeanDefinitionDecorator(装饰内嵌节点)以及registerBeanDefinitionDecoratorForAttribute(装饰属性)方法注册，
	 * 该方法权限类型为protected，基本由Support子类的init方法注入。
	 */
	@Override
	@Nullable
	public BeanDefinitionHolder decorate(
			Node node, BeanDefinitionHolder definition, ParserContext parserContext) {
		// 注：根据当前DOM节点的基础名(冒号后面)来找到bean定义装饰器。
		BeanDefinitionDecorator decorator = findDecoratorForNode(node, parserContext);
		/**
		 * 注：通过自定义bean定义装饰器来解析对应的标签内容并装饰bean定义。
		 * 每一个自定义的标签都必须有对应的装饰器。
		 */
		return (decorator != null ? decorator.decorate(node, definition, parserContext) : null);
	}

	/**
	 * Locates the {@link BeanDefinitionParser} from the register implementations using
	 * the local name of the supplied {@link Node}. Supports both {@link Element Elements}
	 * and {@link Attr Attrs}.
	 * 注：根据提供的DOM节点的基础名来定位bean定义的解析器。DOM节点支持自定义标签节点以及自定义属性。
	 */
	@Nullable
	private BeanDefinitionDecorator findDecoratorForNode(Node node, ParserContext parserContext) {
		BeanDefinitionDecorator decorator = null;
		String localName = parserContext.getDelegate().getLocalName(node);
		if (node instanceof Element) {
			// 注：如果未内嵌标签节点，通过这里获取装饰器
			decorator = this.decorators.get(localName);
		}
		else if (node instanceof Attr) {
			// 注：如果自定义属性，通过这里获取装饰器
			decorator = this.attributeDecorators.get(localName);
		}
		else {
			// 注：DOM节点非标签节点或属性，报错，抛出异常
			parserContext.getReaderContext().fatal(
					"Cannot decorate based on Nodes of type [" + node.getClass().getName() + "]", node);
		}
		if (decorator == null) {
			// 注：未找到装饰器，报错，抛出异常
			parserContext.getReaderContext().fatal("Cannot locate BeanDefinitionDecorator for " +
					(node instanceof Element ? "element" : "attribute") + " [" + localName + "]", node);
		}
		return decorator;
	}


	/**
	 * Subclasses can call this to register the supplied {@link BeanDefinitionParser} to
	 * handle the specified element. The element name is the local (non-namespace qualified)
	 * name.
	 */
	protected final void registerBeanDefinitionParser(String elementName, BeanDefinitionParser parser) {
		this.parsers.put(elementName, parser);
	}

	/**
	 * Subclasses can call this to register the supplied {@link BeanDefinitionDecorator} to
	 * handle the specified element. The element name is the local (non-namespace qualified)
	 * name.
	 */
	protected final void registerBeanDefinitionDecorator(String elementName, BeanDefinitionDecorator dec) {
		this.decorators.put(elementName, dec);
	}

	/**
	 * Subclasses can call this to register the supplied {@link BeanDefinitionDecorator} to
	 * handle the specified attribute. The attribute name is the local (non-namespace qualified)
	 * name.
	 */
	protected final void registerBeanDefinitionDecoratorForAttribute(String attrName, BeanDefinitionDecorator dec) {
		this.attributeDecorators.put(attrName, dec);
	}

}
