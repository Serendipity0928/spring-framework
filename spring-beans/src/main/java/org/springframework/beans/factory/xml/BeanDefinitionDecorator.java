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

import org.w3c.dom.Node;

import org.springframework.beans.factory.config.BeanDefinitionHolder;

/**
 * Interface used by the {@link DefaultBeanDefinitionDocumentReader}
 * to handle custom, nested (directly under a {@code <bean>}) tags.
 * 注：默认bean定义DOM读取器会使用自定义的BeanDefinitionDecorator实现类来解析<bean>下自定义的嵌套子标签，或者自定义的属性。
 *
 * <p>Decoration may also occur based on custom attributes applied to the
 * {@code <bean>} tag. Implementations are free to turn the metadata in the
 * custom tag into as many
 * {@link org.springframework.beans.factory.config.BeanDefinition BeanDefinitions} as
 * required and to transform the
 * {@link org.springframework.beans.factory.config.BeanDefinition} of the enclosing
 * {@code <bean>} tag, potentially even returning a completely different
 * {@link org.springframework.beans.factory.config.BeanDefinition} to replace the
 * original.
 * 注：装饰器也可能会基于<bean>标签上的自定义属性进行装饰bean定义。
 * 具体装饰器实例可以自由地将自定义标签的元数据转换为需要数量的多个bean定义实例，并且可能将闭包的<bean>标签转换为完全不同的bean定义(替换)。
 *
 * <p>{@link BeanDefinitionDecorator BeanDefinitionDecorators} should be aware that
 * they may be part of a chain. In particular, a {@link BeanDefinitionDecorator} should
 * be aware that a previous {@link BeanDefinitionDecorator} may have replaced the
 * original {@link org.springframework.beans.factory.config.BeanDefinition} with a
 * {link org.springframework.aop.framework.ProxyFactoryBean} definition allowing for
 * custom {link org.aopalliance.intercept.MethodInterceptor interceptors} to be added.
 * 注：bean定义装饰器必须意识到他们可能是bean定义装饰链中的一环【装饰器模式】。
 * 特别低，在使用bean定义装饰器时需要意识到，前一个装饰器可能将bean定义替换为了ProxyFactoryBean定义，这种是允许后续增加自定义的拦截器。
 *
 * <p>{@link BeanDefinitionDecorator BeanDefinitionDecorators} that wish to add an
 * interceptor to the enclosing bean should extend
 * {@link org.springframework.aop.config.AbstractInterceptorDrivenBeanDefinitionDecorator}
 * which handles the chaining ensuring that only one proxy is created and that it
 * contains all interceptors from the chain.
 * 注：期望向闭包<bean>添加拦截器的bean定义装饰器应该继承AbstractInterceptorDrivenBeanDefinitionDecorator抽象类。
 * 这个抽象类处理装饰链会确保：① 仅有一个代理会被创建；② 包含所有链上的拦截器。
 *
 * <p>The parser locates a {@link BeanDefinitionDecorator} from the
 * {@link NamespaceHandler} for the namespace in which the custom tag resides.
 * 注：自定义标签对应的装饰器是通过该自定义标签存在的命名空间处理器中获取的。
 *
 * @author Rob Harrop
 * @since 2.0
 * @see NamespaceHandler
 * @see BeanDefinitionParser
 */
public interface BeanDefinitionDecorator {

	/**
	 * Parse the specified {@link Node} (either an element or an attribute) and decorate
	 * the supplied {@link org.springframework.beans.factory.config.BeanDefinition},
	 * returning the decorated definition.
	 * 注：解析指定的DOM节点(要么嵌套标签要么属性)，装饰提供的bean定义并返回。
	 * <p>Implementations may choose to return a completely new definition, which will
	 * replace the original definition in the resulting
	 * {@link org.springframework.beans.factory.BeanFactory}.
	 * 注：该方法的实现可能会返回一个完全新的bean定义实例，替代之前bean定义实例注册到bean工厂中。
	 * <p>The supplied {@link ParserContext} can be used to register any additional
	 * beans needed to support the main definition.
	 * 注：可以利用入参中的解析上下文实例来注册任何额外的bean实例去支持主bean定义
	 */
	BeanDefinitionHolder decorate(Node node, BeanDefinitionHolder definition, ParserContext parserContext);

}
