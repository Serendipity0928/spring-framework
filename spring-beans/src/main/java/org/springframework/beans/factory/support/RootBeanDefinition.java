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

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * A root bean definition represents the merged bean definition that backs
 * a specific bean in a Spring BeanFactory at runtime. It might have been created
 * from multiple original bean definitions that inherit from each other,
 * typically registered as {@link GenericBeanDefinition GenericBeanDefinitions}.
 * A root bean definition is essentially the 'unified' bean definition view at runtime.
 * 注：RootBeanDefinition表示已合并后的bean定义，由spring的bean工厂在运行时生成并返回。
 * 根bean定义，或者说是合并后的bean定义，可能是由多种相互具备某种继承关系的原始bean定义创建而来。
 * 原始bean定义往往以GenericBeanDefinition(通用bean定义)来注册到bean工厂中。相比之下，
 * 根bean定义是一个在运行时基本的、“统一”的Bean定义类型。
 *
 * <p>Root bean definitions may also be used for registering individual bean definitions
 * in the configuration phase. However, since Spring 2.5, the preferred way to register
 * bean definitions programmatically is the {@link GenericBeanDefinition} class.
 * GenericBeanDefinition has the advantage that it allows to dynamically define
 * parent dependencies, not 'hard-coding' the role as a root bean definition.
 * 注：根bean定义可能也会在spring配置阶段，作为独立的bean定义注册到bean工厂中（这也是前面“统一”加双引号的原因）。
 * 然而，自Spring2.5始，更加推荐用户使用GenericBeanDefinition类型作为编程注册bean定义的类型。
 * 这是因为GenericBeanDefinition具有一些优势。比如其允许动态的定义多个父依赖，而不是向根bean定义一样通过角色硬编码。
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @see GenericBeanDefinition
 * @see ChildBeanDefinition
 */
@SuppressWarnings("serial")
public class RootBeanDefinition extends AbstractBeanDefinition {

	/**
	 * 注：包装bean定义，包括beanName以及其别名
	 */
	@Nullable
	private BeanDefinitionHolder decoratedDefinition;

	// 注：JDK原生注解类用于保存bean的注解信息
	@Nullable
	private AnnotatedElement qualifiedElement;

	/** Determines if the definition needs to be re-merged.
	 * 注：判断当前合并后的bean是否过时，并且需要重新合并
	 * */
	volatile boolean stale;

	// 注：标识是否允许缓存
	boolean allowCaching = true;

	// 注：标识bean的工厂方法是否单一
	boolean isFactoryMethodUnique;

	// 注：解析后的目标类型
	@Nullable
	volatile ResolvableType targetType;

	/** Package-visible field for caching the determined Class of a given bean definition.
	 * 注：用于缓存bean定义确定的类型Class，权限为包可见(下同)
	 * 延迟初始化属性，后续会在工厂判断bean类型时解析bean实例的类型，然后在一定情况下缓存最终类型
	 * determineTargetType
	 * */
	@Nullable
	volatile Class<?> resolvedTargetType;

	/** Package-visible field for caching if the bean is a factory bean.
	 * 注：是否当前bean为FactoryBean;
	 * - 后续会根据factoryMethodName属性来推断返回类型，进而判断是否为FactoryBean
	 * - 创建并初始化bean实例后，会检查当前bean是否FactoryBean，是则设置该属性为true；见：AbstractBeanFactory#getObjectForBeanInstance
	 * */
	@Nullable
	volatile Boolean isFactoryBean;

	/** Package-visible field for caching the return type of a generically typed factory method.
	 * 注：当前bean的工厂方法返回的类型
	 * 延迟初始化属性，后续factoryMethodName属性来推断返回类型，进而判断是否为FactoryBean
	 * getTypeForFactoryMethod、resolveBeanClass两种地方都会缓存这个最终方法。
	 * */
	@Nullable
	volatile ResolvableType factoryMethodReturnType;

	/** Package-visible field for caching a unique factory method candidate for introspection.
	 * 注：用于缓存用于自查工厂方法的候选者
	 * 非延迟初始化属性；
	 * 疑问：普通类就有？有时间研究下面的方法逻辑
	 * @see AbstractAutowireCapableBeanFactory#getTypeForFactoryMethod(java.lang.String, org.springframework.beans.factory.support.RootBeanDefinition, java.lang.Class[])
	 * */
	@Nullable
	volatile Method factoryMethodToIntrospect;

	/** Common lock for the four constructor fields below.
	 * 注：下面四个构造器属性的共同锁
	 * */
	final Object constructorArgumentLock = new Object();

	/** Package-visible field for caching the resolved constructor or factory method.
	 * 注：缓存已解析的构造器或工厂方法
	 * Executable是JDK中Method、Constructor类型的父类
	 * */
	@Nullable
	Executable resolvedConstructorOrFactoryMethod;

	/** Package-visible field that marks the constructor arguments as resolved.
	 * 注：标识构造器参数是否解析完毕
	 * */
	boolean constructorArgumentsResolved = false;

	/** Package-visible field for caching fully resolved constructor arguments.
	 * 注：缓存所有已解析的构造器参数
	 * */
	@Nullable
	Object[] resolvedConstructorArguments;

	/** Package-visible field for caching partly prepared constructor arguments.
	 * 注：缓存部分已准备好的构造器参数
	 * */
	@Nullable
	Object[] preparedConstructorArguments;

	/** Common lock for the two post-processing fields below.
	 * 注：用于下面两个后置处理器标识的同步锁
	 * */
	final Object postProcessingLock = new Object();

	/** Package-visible field that indicates MergedBeanDefinitionPostProcessor having been applied.
	 * 注：用于表示合并bean定义后置处理器是否已经处理【处理阶段，实例化之后】
	 * */
	boolean postProcessed = false;

	/** Package-visible field that indicates a before-instantiation post-processor having kicked in.
	 * 注：用于表示在实例化之前的后置处理器(InstantiationAwareBeanPostProcessor)是否已经处理并解析到实例
	 * */
	@Nullable
	volatile Boolean beforeInstantiationResolved;

	// 注：缓存一些注解需要注入的属性值
	@Nullable
	private Set<Member> externallyManagedConfigMembers;

	// 注：注册外部的初始化方法
	@Nullable
	private Set<String> externallyManagedInitMethods;

	// 注：注册外部的销毁方法
	@Nullable
	private Set<String> externallyManagedDestroyMethods;


	/**
	 * Create a new RootBeanDefinition, to be configured through its bean
	 * properties and configuration methods.
	 * 注：无参构造初始化根bean定义
	 * @see #setBeanClass
	 * @see #setScope
	 * @see #setConstructorArgumentValues
	 * @see #setPropertyValues
	 */
	public RootBeanDefinition() {
		super();
	}

	/**
	 * Create a new RootBeanDefinition for a singleton.
	 * 创建一个指定类型的根定义，一般是单例。
	 * @param beanClass the class of the bean to instantiate
	 * @see #setBeanClass
	 */
	public RootBeanDefinition(@Nullable Class<?> beanClass) {
		super();
		setBeanClass(beanClass);
	}

	/**
	 * Create a new RootBeanDefinition for a singleton bean, constructing each instance
	 * through calling the given supplier (possibly a lambda or method reference).
	 *创建一个单例bean的根bean定义。通过调用传进来的supplier逻辑(可能是lambda表示或者方法引用)来创建实例。
	 * @param beanClass the class of the bean to instantiate
	 * @param instanceSupplier the supplier to construct a bean instance,
	 * as an alternative to a declaratively specified factory method
	 * @since 5.0
	 * @see #setInstanceSupplier
	 */
	public <T> RootBeanDefinition(@Nullable Class<T> beanClass, @Nullable Supplier<T> instanceSupplier) {
		super();
		setBeanClass(beanClass);
		setInstanceSupplier(instanceSupplier);
	}

	/**
	 * Create a new RootBeanDefinition for a scoped bean, constructing each instance
	 * through calling the given supplier (possibly a lambda or method reference).
	 * 创建一个指定作用于的单例bean，并且提供bean实例化逻辑。
	 * @param beanClass the class of the bean to instantiate
	 * @param scope the name of the corresponding scope
	 * @param instanceSupplier the supplier to construct a bean instance,
	 * as an alternative to a declaratively specified factory method
	 * @since 5.0
	 * @see #setInstanceSupplier
	 */
	public <T> RootBeanDefinition(@Nullable Class<T> beanClass, String scope, @Nullable Supplier<T> instanceSupplier) {
		super();
		setBeanClass(beanClass);
		setScope(scope);
		setInstanceSupplier(instanceSupplier);
	}

	/**
	 * Create a new RootBeanDefinition for a singleton,
	 * using the given autowire mode.
	 * 创建一个指定自动注入模式的单例bean定义。
	 * 并决定是否要进行依赖检查，对于构造器装配模式，不需要依赖检查，即忽略。
	 * @param beanClass the class of the bean to instantiate
	 * @param autowireMode by name or type, using the constants in this interface
	 * @param dependencyCheck whether to perform a dependency check for objects
	 * (not applicable to autowiring a constructor, thus ignored there)
	 */
	public RootBeanDefinition(@Nullable Class<?> beanClass, int autowireMode, boolean dependencyCheck) {
		super();
		setBeanClass(beanClass);
		setAutowireMode(autowireMode);
		if (dependencyCheck && getResolvedAutowireMode() != AUTOWIRE_CONSTRUCTOR) {
			setDependencyCheck(DEPENDENCY_CHECK_OBJECTS);
		}
	}

	/**
	 * Create a new RootBeanDefinition for a singleton,
	 * providing constructor arguments and property values.
	 * 注：创建一个指定构造器参数以及属性值的单例bean定义
	 * @param beanClass the class of the bean to instantiate
	 * @param cargs the constructor argument values to apply
	 * @param pvs the property values to apply
	 */
	public RootBeanDefinition(@Nullable Class<?> beanClass, @Nullable ConstructorArgumentValues cargs,
			@Nullable MutablePropertyValues pvs) {

		super(cargs, pvs);
		setBeanClass(beanClass);
	}

	/**
	 * Create a new RootBeanDefinition for a singleton,
	 * providing constructor arguments and property values.
	 * <p>Takes a bean class name to avoid eager loading of the bean class.
	 * 注：创建一个指定bean类型名的单例bean。
	 * 之所有使用类名是为了避免过早的加载该类。
	 * @param beanClassName the name of the class to instantiate
	 */
	public RootBeanDefinition(String beanClassName) {
		setBeanClassName(beanClassName);
	}

	/**
	 * Create a new RootBeanDefinition for a singleton,
	 * providing constructor arguments and property values.
	 * <p>Takes a bean class name to avoid eager loading of the bean class.
	 * 注：创建一个指定构造器参数以及属性值的单例bean定义。
	 * 之所有使用类名是为了避免过早的加载该类。
	 * @param beanClassName the name of the class to instantiate
	 * @param cargs the constructor argument values to apply
	 * @param pvs the property values to apply
	 */
	public RootBeanDefinition(String beanClassName, ConstructorArgumentValues cargs, MutablePropertyValues pvs) {
		super(cargs, pvs);
		setBeanClassName(beanClassName);
	}

	/**
	 * Create a new RootBeanDefinition as deep copy of the given
	 * bean definition.
	 * 注：通过已有的根bean定义来创建一个新的根bean定义。-相当于深拷贝
	 * @param original the original bean definition to copy from
	 */
	public RootBeanDefinition(RootBeanDefinition original) {
		super(original);
		this.decoratedDefinition = original.decoratedDefinition;
		this.qualifiedElement = original.qualifiedElement;
		this.allowCaching = original.allowCaching;
		this.isFactoryMethodUnique = original.isFactoryMethodUnique;
		this.targetType = original.targetType;
		this.factoryMethodToIntrospect = original.factoryMethodToIntrospect;
	}

	/**
	 * Create a new RootBeanDefinition as deep copy of the given
	 * bean definition.
	 * 注：通过已有的bean定义来创建一个新的根bean定义。
	 * - 这个无法拷贝AbstractBeanDefinition以下类型的数据了。
	 * - 在合并bean的时，父bean定义会使用这个构造器重新创建一个，这也意味着合并bean是指处理AbstractBeanDefinition数据即可。
	 * @param original the original bean definition to copy from
	 */
	RootBeanDefinition(BeanDefinition original) {
		super(original);
	}


	// 注：返回当前bean定义的父定义（肯定为null）
	@Override
	public String getParentName() {
		return null;
	}

	// 注：设置当前bean定义的父定义名（肯定不允许设置）
	@Override
	public void setParentName(@Nullable String parentName) {
		if (parentName != null) {
			throw new IllegalArgumentException("Root bean cannot be changed into a child bean with parent reference");
		}
	}

	/**
	 * Register a target definition that is being decorated by this bean definition.
	 * 注：注册被当前根bean定义装饰的目标定义
	 */
	public void setDecoratedDefinition(@Nullable BeanDefinitionHolder decoratedDefinition) {
		this.decoratedDefinition = decoratedDefinition;
	}

	/**
	 * Return the target definition that is being decorated by this bean definition, if any.
	 * 注：返回当前bean定义装饰的目标bean定义
	 */
	@Nullable
	public BeanDefinitionHolder getDecoratedDefinition() {
		return this.decoratedDefinition;
	}

	/**
	 * Specify the {@link AnnotatedElement} defining qualifiers,
	 * to be used instead of the target class or factory method.
	 * 注：设置定义的注解信息
	 * @since 4.3.3
	 * @see #setTargetType(ResolvableType)
	 * @see #getResolvedFactoryMethod()
	 */
	public void setQualifiedElement(@Nullable AnnotatedElement qualifiedElement) {
		this.qualifiedElement = qualifiedElement;
	}

	/**
	 * Return the {@link AnnotatedElement} defining qualifiers, if any.
	 * Otherwise, the factory method and target class will be checked.
	 * 注：返回注解信息
	 * @since 4.3.3
	 */
	@Nullable
	public AnnotatedElement getQualifiedElement() {
		return this.qualifiedElement;
	}

	/**
	 * Specify a generics-containing target type of this bean definition, if known in advance.
	 * 注：如果提前知道当前bean定义实例化后的类型(通用包装)，通过这个方法设置
	 * @since 4.3.3
	 */
	public void setTargetType(ResolvableType targetType) {
		this.targetType = targetType;
	}

	/**
	 * Specify the target type of this bean definition, if known in advance.
	 * 注：如果提前知道当前bean定义实例化后的类型，通过这个方法设置
	 * @since 3.2.2
	 */
	public void setTargetType(@Nullable Class<?> targetType) {
		this.targetType = (targetType != null ? ResolvableType.forClass(targetType) : null);
	}

	/**
	 * Return the target type of this bean definition, if known
	 * (either specified in advance or resolved on first instantiation).
	 * 注：返回当前bean定义解析后的最终类型
	 * @since 3.2.2
	 */
	@Nullable
	public Class<?> getTargetType() {
		if (this.resolvedTargetType != null) {
			return this.resolvedTargetType;
		}
		ResolvableType targetType = this.targetType;
		return (targetType != null ? targetType.resolve() : null);
	}

	/**
	 * Return a {@link ResolvableType} for this bean definition,
	 * either from runtime-cached type information or from configuration-time
	 * {@link #setTargetType(ResolvableType)} or {@link #setBeanClass(Class)},
	 * also considering resolved factory method definitions.
	 * 注：返回当前bean定义的可解析类型。
	 * 具体实现就是使用ResolvableType包装下bean的Class对象
	 * @since 5.1
	 * @see #setTargetType(ResolvableType)
	 * @see #setBeanClass(Class)
	 * @see #setResolvedFactoryMethod(Method)
	 */
	@Override
	public ResolvableType getResolvableType() {
		ResolvableType targetType = this.targetType;
		if (targetType != null) {
			return targetType;
		}
		ResolvableType returnType = this.factoryMethodReturnType;
		if (returnType != null) {
			return returnType;
		}
		Method factoryMethod = this.factoryMethodToIntrospect;
		if (factoryMethod != null) {
			return ResolvableType.forMethodReturnType(factoryMethod);
		}
		return super.getResolvableType();
	}

	/**
	 * Determine preferred constructors to use for default construction, if any.
	 * Constructor arguments will be autowired if necessary.
	 * 注：决定应该使用哪个默认构造器
	 * @return one or more preferred constructors, or {@code null} if none
	 * (in which case the regular no-arg default constructor will be called)
	 * @since 5.1
	 */
	@Nullable
	public Constructor<?>[] getPreferredConstructors() {
		return null;
	}

	/**
	 * Specify a factory method name that refers to a non-overloaded method.
	 * 注：指定一个不会被重载的工厂方法
	 */
	public void setUniqueFactoryMethodName(String name) {
		Assert.hasText(name, "Factory method name must not be empty");
		setFactoryMethodName(name);
		this.isFactoryMethodUnique = true;
	}

	/**
	 * Specify a factory method name that refers to an overloaded method.
	 * 注：指定一个会被重载的工厂方法
	 * @since 5.2
	 */
	public void setNonUniqueFactoryMethodName(String name) {
		Assert.hasText(name, "Factory method name must not be empty");
		setFactoryMethodName(name);
		this.isFactoryMethodUnique = false;
	}

	/**
	 * Check whether the given candidate qualifies as a factory method.
	 * 注：检查指定的方法是否是工厂方法
	 */
	public boolean isFactoryMethod(Method candidate) {
		return candidate.getName().equals(getFactoryMethodName());
	}

	/**
	 * Set a resolved Java Method for the factory method on this bean definition.
	 * 注：设置一个已解析的java方法作为当前bean定义的工厂方法
	 * @param method the resolved factory method, or {@code null} to reset it
	 * @since 5.2
	 */
	public void setResolvedFactoryMethod(@Nullable Method method) {
		this.factoryMethodToIntrospect = method;
	}

	/**
	 * Return the resolved factory method as a Java Method object, if available.
	 * 返回已解析的工厂方法
	 * @return the factory method, or {@code null} if not found or not resolved yet
	 */
	@Nullable
	public Method getResolvedFactoryMethod() {
		return this.factoryMethodToIntrospect;
	}

	// 注：注册外部的配置成员
	public void registerExternallyManagedConfigMember(Member configMember) {
		synchronized (this.postProcessingLock) {
			if (this.externallyManagedConfigMembers == null) {
				this.externallyManagedConfigMembers = new HashSet<>(1);
			}
			this.externallyManagedConfigMembers.add(configMember);
		}
	}

	// 注：判断指定的成员类是否为当前bean定义的外部配置成员
	public boolean isExternallyManagedConfigMember(Member configMember) {
		synchronized (this.postProcessingLock) {
			return (this.externallyManagedConfigMembers != null &&
					this.externallyManagedConfigMembers.contains(configMember));
		}
	}

	// 注：注册外部的初始化方法
	public void registerExternallyManagedInitMethod(String initMethod) {
		synchronized (this.postProcessingLock) {
			if (this.externallyManagedInitMethods == null) {
				this.externallyManagedInitMethods = new HashSet<>(1);
			}
			this.externallyManagedInitMethods.add(initMethod);
		}
	}

	// 注：判断指定的成员类是否为当前bean定义的初始化方法
	public boolean isExternallyManagedInitMethod(String initMethod) {
		synchronized (this.postProcessingLock) {
			return (this.externallyManagedInitMethods != null &&
					this.externallyManagedInitMethods.contains(initMethod));
		}
	}

	// 注：注册外部的销毁方法
	public void registerExternallyManagedDestroyMethod(String destroyMethod) {
		synchronized (this.postProcessingLock) {
			if (this.externallyManagedDestroyMethods == null) {
				this.externallyManagedDestroyMethods = new HashSet<>(1);
			}
			this.externallyManagedDestroyMethods.add(destroyMethod);
		}
	}

	// 注：判断指定的成员类是否为当前bean定义的销毁方法
	public boolean isExternallyManagedDestroyMethod(String destroyMethod) {
		synchronized (this.postProcessingLock) {
			return (this.externallyManagedDestroyMethods != null &&
					this.externallyManagedDestroyMethods.contains(destroyMethod));
		}
	}


	// 注：拷贝方法就是利用其拷贝构造器
	@Override
	public RootBeanDefinition cloneBeanDefinition() {
		return new RootBeanDefinition(this);
	}

	@Override
	public boolean equals(@Nullable Object other) {
		return (this == other || (other instanceof RootBeanDefinition && super.equals(other)));
	}

	@Override
	public String toString() {
		return "Root bean: " + super.toString();
	}

}
