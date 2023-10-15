/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.beans.factory.config;

import org.springframework.beans.BeansException;

/**
 * Factory hook that allows for custom modification of an application context's
 * bean definitions, adapting the bean property values of the context's underlying
 * bean factory.
 * 注：工厂后置修改接口，用于修改应用上下文Bean的定义，以此来调整上下文内部Bean工厂的Bean属性值。
 *
 * <p>Useful for custom config files targeted at system administrators that
 * override bean properties configured in the application context. See
 * {@link PropertyResourceConfigurer} and its concrete implementations for
 * out-of-the-box solutions that address such configuration needs.
 * 注：Bean工厂后置处理器针对系统管理员的自定义配置文件非常有用，比如重写应用上下文中bean的属性配置。
 * 这个可以查看PropertyResourceConfigurer具体实现类，这是spring提供的开箱即用的现成解决方案。
 *
 * <p>A {@code BeanFactoryPostProcessor} may interact with and modify bean
 * definitions, but never bean instances. Doing so may cause premature bean
 * instantiation, violating the container and causing unintended side-effects.
 * If bean instance interaction is required, consider implementing
 * {@link BeanPostProcessor} instead.
 * 注：Bean工厂后置处理器可能会操作或修改Bean定义，但是不会操作Bean实例（因为Bean尚未被实例化）。
 * 后者可能会导致Bean过早的实例化，这违反了容器(bean工厂实例化bean)并且可能会导致意外的副作用。
 * 如果要操作Bean实例，可以考虑实现BeanPostProcessor接口。
 *
 * <h3>Registration</h3>
 * <p>An {@code ApplicationContext} auto-detects {@code BeanFactoryPostProcessor}
 * beans in its bean definitions and applies them before any other beans get created.
 * A {@code BeanFactoryPostProcessor} may also be registered programmatically
 * with a {@code ConfigurableApplicationContext}.
 * 注：Bean工厂后置处理器的注册
 * 应用上下文会自动侦测继承了BeanFactoryPostProcessor接口的Bean并且在其他所有Bean创建之前执行其功能。
 * 当然，Bean工厂后置处理器也可以通过ConfigurableApplicationContext#addBeanFactoryPostProcessor方法手动添加。
 *
 * <h3>Ordering</h3>
 * <p>{@code BeanFactoryPostProcessor} beans that are autodetected in an
 * {@code ApplicationContext} will be ordered according to
 * {@link org.springframework.core.PriorityOrdered} and
 * {@link org.springframework.core.Ordered} semantics. In contrast,
 * {@code BeanFactoryPostProcessor} beans that are registered programmatically
 * with a {@code ConfigurableApplicationContext} will be applied in the order of
 * registration; any ordering semantics expressed through implementing the
 * {@code PriorityOrdered} or {@code Ordered} interface will be ignored for
 * programmatically registered post-processors. Furthermore, the
 * {@link org.springframework.core.annotation.Order @Order} annotation is not
 * taken into account for {@code BeanFactoryPostProcessor} beans.
 * 注：多个Bean工厂后置处理器的顺序问题
 * Bean工厂后置处理器会被应用上下文自动侦测并且按照PriorityOrdered、Ordered、rest的顺序语义进行调用。
 * 相反，手动注册的Bean工厂后置处理器会按照注册的顺序进行调用。这意味着这部分的Bean工厂后置处理器将不会在考虑其他语义顺序。
 * 此外，spring中也可以通过Order注解来表示顺序语义，但是Bean后置处理器的调用顺序并不会考虑该接口的影响。
 *
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 06.07.2003
 * @see BeanPostProcessor
 * @see PropertyResourceConfigurer
 */
@FunctionalInterface
public interface BeanFactoryPostProcessor {

	/**
	 * Modify the application context's internal bean factory after its standard
	 * initialization. All bean definitions will have been loaded, but no beans
	 * will have been instantiated yet. This allows for overriding or adding
	 * properties even to eager-initializing beans.
	 * 注：通过Bean后置处理器的当前方法，可以在应用上下文中内部Bean工厂实例化后修改其实例。
	 * 在此之前所有的Bean定义均已经加载完毕，但是还没有实例化任何Bean实例。这就允许通过当前方法覆盖或增加Bean属性，
	 * 甚至对于饿汉式加载Bean也可修改。
	 * @param beanFactory the bean factory used by the application context
	 * @throws org.springframework.beans.BeansException in case of errors
	 */
	void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException;

}
