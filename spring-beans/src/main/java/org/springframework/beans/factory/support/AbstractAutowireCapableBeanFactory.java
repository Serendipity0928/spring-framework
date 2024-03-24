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

import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import org.apache.commons.logging.Log;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyAccessorUtils;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.Aware;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.InjectionPoint;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.AutowiredPropertyMarker;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodParameter;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.MethodCallback;
import org.springframework.util.StringUtils;

/**
 * Abstract bean factory superclass that implements default bean creation,
 * with the full capabilities specified by the {@link RootBeanDefinition} class.
 * Implements the {@link org.springframework.beans.factory.config.AutowireCapableBeanFactory}
 * interface in addition to AbstractBeanFactory's {@link #createBean} method.
 *
 * <p>Provides bean creation (with constructor resolution), property population,
 * wiring (including autowiring), and initialization. Handles runtime bean
 * references, resolves managed collections, calls initialization methods, etc.
 * Supports autowiring constructors, properties by name, and properties by type.
 *
 * <p>The main template method to be implemented by subclasses is
 * {@link #resolveDependency(DependencyDescriptor, String, Set, TypeConverter)},
 * used for autowiring by type. In case of a factory which is capable of searching
 * its bean definitions, matching beans will typically be implemented through such
 * a search. For other factory styles, simplified matching algorithms can be implemented.
 *
 * <p>Note that this class does <i>not</i> assume or implement bean definition
 * registry capabilities. See {@link DefaultListableBeanFactory} for an implementation
 * of the {@link org.springframework.beans.factory.ListableBeanFactory} and
 * {@link BeanDefinitionRegistry} interfaces, which represent the API and SPI
 * view of such a factory, respectively.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Mark Fisher
 * @author Costin Leau
 * @author Chris Beams
 * @author Sam Brannen
 * @author Phillip Webb
 * @since 13.02.2004
 * @see RootBeanDefinition
 * @see DefaultListableBeanFactory
 * @see BeanDefinitionRegistry
 */
public abstract class AbstractAutowireCapableBeanFactory extends AbstractBeanFactory
		implements AutowireCapableBeanFactory {

	/**
	 * Whether this environment lives within a native image.
	 * Exposed as a private static field rather than in a {@code NativeImageDetector.inNativeImage()} static method due to https://github.com/oracle/graal/issues/2594.
	 * @see <a href="https://github.com/oracle/graal/blob/master/sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/ImageInfo.java">ImageInfo.java</a>
	 */
	private static final boolean IN_NATIVE_IMAGE = (System.getProperty("org.graalvm.nativeimage.imagecode") != null);


	/** Strategy for creating bean instances.
	 * 注：创建bean实例的实例化策略
	 * */
	private InstantiationStrategy instantiationStrategy;

	/** Resolver strategy for method parameter names.
	 * 注：方法参数名的解析策略
	 * */
	@Nullable
	private ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

	/** Whether to automatically try to resolve circular references between beans.
	 * 注：是否自动尝试解决bean实例之间的循环引用
	 * */
	private boolean allowCircularReferences = true;

	/**
	 * Whether to resort to injecting a raw bean instance in case of circular reference,
	 * even if the injected bean eventually got wrapped.
	 * 表示是否允许在循环引用的情况下注入原始 bean 实例，即使注入的 bean 最终被增强器增强包装过，默认是false
	 */
	private boolean allowRawInjectionDespiteWrapping = false;

	/**
	 * Dependency types to ignore on dependency check and autowire, as Set of
	 * Class objects: for example, String. Default is none.
	 * 注：用于依赖检查以及自动装配中需要忽略的依赖类型。
	 * 这是一个类型集合，如String.Class。默认集合为空。
	 */
	private final Set<Class<?>> ignoredDependencyTypes = new HashSet<>();

	/**
	 * Dependency interfaces to ignore on dependency check and autowire, as Set of
	 * Class objects. By default, only the BeanFactory interface is ignored.
	 * 注：用于依赖检查以及自动装配中需要忽略的依赖接口(接口的方法表明所依赖的类型)。
	 * 这是一个类型集合。默认情况下仅有BeanFactory接口是需要忽略的【不止，可见当前类无参构造】。
	 * 参考--> https://www.jianshu.com/p/3c7e0608ff1f?from=singlemessage、https://blog.csdn.net/vistaed/article/details/107315265
	 */
	private final Set<Class<?>> ignoredDependencyInterfaces = new HashSet<>();

	/**
	 * The name of the currently created bean, for implicit dependency registration
	 * on getBean etc invocations triggered from a user-specified Supplier callback.
	 * 注：设置当前线程正在创建bean的名称。
	 * 用于在调用用户指定创建bean实例的回调方法时触发的getBean等方法内的隐式依赖注册。
	 * obtainFromSupplier、getObjectForBeanInstance
	 */
	private final NamedThreadLocal<String> currentlyCreatedBean = new NamedThreadLocal<>("Currently created bean");

	/** Cache of unfinished FactoryBean instances: FactoryBean name to BeanWrapper.
	 * 注：用于缓存已完成实例化的FactoryBean实例。缓存工厂bean名称到BeanWrapper(bean的包装类)实例的映射。
	 * */
	private final ConcurrentMap<String, BeanWrapper> factoryBeanInstanceCache = new ConcurrentHashMap<>();

	/** Cache of candidate factory methods per factory class.
	 * 注：缓存每个工厂类的候选工厂方法
	 * */
	private final ConcurrentMap<Class<?>, Method[]> factoryMethodCandidateCache = new ConcurrentHashMap<>();

	/** Cache of filtered PropertyDescriptors: bean Class to PropertyDescriptor array. */
	private final ConcurrentMap<Class<?>, PropertyDescriptor[]> filteredPropertyDescriptorsCache =
			new ConcurrentHashMap<>();


	/**
	 * Create a new AbstractAutowireCapableBeanFactory.
	 * 注：创建bean工厂实例：初始化AbstractAutowireCapableBeanFactory数据
	 */
	public AbstractAutowireCapableBeanFactory() {
		super();		// 注：其父类AbstractBeanFactory无参构造没有任何逻辑
		/**
		 * 注：将bean工厂相关的aware接口方法在自动装配时忽略。
		 * 1. 为什么要忽略？
		 * 在bean属性初始化之前，Aware相关要么会显示调用(比如BeanNameAware)，要么通过后置处理器调用。因此就不需要自动装配、
		 * 2. ignoredDependencyInterfaces和ignoredDependencyTypes有什么区别？
		 * 参考：https://www.jianshu.com/p/3c7e0608ff1f?from=singlemessage、https://blog.csdn.net/vistaed/article/details/107315265
		 */
		ignoreDependencyInterface(BeanNameAware.class);
		ignoreDependencyInterface(BeanFactoryAware.class);
		ignoreDependencyInterface(BeanClassLoaderAware.class);
		/**
		 * 注：初始化bean实例化策略
		 * 默认的实例化策略是CglibSubclassingInstantiationStrategy类型对象，其支持CGLIB动态生成子类实例。
		 * 然而，如果是在本地镜像中，实例化策略为SimpleInstantiationStrategy类型对象，不支持CGLIB动态生成子类实例，会排除异常。
		 */
		if (IN_NATIVE_IMAGE) {
			this.instantiationStrategy = new SimpleInstantiationStrategy();
		}
		else {
			this.instantiationStrategy = new CglibSubclassingInstantiationStrategy();
		}
	}

	/**
	 * Create a new AbstractAutowireCapableBeanFactory with the given parent.
	 * @param parentBeanFactory parent bean factory, or {@code null} if none
	 */
	public AbstractAutowireCapableBeanFactory(@Nullable BeanFactory parentBeanFactory) {
		this();
		setParentBeanFactory(parentBeanFactory);
	}


	/**
	 * Set the instantiation strategy to use for creating bean instances.
	 * Default is CglibSubclassingInstantiationStrategy.
	 * @see CglibSubclassingInstantiationStrategy
	 */
	public void setInstantiationStrategy(InstantiationStrategy instantiationStrategy) {
		this.instantiationStrategy = instantiationStrategy;
	}

	/**
	 * Return the instantiation strategy to use for creating bean instances.
	 * 注：返回创建bean实例的实例化策略，默认为CglibSubclassingInstantiationStrategy
	 */
	protected InstantiationStrategy getInstantiationStrategy() {
		return this.instantiationStrategy;
	}

	/**
	 * Set the ParameterNameDiscoverer to use for resolving method parameter
	 * names if needed (e.g. for constructor names).
	 * <p>Default is a {@link DefaultParameterNameDiscoverer}.
	 */
	public void setParameterNameDiscoverer(@Nullable ParameterNameDiscoverer parameterNameDiscoverer) {
		this.parameterNameDiscoverer = parameterNameDiscoverer;
	}

	/**
	 * Return the ParameterNameDiscoverer to use for resolving method parameter
	 * names if needed.
	 */
	@Nullable
	protected ParameterNameDiscoverer getParameterNameDiscoverer() {
		return this.parameterNameDiscoverer;
	}

	/**
	 * Set whether to allow circular references between beans - and automatically
	 * try to resolve them.
	 * <p>Note that circular reference resolution means that one of the involved beans
	 * will receive a reference to another bean that is not fully initialized yet.
	 * This can lead to subtle and not-so-subtle side effects on initialization;
	 * it does work fine for many scenarios, though.
	 * <p>Default is "true". Turn this off to throw an exception when encountering
	 * a circular reference, disallowing them completely.
	 * <p><b>NOTE:</b> It is generally recommended to not rely on circular references
	 * between your beans. Refactor your application logic to have the two beans
	 * involved delegate to a third bean that encapsulates their common logic.
	 */
	public void setAllowCircularReferences(boolean allowCircularReferences) {
		this.allowCircularReferences = allowCircularReferences;
	}

	/**
	 * Set whether to allow the raw injection of a bean instance into some other
	 * bean's property, despite the injected bean eventually getting wrapped
	 * (for example, through AOP auto-proxying).
	 * <p>This will only be used as a last resort in case of a circular reference
	 * that cannot be resolved otherwise: essentially, preferring a raw instance
	 * getting injected over a failure of the entire bean wiring process.
	 * <p>Default is "false", as of Spring 2.0. Turn this on to allow for non-wrapped
	 * raw beans injected into some of your references, which was Spring 1.2's
	 * (arguably unclean) default behavior.
	 * <p><b>NOTE:</b> It is generally recommended to not rely on circular references
	 * between your beans, in particular with auto-proxying involved.
	 * @see #setAllowCircularReferences
	 */
	public void setAllowRawInjectionDespiteWrapping(boolean allowRawInjectionDespiteWrapping) {
		this.allowRawInjectionDespiteWrapping = allowRawInjectionDespiteWrapping;
	}

	/**
	 * Ignore the given dependency type for autowiring:
	 * for example, String. Default is none.
	 */
	public void ignoreDependencyType(Class<?> type) {
		this.ignoredDependencyTypes.add(type);
	}

	/**
	 * Ignore the given dependency interface for autowiring.
	 * <p>This will typically be used by application contexts to register
	 * dependencies that are resolved in other ways, like BeanFactory through
	 * BeanFactoryAware or ApplicationContext through ApplicationContextAware.
	 * <p>By default, only the BeanFactoryAware interface is ignored.
	 * For further types to ignore, invoke this method for each type.
	 * 注：自动装配时忽略指定的接口依赖。
	 * (ps：这个接口依赖需要进一步说明，接口依赖是指通过该接口依赖的bean，比如通过BeanFactoryAware接口依赖的BeanFactory类型属性)
	 * - 这块功能通常用于应用上下文中注册一些内部依赖，这部分依赖可以通过spring提供的机制来进行解析。
	 * 比如通过BeanFactoryAware解析的BeanFactory类型属性、通过ApplicationContextAware解析的ApplicationContext类型属性。
	 * - 默认情况下，仅有BeanFactoryAware接口会被忽略【这个注释可能随着spring更新不适用，可见当前类的无参构造】。对于其他想要忽略的依赖接口，
	 * 可以调用当前方法添加到ignoredDependencyInterfaces集合中。
	 * @see org.springframework.beans.factory.BeanFactoryAware
//	 * @see org.springframework.context.ApplicationContextAware
	 * 注：参考--> https://www.jianshu.com/p/3c7e0608ff1f?from=singlemessage、https://blog.csdn.net/vistaed/article/details/107315265
	 */
	public void ignoreDependencyInterface(Class<?> ifc) {
		this.ignoredDependencyInterfaces.add(ifc);
	}

	@Override
	public void copyConfigurationFrom(ConfigurableBeanFactory otherFactory) {
		super.copyConfigurationFrom(otherFactory);
		if (otherFactory instanceof AbstractAutowireCapableBeanFactory) {
			AbstractAutowireCapableBeanFactory otherAutowireFactory =
					(AbstractAutowireCapableBeanFactory) otherFactory;
			this.instantiationStrategy = otherAutowireFactory.instantiationStrategy;
			this.allowCircularReferences = otherAutowireFactory.allowCircularReferences;
			this.ignoredDependencyTypes.addAll(otherAutowireFactory.ignoredDependencyTypes);
			this.ignoredDependencyInterfaces.addAll(otherAutowireFactory.ignoredDependencyInterfaces);
		}
	}


	//-------------------------------------------------------------------------
	// Typical methods for creating and populating external bean instances
	//-------------------------------------------------------------------------

	@Override
	@SuppressWarnings("unchecked")
	public <T> T createBean(Class<T> beanClass) throws BeansException {
		// Use prototype bean definition, to avoid registering bean as dependent bean.
		RootBeanDefinition bd = new RootBeanDefinition(beanClass);
		bd.setScope(SCOPE_PROTOTYPE);
		bd.allowCaching = ClassUtils.isCacheSafe(beanClass, getBeanClassLoader());
		return (T) createBean(beanClass.getName(), bd, null);
	}

	@Override
	public void autowireBean(Object existingBean) {
		// Use non-singleton bean definition, to avoid registering bean as dependent bean.
		RootBeanDefinition bd = new RootBeanDefinition(ClassUtils.getUserClass(existingBean));
		bd.setScope(SCOPE_PROTOTYPE);
		bd.allowCaching = ClassUtils.isCacheSafe(bd.getBeanClass(), getBeanClassLoader());
		BeanWrapper bw = new BeanWrapperImpl(existingBean);
		initBeanWrapper(bw);
		populateBean(bd.getBeanClass().getName(), bd, bw);
	}

	@Override
	public Object configureBean(Object existingBean, String beanName) throws BeansException {
		markBeanAsCreated(beanName);
		BeanDefinition mbd = getMergedBeanDefinition(beanName);
		RootBeanDefinition bd = null;
		if (mbd instanceof RootBeanDefinition) {
			RootBeanDefinition rbd = (RootBeanDefinition) mbd;
			bd = (rbd.isPrototype() ? rbd : rbd.cloneBeanDefinition());
		}
		if (bd == null) {
			bd = new RootBeanDefinition(mbd);
		}
		if (!bd.isPrototype()) {
			bd.setScope(SCOPE_PROTOTYPE);
			bd.allowCaching = ClassUtils.isCacheSafe(ClassUtils.getUserClass(existingBean), getBeanClassLoader());
		}
		BeanWrapper bw = new BeanWrapperImpl(existingBean);
		initBeanWrapper(bw);
		populateBean(beanName, bd, bw);
		return initializeBean(beanName, existingBean, bd);
	}


	//-------------------------------------------------------------------------
	// Specialized methods for fine-grained control over the bean lifecycle
	//-------------------------------------------------------------------------

	@Override
	public Object createBean(Class<?> beanClass, int autowireMode, boolean dependencyCheck) throws BeansException {
		// Use non-singleton bean definition, to avoid registering bean as dependent bean.
		RootBeanDefinition bd = new RootBeanDefinition(beanClass, autowireMode, dependencyCheck);
		bd.setScope(SCOPE_PROTOTYPE);
		return createBean(beanClass.getName(), bd, null);
	}

	@Override
	public Object autowire(Class<?> beanClass, int autowireMode, boolean dependencyCheck) throws BeansException {
		// Use non-singleton bean definition, to avoid registering bean as dependent bean.
		RootBeanDefinition bd = new RootBeanDefinition(beanClass, autowireMode, dependencyCheck);
		bd.setScope(SCOPE_PROTOTYPE);
		if (bd.getResolvedAutowireMode() == AUTOWIRE_CONSTRUCTOR) {
			return autowireConstructor(beanClass.getName(), bd, null, null).getWrappedInstance();
		}
		else {
			Object bean;
			if (System.getSecurityManager() != null) {
				bean = AccessController.doPrivileged(
						(PrivilegedAction<Object>) () -> getInstantiationStrategy().instantiate(bd, null, this),
						getAccessControlContext());
			}
			else {
				bean = getInstantiationStrategy().instantiate(bd, null, this);
			}
			populateBean(beanClass.getName(), bd, new BeanWrapperImpl(bean));
			return bean;
		}
	}

	@Override
	public void autowireBeanProperties(Object existingBean, int autowireMode, boolean dependencyCheck)
			throws BeansException {

		if (autowireMode == AUTOWIRE_CONSTRUCTOR) {
			throw new IllegalArgumentException("AUTOWIRE_CONSTRUCTOR not supported for existing bean instance");
		}
		// Use non-singleton bean definition, to avoid registering bean as dependent bean.
		RootBeanDefinition bd =
				new RootBeanDefinition(ClassUtils.getUserClass(existingBean), autowireMode, dependencyCheck);
		bd.setScope(SCOPE_PROTOTYPE);
		BeanWrapper bw = new BeanWrapperImpl(existingBean);
		initBeanWrapper(bw);
		populateBean(bd.getBeanClass().getName(), bd, bw);
	}

	@Override
	public void applyBeanPropertyValues(Object existingBean, String beanName) throws BeansException {
		markBeanAsCreated(beanName);
		BeanDefinition bd = getMergedBeanDefinition(beanName);
		BeanWrapper bw = new BeanWrapperImpl(existingBean);
		initBeanWrapper(bw);
		applyPropertyValues(beanName, bd, bw, bd.getPropertyValues());
	}

	@Override
	public Object initializeBean(Object existingBean, String beanName) {
		return initializeBean(beanName, existingBean, null);
	}

	/**
	 * 调用所有bean后置处理器的初始化前置回调方法
	 */
	@Override
	public Object applyBeanPostProcessorsBeforeInitialization(Object existingBean, String beanName)
			throws BeansException {

		Object result = existingBean;
		for (BeanPostProcessor processor : getBeanPostProcessors()) {
			Object current = processor.postProcessBeforeInitialization(result, beanName);
			if (current == null) {
				// 注：返回null就不会调用后续的后置处理器方法了
				return result;
			}
			result = current;
		}
		return result;
	}

	// 注：将所有bean后置处理器应用到已经初始化的bean实例上
	@Override
	public Object applyBeanPostProcessorsAfterInitialization(Object existingBean, String beanName)
			throws BeansException {

		Object result = existingBean;
		for (BeanPostProcessor processor : getBeanPostProcessors()) {
			Object current = processor.postProcessAfterInitialization(result, beanName);
			if (current == null) {
				// 注：返回null就不会调用后续的后置处理器方法了
				return result;
			}
			result = current;
		}
		return result;
	}

	@Override
	public void destroyBean(Object existingBean) {
		new DisposableBeanAdapter(
				existingBean, getBeanPostProcessorCache().destructionAware, getAccessControlContext()).destroy();
	}


	//-------------------------------------------------------------------------
	// Delegate methods for resolving injection points
	//-------------------------------------------------------------------------

	@Override
	public Object resolveBeanByName(String name, DependencyDescriptor descriptor) {
		InjectionPoint previousInjectionPoint = ConstructorResolver.setCurrentInjectionPoint(descriptor);
		try {
			return getBean(name, descriptor.getDependencyType());
		}
		finally {
			ConstructorResolver.setCurrentInjectionPoint(previousInjectionPoint);
		}
	}

	@Override
	@Nullable
	public Object resolveDependency(DependencyDescriptor descriptor, @Nullable String requestingBeanName) throws BeansException {
		return resolveDependency(descriptor, requestingBeanName, null, null);
	}


	//---------------------------------------------------------------------
	// Implementation of relevant AbstractBeanFactory template methods
	//---------------------------------------------------------------------

	/**
	 * Central method of this class: creates a bean instance,
	 * populates the bean instance, applies post-processors, etc.
	 * 注：当前类的核心方法：创建一个bean实例、初始化bean实例、应用后置处理器等
	 * - 所有需要从bean工厂获取bean实例的方法都会通过该方法来实际创建bean实例。
	 * @see #doCreateBean
	 */
	@Override
	protected Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
			throws BeanCreationException {

		if (logger.isTraceEnabled()) {
			logger.trace("Creating instance of bean '" + beanName + "'");
		}
		RootBeanDefinition mbdToUse = mbd;	// 注：创建当前bean实例要使用的合并bean定义

		// Make sure bean class is actually resolved at this point, and
		// clone the bean definition in case of a dynamically resolved Class
		// which cannot be stored in the shared merged bean definition.
		/**
		 * 注：在实例化之前，需要确保bean的类型已被加载。
		 * - bean类型加载过程中可能存在动态地解析bean类型的情况，即同样的beanName(className)由于spel表达式的支持，对应的实际类型存在不同。
		 * - 这种情况下是不会将bean的类型缓存在bean定义中的，但是在实际创建bean实例时，bean定义必须存在bean类型(保证已加载)，因此这里针对这种情况会复制一份Bean定义。
		 */
		Class<?> resolvedClass = resolveBeanClass(mbd, beanName);
		if (resolvedClass != null && !mbd.hasBeanClass() && mbd.getBeanClassName() != null) {
			// 注：已解析当前bean类型，但是bean定义中未缓存bean类型，这种是bean类型是动态解析的。
			// 需复制一份bean定义并设置bean类型，保证后续实例化bean实例时对应类型已加载。
			mbdToUse = new RootBeanDefinition(mbd);
			mbdToUse.setBeanClass(resolvedClass);
		}

		// Prepare method overrides.
		try {
			// 注：处理下bean定义中方法的覆盖（lookup-method、replaced-method）
			mbdToUse.prepareMethodOverrides();
		}
		catch (BeanDefinitionValidationException ex) {
			throw new BeanDefinitionStoreException(mbdToUse.getResourceDescription(),
					beanName, "Validation of method overrides failed", ex);
		}

		try {
			// Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
			/**
			 * 注：在实例化bean之前，这里将回调InstantiationAwareBeanPostProcessor后置处理器postProcessBeforeInstantiation方法。
			 * - 该方法可能会返回一个代理对象。该代理对象在这里会被直接返回，而不会返回目标bean实例。
			 */
			Object bean = resolveBeforeInstantiation(beanName, mbdToUse);
			if (bean != null) {
				// 注：若实例化后置处理器已经返回bean实例，就直接返回即可
				return bean;
			}
		}
		catch (Throwable ex) {
			throw new BeanCreationException(mbdToUse.getResourceDescription(), beanName,
					"BeanPostProcessor before instantiation of bean failed", ex);
		}

		try {
			// 注：这里执行真正执行spring本身创建并初始化bean的流程方法【重点】
			Object beanInstance = doCreateBean(beanName, mbdToUse, args);
			if (logger.isTraceEnabled()) {
				logger.trace("Finished creating instance of bean '" + beanName + "'");
			}
			return beanInstance;
		}
		catch (BeanCreationException | ImplicitlyAppearedSingletonException ex) {
			// A previously detected exception with proper bean creation context already,
			// or illegal singleton state to be communicated up to DefaultSingletonBeanRegistry.
			throw ex;
		}
		catch (Throwable ex) {
			throw new BeanCreationException(
					mbdToUse.getResourceDescription(), beanName, "Unexpected exception during bean creation", ex);
		}
	}

	/**
	 * Actually create the specified bean. Pre-creation processing has already happened
	 * at this point, e.g. checking {@code postProcessBeforeInstantiation} callbacks.
	 * <p>Differentiates between default bean instantiation, use of a
	 * factory method, and autowiring a constructor.
	 * 注：spring实际创建指定bean实例的方法。在此之前提前创建实例的流程已经执行了，比如检查实例化前后置处理器回调。
	 * - 通过工厂方法以及构造器自动装配的模式来创建实例，这与默认bean的实例化有所不同。
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @param args explicit arguments to use for constructor or factory method invocation
	 * @return a new instance of the bean
	 * @throws BeanCreationException if the bean could not be created
	 * @see #instantiateBean
	 * @see #instantiateUsingFactoryMethod
	 * @see #autowireConstructor
	 * 注：【重要方法】参考->> https://blog.csdn.net/Weixiaohuai/article/details/122093896
	 */
	protected Object doCreateBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
			throws BeanCreationException {

		// Instantiate the bean.
		// 注：实例化指定的bean实例
		BeanWrapper instanceWrapper = null;
		if (mbd.isSingleton()) {
			/**
			 * 注：如果当前bean为单例，先检查已完成实例化的工厂对象缓存；
			 * - 如果已经存在工厂对象，此处进行移除，并返回之前已实例化的工厂对象。
			 * - 为什么要移除？
			 *   在spring通过标准流程创建工厂对象之前，可能出于类型检查(getSingletonFactoryBeanForTypeCheck)的需要，之前有创建过工厂对象实例。
			 *   因此，在标准流程进行进行创建工厂对象时需要检查上一步缓存的内容，并去除，因为其可能未经过一些后置处理器进行处理。但是这个缓存的实例可以继续使用。
			 */
			instanceWrapper = this.factoryBeanInstanceCache.remove(beanName);
		}
		if (instanceWrapper == null) {
			/**
			 * 注：如果非单例或未实例化，那么调用该方法来完成bean的实例化。
			 * 会根据指定bean定义中指定的策略来创建bean的实例。比如工厂方法、构造器自动注入、默认初始化方法
			 */
			instanceWrapper = createBeanInstance(beanName, mbd, args);
		}

		/**
		 * 注：在此之前，bean已经实例化完成！
		 */

		// 注：获取bean的实例对象
		Object bean = instanceWrapper.getWrappedInstance();
		// 注：根据bean的实例获取bean的实际类型
		Class<?> beanType = instanceWrapper.getWrappedClass();
		if (beanType != NullBean.class) {
			// 注：如果bean的实例不为null（也即不是NullBean类型），那么就将bean的类型缓存在合并bean定义中。
			mbd.resolvedTargetType = beanType;
		}

		// Allow post-processors to modify the merged bean definition.
		// 注：bean实例化之后，会调用一次合并bean定义后置处理器来给出修改bean定义的回调
		synchronized (mbd.postProcessingLock) {
			if (!mbd.postProcessed) {
				// 注：一个bean定义只能被处理一次
				try {
					// 注：应用当前bean工厂内所有的合并bean定义后置处理器的postProcessMergedBeanDefinition方法。
					applyMergedBeanDefinitionPostProcessors(mbd, beanType, beanName);
				}
				catch (Throwable ex) {
					throw new BeanCreationException(mbd.getResourceDescription(), beanName,
							"Post-processing of merged bean definition failed", ex);
				}
				// 注：处理完之后，将处理后的标记缓存在bean定义中
				mbd.postProcessed = true;
			}
		}

		// Eagerly cache singletons to be able to resolve circular references
		// even when triggered by lifecycle interfaces like BeanFactoryAware.
		/**
		 * 早期单例缓存可能用于解决循环引用的问题，即使是由生命周期接口(如BeanFactoryAware)引入的循环引用。
		 * 是否允许暴漏早期单例(即，半生品对象，仅实例化对象)由三个条件：
		 * 1. 当前bean为单例bean-仅单例bean之间的循环依赖有可能解决
		 * 2. bean工厂是否允许解决循环引用-allowCircularReferences
		 * 3. 当前bean名称是否正在单例创建流程中-属于spring的标准流程
		 */
		boolean earlySingletonExposure = (mbd.isSingleton() && this.allowCircularReferences &&
				isSingletonCurrentlyInCreation(beanName));
		if (earlySingletonExposure) {
			if (logger.isTraceEnabled()) {
				logger.trace("Eagerly caching bean '" + beanName +
						"' to allow for resolving potential circular references");
			}
			/**
			 * 注：如果允许提前暴漏单例对象，就通过addSingletonFactory方法将获取早期单例的ObjectFactory缓存在三级缓存中
			 * 后续再通过三级缓存中的值调用getObject()方法时实际上就是调用getEarlyBeanReference方法。
			 * 在getEarlyBeanReference方法中主要是为了应用实例化后置处理器获取早期引用的相关回调
			 */
			addSingletonFactory(beanName, () -> getEarlyBeanReference(beanName, mbd, bean));
		}

		// Initialize the bean instance.
		/**
		 * 注：以上为实例化的过程，以下为初始化实例的过程！
		 */
		// 注：获取实例化的bean实例
		Object exposedObject = bean;
		try {
			// 注：属性填充。对bean各个属性值进行注入，可能存在依赖于其它bean的属性，则会递归初始依赖的bean
			populateBean(beanName, mbd, instanceWrapper);
			// 注：初始化Bean，如执行aware接口、执行init-method方法、BeanPostProcessor后置增强等等
			exposedObject = initializeBean(beanName, exposedObject, mbd);
		}
		catch (Throwable ex) {
			if (ex instanceof BeanCreationException && beanName.equals(((BeanCreationException) ex).getBeanName())) {
				throw (BeanCreationException) ex;
			}
			else {
				throw new BeanCreationException(
						mbd.getResourceDescription(), beanName, "Initialization of bean failed", ex);
			}
		}

		// 注：https://blog.csdn.net/Weixiaohuai/article/details/122093896
		/**
		 * 注：如果允许早期暴露，需要进行循环依赖检查
		 * - 为什么要检查？
		 *   当前bean在实例化之后、属性填充&初始化之前就将早期对象引用暴露出去了，如果存在其他bean实例对其的引用，会获取到这个对象引用。
		 *   但注意，这个对象引用真的是当前对象的最终引用吗？还真不一定，如果在初始化阶段的后置处理器可能会返回其他引用作为最终对象。比如动态代理对象
		 *   因此，在bean对象初始化之后，需要检查该bean对象是否存在变化，并且是否有其他bean依赖了已经变化前的对象引用。
		 *
		 */
		if (earlySingletonExposure) {
			/**
			 * 注：尝试通过一级、二级缓存(主要检查二级)中获取早期引用对象。注意这里allowEarlyReference传入的是false
			 * - 如果返回了不为Null的对象，那么说明该对象有被获取过早期引用，即通过三级缓存的ObjectFactory获取早期引用并放置在二级缓存
			 * - 如果返回了Null，那么说明该对象二级缓存中不存在，即没有被获取过早期引用，无循环依赖。
			 */
			Object earlySingletonReference = getSingleton(beanName, false);
			if (earlySingletonReference != null) {
				/**
				 * 注：能进入这里，说明Spring检测到发生了循环依赖
				 * exposedObject == bean：两者相等，说明bean在执行initializeBean()初始化的时候，没有被后置处理器增强，还是原来那个对象
 				 */
				if (exposedObject == bean) {
					/**
					 * 注：这里有个细节。如果初始化未对bean实例进行后置处理，那么会将早期引用对象作为最终返回对象引用
					 * - 如果getEarlyBeanReference回调未修改bean对象，那么早期引用还是bean对象
					 * - 如果getEarlyBeanReference回调修改了bean对象，那么这里返回早期引用，保持其他对象引用的有效性。
					 */
					exposedObject = earlySingletonReference;
				}
				else if (!this.allowRawInjectionDespiteWrapping && hasDependentBean(beanName)) {
					/**
					 * 注：如果不允许注入原始bean实例，并且当前bean存在其他bean对其的依赖。
					 */
					// 注：获取到依赖当前bean的所有bean的beanName
					String[] dependentBeans = getDependentBeans(beanName);
					Set<String> actualDependentBeans = new LinkedHashSet<>(dependentBeans.length);
					for (String dependentBean : dependentBeans) {
						/**
						 * 注：尝试移除之前依赖原始bean实例、且仅用于类型校验的bean实例.
						 * - 因为执行到这里，说明exposedObject != bean，也就是bean已经被后置处理器增强，不是原来的对象了，
						 * - 如果依赖原始bean实例的bean对象并非仅用于类型校验，这里放置在actualDependentBeans集合中。
						 * - actualDependentBeans就存储了spring标准流程的bean对象对原始无效bean对象的依赖，整体流程需要抛出异常。
						 */
						if (!removeSingletonIfCreatedForTypeCheckOnly(dependentBean)) {
							actualDependentBeans.add(dependentBean);
						}
					}
					if (!actualDependentBeans.isEmpty()) {
						// 注：如果存在标准bean对象依赖无效bean对象，则需要抛出异常，Spring不允许脏依赖的存在(可通过allowRawInjectionDespiteWrapping设置)
						throw new BeanCurrentlyInCreationException(beanName,
								"Bean with name '" + beanName + "' has been injected into other beans [" +
								StringUtils.collectionToCommaDelimitedString(actualDependentBeans) +
								"] in its raw version as part of a circular reference, but has eventually been " +
								"wrapped. This means that said other beans do not use the final version of the " +
								"bean. This is often the result of over-eager type matching - consider using " +
								"'getBeanNamesForType' with the 'allowEagerInit' flag turned off, for example.");
					}
				}
			}
		}

		// Register bean as disposable.
		try {
			// 注：注册DisposableBean的实现，在注销时执行来源于DestructionAwareBeanPostProcessors、实现的DisposableBean的destroy方法还有自己配置的destroy-method的处理
			registerDisposableBeanIfNecessary(beanName, bean, mbd);
		}
		catch (BeanDefinitionValidationException ex) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Invalid destruction signature", ex);
		}

		// 注：完成bean的创建并返回
		return exposedObject;
	}

	// 注：对于指定的bean定义预测其最终bean的类型
	@Override
	@Nullable
	protected Class<?> predictBeanType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		// 注：根据bean定义的一系列信息来判断当前bean的目标类型。
		Class<?> targetType = determineTargetType(beanName, mbd, typesToMatch);
		// Apply SmartInstantiationAwareBeanPostProcessors to predict the
		// eventual type after a before-instantiation shortcut.
		// 注：这里应用SmartInstantiationAwareBeanPostProcessors后置处理器的预测bean类型回调方法
		if (targetType != null && !mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
			boolean matchingOnlyFactoryBean = typesToMatch.length == 1 && typesToMatch[0] == FactoryBean.class;
			for (SmartInstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().smartInstantiationAware) {
				Class<?> predicted = bp.predictBeanType(targetType, beanName);	// 注：调用后置处理器预测bean类型
				if (predicted != null &&
						(!matchingOnlyFactoryBean || FactoryBean.class.isAssignableFrom(predicted))) {
					// 返回非null类型，并且不会替换掉FactoryBean类型，就返回回调结果。
					return predicted;
				}
			}
		}
		return targetType;
	}

	/**
	 * Determine the target type for the given bean definition.
	 * 注：判断指定bean定义的目标类型
	 * @param beanName the name of the bean (for error handling purposes)
	 * @param mbd the merged bean definition for the bean
	 * @param typesToMatch the types to match in case of internal type matching purposes
	 * (also signals that the returned {@code Class} will never be exposed to application code)
	 * @return the type for the bean if determinable, or {@code null} otherwise
	 */
	@Nullable
	protected Class<?> determineTargetType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		/**
		 * 注：先通过bean定义中检查是否存在最终类型的缓存【targetType或resolvedTargetType】，不存在则返回null
		 */
		Class<?> targetType = mbd.getTargetType();
		if (targetType == null) {
			/**
			 * 注：下面就是解析bean定义的目标类型(即bean实例化后所属类型)
			 * 根据bean定义是否存在factoryMethodName属性来决定如何判断bean实例的类型：
			 * - 存在工厂方法，那么工厂方法返回的类型实际上就是bean实例的类型，然后根据解析方法返回类型，进而返回对应的Class对象
			 * - 不存在工厂方法，那需要根据beanClassName来加载Class对象。
			 */
			targetType = (mbd.getFactoryMethodName() != null ?
					getTypeForFactoryMethod(beanName, mbd, typesToMatch) :
					resolveBeanClass(mbd, beanName, typesToMatch));
			if (ObjectUtils.isEmpty(typesToMatch) || getTempClassLoader() == null) {
				// 注：使用临时类加载器加载的bean类型对象是不能缓存在bean定义中的。
				mbd.resolvedTargetType = targetType;
			}
		}
		return targetType;
	}

	/**
	 * Determine the target type for the given bean definition which is based on
	 * a factory method. Only called if there is no singleton instance registered
	 * for the target bean already.
	 * 注：根据bean定义中的工厂方法来判断bean实例的最终类型。
	 * 该方法仅当单例bean实例尚未被注册时使用。（意思是如果已经存在该bean定义的实例，就直接通过实例获取其类型即可）
	 * <p>This implementation determines the type matching {@link #createBean}'s
	 * different creation strategies. As far as possible, we'll perform static
	 * type checking to avoid creation of the target bean.
	 * 注：该方法实现决定了不同createBean的创建策略匹配类型。我们将尽可能执行静态类型检查以避免创建目标bean.
	 * @param beanName the name of the bean (for error handling purposes)
	 * @param mbd the merged bean definition for the bean
	 * @param typesToMatch the types to match in case of internal type matching purposes
	 * (also signals that the returned {@code Class} will never be exposed to application code)
	 * @return the type for the bean if determinable, or {@code null} otherwise
	 * @see #createBean
	 * 注：参考资料-https://blog.csdn.net/qq_30321211/article/details/108347133
	 */
	@Nullable
	protected Class<?> getTypeForFactoryMethod(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		// 注：先检查当前合并bean定义中是否已经缓存了工厂方法返回类型(ResolvableType实例)
		ResolvableType cachedReturnType = mbd.factoryMethodReturnType;
		if (cachedReturnType != null) {
			// 注：每个ResolvableType实例在创建的时候都会缓存其解析后的Class类型【统一处理了泛型】。
			return cachedReturnType.resolve();
		}

		/**
		 * 注：当合并bean定义缓存中没有自举工厂方法(唯一)时，就会根据一系列逻辑来判断是否存在唯一的自举工厂方法(uniqueCandidate)，有就会缓存起来；
		 * 如果有多个工厂方法，就需要判断其返回值是否存在共同的类型(commonType)。
		 */
		Class<?> commonType = null;
		Method uniqueCandidate = mbd.factoryMethodToIntrospect;

		// 注：如果不存在工厂方法对象
		if (uniqueCandidate == null) {
			Class<?> factoryClass;
			boolean isStatic = true;

			// 注：从合并bean定义中获取工厂Bean的名称
			String factoryBeanName = mbd.getFactoryBeanName();
			if (factoryBeanName != null) {
				if (factoryBeanName.equals(beanName)) {
					// 注：当前Bean和其工厂bean引同一个bean定义
					throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName,
							"factory-bean reference points back to the same bean definition");
				}
				// Check declared factory method return type on factory class.
				// 注：检查工厂类上声明的工厂方法返回类型
				factoryClass = getType(factoryBeanName);
				isStatic = false;
			}
			else {
				// Check declared factory method return type on bean class.
				// 注：根据合并bean定义的beanName、加载器等返回Class类型
				factoryClass = resolveBeanClass(mbd, beanName, typesToMatch);
			}

			if (factoryClass == null) {
				// 注：如果通过工厂Bean以及beanName都无法获取bean类型，只能返回Null
				return null;
			}
			// 注：处理下Class类型；【如果是CGLIB动态生成类，则返回其父类型，即被代理类型】
			factoryClass = ClassUtils.getUserClass(factoryClass);

			// If all factory methods have the same return type, return that type.
			// Can't clearly figure out exact method due to type converting / autowiring!
			/**
			 * 注：如果所有工厂方法都具有相同的返回类型，则返回该类型。
			 * 由于类型转换/自动匹配，无法明确找出确切的方法。
			 */
			// 注：获取合并bean定义中的构造器参数值的数量，没有就是无参构造-0；
			int minNrOfArgs =
					(mbd.hasConstructorArgumentValues() ? mbd.getConstructorArgumentValues().getArgumentCount() : 0);
			// 注：获取每个类型中的候选工厂方法，后续需遍历
			Method[] candidates = this.factoryMethodCandidateCache.computeIfAbsent(factoryClass,
					clazz -> ReflectionUtils.getUniqueDeclaredMethods(clazz, ReflectionUtils.USER_DECLARED_METHODS));

			for (Method candidate : candidates) {
				// 注：遍历候选方法中，静态的、bean定义中工厂名重名并且方法参数数量>bean定义中给出构造器参数数量  --> 这些都有可能是工厂方法
				if (Modifier.isStatic(candidate.getModifiers()) == isStatic && mbd.isFactoryMethod(candidate) &&
						candidate.getParameterCount() >= minNrOfArgs) {
					// Declared type variables to inspect?
					if (candidate.getTypeParameters().length > 0) {
						// 注：该方法存在泛型类型
						try {
							// Fully resolve parameter names and argument values.
							// 注：完全解析参数名称和参数值
							Class<?>[] paramTypes = candidate.getParameterTypes();	// 注：获取方法参数类型数组
							String[] paramNames = null;		// 注：方法的参数需要进一步解析，解析后的参数名放置在当前数组内
							ParameterNameDiscoverer pnd = getParameterNameDiscoverer();
							if (pnd != null) {
								// 注：使用参数名解析器处理候选参数名
								paramNames = pnd.getParameterNames(candidate);
							}
							ConstructorArgumentValues cav = mbd.getConstructorArgumentValues();
							Set<ConstructorArgumentValues.ValueHolder> usedValueHolders = new HashSet<>(paramTypes.length);
							Object[] args = new Object[paramTypes.length];
							for (int i = 0; i < args.length; i++) {
								ConstructorArgumentValues.ValueHolder valueHolder = cav.getArgumentValue(
										i, paramTypes[i], (paramNames != null ? paramNames[i] : null), usedValueHolders);
								if (valueHolder == null) {
									valueHolder = cav.getGenericArgumentValue(null, null, usedValueHolders);
								}
								if (valueHolder != null) {
									args[i] = valueHolder.getValue();
									usedValueHolders.add(valueHolder);
								}
							}
							// 注：获取当前候选工厂方法的最终返回类型，该方法支持泛型情况下的目标对象获取
							Class<?> returnType = AutowireUtils.resolveReturnTypeForFactoryMethod(
									candidate, args, getBeanClassLoader());
							/**
							 * 注：如果commonType为null，并且解析后返回类型就是其本身的返回类型，那么这种情况下存在唯一的候选工厂方法
							 * 下一次循环就不唯一了，赋值为Null
							 */
							uniqueCandidate = (commonType == null && returnType == candidate.getReturnType() ?
									candidate : null);
							/**
							 * 注：当每次获取到工厂候选方法时，就用其返回类型和之前以获取到的公用类型来取个最低父类型。
							 * 找不到共同父类型就返回null;
							 */
							commonType = ClassUtils.determineCommonAncestor(returnType, commonType);
							if (commonType == null) {
								// Ambiguous return types found: return null to indicate "not determinable".
								// 注：由于存在多个工厂方法，无法准确判断返回类型
								return null;
							}
						}
						catch (Throwable ex) {
							if (logger.isDebugEnabled()) {
								logger.debug("Failed to resolve generic return type for factory method: " + ex);
							}
						}
					}
					else {
						uniqueCandidate = (commonType == null ? candidate : null);
						commonType = ClassUtils.determineCommonAncestor(candidate.getReturnType(), commonType);
						if (commonType == null) {
							// Ambiguous return types found: return null to indicate "not determinable".
							// 注：由于存在多个工厂方法，无法准确判断返回类型
							return null;
						}
					}
				}
			}

			// TODO: 2023/10/25 这里赋值合并bean定义的自举唯一候选方法；至此一处可以初始化这个属性吗？
			mbd.factoryMethodToIntrospect = uniqueCandidate;	
			if (commonType == null) {
				// 注：这个就意味着无法找到多个工厂方法共同的类型，进而也是无法判断类型的
				return null;
			}
		}

		// Common return type found: all factory methods return same type. For a non-parameterized
		// unique candidate, cache the full type declaration context of the target factory method.
		/**
		 * 注：如果bean定义缓存中或者自举过程能够获取唯一的工厂候选方法，那就通过forMethodReturnType来解析方法的返回值；
		 * 反之，则就需要根据自举过程多个工厂方法的共同父类型来返回。
		 * 【这个工厂方法返回类型在这里也会被缓存在合并Bean缓存中】
		 */
		cachedReturnType = (uniqueCandidate != null ?
				ResolvableType.forMethodReturnType(uniqueCandidate) : ResolvableType.forClass(commonType));
		mbd.factoryMethodReturnType = cachedReturnType;
		return cachedReturnType.resolve();
	}

	/**
	 * This implementation attempts to query the FactoryBean's generic parameter metadata
	 * if present to determine the object type. If not present, i.e. the FactoryBean is
	 * declared as a raw type, checks the FactoryBean's {@code getObjectType} method
	 * on a plain instance of the FactoryBean, without bean properties applied yet.
	 * If this doesn't return a type yet, and {@code allowInit} is {@code true} a
	 * full creation of the FactoryBean is used as fallback (through delegation to the
	 * superclass's implementation).
	 * <p>The shortcut check for a FactoryBean is only applied in case of a singleton
	 * FactoryBean. If the FactoryBean instance itself is not kept as singleton,
	 * it will be fully created to check the type of its exposed object.
	 */
	@Override
	protected ResolvableType getTypeForFactoryBean(String beanName, RootBeanDefinition mbd, boolean allowInit) {
		// Check if the bean definition itself has defined the type with an attribute
		ResolvableType result = getTypeForFactoryBeanFromAttributes(mbd);
		if (result != ResolvableType.NONE) {
			return result;
		}

		ResolvableType beanType =
				(mbd.hasBeanClass() ? ResolvableType.forClass(mbd.getBeanClass()) : ResolvableType.NONE);

		// For instance supplied beans try the target type and bean class
		if (mbd.getInstanceSupplier() != null) {
			result = getFactoryBeanGeneric(mbd.targetType);
			if (result.resolve() != null) {
				return result;
			}
			result = getFactoryBeanGeneric(beanType);
			if (result.resolve() != null) {
				return result;
			}
		}

		// Consider factory methods
		String factoryBeanName = mbd.getFactoryBeanName();
		String factoryMethodName = mbd.getFactoryMethodName();

		// Scan the factory bean methods
		if (factoryBeanName != null) {
			if (factoryMethodName != null) {
				// Try to obtain the FactoryBean's object type from its factory method
				// declaration without instantiating the containing bean at all.
				BeanDefinition factoryBeanDefinition = getBeanDefinition(factoryBeanName);
				Class<?> factoryBeanClass;
				if (factoryBeanDefinition instanceof AbstractBeanDefinition &&
						((AbstractBeanDefinition) factoryBeanDefinition).hasBeanClass()) {
					factoryBeanClass = ((AbstractBeanDefinition) factoryBeanDefinition).getBeanClass();
				}
				else {
					RootBeanDefinition fbmbd = getMergedBeanDefinition(factoryBeanName, factoryBeanDefinition);
					factoryBeanClass = determineTargetType(factoryBeanName, fbmbd);
				}
				if (factoryBeanClass != null) {
					result = getTypeForFactoryBeanFromMethod(factoryBeanClass, factoryMethodName);
					if (result.resolve() != null) {
						return result;
					}
				}
			}
			// If not resolvable above and the referenced factory bean doesn't exist yet,
			// exit here - we don't want to force the creation of another bean just to
			// obtain a FactoryBean's object type...
			if (!isBeanEligibleForMetadataCaching(factoryBeanName)) {
				return ResolvableType.NONE;
			}
		}

		// If we're allowed, we can create the factory bean and call getObjectType() early
		if (allowInit) {
			FactoryBean<?> factoryBean = (mbd.isSingleton() ?
					getSingletonFactoryBeanForTypeCheck(beanName, mbd) :
					getNonSingletonFactoryBeanForTypeCheck(beanName, mbd));
			if (factoryBean != null) {
				// Try to obtain the FactoryBean's object type from this early stage of the instance.
				Class<?> type = getTypeForFactoryBean(factoryBean);
				if (type != null) {
					return ResolvableType.forClass(type);
				}
				// No type found for shortcut FactoryBean instance:
				// fall back to full creation of the FactoryBean instance.
				return super.getTypeForFactoryBean(beanName, mbd, true);
			}
		}

		if (factoryBeanName == null && mbd.hasBeanClass() && factoryMethodName != null) {
			// No early bean instantiation possible: determine FactoryBean's type from
			// static factory method signature or from class inheritance hierarchy...
			return getTypeForFactoryBeanFromMethod(mbd.getBeanClass(), factoryMethodName);
		}
		result = getFactoryBeanGeneric(beanType);
		if (result.resolve() != null) {
			return result;
		}
		return ResolvableType.NONE;
	}

	private ResolvableType getFactoryBeanGeneric(@Nullable ResolvableType type) {
		if (type == null) {
			return ResolvableType.NONE;
		}
		return type.as(FactoryBean.class).getGeneric();
	}

	/**
	 * Introspect the factory method signatures on the given bean class,
	 * trying to find a common {@code FactoryBean} object type declared there.
	 * @param beanClass the bean class to find the factory method on
	 * @param factoryMethodName the name of the factory method
	 * @return the common {@code FactoryBean} object type, or {@code null} if none
	 */
	private ResolvableType getTypeForFactoryBeanFromMethod(Class<?> beanClass, String factoryMethodName) {
		// CGLIB subclass methods hide generic parameters; look at the original user class.
		Class<?> factoryBeanClass = ClassUtils.getUserClass(beanClass);
		FactoryBeanMethodTypeFinder finder = new FactoryBeanMethodTypeFinder(factoryMethodName);
		ReflectionUtils.doWithMethods(factoryBeanClass, finder, ReflectionUtils.USER_DECLARED_METHODS);
		return finder.getResult();
	}

	/**
	 * This implementation attempts to query the FactoryBean's generic parameter metadata
	 * if present to determine the object type. If not present, i.e. the FactoryBean is
	 * declared as a raw type, checks the FactoryBean's {@code getObjectType} method
	 * on a plain instance of the FactoryBean, without bean properties applied yet.
	 * If this doesn't return a type yet, a full creation of the FactoryBean is
	 * used as fallback (through delegation to the superclass's implementation).
	 * <p>The shortcut check for a FactoryBean is only applied in case of a singleton
	 * FactoryBean. If the FactoryBean instance itself is not kept as singleton,
	 * it will be fully created to check the type of its exposed object.
	 */
	@Override
	@Deprecated
	@Nullable
	protected Class<?> getTypeForFactoryBean(String beanName, RootBeanDefinition mbd) {
		return getTypeForFactoryBean(beanName, mbd, true).resolve();
	}

	/**
	 * Obtain a reference for early access to the specified bean,
	 * typically for the purpose of resolving a circular reference.
	 * 注：根据指定的bean是获取早期引用
	 * @param beanName the name of the bean (for error handling purposes)
	 * @param mbd the merged bean definition for the bean
	 * @param bean the raw bean instance  注：最原始的bean实例
	 * @return the object to expose as bean reference
	 */
	protected Object getEarlyBeanReference(String beanName, RootBeanDefinition mbd, Object bean) {
		Object exposedObject = bean;
		if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
			// 注：如果bean定义不是动态合成的，并且工厂中存在InstantiationAwareBeanPostProcessor后置处理器实例
			for (SmartInstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().smartInstantiationAware) {
				// 注：遍历SmartInstantiationAwareBeanPostProcessor处理器实例，并调用其getEarlyBeanReference进行处理
				exposedObject = bp.getEarlyBeanReference(exposedObject, beanName);
			}
		}
		return exposedObject;
	}


	//---------------------------------------------------------------------
	// Implementation methods
	//---------------------------------------------------------------------

	/**
	 * Obtain a "shortcut" singleton FactoryBean instance to use for a
	 * {@code getObjectType()} call, without full initialization of the FactoryBean.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @return the FactoryBean instance, or {@code null} to indicate
	 * that we couldn't obtain a shortcut FactoryBean instance
	 */
	@Nullable
	private FactoryBean<?> getSingletonFactoryBeanForTypeCheck(String beanName, RootBeanDefinition mbd) {
		synchronized (getSingletonMutex()) {
			BeanWrapper bw = this.factoryBeanInstanceCache.get(beanName);
			if (bw != null) {
				return (FactoryBean<?>) bw.getWrappedInstance();
			}
			Object beanInstance = getSingleton(beanName, false);
			if (beanInstance instanceof FactoryBean) {
				return (FactoryBean<?>) beanInstance;
			}
			if (isSingletonCurrentlyInCreation(beanName) ||
					(mbd.getFactoryBeanName() != null && isSingletonCurrentlyInCreation(mbd.getFactoryBeanName()))) {
				return null;
			}

			Object instance;
			try {
				// Mark this bean as currently in creation, even if just partially.
				beforeSingletonCreation(beanName);
				// Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
				instance = resolveBeforeInstantiation(beanName, mbd);
				if (instance == null) {
					bw = createBeanInstance(beanName, mbd, null);
					instance = bw.getWrappedInstance();
				}
			}
			catch (UnsatisfiedDependencyException ex) {
				// Don't swallow, probably misconfiguration...
				throw ex;
			}
			catch (BeanCreationException ex) {
				// Instantiation failure, maybe too early...
				if (logger.isDebugEnabled()) {
					logger.debug("Bean creation exception on singleton FactoryBean type check: " + ex);
				}
				onSuppressedException(ex);
				return null;
			}
			finally {
				// Finished partial creation of this bean.
				afterSingletonCreation(beanName);
			}

			FactoryBean<?> fb = getFactoryBean(beanName, instance);
			if (bw != null) {
				this.factoryBeanInstanceCache.put(beanName, bw);
			}
			return fb;
		}
	}

	/**
	 * Obtain a "shortcut" non-singleton FactoryBean instance to use for a
	 * {@code getObjectType()} call, without full initialization of the FactoryBean.
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @return the FactoryBean instance, or {@code null} to indicate
	 * that we couldn't obtain a shortcut FactoryBean instance
	 */
	@Nullable
	private FactoryBean<?> getNonSingletonFactoryBeanForTypeCheck(String beanName, RootBeanDefinition mbd) {
		if (isPrototypeCurrentlyInCreation(beanName)) {
			return null;
		}

		Object instance;
		try {
			// Mark this bean as currently in creation, even if just partially.
			beforePrototypeCreation(beanName);
			// Give BeanPostProcessors a chance to return a proxy instead of the target bean instance.
			instance = resolveBeforeInstantiation(beanName, mbd);
			if (instance == null) {
				BeanWrapper bw = createBeanInstance(beanName, mbd, null);
				instance = bw.getWrappedInstance();
			}
		}
		catch (UnsatisfiedDependencyException ex) {
			// Don't swallow, probably misconfiguration...
			throw ex;
		}
		catch (BeanCreationException ex) {
			// Instantiation failure, maybe too early...
			if (logger.isDebugEnabled()) {
				logger.debug("Bean creation exception on non-singleton FactoryBean type check: " + ex);
			}
			onSuppressedException(ex);
			return null;
		}
		finally {
			// Finished partial creation of this bean.
			afterPrototypeCreation(beanName);
		}

		return getFactoryBean(beanName, instance);
	}

	/**
	 * Apply MergedBeanDefinitionPostProcessors to the specified bean definition,
	 * invoking their {@code postProcessMergedBeanDefinition} methods.
	 * 注：调用合并bean定义的postProcessMergedBeanDefinition方法来处理指定的bean定义。
	 * @param mbd the merged bean definition for the bean
	 * @param beanType the actual type of the managed bean instance
	 * @param beanName the name of the bean
	 * @see MergedBeanDefinitionPostProcessor#postProcessMergedBeanDefinition
	 */
	protected void applyMergedBeanDefinitionPostProcessors(RootBeanDefinition mbd, Class<?> beanType, String beanName) {
		for (MergedBeanDefinitionPostProcessor processor : getBeanPostProcessorCache().mergedDefinition) {
			processor.postProcessMergedBeanDefinition(mbd, beanType, beanName);
		}
	}

	/**
	 * Apply before-instantiation post-processors, resolving whether there is a
	 * before-instantiation shortcut for the specified bean.
	 * 注：应用实例化前的bean后置处理器，解析出是否有返回特定bean的实例
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @return the shortcut-determined bean instance, or {@code null} if none
	 */
	@Nullable
	protected Object resolveBeforeInstantiation(String beanName, RootBeanDefinition mbd) {
		Object bean = null;
		// 注：通过bean定义缓存的实例化前后置处理器处理标识判断当前bean是否需要处理
		if (!Boolean.FALSE.equals(mbd.beforeInstantiationResolved)) {
			// Make sure bean class is actually resolved at this point.
			if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {	// 注：当前并非生成类且具有实例化后置处理器
				// 注：判断指定bean定义的目标类型
				Class<?> targetType = determineTargetType(beanName, mbd);
				if (targetType != null) {
					/**
					 * 注：确保当前bean定义的bean类型是否已经加载
					 * 1. 先调用所有实例化后置处理器的before方法；（只要有1个返回实例就直接返回）
					 * 2. 如果before方法返回了实例，那么就调用所有Bean后置处理器的初始化后置方法。
					 * 这就意味着通过InstantiationAwareBeanPostProcessor返回的实例仅会执行初始化后置方法
					 */
					bean = applyBeanPostProcessorsBeforeInstantiation(targetType, beanName);
					if (bean != null) {
						// 注：通过实例化后置处理器获取到的bean实例也会经过bean后置处理器的postProcessAfterInitialization处理。
						bean = applyBeanPostProcessorsAfterInitialization(bean, beanName);
					}
				}
			}
			// 注：已
			mbd.beforeInstantiationResolved = (bean != null);
		}
		return bean;
	}

	/**
	 * Apply InstantiationAwareBeanPostProcessors to the specified bean definition
	 * (by class and name), invoking their {@code postProcessBeforeInstantiation} methods.
	 * <p>Any returned object will be used as the bean instead of actually instantiating
	 * the target bean. A {@code null} return value from the post-processor will
	 * result in the target bean being instantiated.
	 * @param beanClass the class of the bean to be instantiated
	 * @param beanName the name of the bean
	 * @return the bean object to use instead of a default instance of the target bean, or {@code null}
	 * @see InstantiationAwareBeanPostProcessor#postProcessBeforeInstantiation
	 */
	@Nullable
	protected Object applyBeanPostProcessorsBeforeInstantiation(Class<?> beanClass, String beanName) {
		for (InstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().instantiationAware) {
			Object result = bp.postProcessBeforeInstantiation(beanClass, beanName);
			if (result != null) {
				return result;
			}
		}
		return null;
	}

	/**
	 * Create a new instance for the specified bean, using an appropriate instantiation strategy:
	 * factory method, constructor autowiring, or simple instantiation.
	 * 注：用于创建指定bean的新实例。采用合适的实例化策略：工厂方法、构造器装配或者简单的实例化
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @param args explicit arguments to use for constructor or factory method invocation
	 * @return a BeanWrapper for the new instance
	 * @see #obtainFromSupplier
	 * @see #instantiateUsingFactoryMethod
	 * @see #autowireConstructor
	 * @see #instantiateBean
	 * 注：参考--> https://blog.csdn.net/Weixiaohuai/article/details/122093896
	 */
	protected BeanWrapper createBeanInstance(String beanName, RootBeanDefinition mbd, @Nullable Object[] args) {
		// Make sure bean class is actually resolved at this point.
		// 注：实例化之前必须确保bean的类型已经被加载
		Class<?> beanClass = resolveBeanClass(mbd, beanName);

		if (beanClass != null && !Modifier.isPublic(beanClass.getModifiers()) && !mbd.isNonPublicAccessAllowed()) {
			// 注：如果bean为非公共权限，并且bean定义不允许访问非公共方法或构造器，抛出异常！
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					"Bean class isn't public, and non-public access not allowed: " + beanClass.getName());
		}

		Supplier<?> instanceSupplier = mbd.getInstanceSupplier();
		if (instanceSupplier != null) {
			/**
			 * 注：如果bean定义中有指定创建实例的回调，这里将通过回调创建bean实例对象
			 * 正常bd中都不会有这个instanceSupplier属性，这里也是Spring提供的一个扩展点，但实际上不常用
			 */
			return obtainFromSupplier(instanceSupplier, beanName);
		}

		if (mbd.getFactoryMethodName() != null) {
			/**
			 * 注：如果bean定义了工厂方法，那么就是用工厂方法获取bean实例
			 * - bean定义中提供factoryMethodName属性，使用对应的工厂方法来创建实例
			 * - 指定了”factory-method“属性的bean是通过工厂方法来进行获取实例。
			 * - 指定了“factory-bean”属性的bean是通过该属性对应的工厂bean对象的实例方法来获取实例。
			 * - 【注意】实现了FactoryBean接口的bean和其工厂对象是通过名称前缀“&”进行区分。其不存在工厂方法和工厂bean对象。
			 * - 【注意"工厂对象"、“工厂方法”、"工厂bean对象"几个概念的区分】
			 */
			return instantiateUsingFactoryMethod(beanName, mbd, args);
		}

		// Shortcut when re-creating the same bean...
		/**
		 * 注：下面为正常bean的实例化创建逻辑，需要推断构造函数后，调用bean工厂实例化策略来完成实例化动作。
		 * 在原型模式下，如果已经创建过该bean类型实例，就会将构造器缓存在bean定义中，以便下次直接使用。
		 */
		// 注：bean定义中是否已缓存解析后的构造函数
		boolean resolved = false;
		// 注：构造函数是否需要进行自动注入
		boolean autowireNecessary = false;
		if (args == null) {
			/**
			 * 注：只有getBean传过来的参数为null(即没有传)才会使用bean定义缓存的构造器及参数。
			 * 不同参数会匹配到不同的构造器，因此args不为Null时可不一定是正常解析的构造方法。
			 */
			synchronized (mbd.constructorArgumentLock) {
				if (mbd.resolvedConstructorOrFactoryMethod != null) {
					// 注：如果bean定义中存在已解析的无参构造器，则这设置已解析标识，后续不在进行匹配构造器了
					resolved = true;
					// 注：标识构造器是否已解析完成(解析完成意味着存在构造器参数，需要考虑自动注入)
					autowireNecessary = mbd.constructorArgumentsResolved;
				}
			}
		}
		if (resolved) {
			if (autowireNecessary) {
				// 注：已解析，并且存在构造器参数需要自动注入
				return autowireConstructor(beanName, mbd, null, null);
			}
			else {
				/**
				 * 注：已解析无参构造器，不需要自动注入，
				 * 则直接调用bean工厂的实例化策略进行实例化，并封装为BeanWrapper返回。
				 */
				return instantiateBean(beanName, mbd);
			}
		}

		// 注：参考--> https://juejin.cn/post/7081553925190467614
		// Candidate constructors for autowiring?
		/**
		 * 注：运行到这里基本上就是无自定义创建逻辑、无工厂方法、且为第一次创建bean实例。
		 * 那么如何找出构造方法呢？
		 * 1. 首先根据SmartInstantiationAwareBeanPostProcessor后置处理器的determineCandidateConstructors回调方法来确定
		 * 2. 根据bean定义中提供的更优构造器列表进行匹配并实例化
		 * 3. 默认使用无参构造器实例化进行兜底。
		 */
		Constructor<?>[] ctors = determineConstructorsFromBeanPostProcessors(beanClass, beanName);
		if (ctors != null || mbd.getResolvedAutowireMode() == AUTOWIRE_CONSTRUCTOR ||
				mbd.hasConstructorArgumentValues() || !ObjectUtils.isEmpty(args)) {
			/**
			 * 注：在以下情况下，会通过构造器进行匹配并实例化：
			 * - 通过BeanPostProcessor找出了构造方法
			 * - BeanDefinition的autowire属性为AUTOWIRE_CONSTRUCTOR，即在xml中使用了 autowire="constructor"
			 * - BeanDefinition中指定了构造方法参数值 使用了 <constructor-arg>标签
			 * - 在getBean()时指定了args
			 */
			return autowireConstructor(beanName, mbd, ctors, args);
		}

		// Preferred constructors for default construction?
		// 注：bean定义是否提供了更优的构造器，比如Kotlin的主构造器。【一般返回的都是Null】
		ctors = mbd.getPreferredConstructors();
		if (ctors != null) {
			// 注：根据指定的构造器列表自动注入匹配构造器，并实例化返回。
			return autowireConstructor(beanName, mbd, ctors, null);
		}

		// No special handling: simply use no-arg constructor.
		// 注：没有特殊的处理：仅使用无参构造器实例化即可
		return instantiateBean(beanName, mbd);
	}

	/**
	 * Obtain a bean instance from the given supplier.
	 * 注：根据指定的回调函数创建对应bean实例
	 * @param instanceSupplier the configured supplier
	 * @param beanName the corresponding bean name
	 * @return a BeanWrapper for the new instance
	 * @since 5.0
	 * @see #getObjectForBeanInstance
	 * 注：参考->> https://blog.csdn.net/qq_30321211/article/details/108350140
	 */
	protected BeanWrapper obtainFromSupplier(Supplier<?> instanceSupplier, String beanName) {
		Object instance;

		// 注：获取当前线程正在创建的bean名称
		String outerBean = this.currentlyCreatedBean.get();
		// 注：将当前镇正要创建的bean名称设置到当前线程内，以便依赖注册使用
		this.currentlyCreatedBean.set(beanName);
		try {
			// 注：从回调方法中获取bean实例
			instance = instanceSupplier.get();
		}
		finally {
			// 注：回调方法执行后，需要正在创建的bean名称还设置到线程内；无则直接移除当前bean名称即可。
			if (outerBean != null) {
				this.currentlyCreatedBean.set(outerBean);
			}
			else {
				this.currentlyCreatedBean.remove();
			}
		}

		if (instance == null) {
			// 注：回调方法可能会返回null值，spring的bean实例不能为null，使用NullBean类型代替。
			instance = new NullBean();
		}
		/**
		 * BeanWrapperImpl类是对BeanWrapper接口的默认实现，它包装了一个bean对象，
		 * 缓存了bean的内省结果， 并可以访问bean的属性、设置bean的属性值。BeanWrapperImpl
		 * 类提供了许多默认属性编辑器， 支持多种不同类型的类型转换，可以将数组、集合类型的属
		 * 性转换成指定特殊类型的数组或集合。 用户也可以注册自定义的属性编辑器在BeanWrapperImpl中。
		 * 如果没有成功获取到bean实例
		 */
		BeanWrapper bw = new BeanWrapperImpl(instance);		// 注：使用BeanWrapper对bean实例进行包装
		initBeanWrapper(bw);	// 注：初始化包装对象
		return bw;
	}

	/**
	 * Overridden in order to implicitly register the currently created bean as
	 * dependent on further beans getting programmatically retrieved during a
	 * {@link Supplier} callback.
	 * 注：重写getObjectForBeanInstance方法，以便于隐式地注册线程中当前正在创建的bean依赖于Supplier回调期间以编程方法检索的其他bean
	 * @since 5.0
	 * @see #obtainFromSupplier
	 */
	@Override
	protected Object getObjectForBeanInstance(
			Object beanInstance, String name, String beanName, @Nullable RootBeanDefinition mbd) {

		// 注：从线程上下文中获取当前正常创建的bean实例；何时添加到线程上下文中？
		String currentlyCreatedBean = this.currentlyCreatedBean.get();
		if (currentlyCreatedBean != null) {
			// 注：如果存在当前线程正在创建的bean，则此处注册线程bean依赖于当前beanName
			registerDependentBean(beanName, currentlyCreatedBean);
		}

		// 注：继续从当前bean实例中获取对象；即解析FactoryBean的过程
		return super.getObjectForBeanInstance(beanInstance, name, beanName, mbd);
	}

	/**
	 * Determine candidate constructors to use for the given bean, checking all registered
	 * {@link SmartInstantiationAwareBeanPostProcessor SmartInstantiationAwareBeanPostProcessors}.
	 * @param beanClass the raw class of the bean
	 * @param beanName the name of the bean
	 * @return the candidate constructors, or {@code null} if none specified
	 * @throws org.springframework.beans.BeansException in case of errors
	 * @see org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor#determineCandidateConstructors
	 */
	@Nullable
	protected Constructor<?>[] determineConstructorsFromBeanPostProcessors(@Nullable Class<?> beanClass, String beanName)
			throws BeansException {

		if (beanClass != null && hasInstantiationAwareBeanPostProcessors()) {
			for (SmartInstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().smartInstantiationAware) {
				Constructor<?>[] ctors = bp.determineCandidateConstructors(beanClass, beanName);
				if (ctors != null) {
					return ctors;
				}
			}
		}
		return null;
	}

	/**
	 * Instantiate the given bean using its default constructor.
	 * 注：使用缓存的无参构造器进行初始化bean实例
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @return a BeanWrapper for the new instance
	 */
	protected BeanWrapper instantiateBean(String beanName, RootBeanDefinition mbd) {
		try {
			Object beanInstance;
			if (System.getSecurityManager() != null) {
				beanInstance = AccessController.doPrivileged(
						(PrivilegedAction<Object>) () -> getInstantiationStrategy().instantiate(mbd, beanName, this),
						getAccessControlContext());
			}
			else {
				// 注：调用bean工厂的实例化策略，根据bean定义中缓存的无参构造器(或根据bean类型反射获取)进行实例化
				beanInstance = getInstantiationStrategy().instantiate(mbd, beanName, this);
			}
			// 注：将实例化的bean通过BeanWrapper进行包装后返回
			BeanWrapper bw = new BeanWrapperImpl(beanInstance);
			initBeanWrapper(bw);
			return bw;
		}
		catch (Throwable ex) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Instantiation of bean failed", ex);
		}
	}

	/**
	 * Instantiate the bean using a named factory method. The method may be static, if the
	 * mbd parameter specifies a class, rather than a factoryBean, or an instance variable
	 * on a factory object itself configured using Dependency Injection.
	 * 注：使用指定的工厂方法来创建一个bean实例。
	 * 如果bean定义参数指定了一个非工厂bean，或者使用依赖注入的工厂对象自身的属性变量，那么这个方法可能是一个静态方法。
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @param explicitArgs argument values passed in programmatically via the getBean method,
	 * or {@code null} if none (-> use constructor argument values from bean definition)
	 * 注：通过getBean方法透传过来的构造方法或工厂方法参数。
	 * @return a BeanWrapper for the new instance
	 * @see #getBean(String, Object[])
	 */
	protected BeanWrapper instantiateUsingFactoryMethod(
			String beanName, RootBeanDefinition mbd, @Nullable Object[] explicitArgs) {

		return new ConstructorResolver(this).instantiateUsingFactoryMethod(beanName, mbd, explicitArgs);
	}

	/**
	 * "autowire constructor" (with constructor arguments by type) behavior.
	 * Also applied if explicit constructor argument values are specified,
	 * matching all remaining arguments with beans from the bean factory.
	 * 注：根据构造器参数类型来对构造器进行自动装配；
	 * 如果指定了明确的构造器参数(explicitArgs)会被匹配使用，所有剩余的参数类型将会通过bean工厂bean类型进行匹配。
	 * <p>This corresponds to constructor injection: In this mode, a Spring
	 * bean factory is able to host components that expect constructor-based
	 * dependency resolution.
	 * 注：对于构造器注入：在这种模式下，spring的bean工厂具备根据期望构造器依赖解析来管理组件。
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @param ctors the chosen candidate constructors
	 * @param explicitArgs argument values passed in programmatically via the getBean method,
	 * or {@code null} if none (-> use constructor argument values from bean definition)
	 * @return a BeanWrapper for the new instance
	 */
	protected BeanWrapper autowireConstructor(
			String beanName, RootBeanDefinition mbd, @Nullable Constructor<?>[] ctors, @Nullable Object[] explicitArgs) {

		return new ConstructorResolver(this).autowireConstructor(beanName, mbd, ctors, explicitArgs);
	}

	/**
	 * Populate the bean instance in the given BeanWrapper with the property values
	 * from the bean definition.
	 * 注：使用bean定义中的属性值来填充给定bean包装器中的bean实例
	 * @param beanName the name of the bean
	 * @param mbd the bean definition for the bean
	 * @param bw the BeanWrapper with bean instance
	 * 注：参考--> https://blog.csdn.net/Weixiaohuai/article/details/122093896
	 */
	@SuppressWarnings("deprecation")  // for postProcessPropertyValues
	protected void populateBean(String beanName, RootBeanDefinition mbd, @Nullable BeanWrapper bw) {
		if (bw == null) {
			/**
			 * 注：bean包装器为null的情况下，如果bean定义中存在属性值，就需要抛出异常，无法填充给任何bean对象。
			 */
			if (mbd.hasPropertyValues()) {
				throw new BeanCreationException(
						mbd.getResourceDescription(), beanName, "Cannot apply property values to null instance");
			}
			else {
				// Skip property population phase for null instance.
				// 注：对于无填充属性且实例为Null的情况，直接跳过即可
				return;
			}
		}

		// Give any InstantiationAwareBeanPostProcessors the opportunity to modify the
		// state of the bean before properties are set. This can be used, for example,
		// to support styles of field injection.
		/**
		 * 注：在属性填充之前，提供InstantiationAwareBeanPostProcessor后置处理器时机去修改bean定义的一些数据。
		 * 这个机制可以用于支持属性注入样式的扩展。
		 */
		if (!mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
			// 注：如果bean定义非动态生成的，且bean工厂中具有实例化后置处理器
			for (InstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().instantiationAware) {
				if (!bp.postProcessAfterInstantiation(bw.getWrappedInstance(), beanName)) {
					// 注：如果当前实例化后置处理器处理后返回false，阻止后续bean属性的注入以及后续处理器的处理过程。
					return;
				}
			}
		}

		// 注：从bean定义中获取配置的属性值
		PropertyValues pvs = (mbd.hasPropertyValues() ? mbd.getPropertyValues() : null);

		// 注：从bean定义中获取自动注入模式
		int resolvedAutowireMode = mbd.getResolvedAutowireMode();
		if (resolvedAutowireMode == AUTOWIRE_BY_NAME || resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
			// 注：如果是按照名称或者类型进行自动装配，就根据将配置的属性值pvs解析后存储在新属性值对象上newPvs
			MutablePropertyValues newPvs = new MutablePropertyValues(pvs);
			// Add property values based on autowire by name if applicable.
			if (resolvedAutowireMode == AUTOWIRE_BY_NAME) {
				// 注：按照名称进行自动装配
				autowireByName(beanName, mbd, bw, newPvs);
			}
			// Add property values based on autowire by type if applicable.
			if (resolvedAutowireMode == AUTOWIRE_BY_TYPE) {
				// 注：按照类型进行自动装配
				autowireByType(beanName, mbd, bw, newPvs);
			}
			pvs = newPvs;
		}

		// 注：是否存在后实例化后置处理器(InstantiationAwareBeanPostProcessor)
		boolean hasInstAwareBpps = hasInstantiationAwareBeanPostProcessors();
		// 注：是否要进行依赖检查
		boolean needsDepCheck = (mbd.getDependencyCheck() != AbstractBeanDefinition.DEPENDENCY_CHECK_NONE);

		PropertyDescriptor[] filteredPds = null;
		if (hasInstAwareBpps) {
			// 注：如果存在实例化后置处理器，需要执行postProcessProperties方法。
			if (pvs == null) {
				pvs = mbd.getPropertyValues();
			}
			/**
			 * 注：执行InstantiationAwareBeanPostProcessor的postProcessProperties()以及postProcessPropertyValues()方法回调
			 * postProcessProperties(): 允许对填充前的属性进行处理（如对属性的验证）
			 * postProcessPropertyValues(): 对属性值进行修改，通过基于原始的PropertyValues创建一个新的MutablePropertyValues实例，添加或删除特定的值。
			 * 不过目前方法已经被标记为过期，在后续Spring版本中可能会被删除
			 */
			for (InstantiationAwareBeanPostProcessor bp : getBeanPostProcessorCache().instantiationAware) {
				PropertyValues pvsToUse = bp.postProcessProperties(pvs, bw.getWrappedInstance(), beanName);
				if (pvsToUse == null) {
					if (filteredPds == null) {
						filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
					}
					pvsToUse = bp.postProcessPropertyValues(pvs, filteredPds, bw.getWrappedInstance(), beanName);
					if (pvsToUse == null) {
						return;
					}
				}
				pvs = pvsToUse;
			}
		}
		if (needsDepCheck) {
			// 注：执行依赖检查，对应depend-on属性
			if (filteredPds == null) {
				filteredPds = filterPropertyDescriptorsForDependencyCheck(bw, mbd.allowCaching);
			}
			checkDependencies(beanName, mbd, filteredPds, pvs);
		}

		if (pvs != null) {
			// 注：属性填充的具体过程，即将属性值赋值到beanWrapper中bean实例的具体属性中
			applyPropertyValues(beanName, mbd, bw, pvs);
		}
	}

	/**
	 * Fill in any missing property values with references to
	 * other beans in this factory if autowire is set to "byName".
	 * 注：如果当前bean的装配模式为按照bean名称装配，
	 * 这里会获取当前bean工厂内的相应名称的bean来填充任何确实的属性值。
	 * @param beanName the name of the bean we're wiring up.
	 * Useful for debugging messages; not used functionally.
	 * @param mbd bean definition to update through autowiring
	 * @param bw the BeanWrapper from which we can obtain information about the bean
	 * @param pvs the PropertyValues to register wired objects with
	 */
	protected void autowireByName(
			String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues pvs) {

		/**
		 * 注：根据bean的类型以及bean定义中存储的属性值，来获取需要注入的属性名称数组【属性值缺失，需要自动装配】。
		 * - 这里的属性具有一定条件的限制，比如不是简单属性类型，比如基础类型、枚举、Number等。
		 */
		String[] propertyNames = unsatisfiedNonSimpleProperties(mbd, bw);
		// 注：遍历这些需要自动装配的属性值
		for (String propertyName : propertyNames) {
			// 注：根据名称来判断当前bean工厂中是否包含该属性名的bean
			if (containsBean(propertyName)) {
				// 注：如果存在该bean，就调用getBean获取名称为propertyName的实例bean
				Object bean = getBean(propertyName);
				// 注：将当前属性名以及其对应的属性值(bean实例)设置到属性值对象中
				pvs.add(propertyName, bean);
				// 注：向bean工厂注册这种依赖关系，beanName依赖于propertyName
				registerDependentBean(propertyName, beanName);
				if (logger.isTraceEnabled()) {
					logger.trace("Added autowiring by name from bean name '" + beanName +
							"' via property '" + propertyName + "' to bean named '" + propertyName + "'");
				}
			}
			else {
				if (logger.isTraceEnabled()) {
					logger.trace("Not autowiring property '" + propertyName + "' of bean '" + beanName +
							"' by name: no matching bean found");
				}
			}
		}
	}

	/**
	 * Abstract method defining "autowire by type" (bean properties by type) behavior.
	 * <p>This is like PicoContainer default, in which there must be exactly one bean
	 * of the property type in the bean factory. This makes bean factories simple to
	 * configure for small namespaces, but doesn't work as well as standard Spring
	 * behavior for bigger applications.
	 * 注：根据类型进行装配逻辑的抽象方法(哪里抽象？)
	 * 这类似于PicoContainer(非常轻量级的IOC容器)默认值，其中在bean工厂中必须存在一个当前属性类型的bean。
	 * 这使得bean工厂很容易配置小型命名空间，但对于大型应用，这种效果不如标准spring行为好。
	 * @param beanName the name of the bean to autowire by type
	 * @param mbd the merged bean definition to update through autowiring
	 * @param bw the BeanWrapper from which we can obtain information about the bean
	 * @param pvs the PropertyValues to register wired objects with
	 * 注：参考--> https://blog.csdn.net/GDUT_Trim/article/details/120935879、https://blog.csdn.net/qq_30321211/article/details/108357238
	 */
	protected void autowireByType(
			String beanName, AbstractBeanDefinition mbd, BeanWrapper bw, MutablePropertyValues pvs) {

		// 注：获取bean工厂自定义的类型转换器
		TypeConverter converter = getCustomTypeConverter();
		if (converter == null) {
			// 注：如果不存在自定义类型转换器就是用当前bean的包装器实例
			converter = bw;
		}

		// 注：在当前bean中已自动装配的bean名称，后续会根据该集合记录bean之间的依赖关系
		Set<String> autowiredBeanNames = new LinkedHashSet<>(4);
		// 注：根据bean的类型以及bean定义中存储的属性值，来获取需要注入的属性名称数组【属性值缺失，需要自动装配】。
		String[] propertyNames = unsatisfiedNonSimpleProperties(mbd, bw);
		for (String propertyName : propertyNames) {		// 注：遍历所有需要注入的属性
			try {
				// 注：根据属性名从bean包装器中获取属性描述对象
				PropertyDescriptor pd = bw.getPropertyDescriptor(propertyName);
				// Don't try autowiring by type for type Object: never makes sense,
				// even if it technically is a unsatisfied, non-simple property.
				/**
				 * 注：不要试图自动装配Object类型的属性，没有任何意义。
				 * 即使该属性为未满足、非简易属性。
				 */
				if (Object.class != pd.getPropertyType()) {
					// 注：获取当前属性的写方法(Setter)的属性(第一个属性)
					MethodParameter methodParam = BeanUtils.getWriteMethodParameter(pd);
					// Do not allow eager init for type matching in case of a prioritized post-processor.
					boolean eager = !(bw.getWrappedInstance() instanceof PriorityOrdered);
					DependencyDescriptor desc = new AutowireByTypeDependencyDescriptor(methodParam, eager);
					Object autowiredArgument = resolveDependency(desc, beanName, autowiredBeanNames, converter);
					if (autowiredArgument != null) {
						pvs.add(propertyName, autowiredArgument);
					}
					for (String autowiredBeanName : autowiredBeanNames) {
						registerDependentBean(autowiredBeanName, beanName);
						if (logger.isTraceEnabled()) {
							logger.trace("Autowiring by type from bean name '" + beanName + "' via property '" +
									propertyName + "' to bean named '" + autowiredBeanName + "'");
						}
					}
					autowiredBeanNames.clear();
				}
			}
			catch (BeansException ex) {
				throw new UnsatisfiedDependencyException(mbd.getResourceDescription(), beanName, propertyName, ex);
			}
		}
	}


	/**
	 * Return an array of non-simple bean properties that are unsatisfied.
	 * These are probably unsatisfied references to other beans in the
	 * factory. Does not include simple properties like primitives or Strings.
	 * 注：返回bean的非简属性数组。这可能是其他bean实例的不完全引用。
	 * 返回的数组中不包括基本的属性，比如基本类型或者字符串类型。
	 * @param mbd the merged bean definition the bean was created with
	 * @param bw the BeanWrapper the bean was created with
	 * @return an array of bean property names
	 * @see org.springframework.beans.BeanUtils#isSimpleProperty
	 */
	protected String[] unsatisfiedNonSimpleProperties(AbstractBeanDefinition mbd, BeanWrapper bw) {
		// 注：创建一个treeSet进行去重
		Set<String> result = new TreeSet<>();
		// 注：获取bean定义中配置的属性
		PropertyValues pvs = mbd.getPropertyValues();
		// 注：bean类型中属性描述数组
		PropertyDescriptor[] pds = bw.getPropertyDescriptors();
		for (PropertyDescriptor pd : pds) {
			if (pd.getWriteMethod() != null && !isExcludedFromDependencyCheck(pd) && !pvs.contains(pd.getName()) &&
					!BeanUtils.isSimpleProperty(pd.getPropertyType())) {
				/**
				 * 遍历bean类型的所有属性，筛选出满足一下条件的属性作为非简属性返回：
				 * 1. 属性存在对应的写方法【后续研究下PropertyDescriptor】
				 * 2. 当前属性需要依赖检查
				 * 3. bean定义中存储属性值中不存在当前属性
				 * 4. 当前属性并非是简单类型（数组类型以组件元素类型并非是简单类型）
				 */
				result.add(pd.getName());
			}
		}
		// 注：将属性名称以字符串数组返回
		return StringUtils.toStringArray(result);
	}

	/**
	 * Extract a filtered set of PropertyDescriptors from the given BeanWrapper,
	 * excluding ignored dependency types or properties defined on ignored dependency interfaces.
	 * @param bw the BeanWrapper the bean was created with
	 * @param cache whether to cache filtered PropertyDescriptors for the given bean Class
	 * @return the filtered PropertyDescriptors
	 * @see #isExcludedFromDependencyCheck
	 * @see #filterPropertyDescriptorsForDependencyCheck(org.springframework.beans.BeanWrapper)
	 */
	protected PropertyDescriptor[] filterPropertyDescriptorsForDependencyCheck(BeanWrapper bw, boolean cache) {
		PropertyDescriptor[] filtered = this.filteredPropertyDescriptorsCache.get(bw.getWrappedClass());
		if (filtered == null) {
			filtered = filterPropertyDescriptorsForDependencyCheck(bw);
			if (cache) {
				PropertyDescriptor[] existing =
						this.filteredPropertyDescriptorsCache.putIfAbsent(bw.getWrappedClass(), filtered);
				if (existing != null) {
					filtered = existing;
				}
			}
		}
		return filtered;
	}

	/**
	 * Extract a filtered set of PropertyDescriptors from the given BeanWrapper,
	 * excluding ignored dependency types or properties defined on ignored dependency interfaces.
	 * @param bw the BeanWrapper the bean was created with
	 * @return the filtered PropertyDescriptors
	 * @see #isExcludedFromDependencyCheck
	 */
	protected PropertyDescriptor[] filterPropertyDescriptorsForDependencyCheck(BeanWrapper bw) {
		List<PropertyDescriptor> pds = new ArrayList<>(Arrays.asList(bw.getPropertyDescriptors()));
		pds.removeIf(this::isExcludedFromDependencyCheck);
		return pds.toArray(new PropertyDescriptor[0]);
	}

	/**
	 * Determine whether the given bean property is excluded from dependency checks.
	 * <p>This implementation excludes properties defined by CGLIB and
	 * properties whose type matches an ignored dependency type or which
	 * are defined by an ignored dependency interface.
	 * 注：判断指定的ben属性是否从依赖检查中排除。当前方法的实现排除了以下属性：
	 * - 由CGLIB动态定义的属性
	 * - 缓存在ignoredDependencyTypes集合中的类型属性
	 * - 由ignoredDependencyInterfaces集合的接口定义该属性的setter方法
	 * @param pd the PropertyDescriptor of the bean property
	 * @return whether the bean property is excluded
	 * @see #ignoreDependencyType(Class)
	 * @see #ignoreDependencyInterface(Class)
	 */
	protected boolean isExcludedFromDependencyCheck(PropertyDescriptor pd) {
		return (AutowireUtils.isExcludedFromDependencyCheck(pd) ||
				this.ignoredDependencyTypes.contains(pd.getPropertyType()) ||
				AutowireUtils.isSetterDefinedInInterface(pd, this.ignoredDependencyInterfaces));
	}

	/**
	 * Perform a dependency check that all properties exposed have been set,
	 * if desired. Dependency checks can be objects (collaborating beans),
	 * simple (primitives and String), or all (both).
	 * 注：对需要设置的暴露属性进行依赖检查。依赖检查可以是对象(bean实例)、简单类型(原始类型和String对象)或者二者
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition the bean was created with
	 * @param pds the relevant property descriptors for the target bean
	 * @param pvs the property values to be applied to the bean
	 * @see #isExcludedFromDependencyCheck(java.beans.PropertyDescriptor)
	 */
	protected void checkDependencies(
			String beanName, AbstractBeanDefinition mbd, PropertyDescriptor[] pds, @Nullable PropertyValues pvs)
			throws UnsatisfiedDependencyException {

		int dependencyCheck = mbd.getDependencyCheck();		// 注：依赖检查的模式
		for (PropertyDescriptor pd : pds) {
			if (pd.getWriteMethod() != null && (pvs == null || !pvs.contains(pd.getName()))) {
				boolean isSimple = BeanUtils.isSimpleProperty(pd.getPropertyType());
				boolean unsatisfied = (dependencyCheck == AbstractBeanDefinition.DEPENDENCY_CHECK_ALL) ||
						(isSimple && dependencyCheck == AbstractBeanDefinition.DEPENDENCY_CHECK_SIMPLE) ||
						(!isSimple && dependencyCheck == AbstractBeanDefinition.DEPENDENCY_CHECK_OBJECTS);
				if (unsatisfied) {
					throw new UnsatisfiedDependencyException(mbd.getResourceDescription(), beanName, pd.getName(),
							"Set this property value or disable dependency checking for this bean.");
				}
			}
		}
	}

	/**
	 * Apply the given property values, resolving any runtime references
	 * to other beans in this bean factory. Must use deep copy, so we
	 * don't permanently modify this property.
	 * 注：将从bean工厂解析到的其他bean应用到指定的属性值上。
	 * 必须使用深度拷贝，因此我们不会永久的修改这个属性。
	 * @param beanName the bean name passed for better exception information // 注：bean名称，用于输出完整的异常信息
	 * @param mbd the merged bean definition	// 注：当前bean的合并bean定义
	 * @param bw the BeanWrapper wrapping the target object // 注：bean实例的包装对象
	 * @param pvs the new property values // 注：新的属性值
	 */
	protected void applyPropertyValues(String beanName, BeanDefinition mbd, BeanWrapper bw, PropertyValues pvs) {
		if (pvs.isEmpty()) {
			// 注：如果没有解析后的属性值，直接返回
			return;
		}

		if (System.getSecurityManager() != null && bw instanceof BeanWrapperImpl) {
			// 注：设置安全上下文
			((BeanWrapperImpl) bw).setSecurityContext(getAccessControlContext());
		}

		MutablePropertyValues mpvs = null;
		List<PropertyValue> original;

		if (pvs instanceof MutablePropertyValues) {
			mpvs = (MutablePropertyValues) pvs;
			if (mpvs.isConverted()) {
				// Shortcut: use the pre-converted values as-is.
				try {
					bw.setPropertyValues(mpvs);
					return;
				}
				catch (BeansException ex) {
					throw new BeanCreationException(
							mbd.getResourceDescription(), beanName, "Error setting property values", ex);
				}
			}
			original = mpvs.getPropertyValueList();
		}
		else {
			original = Arrays.asList(pvs.getPropertyValues());
		}

		TypeConverter converter = getCustomTypeConverter();
		if (converter == null) {
			converter = bw;
		}
		BeanDefinitionValueResolver valueResolver = new BeanDefinitionValueResolver(this, beanName, mbd, converter);

		// Create a deep copy, resolving any references for values.
		List<PropertyValue> deepCopy = new ArrayList<>(original.size());
		boolean resolveNecessary = false;
		for (PropertyValue pv : original) {
			if (pv.isConverted()) {
				deepCopy.add(pv);
			}
			else {
				String propertyName = pv.getName();
				Object originalValue = pv.getValue();
				if (originalValue == AutowiredPropertyMarker.INSTANCE) {
					Method writeMethod = bw.getPropertyDescriptor(propertyName).getWriteMethod();
					if (writeMethod == null) {
						throw new IllegalArgumentException("Autowire marker for property without write method: " + pv);
					}
					originalValue = new DependencyDescriptor(new MethodParameter(writeMethod, 0), true);
				}
				Object resolvedValue = valueResolver.resolveValueIfNecessary(pv, originalValue);
				Object convertedValue = resolvedValue;
				boolean convertible = bw.isWritableProperty(propertyName) &&
						!PropertyAccessorUtils.isNestedOrIndexedProperty(propertyName);
				if (convertible) {
					convertedValue = convertForProperty(resolvedValue, propertyName, bw, converter);
				}
				// Possibly store converted value in merged bean definition,
				// in order to avoid re-conversion for every created bean instance.
				if (resolvedValue == originalValue) {
					if (convertible) {
						pv.setConvertedValue(convertedValue);
					}
					deepCopy.add(pv);
				}
				else if (convertible && originalValue instanceof TypedStringValue &&
						!((TypedStringValue) originalValue).isDynamic() &&
						!(convertedValue instanceof Collection || ObjectUtils.isArray(convertedValue))) {
					pv.setConvertedValue(convertedValue);
					deepCopy.add(pv);
				}
				else {
					resolveNecessary = true;
					deepCopy.add(new PropertyValue(pv, convertedValue));
				}
			}
		}
		if (mpvs != null && !resolveNecessary) {
			mpvs.setConverted();
		}

		// Set our (possibly massaged) deep copy.
		try {
			bw.setPropertyValues(new MutablePropertyValues(deepCopy));
		}
		catch (BeansException ex) {
			throw new BeanCreationException(
					mbd.getResourceDescription(), beanName, "Error setting property values", ex);
		}
	}

	/**
	 * Convert the given value for the specified target property.
	 */
	@Nullable
	private Object convertForProperty(
			@Nullable Object value, String propertyName, BeanWrapper bw, TypeConverter converter) {

		if (converter instanceof BeanWrapperImpl) {
			return ((BeanWrapperImpl) converter).convertForProperty(value, propertyName);
		}
		else {
			PropertyDescriptor pd = bw.getPropertyDescriptor(propertyName);
			MethodParameter methodParam = BeanUtils.getWriteMethodParameter(pd);
			return converter.convertIfNecessary(value, pd.getPropertyType(), methodParam);
		}
	}


	/**
	 * Initialize the given bean instance, applying factory callbacks
	 * as well as init methods and bean post processors.
	 * 注：初始化指定的bean实例，应用工厂回调方法、初始化方法以及后置处理器相关方法
	 * <p>Called from {@link #createBean} for traditionally defined beans,
	 * and from {@link #initializeBean} for existing bean instances.
	 * 注：该方法会在传统定义的bean创建方法(createBean)中调用，也会在已存在的bean实例中的initializeBean方法调用。
	 * @param beanName the bean name in the factory (for debugging purposes)  // 注：bean名称
	 * @param bean the new bean instance we may need to initialize		// 注：我们可能需要初始化的bean实例
	 * @param mbd the bean definition that the bean was created with	// 注：用于创建bean实例的bean定义，对于已存在的bean定义，该参数可能为null
	 * (can also be {@code null}, if given an existing bean instance)
	 * @return the initialized bean instance (potentially wrapped)
	 * @see BeanNameAware
	 * @see BeanClassLoaderAware
	 * @see BeanFactoryAware
	 * @see #applyBeanPostProcessorsBeforeInitialization
	 * @see #invokeInitMethods
	 * @see #applyBeanPostProcessorsAfterInitialization
	 */
	protected Object initializeBean(String beanName, Object bean, @Nullable RootBeanDefinition mbd) {
		if (System.getSecurityManager() != null) {
			AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
				invokeAwareMethods(beanName, bean);
				return null;
			}, getAccessControlContext());
		}
		else {
			/**
			 * 注：执行各种bean名称和Bean实例相关Aware方法
			 * BeanNameAware、BeanClassLoaderAware、BeanFactoryAware。
			 */
			invokeAwareMethods(beanName, bean);
		}

		Object wrappedBean = bean;
		if (mbd == null || !mbd.isSynthetic()) {
			/**
			 * 注：如果当前bean非动态生成bean，就调用所有bean后置处理器的初始化前置回调方法
			 * 比如CommonAnnotationBeanPostProcessor后置处理器会在这里根据bean类型调用其生命周期方法，比如@PostConstruct注解的方法
			 */
			wrappedBean = applyBeanPostProcessorsBeforeInitialization(wrappedBean, beanName);
		}

		try {
			// 注：调用InitializingBean接口中的afterPropertiesSet方法，以及自定义初始化init方法。
			invokeInitMethods(beanName, wrappedBean, mbd);
		}
		catch (Throwable ex) {
			throw new BeanCreationException(
					(mbd != null ? mbd.getResourceDescription() : null),
					beanName, "Invocation of init method failed", ex);
		}
		if (mbd == null || !mbd.isSynthetic()) {
			// 注：如果当前bean非动态生成bean，就调用所有bean后置处理器的初始化后置回调方法
			wrappedBean = applyBeanPostProcessorsAfterInitialization(wrappedBean, beanName);
		}

		return wrappedBean;
	}

	/**
	 * 注：执行各种Aware方法
	 * 涉及bean名称或者bean实例的Aware实例会在这里执行，包括BeanNameAware、BeanClassLoaderAware、BeanFactoryAware。
	 * 其他的Aware因为咱没有其他信息，后续会在后置处理器中执行。
	 */
	private void invokeAwareMethods(String beanName, Object bean) {
		if (bean instanceof Aware) {  		// 注：如果当前bean实现了Aware接口
			if (bean instanceof BeanNameAware) {
				// 注：对于BeanNameAware实例，这里会执行对应的set方法
				((BeanNameAware) bean).setBeanName(beanName);
			}
			if (bean instanceof BeanClassLoaderAware) {
				// 注：对于BeanClassLoaderAware实例，这里会执行对应的set方法，设置当前bean的类加载器
				ClassLoader bcl = getBeanClassLoader();
				if (bcl != null) {
					((BeanClassLoaderAware) bean).setBeanClassLoader(bcl);
				}
			}
			if (bean instanceof BeanFactoryAware) {
				// 注：对于BeanFactoryAware实例，这里会执行对应的set方法，设置当前bean的工厂实例
				((BeanFactoryAware) bean).setBeanFactory(AbstractAutowireCapableBeanFactory.this);
			}
		}
	}

	/**
	 * Give a bean a chance to react now all its properties are set,
	 * and a chance to know about its owning bean factory (this object).
	 * This means checking whether the bean implements InitializingBean or defines
	 * a custom init method, and invoking the necessary callback(s) if it does.
	 * 注：该方法会调用bean实例实现的InitializingBean接口中的afterPropertiesSet方法，以及自定义初始化方法。
	 * @param beanName the bean name in the factory (for debugging purposes)
	 * @param bean the new bean instance we may need to initialize
	 * @param mbd the merged bean definition that the bean was created with
	 * (can also be {@code null}, if given an existing bean instance)
	 * @throws Throwable if thrown by init methods or by the invocation process
	 * @see #invokeCustomInitMethod
	 */
	protected void invokeInitMethods(String beanName, Object bean, @Nullable RootBeanDefinition mbd)
			throws Throwable {

		// 注：是否bean实例实现了InitializingBean接口的afterPropertiesSet方法。
		boolean isInitializingBean = (bean instanceof InitializingBean);
		if (isInitializingBean && (mbd == null || !mbd.isExternallyManagedInitMethod("afterPropertiesSet"))) {
			/**
			 * 注：存在bean定义，就检查afterPropertiesSet非外部注入的初始化方法
			 * 则，执行当前bean的afterPropertiesSet方法。
			 */
			if (logger.isTraceEnabled()) {
				logger.trace("Invoking afterPropertiesSet() on bean with name '" + beanName + "'");
			}
			if (System.getSecurityManager() != null) {
				try {
					AccessController.doPrivileged((PrivilegedExceptionAction<Object>) () -> {
						((InitializingBean) bean).afterPropertiesSet();
						return null;
					}, getAccessControlContext());
				}
				catch (PrivilegedActionException pae) {
					throw pae.getException();
				}
			}
			else {
				((InitializingBean) bean).afterPropertiesSet();
			}
		}

		if (mbd != null && bean.getClass() != NullBean.class) {
			/**
			 * 注：mbd != null意味着bean创建后的实例化流程，bean实例有可能是NullBean实例。
			 * 如果bean指定了init方法，这里会执行初始化方法
			 */
			String initMethodName = mbd.getInitMethodName();
			if (StringUtils.hasLength(initMethodName) &&
					!(isInitializingBean && "afterPropertiesSet".equals(initMethodName)) &&
					!mbd.isExternallyManagedInitMethod(initMethodName)) {
				invokeCustomInitMethod(beanName, bean, mbd);
			}
		}
	}

	/**
	 * Invoke the specified custom init method on the given bean.
	 * Called by invokeInitMethods.
	 * <p>Can be overridden in subclasses for custom resolution of init
	 * methods with arguments.
	 * @see #invokeInitMethods
	 */
	protected void invokeCustomInitMethod(String beanName, Object bean, RootBeanDefinition mbd)
			throws Throwable {

		String initMethodName = mbd.getInitMethodName();
		Assert.state(initMethodName != null, "No init method set");
		Method initMethod = (mbd.isNonPublicAccessAllowed() ?
				BeanUtils.findMethod(bean.getClass(), initMethodName) :		// 注：如果bean允许访问非公共方法
				ClassUtils.getMethodIfAvailable(bean.getClass(), initMethodName));

		if (initMethod == null) {
			if (mbd.isEnforceInitMethod()) {
				// 注：必须执行自定义初始化方法，但是没有找到初始化方法，抛出异常
				throw new BeanDefinitionValidationException("Could not find an init method named '" +
						initMethodName + "' on bean with name '" + beanName + "'");
			}
			else {
				if (logger.isTraceEnabled()) {
					logger.trace("No default init method named '" + initMethodName +
							"' found on bean with name '" + beanName + "'");
				}
				// Ignore non-existent default lifecycle methods.
				// 注：忽略不存在的初始化方法
				return;
			}
		}

		if (logger.isTraceEnabled()) {
			logger.trace("Invoking init method  '" + initMethodName + "' on bean with name '" + beanName + "'");
		}
		Method methodToInvoke = ClassUtils.getInterfaceMethodIfPossible(initMethod);	// 注：获取初始化方法句柄

		if (System.getSecurityManager() != null) {
			AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
				ReflectionUtils.makeAccessible(methodToInvoke);
				return null;
			});
			try {
				AccessController.doPrivileged((PrivilegedExceptionAction<Object>)
						() -> methodToInvoke.invoke(bean), getAccessControlContext());
			}
			catch (PrivilegedActionException pae) {
				InvocationTargetException ex = (InvocationTargetException) pae.getException();
				throw ex.getTargetException();
			}
		}
		else {
			try {
				// 注：设置方法为可访问
				ReflectionUtils.makeAccessible(methodToInvoke);
				// 注：调用当前方法(从这里看，自定义初始化方法是为空参方法)
				methodToInvoke.invoke(bean);
			}
			catch (InvocationTargetException ex) {
				throw ex.getTargetException();
			}
		}
	}


	/**
	 * Applies the {@code postProcessAfterInitialization} callback of all
	 * registered BeanPostProcessors, giving them a chance to post-process the
	 * object obtained from FactoryBeans (for example, to auto-proxy them).
	 * @see #applyBeanPostProcessorsAfterInitialization
	 */
	@Override
	protected Object postProcessObjectFromFactoryBean(Object object, String beanName) {
		return applyBeanPostProcessorsAfterInitialization(object, beanName);
	}

	/**
	 * Overridden to clear FactoryBean instance cache as well.
	 */
	@Override
	protected void removeSingleton(String beanName) {
		synchronized (getSingletonMutex()) {
			super.removeSingleton(beanName);
			this.factoryBeanInstanceCache.remove(beanName);
		}
	}

	/**
	 * Overridden to clear FactoryBean instance cache as well.
	 */
	@Override
	protected void clearSingletonCache() {
		synchronized (getSingletonMutex()) {
			super.clearSingletonCache();
			this.factoryBeanInstanceCache.clear();
		}
	}

	/**
	 * Expose the logger to collaborating delegates.
	 * @since 5.0.7
	 */
	Log getLogger() {
		return logger;
	}


	/**
	 * Special DependencyDescriptor variant for Spring's good old autowire="byType" mode.
	 * Always optional; never considering the parameter name for choosing a primary candidate.
	 */
	@SuppressWarnings("serial")
	private static class AutowireByTypeDependencyDescriptor extends DependencyDescriptor {

		public AutowireByTypeDependencyDescriptor(MethodParameter methodParameter, boolean eager) {
			super(methodParameter, false, eager);
		}

		@Override
		public String getDependencyName() {
			return null;
		}
	}


	/**
	 * {@link MethodCallback} used to find {@link FactoryBean} type information.
	 */
	private static class FactoryBeanMethodTypeFinder implements MethodCallback {

		private final String factoryMethodName;

		private ResolvableType result = ResolvableType.NONE;

		FactoryBeanMethodTypeFinder(String factoryMethodName) {
			this.factoryMethodName = factoryMethodName;
		}

		@Override
		public void doWith(Method method) throws IllegalArgumentException, IllegalAccessException {
			if (isFactoryBeanMethod(method)) {
				ResolvableType returnType = ResolvableType.forMethodReturnType(method);
				ResolvableType candidate = returnType.as(FactoryBean.class).getGeneric();
				if (this.result == ResolvableType.NONE) {
					this.result = candidate;
				}
				else {
					Class<?> resolvedResult = this.result.resolve();
					Class<?> commonAncestor = ClassUtils.determineCommonAncestor(candidate.resolve(), resolvedResult);
					if (!ObjectUtils.nullSafeEquals(resolvedResult, commonAncestor)) {
						this.result = ResolvableType.forClass(commonAncestor);
					}
				}
			}
		}

		private boolean isFactoryBeanMethod(Method method) {
			return (method.getName().equals(this.factoryMethodName) &&
					FactoryBean.class.isAssignableFrom(method.getReturnType()));
		}

		ResolvableType getResult() {
			Class<?> resolved = this.result.resolve();
			boolean foundResult = resolved != null && resolved != Object.class;
			return (foundResult ? this.result : ResolvableType.NONE);
		}
	}

}
