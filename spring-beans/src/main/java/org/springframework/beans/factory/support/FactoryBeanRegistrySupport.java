/*
 * Copyright 2002-2020 the original author or authors.
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

package org.springframework.beans.factory.support;

import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.FactoryBeanNotInitializedException;
import org.springframework.lang.Nullable;

/**
 * Support base class for singleton registries which need to handle
 * {@link org.springframework.beans.factory.FactoryBean} instances,
 * integrated with {@link DefaultSingletonBeanRegistry}'s singleton management.
 *
 * <p>Serves as base class for {@link AbstractBeanFactory}.
 *
 * @author Juergen Hoeller
 * @since 2.5.1
 */
public abstract class FactoryBeanRegistrySupport extends DefaultSingletonBeanRegistry {

	/** Cache of singleton objects created by FactoryBeans: FactoryBean name to object.
	 * 注：用于缓存【单例】工厂Bean名称到【单例】工厂bean实例的映射。
	 * - 对于单例工厂对象是缓存在单例bean缓存对象(singletonObjects)中
	 * - 对于单例工厂对象产生的目标单例bean对象缓存在工厂bean对象缓存中-即factoryBeanObjectCache
	 * */
	private final Map<String, Object> factoryBeanObjectCache = new ConcurrentHashMap<>(16);


	/**
	 * Determine the type for the given FactoryBean.
	 * @param factoryBean the FactoryBean instance to check
	 * @return the FactoryBean's object type,
	 * or {@code null} if the type cannot be determined yet
	 */
	@Nullable
	protected Class<?> getTypeForFactoryBean(FactoryBean<?> factoryBean) {
		try {
			if (System.getSecurityManager() != null) {
				return AccessController.doPrivileged(
						(PrivilegedAction<Class<?>>) factoryBean::getObjectType, getAccessControlContext());
			}
			else {
				return factoryBean.getObjectType();
			}
		}
		catch (Throwable ex) {
			// Thrown from the FactoryBean's getObjectType implementation.
			logger.info("FactoryBean threw exception from getObjectType, despite the contract saying " +
					"that it should return null if the type of its object cannot be determined yet", ex);
			return null;
		}
	}

	/**
	 * Obtain an object to expose from the given FactoryBean, if available
	 * in cached form. Quick check for minimal synchronization.
	 * 注：根据给定的FactoryBean的名称来获取器生产的bean实例。
	 * @param beanName the name of the bean
	 * @return the object obtained from the FactoryBean,
	 * or {@code null} if not available
	 */
	@Nullable
	protected Object getCachedObjectForFactoryBean(String beanName) {
		return this.factoryBeanObjectCache.get(beanName);
	}

	/**
	 * Obtain an object to expose from the given FactoryBean.
	 * 注：根据指定的工厂对象来获取目标工厂bean实例。创建工厂bean实例后，会调用所有的bean后置处理器
	 * @param factory the FactoryBean instance // 注：工厂bean实例
	 * @param beanName the name of the bean	// 注：目标bean的名称
	 * // 注：是否当前bean是否需要后置处理器处理；动态生成工厂bean不需要
	 * @param shouldPostProcess whether the bean is subject to post-processing
	 * @return the object obtained from the FactoryBean
	 * @throws BeanCreationException if FactoryBean object creation failed
	 * @see org.springframework.beans.factory.FactoryBean#getObject()
	 */
	protected Object getObjectFromFactoryBean(FactoryBean<?> factory, String beanName, boolean shouldPostProcess) {
		if (factory.isSingleton() && containsSingleton(beanName)) {
			/**
			 * 注：如果当前工厂本身是个单例Bean【默认情况下为true】，并且该工厂bean已经作为单例缓存在工厂中
			 * - 关于containsSingleton(beanName)说明：如果工厂bean实例为单例，其在实例化的时候就会被添加到单例缓存中。
			 * - DefaultSingletonBeanRegistry#getSingleton
			 */
			synchronized (getSingletonMutex()) {
				/**
				 * 注：针对singletonObjects缓存添加同步锁
				 * - 这里说明下为什么不是对factoryBeanObjectCache加锁？spring要求在创建任何单例bean时都是通过getSingletonMutex返回的对象加锁。
				 * - 如果在factoryBeanObjectCache缓存中已经存在工厂bean映射的生成bean实例，就直接返回了
				 */
				Object object = this.factoryBeanObjectCache.get(beanName);
				if (object == null) {
					// 注：内部就是调用工厂bean的getObject方法获取目标对象
					object = doGetObjectFromFactoryBean(factory, beanName);
					// Only post-process and store if not put there already during getObject() call above
					// (e.g. because of circular reference processing triggered by custom getBean calls)
					/**
					 * 注：同步区是对singletonObjects上的锁，有可能在上述getObject期间目标bean已经通过循环引用的处理初始化了。
					 * 这里再次检查缓存中是否存在目标bean，存在则直接返回即可；
					 * 不存在则说明本次是第一次获取，需要应用bean初始化后的后置处理器后在添加到工厂bean对象缓存中。
					 */
					Object alreadyThere = this.factoryBeanObjectCache.get(beanName);
					if (alreadyThere != null) {
						object = alreadyThere;	// 注：已经存在就直接返回之
					}
					else {
						/**
						 * 第一次创建工厂bean对象，根据其是否为动态生成类来判断是否需要应用bean后置处理器方法。
						 * 应用后置处理器初始化方法后的对象才可以当做最终对象缓存起来。
						 * - 但是需要注意一点，bean后置处理器也有可能获取该工厂bean对象，因此需要解决类似循环引用的问题。
						 * 如何解决呢？在执行后置处理器之前，将该工厂bean名称设置为正在创建中(实际上已经创建完成)，
						 * 如果在处理的过程中又再次来执行后置处理器之前，会判断这个标识。是则直接提前返回不完全对象。
						 * 【个人觉得这里使用singletonsCurrentlyInCreation缓存不好，因为确实已经创建完成了。
						 *   而且还有一个问题，破坏了单例工厂bean。直接返回的对象和正在创建中的对象不是一个引用。
						 * 】
						 */
						if (shouldPostProcess) { 	// 注：是否需要后置处理器处理【非动态生成类这里为true】
							if (isSingletonCurrentlyInCreation(beanName)) {
								// Temporarily return non-post-processed object, not storing it yet..
								// 注：在调用后置处理器过程中，如果需要获取正在创建的工厂bean实例，直接返回即可(更不需要缓存)。
								// 问题：这里返回的对象和实际创建的对象不是一个对象引用，破坏了单例
								return object;
							}
							// 早期疑问：这不是在创建之前回调吗？用在这里不符合方法含义，有待商榷。
							beforeSingletonCreation(beanName);
							try {
								// 注：这里应用所有的Bean后置处理器的postProcessAfterInitialization方法。
								object = postProcessObjectFromFactoryBean(object, beanName);
							}
							catch (Throwable ex) {
								throw new BeanCreationException(beanName,
										"Post-processing of FactoryBean's singleton object failed", ex);
							}
							finally {
								// 早期疑问：这不是在创建之后回调吗？同上
								afterSingletonCreation(beanName);
							}
						}
						/**
						 * 这里再次做containsSingleton防御性判断是否多余？在线程同步区之前就已经防御性判断了，这里判断让人无措。
						 * 难道还有可能工厂对象在运行时从单例缓存中注销了，销毁了？
						 * （根据git记录，这个防御性判断和上述正在创建bean机制属于同一个人）
						 */
						if (containsSingleton(beanName)) {
							// 注：将产生单例工厂bean对象缓存在工厂bean对象缓存映射中；下次再通过单例工厂对象获取bean对象时，直接检查缓存返回即可。
							this.factoryBeanObjectCache.put(beanName, object);
						}
					}
				}
				return object;
			}
		}
		else {
			/**
			 * 注：如果当前工厂是个非单例的，那么直接调用工厂对象的getObject获取工厂bean对象即可，不需要设计缓存
			 */
			Object object = doGetObjectFromFactoryBean(factory, beanName);
			if (shouldPostProcess) {		// 注：是否需要后置处理器处理
				try {
					// 注：这里应用所有的Bean后置处理器的postProcessAfterInitialization方法。
					// 早期疑问：这前后为什么不需要回调了？这里会不会有可能死循环？bean后置处理器中获取工厂bean对象。
					object = postProcessObjectFromFactoryBean(object, beanName);
				}
				catch (Throwable ex) {
					throw new BeanCreationException(beanName, "Post-processing of FactoryBean's object failed", ex);
				}
			}
			return object;
		}
	}

	/**
	 * Obtain an object to expose from the given FactoryBean.
	 * 注：根据指定的工厂Bean生产目标bean实例【do方法】
	 * @param factory the FactoryBean instance // 注：工厂bean实例
	 * @param beanName the name of the bean // 注：目标bean的名称
	 * @return the object obtained from the FactoryBean
	 * @throws BeanCreationException if FactoryBean object creation failed
	 * @see org.springframework.beans.factory.FactoryBean#getObject()
	 */
	private Object doGetObjectFromFactoryBean(FactoryBean<?> factory, String beanName) throws BeanCreationException {
		Object object;
		try {
			// 注：下面就是调用工厂类的getObject方法来获取目标bean实例
			if (System.getSecurityManager() != null) {
				AccessControlContext acc = getAccessControlContext();
				try {
					object = AccessController.doPrivileged((PrivilegedExceptionAction<Object>) factory::getObject, acc);
				}
				catch (PrivilegedActionException pae) {
					throw pae.getException();
				}
			}
			else {
				object = factory.getObject();
			}
		}
		catch (FactoryBeanNotInitializedException ex) {
			throw new BeanCurrentlyInCreationException(beanName, ex.toString());
		}
		catch (Throwable ex) {
			throw new BeanCreationException(beanName, "FactoryBean threw exception on object creation", ex);
		}

		// Do not accept a null value for a FactoryBean that's not fully
		// initialized yet: Many FactoryBeans just return null then.
		// 注：许多工厂bean可能会返回null，这里将Null转换为内部的特殊表示类NullBean
		if (object == null) {
			if (isSingletonCurrentlyInCreation(beanName)) {
				throw new BeanCurrentlyInCreationException(
						beanName, "FactoryBean which is currently in creation returned null from getObject");
			}
			object = new NullBean();
		}
		return object;
	}

	/**
	 * Post-process the given object that has been obtained from the FactoryBean.
	 * The resulting object will get exposed for bean references.
	 * <p>The default implementation simply returns the given object as-is.
	 * Subclasses may override this, for example, to apply post-processors.
	 * @param object the object obtained from the FactoryBean.
	 * @param beanName the name of the bean
	 * @return the object to expose
	 * @throws org.springframework.beans.BeansException if any post-processing failed
	 */
	protected Object postProcessObjectFromFactoryBean(Object object, String beanName) throws BeansException {
		return object;
	}

	/**
	 * Get a FactoryBean for the given bean if possible.
	 * @param beanName the name of the bean
	 * @param beanInstance the corresponding bean instance
	 * @return the bean instance as FactoryBean
	 * @throws BeansException if the given bean cannot be exposed as a FactoryBean
	 */
	protected FactoryBean<?> getFactoryBean(String beanName, Object beanInstance) throws BeansException {
		if (!(beanInstance instanceof FactoryBean)) {
			throw new BeanCreationException(beanName,
					"Bean instance of type [" + beanInstance.getClass() + "] is not a FactoryBean");
		}
		return (FactoryBean<?>) beanInstance;
	}

	/**
	 * Overridden to clear the FactoryBean object cache as well.
	 */
	@Override
	protected void removeSingleton(String beanName) {
		synchronized (getSingletonMutex()) {
			super.removeSingleton(beanName);
			this.factoryBeanObjectCache.remove(beanName);
		}
	}

	/**
	 * Overridden to clear the FactoryBean object cache as well.
	 */
	@Override
	protected void clearSingletonCache() {
		synchronized (getSingletonMutex()) {
			super.clearSingletonCache();
			this.factoryBeanObjectCache.clear();
		}
	}

	/**
	 * Return the security context for this bean factory. If a security manager
	 * is set, interaction with the user code will be executed using the privileged
	 * of the security context returned by this method.
	 * @see AccessController#getContext()
	 */
	protected AccessControlContext getAccessControlContext() {
		return AccessController.getContext();
	}

}
