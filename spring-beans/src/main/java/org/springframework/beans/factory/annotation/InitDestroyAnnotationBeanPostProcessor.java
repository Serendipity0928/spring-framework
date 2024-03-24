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

package org.springframework.beans.factory.annotation;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * {@link org.springframework.beans.factory.config.BeanPostProcessor} implementation
 * that invokes annotated init and destroy methods. Allows for an annotation
 * alternative to Spring's {@link org.springframework.beans.factory.InitializingBean}
 * and {@link org.springframework.beans.factory.DisposableBean} callback interfaces.
 *
 * <p>The actual annotation types that this post-processor checks for can be
 * configured through the {@link #setInitAnnotationType "initAnnotationType"}
 * and {@link #setDestroyAnnotationType "destroyAnnotationType"} properties.
 * Any custom annotation can be used, since there are no required annotation
 * attributes.
 *
 * <p>Init and destroy annotations may be applied to methods of any visibility:
 * public, package-protected, protected, or private. Multiple such methods
 * may be annotated, but it is recommended to only annotate one single
 * init method and destroy method, respectively.
 *
 * <p>Spring's {@link org.springframework.context.annotation.CommonAnnotationBeanPostProcessor}
 * supports the JSR-250 {@link javax.annotation.PostConstruct} and {@link javax.annotation.PreDestroy}
 * annotations out of the box, as init annotation and destroy annotation, respectively.
 * Furthermore, it also supports the {@link javax.annotation.Resource} annotation
 * for annotation-driven injection of named beans.
 *
 * @author Juergen Hoeller
 * @since 2.5
 * @see #setInitAnnotationType
 * @see #setDestroyAnnotationType
 */
@SuppressWarnings("serial")
public class InitDestroyAnnotationBeanPostProcessor
		implements DestructionAwareBeanPostProcessor, MergedBeanDefinitionPostProcessor, PriorityOrdered, Serializable {

	private final transient LifecycleMetadata emptyLifecycleMetadata =
			new LifecycleMetadata(Object.class, Collections.emptyList(), Collections.emptyList()) {
				@Override
				public void checkConfigMembers(RootBeanDefinition beanDefinition) {
				}
				@Override
				public void invokeInitMethods(Object target, String beanName) {
				}
				@Override
				public void invokeDestroyMethods(Object target, String beanName) {
				}
				@Override
				public boolean hasDestroyMethods() {
					return false;
				}
			};


	protected transient Log logger = LogFactory.getLog(getClass());

	// 注：用于标识初始化方法的注解类型
	@Nullable
	private Class<? extends Annotation> initAnnotationType;

	// 注：用于标识销毁方法的注解类型
	@Nullable
	private Class<? extends Annotation> destroyAnnotationType;

	// 注：当前后置处理器的优先级
	private int order = Ordered.LOWEST_PRECEDENCE;

	// 缓存各个Bean类型对应的生命周期元数据(包括识别到的初始化方法和销毁方法)
	@Nullable
	private final transient Map<Class<?>, LifecycleMetadata> lifecycleMetadataCache = new ConcurrentHashMap<>(256);


	/**
	 * Specify the init annotation to check for, indicating initialization
	 * methods to call after configuration of a bean.
	 * <p>Any custom annotation can be used, since there are no required
	 * annotation attributes. There is no default, although a typical choice
	 * is the JSR-250 {@link javax.annotation.PostConstruct} annotation.
	 * 注：指定用于检查的初始化注解，该注解标识bean对象的初始化方法
	 * - 任何自定义注解都可以使用，因为这里没有指定注解属性值。
	 * - 尽管典型的使用@PostConstruct注解作为初始化注解，这里没有设置对应的默认值。
	 */
	public void setInitAnnotationType(Class<? extends Annotation> initAnnotationType) {
		this.initAnnotationType = initAnnotationType;
	}

	/**
	 * Specify the destroy annotation to check for, indicating destruction
	 * methods to call when the context is shutting down.
	 * <p>Any custom annotation can be used, since there are no required
	 * annotation attributes. There is no default, although a typical choice
	 * is the JSR-250 {@link javax.annotation.PreDestroy} annotation.
	 * 注：指定用于检查的销毁注解，该注解标识在上下文关闭时调用的销毁方法
	 * - 任何自定义注解都可以使用，因为这里没有指定注解属性值。
	 * - 尽管典型的使用@PreDestroy注解作为销毁注解，这里没有设置对应的默认值。
	 */
	public void setDestroyAnnotationType(Class<? extends Annotation> destroyAnnotationType) {
		this.destroyAnnotationType = destroyAnnotationType;
	}

	// 注：设置当前注解的优先级
	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	@Override
	public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
		// 注：根据bean类型来获取其对应生命周期元数据(实际就是初始化、销毁方法)
		LifecycleMetadata metadata = findLifecycleMetadata(beanType);
		// 注：检查生命周期数据&处理Bean定义
		metadata.checkConfigMembers(beanDefinition);
	}

	/**
	 * 注：@PostConstruct就在这里被调用的，缓存在了this.lifecycleMetadataCache里面
	 */
	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		// 注：根据bean类型来获取其对应生命周期元数据(实际就是初始化、销毁方法)
		LifecycleMetadata metadata = findLifecycleMetadata(bean.getClass());
		try {
			// 注：通过生命周期元数据对象调用初始化方法
			metadata.invokeInitMethods(bean, beanName);
		}
		catch (InvocationTargetException ex) {
			throw new BeanCreationException(beanName, "Invocation of init method failed", ex.getTargetException());
		}
		catch (Throwable ex) {
			throw new BeanCreationException(beanName, "Failed to invoke init method", ex);
		}
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}

	@Override
	public void postProcessBeforeDestruction(Object bean, String beanName) throws BeansException {
		LifecycleMetadata metadata = findLifecycleMetadata(bean.getClass());
		try {
			metadata.invokeDestroyMethods(bean, beanName);
		}
		catch (InvocationTargetException ex) {
			String msg = "Destroy method on bean with name '" + beanName + "' threw an exception";
			if (logger.isDebugEnabled()) {
				logger.warn(msg, ex.getTargetException());
			}
			else {
				logger.warn(msg + ": " + ex.getTargetException());
			}
		}
		catch (Throwable ex) {
			logger.warn("Failed to invoke destroy method on bean with name '" + beanName + "'", ex);
		}
	}

	@Override
	public boolean requiresDestruction(Object bean) {
		return findLifecycleMetadata(bean.getClass()).hasDestroyMethods();
	}


	// 注：获取指定类型的生命周期元数据(缓存自定义注解指定的初始化、销毁方法)
	private LifecycleMetadata findLifecycleMetadata(Class<?> clazz) {
		if (this.lifecycleMetadataCache == null) {
			// Happens after deserialization, during destruction...
			/**
			 * 注：非spring运行的正常情况下，不通过缓存，根据类信息获取生命周期元数据并返回。
			 * 1. 当前对象反序列化后，lifecycleMetadataCache缓存使用transient修饰，所以会为null
			 * 2. 当前对象正在被销毁
			 */
			return buildLifecycleMetadata(clazz);
		}
		// Quick check on the concurrent map first, with minimal locking.
		// 注：由于构建生命周期元数据涉及反射操作，因此使用并发map来进行缓存类型的生命周期数据。
		// - 下面这一段代码就是典型的线程安全缓存写法-synchronized+Double Check。
		LifecycleMetadata metadata = this.lifecycleMetadataCache.get(clazz);
		if (metadata == null) {
			synchronized (this.lifecycleMetadataCache) {
				metadata = this.lifecycleMetadataCache.get(clazz);
				if (metadata == null) {
					metadata = buildLifecycleMetadata(clazz);		// 注：根据类型反射获取生命周期元数据
					this.lifecycleMetadataCache.put(clazz, metadata);
				}
				return metadata;
			}
		}
		return metadata;
	}

	// 注：根据类型通过反射手段获取生命周期元数据(自定义注解指定的初始化、销毁方法)
	private LifecycleMetadata buildLifecycleMetadata(final Class<?> clazz) {
		if (!AnnotationUtils.isCandidateClass(clazz, Arrays.asList(this.initAnnotationType, this.destroyAnnotationType))) {
			// 注：快速判断initAnnotationType或destroyAnnotationType注解类型是否可能用于指定类型clazz
			// 注：emptyLifecycleMetadata是一个空元数据(既没有初始化方法也没有销毁方法)，也会被用于缓存起来。
			return this.emptyLifecycleMetadata;
		}

		List<LifecycleElement> initMethods = new ArrayList<>();
		List<LifecycleElement> destroyMethods = new ArrayList<>();
		Class<?> targetClass = clazz;

		// 注：下面是从当前类开始逐步向父级遍历，反射获取方法对象，
		// - 判断是否有initAnnotationType或destroyAnnotationType注解修饰
		do {
			final List<LifecycleElement> currInitMethods = new ArrayList<>();
			final List<LifecycleElement> currDestroyMethods = new ArrayList<>();

			// 注：获取当前类型生命的所有方法，遍历这些方法，回调判断方法是否有注解修饰并缓存起来
			ReflectionUtils.doWithLocalMethods(targetClass, method -> {
				if (this.initAnnotationType != null && method.isAnnotationPresent(this.initAnnotationType)) {
					// 注：判断当前方法是否存在initAnnotationType注释，是则为初始化方法
					LifecycleElement element = new LifecycleElement(method);
					currInitMethods.add(element);
					if (logger.isTraceEnabled()) {
						logger.trace("Found init method on class [" + clazz.getName() + "]: " + method);
					}
				}
				if (this.destroyAnnotationType != null && method.isAnnotationPresent(this.destroyAnnotationType)) {
					// 注：判断当前方法是否存在destroyAnnotationType注释，是则为销毁方法
					currDestroyMethods.add(new LifecycleElement(method));
					if (logger.isTraceEnabled()) {
						logger.trace("Found destroy method on class [" + clazz.getName() + "]: " + method);
					}
				}
			});

			initMethods.addAll(0, currInitMethods);
			destroyMethods.addAll(currDestroyMethods);
			targetClass = targetClass.getSuperclass();		// 注：获取父类，继续循环遍历
		}
		while (targetClass != null && targetClass != Object.class);

		// 注：将获取到的当前类型的生命周期元数据返回
		return (initMethods.isEmpty() && destroyMethods.isEmpty() ? this.emptyLifecycleMetadata :
				new LifecycleMetadata(clazz, initMethods, destroyMethods));
	}


	//---------------------------------------------------------------------
	// Serialization support
	//---------------------------------------------------------------------

	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		// Rely on default serialization; just initialize state after deserialization.
		ois.defaultReadObject();

		// Initialize transient fields.
		this.logger = LogFactory.getLog(getClass());
	}


	/**
	 * Class representing information about annotated init and destroy methods.
	 * 注：LifecycleMetadata类表示注解和销毁方法的元数据信息。
	 * - 实际上就是扫描后缓存的对象
	 */
	private class LifecycleMetadata {

		private final Class<?> targetClass;

		private final Collection<LifecycleElement> initMethods;

		private final Collection<LifecycleElement> destroyMethods;

		@Nullable
		private volatile Set<LifecycleElement> checkedInitMethods;

		@Nullable
		private volatile Set<LifecycleElement> checkedDestroyMethods;

		public LifecycleMetadata(Class<?> targetClass, Collection<LifecycleElement> initMethods,
				Collection<LifecycleElement> destroyMethods) {

			this.targetClass = targetClass;
			this.initMethods = initMethods;
			this.destroyMethods = destroyMethods;
		}

		// 校验生命周期元数据 & 处理Bean定义
		public void checkConfigMembers(RootBeanDefinition beanDefinition) {
			Set<LifecycleElement> checkedInitMethods = new LinkedHashSet<>(this.initMethods.size());
			for (LifecycleElement element : this.initMethods) {		// 注：逐个遍历初始化方法
				// 注：获取方法标识: 私有方法-类路径.方法名；其他-方法名
				String methodIdentifier = element.getIdentifier();
				if (!beanDefinition.isExternallyManagedInitMethod(methodIdentifier)) {
					// 注：判断当前方法是否已注册到bean定义中
					// 注：将当前方法注册到bean定义的externallyManagedInitMethods缓存起来，后续使用...
					beanDefinition.registerExternallyManagedInitMethod(methodIdentifier);
					// 注：将当前生命周期方法信息存储在有序集合checkedInitMethods中
					checkedInitMethods.add(element);
					if (logger.isTraceEnabled()) {
						logger.trace("Registered init method on class [" + this.targetClass.getName() + "]: " + element);
					}
				}
			}
			// 注：后续处理销毁方法，逻辑与上类似
			Set<LifecycleElement> checkedDestroyMethods = new LinkedHashSet<>(this.destroyMethods.size());
			for (LifecycleElement element : this.destroyMethods) {
				String methodIdentifier = element.getIdentifier();
				if (!beanDefinition.isExternallyManagedDestroyMethod(methodIdentifier)) {
					beanDefinition.registerExternallyManagedDestroyMethod(methodIdentifier);
					checkedDestroyMethods.add(element);
					if (logger.isTraceEnabled()) {
						logger.trace("Registered destroy method on class [" + this.targetClass.getName() + "]: " + element);
					}
				}
			}
			this.checkedInitMethods = checkedInitMethods;
			this.checkedDestroyMethods = checkedDestroyMethods;
		}

		// 注：调用当前bean类型的自定义注解初始化方法
		public void invokeInitMethods(Object target, String beanName) throws Throwable {
			// 注：bean定义合并后回调检查自定义初始化方法，会存储在checkedInitMethods中，此种的初始化方法优先级高
			Collection<LifecycleElement> checkedInitMethods = this.checkedInitMethods;
			Collection<LifecycleElement> initMethodsToIterate =
					(checkedInitMethods != null ? checkedInitMethods : this.initMethods);
			if (!initMethodsToIterate.isEmpty()) {
				for (LifecycleElement element : initMethodsToIterate) {
					if (logger.isTraceEnabled()) {
						logger.trace("Invoking init method on bean '" + beanName + "': " + element.getMethod());
					}
					// 注：触发自定义注解初始化方法
					element.invoke(target);
				}
			}
		}

		public void invokeDestroyMethods(Object target, String beanName) throws Throwable {
			Collection<LifecycleElement> checkedDestroyMethods = this.checkedDestroyMethods;
			Collection<LifecycleElement> destroyMethodsToUse =
					(checkedDestroyMethods != null ? checkedDestroyMethods : this.destroyMethods);
			if (!destroyMethodsToUse.isEmpty()) {
				for (LifecycleElement element : destroyMethodsToUse) {
					if (logger.isTraceEnabled()) {
						logger.trace("Invoking destroy method on bean '" + beanName + "': " + element.getMethod());
					}
					element.invoke(target);
				}
			}
		}

		public boolean hasDestroyMethods() {
			Collection<LifecycleElement> checkedDestroyMethods = this.checkedDestroyMethods;
			Collection<LifecycleElement> destroyMethodsToUse =
					(checkedDestroyMethods != null ? checkedDestroyMethods : this.destroyMethods);
			return !destroyMethodsToUse.isEmpty();
		}
	}


	/**
	 * Class representing injection information about an annotated method.
	 */
	private static class LifecycleElement {

		private final Method method;

		private final String identifier;

		public LifecycleElement(Method method) {
			if (method.getParameterCount() != 0) {
				throw new IllegalStateException("Lifecycle method annotation requires a no-arg method: " + method);
			}
			this.method = method;
			this.identifier = (Modifier.isPrivate(method.getModifiers()) ?
					ClassUtils.getQualifiedMethodName(method) : method.getName());
		}

		public Method getMethod() {
			return this.method;
		}

		public String getIdentifier() {
			return this.identifier;
		}

		public void invoke(Object target) throws Throwable {
			ReflectionUtils.makeAccessible(this.method);
			this.method.invoke(target, (Object[]) null);
		}

		@Override
		public boolean equals(@Nullable Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof LifecycleElement)) {
				return false;
			}
			LifecycleElement otherElement = (LifecycleElement) other;
			return (this.identifier.equals(otherElement.identifier));
		}

		@Override
		public int hashCode() {
			return this.identifier.hashCode();
		}
	}

}
