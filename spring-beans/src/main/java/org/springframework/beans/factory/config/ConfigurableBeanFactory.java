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

import java.beans.PropertyEditor;
import java.security.AccessControlContext;

import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.HierarchicalBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.core.convert.ConversionService;
import org.springframework.lang.Nullable;
import org.springframework.util.StringValueResolver;

/**
 * Configuration interface to be implemented by most bean factories. Provides
 * facilities to configure a bean factory, in addition to the bean factory
 * client methods in the {@link org.springframework.beans.factory.BeanFactory}
 * interface.
 * 注：ConfigurableBeanFactory是大多数Bean工厂需要实现的配置接口。
 * 该接口继承了BeanFactory接口，因此除了有BeanFactory的访问方法，还具备配置bean工厂的能力。
 *
 * <p>This bean factory interface is not meant to be used in normal application
 * code: Stick to {@link org.springframework.beans.factory.BeanFactory} or
 * {@link org.springframework.beans.factory.ListableBeanFactory} for typical
 * needs. This extended interface is just meant to allow for framework-internal
 * plug'n'play and for special access to bean factory configuration methods.
 * 注：这并不意味着在正常应用程序中使用该接口，还是持续使用BeanFactory和ListableBeanFactory的访问接口。
 * 该配置接口仅允许spring框架内部使用或者用于特殊目的下的配置方法。
 *
 * @author Juergen Hoeller
 * @since 03.11.2003
 * @see org.springframework.beans.factory.BeanFactory
 * @see org.springframework.beans.factory.ListableBeanFactory
 * @see ConfigurableListableBeanFactory
 */
public interface ConfigurableBeanFactory extends HierarchicalBeanFactory, SingletonBeanRegistry {

	/**
	 * Scope identifier for the standard singleton scope: {@value}.
	 * <p>Custom scopes can be added via {@code registerScope}.
	 * 注：(内置)标准单例作用域标识
	 * - 可以通过registerScope自定义作用域
	 * @see #registerScope
	 */
	String SCOPE_SINGLETON = "singleton";

	/**
	 * Scope identifier for the standard prototype scope: {@value}.
	 * <p>Custom scopes can be added via {@code registerScope}.
	 * 注：(内置)标准原型作用域标识
	 * - 可以通过registerScope自定义作用域
	 * @see #registerScope
	 */
	String SCOPE_PROTOTYPE = "prototype";


	/**
	 * Set the parent of this bean factory.
	 * <p>Note that the parent cannot be changed: It should only be set outside
	 * a constructor if it isn't available at the time of factory instantiation.
	 * 注：设置当前bean工厂的父工厂
	 * - 注意父工厂设置之后不能修改，因此推荐在工厂实例化时通过构造函数设置父工厂。
	 * 只有当工厂实例化时，父工厂还不可用，才应该使用该方法。
	 * @param parentBeanFactory the parent BeanFactory
	 * @throws IllegalStateException if this factory is already associated with
	 * a parent BeanFactory
	 * @see #getParentBeanFactory()
	 */
	void setParentBeanFactory(BeanFactory parentBeanFactory) throws IllegalStateException;

	/**
	 * Set the class loader to use for loading bean classes.
	 * Default is the thread context class loader.
	 * 注：设置用于加载bean类信息的类加载器；默认使用线程上下文类加载器。
	 * <p>Note that this class loader will only apply to bean definitions
	 * that do not carry a resolved bean class yet. This is the case as of
	 * Spring 2.0 by default: Bean definitions only carry bean class names,
	 * to be resolved once the factory processes the bean definition.
	 * 注：该类加载器仅用于处理未携带已解析bean类型信息的bean定义。
	 * spring2.0默认情况下，bean定义仅携带bean类名，一旦工厂处理该bean定义时就会解析该类型。
	 * @param beanClassLoader the class loader to use,
	 * or {@code null} to suggest the default class loader
	 */
	void setBeanClassLoader(@Nullable ClassLoader beanClassLoader);

	/**
	 * Return this factory's class loader for loading bean classes
	 * (only {@code null} if even the system ClassLoader isn't accessible).
	 * 注：返回加载bean类型信息的类加载器。
	 * 只有系统类加载器不可见时才会返回null
	 * @see org.springframework.util.ClassUtils#forName(String, ClassLoader)
	 */
	@Nullable
	ClassLoader getBeanClassLoader();

	/**
	 * Specify a temporary ClassLoader to use for type matching purposes.
	 * Default is none, simply using the standard bean ClassLoader.
	 * <p>A temporary ClassLoader is usually just specified if
	 * <i>load-time weaving</i> is involved, to make sure that actual bean
	 * classes are loaded as lazily as possible. The temporary loader is
	 * then removed once the BeanFactory completes its bootstrap phase.
	 * 注：指定用于类型匹配的临时类加载器。默认使用标准bean类加载器。
	 * - 临时类加载器通常只有当涉及织入时才会指定。该加载器确保实际bean类型尽可能懒加载。
	 * 临时类加载器会在bean工厂完成启动阶段后被移除。
	 * @since 2.5
	 */
	void setTempClassLoader(@Nullable ClassLoader tempClassLoader);

	/**
	 * Return the temporary ClassLoader to use for type matching purposes,
	 * if any.
	 * 注：返回用于类型匹配目的的临时类加载器
	 * @since 2.5
	 */
	@Nullable
	ClassLoader getTempClassLoader();

	/**
	 * Set whether to cache bean metadata such as given bean definitions
	 * (in merged fashion) and resolved bean classes. Default is on.
	 * 注：设置是否缓存bean元数据，比如指定的bean根定义以及已解析的bean类型。默认情况下开启缓存。
	 * <p>Turn this flag off to enable hot-refreshing of bean definition objects
	 * and in particular bean classes. If this flag is off, any creation of a bean
	 * instance will re-query the bean class loader for newly resolved classes.
	 * 注：关闭此开关能够实现bean定义对象的热刷新，尤其是修改bean类型。
	 * 如果当前开关关闭，任何bean实例的创建都会使用bean类加载器加载新的解析类型。
	 */
	void setCacheBeanMetadata(boolean cacheBeanMetadata);

	/**
	 * Return whether to cache bean metadata such as given bean definitions
	 * (in merged fashion) and resolved bean classes.
	 * 注：返回是否缓存bean元数据，比如指定的bean根定义以及已解析的bean类型。默认情况下开启缓存。
	 */
	boolean isCacheBeanMetadata();

	/**
	 * Specify the resolution strategy for expressions in bean definition values.
	 * 注：指定Bean定义中value的解析器
	 * <p>There is no expression support active in a BeanFactory by default.
	 * An ApplicationContext will typically set a standard expression strategy
	 * here, supporting "#{...}" expressions in a Unified EL compatible style.
	 * 注：默认情况Bean工厂没有相应表达式的支持。
	 * 在应用上下文会通常使用标准解析策略来支持spring统一的表达式语言。
	 * @since 3.0
	 */
	void setBeanExpressionResolver(@Nullable BeanExpressionResolver resolver);

	/**
	 * Return the resolution strategy for expressions in bean definition values.
	 * 注：返回Bean定义中value的解析器
	 * @since 3.0
	 */
	@Nullable
	BeanExpressionResolver getBeanExpressionResolver();

	/**
	 * Specify a Spring 3.0 ConversionService to use for converting
	 * property values, as an alternative to JavaBeans PropertyEditors.
	 * 注：指定用于属性值的类型转换服务。--可替换PropertyEditors
	 * @since 3.0
	 */
	void setConversionService(@Nullable ConversionService conversionService);

	/**
	 * Return the associated ConversionService, if any.
	 * 注：返回关联的类型转换服务
	 * @since 3.0
	 */
	@Nullable
	ConversionService getConversionService();

	/**
	 * Add a PropertyEditorRegistrar to be applied to all bean creation processes.
	 * 注：增加一个应用于所有Bean创建过程的属性编辑注册器
	 * <p>Such a registrar creates new PropertyEditor instances and registers them
	 * on the given registry, fresh for each bean creation attempt. This avoids
	 * the need for synchronization on custom editors; hence, it is generally
	 * preferable to use this method instead of {@link #registerCustomEditor}.
	 * 注：...
	 * @param registrar the PropertyEditorRegistrar to register
	 */
	void addPropertyEditorRegistrar(PropertyEditorRegistrar registrar);

	/**
	 * Register the given custom property editor for all properties of the
	 * given type. To be invoked during factory configuration.
	 * 注：注册用于指定类型的所有属性的属性编辑器。
	 * <p>Note that this method will register a shared custom editor instance;
	 * access to that instance will be synchronized for thread-safety. It is
	 * generally preferable to use {@link #addPropertyEditorRegistrar} instead
	 * of this method, to avoid for the need for synchronization on custom editors.
	 * 注：当前方法会注册一个共享编辑器实例，需要注意线程安全性。
	 * 通常更加推荐使用addPropertyEditorRegistrar，避免编辑器实例的同步问题。
	 * @param requiredType type of the property
	 * @param propertyEditorClass the {@link PropertyEditor} class to register
	 */
	void registerCustomEditor(Class<?> requiredType, Class<? extends PropertyEditor> propertyEditorClass);

	/**
	 * Initialize the given PropertyEditorRegistry with the custom editors
	 * that have been registered with this BeanFactory.
	 * 注：初始化已经注册到当前bean容器的自定义编辑器。
	 * @param registry the PropertyEditorRegistry to initialize
	 */
	void copyRegisteredEditorsTo(PropertyEditorRegistry registry);

	/**
	 * Set a custom type converter that this BeanFactory should use for converting
	 * bean property values, constructor argument values, etc.
	 * 注：设置一个自定义的类型转换器。bean工厂会使用该转换器转换bean属性值、构造器参数等。
	 * <p>This will override the default PropertyEditor mechanism and hence make
	 * any custom editors or custom editor registrars irrelevant.
	 * 注：这会覆盖默认的属性编辑器机制
	 * @since 2.5
	 * @see #addPropertyEditorRegistrar
	 * @see #registerCustomEditor
	 */
	void setTypeConverter(TypeConverter typeConverter);

	/**
	 * Obtain a type converter as used by this BeanFactory. This may be a fresh
	 * instance for each call, since TypeConverters are usually <i>not</i> thread-safe.
	 * 注：获取bean工厂使用的类型转换器。
	 * <p>If the default PropertyEditor mechanism is active, the returned
	 * TypeConverter will be aware of all custom editors that have been registered.
	 * @since 2.5
	 */
	TypeConverter getTypeConverter();

	/**
	 * Add a String resolver for embedded values such as annotation attributes.
	 * 注：增加一个嵌入值得解析器，比如注解属性
	 * @param valueResolver the String resolver to apply to embedded values
	 * @since 3.0
	 */
	void addEmbeddedValueResolver(StringValueResolver valueResolver);

	/**
	 * Determine whether an embedded value resolver has been registered with this
	 * bean factory, to be applied through {@link #resolveEmbeddedValue(String)}.
	 * 注：判断是否当前bean容器已经注册了用于解析注解值的嵌入值解析器实例
	 * @since 4.3
	 */
	boolean hasEmbeddedValueResolver();

	/**
	 * Resolve the given embedded value, e.g. an annotation attribute.
	 * 注：解析给定的嵌套值，比如注解属性
	 * @param value the value to resolve
	 * @return the resolved value (may be the original value as-is)
	 * @since 3.0
	 */
	@Nullable
	String resolveEmbeddedValue(String value);

	/**
	 * Add a new BeanPostProcessor that will get applied to beans created
	 * by this factory. To be invoked during factory configuration.
	 * 注：增加一个bean后置处理器。bean工厂创建bean之后会调用后置处理器方法。
	 * <p>Note: Post-processors submitted here will be applied in the order of
	 * registration; any ordering semantics expressed through implementing the
	 * {@link org.springframework.core.Ordered} interface will be ignored. Note
	 * that autodetected post-processors (e.g. as beans in an ApplicationContext)
	 * will always be applied after programmatically registered ones.
	 * 注：通过这个方法提交的后置处理器没有顺序语义，忽略Ordered，根据注册方法的顺序执行。
	 * 任何自动监测的bean后置处理器都会在通过该方法注册的bean后置处理器之后执行。
	 * @param beanPostProcessor the post-processor to register
	 */
	void addBeanPostProcessor(BeanPostProcessor beanPostProcessor);

	/**
	 * Return the current number of registered BeanPostProcessors, if any.
	 * 注：返回当前已注册的后置处理器数量
	 */
	int getBeanPostProcessorCount();

	/**
	 * Register the given scope, backed by the given Scope implementation.
	 * 注：注册指定的自定义作用域
	 * @param scopeName the scope identifier
	 * @param scope the backing Scope implementation
	 */
	void registerScope(String scopeName, Scope scope);

	/**
	 * Return the names of all currently registered scopes.
	 * 注：返回当前所有已注册作用域的名称
	 * <p>This will only return the names of explicitly registered scopes.
	 * Built-in scopes such as "singleton" and "prototype" won't be exposed.
	 * 注：该方法只会返回明确已注册作用域的名称。内部作用域不会返回，比如"singleton"、"prototype"
	 * @return the array of scope names, or an empty array if none
	 * @see #registerScope
	 */
	String[] getRegisteredScopeNames();

	/**
	 * Return the Scope implementation for the given scope name, if any.
	 * 注：返回指定作用域名称的已注册的作用域实现
	 * <p>This will only return explicitly registered scopes.
	 * Built-in scopes such as "singleton" and "prototype" won't be exposed.
	 * 注：不会返回"singleton"、"prototype"内置作用域。
	 * @param scopeName the name of the scope
	 * @return the registered Scope implementation, or {@code null} if none
	 * @see #registerScope
	 */
	@Nullable
	Scope getRegisteredScope(String scopeName);

	/**
	 * Provides a security access control context relevant to this factory.
	 * 注：提供安全控制访问上下文。
	 * @return the applicable AccessControlContext (never {@code null})
	 * @since 3.0
	 */
	AccessControlContext getAccessControlContext();

	/**
	 * Copy all relevant configuration from the given other factory.
	 * 注：复制指定其他工厂的所有相关配置
	 * <p>Should include all standard configuration settings as well as
	 * BeanPostProcessors, Scopes, and factory-specific internal settings.
	 * Should not include any metadata of actual bean definitions,
	 * such as BeanDefinition objects and bean name aliases.
	 * 注：该方法应该包括所有标准配置设置，比如bean后置处理器、作用域、以及特定工厂内部设置。
	 * 不应该包括任何实际bean定义的元信息，比如bean定义对象和bean定义别名。
	 * @param otherFactory the other BeanFactory to copy from
	 */
	void copyConfigurationFrom(ConfigurableBeanFactory otherFactory);

	/**
	 * Given a bean name, create an alias. We typically use this method to
	 * support names that are illegal within XML ids (used for bean names).
	 * 注：为指定beanName的bean实例创建一个别名。
	 * 我们通常使用这个方法支持XML中非法别名
	 * <p>Typically invoked during factory configuration, but can also be
	 * used for runtime registration of aliases. Therefore, a factory
	 * implementation should synchronize alias access.
	 * 注：通常这个方法用于工厂配置&初始化阶段，但是也可以在应用运行时注册别名。
	 * 因此，Bean工厂实现类应该将新增别名同步至访问
	 * @param beanName the canonical name of the target bean
	 * @param alias the alias to be registered for the bean
	 * @throws BeanDefinitionStoreException if the alias is already in use
	 */
	void registerAlias(String beanName, String alias) throws BeanDefinitionStoreException;

	/**
	 * Resolve all alias target names and aliases registered in this
	 * factory, applying the given StringValueResolver to them.
	 * 注：使用指定的字符串解析器，解析当前Bean工厂所有别名的目标名以及已注册的别名
	 * <p>The value resolver may for example resolve placeholders
	 * in target bean names and even in alias names.
	 * 注：值解析器的作用比如解析目标bean名称甚至是别名中的占位符。
	 * @param valueResolver the StringValueResolver to apply
	 * @since 2.5
	 */
	void resolveAliases(StringValueResolver valueResolver);

	/**
	 * Return a merged BeanDefinition for the given bean name,
	 * merging a child bean definition with its parent if necessary.
	 * Considers bean definitions in ancestor factories as well.
	 * 注：根据指定bean名称返回合并bean定义，合并bean定义会合并其子bean定义的信息。
	 * - 在合并bean的时候会考虑父工厂的bean定义。
	 * @param beanName the name of the bean to retrieve the merged definition for
	 * @return a (potentially merged) BeanDefinition for the given bean
	 * @throws NoSuchBeanDefinitionException if there is no bean definition with the given name
	 * @since 2.5
	 */
	BeanDefinition getMergedBeanDefinition(String beanName) throws NoSuchBeanDefinitionException;

	/**
	 * Determine whether the bean with the given name is a FactoryBean.
	 * 注：检查指定的bean名对应的bean实例是否为FactoryBean
	 * @param name the name of the bean to check
	 * @return whether the bean is a FactoryBean
	 * ({@code false} means the bean exists but is not a FactoryBean)
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @since 2.5
	 */
	boolean isFactoryBean(String name) throws NoSuchBeanDefinitionException;

	/**
	 * Explicitly control the current in-creation status of the specified bean.
	 * For container-internal use only.
	 * 注：控制指定bean的正在创建中的状态
	 * - 仅用于容器内部使用(循环依赖)
	 * @param beanName the name of the bean
	 * @param inCreation whether the bean is currently in creation
	 * @since 3.1
	 */
	void setCurrentlyInCreation(String beanName, boolean inCreation);

	/**
	 * Determine whether the specified bean is currently in creation.
	 * 注：判断指定bean是否正在创建中的状态
	 * @param beanName the name of the bean
	 * @return whether the bean is currently in creation
	 * @since 2.5
	 */
	boolean isCurrentlyInCreation(String beanName);

	/**
	 * Register a dependent bean for the given bean,
	 * to be destroyed before the given bean is destroyed.
	 * 注：注册bean之间的依赖关系.
	 * - 依赖bean(A)会在当前bean(B)销毁之前销毁。  （假设A依赖B）
	 * @param beanName the name of the bean		当前bean  --- B
	 * @param dependentBeanName the name of the dependent bean  依赖bean名称  --- A
	 * @since 2.5
	 */
	void registerDependentBean(String beanName, String dependentBeanName);

	/**
	 * Return the names of all beans which depend on the specified bean, if any.
	 * 注：返回依赖指定bean的所有bean名称。
	 * @param beanName the name of the bean 被依赖的bean名
	 * @return the array of dependent bean names, or an empty array if none  依赖bean数组
	 * @since 2.5
	 */
	String[] getDependentBeans(String beanName);

	/**
	 * Return the names of all beans that the specified bean depends on, if any.
	 * 注：返回指定bean依赖的所有bean明后才能
	 * @param beanName the name of the bean
	 * @return the array of names of beans which the bean depends on,
	 * or an empty array if none
	 * @since 2.5
	 */
	String[] getDependenciesForBean(String beanName);

	/**
	 * Destroy the given bean instance (usually a prototype instance
	 * obtained from this factory) according to its bean definition.
	 * 注：根据bean定义销毁指定bean实例(通常是从这个工厂获得的原型实例)
	 * <p>Any exception that arises during destruction should be caught
	 * and logged instead of propagated to the caller of this method.
	 * 注：销毁方法抛出的任何异常都应该被捕获并且记录日志，而不是抛出到调用该方法的地方
	 * @param beanName the name of the bean definition
	 * @param beanInstance the bean instance to destroy
	 */
	void destroyBean(String beanName, Object beanInstance);

	/**
	 * Destroy the specified scoped bean in the current target scope, if any.
	 * <p>Any exception that arises during destruction should be caught
	 * and logged instead of propagated to the caller of this method.
	 * 注：销毁当前作用的指定bean。
	 * - 销毁方法抛出的任何异常都应该被捕获并且记录日志，而不是抛出到调用该方法的地方
	 * @param beanName the name of the scoped bean
	 */
	void destroyScopedBean(String beanName);

	/**
	 * Destroy all singleton beans in this factory, including inner beans that have
	 * been registered as disposable. To be called on shutdown of a factory.
	 * <p>Any exception that arises during destruction should be caught
	 * and logged instead of propagated to the caller of this method.
	 * 注：在Bean工厂关闭时，会调用该方法销毁所有工厂的单例bean，包括作为可销毁的已注册内部bean。
	 * - 销毁方法抛出的任何异常都应该被捕获并且记录日志，而不是抛出到调用该方法的地方
	 */
	void destroySingletons();

}
