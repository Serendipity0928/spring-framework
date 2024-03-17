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

package org.springframework.beans.factory;

import org.springframework.beans.BeansException;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;

/**
 * The root interface for accessing a Spring bean container.
 * 注：BeanFactory是访问一个Spring bean容器的根接口。
 *
 * <p>This is the basic client view of a bean container;
 * further interfaces such as {@link ListableBeanFactory} and
 * {@link org.springframework.beans.factory.config.ConfigurableBeanFactory}
 * are available for specific purposes.
 * 注：这是一个最基本的bean容器操作方法。
 * 对于更加特定目的的操作可以查看其他接口，如ListableBeanFactory、ConfigurableBeanFactory等。
 *
 * <p>This interface is implemented by objects that hold a number of bean definitions,
 * each uniquely identified by a String name. Depending on the bean definition,
 * the factory will return either an independent instance of a contained object
 * (the Prototype design pattern), or a single shared instance (a superior
 * alternative to the Singleton design pattern, in which the instance is a
 * singleton in the scope of the factory). Which type of instance will be returned
 * depends on the bean factory configuration: the API is the same. Since Spring
 * 2.0, further scopes are available depending on the concrete application
 * context (e.g. "request" and "session" scopes in a web environment).
 * 注：BeanFactory接口会被持有一定量的独一无二名称bean定义的具体类实现。
 * 当前工厂会返回要么独立的实例(原型模式)或者共享的实例(单例模式)，这取决于bean的定义。
 * 当前工厂返回实例的类型取决于bean工厂的配置。
 * 自从spring2.0，返回实例的作用域取决于具体的应用上下文(比如，在web环境中的请求和会话作用域)
 *
 * <p>The point of this approach is that the BeanFactory is a central registry
 * of application components, and centralizes configuration of application
 * components (no more do individual objects need to read properties files,
 * for example). See chapters 4 and 11 of "Expert One-on-One J2EE Design and
 * Development" for a discussion of the benefits of this approach.
 * 注：Bean工厂的意义在于其是一个应用程序组件的注册中心，并集中地管理应用程序配置(比如，创建单一对象不再需要读取配置文件)。
 * 有关于这种设计的好处可参考"Expert One-on-One J2EE Design and Development"的第4、11章节。
 *
 * <p>Note that it is generally better to rely on Dependency Injection
 * ("push" configuration) to configure application objects through setters
 * or constructors, rather than use any form of "pull" configuration like a
 * BeanFactory lookup. Spring's Dependency Injection functionality is
 * implemented using this BeanFactory interface and its subinterfaces.
 * 注：通常情况下最好依赖依赖注入(“推”配置)的方式来配置应用程序对象，即通过Setters或者构造器。
 * 而不是使用任何形式的“拉”配置，比如BeanFactory接口的查找方法。
 * Spring的依赖注入功能是使用BeanFactory接口及其子接口实现的。
 *
 * <p>Normally a BeanFactory will load bean definitions stored in a configuration
 * source (such as an XML document), and use the {@code org.springframework.beans}
 * package to configure the beans. However, an implementation could simply return
 * Java objects it creates as necessary directly in Java code. There are no
 * constraints on how the definitions could be stored: LDAP, RDBMS, XML,
 * properties file, etc. Implementations are encouraged to support references
 * amongst beans (Dependency Injection).
 * 注：正常情况下，Bean工厂会加载存储在配置源中(比如XML文档)的bean定义，并且使用spring-beans配置bean实例。
 * 然而，Bean工厂实例也能够返回Java用户程序创建出来的实例对象(需注册到bean工厂)。
 * - 对于bean定义的存储方式没有任何显示，可以是LDAP、RDBMS、XML、属性文件等。
 * - Bean工厂的实例应该支持bean对象之间的依赖关系(依赖注入能力)
 *
 * <p>In contrast to the methods in {@link ListableBeanFactory}, all of the
 * operations in this interface will also check parent factories if this is a
 * {@link HierarchicalBeanFactory}. If a bean is not found in this factory instance,
 * the immediate parent factory will be asked. Beans in this factory instance
 * are supposed to override beans of the same name in any parent factory.
 * 注：相比较于ListableBeanFactory接口的方法，BeanFactory接口的所有方法都会检查是否存在父工厂。
 * 如果一个bean实例未在当前工厂实例中找到，则立即会查询父工厂是否存在该bean实例。
 * 同样，当前bean工厂的bean实例会覆盖父工厂相同名称的bean实例。
 *
 * <p>Bean factory implementations should support the standard bean lifecycle interfaces
 * as far as possible. The full set of initialization methods and their standard order is:
 * <ol>
 * <li>BeanNameAware's {@code setBeanName}
 * <li>BeanClassLoaderAware's {@code setBeanClassLoader}
 * <li>BeanFactoryAware's {@code setBeanFactory}
 * <li>EnvironmentAware's {@code setEnvironment}
 * <li>EmbeddedValueResolverAware's {@code setEmbeddedValueResolver}
 * <li>ResourceLoaderAware's {@code setResourceLoader}
 * (only applicable when running in an application context)
 * <li>ApplicationEventPublisherAware's {@code setApplicationEventPublisher}
 * (only applicable when running in an application context)
 * <li>MessageSourceAware's {@code setMessageSource}
 * (only applicable when running in an application context)
 * <li>ApplicationContextAware's {@code setApplicationContext}
 * (only applicable when running in an application context)
 * <li>ServletContextAware's {@code setServletContext}
 * (only applicable when running in a web application context)
 * <li>{@code postProcessBeforeInitialization} methods of BeanPostProcessors
 * <li>InitializingBean's {@code afterPropertiesSet}
 * <li>a custom init-method definition
 * <li>{@code postProcessAfterInitialization} methods of BeanPostProcessors
 * </ol>
 * 注：Bean工厂的实现类应该尽可能支持标准Bean生命周期接口。以下是全部的初始化方法，并按照顺序排列：
 * 1. BeanNameAware setBeanName
 * 2. BeanClassLoaderAware  setBeanClassLoader
 * 3. EnvironmentAware setEnvironment
 * 4. EmbeddedValueResolverAware setEmbeddedValueResolver
 * 5. ResourceLoaderAware setResourceLoader  (context环境)
 * 6. ApplicationEventPublisherAware setApplicationEventPublisher (context环境)
 * 7. MessageSourceAware setMessageSource (context环境)
 * 8. ApplicationContextAware setApplicationContext (context环境)
 * 9. ServletContextAware setServletContext (context环境)
 * 10. postProcessBeforeInitialization
 * 11. InitializingBean
 * 12. postProcessAfterInitialization
 *
 * <p>On shutdown of a bean factory, the following lifecycle methods apply:
 * <ol>
 * <li>{@code postProcessBeforeDestruction} methods of DestructionAwareBeanPostProcessors
 * <li>DisposableBean's {@code destroy}
 * <li>a custom destroy-method definition
 * </ol>
 * 注：当bean工厂关闭时，下面的生命周期方法会被执行：
 * 1. DestructionAwareBeanPostProcessors postProcessBeforeDestruction
 * 2. DisposableBean destroy
 * 3. destroy-method
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Chris Beams
 * @since 13 April 2001
 * @see BeanNameAware#setBeanName
 * @see BeanClassLoaderAware#setBeanClassLoader
 * @see BeanFactoryAware#setBeanFactory
 * // @see org.springframework.context.ResourceLoaderAware#setResourceLoader
 * // @see org.springframework.context.ApplicationEventPublisherAware#setApplicationEventPublisher
 * // @see org.springframework.context.MessageSourceAware#setMessageSource
 * // @see org.springframework.context.ApplicationContextAware#setApplicationContext
 * // @see org.springframework.web.context.ServletContextAware#setServletContext
 * @see org.springframework.beans.factory.config.BeanPostProcessor#postProcessBeforeInitialization
 * @see InitializingBean#afterPropertiesSet
 * @see org.springframework.beans.factory.support.RootBeanDefinition#getInitMethodName
 * @see org.springframework.beans.factory.config.BeanPostProcessor#postProcessAfterInitialization
 * @see DisposableBean#destroy
 * @see org.springframework.beans.factory.support.RootBeanDefinition#getDestroyMethodName
 */
public interface BeanFactory {

	/**
	 * Used to dereference a {@link FactoryBean} instance and distinguish it from
	 * beans <i>created</i> by the FactoryBean. For example, if the bean named
	 * {@code myJndiObject} is a FactoryBean, getting {@code &myJndiObject}
	 * will return the factory, not the instance returned by the factory.
	 * 注：该常量用于间接引用工厂Bean对象，以及区分工厂Bean对象与工厂Bean创建的对象。
	 * 比如，如果名称为“myJndiObject”的bean对象为一个工厂Bean对象，当获取“&myJndiObject”时，则返回工厂Bean对象，而非创建对象。
	 * 疑问：为什么这个常量不放在FactoryBeanRegistrySupport中？
	 */
	String FACTORY_BEAN_PREFIX = "&";


	/**
	 * Return an instance, which may be shared or independent, of the specified bean.
	 * 注：返回指定名称的bean实例，该实例可能是单例的，也可能是原型(实例不共享，互相独立)
	 * <p>This method allows a Spring BeanFactory to be used as a replacement for the
	 * Singleton or Prototype design pattern. Callers may retain references to
	 * returned objects in the case of Singleton beans.
	 * - 这个方法使得Spring的BeanFactory可以代替单例模式或者原型模式【意即可以同时实现单例模式和原型模式效果】。
	 * 并在在单例模式下，调用者可以持有返回对象的引用。【意即下次再调用该方法，也是返回这个引用】
	 * <p>Translates aliases back to the corresponding canonical bean name.
	 * <p>Will ask the parent factory if the bean cannot be found in this factory instance.
	 * - 传入的bean名称可能是个别名或者是个工厂bean名，方法内部会将该名称转换为规范的bean名称。
	 * - 如果需要获取的bean实例不在当前工厂实例中，会尝试从父工厂中获取该实例。
	 * @param name the name of the bean to retrieve
	 * @return an instance of the bean
	 * @throws NoSuchBeanDefinitionException if there is no bean with the specified name
	 * @throws BeansException if the bean could not be obtained
	 */
	Object getBean(String name) throws BeansException;

	/**
	 * Return an instance, which may be shared or independent, of the specified bean.
	 * 注：返回指定名称的bean实例，该实例可能是单例的，也可能是原型(实例不共享，互相独立)
	 * <p>Behaves the same as {@link #getBean(String)}, but provides a measure of type
	 * safety by throwing a BeanNotOfRequiredTypeException if the bean is not of the
	 * required type. This means that ClassCastException can't be thrown on casting
	 * the result correctly, as can happen with {@link #getBean(String)}.
	 * 注：当前方法的结果和getBean(String)方法一样，但是提供了bean类型校验。
	 * 当返回的bean对象非指定类型时，会返回BeanNotOfRequiredTypeException异常。
	 * 这就意味着该方法不会发生ClassCastException异常，而getBean(String)方法有可能。
	 * <p>Translates aliases back to the corresponding canonical bean name.
	 * <p>Will ask the parent factory if the bean cannot be found in this factory instance.
	 * - 传入的bean名称可能是个别名或者是个工厂bean名，方法内部会将该名称转换为规范的bean名称。
	 * - 如果需要获取的bean实例不在当前工厂实例中，会尝试从父工厂中获取该实例。
	 * @param name the name of the bean to retrieve
	 * @param requiredType type the bean must match; can be an interface or superclass
	 * @return an instance of the bean
	 * @throws NoSuchBeanDefinitionException if there is no such bean definition
	 * @throws BeanNotOfRequiredTypeException if the bean is not of the required type
	 * @throws BeansException if the bean could not be created
	 */
	<T> T getBean(String name, Class<T> requiredType) throws BeansException;

	/**
	 * Return an instance, which may be shared or independent, of the specified bean.
	 * 注：返回指定名称的bean实例，该实例可能是单例的，也可能是原型(实例不共享，互相独立)
	 * <p>Allows for specifying explicit constructor arguments / factory method arguments,
	 * overriding the specified default arguments (if any) in the bean definition.
	 * 注：允许明确指定构造器参数或者工厂方法参数，这将覆盖在bean定义中指定的默认参数。
	 * - 如果存在默认参数，并且这里指定的话，则需要保证该Bean实例为原型bean。
	 * @param name the name of the bean to retrieve
	 * @param args arguments to use when creating a bean instance using explicit arguments
	 * (only applied when creating a new instance as opposed to retrieving an existing one)
	 * @return an instance of the bean
	 * @throws NoSuchBeanDefinitionException if there is no such bean definition
	 * @throws BeanDefinitionStoreException if arguments have been given but
	 * the affected bean isn't a prototype
	 * @throws BeansException if the bean could not be created
	 * @since 2.5
	 */
	Object getBean(String name, Object... args) throws BeansException;

	/**
	 * Return the bean instance that uniquely matches the given object type, if any.
	 * 注：返回匹配指定类型的bean实例
	 * <p>This method goes into {@link ListableBeanFactory} by-type lookup territory
	 * but may also be translated into a conventional by-name lookup based on the name
	 * of the given type. For more extensive retrieval operations across sets of beans,
	 * use {@link ListableBeanFactory} and/or {@link BeanFactoryUtils}.
	 * 注：当前方法是ListableBeanFactory通过类型查找bean实例的功能，
	 * 但是当前方法也会基于类型转换后的名称来查找bean实例。
	 * - 如果要在bean集合之间进行更广泛地检索操作，请使用ListableBeanFactory或者BeanFactoryUtils
	 * @param requiredType type the bean must match; can be an interface or superclass
	 * @return an instance of the single bean matching the required type
	 * @throws NoSuchBeanDefinitionException if no bean of the given type was found
	 * @throws NoUniqueBeanDefinitionException if more than one bean of the given type was found
	 * @throws BeansException if the bean could not be created
	 * @since 3.0
	 * @see ListableBeanFactory
	 */
	<T> T getBean(Class<T> requiredType) throws BeansException;

	/**
	 * Return an instance, which may be shared or independent, of the specified bean.
	 * 注：返回指定名称的bean实例，该实例可能是单例的，也可能是原型(实例不共享，互相独立)
	 * <p>Allows for specifying explicit constructor arguments / factory method arguments,
	 * overriding the specified default arguments (if any) in the bean definition.
	 * 注：允许明确指定构造器参数或者工厂方法参数，这将覆盖在bean定义中指定的默认参数。
	 * - 如果存在默认参数，并且这里指定的话，则需要保证该Bean实例为原型bean。
	 * <p>This method goes into {@link ListableBeanFactory} by-type lookup territory
	 * but may also be translated into a conventional by-name lookup based on the name
	 * of the given type. For more extensive retrieval operations across sets of beans,
	 * use {@link ListableBeanFactory} and/or {@link BeanFactoryUtils}.
	 * 注：当前方法是ListableBeanFactory通过类型查找bean实例的功能，
	 * 但是当前方法也会基于类型转换后的名称来查找bean实例。
	 * 如果要在bean集合之间进行更广泛地检索操作，请使用ListableBeanFactory或者BeanFactoryUtils
	 * @param requiredType type the bean must match; can be an interface or superclass
	 * @param args arguments to use when creating a bean instance using explicit arguments
	 * (only applied when creating a new instance as opposed to retrieving an existing one)
	 * @return an instance of the bean
	 * @throws NoSuchBeanDefinitionException if there is no such bean definition
	 * @throws BeanDefinitionStoreException if arguments have been given but
	 * the affected bean isn't a prototype
	 * @throws BeansException if the bean could not be created
	 * @since 4.1
	 */
	<T> T getBean(Class<T> requiredType, Object... args) throws BeansException;

	/**
	 * Return a provider for the specified bean, allowing for lazy on-demand retrieval
	 * of instances, including availability and uniqueness options.
	 * 注：返回指定bean的tProvider实例。允许延迟按需检索实例，包括可用性和唯一性选项。
	 * @param requiredType type the bean must match; can be an interface or superclass
	 * @return a corresponding provider handle
	 * @since 5.1
	 * @see #getBeanProvider(ResolvableType)
	 */
	<T> ObjectProvider<T> getBeanProvider(Class<T> requiredType);

	/**
	 * Return a provider for the specified bean, allowing for lazy on-demand retrieval
	 * of instances, including availability and uniqueness options.
	 * 注：返回指定bean的tProvider实例。允许延迟按需检索实例，包括可用性和唯一性选项。
	 * @param requiredType type the bean must match; can be a generic type declaration.
	 * Note that collection types are not supported here, in contrast to reflective
	 * injection points. For programmatically retrieving a list of beans matching a
	 * specific type, specify the actual bean type as an argument here and subsequently
	 * use {@link ObjectProvider#orderedStream()} or its lazy streaming/iteration options.
	 * @return a corresponding provider handle
	 * @since 5.1
	 * @see ObjectProvider#iterator()
	 * @see ObjectProvider#stream()
	 * @see ObjectProvider#orderedStream()
	 */
	<T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType);

	/**
	 * Does this bean factory contain a bean definition or externally registered singleton
	 * instance with the given name?
	 * 注：判断当前Bean工厂是否包含指定名称的bean定义或者外部注册进来的bean单例。
	 * <p>If the given name is an alias, it will be translated back to the corresponding
	 * canonical bean name.
	 * 注：如果指定的名称是一个别名，该方法会被转换回相应标准bean名称。
	 * <p>If this factory is hierarchical, will ask any parent factory if the bean cannot
	 * be found in this factory instance.
	 * 注：如果当前工厂存在父工厂，当前工厂未找到的bean实例会尝试从父工厂中寻找。
	 * <p>If a bean definition or singleton instance matching the given name is found,
	 * this method will return {@code true} whether the named bean definition is concrete
	 * or abstract, lazy or eager, in scope or not. Therefore, note that a {@code true}
	 * return value from this method does not necessarily indicate that {@link #getBean}
	 * will be able to obtain an instance for the same name.
	 * 注：如果当前bean定义或者单例匹配指定的bean名称，该方法就会返回true，无论该bean定义是否抽象，是否懒加载，是否在作用域内。
	 * 因此，当前方法返回true，并不意味着就能够获取到bean实例。
	 * @param name the name of the bean to query
	 * @return whether a bean with the given name is present
	 */
	boolean containsBean(String name);

	/**
	 * Is this bean a shared singleton? That is, will {@link #getBean} always
	 * return the same instance?
	 * 注：是否指定bean是单例？单例意味着获取bean实例为同一个对象。
	 * <p>Note: This method returning {@code false} does not clearly indicate
	 * independent instances. It indicates non-singleton instances, which may correspond
	 * to a scoped bean as well. Use the {@link #isPrototype} operation to explicitly
	 * check for independent instances.
	 * 注：当前方法返回false，并不意味着该bean是原型对象。这些实例也可能对应于作用域。判断原型使用isPrototype方法。
	 * <p>Translates aliases back to the corresponding canonical bean name.
	 * <p>Will ask the parent factory if the bean cannot be found in this factory instance.
	 * - 传入的bean名称可能是个别名或者是个工厂bean名，方法内部会将该名称转换为规范的bean名称。
	 * - 如果需要获取的bean实例不在当前工厂实例中，会尝试从父工厂中获取该实例。
	 * @param name the name of the bean to query
	 * @return whether this bean corresponds to a singleton instance
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @see #getBean
	 * @see #isPrototype
	 */
	boolean isSingleton(String name) throws NoSuchBeanDefinitionException;

	/**
	 * Is this bean a prototype? That is, will {@link #getBean} always return
	 * independent instances?
	 * 注：是否指定bean是原型对象？原型对象意味着获取bean实例为非同一个对象。
	 * <p>Note: This method returning {@code false} does not clearly indicate
	 * a singleton object. It indicates non-independent instances, which may correspond
	 * to a scoped bean as well. Use the {@link #isSingleton} operation to explicitly
	 * check for a shared singleton instance.
	 * 注：当前方法返回false，并不意味着该bean是单例对象。这些实例也可能对应于作用域。判断单例使用isSingleton方法。
	 * <p>Translates aliases back to the corresponding canonical bean name.
	 * <p>Will ask the parent factory if the bean cannot be found in this factory instance.
	 * 注：- 传入的bean名称可能是个别名或者是个工厂bean名，方法内部会将该名称转换为规范的bean名称。
	 * - 如果需要获取的bean实例不在当前工厂实例中，会尝试从父工厂中获取该实例。
	 * @param name the name of the bean to query
	 * @return whether this bean will always deliver independent instances
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @since 2.0.3
	 * @see #getBean
	 * @see #isSingleton
	 */
	boolean isPrototype(String name) throws NoSuchBeanDefinitionException;

	/**
	 * Check whether the bean with the given name matches the specified type.
	 * More specifically, check whether a {@link #getBean} call for the given name
	 * would return an object that is assignable to the specified target type.
	 * 注：检查是否指定名称的bean实例是否与指定类型匹配
	 * - 更具体的说，检查getBean方法返回指定名称的bean实例是否为指定的目标类型
	 * <p>Translates aliases back to the corresponding canonical bean name.
	 * <p>Will ask the parent factory if the bean cannot be found in this factory instance.
	 * 注：- 传入的bean名称可能是个别名或者是个工厂bean名，方法内部会将该名称转换为规范的bean名称。
	 * - 如果需要获取的bean实例不在当前工厂实例中，会尝试从父工厂中获取该实例。
	 * @param name the name of the bean to query
	 * @param typeToMatch the type to match against (as a {@code ResolvableType})
	 * @return {@code true} if the bean type matches,
	 * {@code false} if it doesn't match or cannot be determined yet
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @since 4.2
	 * @see #getBean
	 * @see #getType
	 */
	boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException;

	/**
	 * Check whether the bean with the given name matches the specified type.
	 * More specifically, check whether a {@link #getBean} call for the given name
	 * would return an object that is assignable to the specified target type.
	 * 注：检查是否指定名称的bean实例是否与指定类型匹配
	 * - 更具体的说，检查getBean方法返回指定名称的bean实例是否为指定的目标类型
	 * <p>Translates aliases back to the corresponding canonical bean name.
	 * <p>Will ask the parent factory if the bean cannot be found in this factory instance.
	 * 注：- 传入的bean名称可能是个别名或者是个工厂bean名，方法内部会将该名称转换为规范的bean名称。
	 * - 如果需要获取的bean实例不在当前工厂实例中，会尝试从父工厂中获取该实例。
	 * @param name the name of the bean to query
	 * @param typeToMatch the type to match against (as a {@code Class})
	 * @return {@code true} if the bean type matches,
	 * {@code false} if it doesn't match or cannot be determined yet
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @since 2.0.1
	 * @see #getBean
	 * @see #getType
	 */
	boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException;

	/**
	 * Determine the type of the bean with the given name. More specifically,
	 * determine the type of object that {@link #getBean} would return for the given name.
	 * 注：返回指定名称的Bean实例类型。更具体的说，返回getBean方法的指定名称的bean实例类型。
	 * <p>For a {@link FactoryBean}, return the type of object that the FactoryBean creates,
	 * as exposed by {@link FactoryBean#getObjectType()}. This may lead to the initialization
	 * of a previously uninitialized {@code FactoryBean} (see {@link #getType(String, boolean)}).
	 * 注：对于工厂Bean对象，该方法会返回其创建对象的类型。即getObjectType方法返回的类型。
	 * 这个方法可能会导致初始化未初始化的工厂Bean对象。
	 * <p>Translates aliases back to the corresponding canonical bean name.
	 * <p>Will ask the parent factory if the bean cannot be found in this factory instance.
	 * @param name the name of the bean to query
	 * @return the type of the bean, or {@code null} if not determinable
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @since 1.1.2
	 * @see #getBean
	 * @see #isTypeMatch
	 */
	@Nullable
	Class<?> getType(String name) throws NoSuchBeanDefinitionException;

	/**
	 * Determine the type of the bean with the given name. More specifically,
	 * determine the type of object that {@link #getBean} would return for the given name.
	 * 注：返回指定名称的Bean实例类型。更具体的说，返回getBean方法的指定名称的bean实例类型。
	 * <p>For a {@link FactoryBean}, return the type of object that the FactoryBean creates,
	 * as exposed by {@link FactoryBean#getObjectType()}. Depending on the
	 * {@code allowFactoryBeanInit} flag, this may lead to the initialization of a previously
	 * uninitialized {@code FactoryBean} if no early type information is available.
	 * 注：对于工厂Bean对象，该方法会返回其创建对象的类型。即getObjectType方法返回的类型。
	 * 取决于allowFactoryBeanInit参数，如果工厂Bean没有提前解析类型信息，这个方法可能会导致初始化未初始化的工厂Bean对象。
	 * <p>Translates aliases back to the corresponding canonical bean name.
	 * <p>Will ask the parent factory if the bean cannot be found in this factory instance.
	 * 注：- 传入的bean名称可能是个别名或者是个工厂bean名，方法内部会将该名称转换为规范的bean名称。
	 * - 如果需要获取的bean实例不在当前工厂实例中，会尝试从父工厂中获取该实例。
	 * @param name the name of the bean to query
	 * @param allowFactoryBeanInit whether a {@code FactoryBean} may get initialized
	 * just for the purpose of determining its object type
	 * @return the type of the bean, or {@code null} if not determinable
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * @since 5.2
	 * @see #getBean
	 * @see #isTypeMatch
	 */
	@Nullable
	Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException;

	/**
	 * Return the aliases for the given bean name, if any.
	 * 注：返回指定bean名称的别名
	 * <p>All of those aliases point to the same bean when used in a {@link #getBean} call.
	 * 注：返回的所有别名，当调用getBean方法时都返回同一bean实例。
	 * <p>If the given name is an alias, the corresponding original bean name
	 * and other aliases (if any) will be returned, with the original bean name
	 * being the first element in the array.
	 * 注：如果指定名称为一个别名，则该方法会返回相对应的原始bean名以及其他别名，并且原始bean名称会被放置在数组的第一顺位。
	 * <p>Will ask the parent factory if the bean cannot be found in this factory instance.
	 * 注：如果需要获取的bean实例不在当前工厂实例中，会尝试从父工厂中获取该实例。
	 * @param name the bean name to check for aliases
	 * @return the aliases, or an empty array if none
	 * @see #getBean
	 */
	String[] getAliases(String name);

}
