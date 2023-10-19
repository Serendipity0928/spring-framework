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

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.beans.BeanMetadataAttributeAccessor;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.DescriptiveResource;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * Base class for concrete, full-fledged {@link BeanDefinition} classes,
 * factoring out common properties of {@link GenericBeanDefinition},
 * {@link RootBeanDefinition}, and {@link ChildBeanDefinition}.
 * 注：AbstractBeanDefinition是一个具体得、完整的Bean定义的抽象基类，
 * 并将其三个具体子类的共有属性分解并定义在该抽象基类中。
 *
 * <p>The autowire constants match the ones defined in the
 * {@link org.springframework.beans.factory.config.AutowireCapableBeanFactory}
 * interface.
 * bean定义中涉及到自动装配的属性，其涉及到的常量定义在AutowireCapableBeanFactory接口中。
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Mark Fisher
 * @see GenericBeanDefinition
 * @see RootBeanDefinition
 * @see ChildBeanDefinition
 * 注：参考：https://mp.weixin.qq.com/s/ufa8BYlnWxaMXst1rypuNA
 */
@SuppressWarnings("serial")
public abstract class AbstractBeanDefinition extends BeanMetadataAttributeAccessor
		implements BeanDefinition, Cloneable {
	/**
	 * 注：抽象基类继承了BeanMetadataAttributeAccessor类，具备bean元数据属性访问以及获取配置源的能力。
	 * BeanDefinition接口实际上实现的接口(AttributeAccessor, BeanMetadataElement)均已经被BeanMetadataAttributeAccessor实现了，
	 * 因此，bean定义抽象基类中只需要实现BeanDefinition接口本身定义的相关方法即可。
	 */

	/**
	 * Constant for the default scope name: {@code ""}, equivalent to singleton
	 * status unless overridden from a parent bean definition (if applicable).
	 * 注：bean的默认作用范围。如果不存在父bean定义，默认空等价于单例；否则，跟随父bean定义。
	 */
	public static final String SCOPE_DEFAULT = "";

	/**
	 * Constant that indicates no external autowiring at all.
	 * 注：自动注入模式-不会记进行外部的bean注入
	 * @see #setAutowireMode
	 */
	public static final int AUTOWIRE_NO = AutowireCapableBeanFactory.AUTOWIRE_NO;

	/**
	 * Constant that indicates autowiring bean properties by name.
	 * 注：自动注入模式-按照bean名称进行注入
	 * @see #setAutowireMode
	 */
	public static final int AUTOWIRE_BY_NAME = AutowireCapableBeanFactory.AUTOWIRE_BY_NAME;

	/**
	 * Constant that indicates autowiring bean properties by type.
	 * 注：自动注入模式-按照bean类型进行注入
	 * @see #setAutowireMode
	 */
	public static final int AUTOWIRE_BY_TYPE = AutowireCapableBeanFactory.AUTOWIRE_BY_TYPE;

	/**
	 * Constant that indicates autowiring a constructor.
	 * 注：自动注入模式-自动注入合适的构造器
	 * @see #setAutowireMode
	 */
	public static final int AUTOWIRE_CONSTRUCTOR = AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR;

	/**
	 * Constant that indicates determining an appropriate autowire strategy
	 * through introspection of the bean class.
	 * 注：自动注入模式-根据Bean类型信息来决定自动注入策略
	 * @see #setAutowireMode
	 * @deprecated as of Spring 3.0: If you are using mixed autowiring strategies,
	 * use annotation-based autowiring for clearer demarcation of autowiring needs.
	 */
	@Deprecated
	public static final int AUTOWIRE_AUTODETECT = AutowireCapableBeanFactory.AUTOWIRE_AUTODETECT;

	/**
	 * Constant that indicates no dependency check at all.
	 * 注：属性检查模式-不进行依赖检查
	 * @see #setDependencyCheck
	 */
	public static final int DEPENDENCY_CHECK_NONE = 0;

	/**
	 * Constant that indicates dependency checking for object references.
	 * 注：属性检查模式-仅对依赖的对象类型进行检查
	 * @see #setDependencyCheck
	 */
	public static final int DEPENDENCY_CHECK_OBJECTS = 1;

	/**
	 * Constant that indicates dependency checking for "simple" properties.
	 * 注：属性检查模式-仅对原始的基本类型（String\集合类\基本类型）进行检查
	 * @see #setDependencyCheck
	 * @see org.springframework.beans.BeanUtils#isSimpleProperty
	 */
	public static final int DEPENDENCY_CHECK_SIMPLE = 2;

	/**
	 * Constant that indicates dependency checking for all properties
	 * (object references as well as "simple" properties).
	 * 注：属性检查模式-对所有依赖的属性都会进行检查
	 * @see #setDependencyCheck
	 */
	public static final int DEPENDENCY_CHECK_ALL = 3;

	/**
	 * Constant that indicates the container should attempt to infer the
	 * {@link #setDestroyMethodName destroy method name} for a bean as opposed to
	 * explicit specification of a method name. The value {@value} is specifically
	 * designed to include characters otherwise illegal in a method name, ensuring
	 * no possibility of collisions with legitimately named methods having the same
	 * name.
	 * 注：当用户没有指定destroyMethod属性时，容器会尝试推断销毁方法。
	 * <p>Currently, the method names detected during destroy method inference
	 * are "close" and "shutdown", if present on the specific bean class.
	 * 注：目前，spring会检查当前bean对象的类型是否存在名为"close"或者"shutdown"方法。
	 */
	public static final String INFER_METHOD = "(inferred)";

	// 注：bean定义对应的class或类名
	@Nullable
	private volatile Object beanClass;

	// 注：bean的作用域信息；一般有单例、原型；在web场景还有请求、会话等。
	@Nullable
	private String scope = SCOPE_DEFAULT;

	// 注：标识当前bean是否为抽象类-即不可实例化
	private boolean abstractFlag = false;

	// 注：当前bean是否是懒加载
	@Nullable
	private Boolean lazyInit;

	// 注：当前bean的自动装配模式
	private int autowireMode = AUTOWIRE_NO;

	// 注：当前bean的依赖检查模式
	private int dependencyCheck = DEPENDENCY_CHECK_NONE;

	// 注：当前bean依赖的其他bean-用于控制bean的初始化顺序
	@Nullable
	private String[] dependsOn;

	// 注：标识当前bean实例是否可作为自动注入的候选人。默认是都可以自动注入
	private boolean autowireCandidate = true;

	// 注：如果自动注入的候选bean有多个就会抛出异常，可以指定其中一个为主候选bean，将会在自动装配时优先考虑。
	private boolean primary = false;

	// 注：用于保存bean属性的qualifiers ...
	private final Map<String, AutowireCandidateQualifier> qualifiers = new LinkedHashMap<>();

	// 注：提供一个bean的初始化逻辑，而不是通过构造函数或工厂方法
	@Nullable
	private Supplier<?> instanceSupplier;

	// 注：是否允许访问非公共的方法
	private boolean nonPublicAccessAllowed = true;

	// 注：解析构造器类型时是否宽松类型校验
	private boolean lenientConstructorResolution = true;

	// 注：当前bean的工厂bean名称。工厂bean本身是一个特殊的bean，通过工厂bean，可以创建出一个指定类型的bean
	@Nullable
	private String factoryBeanName;

	// 注：工厂方法的名称
	@Nullable
	private String factoryMethodName;

	// 注：用于记录构造器属性值
	@Nullable
	private ConstructorArgumentValues constructorArgumentValues;

	// 注：用来记录普通属性的属性和值。这些属性的值在复制给bean之前是可以修改的
	@Nullable
	private MutablePropertyValues propertyValues;

	/**
	 * 注：用于记录哪些方法被复写了
	 * lookup-method、replaced-method标签指定要覆盖的方法名会设置到该缓存汇总
	 */
	private MethodOverrides methodOverrides = new MethodOverrides();

	// 初始化bean方法，即init-method指定的方法
	@Nullable
	private String initMethodName;

	// 销毁bean的方法，即destroy-method指定的方法
	@Nullable
	private String destroyMethodName;

	// 注：是否执行初始化方法
	private boolean enforceInitMethod = true;

	// 注：是否执行销毁方法
	private boolean enforceDestroyMethod = true;

	// 注：是否为合成类型的bean。比如aop代理生成的代理类即是合成的
	private boolean synthetic = false;

	// 注：bean的角色标识，默认是用户定义的应用bean。其它角色比如：内部基础bean，配置支持bean
	private int role = BeanDefinition.ROLE_APPLICATION;

	// 注：bean定义的描述信息
	@Nullable
	private String description;

	// 注：bean定义的资源
	@Nullable
	private Resource resource;


	/**
	 * Create a new AbstractBeanDefinition with default settings.
	 * 注：无参构造-不初始化任何bean定义信息
	 */
	protected AbstractBeanDefinition() {
		this(null, null);	// 注：完全都没必要调用，之前是因为需要设置默认值
	}

	/**
	 * Create a new AbstractBeanDefinition with the given
	 * constructor argument values and property values.
	 * 注：初始化构造器属性值以及其他属性值，两方面bean信息
	 */
	protected AbstractBeanDefinition(@Nullable ConstructorArgumentValues cargs, @Nullable MutablePropertyValues pvs) {
		this.constructorArgumentValues = cargs;
		this.propertyValues = pvs;
	}

	/**
	 * Create a new AbstractBeanDefinition as a deep copy of the given
	 * bean definition.
	 * 注：通过指定的bean定义实例复制一份新的bean定义。
	 * @param original the original bean definition to copy from
	 */
	protected AbstractBeanDefinition(BeanDefinition original) {
		setParentName(original.getParentName());
		setBeanClassName(original.getBeanClassName());
		setScope(original.getScope());
		setAbstract(original.isAbstract());
		setFactoryBeanName(original.getFactoryBeanName());
		setFactoryMethodName(original.getFactoryMethodName());
		setRole(original.getRole());
		setSource(original.getSource());
		copyAttributesFrom(original);

		/**
		 * 注：上面拷贝了一些BeanDefinition方法相关的属性，下面根据original是否为抽象bean定义类型选择拷贝抽象相关bean信息。
		 * - 对于部分属性(beanClass、constructorArgumentValues等)，如果原Bean定义没有对应值，保持null而不设置默认值。
		 */
		if (original instanceof AbstractBeanDefinition) {
			AbstractBeanDefinition originalAbd = (AbstractBeanDefinition) original;
			if (originalAbd.hasBeanClass()) {
				setBeanClass(originalAbd.getBeanClass());
			}
			if (originalAbd.hasConstructorArgumentValues()) {
				setConstructorArgumentValues(new ConstructorArgumentValues(original.getConstructorArgumentValues()));
			}
			if (originalAbd.hasPropertyValues()) {
				setPropertyValues(new MutablePropertyValues(original.getPropertyValues()));
			}
			if (originalAbd.hasMethodOverrides()) {
				setMethodOverrides(new MethodOverrides(originalAbd.getMethodOverrides()));
			}
			Boolean lazyInit = originalAbd.getLazyInit();
			if (lazyInit != null) {
				setLazyInit(lazyInit);
			}
			setAutowireMode(originalAbd.getAutowireMode());
			setDependencyCheck(originalAbd.getDependencyCheck());
			setDependsOn(originalAbd.getDependsOn());
			setAutowireCandidate(originalAbd.isAutowireCandidate());
			setPrimary(originalAbd.isPrimary());
			copyQualifiersFrom(originalAbd);
			setInstanceSupplier(originalAbd.getInstanceSupplier());
			setNonPublicAccessAllowed(originalAbd.isNonPublicAccessAllowed());
			setLenientConstructorResolution(originalAbd.isLenientConstructorResolution());
			setInitMethodName(originalAbd.getInitMethodName());
			setEnforceInitMethod(originalAbd.isEnforceInitMethod());
			setDestroyMethodName(originalAbd.getDestroyMethodName());
			setEnforceDestroyMethod(originalAbd.isEnforceDestroyMethod());
			setSynthetic(originalAbd.isSynthetic());
			setResource(originalAbd.getResource());
		}
		else {
			// BeanDefinition方法相关属性
			setConstructorArgumentValues(new ConstructorArgumentValues(original.getConstructorArgumentValues()));
			setPropertyValues(new MutablePropertyValues(original.getPropertyValues()));
			setLazyInit(original.isLazyInit());
			setResourceDescription(original.getResourceDescription());
		}
	}


	/**
	 * Override settings in this bean definition (presumably a copied parent
	 * from a parent-child inheritance relationship) from the given bean
	 * definition (presumably the child).
	 * 注：根据指定的bean定义(可能是子bean定义)来覆盖当前的bean定义(可能是父定义)设置
	 * <ul>
	 * <li>Will override beanClass if specified in the given bean definition.
	 * <li>Will always take {@code abstract}, {@code scope},
	 * {@code lazyInit}, {@code autowireMode}, {@code dependencyCheck},
	 * and {@code dependsOn} from the given bean definition.
	 * <li>Will add {@code constructorArgumentValues}, {@code propertyValues},
	 * {@code methodOverrides} from the given bean definition to existing ones.
	 * <li>Will override {@code factoryBeanName}, {@code factoryMethodName},
	 * {@code initMethodName}, and {@code destroyMethodName} if specified
	 * in the given bean definition.
	 * </ul>
	 *
	 */
	public void overrideFrom(BeanDefinition other) {
		if (StringUtils.hasLength(other.getBeanClassName())) {
			setBeanClassName(other.getBeanClassName());
		}
		if (StringUtils.hasLength(other.getScope())) {
			setScope(other.getScope());
		}
		setAbstract(other.isAbstract());
		if (StringUtils.hasLength(other.getFactoryBeanName())) {
			setFactoryBeanName(other.getFactoryBeanName());
		}
		if (StringUtils.hasLength(other.getFactoryMethodName())) {
			setFactoryMethodName(other.getFactoryMethodName());
		}
		setRole(other.getRole());
		setSource(other.getSource());
		copyAttributesFrom(other);

		if (other instanceof AbstractBeanDefinition) {
			AbstractBeanDefinition otherAbd = (AbstractBeanDefinition) other;
			if (otherAbd.hasBeanClass()) {
				setBeanClass(otherAbd.getBeanClass());
			}
			if (otherAbd.hasConstructorArgumentValues()) {
				getConstructorArgumentValues().addArgumentValues(other.getConstructorArgumentValues());
			}
			if (otherAbd.hasPropertyValues()) {
				getPropertyValues().addPropertyValues(other.getPropertyValues());
			}
			if (otherAbd.hasMethodOverrides()) {
				getMethodOverrides().addOverrides(otherAbd.getMethodOverrides());
			}
			Boolean lazyInit = otherAbd.getLazyInit();
			if (lazyInit != null) {
				setLazyInit(lazyInit);
			}
			setAutowireMode(otherAbd.getAutowireMode());
			setDependencyCheck(otherAbd.getDependencyCheck());
			setDependsOn(otherAbd.getDependsOn());
			setAutowireCandidate(otherAbd.isAutowireCandidate());
			setPrimary(otherAbd.isPrimary());
			copyQualifiersFrom(otherAbd);
			setInstanceSupplier(otherAbd.getInstanceSupplier());
			setNonPublicAccessAllowed(otherAbd.isNonPublicAccessAllowed());
			setLenientConstructorResolution(otherAbd.isLenientConstructorResolution());
			if (otherAbd.getInitMethodName() != null) {
				setInitMethodName(otherAbd.getInitMethodName());
				setEnforceInitMethod(otherAbd.isEnforceInitMethod());
			}
			if (otherAbd.getDestroyMethodName() != null) {
				setDestroyMethodName(otherAbd.getDestroyMethodName());
				setEnforceDestroyMethod(otherAbd.isEnforceDestroyMethod());
			}
			setSynthetic(otherAbd.isSynthetic());
			setResource(otherAbd.getResource());
		}
		else {
			getConstructorArgumentValues().addArgumentValues(other.getConstructorArgumentValues());
			getPropertyValues().addPropertyValues(other.getPropertyValues());
			setLazyInit(other.isLazyInit());
			setResourceDescription(other.getResourceDescription());
		}
	}

	/**
	 * Apply the provided default values to this bean.
	 * 注：设置默认的bean定义值
	 * @param defaults the default settings to apply
	 * @since 2.5
	 */
	public void applyDefaults(BeanDefinitionDefaults defaults) {
		Boolean lazyInit = defaults.getLazyInit();
		if (lazyInit != null) {
			setLazyInit(lazyInit);
		}
		setAutowireMode(defaults.getAutowireMode());
		setDependencyCheck(defaults.getDependencyCheck());
		setInitMethodName(defaults.getInitMethodName());
		setEnforceInitMethod(false);
		setDestroyMethodName(defaults.getDestroyMethodName());
		setEnforceDestroyMethod(false);
	}


	/**
	 * Specify the bean class name of this bean definition.
	 * 注：指定当前bean的类名
	 */
	@Override
	public void setBeanClassName(@Nullable String beanClassName) {
		this.beanClass = beanClassName;
	}

	/**
	 * Return the current bean class name of this bean definition.
	 * 注：返回当前bean的类名
	 */
	@Override
	@Nullable
	public String getBeanClassName() {
		Object beanClassObject = this.beanClass;
		if (beanClassObject instanceof Class) {
			return ((Class<?>) beanClassObject).getName();
		}
		else {
			return (String) beanClassObject;
		}
	}

	/**
	 * Specify the class for this bean.
	 * 注：设置bean当前的bean的Class信息
	 * @see #setBeanClassName(String)
	 */
	public void setBeanClass(@Nullable Class<?> beanClass) {
		this.beanClass = beanClass;
	}

	/**
	 * Return the specified class of the bean definition (assuming it is resolved already).
	 * 注：返回当前已经解析后的bean定义指定的class对象
	 * <p><b>NOTE:</b> This is an initial class reference as declared in the bean metadata
	 * definition, potentially combined with a declared factory method or a
	 * {@link org.springframework.beans.factory.FactoryBean} which may lead to a different
	 * runtime type of the bean, or not being set at all in case of an instance-level
	 * factory method (which is resolved via {@link #getFactoryBeanName()} instead).
	 * 注：这是在bean定义中声明的初始类引用。bean的初始类引用可能组合了工厂方法或工厂bean，这些都会在运行时会有不同的bean类型，
	 * 甚至在实例工厂方法中可能都不会设置。
	 * <b>Do not use this for runtime type introspection of arbitrary bean definitions.</b>
	 * The recommended way to find out about the actual runtime type of a particular bean
	 * is a {@link org.springframework.beans.factory.BeanFactory#getType} call for the
	 * specified bean name; this takes all of the above cases into account and returns the
	 * type of object that a {@link org.springframework.beans.factory.BeanFactory#getBean}
	 * call is going to return for the same bean name.
	 * 注：不要使用这个方法用于任意bean定义的运行时的类型。
	 * 推荐你使用Bean工厂的getType方法来获取运行时的指定beanName实际类型。
	 * 这个方法会考虑所有上述情况，并且会返回和Bean工厂生产的bean实例的类型。
	 * @return the resolved bean class (never {@code null})
	 * @throws IllegalStateException if the bean definition does not define a bean class,
	 * or a specified bean class name has not been resolved into an actual Class yet
	 * @see #getBeanClassName()
	 * @see #hasBeanClass()
	 * @see #setBeanClass(Class)
	 * @see #resolveBeanClass(ClassLoader)
	 */
	public Class<?> getBeanClass() throws IllegalStateException {
		Object beanClassObject = this.beanClass;
		if (beanClassObject == null) {
			throw new IllegalStateException("No bean class specified on bean definition");
		}
		if (!(beanClassObject instanceof Class)) {
			throw new IllegalStateException(
					"Bean class name [" + beanClassObject + "] has not been resolved into an actual Class");
		}
		return (Class<?>) beanClassObject;
	}

	/**
	 * Return whether this definition specifies a bean class.
	 * 注：判断是否当前bean定义制定了bean类型
	 * @see #getBeanClass()
	 * @see #setBeanClass(Class)
	 * @see #resolveBeanClass(ClassLoader)
	 */
	public boolean hasBeanClass() {
		return (this.beanClass instanceof Class);
	}

	/**
	 * Determine the class of the wrapped bean, resolving it from a
	 * specified class name if necessary. Will also reload a specified
	 * Class from its name when called with the bean class already resolved.
	 * 注：根据指定的bean类型名，确定包装bean的类型-Class对象。
	 * 注意，即是当前bean类型已经解析了，这里我们还是会重新根据beanName加载
	 * @param classLoader the ClassLoader to use for resolving a (potential) class name
	 * @return the resolved bean class
	 * @throws ClassNotFoundException if the class name could be resolved
	 */
	@Nullable
	public Class<?> resolveBeanClass(@Nullable ClassLoader classLoader) throws ClassNotFoundException {
		String className = getBeanClassName();
		if (className == null) {
			return null;
		}
		Class<?> resolvedClass = ClassUtils.forName(className, classLoader);
		this.beanClass = resolvedClass;
		return resolvedClass;
	}

	/**
	 * Return a resolvable type for this bean definition.
	 * <p>This implementation delegates to {@link #getBeanClass()}.
	 * 注：返回当前bean定义的可解析类型。
	 * 具体实现就是使用ResolvableType包装下bean的Class对象
	 * @since 5.2
	 */
	@Override
	public ResolvableType getResolvableType() {
		return (hasBeanClass() ? ResolvableType.forClass(getBeanClass()) : ResolvableType.NONE);
	}

	/**
	 * Set the name of the target scope for the bean.
	 * <p>The default is singleton status, although this is only applied once
	 * a bean definition becomes active in the containing factory. A bean
	 * definition may eventually inherit its scope from a parent bean definition.
	 * For this reason, the default scope name is an empty string (i.e., {@code ""}),
	 * with singleton status being assumed until a resolved scope is set.
	 * 注：设置bean定义的最终作用域
	 * 默认作用域为单例。尽管bean定义的作用域只会设置一次，但是在工厂容器场景可能会被设置多次。
	 * 一个bean定义的作用域可能会从父bean定义中继承而来。处于这个情况，默认作用域设计为空字符串，以便后续修改。
	 * @see #SCOPE_SINGLETON
	 * @see #SCOPE_PROTOTYPE
	 */
	@Override
	public void setScope(@Nullable String scope) {
		this.scope = scope;
	}

	/**
	 * Return the name of the target scope for the bean.
	 * 注：返回当前bean的作用域
	 */
	@Override
	@Nullable
	public String getScope() {
		return this.scope;
	}

	/**
	 * Return whether this a <b>Singleton</b>, with a single shared instance
	 * returned from all calls.
	 * 注：返回当前是否为单例bean-即仅有一个实例
	 * 这里判断时，默认空也是单例
	 * @see #SCOPE_SINGLETON
	 */
	@Override
	public boolean isSingleton() {
		return SCOPE_SINGLETON.equals(this.scope) || SCOPE_DEFAULT.equals(this.scope);
	}

	/**
	 * Return whether this a <b>Prototype</b>, with an independent instance
	 * 注：返回当前是否为原型bean-即不同实例互相独立
	 * returned for each call.
	 * @see #SCOPE_PROTOTYPE
	 */
	@Override
	public boolean isPrototype() {
		return SCOPE_PROTOTYPE.equals(this.scope);
	}

	/**
	 * Set if this bean is "abstract", i.e. not meant to be instantiated itself but
	 * rather just serving as parent for concrete child bean definitions.
	 * <p>Default is "false". Specify true to tell the bean factory to not try to
	 * instantiate that particular bean in any case.
	 * 注：设置当前bean是否为一个抽象。这意味着当前bean不会被实例化，仅仅作为某个具体子bean定义的父bean定义而存在。
	 * 默认非抽象bean。用于可以指定为抽象类来防止bean工厂试图对特定bean实例化动作
	 */
	public void setAbstract(boolean abstractFlag) {
		this.abstractFlag = abstractFlag;
	}

	/**
	 * Return whether this bean is "abstract", i.e. not meant to be instantiated
	 * itself but rather just serving as parent for concrete child bean definitions.
	 * 注：返回当前bean是否为一个抽象。这意味着当前bean不会被实例化，仅仅作为某个具体子bean定义的父bean定义而存在。
	 */
	@Override
	public boolean isAbstract() {
		return this.abstractFlag;
	}

	/**
	 * Set whether this bean should be lazily initialized.
	 * <p>If {@code false}, the bean will get instantiated on startup by bean
	 * factories that perform eager initialization of singletons.
	 * 注：设置当前bean定义是否为懒加载bean
	 * 非懒加载bean会在bean工厂启动时进行早期的初始化
	 */
	@Override
	public void setLazyInit(boolean lazyInit) {
		this.lazyInit = lazyInit;
	}

	/**
	 * Return whether this bean should be lazily initialized, i.e. not
	 * eagerly instantiated on startup. Only applicable to a singleton bean.
	 * @return whether to apply lazy-init semantics ({@code false} by default)
	 * 注：判断当前bean是否为懒加载bean
	 */
	@Override
	public boolean isLazyInit() {
		return (this.lazyInit != null && this.lazyInit.booleanValue());
	}

	/**
	 * Return whether this bean should be lazily initialized, i.e. not
	 * eagerly instantiated on startup. Only applicable to a singleton bean.
	 * 注：返回当前bean的懒加载状态
	 * @return the lazy-init flag if explicitly set, or {@code null} otherwise
	 * @since 5.2
	 */
	@Nullable
	public Boolean getLazyInit() {
		return this.lazyInit;
	}

	/**
	 * Set the autowire mode. This determines whether any automagical detection
	 * and setting of bean references will happen. Default is AUTOWIRE_NO
	 * which means there won't be convention-based autowiring by name or type
	 * (however, there may still be explicit annotation-driven autowiring).
	 * 注：设置自动装配模式。自动装配模式决定了如何对属性进行任何自动检测和引用bean。
	 * 默认是不自动装配。这意味不能够根据约定的beanName或beanTpye进行自动装配。
	 * (然而，用户仍然可以使用注释驱动的自动装配能力)
	 * @param autowireMode the autowire mode to set.
	 * Must be one of the constants defined in this class.
	 * @see #AUTOWIRE_NO
	 * @see #AUTOWIRE_BY_NAME
	 * @see #AUTOWIRE_BY_TYPE
	 * @see #AUTOWIRE_CONSTRUCTOR
	 * @see #AUTOWIRE_AUTODETECT
	 */
	public void setAutowireMode(int autowireMode) {
		this.autowireMode = autowireMode;
	}

	/**
	 * Return the autowire mode as specified in the bean definition.
	 * 注：返回当前bean定义的自动装配模式
	 */
	public int getAutowireMode() {
		return this.autowireMode;
	}

	/**
	 * Return the resolved autowire code,
	 * (resolving AUTOWIRE_AUTODETECT to AUTOWIRE_CONSTRUCTOR or AUTOWIRE_BY_TYPE).
	 * 注：返回解析后的自动装配模式。
	 * 这主要针对于自动检测自动装配模式，需要根据beanClass的构造器信息来判断自动装配模式
	 * @see #AUTOWIRE_AUTODETECT
	 * @see #AUTOWIRE_CONSTRUCTOR
	 * @see #AUTOWIRE_BY_TYPE
	 */
	public int getResolvedAutowireMode() {
		if (this.autowireMode == AUTOWIRE_AUTODETECT) {
			// Work out whether to apply setter autowiring or constructor autowiring.
			// If it has a no-arg constructor it's deemed to be setter autowiring,
			// otherwise we'll try constructor autowiring.
			// 注：确定自动侦测装配的实际装配类型。
			// 如果bean类型不存在有参构造，那就使用Setter自动装配-byType；否则，使用构造器自动装配。
			Constructor<?>[] constructors = getBeanClass().getConstructors();
			for (Constructor<?> constructor : constructors) {
				if (constructor.getParameterCount() == 0) {
					return AUTOWIRE_BY_TYPE;
				}
			}
			return AUTOWIRE_CONSTRUCTOR;
		}
		else {
			return this.autowireMode;
		}
	}

	/**
	 * Set the dependency check code.
	 * 注：设置依赖检查的模式
	 * @param dependencyCheck the code to set.
	 * Must be one of the four constants defined in this class.
	 * @see #DEPENDENCY_CHECK_NONE
	 * @see #DEPENDENCY_CHECK_OBJECTS
	 * @see #DEPENDENCY_CHECK_SIMPLE
	 * @see #DEPENDENCY_CHECK_ALL
	 */
	public void setDependencyCheck(int dependencyCheck) {
		this.dependencyCheck = dependencyCheck;
	}

	/**
	 * Return the dependency check code.
	 * 注：返回依赖检查的模式
	 */
	public int getDependencyCheck() {
		return this.dependencyCheck;
	}

	/**
	 * Set the names of the beans that this bean depends on being initialized.
	 * The bean factory will guarantee that these beans get initialized first.
	 * <p>Note that dependencies are normally expressed through bean properties or
	 * constructor arguments. This property should just be necessary for other kinds
	 * of dependencies like statics (*ugh*) or database preparation on startup.
	 * 注：设置当前bean初始化时需要依赖的其他bean名称。bean工厂会保证bean初始化之前先初始化其依赖的bean.
	 * 注意bean的依赖通常通过bean的属性或者构造器参数声明。这些属性或者参数往往对于bean的启动是非常重要的，就如同静态域或数据库在启动之前的准备工作等。
	 */
	@Override
	public void setDependsOn(@Nullable String... dependsOn) {
		this.dependsOn = dependsOn;
	}

	/**
	 * Return the bean names that this bean depends on.
	 * 注：返回当前bean依赖的其他beanName
	 */
	@Override
	@Nullable
	public String[] getDependsOn() {
		return this.dependsOn;
	}

	/**
	 * Set whether this bean is a candidate for getting autowired into some other bean.
	 * 注：设置当前bean是否为其他bean的自动装配候选bean
	 * <p>Note that this flag is designed to only affect type-based autowiring.
	 * It does not affect explicit references by name, which will get resolved even
	 * if the specified bean is not marked as an autowire candidate. As a consequence,
	 * autowiring by name will nevertheless inject a bean if the name matches.
	 * 注：注意，这个标识仅会影响类型自动装配，不能够针对按名称显示引用生效，当然按照名称也能够解析。
	 * @see #AUTOWIRE_BY_TYPE
	 * @see #AUTOWIRE_BY_NAME
	 */
	@Override
	public void setAutowireCandidate(boolean autowireCandidate) {
		this.autowireCandidate = autowireCandidate;
	}

	/**
	 * Return whether this bean is a candidate for getting autowired into some other bean.
	 * 注：返回当前bean是否可以为其他bean自动装配
	 */
	@Override
	public boolean isAutowireCandidate() {
		return this.autowireCandidate;
	}

	/**
	 * Set whether this bean is a primary autowire candidate.
	 * <p>If this value is {@code true} for exactly one bean among multiple
	 * matching candidates, it will serve as a tie-breaker.
	 * 注：设置当前bean是否为主候选装配bean
	 * 对于多个匹配的候选bean,主候选bean将会被用来注入
	 */
	@Override
	public void setPrimary(boolean primary) {
		this.primary = primary;
	}

	/**
	 * Return whether this bean is a primary autowire candidate.
	 * 注：返回当前bean是否为主候选装配bean
	 */
	@Override
	public boolean isPrimary() {
		return this.primary;
	}

	/**
	 * Register a qualifier to be used for autowire candidate resolution,
	 * keyed by the qualifier's type name.
	 * 注：注册一个用于自动装配候选解析的qualifier，由qualifier的类型名作为key
	 * @see AutowireCandidateQualifier#getTypeName()
	 */
	public void addQualifier(AutowireCandidateQualifier qualifier) {
		this.qualifiers.put(qualifier.getTypeName(), qualifier);
	}

	/**
	 * Return whether this bean has the specified qualifier.
	 * 注：返回当前bean是否有指定的qualifier
	 */
	public boolean hasQualifier(String typeName) {
		return this.qualifiers.containsKey(typeName);
	}

	/**
	 * Return the qualifier mapped to the provided type name.
	 * 注：根据提供的类型名查找缓存的qualifier
	 */
	@Nullable
	public AutowireCandidateQualifier getQualifier(String typeName) {
		return this.qualifiers.get(typeName);
	}

	/**
	 * Return all registered qualifiers.
	 * 注：返回所有注册的qualifier
	 * @return the Set of {@link AutowireCandidateQualifier} objects.
	 */
	public Set<AutowireCandidateQualifier> getQualifiers() {
		return new LinkedHashSet<>(this.qualifiers.values());
	}

	/**
	 * Copy the qualifiers from the supplied AbstractBeanDefinition to this bean definition.
	 * 注：根据指定的AbstractBeanDefinition实例获取其所有qualifiers，并添加到当前bean相应缓存中
	 * @param source the AbstractBeanDefinition to copy from
	 */
	public void copyQualifiersFrom(AbstractBeanDefinition source) {
		Assert.notNull(source, "Source must not be null");
		this.qualifiers.putAll(source.qualifiers);
	}

	/**
	 * Specify a callback for creating an instance of the bean,
	 * as an alternative to a declaratively specified factory method.
	 * 注：制定一个创建bean实例的回调逻辑。否则需要在工厂方法中调用声明这部分逻辑。
	 * <p>If such a callback is set, it will override any other constructor
	 * or factory method metadata. However, bean property population and
	 * potential annotation-driven injection will still apply as usual.
	 * 注：如果设置了当前回调逻辑，它将覆盖任何其他构造器或者工厂方法元数据。
	 * 然而，bean属性填充以及隐式注解驱动不受影响。
	 * @since 5.0
	 * @see #setConstructorArgumentValues(ConstructorArgumentValues)
	 * @see #setPropertyValues(MutablePropertyValues)
	 */
	public void setInstanceSupplier(@Nullable Supplier<?> instanceSupplier) {
		this.instanceSupplier = instanceSupplier;
	}

	/**
	 * Return a callback for creating an instance of the bean, if any.
	 * 注：返回当前bean创建实例的回调。
	 * @since 5.0
	 */
	@Nullable
	public Supplier<?> getInstanceSupplier() {
		return this.instanceSupplier;
	}

	/**
	 * Specify whether to allow access to non-public constructors and methods,
	 * for the case of externalized metadata pointing to those. The default is
	 * {@code true}; switch this to {@code false} for public access only.
	 * 注：对于一些构造器或方法外部化的元数据，指定是否允许访问。默认允许，可以设置为true后仅允许访问公共权限方法。
	 * <p>This applies to constructor resolution, factory method resolution,
	 * and also init/destroy methods. Bean property accessors have to be public
	 * in any case and are not affected by this setting.
	 * 注：这会影响用于构造器函数解析，工厂方法解析以及init/destroy方法。
	 * Bean属性访问器在任何情况下都必须是公共的，并且不受当前设置影响。
	 * <p>Note that annotation-driven configuration will still access non-public
	 * members as far as they have been annotated. This setting applies to
	 * externalized metadata in this bean definition only.
	 * 注：注解驱动的配置将会仍然访问非公共成员。此设置仅适用于该bean定义中的外部化元数据。
	 */
	public void setNonPublicAccessAllowed(boolean nonPublicAccessAllowed) {
		this.nonPublicAccessAllowed = nonPublicAccessAllowed;
	}

	/**
	 * Return whether to allow access to non-public constructors and methods.
	 * 注：返回是否允许允许方法非公共构造器或方法
	 */
	public boolean isNonPublicAccessAllowed() {
		return this.nonPublicAccessAllowed;
	}

	/**
	 * Specify whether to resolve constructors in lenient mode ({@code true},
	 * which is the default) or to switch to strict resolution (throwing an exception
	 * in case of ambiguous constructors that all match when converting the arguments,
	 * whereas lenient mode would use the one with the 'closest' type matches).
	 * 注：指定解析构造器时采用较为宽松的方式(默认采用)或者使用严格匹配模式。
	 * 严格匹配模式会在转换参数时如果匹配到不明确的多个构造器将会抛出异常，而宽松模式会使用最接近的类型匹配。
	 */
	public void setLenientConstructorResolution(boolean lenientConstructorResolution) {
		this.lenientConstructorResolution = lenientConstructorResolution;
	}

	/**
	 * Return whether to resolve constructors in lenient mode or in strict mode.
	 * 注：返回当前解析构造器的模式
	 */
	public boolean isLenientConstructorResolution() {
		return this.lenientConstructorResolution;
	}

	/**
	 * Specify the factory bean to use, if any.
	 * This the name of the bean to call the specified factory method on.
	 * 注：指定工厂beanName
	 * @see #setFactoryMethodName
	 */
	@Override
	public void setFactoryBeanName(@Nullable String factoryBeanName) {
		this.factoryBeanName = factoryBeanName;
	}

	/**
	 * Return the factory bean name, if any.
	 * 注：返回工厂beanName
	 */
	@Override
	@Nullable
	public String getFactoryBeanName() {
		return this.factoryBeanName;
	}

	/**
	 * Specify a factory method, if any. This method will be invoked with
	 * constructor arguments, or with no arguments if none are specified.
	 * The method will be invoked on the specified factory bean, if any,
	 * or otherwise as a static method on the local bean class.
	 * 注：指定工厂方法。这个方法将会通过构造参数调用，如果没有指定会调用该方法的无参构造。
	 * 该工厂方法将会在指定的工厂bean上调用，或者作为当前bean类型的静态方法调用。
	 * @see #setFactoryBeanName
	 * @see #setBeanClassName
	 */
	@Override
	public void setFactoryMethodName(@Nullable String factoryMethodName) {
		this.factoryMethodName = factoryMethodName;
	}

	/**
	 * Return a factory method, if any.
	 * 注：返回一个工厂方法
	 */
	@Override
	@Nullable
	public String getFactoryMethodName() {
		return this.factoryMethodName;
	}

	/**
	 * Specify constructor argument values for this bean.
	 * 注：设置当前bean构造器参数
	 */
	public void setConstructorArgumentValues(ConstructorArgumentValues constructorArgumentValues) {
		this.constructorArgumentValues = constructorArgumentValues;
	}

	/**
	 * Return constructor argument values for this bean (never {@code null}).
	 * 注：返回当前bean的构造器参数
	 */
	@Override
	public ConstructorArgumentValues getConstructorArgumentValues() {
		if (this.constructorArgumentValues == null) {
			this.constructorArgumentValues = new ConstructorArgumentValues();
		}
		return this.constructorArgumentValues;
	}

	/**
	 * Return if there are constructor argument values defined for this bean.
	 * 注：返回当前bean是否存在构造器参数
	 */
	@Override
	public boolean hasConstructorArgumentValues() {
		return (this.constructorArgumentValues != null && !this.constructorArgumentValues.isEmpty());
	}

	/**
	 * Specify property values for this bean, if any.
	 * 注：设置当前bean的属性数据
	 */
	public void setPropertyValues(MutablePropertyValues propertyValues) {
		this.propertyValues = propertyValues;
	}

	/**
	 * Return property values for this bean (never {@code null}).
	 * 注：返回当前bean的属性数据，Null返回默认
	 */
	@Override
	public MutablePropertyValues getPropertyValues() {
		if (this.propertyValues == null) {
			this.propertyValues = new MutablePropertyValues();
		}
		return this.propertyValues;
	}

	/**
	 * Return if there are property values values defined for this bean.
	 * 注：返回当前bean是否定义了属性值
	 * @since 5.0.2
	 */
	@Override
	public boolean hasPropertyValues() {
		return (this.propertyValues != null && !this.propertyValues.isEmpty());
	}

	/**
	 * Specify method overrides for the bean, if any.
	 * 注：设置当前bean的重新方法
	 */
	public void setMethodOverrides(MethodOverrides methodOverrides) {
		this.methodOverrides = methodOverrides;
	}

	/**
	 * Return information about methods to be overridden by the IoC
	 * container. This will be empty if there are no method overrides.
	 * 注：返回被IOC容器重写的方法信息
	 * <p>Never returns {@code null}.
	 */
	public MethodOverrides getMethodOverrides() {
		return this.methodOverrides;
	}

	/**
	 * Return if there are method overrides defined for this bean.
	 * 注：返回当前bean是否存在重写的方法
	 * @since 5.0.2
	 */
	public boolean hasMethodOverrides() {
		return !this.methodOverrides.isEmpty();
	}

	/**
	 * Set the name of the initializer method.
	 * <p>The default is {@code null} in which case there is no initializer method.
	 * 注：设置初始化方法名
	 * 当没有初始化方法时，该属性设置为Null
	 */
	@Override
	public void setInitMethodName(@Nullable String initMethodName) {
		this.initMethodName = initMethodName;
	}

	/**
	 * Return the name of the initializer method.
	 * 注：返回初始化方法名
	 */
	@Override
	@Nullable
	public String getInitMethodName() {
		return this.initMethodName;
	}

	/**
	 * Specify whether or not the configured init method is the default.
	 * <p>The default value is {@code false}.
	 * 注：设置是否默认配置初始化方法，也即强制初始化。
	 * @see #setInitMethodName
	 */
	public void setEnforceInitMethod(boolean enforceInitMethod) {
		this.enforceInitMethod = enforceInitMethod;
	}

	/**
	 * Indicate whether the configured init method is the default.
	 * 注：返回配置的初始化方法是否是默认
	 * @see #getInitMethodName()
	 */
	public boolean isEnforceInitMethod() {
		return this.enforceInitMethod;
	}

	/**
	 * Set the name of the destroy method.
	 * <p>The default is {@code null} in which case there is no destroy method.
	 * 注：设置销毁方法名称。如果没有销毁方法则为null
	 */
	@Override
	public void setDestroyMethodName(@Nullable String destroyMethodName) {
		this.destroyMethodName = destroyMethodName;
	}

	/**
	 * Return the name of the destroy method.
	 * 注：返回销毁方法名
	 */
	@Override
	@Nullable
	public String getDestroyMethodName() {
		return this.destroyMethodName;
	}

	/**
	 * Specify whether or not the configured destroy method is the default.
	 * <p>The default value is {@code false}.
	 * 注：设置是否配置的销毁方法为默认销毁方法
	 * @see #setDestroyMethodName
	 */
	public void setEnforceDestroyMethod(boolean enforceDestroyMethod) {
		this.enforceDestroyMethod = enforceDestroyMethod;
	}

	/**
	 * Indicate whether the configured destroy method is the default.
	 * 注：返回销毁方法是否是默认
	 * @see #getDestroyMethodName
	 */
	public boolean isEnforceDestroyMethod() {
		return this.enforceDestroyMethod;
	}

	/**
	 * Set whether this bean definition is 'synthetic', that is, not defined
	 * by the application itself (for example, an infrastructure bean such
	 * as a helper for auto-proxying, created through {@code <aop:config>}).
	 * 注：设置当前bean定义是否为动态生成的，即非应用本身定义
	 */
	public void setSynthetic(boolean synthetic) {
		this.synthetic = synthetic;
	}

	/**
	 * Return whether this bean definition is 'synthetic', that is,
	 * not defined by the application itself.
	 * 注：返回当前是否为动态生成的bean定义
	 */
	public boolean isSynthetic() {
		return this.synthetic;
	}

	/**
	 * Set the role hint for this {@code BeanDefinition}.
	 * 注：设置当前bean定义的角色标识
	 */
	@Override
	public void setRole(int role) {
		this.role = role;
	}

	/**
	 * Return the role hint for this {@code BeanDefinition}.
	 * 注：返回当前bean定义的角色标识
	 */
	@Override
	public int getRole() {
		return this.role;
	}

	/**
	 * Set a human-readable description of this bean definition.
	 * 注：设置当前bean定义的描述信息
	 */
	@Override
	public void setDescription(@Nullable String description) {
		this.description = description;
	}

	/**
	 * Return a human-readable description of this bean definition.
	 * 注：返回当前bean定义的描述信息
	 */
	@Override
	@Nullable
	public String getDescription() {
		return this.description;
	}

	/**
	 * Set the resource that this bean definition came from
	 * (for the purpose of showing context in case of errors).
	 * 注：设置当前bean定义的源文件(用于上下文异常情况日志)
	 */
	public void setResource(@Nullable Resource resource) {
		this.resource = resource;
	}

	/**
	 * Return the resource that this bean definition came from.
	 * 注：返回当前bean定义的源文件
	 */
	@Nullable
	public Resource getResource() {
		return this.resource;
	}

	/**
	 * Set a description of the resource that this bean definition
	 * came from (for the purpose of showing context in case of errors).
	 * 注：设置当前bean定义的源文件描述(用于上下文异常情况日志)
	 */
	public void setResourceDescription(@Nullable String resourceDescription) {
		this.resource = (resourceDescription != null ? new DescriptiveResource(resourceDescription) : null);
	}

	/**
	 * Return a description of the resource that this bean definition
	 * came from (for the purpose of showing context in case of errors).
	 * 注：返回当前bean定义的源文件描述(用于上下文异常情况日志)
	 */
	@Override
	@Nullable
	public String getResourceDescription() {
		return (this.resource != null ? this.resource.getDescription() : null);
	}

	/**
	 * Set the originating (e.g. decorated) BeanDefinition, if any.
	 * 注：设置原始的、被装饰的bean定义
	 */
	public void setOriginatingBeanDefinition(BeanDefinition originatingBd) {
		this.resource = new BeanDefinitionResource(originatingBd);
	}

	/**
	 * Return the originating BeanDefinition, or {@code null} if none.
	 * Allows for retrieving the decorated bean definition, if any.
	 * 注：返回原始的bean定义。这允许召回被被装饰的bean定义
	 * <p>Note that this method returns the immediate originator. Iterate through the
	 * originator chain to find the original BeanDefinition as defined by the user.
	 * 注：注意这个方法仅返回直接的原始bean定义。如果有必要的话，可能需要多次调用原始bean定义链条来获得用户定义的bean定义。
	 */
	@Override
	@Nullable
	public BeanDefinition getOriginatingBeanDefinition() {
		return (this.resource instanceof BeanDefinitionResource ?
				((BeanDefinitionResource) this.resource).getBeanDefinition() : null);
	}

	/**
	 * Validate this bean definition.
	 * 抽象类提供了bean定义的验证过程【registerBeanDefinition的最后一步】
	 * @throws BeanDefinitionValidationException in case of validation failure
	 */
	public void validate() throws BeanDefinitionValidationException {
		/**
		 * 1. “重写方法”不能和“工厂方法”同时存在
		 * 因为工厂方法会返回一个具体的bean实例，不由spring来创建，因此重写方法没有任何意义。
		 */
		if (hasMethodOverrides() && getFactoryMethodName() != null) {
			throw new BeanDefinitionValidationException(
					"Cannot combine factory method with container-generated method overrides: " +
					"the factory method must create the concrete bean instance.");
		}
		// 2. 校验当前bean类型中的"重写方法"，可能有不存在的情况
		if (hasBeanClass()) {
			prepareMethodOverrides();
		}
	}

	/**
	 * Validate and prepare the method overrides defined for this bean.
	 * Checks for existence of a method with the specified name.
	 * 注：校验并准备当前bean中重写的方法，其中校验是指检查指定方法名的存在性。
	 * @throws BeanDefinitionValidationException in case of validation failure
	 */
	public void prepareMethodOverrides() throws BeanDefinitionValidationException {
		// Check that lookup methods exist and determine their overloaded status.
		// 检查当前bean重写方法的存在性，并且设置方法重载标识
		if (hasMethodOverrides()) {
			getMethodOverrides().getOverrides().forEach(this::prepareMethodOverride);
		}
	}

	/**
	 * Validate and prepare the given method override.
	 * Checks for existence of a method with the specified name,
	 * marking it as not overloaded if none found.
	 * 注：验证并且准备指定的重写方法实例
	 * 校验指定重写方法名的存在性，如果存在的话就标注下不允许重载
	 * @param mo the MethodOverride object to validate
	 * @throws BeanDefinitionValidationException in case of validation failure
	 */
	protected void prepareMethodOverride(MethodOverride mo) throws BeanDefinitionValidationException {
		int count = ClassUtils.getMethodCountForName(getBeanClass(), mo.getMethodName());
		if (count == 0) {
			throw new BeanDefinitionValidationException(
					"Invalid method override: no method with name '" + mo.getMethodName() +
					"' on class [" + getBeanClassName() + "]");
		}
		else if (count == 1) {
			// Mark override as not overloaded, to avoid the overhead of arg type checking.
			/**
			 * 注：表示重写的方法不会被重载(因为数量就1个)。这么做的目的增加参数类型匹配检查的性能
			 * 实际上，方法的个数也可能不只有1个或0个。这个方法主要目的是校验方法是否存在，顺便设置个标识提高性能。
			 */
			mo.setOverloaded(false);
		}
	}


	/**
	 * Public declaration of Object's {@code clone()} method.
	 * Delegates to {@link #cloneBeanDefinition()}.
	 * 抽象bean定义支持拷贝。
	 * 但具体如何拷贝bean定义由子类实现cloneBeanDefinition方法。
	 * @see Object#clone()
	 */
	@Override
	public Object clone() {
		return cloneBeanDefinition();
	}

	/**
	 * Clone this bean definition.
	 * To be implemented by concrete subclasses.
	 * 子类实现bean定义的拷贝方式
	 * @return the cloned bean definition object
	 */
	public abstract AbstractBeanDefinition cloneBeanDefinition();

	@Override
	public boolean equals(@Nullable Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof AbstractBeanDefinition)) {
			return false;
		}
		AbstractBeanDefinition that = (AbstractBeanDefinition) other;
		return (ObjectUtils.nullSafeEquals(getBeanClassName(), that.getBeanClassName()) &&
				ObjectUtils.nullSafeEquals(this.scope, that.scope) &&
				this.abstractFlag == that.abstractFlag &&
				this.lazyInit == that.lazyInit &&
				this.autowireMode == that.autowireMode &&
				this.dependencyCheck == that.dependencyCheck &&
				Arrays.equals(this.dependsOn, that.dependsOn) &&
				this.autowireCandidate == that.autowireCandidate &&
				ObjectUtils.nullSafeEquals(this.qualifiers, that.qualifiers) &&
				this.primary == that.primary &&
				this.nonPublicAccessAllowed == that.nonPublicAccessAllowed &&
				this.lenientConstructorResolution == that.lenientConstructorResolution &&
				ObjectUtils.nullSafeEquals(this.constructorArgumentValues, that.constructorArgumentValues) &&
				ObjectUtils.nullSafeEquals(this.propertyValues, that.propertyValues) &&
				ObjectUtils.nullSafeEquals(this.methodOverrides, that.methodOverrides) &&
				ObjectUtils.nullSafeEquals(this.factoryBeanName, that.factoryBeanName) &&
				ObjectUtils.nullSafeEquals(this.factoryMethodName, that.factoryMethodName) &&
				ObjectUtils.nullSafeEquals(this.initMethodName, that.initMethodName) &&
				this.enforceInitMethod == that.enforceInitMethod &&
				ObjectUtils.nullSafeEquals(this.destroyMethodName, that.destroyMethodName) &&
				this.enforceDestroyMethod == that.enforceDestroyMethod &&
				this.synthetic == that.synthetic &&
				this.role == that.role &&
				super.equals(other));
	}

	@Override
	public int hashCode() {
		int hashCode = ObjectUtils.nullSafeHashCode(getBeanClassName());
		hashCode = 29 * hashCode + ObjectUtils.nullSafeHashCode(this.scope);
		hashCode = 29 * hashCode + ObjectUtils.nullSafeHashCode(this.constructorArgumentValues);
		hashCode = 29 * hashCode + ObjectUtils.nullSafeHashCode(this.propertyValues);
		hashCode = 29 * hashCode + ObjectUtils.nullSafeHashCode(this.factoryBeanName);
		hashCode = 29 * hashCode + ObjectUtils.nullSafeHashCode(this.factoryMethodName);
		hashCode = 29 * hashCode + super.hashCode();
		return hashCode;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("class [");
		sb.append(getBeanClassName()).append("]");
		sb.append("; scope=").append(this.scope);
		sb.append("; abstract=").append(this.abstractFlag);
		sb.append("; lazyInit=").append(this.lazyInit);
		sb.append("; autowireMode=").append(this.autowireMode);
		sb.append("; dependencyCheck=").append(this.dependencyCheck);
		sb.append("; autowireCandidate=").append(this.autowireCandidate);
		sb.append("; primary=").append(this.primary);
		sb.append("; factoryBeanName=").append(this.factoryBeanName);
		sb.append("; factoryMethodName=").append(this.factoryMethodName);
		sb.append("; initMethodName=").append(this.initMethodName);
		sb.append("; destroyMethodName=").append(this.destroyMethodName);
		if (this.resource != null) {
			sb.append("; defined in ").append(this.resource.getDescription());
		}
		return sb.toString();
	}

}
