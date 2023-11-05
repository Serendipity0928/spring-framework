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
import org.springframework.lang.Nullable;

/**
 * Factory hook that allows for custom modification of new bean instances &mdash;
 * for example, checking for marker interfaces or wrapping beans with proxies.
 * 注：Bean容器扩展机制-Bean后置处理器，可以针对Bean实例进行自定义修改。
 * 比如，标记接口的校验或者包装bean实例的代理。
 * 【bean后置处理器的作用对象是bean实例，意即此时bean已经被实例化了】
 *
 * <p>Typically, post-processors that populate beans via marker interfaces
 * or the like will implement {@link #postProcessBeforeInitialization},
 * while post-processors that wrap beans with proxies will normally
 * implement {@link #postProcessAfterInitialization}.
 * 注：一般情况下，类似用于根据标记接口填充bean属性值的后置处理器可能需要实现postProcessBeforeInitialization方法，在初始化动作之前执行。
 * 而对用于代理包装bean实例的后置处理器通常会实现postProcessAfterInitialization方法
 *
 * <h3>Registration</h3>
 * <p>An {@code ApplicationContext} can autodetect {@code BeanPostProcessor} beans
 * in its bean definitions and apply those post-processors to any beans subsequently
 * created. A plain {@code BeanFactory} allows for programmatic registration of
 * post-processors, applying them to all beans created through the bean factory.
 * 注：Bean后置处理器的注册
 * 应用上下文会自动侦测Bean后置处理器实例，并且在后续任何bean的创建过程中会调用这些后置处理器。
 * 对于普通的Bean工厂(容器)可以通过addBeanPostProcessors方法手动添加Bean工厂后置处理器，并且在bean工厂创建bean的过程中调用这些处理器。
 *
 * <h3>Ordering</h3>
 * <p>{@code BeanPostProcessor} beans that are autodetected in an
 * {@code ApplicationContext} will be ordered according to
 * {@link org.springframework.core.PriorityOrdered} and
 * {@link org.springframework.core.Ordered} semantics. In contrast,
 * {@code BeanPostProcessor} beans that are registered programmatically with a
 * {@code BeanFactory} will be applied in the order of registration; any ordering
 * semantics expressed through implementing the
 * {@code PriorityOrdered} or {@code Ordered} interface will be ignored for
 * programmatically registered post-processors. Furthermore, the
 * {@link org.springframework.core.annotation.Order @Order} annotation is not
 * taken into account for {@code BeanPostProcessor} beans.
 * 注：Bean后置处理器的顺序性
 * 在应用上下文中会自动侦测Bean后置处理器实例，并且会按照PriorityOrdered、Ordered两种表达顺序语义进行排序。
 * 相比之下，手动注册进容器的Bean后置处理器会按照注册的顺序处理，这里不会在考虑PriorityOrdered、Ordered的顺序语义。
 * 此外，spring内部提供的另外一个表达顺序语义的Order注解不会考虑在内。
 *
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 10.10.2003
 * @see InstantiationAwareBeanPostProcessor
 * @see DestructionAwareBeanPostProcessor
 * @see ConfigurableBeanFactory#addBeanPostProcessor
 * @see BeanFactoryPostProcessor
 */
public interface BeanPostProcessor {

	/**
	 * Apply this {@code BeanPostProcessor} to the given new bean instance <i>before</i> any bean
	 * initialization callbacks (like InitializingBean's {@code afterPropertiesSet}
	 * or a custom init-method). The bean will already be populated with property values.
	 * The returned bean instance may be a wrapper around the original.
	 * <p>The default implementation returns the given {@code bean} as-is.
	 * @param bean the new bean instance
	 * @param beanName the name of the bean
	 * @return the bean instance to use, either the original or a wrapped one;
	 * if {@code null}, no subsequent BeanPostProcessors will be invoked
	 * // 注：返回null，就不会再调用后续的bean后置处理器方法了
	 * @throws org.springframework.beans.BeansException in case of errors
	 * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet
	 */
	/**
	 * 注：将Bean后置处理器的当前方法应用于刚实例化，但尚未初始化（未执行afterPropertiesSet、init-method等方法）的bean。
	 * 通过这个方法可以填充bean的属性值，也可以对bean进行包装后返回。
	 * Bean后置处理器的方法均为接口默认方法，默认不做处理-即直接返回。为什么是默认方法呢?
	 * Bean后置处理器提供的两个方法分别作用于bean的不同阶段，就可以实现不同的功能。因此，往往可能一个后置处理器仅需实现其中一个方法，而不需要去关心另外一个方法。
	 */
	@Nullable
	default Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}

	/**
	 * Apply this {@code BeanPostProcessor} to the given new bean instance <i>after</i> any bean
	 * initialization callbacks (like InitializingBean's {@code afterPropertiesSet}
	 * or a custom init-method). The bean will already be populated with property values.
	 * The returned bean instance may be a wrapper around the original.
	 * 注：将Bean后置处理器的当前方法应用于已经被实例化、初始化后的bean。
	 * 通过这个方法可以填充bean的属性值，也可以对bean进行包装后返回。
	 * <p>In case of a FactoryBean, this callback will be invoked for both the FactoryBean
	 * instance and the objects created by the FactoryBean (as of Spring 2.0). The
	 * post-processor can decide whether to apply to either the FactoryBean or created
	 * objects or both through corresponding {@code bean instanceof FactoryBean} checks.
	 * 注：在工厂bean类型场景，这个回调方法会在FactoryBean实例本身以及其创建出的bean实力初始化后调用。
	 * 可以在方法内部判断bean是否为FactoryBean类型来决定需要都处理或者处理其中一种。
	 * <p>This callback will also be invoked after a short-circuiting triggered by a
	 * {@link InstantiationAwareBeanPostProcessor#} method,
	 * in contrast to all other {@code BeanPostProcessor} callbacks.
	 * 注：相比于前置方法，这个后置方法在InstantiationAwareBeanPostProcessor短路场景仍然会执行，而前置方法不会被执行。
	 * <p>The default implementation returns the given {@code bean} as-is.
	 * 注：当前方法如上默认，也是返回bean实例本身
	 * @param bean the new bean instance
	 * @param beanName the name of the bean
	 * @return the bean instance to use, either the original or a wrapped one;
	 * if {@code null}, no subsequent BeanPostProcessors will be invoked
	 * @throws org.springframework.beans.BeansException in case of errors
	 * @see org.springframework.beans.factory.InitializingBean#afterPropertiesSet
	 * @see org.springframework.beans.factory.FactoryBean
	 */
	/**
	 * 在bean初始化之后会被调用
	 * 在调用InitializingBean的afterPropertiesSet方法或者init-method指定的方法执行之后调用.
	 */
	@Nullable
	default Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}

}
