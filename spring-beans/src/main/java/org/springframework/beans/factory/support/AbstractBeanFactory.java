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

import java.beans.PropertyEditor;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.beans.PropertyEditorRegistrySupport;
import org.springframework.beans.SimpleTypeConverter;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanIsAbstractException;
import org.springframework.beans.factory.BeanIsNotAFactoryException;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.CannotLoadBeanClassException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.config.Scope;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.core.AttributeAccessor;
import org.springframework.core.DecoratingClassLoader;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.log.LogMessage;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

/**
 * Abstract base class for {@link org.springframework.beans.factory.BeanFactory}
 * implementations, providing the full capabilities of the
 * {@link org.springframework.beans.factory.config.ConfigurableBeanFactory} SPI.
 * Does <i>not</i> assume a listable bean factory: can therefore also be used
 * as base class for bean factory implementations which obtain bean definitions
 * from some backend resource (where bean definition access is an expensive operation).
 * 注：AbstractBeanFactory是bean工厂基本抽象类实现，提供了ConfigurableBeanFactory接口全部能力。
 *
 * <p>This class provides a singleton cache (through its base class
 * {@link org.springframework.beans.factory.support.DefaultSingletonBeanRegistry},
 * singleton/prototype determination, {@link org.springframework.beans.factory.FactoryBean}
 * handling, aliases, bean definition merging for child bean definitions,
 * and bean destruction ({@link org.springframework.beans.factory.DisposableBean}
 * interface, custom destroy methods). Furthermore, it can manage a bean factory
 * hierarchy (delegating to the parent in case of an unknown bean), through implementing
 * the {@link org.springframework.beans.factory.HierarchicalBeanFactory} interface.
 *
 * <p>The main template methods to be implemented by subclasses are
 * {@link #getBeanDefinition} and {@link #createBean}, retrieving a bean definition
 * for a given bean name and creating a bean instance for a given bean definition,
 * respectively. Default implementations of those operations can be found in
 * {@link DefaultListableBeanFactory} and {@link AbstractAutowireCapableBeanFactory}.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Costin Leau
 * @author Chris Beams
 * @author Phillip Webb
 * @since 15 April 2001
 * @see #getBeanDefinition
 * @see #createBean
 * @see AbstractAutowireCapableBeanFactory#createBean
 * @see DefaultListableBeanFactory#getBeanDefinition
 */
public abstract class AbstractBeanFactory extends FactoryBeanRegistrySupport implements ConfigurableBeanFactory {

	/** Parent bean factory, for bean inheritance support. */
	@Nullable
	private BeanFactory parentBeanFactory;

	/** ClassLoader to resolve bean class names with, if necessary. */
	@Nullable
	private ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();

	/** ClassLoader to temporarily resolve bean class names with, if necessary. */
	@Nullable
	private ClassLoader tempClassLoader;

	/** Whether to cache bean metadata or rather reobtain it for every access.
	 * 注：是否缓存非内部bean的已合并bean定义元数据，否则会在每次访问时重新获取元数据【重新合并bean定义】
	 * - 如果设置为false，就不会将合并bean定义合并到mergedBeanDefinitions缓存中。
	 * */
	private boolean cacheBeanMetadata = true;

	/** Resolution strategy for expressions in bean definition values.
	 * 注：用于解析bean定义值的表达式解析器；比如解析bean类名
	 * */
	@Nullable
	private BeanExpressionResolver beanExpressionResolver;

	/** Spring ConversionService to use instead of PropertyEditors. */
	@Nullable
	private ConversionService conversionService;

	/** Custom PropertyEditorRegistrars to apply to the beans of this factory. */
	private final Set<PropertyEditorRegistrar> propertyEditorRegistrars = new LinkedHashSet<>(4);

	/** Custom PropertyEditors to apply to the beans of this factory. */
	private final Map<Class<?>, Class<? extends PropertyEditor>> customEditors = new HashMap<>(4);

	/** A custom TypeConverter to use, overriding the default PropertyEditor mechanism. */
	@Nullable
	private TypeConverter typeConverter;

	/** String resolvers to apply e.g. to annotation attribute values.
	 * 注：用于解析给定字符串值，比如解析占位符
	 * */
	private final List<StringValueResolver> embeddedValueResolvers = new CopyOnWriteArrayList<>();

	/** BeanPostProcessors to apply.
	 * 注：通过addBeanPostProcessor等方法手动以及应用上下文自动注册的Bean后置处理器存储在该缓存中
	 * */
	private final List<BeanPostProcessor> beanPostProcessors = new BeanPostProcessorCacheAwareList();

	/** Cache of pre-filtered post-processors. */
	@Nullable
	private volatile BeanPostProcessorCache beanPostProcessorCache;

	/** Map from scope identifier String to corresponding Scope.
	 * 注：缓存注册到工厂发内的作用域(Scope)处理类实例。
	 * */
	private final Map<String, Scope> scopes = new LinkedHashMap<>(8);

	/** Security context used when running with a SecurityManager. */
	@Nullable
	private SecurityContextProvider securityContextProvider;

	/** Map from bean name to merged RootBeanDefinition.
	 * 注：已合并后的bean定义缓存；
	 * 仅缓存的是不具有内部bean的合并bean定义；并且cacheBeanMetadata为true
	 * */
	private final Map<String, RootBeanDefinition> mergedBeanDefinitions = new ConcurrentHashMap<>(256);

	/** Names of beans that have already been created at least once.
	 * 注：当前bean工厂内至少实例化了一次的bean名称
	 * */
	private final Set<String> alreadyCreated = Collections.newSetFromMap(new ConcurrentHashMap<>(256));

	/** Names of beans that are currently in creation.
	 * 注：用于缓存当前线程内正在创建(非单例)bean的名称
	 * - 如果当前只有一个正在创建的(非单例)bean，那该值为String类型
	 * - 如果当前有多个正在创建的(非单例)bean，那么该值为Set<String>类型
	 * */
	private final ThreadLocal<Object> prototypesCurrentlyInCreation =
			new NamedThreadLocal<>("Prototype beans currently in creation");


	/**
	 * Create a new AbstractBeanFactory.
	 */
	public AbstractBeanFactory() {
	}

	/**
	 * Create a new AbstractBeanFactory with the given parent.
	 * @param parentBeanFactory parent bean factory, or {@code null} if none
	 * @see #getBean
	 */
	public AbstractBeanFactory(@Nullable BeanFactory parentBeanFactory) {
		this.parentBeanFactory = parentBeanFactory;
	}


	//---------------------------------------------------------------------
	// Implementation of BeanFactory interface
	//---------------------------------------------------------------------

	/**
	 * 该方法没有任何处理逻辑，真正逻辑在doGetBean中，此处是重写BeanFactory接口的方法
	 */
	@Override
	public Object getBean(String name) throws BeansException {
		return doGetBean(name, null, null, false);
	}

	@Override
	public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
		return doGetBean(name, requiredType, null, false);
	}

	@Override
	public Object getBean(String name, Object... args) throws BeansException {
		return doGetBean(name, null, args, false);
	}

	/**
	 * Return an instance, which may be shared or independent, of the specified bean.
	 * @param name the name of the bean to retrieve
	 * @param requiredType the required type of the bean to retrieve
	 * @param args arguments to use when creating a bean instance using explicit arguments
	 * (only applied when creating a new instance as opposed to retrieving an existing one)
	 * @return an instance of the bean
	 * @throws BeansException if the bean could not be created
	 */
	public <T> T getBean(String name, @Nullable Class<T> requiredType, @Nullable Object... args)
			throws BeansException {

		return doGetBean(name, requiredType, args, false);
	}

	/**
	 * Return an instance, which may be shared or independent, of the specified bean.
	 * 注：返回指定bean的实例(可能是单例或者原型)
	 * @param name the name of the bean to retrieve // 注：要检索的bean名称
	 * @param requiredType the required type of the bean to retrieve  // 注：要返回的bean实例类型
	 * @param args arguments to use when creating a bean instance using explicit arguments
	 * (only applied when creating a new instance as opposed to retrieving an existing one)
	 * 注：当创建一个bean时通过该参数指定构造参数值。（仅仅当需要创建一个新实例而不是从缓存召回已经存在的bean时传入。意即正常创建bean是不会传入参数的）
	 * @param typeCheckOnly whether the instance is obtained for a type check,
	 * not for actual use // 是否获取的实例仅仅用于类型检查，而不实际使用(即不需要缓存，正常创建bean是不会传入参数的)
	 * @return an instance of the bean
	 * @throws BeansException if the bean could not be created
	 */
	@SuppressWarnings("unchecked")
	protected <T> T doGetBean(
			String name, @Nullable Class<T> requiredType, @Nullable Object[] args, boolean typeCheckOnly)
			throws BeansException {

		/**
		 * 注：此处传入的name可能是别名，也有可能是工厂bean的name，因此需要先进行转换
		 * - 如果是别名，则需要获取其对应的bean实例的ID名。
		 * - 如果是工厂bean的名称，会去掉其前缀"&"; 那是不是意味着该方法无法获取工厂bean实例呢？
		 *   实际上不是的，不论传入的name是否存在前缀"&"，从beanDefinitionMap缓存中获取的bean定义都是以规范bean名为key，以工厂bean定义为value
		 *   我们在配置bean定义的时候，也是声明的是工厂bean定义，id为规范bean名。实际获取目标bean方法为getObjectForBeanInstance：会比较name和beanName
		 */
		String beanName = transformedBeanName(name);
		Object bean;

		// Eagerly check singleton cache for manually registered singletons.
		// 注：尝试去缓存中获取单例bean对象。默认允许返回早期单例bean实例(即，尚未执行属性注入以及初始化方法)
		Object sharedInstance = getSingleton(beanName);
		if (sharedInstance != null && args == null) {
			// 注：这里是已经通过缓存获取到了单例bean实例；args为null表示不是要重新创建一个新的实例。
			if (logger.isTraceEnabled()) {
				if (isSingletonCurrentlyInCreation(beanName)) {
					logger.trace("Returning eagerly cached instance of singleton bean '" + beanName +
							"' that is not fully initialized yet - a consequence of a circular reference");
				}
				else {
					logger.trace("Returning cached instance of singleton bean '" + beanName + "'");
				}
			}
			// 注：从已有的单例bean实例中获取name所指向的目标bean对象【如果当前单例bean是工厂bean，内部会生成目标bean】
			bean = getObjectForBeanInstance(sharedInstance, name, beanName, null);
		}

		else {
			// 注：这里意味着不能从多级缓存中找到该beanName对应的bean实例，或者传递了参数args想要重新实例化一个bean实例。
			// Fail if we're already creating this bean instance:
			// We're assumably within a circular reference.
			/**
			 * 注：如果我们已经正在创建了这个bean实例，有可能处于循环引用内，就需要抛出异常。
			 * 【可解决的循环异常上面会从缓存中获取到早期引用对象，而像构造器循环依赖就无法获取到早期引用对象，这里就会抛出异常】
			 */
			if (isPrototypeCurrentlyInCreation(beanName)) {
				// 注：当前bean处于正在创建过程中，意即处于循环依赖中，并且无法解决，抛出异常。
				throw new BeanCurrentlyInCreationException(beanName);
			}

			// Check if bean definition exists in this factory.
			// 这里先判断当前bean是否存在于父工厂中。
			BeanFactory parentBeanFactory = getParentBeanFactory();
			if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
				// Not found -> check parent.
				/**
				 * 注：如果当前存在父bean工厂，并且当前bean工厂内不包括指定bean名称的bean定义，那这里就需要通过父bean工厂来创建返回。
				 * - 获取父工厂中的bean实例，自然还是调用父工厂的getBean或者doGetBean方法。
				 * - 对于方法参数name肯定不能是beanName了。如果是要获取工厂对象，需要在beanName之前添加"&"前缀即可。
				 */
				String nameToLookup = originalBeanName(name);
				/**
				 * 注：下面就是调用父bean工厂的getBean或者doGetBean方法来返回bean实例。
				 * 需要注意的是，本方法可能存在4个参数，因此需要根据父bean工厂类型以及参数值来决定调用那个getBean方法。
				 */
				if (parentBeanFactory instanceof AbstractBeanFactory) {
					// 注：如果父bean工厂是AbstractBeanFactory，那就调用doGetBean方法，传入所有参数。
					return ((AbstractBeanFactory) parentBeanFactory).doGetBean(
							nameToLookup, requiredType, args, typeCheckOnly);
				}
				// 注：BeanFactory接口根据bean名称来获取bean实例，就只有下面三个接口
				else if (args != null) {
					// Delegation to parent with explicit args.
					// 注：存在构造参数，则调用这个重载方法
					return (T) parentBeanFactory.getBean(nameToLookup, args);
				}
				else if (requiredType != null) {
					// No args -> delegate to standard getBean method.
					// 注：没有构造参数，则调用这个重载方法；
					// - 为什么args要比requiredType优先级要高？校验bean实例的类型不需要设置args。
					return parentBeanFactory.getBean(nameToLookup, requiredType);
				}
				else {
					// 注：仅有bean名称，则调用这个重载方法
					return (T) parentBeanFactory.getBean(nameToLookup);
				}
			}

			if (!typeCheckOnly) {
				/**
				 * 注：typeCheckOnly=true：创建bean仅仅用于类型检查，而不会实际使用
				 * - 如果此方法创建的bean实例仅用于类型检查，就不需要将beanName添加到已创建bean的缓存中，即alreadyCreated
				 * - 如果此方法创建bean实例是实际使用的，那么这里会将该beanName添加到已创建bean实例缓存中，并会清除可能已存在的合并bean定义，也意味着spring开始创建bean实例了。
				 * 【问题】如果在实际创建单例bean之前，先调用了该API进行类型检查就有问题了. （需了解alreadyCreated在哪几种场景会用到）
				 * 1. 如果检查的是单例bean，那么创建后会放置在单例bean缓存中，之后就永远不会走到这里，也即不会放置到该缓存中
				 * 2. 清空合并bean定义缓存时会根据即alreadyCreated缓存来判断是否缓存了合并bean定义，那么这种情况下就不会清除了
				 */
				markBeanAsCreated(beanName);
			}

			/**
			 * 注：下面就是具体的创建bean实例的流程了。
			 * - 如果创建bean实例异常失败，则会将该beanName从alreadyCreated中移除。
			 * - 这里创建bean实例的步骤大致可分为以下几个部分：
			 *   ① 获取并校验当前bean的合并bean定义
			 *   ② 根据bean定义。先进行依赖bean的实例化
			 *   ③ 根据bean定义中的作用域(单例、原型或其他scope)，进行不同的实际实例化过程。
			 */
			try {
				/**
				 * 注：先尝试获取指定beanName在当前bean工厂中的合并bean定义。
				 * - 为什么是合并bean定义呢？
				 * 	 可能当前bean定义存在父bean定义，需要合并后才能使用。合并后的bean定义类型为RootBeanDefinition。
				 * - 为什么是从当前bean工厂获取而不尝试从父工厂获取呢？
				 * 	 父工厂上面已经处理并判断了，这里已经确定是属于当前工厂内的bean。
				 */
				RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
				// 注：对合并后的bean定义进行检查-非抽象bean才能创建实例；(注册bean定义在注册到bean定义注册中心之前检查)
				checkMergedBeanDefinition(mbd, beanName, args);

				// Guarantee initialization of beans that the current bean depends on.
				/**
				 * 注：在当前bean创建之前，需保证“depends-on”属性指定的依赖bean提前初始化。
				 * 1. 检查是否存在循环业务逻辑依赖，存在则抛出异常
				 * 2. 将bean之前的依赖关系缓存起来。dependentBeanMap、dependenciesForBeanMap
				 * 3. 触发依赖bean的初始化(依赖bean可以是父工厂中的bean)
				 * 【重点说明】spring中的依赖区分为业务逻辑依赖、属性依赖。
				 * - 业务逻辑依赖是指在业务含以上，不同bean之前存在初始化前后顺序的关系。这种情况下循环依赖是无法解决的。“depends-on”就是这种依赖关系。
				 * - 属性依赖是指当前bean的属性赋值以及初始化依赖于另外一个bean实例。部分属性循环依赖可以通过多级缓存解决，而部分也无法解决。
				 * - 依赖关系的缓存会存储以上两种依赖方式的关系，便于后续使用。
				 */
				String[] dependsOn = mbd.getDependsOn();	// 注：获取当前合并bean定义的“depends-on”属性值
				if (dependsOn != null) {
					for (String dep : dependsOn) {
						// 注：判断dep是否依赖于beanName，即是否构成循环依赖；这种业务逻辑循环依赖无法解决。
						if (isDependent(beanName, dep)) {
							throw new BeanCreationException(mbd.getResourceDescription(), beanName,
									"Circular depends-on relationship between '" + beanName + "' and '" + dep + "'");
						}
						// 注：将beanNme依赖于dep注册到缓存中，便于后续发现循环依赖以及销毁流程使用
						registerDependentBean(dep, beanName);
						try {
							// 注：触发依赖bean的初始化
							getBean(dep);
						}
						catch (NoSuchBeanDefinitionException ex) {
							// 注：有可能找不到依赖bean的定义【依赖关系可以不在同一个工厂内】
							throw new BeanCreationException(mbd.getResourceDescription(), beanName,
									"'" + beanName + "' depends on missing bean '" + dep + "'", ex);
						}
					}
				}

				// Create bean instance.
				// 注：创建bean实例
				/**
				 * 注：下面将根据合并bean定义的作用域属性来进行bean实例的创建和初始化过程。
				 * - 无论是哪种作用域，对于初始化后的bean实例都会调用getObjectForBeanInstance方法处理工厂bean的情况，这个在之前已经见到过。
				 */
				if (mbd.isSingleton()) {
					/**
					 * 注：创建单例bean的过程。
					 * - 单例bean由DefaultSingletonBeanRegistry#getSingleton来进行。
					 * - 单例bean本身的创建和实例化过程由AbstractAutowireCapableBeanFactory#createBean来进行。
					 */
					sharedInstance = getSingleton(beanName, () -> {
						try {
 							return createBean(beanName, mbd, args);
						}
						catch (BeansException ex) {
							// Explicitly remove instance from singleton cache: It might have been put there
							// eagerly by the creation process, to allow for circular reference resolution.
							// Also remove any beans that received a temporary reference to the bean.
							/**
							 * 创建bean失败后需要显示移除单例bean的缓存。
							 * 为了解决循环引用问题，在创建的过程中可能已经提前存储了缓存数据，这部分数据需要在销毁过程中清除。
							 * 同时也会清楚bean工厂内任何引用该bean实例的bean实例相关临时引用缓存。
							 */
							destroySingleton(beanName);
							throw ex;
						}
					});
					// 注：从已有的单例bean实例中获取name所指向的目标bean对象【如果当前单例bean是工厂bean，内部会生成目标bean】
					bean = getObjectForBeanInstance(sharedInstance, name, beanName, mbd);
				}

				else if (mbd.isPrototype()) {
					// It's a prototype -> create a new instance.
					// 注：对于原型bean实例的创建，这里直接创建一个新的实例即可；主要有由AbstractAutowireCapableBeanFactory#createBean来进行。
					Object prototypeInstance = null;
					try {
						// 注：在创建原型实例之前的回调逻辑。默认实现将该bean名称缓存在线程上下文中
						beforePrototypeCreation(beanName);
						// 注：触发原型bean实例的创建；AbstractAutowireCapableBeanFactory#createBean
						prototypeInstance = createBean(beanName, mbd, args);
					}
					finally {
						// 注：在创建原型实例之后的回调逻辑。默认实现将该bean名称缓存从线程上下文中移除。
						afterPrototypeCreation(beanName);
					}
					// 注：从已有的单例bean实例中获取name所指向的目标bean对象【如果当前单例bean是工厂bean，内部会生成目标bean】
					bean = getObjectForBeanInstance(prototypeInstance, name, beanName, mbd);
				}

				else {
					/**
					 * 注：对于非单例、原型作用域之外，其余类型作用域需通过registerScope进行注册到工厂内。
					 * 1. 根据bean定义的作用域来获取对应注册的Scope实例进行处理
					 * 2. 调用Scope的get方法来获取实例；
					 * 	  和单例bean-DefaultSingletonBeanRegistry类似，这里Scope只负责创建实例之外的处理，实际bean的创建逻辑还是由AbstractAutowireCapableBeanFactory#createBean来进行。
					 */
					String scopeName = mbd.getScope();
					if (!StringUtils.hasLength(scopeName)) {
						throw new IllegalStateException("No scope name defined for bean ´" + beanName + "'");
					}
					// 注：从已注册(registerScope)的作用域实例中获取当前作用域处理对象
					Scope scope = this.scopes.get(scopeName);
					if (scope == null) {
						throw new IllegalStateException("No Scope registered for scope name '" + scopeName + "'");
					}
					try {
						Object scopedInstance = scope.get(beanName, () -> {
							// 注：非单例bean的创建前都需要添加到线程上下文缓存中。
							beforePrototypeCreation(beanName);
							try {
								// 注：回调触发bean实例的创建；AbstractAutowireCapableBeanFactory#createBean
								return createBean(beanName, mbd, args);
							}
							finally {
								// 注：创建之后从线程上下文缓存中移除。
								afterPrototypeCreation(beanName);
							}
						});
						// 注：从已有的单例bean实例中获取name所指向的目标bean对象【如果当前单例bean是工厂bean，内部会生成目标bean】
						bean = getObjectForBeanInstance(scopedInstance, name, beanName, mbd);
					}
					catch (IllegalStateException ex) {
						throw new ScopeNotActiveException(beanName, scopeName, ex);
					}
				}
			}
			catch (BeansException ex) {
				// 注：如果创建bean实例异常失败，则会将该beanName从alreadyCreated中移除。
				cleanupAfterBeanCreationFailure(beanName);
				throw ex;
			}
		}

		// Check if required type matches the type of the actual bean instance.
		// 注：如果该方法传入了需要返回bean实例的类型-requiredType，这里会检查获取到的bean实例是否为该类型或其子类型。
		if (requiredType != null && !requiredType.isInstance(bean)) {
			try {
				// 注：通过自定义的或者默认(SimpleTypeConverter)的类型转换器，将上述实例化的bean实例转换为指定的类型。
				T convertedBean = getTypeConverter().convertIfNecessary(bean, requiredType);
				if (convertedBean == null) {
					// 注：转换结果为null(可能自定义转换不合理)，则抛出异常
					throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
				}
				// 注：返回requiredType类型的bean实例
				return convertedBean;
			}
			catch (TypeMismatchException ex) {
				if (logger.isTraceEnabled()) {
					logger.trace("Failed to convert bean '" + name + "' to required type '" +
							ClassUtils.getQualifiedName(requiredType) + "'", ex);
				}
				// 注：由于类型不匹配导致的类型转换异常
				throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
			}
		}
		return (T) bean;
	}

	@Override
	public boolean containsBean(String name) {
		String beanName = transformedBeanName(name);
		if (containsSingleton(beanName) || containsBeanDefinition(beanName)) {
			return (!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(name));
		}
		// Not found -> check parent.
		BeanFactory parentBeanFactory = getParentBeanFactory();
		return (parentBeanFactory != null && parentBeanFactory.containsBean(originalBeanName(name)));
	}

	@Override
	public boolean isSingleton(String name) throws NoSuchBeanDefinitionException {
		String beanName = transformedBeanName(name);

		Object beanInstance = getSingleton(beanName, false);
		if (beanInstance != null) {
			if (beanInstance instanceof FactoryBean) {
				return (BeanFactoryUtils.isFactoryDereference(name) || ((FactoryBean<?>) beanInstance).isSingleton());
			}
			else {
				return !BeanFactoryUtils.isFactoryDereference(name);
			}
		}

		// No singleton instance found -> check bean definition.
		BeanFactory parentBeanFactory = getParentBeanFactory();
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			// No bean definition found in this factory -> delegate to parent.
			return parentBeanFactory.isSingleton(originalBeanName(name));
		}

		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);

		// In case of FactoryBean, return singleton status of created object if not a dereference.
		if (mbd.isSingleton()) {
			if (isFactoryBean(beanName, mbd)) {
				if (BeanFactoryUtils.isFactoryDereference(name)) {
					return true;
				}
				FactoryBean<?> factoryBean = (FactoryBean<?>) getBean(FACTORY_BEAN_PREFIX + beanName);
				return factoryBean.isSingleton();
			}
			else {
				return !BeanFactoryUtils.isFactoryDereference(name);
			}
		}
		else {
			return false;
		}
	}

	@Override
	public boolean isPrototype(String name) throws NoSuchBeanDefinitionException {
		String beanName = transformedBeanName(name);

		BeanFactory parentBeanFactory = getParentBeanFactory();
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			// No bean definition found in this factory -> delegate to parent.
			return parentBeanFactory.isPrototype(originalBeanName(name));
		}

		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
		if (mbd.isPrototype()) {
			// In case of FactoryBean, return singleton status of created object if not a dereference.
			return (!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(beanName, mbd));
		}

		// Singleton or scoped - not a prototype.
		// However, FactoryBean may still produce a prototype object...
		if (BeanFactoryUtils.isFactoryDereference(name)) {
			return false;
		}
		if (isFactoryBean(beanName, mbd)) {
			FactoryBean<?> fb = (FactoryBean<?>) getBean(FACTORY_BEAN_PREFIX + beanName);
			if (System.getSecurityManager() != null) {
				return AccessController.doPrivileged(
						(PrivilegedAction<Boolean>) () ->
								((fb instanceof SmartFactoryBean && ((SmartFactoryBean<?>) fb).isPrototype()) ||
										!fb.isSingleton()),
						getAccessControlContext());
			}
			else {
				return ((fb instanceof SmartFactoryBean && ((SmartFactoryBean<?>) fb).isPrototype()) ||
						!fb.isSingleton());
			}
		}
		else {
			return false;
		}
	}

	@Override
	public boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException {
		return isTypeMatch(name, typeToMatch, true);
	}

	/**
	 * Internal extended variant of {@link #isTypeMatch(String, ResolvableType)}
	 * to check whether the bean with the given name matches the specified type. Allow
	 * additional constraints to be applied to ensure that beans are not created early.
	 * @param name the name of the bean to query
	 * @param typeToMatch the type to match against (as a
	 * {@code ResolvableType})
	 * @return {@code true} if the bean type matches, {@code false} if it
	 * doesn't match or cannot be determined yet
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @since 5.2
	 * @see #getBean
	 * @see #getType
	 */
	protected boolean isTypeMatch(String name, ResolvableType typeToMatch, boolean allowFactoryBeanInit)
			throws NoSuchBeanDefinitionException {

		String beanName = transformedBeanName(name);
		boolean isFactoryDereference = BeanFactoryUtils.isFactoryDereference(name);

		// Check manually registered singletons.
		Object beanInstance = getSingleton(beanName, false);
		if (beanInstance != null && beanInstance.getClass() != NullBean.class) {
			if (beanInstance instanceof FactoryBean) {
				if (!isFactoryDereference) {
					Class<?> type = getTypeForFactoryBean((FactoryBean<?>) beanInstance);
					return (type != null && typeToMatch.isAssignableFrom(type));
				}
				else {
					return typeToMatch.isInstance(beanInstance);
				}
			}
			else if (!isFactoryDereference) {
				if (typeToMatch.isInstance(beanInstance)) {
					// Direct match for exposed instance?
					return true;
				}
				else if (typeToMatch.hasGenerics() && containsBeanDefinition(beanName)) {
					// Generics potentially only match on the target class, not on the proxy...
					RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
					Class<?> targetType = mbd.getTargetType();
					if (targetType != null && targetType != ClassUtils.getUserClass(beanInstance)) {
						// Check raw class match as well, making sure it's exposed on the proxy.
						Class<?> classToMatch = typeToMatch.resolve();
						if (classToMatch != null && !classToMatch.isInstance(beanInstance)) {
							return false;
						}
						if (typeToMatch.isAssignableFrom(targetType)) {
							return true;
						}
					}
					ResolvableType resolvableType = mbd.targetType;
					if (resolvableType == null) {
						resolvableType = mbd.factoryMethodReturnType;
					}
					return (resolvableType != null && typeToMatch.isAssignableFrom(resolvableType));
				}
			}
			return false;
		}
		else if (containsSingleton(beanName) && !containsBeanDefinition(beanName)) {
			// null instance registered
			return false;
		}

		// No singleton instance found -> check bean definition.
		BeanFactory parentBeanFactory = getParentBeanFactory();
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			// No bean definition found in this factory -> delegate to parent.
			return parentBeanFactory.isTypeMatch(originalBeanName(name), typeToMatch);
		}

		// Retrieve corresponding bean definition.
		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
		BeanDefinitionHolder dbd = mbd.getDecoratedDefinition();

		// Setup the types that we want to match against
		Class<?> classToMatch = typeToMatch.resolve();
		if (classToMatch == null) {
			classToMatch = FactoryBean.class;
		}
		Class<?>[] typesToMatch = (FactoryBean.class == classToMatch ?
				new Class<?>[] {classToMatch} : new Class<?>[] {FactoryBean.class, classToMatch});


		// Attempt to predict the bean type
		Class<?> predictedType = null;

		// We're looking for a regular reference but we're a factory bean that has
		// a decorated bean definition. The target bean should be the same type
		// as FactoryBean would ultimately return.
		if (!isFactoryDereference && dbd != null && isFactoryBean(beanName, mbd)) {
			// We should only attempt if the user explicitly set lazy-init to true
			// and we know the merged bean definition is for a factory bean.
			if (!mbd.isLazyInit() || allowFactoryBeanInit) {
				RootBeanDefinition tbd = getMergedBeanDefinition(dbd.getBeanName(), dbd.getBeanDefinition(), mbd);
				Class<?> targetType = predictBeanType(dbd.getBeanName(), tbd, typesToMatch);
				if (targetType != null && !FactoryBean.class.isAssignableFrom(targetType)) {
					predictedType = targetType;
				}
			}
		}

		// If we couldn't use the target type, try regular prediction.
		if (predictedType == null) {
			predictedType = predictBeanType(beanName, mbd, typesToMatch);
			if (predictedType == null) {
				return false;
			}
		}

		// Attempt to get the actual ResolvableType for the bean.
		ResolvableType beanType = null;

		// If it's a FactoryBean, we want to look at what it creates, not the factory class.
		if (FactoryBean.class.isAssignableFrom(predictedType)) {
			if (beanInstance == null && !isFactoryDereference) {
				beanType = getTypeForFactoryBean(beanName, mbd, allowFactoryBeanInit);
				predictedType = beanType.resolve();
				if (predictedType == null) {
					return false;
				}
			}
		}
		else if (isFactoryDereference) {
			// Special case: A SmartInstantiationAwareBeanPostProcessor returned a non-FactoryBean
			// type but we nevertheless are being asked to dereference a FactoryBean...
			// Let's check the original bean class and proceed with it if it is a FactoryBean.
			predictedType = predictBeanType(beanName, mbd, FactoryBean.class);
			if (predictedType == null || !FactoryBean.class.isAssignableFrom(predictedType)) {
				return false;
			}
		}

		// We don't have an exact type but if bean definition target type or the factory
		// method return type matches the predicted type then we can use that.
		if (beanType == null) {
			ResolvableType definedType = mbd.targetType;
			if (definedType == null) {
				definedType = mbd.factoryMethodReturnType;
			}
			if (definedType != null && definedType.resolve() == predictedType) {
				beanType = definedType;
			}
		}

		// If we have a bean type use it so that generics are considered
		if (beanType != null) {
			return typeToMatch.isAssignableFrom(beanType);
		}

		// If we don't have a bean type, fallback to the predicted type
		return typeToMatch.isAssignableFrom(predictedType);
	}

	@Override
	public boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException {
		return isTypeMatch(name, ResolvableType.forRawClass(typeToMatch));
	}

	@Override
	@Nullable
	public Class<?> getType(String name) throws NoSuchBeanDefinitionException {
		return getType(name, true);
	}

	@Override
	@Nullable
	public Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
		String beanName = transformedBeanName(name);

		// Check manually registered singletons.
		Object beanInstance = getSingleton(beanName, false);
		if (beanInstance != null && beanInstance.getClass() != NullBean.class) {
			if (beanInstance instanceof FactoryBean && !BeanFactoryUtils.isFactoryDereference(name)) {
				return getTypeForFactoryBean((FactoryBean<?>) beanInstance);
			}
			else {
				return beanInstance.getClass();
			}
		}

		// No singleton instance found -> check bean definition.
		BeanFactory parentBeanFactory = getParentBeanFactory();
		if (parentBeanFactory != null && !containsBeanDefinition(beanName)) {
			// No bean definition found in this factory -> delegate to parent.
			return parentBeanFactory.getType(originalBeanName(name));
		}

		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);

		// Check decorated bean definition, if any: We assume it'll be easier
		// to determine the decorated bean's type than the proxy's type.
		BeanDefinitionHolder dbd = mbd.getDecoratedDefinition();
		if (dbd != null && !BeanFactoryUtils.isFactoryDereference(name)) {
			RootBeanDefinition tbd = getMergedBeanDefinition(dbd.getBeanName(), dbd.getBeanDefinition(), mbd);
			Class<?> targetClass = predictBeanType(dbd.getBeanName(), tbd);
			if (targetClass != null && !FactoryBean.class.isAssignableFrom(targetClass)) {
				return targetClass;
			}
		}

		Class<?> beanClass = predictBeanType(beanName, mbd);

		// Check bean class whether we're dealing with a FactoryBean.
		if (beanClass != null && FactoryBean.class.isAssignableFrom(beanClass)) {
			if (!BeanFactoryUtils.isFactoryDereference(name)) {
				// If it's a FactoryBean, we want to look at what it creates, not at the factory class.
				return getTypeForFactoryBean(beanName, mbd, allowFactoryBeanInit).resolve();
			}
			else {
				return beanClass;
			}
		}
		else {
			return (!BeanFactoryUtils.isFactoryDereference(name) ? beanClass : null);
		}
	}

	@Override
	public String[] getAliases(String name) {
		String beanName = transformedBeanName(name);
		List<String> aliases = new ArrayList<>();
		boolean factoryPrefix = name.startsWith(FACTORY_BEAN_PREFIX);
		String fullBeanName = beanName;
		if (factoryPrefix) {
			fullBeanName = FACTORY_BEAN_PREFIX + beanName;
		}
		if (!fullBeanName.equals(name)) {
			aliases.add(fullBeanName);
		}
		String[] retrievedAliases = super.getAliases(beanName);
		String prefix = factoryPrefix ? FACTORY_BEAN_PREFIX : "";
		for (String retrievedAlias : retrievedAliases) {
			String alias = prefix + retrievedAlias;
			if (!alias.equals(name)) {
				aliases.add(alias);
			}
		}
		if (!containsSingleton(beanName) && !containsBeanDefinition(beanName)) {
			BeanFactory parentBeanFactory = getParentBeanFactory();
			if (parentBeanFactory != null) {
				aliases.addAll(Arrays.asList(parentBeanFactory.getAliases(fullBeanName)));
			}
		}
		return StringUtils.toStringArray(aliases);
	}


	//---------------------------------------------------------------------
	// Implementation of HierarchicalBeanFactory interface
	//---------------------------------------------------------------------

	@Override
	@Nullable
	public BeanFactory getParentBeanFactory() {
		return this.parentBeanFactory;
	}

	@Override
	public boolean containsLocalBean(String name) {
		String beanName = transformedBeanName(name);
		return ((containsSingleton(beanName) || containsBeanDefinition(beanName)) &&
				(!BeanFactoryUtils.isFactoryDereference(name) || isFactoryBean(beanName)));
	}


	//---------------------------------------------------------------------
	// Implementation of ConfigurableBeanFactory interface
	//---------------------------------------------------------------------

	@Override
	public void setParentBeanFactory(@Nullable BeanFactory parentBeanFactory) {
		if (this.parentBeanFactory != null && this.parentBeanFactory != parentBeanFactory) {
			throw new IllegalStateException("Already associated with parent BeanFactory: " + this.parentBeanFactory);
		}
		if (this == parentBeanFactory) {
			throw new IllegalStateException("Cannot set parent bean factory to self");
		}
		this.parentBeanFactory = parentBeanFactory;
	}

	@Override
	public void setBeanClassLoader(@Nullable ClassLoader beanClassLoader) {
		this.beanClassLoader = (beanClassLoader != null ? beanClassLoader : ClassUtils.getDefaultClassLoader());
	}

	@Override
	@Nullable
	public ClassLoader getBeanClassLoader() {
		return this.beanClassLoader;
	}

	@Override
	public void setTempClassLoader(@Nullable ClassLoader tempClassLoader) {
		this.tempClassLoader = tempClassLoader;
	}

	@Override
	@Nullable
	public ClassLoader getTempClassLoader() {
		return this.tempClassLoader;
	}

	@Override
	public void setCacheBeanMetadata(boolean cacheBeanMetadata) {
		this.cacheBeanMetadata = cacheBeanMetadata;
	}

	@Override
	public boolean isCacheBeanMetadata() {
		return this.cacheBeanMetadata;
	}

	@Override
	public void setBeanExpressionResolver(@Nullable BeanExpressionResolver resolver) {
		this.beanExpressionResolver = resolver;
	}

	@Override
	@Nullable
	public BeanExpressionResolver getBeanExpressionResolver() {
		return this.beanExpressionResolver;
	}

	@Override
	public void setConversionService(@Nullable ConversionService conversionService) {
		this.conversionService = conversionService;
	}

	@Override
	@Nullable
	public ConversionService getConversionService() {
		return this.conversionService;
	}

	@Override
	public void addPropertyEditorRegistrar(PropertyEditorRegistrar registrar) {
		Assert.notNull(registrar, "PropertyEditorRegistrar must not be null");
		this.propertyEditorRegistrars.add(registrar);
	}

	/**
	 * Return the set of PropertyEditorRegistrars.
	 */
	public Set<PropertyEditorRegistrar> getPropertyEditorRegistrars() {
		return this.propertyEditorRegistrars;
	}

	@Override
	public void registerCustomEditor(Class<?> requiredType, Class<? extends PropertyEditor> propertyEditorClass) {
		Assert.notNull(requiredType, "Required type must not be null");
		Assert.notNull(propertyEditorClass, "PropertyEditor class must not be null");
		this.customEditors.put(requiredType, propertyEditorClass);
	}

	@Override
	public void copyRegisteredEditorsTo(PropertyEditorRegistry registry) {
		registerCustomEditors(registry);
	}

	/**
	 * Return the map of custom editors, with Classes as keys and PropertyEditor classes as values.
	 */
	public Map<Class<?>, Class<? extends PropertyEditor>> getCustomEditors() {
		return this.customEditors;
	}

	@Override
	public void setTypeConverter(TypeConverter typeConverter) {
		this.typeConverter = typeConverter;
	}

	/**
	 * Return the custom TypeConverter to use, if any.
	 * @return the custom TypeConverter, or {@code null} if none specified
	 */
	@Nullable
	protected TypeConverter getCustomTypeConverter() {
		return this.typeConverter;
	}

	@Override
	public TypeConverter getTypeConverter() {
		TypeConverter customConverter = getCustomTypeConverter();
		if (customConverter != null) {
			return customConverter;
		}
		else {
			// Build default TypeConverter, registering custom editors.
			SimpleTypeConverter typeConverter = new SimpleTypeConverter();
			typeConverter.setConversionService(getConversionService());
			registerCustomEditors(typeConverter);
			return typeConverter;
		}
	}

	@Override
	public void addEmbeddedValueResolver(StringValueResolver valueResolver) {
		Assert.notNull(valueResolver, "StringValueResolver must not be null");
		this.embeddedValueResolvers.add(valueResolver);
	}

	@Override
	public boolean hasEmbeddedValueResolver() {
		return !this.embeddedValueResolvers.isEmpty();
	}

	// 注：解析给定的嵌套值
	@Override
	@Nullable
	public String resolveEmbeddedValue(@Nullable String value) {
		if (value == null) {
			return null;
		}
		String result = value;
		for (StringValueResolver resolver : this.embeddedValueResolvers) {
			// 注：遍历字符串解析器
			result = resolver.resolveStringValue(result);
			if (result == null) {
				// 注：解析结果可能会返回null(一般是value本身就是null)，直接返回。
				return null;
			}
		}
		return result;
	}

	@Override
	public void addBeanPostProcessor(BeanPostProcessor beanPostProcessor) {
		Assert.notNull(beanPostProcessor, "BeanPostProcessor must not be null");
		// Remove from old position, if any
		this.beanPostProcessors.remove(beanPostProcessor);
		// Add to end of list
		this.beanPostProcessors.add(beanPostProcessor);
	}

	/**
	 * Add new BeanPostProcessors that will get applied to beans created
	 * by this factory. To be invoked during factory configuration.
	 * @since 5.3
	 * @see #addBeanPostProcessor
	 */
	public void addBeanPostProcessors(Collection<? extends BeanPostProcessor> beanPostProcessors) {
		this.beanPostProcessors.removeAll(beanPostProcessors);
		this.beanPostProcessors.addAll(beanPostProcessors);
	}

	@Override
	public int getBeanPostProcessorCount() {
		return this.beanPostProcessors.size();
	}

	/**
	 * Return the list of BeanPostProcessors that will get applied
	 * to beans created with this factory.
	 */
	public List<BeanPostProcessor> getBeanPostProcessors() {
		return this.beanPostProcessors;
	}

	/**
	 * Return the internal cache of pre-filtered post-processors,
	 * freshly (re-)building it if necessary.
	 * 注：返回预过滤后处理器的内部缓存，必要时可重新构建
	 * @since 5.3
	 */
	BeanPostProcessorCache getBeanPostProcessorCache() {
		BeanPostProcessorCache bpCache = this.beanPostProcessorCache;
		if (bpCache == null) {
			bpCache = new BeanPostProcessorCache();
			// 注：所有的bean后置处理器都在this.beanPostProcessors属性中，下面过滤四种特有功能的后置处理器缓存起来
			for (BeanPostProcessor bp : this.beanPostProcessors) {
				if (bp instanceof InstantiationAwareBeanPostProcessor) {
					bpCache.instantiationAware.add((InstantiationAwareBeanPostProcessor) bp);
					if (bp instanceof SmartInstantiationAwareBeanPostProcessor) {
						bpCache.smartInstantiationAware.add((SmartInstantiationAwareBeanPostProcessor) bp);
					}
				}
				if (bp instanceof DestructionAwareBeanPostProcessor) {
					bpCache.destructionAware.add((DestructionAwareBeanPostProcessor) bp);
				}
				if (bp instanceof MergedBeanDefinitionPostProcessor) {
					bpCache.mergedDefinition.add((MergedBeanDefinitionPostProcessor) bp);
				}
			}
			this.beanPostProcessorCache = bpCache;
		}
		return bpCache;
	}

	/**
	 * Return whether this factory holds a InstantiationAwareBeanPostProcessor
	 * that will get applied to singleton beans on creation.
	 * @see #addBeanPostProcessor
	 * @see org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor
	 */
	protected boolean hasInstantiationAwareBeanPostProcessors() {
		return !getBeanPostProcessorCache().instantiationAware.isEmpty();
	}

	/**
	 * Return whether this factory holds a DestructionAwareBeanPostProcessor
	 * that will get applied to singleton beans on shutdown.
	 * @see #addBeanPostProcessor
	 * @see org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor
	 */
	protected boolean hasDestructionAwareBeanPostProcessors() {
		return !getBeanPostProcessorCache().destructionAware.isEmpty();
	}

	@Override
	public void registerScope(String scopeName, Scope scope) {
		Assert.notNull(scopeName, "Scope identifier must not be null");
		Assert.notNull(scope, "Scope must not be null");
		if (SCOPE_SINGLETON.equals(scopeName) || SCOPE_PROTOTYPE.equals(scopeName)) {
			throw new IllegalArgumentException("Cannot replace existing scopes 'singleton' and 'prototype'");
		}
		Scope previous = this.scopes.put(scopeName, scope);
		if (previous != null && previous != scope) {
			if (logger.isDebugEnabled()) {
				logger.debug("Replacing scope '" + scopeName + "' from [" + previous + "] to [" + scope + "]");
			}
		}
		else {
			if (logger.isTraceEnabled()) {
				logger.trace("Registering scope '" + scopeName + "' with implementation [" + scope + "]");
			}
		}
	}

	@Override
	public String[] getRegisteredScopeNames() {
		return StringUtils.toStringArray(this.scopes.keySet());
	}

	@Override
	@Nullable
	public Scope getRegisteredScope(String scopeName) {
		Assert.notNull(scopeName, "Scope identifier must not be null");
		return this.scopes.get(scopeName);
	}

	/**
	 * Set the security context provider for this bean factory. If a security manager
	 * is set, interaction with the user code will be executed using the privileged
	 * of the provided security context.
	 */
	public void setSecurityContextProvider(SecurityContextProvider securityProvider) {
		this.securityContextProvider = securityProvider;
	}

	/**
	 * Delegate the creation of the access control context to the
	 * {@link #setSecurityContextProvider SecurityContextProvider}.
	 */
	@Override
	public AccessControlContext getAccessControlContext() {
		return (this.securityContextProvider != null ?
				this.securityContextProvider.getAccessControlContext() :
				AccessController.getContext());
	}

	@Override
	public void copyConfigurationFrom(ConfigurableBeanFactory otherFactory) {
		Assert.notNull(otherFactory, "BeanFactory must not be null");
		setBeanClassLoader(otherFactory.getBeanClassLoader());
		setCacheBeanMetadata(otherFactory.isCacheBeanMetadata());
		setBeanExpressionResolver(otherFactory.getBeanExpressionResolver());
		setConversionService(otherFactory.getConversionService());
		if (otherFactory instanceof AbstractBeanFactory) {
			AbstractBeanFactory otherAbstractFactory = (AbstractBeanFactory) otherFactory;
			this.propertyEditorRegistrars.addAll(otherAbstractFactory.propertyEditorRegistrars);
			this.customEditors.putAll(otherAbstractFactory.customEditors);
			this.typeConverter = otherAbstractFactory.typeConverter;
			this.beanPostProcessors.addAll(otherAbstractFactory.beanPostProcessors);
			this.scopes.putAll(otherAbstractFactory.scopes);
			this.securityContextProvider = otherAbstractFactory.securityContextProvider;
		}
		else {
			setTypeConverter(otherFactory.getTypeConverter());
			String[] otherScopeNames = otherFactory.getRegisteredScopeNames();
			for (String scopeName : otherScopeNames) {
				this.scopes.put(scopeName, otherFactory.getRegisteredScope(scopeName));
			}
		}
	}

	/**
	 * Return a 'merged' BeanDefinition for the given bean name,
	 * merging a child bean definition with its parent if necessary.
	 * <p>This {@code getMergedBeanDefinition} considers bean definition
	 * in ancestors as well.
	 * 注：根据指定bean名称返回合并后的bean定义，并且这是一个公开方法。
	 * 如果当前上下文不包括指定的名称bean，就会尝试去父上下文中寻找
	 * @param name the name of the bean to retrieve the merged definition for
	 * (may be an alias)
	 * @return a (potentially merged) RootBeanDefinition for the given bean
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @throws BeanDefinitionStoreException in case of an invalid bean definition
	 */
	@Override
	public BeanDefinition getMergedBeanDefinition(String name) throws BeansException {
		String beanName = transformedBeanName(name); // 这里会做去除前缀、别名的处理
		// Efficiently check whether bean definition exists in this factory.
		if (!containsBeanDefinition(beanName) && getParentBeanFactory() instanceof ConfigurableBeanFactory) {
			return ((ConfigurableBeanFactory) getParentBeanFactory()).getMergedBeanDefinition(beanName);
		}
		// Resolve merged bean definition locally.
		return getMergedLocalBeanDefinition(beanName);
	}

	// 注：检查指定的bean名对应的bean实例是否为FactoryBean
	@Override
	public boolean isFactoryBean(String name) throws NoSuchBeanDefinitionException {
		String beanName = transformedBeanName(name);	// 注：转换bean名称(去除&前缀、处理别名)
		/**
		 * 注：尝试获取单例bean实例，通过bean示例来判断是否为FactoryBean。【一般这里是获取不到实例的】
		 * 	这里入参不允许早期引用，这就意味着getSingleton实际上就是通过一级缓存查找该bean是否已实例化
		 */
		Object beanInstance = getSingleton(beanName, false);
		if (beanInstance != null) {
			return (beanInstance instanceof FactoryBean);	// 注：直接通过实例判断factoryBean类型
		}
		// No singleton instance found -> check bean definition.
		// 注：没法通过bean单例来判断的话，就只能检查bean定义了。
		if (!containsBeanDefinition(beanName) && getParentBeanFactory() instanceof ConfigurableBeanFactory) {
			// No bean definition found in this factory -> delegate to parent.
			// 如果当前bean定义不在当前工厂内，那就通过父工厂来处理
			return ((ConfigurableBeanFactory) getParentBeanFactory()).isFactoryBean(name);
		}

		// 注：下面完全就是根据beanName以及合并后的bean定义来判断是否为FactoryBean类型。
		return isFactoryBean(beanName, getMergedLocalBeanDefinition(beanName));
	}

	@Override
	public boolean isActuallyInCreation(String beanName) {
		return (isSingletonCurrentlyInCreation(beanName) || isPrototypeCurrentlyInCreation(beanName));
	}

	/**
	 * Return whether the specified prototype bean is currently in creation
	 * (within the current thread).
	 * 注：判断当前指定的原型bean名的实例是否正在创建中(当前线程)
	 * @param beanName the name of the bean
	 */
	protected boolean isPrototypeCurrentlyInCreation(String beanName) {
		Object curVal = this.prototypesCurrentlyInCreation.get();
		return (curVal != null &&
				(curVal.equals(beanName) || (curVal instanceof Set && ((Set<?>) curVal).contains(beanName))));
	}

	/**
	 * Callback before prototype creation.
	 * <p>The default implementation register the prototype as currently in creation.
	 * 注：在(非单例)bean创建之前回调该函数。
	 * - 默认的实现是将当前要创建的(非单例)bean名称缓存在线程上下文中
	 * @param beanName the name of the prototype about to be created
	 * @see #isPrototypeCurrentlyInCreation
	 */
	@SuppressWarnings("unchecked")
	protected void beforePrototypeCreation(String beanName) {
		Object curVal = this.prototypesCurrentlyInCreation.get();
		if (curVal == null) {
			// 注：如果尚未存在正在创建的(非单例)bean缓存，则将该bean名称缓存起来
			this.prototypesCurrentlyInCreation.set(beanName);
		}
		else if (curVal instanceof String) {
			// 注：当前存在一个正在创建的bean，换成set缓存起来
			Set<String> beanNameSet = new HashSet<>(2);
			beanNameSet.add((String) curVal);
			beanNameSet.add(beanName);
			this.prototypesCurrentlyInCreation.set(beanNameSet);
		}
		else {
			// 注：将当前bean名称缓存在线程上下文的(非单例)bean正在创建缓存中
			Set<String> beanNameSet = (Set<String>) curVal;
			beanNameSet.add(beanName);
		}
	}

	/**
	 * Callback after prototype creation.
	 * <p>The default implementation marks the prototype as not in creation anymore.
	 * 注：(非单例)bean创建后的回调逻辑
	 * - 默认实现上将该bean名称从线程上下文缓存中移除。
	 * @param beanName the name of the prototype that has been created
	 * @see #isPrototypeCurrentlyInCreation
	 */
	@SuppressWarnings("unchecked")
	protected void afterPrototypeCreation(String beanName) {
		Object curVal = this.prototypesCurrentlyInCreation.get();
		if (curVal instanceof String) {
			this.prototypesCurrentlyInCreation.remove();
		}
		else if (curVal instanceof Set) {
			Set<String> beanNameSet = (Set<String>) curVal;
			beanNameSet.remove(beanName);
			if (beanNameSet.isEmpty()) {
				this.prototypesCurrentlyInCreation.remove();
			}
		}
	}

	@Override
	public void destroyBean(String beanName, Object beanInstance) {
		destroyBean(beanName, beanInstance, getMergedLocalBeanDefinition(beanName));
	}

	/**
	 * Destroy the given bean instance (usually a prototype instance
	 * obtained from this factory) according to the given bean definition.
	 * @param beanName the name of the bean definition
	 * @param bean the bean instance to destroy
	 * @param mbd the merged bean definition
	 */
	protected void destroyBean(String beanName, Object bean, RootBeanDefinition mbd) {
		new DisposableBeanAdapter(
				bean, beanName, mbd, getBeanPostProcessorCache().destructionAware, getAccessControlContext()).destroy();
	}

	@Override
	public void destroyScopedBean(String beanName) {
		RootBeanDefinition mbd = getMergedLocalBeanDefinition(beanName);
		if (mbd.isSingleton() || mbd.isPrototype()) {
			throw new IllegalArgumentException(
					"Bean name '" + beanName + "' does not correspond to an object in a mutable scope");
		}
		String scopeName = mbd.getScope();
		Scope scope = this.scopes.get(scopeName);
		if (scope == null) {
			throw new IllegalStateException("No Scope SPI registered for scope name '" + scopeName + "'");
		}
		Object bean = scope.remove(beanName);
		if (bean != null) {
			destroyBean(beanName, bean, mbd);
		}
	}


	//---------------------------------------------------------------------
	// Implementation methods
	//---------------------------------------------------------------------

	/**
	 * Return the bean name, stripping out the factory dereference prefix if necessary,
	 * and resolving aliases to canonical names.
	 * 注：返回bean的名称。内部去除掉了factoryBean的前缀、处理别名
	 * @param name the user-specified name
	 * @return the transformed bean name
	 */
	protected String transformedBeanName(String name) {
		return canonicalName(BeanFactoryUtils.transformedBeanName(name));
	}

	/**
	 * Determine the original bean name, resolving locally defined aliases to canonical names.
	 * @param name the user-specified name
	 * @return the original bean name
	 */
	protected String originalBeanName(String name) {
		String beanName = transformedBeanName(name);
		if (name.startsWith(FACTORY_BEAN_PREFIX)) {
			beanName = FACTORY_BEAN_PREFIX + beanName;
		}
		return beanName;
	}

	/**
	 * Initialize the given BeanWrapper with the custom editors registered
	 * with this factory. To be called for BeanWrappers that will create
	 * and populate bean instances.
	 * <p>The default implementation delegates to {@link #registerCustomEditors}.
	 * Can be overridden in subclasses.
	 * @param bw the BeanWrapper to initialize
	 * 注：参考--> https://blog.csdn.net/qq_30321211/article/details/108350140
	 */
	protected void initBeanWrapper(BeanWrapper bw) {
		bw.setConversionService(getConversionService());
		registerCustomEditors(bw);
	}

	/**
	 * Initialize the given PropertyEditorRegistry with the custom editors
	 * that have been registered with this BeanFactory.
	 * <p>To be called for BeanWrappers that will create and populate bean
	 * instances, and for SimpleTypeConverter used for constructor argument
	 * and factory method type conversion.
	 * @param registry the PropertyEditorRegistry to initialize
	 */
	protected void registerCustomEditors(PropertyEditorRegistry registry) {
		PropertyEditorRegistrySupport registrySupport =
				(registry instanceof PropertyEditorRegistrySupport ? (PropertyEditorRegistrySupport) registry : null);
		if (registrySupport != null) {
			registrySupport.useConfigValueEditors();
		}
		if (!this.propertyEditorRegistrars.isEmpty()) {
			for (PropertyEditorRegistrar registrar : this.propertyEditorRegistrars) {
				try {
					registrar.registerCustomEditors(registry);
				}
				catch (BeanCreationException ex) {
					Throwable rootCause = ex.getMostSpecificCause();
					if (rootCause instanceof BeanCurrentlyInCreationException) {
						BeanCreationException bce = (BeanCreationException) rootCause;
						String bceBeanName = bce.getBeanName();
						if (bceBeanName != null && isCurrentlyInCreation(bceBeanName)) {
							if (logger.isDebugEnabled()) {
								logger.debug("PropertyEditorRegistrar [" + registrar.getClass().getName() +
										"] failed because it tried to obtain currently created bean '" +
										ex.getBeanName() + "': " + ex.getMessage());
							}
							onSuppressedException(ex);
							continue;
						}
					}
					throw ex;
				}
			}
		}
		if (!this.customEditors.isEmpty()) {
			this.customEditors.forEach((requiredType, editorClass) ->
					registry.registerCustomEditor(requiredType, BeanUtils.instantiateClass(editorClass)));
		}
	}


	/**
	 * Return a merged RootBeanDefinition, traversing the parent bean definition
	 * if the specified bean corresponds to a child bean definition.
	 * 注：通过当前方法在当前上下文中(当前bean工厂)返回指定beanName对应的合并后的Bean定义。
	 * 合并后的bean定义是指如果指定的bean具有父bean定义，则先遍历父bean定义，然后再用子bean定义覆盖之。
	 * @param beanName the name of the bean to retrieve the merged definition for
	 * @return a (potentially merged) RootBeanDefinition for the given bean
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @throws BeanDefinitionStoreException in case of an invalid bean definition
	 * 参考：https://blog.csdn.net/qq_30321211/article/details/108336168
	 * 区别当前抽象工厂提供的【公开】的获取合并bean定义的方法。
	 * @see AbstractBeanFactory#getMergedBeanDefinition(java.lang.String)
	 */
	protected RootBeanDefinition getMergedLocalBeanDefinition(String beanName) throws BeansException {
		// Quick check on the concurrent map first, with minimal locking.
		// 注：合并bean定义是需要在mergedBeanDefinitions缓存上加锁的，这里通过从缓存中get快速判断当前bean是否已经合并了
		RootBeanDefinition mbd = this.mergedBeanDefinitions.get(beanName);
		if (mbd != null && !mbd.stale) {
			// 注：如果合并后的bean定义存在且未过时(不需要重新合并)，就直接返回即可
			return mbd;
		}

		/**
		 * 注：获取当前beanName对应的合并bean定义。
		 * 1. 先通过基础方法getBeanDefinition，获取通过该beanName注册的bean定义。【注意该beanName不能是别名，必须是bean的ID】
		 * 2. 再通过getMergedBeanDefinition方法根据注册bean定义来进行合并-如果存在parent定义，就需要合并父bean定义。
		 */
		return getMergedBeanDefinition(beanName, getBeanDefinition(beanName));
	}

	/**
	 * Return a RootBeanDefinition for the given top-level bean, by merging with
	 * the parent if the given bean's definition is a child bean definition.
	 * 注：如果给的一级bean定义是一个子bean定义，就执行合并操作并赶回合并bean定义
	 * @param beanName the name of the bean definition
	 * @param bd the original bean definition (Root/ChildBeanDefinition)
	 * 注：bd就是当前beanName注册的bean定义，可能是个子bean定义(即具有父bean定义)，也可能是不具有父bean定义的根bean定义
	 * @return a (potentially merged) RootBeanDefinition for the given bean
	 * @throws BeanDefinitionStoreException in case of an invalid bean definition
	 */
	protected RootBeanDefinition getMergedBeanDefinition(String beanName, BeanDefinition bd)
			throws BeanDefinitionStoreException {

		// 注：根据bean名称以及注册的bean定义来获取合并bean定义【外部bean】
		return getMergedBeanDefinition(beanName, bd, null);
	}

	/**
	 * Return a RootBeanDefinition for the given bean, by merging with the
	 * parent if the given bean's definition is a child bean definition.
	 * 注：如果给的bean定义是一个子bean定义，就执行合并操作并赶回合并后的bean定义
	 * @param beanName the name of the bean definition
	 * @param bd the original bean definition (Root/ChildBeanDefinition)
	 * @param containingBd the containing bean definition in case of inner bean,
	 * or {@code null} in case of a top-level bean
	 * 注：如果是内部bean，则为外部bean(即，top-level)的定义；如果是外部bean，此参数为null
	 * @return a (potentially merged) RootBeanDefinition for the given bean
	 * @throws BeanDefinitionStoreException in case of an invalid bean definition
	 */
	protected RootBeanDefinition getMergedBeanDefinition(
			String beanName, BeanDefinition bd, @Nullable BeanDefinition containingBd)
			throws BeanDefinitionStoreException {

		// 注：涉及到合并bean定义的动作均需要对mergedBeanDefinitions对象添加同步锁
		synchronized (this.mergedBeanDefinitions) {
			RootBeanDefinition mbd = null;		// 注：最终要返回的合并bean定义
			RootBeanDefinition previous = null;	// 注：缓存已经合并，但需要重新合并的bean定义

			// Check with full lock now in order to enforce the same merged instance.
			/**
			 * 注：进入同步区再次检查合并bean定义缓存中是否存在已经合并的实例[DCL]，为了防止获得锁之前并发线程已经合并bean定义了。
			 * - 为什么这里containingBd不为Null(即，合并内部bean定义)的时候不需要检查呢？
			 * 	 内部bean的合并bean定义结果不会缓存在mergedBeanDefinitions缓存中，这里也没必要检查。
			 * - 为什么mergedBeanDefinitions不缓存内部bean的合并bean定义呢？
			 * 	 1. 猜测可能的原因是由于内部bean可能依赖于外部bean的一些bean定义属性，比如作用域。
			 * 	    如果缓存内部bean定义就无法在外部bean定义更新的情况下更新内部bean定义了。
			 *	 2. 外部合并bean定义可以根据是否已创建缓存来对bean定义设置过时标识，而内部bean无法设置过时标识。【最大原因】
			 *	 	因此不能对其进行缓存，免得内部bean定义修改后无法生效。
			 */
			if (containingBd == null) {
				mbd = this.mergedBeanDefinitions.get(beanName);
			}

			/**
			 * 注：此时如果mbd存在且未生效(stale属性为false)，则直接返回该已合并的bean定义即可。
			 * 如果mbd不存在或已失效(stale属性为false)，则根据已注册的bean定义是否存在parent属性来决定是否进行合并bean定义流程。
			 */

			if (mbd == null || mbd.stale) {
				// 注：下面是合并bean不存在或合并bean定义已过时的情况
				previous = mbd;		// 注: 记录之前的合并bean定义 【有何用途？】
				if (bd.getParentName() == null) {
					// Use copy of given root bean definition.
					/**
					 * 注：如果当前bean定义不存在parent属性，即未指定父bean定义，这也就意味着不需要合并。
					 * 但不合并并不意味着直接将注册的bean定义返回去。spring运行时创建bean实例所需要的bean定义类型为RootBeanDefinition。
					 * (RootBeanDefinition，即根定义，肯定是不需要合并的。RootBeanDefinition是目前bean工厂运行时bean定义的统一类型。)
					 * 1. 如果已注册的bean定义类型为RootBeanDefinition，就执行RootBeanDefinition的拷贝。
					 * 2. 如果已注册的bean定义类型非根bean定义，则通过RootBeanDefinition的构造方法来"拷贝"。(这里数据仅只有AbstractBeanDefinition属性数据)
					 * - 为什么这里是拷贝，而不是直接返回？即使注册bean类型已经是合并bean定义类型了。
					 * 	 原因是保留最原始注册bean定义，返回的已合并bean定义可能会被一些后置处理器修改，比如合并bean定义后置处理器。
					 */
					if (bd instanceof RootBeanDefinition) {
						// 注：如果是RootBeanDefinition，拷贝时就需要保留RootBeanDefinition本身的许多属性数据
						mbd = ((RootBeanDefinition) bd).cloneBeanDefinition();
					}
					else {
						// 注：如果是非RootBeanDefinition，拷贝时就需要只需要拷贝AbstractBeanDefinition属性数据
						mbd = new RootBeanDefinition(bd);
					}
				}
				else {
					// Child bean definition: needs to be merged with parent.
					/**
					 * 注：如果当前bean定义存在parent属性，即当前bd是一个子bean定义，这也就意味着需要与parent属性指定的父bean定义合并。
					 * 合并的步骤该大概如下：
					 * 1. 获取父bean合并bean定义(非null)。【仍考虑两个方面：① 父bean定义也是个子bean定义；② 父bean定义可能注册于父工厂】
					 * 2. 通过RootBeanDefinition构造方法，将父合并bean定义“拷贝”一份(不直接引用父合并bean定义)。
					 * 3. 将子bean定义的属性"合并"到父bean定义上得到最终合并bean定义。【合并操作具体根据属性不同操作也不同】
					 * 	  具体哪些父bean属性子类可以继承，哪些可以不继承，哪些是合并的属性，可参见AbstractBeanDefinition#overrideFrom()
					 */
					BeanDefinition pbd;		// 注：用于存储根据parent属性获取的父级bean定义
					try {
						// 注：获取父bean的规范名(已处理前缀、别名)
						String parentBeanName = transformedBeanName(bd.getParentName());
						if (!beanName.equals(parentBeanName)) {
							// 注：这里通过公共方法获取beanName的合并bean定义，可能父bean定义来自于父工厂。
							pbd = getMergedBeanDefinition(parentBeanName);
						}
						else {
							// 注：如果父bean名和子bean名相同，那就能够确定父bean定义肯定在父bean工厂中【当前工厂不能有重复bean，也不可能自己是自己的父bean】
							BeanFactory parent = getParentBeanFactory();
							// 注：ConfigurableBeanFactory类型是实现父子工厂最低类型要求，然后调用对应方法获取父bean定义。
							if (parent instanceof ConfigurableBeanFactory) {
								pbd = ((ConfigurableBeanFactory) parent).getMergedBeanDefinition(parentBeanName);
							}
							else {
								throw new NoSuchBeanDefinitionException(parentBeanName,
										"Parent name '" + parentBeanName + "' is equal to bean name '" + beanName +
										"': cannot be resolved without a ConfigurableBeanFactory parent");
							}
						}
					}
					catch (NoSuchBeanDefinitionException ex) {
						// 注：获取不到pbd抛出异常，pbd肯定不会null
						throw new BeanDefinitionStoreException(bd.getResourceDescription(), beanName,
								"Could not resolve parent bean definition '" + bd.getParentName() + "'", ex);
					}
					// Deep copy with overridden values.
					/**
					 * 注：这里是合并bean定义的重点操作。
					 * 1. 根据父bean定义来创建一个RootBeanDefinition实例。
					 * 	  正常情况下pbd肯定是RootBeanDefinition类型，但也能存在自定义父工厂而返回其他类型。
					 * 	  这种情况下复制父合并bean定义不会考虑RootBeanDefinition类型的独有属性数据。
					 * 2. 使用子bean定义来覆盖通过父bean定义创建的bean定义--也可理解为子bean定义覆盖父bean定义(实际这种说法不准确)
					 *	  需要注意overrideFrom函数具体含义。并非简单的覆盖！有的属性数据可继承，有的不可、也有的是合并。
					 *	  具体不同属性如何合并，参考overrideFrom函数注释。
					 */
					mbd = new RootBeanDefinition(pbd);	// 注：复制父bean定义
					mbd.overrideFrom(bd);	// 注：“覆盖”父bean定义中复制("继承")过来的属性
				}

				/**
				 * 注：实际上这里已经完成了bean定义的合并，即mbd。
				 * 下面除了最后缓存已合并的bean定义，还有两步是修正合并后bean定义的作用域属性的：
				 * 1. 修改空作用域为单例。
				 * 2. 当内部bean为单例，而外部bean为非单例的情况下，需要修正内部bean的作用域
				 * - 这些动作不能在注册bean定义的时候或者合并bean定义时搞定么？由于合并bean的动作，无法在前期一次性完成。
				 *   对于1，如果注册时将默认空作用域(用户未指定)直接设置为单例，那么在合并bean定义时，肯定不会继承父bean定义的作用域了，即使其实非单例。
				 *   对于2，父bean定义可能在执行自身合并时受其他bean定义影响其作用域，进而需要对当前合并bean定义进行修正。
				 */

				// Set default singleton scope, if not configured before.
				// 注：如果合并bean定义没有配置作用域，这里设置为默认的单例作用域
				if (!StringUtils.hasLength(mbd.getScope())) {
					mbd.setScope(SCOPE_SINGLETON);
				}

				// A bean contained in a non-singleton bean cannot be a singleton itself.
				// Let's correct this on the fly here, since this might be the result of
				// parent-child merging for the outer bean, in which case the original inner bean
				// definition will not have inherited the merged outer bean's singleton status.
				/**
				 * 注：单例bean不能包含非单例内部bean。原因是spring容器创建单例bean仅有一次，无法获得多个不同的非单例内部bean【即破坏了内部bean的单例】。
				 * 由于合并父子bean定义的原因，外部bean定义在合并后可能会改变其作用域为单例，
				 * 并且原有的内部bean也不会从已合并bean定义继承单例状态，因此需要在这里赶紧纠正外部bean的作用域状态。
				 */
				if (containingBd != null && !containingBd.isSingleton() && mbd.isSingleton()) {
					// 注：异常情况：当前内部bean为单例，而外部bean定义为非单例。
					// 内部bean的作用域失效，直接与外部bean的作用域保持一致。
					mbd.setScope(containingBd.getScope());
				}

				// Cache the merged bean definition for the time being
				// (it might still get re-merged later on in order to pick up metadata changes)
				/**
				 * 注：暂时缓存已合并的bean定义（为了获取元数据的变动，可能后续还会重新合并，如clearMetadataCache）
				 * -- 从这里看，cacheBeanMetadata表示是否缓存非内部bean的合并bean定义。
				 */
				if (containingBd == null && isCacheBeanMetadata()) {
					this.mergedBeanDefinitions.put(beanName, mbd);
				}
			}
			if (previous != null) {
				/**
				 * 注：如果当前beanName之前存在已过时的合并bean定义，且本次又重新合并的情况下，考虑将之前合并bean定义的一些解析后的属性直接赋值到新的合并bean定义上。
				 * 如果前后的合并bean定义类名、工厂bean类名、工厂方法名都相同的话，那么RootBeanDefinition的一些需解析的缓存属性也可以直接复制即可。
				 * 可复制数据有：目标类型(包装后)、是否为工厂bean标记，解析后的确定类型的Class对象、工厂方法返回类型、自查工厂方法的候选者
				 * - 这部分代码是不是应该放置在缓存mbd之前更好...
				 *  因为当前是mergedBeanDefinitions缓存的同步区，put操作必须在最后，否则仍有可能出现多线程竞争情况下拿到未复制旧定义属性的合并bean定义（不过这个影响不大）。
				 */
				copyRelevantMergedBeanDefinitionCaches(previous, mbd);
			}
			return mbd;
		}
	}

	// 注：相关说明如上
	private void copyRelevantMergedBeanDefinitionCaches(RootBeanDefinition previous, RootBeanDefinition mbd) {
		if (ObjectUtils.nullSafeEquals(mbd.getBeanClassName(), previous.getBeanClassName()) &&
				ObjectUtils.nullSafeEquals(mbd.getFactoryBeanName(), previous.getFactoryBeanName()) &&
				ObjectUtils.nullSafeEquals(mbd.getFactoryMethodName(), previous.getFactoryMethodName())) {
			ResolvableType targetType = mbd.targetType;
			ResolvableType previousTargetType = previous.targetType;
			if (targetType == null || targetType.equals(previousTargetType)) {
				mbd.targetType = previousTargetType;
				mbd.isFactoryBean = previous.isFactoryBean;
				mbd.resolvedTargetType = previous.resolvedTargetType;
				mbd.factoryMethodReturnType = previous.factoryMethodReturnType;
				mbd.factoryMethodToIntrospect = previous.factoryMethodToIntrospect;
			}
		}
	}

	/**
	 * Check the given merged bean definition,
	 * potentially throwing validation exceptions.
	 * 注：在创建bean的时候检查合并bean定义-可能会抛出bean创建异常(BeanCreationException)
	 * - 验证当前bean是否为抽象-即不允许实例化
	 * @param mbd the merged bean definition to check
	 * @param beanName the name of the bean
	 * @param args the arguments for bean creation, if any
	 * @throws BeanDefinitionStoreException in case of validation failure
	 */
	protected void checkMergedBeanDefinition(RootBeanDefinition mbd, String beanName, @Nullable Object[] args)
			throws BeanDefinitionStoreException {

		if (mbd.isAbstract()) {
			throw new BeanIsAbstractException(beanName);
		}
	}

	/**
	 * Remove the merged bean definition for the specified bean,
	 * recreating it on next access.
	 * @param beanName the bean name to clear the merged definition for
	 */
	protected void clearMergedBeanDefinition(String beanName) {
		RootBeanDefinition bd = this.mergedBeanDefinitions.get(beanName);
		if (bd != null) {
			bd.stale = true;
		}
	}

	/**
	 * Clear the merged bean definition cache, removing entries for beans
	 * which are not considered eligible for full metadata caching yet.
	 * <p>Typically triggered after changes to the original bean definitions,
	 * e.g. after applying a {@code BeanFactoryPostProcessor}. Note that metadata
	 * for beans which have already been created at this point will be kept around.
	 * @since 4.2
	 */
	public void clearMetadataCache() {
		this.mergedBeanDefinitions.forEach((beanName, bd) -> {
			if (!isBeanEligibleForMetadataCaching(beanName)) {
				bd.stale = true;
			}
		});
	}

	/**
	 * Resolve the bean class for the specified bean definition,
	 * resolving a bean class name into a Class reference (if necessary)
	 * and storing the resolved Class in the bean definition for further use.
	 * 注：根据指定的bean定义解析bean的类型，
	 * 内部逻辑是将bean的名称解析为bean的引用并且将解析后的类型信息缓存在bean定义中以备后续之用。
	 * @param mbd the merged bean definition to determine the class for
	 * @param beanName the name of the bean (for error handling purposes)
	 * @param typesToMatch the types to match in case of internal type matching purposes
	 * (also signals that the returned {@code Class} will never be exposed to application code)
	 * @return the resolved bean class (or {@code null} if none)
	 * @throws CannotLoadBeanClassException if we failed to load the class
	 * 参考：https://blog.csdn.net/qq_30321211/article/details/108345288
	 */
	@Nullable
	protected Class<?> resolveBeanClass(RootBeanDefinition mbd, String beanName, Class<?>... typesToMatch)
			throws CannotLoadBeanClassException {

		try {
			if (mbd.hasBeanClass()) {
				// 注：如果bean定义中指定了bean的类型，就直接返回即可。
				return mbd.getBeanClass();
			}
			if (System.getSecurityManager() != null) {
				/**
				 * 注：安全管理器相关可参考：
				 * https://www.jianshu.com/p/3fe79e24f8a1
				 */
				return AccessController.doPrivileged((PrivilegedExceptionAction<Class<?>>)
						() -> doResolveBeanClass(mbd, typesToMatch), getAccessControlContext());
			}
			else {
				// 注：根据已合并bean定义来加载bean的类型
				return doResolveBeanClass(mbd, typesToMatch);
			}
		}
		catch (PrivilegedActionException pae) {
			// 注：无法使用特权异常
			ClassNotFoundException ex = (ClassNotFoundException) pae.getException();
			throw new CannotLoadBeanClassException(mbd.getResourceDescription(), beanName, mbd.getBeanClassName(), ex);
		}
		catch (ClassNotFoundException ex) {
			// 注：未找到对应类异常
			throw new CannotLoadBeanClassException(mbd.getResourceDescription(), beanName, mbd.getBeanClassName(), ex);
		}
		catch (LinkageError err) {
			// LinkageError:LinkageError的子类表明一个类对另一个类具有一定的依赖性。但是，后一类在前一类编译之后发生了
			// 不兼容的变化
			throw new CannotLoadBeanClassException(mbd.getResourceDescription(), beanName, mbd.getBeanClassName(), err);
		}
	}

	// 注：根据已合并bean定义来解析bean的类型。注意，这里的私有方法名前面有do.
	@Nullable
	private Class<?> doResolveBeanClass(RootBeanDefinition mbd, Class<?>... typesToMatch)
			throws ClassNotFoundException {

		// 注：获取该工厂加载bean的类加载器
		ClassLoader beanClassLoader = getBeanClassLoader();
		/**
		 * 注：为什么叫这个为动态类加载器呢？为什么不直接使用beanClassLoader？
		 * 1. 如果当前仅用于类型匹配，对于织入场景，需要使用工厂中的临时类加载器。
		 * 2. 如果当前className配置为spel表达式，动态解析后的类名可能每次都不同。这种情况也是使用动态类加载器加载。
		 */
		// 注：默认使用bean类加载器
		ClassLoader dynamicLoader = beanClassLoader;
		// 注：标识已合并bean定义是否需要被dynamicLoader重新解析、加载、标记。默认不需要；
		boolean freshResolve = false;

		if (!ObjectUtils.isEmpty(typesToMatch)) {
			// When just doing type checks (i.e. not creating an actual instance yet),
			// use the specified temporary class loader (e.g. in a weaving scenario).
			/**
			 * 注：我们仅仅使用指定的类加载器进行类型检查，而不会进行实际的实例创建。
			 */
			ClassLoader tempClassLoader = getTempClassLoader();
			if (tempClassLoader != null) {
				// 注：如果存在临时类加载器，那么后续就使用临时类加载器，并且设置重新加载标识。
				dynamicLoader = tempClassLoader;
				freshResolve = true;
				if (tempClassLoader instanceof DecoratingClassLoader) {
					/**
					 * 注：如果临时类加载器是DecoratingClassLoader类型，就将匹配的类型在加载器中排除掉。
					 * 这意味后续类型的加载都交给其父加载器(一般是应用类加载器)进行加载执行。
					 * DecoratingClassLoader类是装饰ClassLoader的类。提供了对类、包排除的通用处理。
					 */
					DecoratingClassLoader dcl = (DecoratingClassLoader) tempClassLoader;
					for (Class<?> typeToMatch : typesToMatch) {
						dcl.excludeClass(typeToMatch.getName());
					}
				}
			}
		}

		// 注：获取bean定义的bean类名(bean类未加载的情况下返回类名) 【这里是不可能为Null】
		String className = mbd.getBeanClassName();
		if (className != null) {
			// 注：解析bean定义中的字符串【这里就是解析类名中可能存在的spel表达式】
			Object evaluated = evaluateBeanDefinitionString(className, mbd);
			if (!className.equals(evaluated)) {
				// A dynamically resolved expression, supported as of 4.2...
				/**
				 * 注：4.2版本后bean的类名支持动态解析表达式
				 * spel表达式解析属于动态解析，因此解析后如果是Class类型，就直接返回；如果是String类型的类名，就重新加载(bean类型可能存在动态变化)。
				 */
				if (evaluated instanceof Class) { // 注：如果spel直接解析为类对象，返回！
					return (Class<?>) evaluated;
				}
				else if (evaluated instanceof String) {	// 注：spel解析后的类名
					className = (String) evaluated;
					// 注：spel动态解析的类型，需要重新刷新加载；重新加载是不会缓存到bean定义中。
					freshResolve = true;
				}
				else {
					throw new IllegalStateException("Invalid class name expression result: " + evaluated);
				}
			}
			if (freshResolve) {
				// When resolving against a temporary class loader, exit early in order
				// to avoid storing the resolved Class in the bean definition.
				/**
				 * 注：需要重新加载beanClass时，需要注意的就是，如果我们使用临时类加载器加载，就不需要存储在bean定义中。
				 * - 重新加载的class均不会存储在bean定义中，以便于后续bean类型可能存在变化。
				 * - 后续：类加载器的loadClass和forName区别呢
				 */
				if (dynamicLoader != null) {
					try {
						// 注：通过动态类加载器加载类
						return dynamicLoader.loadClass(className);
					}
					catch (ClassNotFoundException ex) {
						if (logger.isTraceEnabled()) {
							logger.trace("Could not load class [" + className + "] from " + dynamicLoader + ": " + ex);
						}
					}
				}
				// 注：使用bean加载器加载类
				return ClassUtils.forName(className, dynamicLoader);
			}
		}

		// Resolve regularly, caching the result in the BeanDefinition...
		/**
		 * 注：根据bean定义中的类名解析类型，并将加载的结果缓存的bean定义中。
		 */
		return mbd.resolveBeanClass(beanClassLoader);
	}

	/**
	 * Evaluate the given String as contained in a bean definition,
	 * potentially resolving it as an expression.
	 * 注：解析可能需要作为表达式进行解析的bean定义中包含的字符串；
	 * 比如bean类名
	 * @param value the value to check
	 * @param beanDefinition the bean definition that the value comes from
	 * @return the resolved value
	 * @see #setBeanExpressionResolver
	 * 注：参考--> https://blog.csdn.net/qq_30321211/article/details/108345288
	 */
	@Nullable
	protected Object evaluateBeanDefinitionString(@Nullable String value, @Nullable BeanDefinition beanDefinition) {
		if (this.beanExpressionResolver == null) {
			// 注：如果不存在bean表达式解析器，那就直接返回即可。
			return value;
		}

		// 注：获取bean定义作用范围Scope对象
		Scope scope = null;
		if (beanDefinition != null) {
			String scopeName = beanDefinition.getScope();
			if (scopeName != null) {
				scope = getRegisteredScope(scopeName);
			}
		}

		/**
		 * 注：通过bean表达式解析器进行解析【具体实现类：StandardBeanExpressionResolver】
		 * - 传入待解析的值
		 * - 传入bean表达式的上下文(存有bean工厂、所在bean的作用域)
		 */
		return this.beanExpressionResolver.evaluate(value, new BeanExpressionContext(this, scope));
	}


	/**
	 * Predict the eventual bean type (of the processed bean instance) for the
	 * specified bean. Called by {@link #getType} and {@link #isTypeMatch}.
	 * Does not need to handle FactoryBeans specifically, since it is only
	 * supposed to operate on the raw bean type.
	 * 注：对于指定的bean(已经处理的bean实例)预测其最终bean的类型。该方法会被getType、isTypeMatch方法调用。
	 * 不需要特地处理FactoryBean的情况，因为他只应该对原始bean类型进行操作(不太理解...)。
	 * <p>This implementation is simplistic in that it is not able to
	 * handle factory methods and InstantiationAwareBeanPostProcessors.
	 * It only predicts the bean type correctly for a standard bean.
	 * To be overridden in subclasses, applying more sophisticated type detection.
	 * 注：该方法是一个简化的实现，因为它不能够处理工厂方法以及实例化后置处理器。
	 * 这个方案仅预测标准bean的正确最终的类型。
	 * 子工厂类应该重写这个方法，以支持更加复杂的类型推断。【该方法被AbstractAutowireCapableBeanFactory复写】
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition to determine the type for
	 * @param typesToMatch the types to match in case of internal type matching purposes
	 * (also signals that the returned {@code Class} will never be exposed to application code)
	 * 注：在内部类型匹配过程时需要指定的类型(临时类加载器将会忽略这些类)。（这也意味着返回的类不会暴漏在应用程序代码中）
	 * @return the type of the bean, or {@code null} if not predictable
	 * 参考：https://blog.csdn.net/qq_30321211/article/details/108348807
	 */
	@Nullable
	protected Class<?> predictBeanType(String beanName, RootBeanDefinition mbd, Class<?>... typesToMatch) {
		Class<?> targetType = mbd.getTargetType();	// 注：判断当前bean定义是否有目标类型
		if (targetType != null) {
			return targetType;
		}
		if (mbd.getFactoryMethodName() != null) {	// 注：bean定义无factoryMethodName，也就无法继续预测了
			return null;
		}
		return resolveBeanClass(mbd, beanName, typesToMatch);
	}

	/**
	 * Check whether the given bean is defined as a {@link FactoryBean}.
	 * 注：根据给定的合并后的bean定义来判断是否为FactoryBean
	 * @param beanName the name of the bean
	 * @param mbd the corresponding bean definition
	 */
	protected boolean isFactoryBean(String beanName, RootBeanDefinition mbd) {
		Boolean result = mbd.isFactoryBean;
		if (result == null) {
			/**
			 * 注：如果bean定义的isFactoryBean属性为null，这就意味着该属性没有初始化。
			 * 下面将根据bean定义来推断
			 */
			Class<?> beanType = predictBeanType(beanName, mbd, FactoryBean.class);
			result = (beanType != null && FactoryBean.class.isAssignableFrom(beanType));
			mbd.isFactoryBean = result;
		}
		return result;
	}

	/**
	 * Determine the bean type for the given FactoryBean definition, as far as possible.
	 * Only called if there is no singleton instance registered for the target bean
	 * already. Implementations are only allowed to instantiate the factory bean if
	 * {@code allowInit} is {@code true}, otherwise they should try to determine the
	 * result through other means.
	 * <p>If no {@link FactoryBean#OBJECT_TYPE_ATTRIBUTE} if set on the bean definition
	 * and {@code allowInit} is {@code true}, the default implementation will create
	 * the FactoryBean via {@code getBean} to call its {@code getObjectType} method.
	 * Subclasses are encouraged to optimize this, typically by inspecting the generic
	 * signature of the factory bean class or the factory method that creates it. If
	 * subclasses do instantiate the FactoryBean, they should consider trying the
	 * {@code getObjectType} method without fully populating the bean. If this fails, a
	 * full FactoryBean creation as performed by this implementation should be used as
	 * fallback.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @param allowInit if initialization of the FactoryBean is permitted
	 * @return the type for the bean if determinable, otherwise {@code ResolvableType.NONE}
	 * @since 5.2
	 * @see org.springframework.beans.factory.FactoryBean#getObjectType()
	 * @see #getBean(String)
	 */
	protected ResolvableType getTypeForFactoryBean(String beanName, RootBeanDefinition mbd, boolean allowInit) {
		ResolvableType result = getTypeForFactoryBeanFromAttributes(mbd);
		if (result != ResolvableType.NONE) {
			return result;
		}

		if (allowInit && mbd.isSingleton()) {
			try {
				FactoryBean<?> factoryBean = doGetBean(FACTORY_BEAN_PREFIX + beanName, FactoryBean.class, null, true);
				Class<?> objectType = getTypeForFactoryBean(factoryBean);
				return (objectType != null) ? ResolvableType.forClass(objectType) : ResolvableType.NONE;
			}
			catch (BeanCreationException ex) {
				if (ex.contains(BeanCurrentlyInCreationException.class)) {
					logger.trace(LogMessage.format("Bean currently in creation on FactoryBean type check: %s", ex));
				}
				else if (mbd.isLazyInit()) {
					logger.trace(LogMessage.format("Bean creation exception on lazy FactoryBean type check: %s", ex));
				}
				else {
					logger.debug(LogMessage.format("Bean creation exception on eager FactoryBean type check: %s", ex));
				}
				onSuppressedException(ex);
			}
		}
		return ResolvableType.NONE;
	}

	/**
	 * Determine the bean type for a FactoryBean by inspecting its attributes for a
	 * {@link FactoryBean#OBJECT_TYPE_ATTRIBUTE} value.
	 * @param attributes the attributes to inspect
	 * @return a {@link ResolvableType} extracted from the attributes or
	 * {@code ResolvableType.NONE}
	 * @since 5.2
	 */
	ResolvableType getTypeForFactoryBeanFromAttributes(AttributeAccessor attributes) {
		Object attribute = attributes.getAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE);
		if (attribute instanceof ResolvableType) {
			return (ResolvableType) attribute;
		}
		if (attribute instanceof Class) {
			return ResolvableType.forClass((Class<?>) attribute);
		}
		return ResolvableType.NONE;
	}

	/**
	 * Determine the bean type for the given FactoryBean definition, as far as possible.
	 * Only called if there is no singleton instance registered for the target bean already.
	 * <p>The default implementation creates the FactoryBean via {@code getBean}
	 * to call its {@code getObjectType} method. Subclasses are encouraged to optimize
	 * this, typically by just instantiating the FactoryBean but not populating it yet,
	 * trying whether its {@code getObjectType} method already returns a type.
	 * If no type found, a full FactoryBean creation as performed by this implementation
	 * should be used as fallback.
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @return the type for the bean if determinable, or {@code null} otherwise
	 * @see org.springframework.beans.factory.FactoryBean#getObjectType()
	 * @see #getBean(String)
	 * @deprecated since 5.2 in favor of {@link #getTypeForFactoryBean(String, RootBeanDefinition, boolean)}
	 */
	@Nullable
	@Deprecated
	protected Class<?> getTypeForFactoryBean(String beanName, RootBeanDefinition mbd) {
		return getTypeForFactoryBean(beanName, mbd, true).resolve();
	}

	/**
	 * Mark the specified bean as already created (or about to be created).
	 * <p>This allows the bean factory to optimize its caching for repeated
	 * creation of the specified bean.
	 * 注：标记当前指定的bean已经创建。【实际上该bean正要创建...】
	 * - 如果该beanName对应的bean实例是第一次创建，那么在创建之前需要先清空下可能已经合并的bean定义。然后将beanName缓存到已创建bean的缓存中。
	 * - 为什么要清空已经合并的bean定义？
	 *	 在实际第一次创建该bean实例之前可能已经存在合并bean定义了(比如bean类型检查)，而bean定义也有可能在运行时发生修改，因此在实际创建bean实例之前，
	 *	 需要清除下合并bean定义。清除动作就是设置bean定义的stale标识为true，即已过时。
	 * - 实际上该bean实例是正要创建，还未创建，这里直接放置在alreadyCreated缓存中可能存在问题。
	 * 	 在bean发生循环依赖时，如果bean实例在初始化之后可能获得的是代理对象而非早期引用对象了，如果这时存在已创建的bean依赖了该实例，那么就会报错。
	 * 	 那么：A -依赖-> B，而B在初始化后是个代理对象，B会检测到A依赖了B，而A还在已创建缓存中，那么是否会报错呢？后续研究下。
	 * 	 @see  AbstractBeanFactory#removeSingletonIfCreatedForTypeCheckOnly(java.lang.String)
	 * @param beanName the name of the bean
	 */
	protected void markBeanAsCreated(String beanName) {
		/**
		 * 注：通过mergedBeanDefinitions缓存添加同步锁；
		 * - 合并bean定义仍可能在运行时发生改变，每次创建bean实例时都会合并bean定义，添加到已经创建列表时就设置【过时】标识。
		 */
		if (!this.alreadyCreated.contains(beanName)) {
			synchronized (this.mergedBeanDefinitions) {		// 注：所有涉及合并bean的操作，都是通过mergedBeanDefinitions对象加锁
				if (!this.alreadyCreated.contains(beanName)) {
					// Let the bean definition get re-merged now that we're actually creating
					// the bean... just in case some of its metadata changed in the meantime.
					clearMergedBeanDefinition(beanName);	// 注：内部设置stale标识
					this.alreadyCreated.add(beanName);
				}
			}
		}
	}

	/**
	 * Perform appropriate cleanup of cached metadata after bean creation failed.
	 * 注：bean实例创建失败后，需要对缓存的合并bean定义缓存进行清除。
	 * - 如何清除？这里看到只需从已创建bean缓存中移除即可，后续再次尝试获取bean时会重新构建合并bean定义。
	 * @param beanName the name of the bean
	 */
	protected void cleanupAfterBeanCreationFailure(String beanName) {
		// 注：为什么这里对mergedBeanDefinitions加锁？因为操作alreadyCreated时也是对该对象加锁，实际上alreadyCreated也影响了合并bean定义的内容。
		synchronized (this.mergedBeanDefinitions) {
			// 注：从已创建bean实例缓存(alreadyCreated)中移除bean名称
			this.alreadyCreated.remove(beanName);
		}
	}

	/**
	 * Determine whether the specified bean is eligible for having
	 * its bean definition metadata cached.
	 * @param beanName the name of the bean
	 * @return {@code true} if the bean's metadata may be cached
	 * at this point already
	 */
	protected boolean isBeanEligibleForMetadataCaching(String beanName) {
		return this.alreadyCreated.contains(beanName);
	}

	/**
	 * Remove the singleton instance (if any) for the given bean name,
	 * but only if it hasn't been used for other purposes than type checking.
	 * 注：移除指定bean名称的单例对象，但是前提是该对象没有用于除类型校验之外的其他使用目的。
	 * @param beanName the name of the bean
	 * @return {@code true} if actually removed, {@code false} otherwise
	 */
	protected boolean removeSingletonIfCreatedForTypeCheckOnly(String beanName) {
		if (!this.alreadyCreated.contains(beanName)) {
			// 注：非标准化流程，仅用于类型校验
			removeSingleton(beanName);
			return true;
		}
		else {
			// 注：this.alreadyCreated缓存的是spring标准创建Bean流程，非类型检查。
			return false;
		}
	}

	/**
	 * Check whether this factory's bean creation phase already started,
	 * i.e. whether any bean has been marked as created in the meantime.
	 * 注：检查是否当前bean工厂已经处于bean的创建阶段了
	 * @since 4.2.2
	 * @see #markBeanAsCreated
	 */
	protected boolean hasBeanCreationStarted() {
		return !this.alreadyCreated.isEmpty();
	}

	/**
	 * Get the object for the given bean instance, either the bean
	 * instance itself or its created object in case of a FactoryBean.
	 * 注：根据指定的bean实例返回向外的bean实例-要么是传入的bean实例本身、或者该实例是个FactoryBean实例时创建bean。
	 * ① 根据入参name来判断要获取的对象是属于工厂bean还是非工厂bean
	 * ② 如果要获取工厂bean，则只要传入的bean实例就是工厂对象，那么直接返回，否则抛出异常
	 * ③ 如果要获取非工厂bean，且传入的bean实例就是非工厂对象，那么直接返回
	 * ④ 如果要获取非工厂bean，且传入的bean实例就是工厂对象，那么需要通过工厂对象获取工厂bean对象。即getObjectFromFactoryBean
	 * @param beanInstance the shared bean instance  // 注：单例bean
	 * @param name the name that may include factory dereference prefix // 注：可能包含有工厂bean前缀的名称
	 * @param beanName the canonical bean name	// 注：去除了前缀、处理了别名后的名称
	 * @param mbd the merged bean definition	// 合并后的bean定义
	 * @return the object to expose for the bean
	 * 参考：https://blog.csdn.net/dhaiuda/article/details/83621970
	 */
	protected Object getObjectForBeanInstance(
			Object beanInstance, String name, String beanName, @Nullable RootBeanDefinition mbd) {

		// Don't let calling code try to dereference the factory if the bean isn't a factory.
		// 注：判断当前要获得的name是否带有FactoryBean的前缀
		if (BeanFactoryUtils.isFactoryDereference(name)) {
			/**
			 * 注：如果当前getBean的入参就是要获取FactoryBean，则这里做一些校验，设置下bean定义中的isFactoryBean属性即可
			 */
			if (beanInstance instanceof NullBean) {
				// 注：当前bean实例为空bean，则直接返回
				return beanInstance;
			}
			if (!(beanInstance instanceof FactoryBean)) {
				// 注：校验-带有&前缀的必须是FactoryBean，否则抛出异常
				throw new BeanIsNotAFactoryException(beanName, beanInstance.getClass());
			}
			if (mbd != null) {
				// 注：当前是工厂bean，如果传入了合并Bean定义的话，就需要在这里修改factoryBean标识；
				mbd.isFactoryBean = true;
			}
			return beanInstance;
		}

		// Now we have the bean instance, which may be a normal bean or a FactoryBean.
		// If it's a FactoryBean, we use it to create a bean instance, unless the
		// caller actually wants a reference to the factory.
		/**
		 * 注：1. 如果name就是要返回FactoryBean，那么通过上面一块逻辑，经过校验后直接返回或抛出异常。
		 * 2. 如果name并非是要返回FactoryBean，并且bean实例并非FactoryBean，那么就是普通bean，也是下面直接返回
		 */
		if (!(beanInstance instanceof FactoryBean)) {
			return beanInstance;
		}

		// 注：3. 下面就是name是要返回正常bean，但是bean实例确实一个工厂类，下面需要从工厂类中创建出目标的bean实例并返回。
		Object object = null;
		if (mbd != null) {
			// 注：当前是工厂bean，如果传入了合并Bean定义的话，就需要在这里修改factoryBean标识；
			mbd.isFactoryBean = true;
		}
		else {
			/**
			 * 注：未传入bean定义，意味着当前FactoryBean并非第一次创建。
			 * 尝试通过factoryBeanObjectCache缓存来获取工厂bean生成的单例对象。
			 */
			object = getCachedObjectForFactoryBean(beanName);
		}
		if (object == null) {
			/**
			 * 注：未获取到缓存对象，意味着第一次创建FactoryBean及其对象(存在bean定义)，也可能是获取非单例对象(不存在bean定义)
			 * - 为了判断当前工厂bean定义是否为动态生成的，这里还会尝试获取合并bean定义。
			 * - 动态生成的工厂bean生成的实例初始化后不会调用初始化后置处理器。
			 */
			// Return bean instance from factory.
			FactoryBean<?> factory = (FactoryBean<?>) beanInstance;
			// Caches object obtained from FactoryBean if it is a singleton.
			if (mbd == null && containsBeanDefinition(beanName)) {
				// 注：获取当前工厂bean的合并bean定义
				mbd = getMergedLocalBeanDefinition(beanName);
			}
			// 注：判断当前bean定义是否是动态生成的（动态代理），非动态生成的工厂bean对象会执行初始化后置处理器。
			boolean synthetic = (mbd != null && mbd.isSynthetic());
			// 注：根据工厂对象、目标bean名称、是否动态生成工厂信息来获取目标工厂bean实例【重点】
			object = getObjectFromFactoryBean(factory, beanName, !synthetic);
		}
		return object;
	}

	/**
	 * Determine whether the given bean name is already in use within this factory,
	 * i.e. whether there is a local bean or alias registered under this name or
	 * an inner bean created with this name.
	 * @param beanName the name to check
	 */
	public boolean isBeanNameInUse(String beanName) {
		return isAlias(beanName) || containsLocalBean(beanName) || hasDependentBean(beanName);
	}

	/**
	 * Determine whether the given bean requires destruction on shutdown.
	 * <p>The default implementation checks the DisposableBean interface as well as
	 * a specified destroy method and registered DestructionAwareBeanPostProcessors.
	 * @param bean the bean instance to check
	 * @param mbd the corresponding bean definition
	 * @see org.springframework.beans.factory.DisposableBean
	 * @see AbstractBeanDefinition#getDestroyMethodName()
	 * @see org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor
	 */
	protected boolean requiresDestruction(Object bean, RootBeanDefinition mbd) {
		return (bean.getClass() != NullBean.class && (DisposableBeanAdapter.hasDestroyMethod(bean, mbd) ||
				(hasDestructionAwareBeanPostProcessors() && DisposableBeanAdapter.hasApplicableProcessors(
						bean, getBeanPostProcessorCache().destructionAware))));
	}

	/**
	 * Add the given bean to the list of disposable beans in this factory,
	 * registering its DisposableBean interface and/or the given destroy method
	 * to be called on factory shutdown (if applicable). Only applies to singletons.
	 * @param beanName the name of the bean
	 * @param bean the bean instance
	 * @param mbd the bean definition for the bean
	 * @see RootBeanDefinition#isSingleton
	 * @see RootBeanDefinition#getDependsOn
	 * @see #registerDisposableBean
	 * @see #registerDependentBean
	 * 注：参考--> https://blog.csdn.net/Weixiaohuai/article/details/122093896
	 */
	protected void registerDisposableBeanIfNecessary(String beanName, Object bean, RootBeanDefinition mbd) {
		AccessControlContext acc = (System.getSecurityManager() != null ? getAccessControlContext() : null);
		// 注：如果bean的作用域不是prototype，且bean需要在关闭时进行销毁
		if (!mbd.isPrototype() && requiresDestruction(bean, mbd)) {
			// 注：如果bean的作用域是singleton，则会注册用于销毁的bean到disposableBeans缓存，执行给定bean的所有销毁工作
			if (mbd.isSingleton()) {
				// Register a DisposableBean implementation that performs all destruction
				// work for the given bean: DestructionAwareBeanPostProcessors,
				// DisposableBean interface, custom destroy method.

				registerDisposableBean(beanName, new DisposableBeanAdapter(
						bean, beanName, mbd, getBeanPostProcessorCache().destructionAware, acc));
			}
			else {
				// A bean with a custom scope...
				// 注：如果bean的作用域不是prototype、也不是singleton，而是其他作自定义用域的话，则注册一个回调，以在销毁作用域内的指定对象时执行
				Scope scope = this.scopes.get(mbd.getScope());
				if (scope == null) {
					throw new IllegalStateException("No Scope registered for scope name '" + mbd.getScope() + "'");
				}
				scope.registerDestructionCallback(beanName, new DisposableBeanAdapter(
						bean, beanName, mbd, getBeanPostProcessorCache().destructionAware, acc));
			}
		}
	}


	//---------------------------------------------------------------------
	// Abstract methods to be implemented by subclasses
	//---------------------------------------------------------------------

	/**
	 * Check if this bean factory contains a bean definition with the given name.
	 * Does not consider any hierarchy this factory may participate in.
	 * Invoked by {@code containsBean} when no cached singleton instance is found.
	 * <p>Depending on the nature of the concrete bean factory implementation,
	 * this operation might be expensive (for example, because of directory lookups
	 * in external registries). However, for listable bean factories, this usually
	 * just amounts to a local hash lookup: The operation is therefore part of the
	 * public interface there. The same implementation can serve for both this
	 * template method and the public interface method in that case.
	 * @param beanName the name of the bean to look for
	 * @return if this bean factory contains a bean definition with the given name
	 * @see #containsBean
	 * @see org.springframework.beans.factory.ListableBeanFactory#containsBeanDefinition
	 */
	protected abstract boolean containsBeanDefinition(String beanName);

	/**
	 * Return the bean definition for the given bean name.
	 * Subclasses should normally implement caching, as this method is invoked
	 * by this class every time bean definition metadata is needed.
	 * 注：通过当前方法返回指定beanName对应注册的的bean定义信息
	 * 子类，即具体工厂实现类应该设计缓存的方式快速支持当前查询接口，因为每次需要bean定义元数据时就会调用该方法。
	 * - 这就说明bean工厂必须具备管理bean定义的能力，也肯定会继承BeanDefinitionRegistry接口支持bean定义注册。如DefaultListableBeanFactory
	 * - 【疑问】BeanDefinitionRegistry接口已经bean定义的注册、读取、判断接口。而AbstractBeanFactory也定义了getBeanDefinition，是否冲突？
	 * 	  有点冲突。AbstractBeanFactory既然需要读取bean定义，那必然需要注册，这些都是BeanDefinitionRegistry该干的。
	 * 	  AbstractBeanFactory因为模版模式强依赖读取，那么AbstractBeanFactory是否要继承BeanDefinitionRegistry接口更好一些。
	 * <p>Depending on the nature of the concrete bean factory implementation,
	 * this operation might be expensive (for example, because of directory lookups
	 * in external registries). However, for listable bean factories, this usually
	 * just amounts to a local hash lookup: The operation is therefore part of the
	 * public interface there. The same implementation can serve for both this
	 * template method and the public interface method in that case.
	 * 注：由于不同具体Bean工厂的实现有所不同，该方法可能实现逻辑十分复杂，比如需要考虑在外部注册器查找目录。
	 * 然而，在普通可查询bean工厂中，这个方法的实现逻辑相当于本地hash查找操作。
	 * 因此，这部分操作是公共获取bean定义方法的一部分。并且模版方法和公有接口方法可以使用一个实现逻辑。
	 * 公有接口：ConfigurableListableBeanFactory#getBeanDefinition
	 * - 【疑问】bean工厂必依赖于bean定义，为什么不继承bean定义注册中心呢？
	 * @param beanName the name of the bean to find a definition for
	 * @return the BeanDefinition for this prototype name (never {@code null})
	 * @throws org.springframework.beans.factory.NoSuchBeanDefinitionException
	 * if the bean definition cannot be resolved
	 * @throws BeansException in case of errors
	 * @see RootBeanDefinition
	 * @see ChildBeanDefinition
	 * @see org.springframework.beans.factory.config.ConfigurableListableBeanFactory#getBeanDefinition
	 */
	protected abstract BeanDefinition getBeanDefinition(String beanName) throws BeansException;

	/**
	 * Create a bean instance for the given merged bean definition (and arguments).
	 * The bean definition will already have been merged with the parent definition
	 * in case of a child definition.
	 * <p>All bean retrieval methods delegate to this method for actual bean creation.
	 * 注：根据给定的合并后的bean定义以及参数(可能存在)创建一个bean实例。
	 * - 这里的bean定义为已经合并后的bean定义。
	 * - 所有需要从bean工厂获取bean实例的方法都会通过该方法来实际创建bean实例。
	 * - 目前该方法只有AbstractAutowireCapableBeanFactory#createBean实现。
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @param args explicit arguments to use for constructor or factory method invocation
	 * @return a new instance of the bean
	 * @throws BeanCreationException if the bean could not be created
	 * @see AbstractAutowireCapableBeanFactory#createBean(String, RootBeanDefinition, Object[])
	 */
	protected abstract Object createBean(String beanName, RootBeanDefinition mbd, @Nullable Object[] args)
			throws BeanCreationException;


	/**
	 * CopyOnWriteArrayList which resets the beanPostProcessorCache field on modification.
	 *
	 * @since 5.3
	 */
	private class BeanPostProcessorCacheAwareList extends CopyOnWriteArrayList<BeanPostProcessor> {

		@Override
		public BeanPostProcessor set(int index, BeanPostProcessor element) {
			BeanPostProcessor result = super.set(index, element);
			beanPostProcessorCache = null;
			return result;
		}

		@Override
		public boolean add(BeanPostProcessor o) {
			boolean success = super.add(o);
			beanPostProcessorCache = null;
			return success;
		}

		@Override
		public void add(int index, BeanPostProcessor element) {
			super.add(index, element);
			beanPostProcessorCache = null;
		}

		@Override
		public BeanPostProcessor remove(int index) {
			BeanPostProcessor result = super.remove(index);
			beanPostProcessorCache = null;
			return result;
		}

		@Override
		public boolean remove(Object o) {
			boolean success = super.remove(o);
			if (success) {
				beanPostProcessorCache = null;
			}
			return success;
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			boolean success = super.removeAll(c);
			if (success) {
				beanPostProcessorCache = null;
			}
			return success;
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			boolean success = super.retainAll(c);
			if (success) {
				beanPostProcessorCache = null;
			}
			return success;
		}

		@Override
		public boolean addAll(Collection<? extends BeanPostProcessor> c) {
			boolean success = super.addAll(c);
			if (success) {
				beanPostProcessorCache = null;
			}
			return success;
		}

		@Override
		public boolean addAll(int index, Collection<? extends BeanPostProcessor> c) {
			boolean success = super.addAll(index, c);
			if (success) {
				beanPostProcessorCache = null;
			}
			return success;
		}

		@Override
		public boolean removeIf(Predicate<? super BeanPostProcessor> filter) {
			boolean success = super.removeIf(filter);
			if (success) {
				beanPostProcessorCache = null;
			}
			return success;
		}

		@Override
		public void replaceAll(UnaryOperator<BeanPostProcessor> operator) {
			super.replaceAll(operator);
			beanPostProcessorCache = null;
		}
	};


	/**
	 * Internal cache of pre-filtered post-processors.
	 * 注：用于可提前过滤的内部后置处理器缓存【四种特殊的】
	 * @since 5.3
	 */
	static class BeanPostProcessorCache {

		final List<InstantiationAwareBeanPostProcessor> instantiationAware = new ArrayList<>();

		final List<SmartInstantiationAwareBeanPostProcessor> smartInstantiationAware = new ArrayList<>();

		final List<DestructionAwareBeanPostProcessor> destructionAware = new ArrayList<>();

		final List<MergedBeanDefinitionPostProcessor> mergedDefinition = new ArrayList<>();
	}

}
