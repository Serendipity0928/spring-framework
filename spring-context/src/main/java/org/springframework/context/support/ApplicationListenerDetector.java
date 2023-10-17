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

package org.springframework.context.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.lang.Nullable;
import org.springframework.util.ObjectUtils;

/**
 * {@code BeanPostProcessor} that detects beans which implement the {@code ApplicationListener}
 * interface. This catches beans that can't reliably be detected by {@code getBeanNamesForType}
 * and related operations which only work against top-level beans.
 * 注：应用监听器侦测器实际上是一个Bean后置处理器，在bean初始化之后，会检测当前bean是否实现了ApplicationListener接口，即为应用监听器实例。
 * 但是判断是否为监听器不能够依赖getBeanNamesForType方法，因为可能由于其他bean后置处理器的原因，导致监听器实例会被包装。
 * 并且，侦测器只会侦测出非内置bean的监听器实例。
 *
 * <p>With standard Java serialization, this post-processor won't get serialized as part of
 * {@code DisposableBeanAdapter} to begin with. However, with alternative serialization
 * mechanisms, {@code DisposableBeanAdapter.writeReplace} might not get used at all, so we
 * defensively mark this post-processor's field state as {@code transient}.
 *
 * @author Juergen Hoeller
 * @since 4.3.4
 */
class ApplicationListenerDetector implements DestructionAwareBeanPostProcessor, MergedBeanDefinitionPostProcessor {

	private static final Log logger = LogFactory.getLog(ApplicationListenerDetector.class);

	private final transient AbstractApplicationContext applicationContext;

	private final transient Map<String, Boolean> singletonNames = new ConcurrentHashMap<>(256);


	public ApplicationListenerDetector(AbstractApplicationContext applicationContext) {
		this.applicationContext = applicationContext;
	}


	// 利用合并bean定义后置处理器，在bean的定义合并结束后，根据bean定义缓存一些信息。
	@Override
	public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
		if (ApplicationListener.class.isAssignableFrom(beanType)) {
			this.singletonNames.put(beanName, beanDefinition.isSingleton());
		}
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) {
		return bean;
	}

	// 通过bean后置处理器在bean初始化后，将应用监听器实例注册到应用上下文中
	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) {
		if (bean instanceof ApplicationListener) {
			// potentially not detected as a listener by getBeanNamesForType retrieval
			// 注：这里若是通过getBeanNamesForType获取beanName对应的类型，然后再判断是否为ApplicationListener可能存在漏检测的风险。
			// bean在实例化后可能会被bean后置处理器代理包装，因此通过bean定义后置处理器来根据bean的实际类型判断后缓存在singletonNames中。
			Boolean flag = this.singletonNames.get(beanName);
			if (Boolean.TRUE.equals(flag)) {
				// singleton bean (top-level or inner): register on the fly
				// 注：将单例bean注册到应用上下文中
				this.applicationContext.addApplicationListener((ApplicationListener<?>) bean);
			}
			else if (Boolean.FALSE.equals(flag)) {
				if (logger.isWarnEnabled() && !this.applicationContext.containsBean(beanName)) {
					// inner bean with other scope - can't reliably process events
					// 注：内置Bean实例如果设计为时间监听器，必须是单例bean，否则无法可靠的处理事件。
					logger.warn("Inner bean '" + beanName + "' implements ApplicationListener interface " +
							"but is not reachable for event multicasting by its containing ApplicationContext " +
							"because it does not have singleton scope. Only top-level listener beans are allowed " +
							"to be of non-singleton scope.");
				}
				// 注：移除非单例的事件监听器bean
				// 疑问：非内置事件监听器bean也在此处移除了吧，为什么只有内置bean才打印日志信息呢？
				// 【应该是因为不是一个人写的，看git之前缓存map只存储value未true的bean，这里本身就过滤了非内置非单例bean...】
				this.singletonNames.remove(beanName);
			}
		}
		return bean;
	}

	// 注：在应用上下文中去除指定的时间监听器
	@Override
	public void postProcessBeforeDestruction(Object bean, String beanName) {
		if (bean instanceof ApplicationListener) {
			try {
				ApplicationEventMulticaster multicaster = this.applicationContext.getApplicationEventMulticaster();
				multicaster.removeApplicationListener((ApplicationListener<?>) bean);
				multicaster.removeApplicationListenerBean(beanName);
			}
			catch (IllegalStateException ex) {
				// ApplicationEventMulticaster not initialized yet - no need to remove a listener
			}
		}
	}

	@Override
	public boolean requiresDestruction(Object bean) {
		return (bean instanceof ApplicationListener);
	}


	/**
	 * 应用监听器的侦测器在后置处理器缓存中仅保存一个实例。主要用于该实例需处于应用后置处理器最后一个位置。
	 * 为什么要保证监听器的后置处理器必须在最后一个呢？因为该后置处理器执行的是注册操作。--也即，
	 * 如果提前处理，可能bean还需要代理包装后才是用户预期的事件监听器，这就导致了问题。
	 */
	@Override
	public boolean equals(@Nullable Object other) {
		return (this == other || (other instanceof ApplicationListenerDetector &&
				this.applicationContext == ((ApplicationListenerDetector) other).applicationContext));
	}

	@Override
	public int hashCode() {
		return ObjectUtils.nullSafeHashCode(this.applicationContext);
	}

}
