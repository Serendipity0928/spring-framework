/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.core.env;

/**
 * Interface representing the environment in which the current application is running.
 * Models two key aspects of the application environment: <em>profiles</em> and
 * <em>properties</em>. Methods related to property access are exposed via the
 * {@link PropertyResolver} superinterface.
 * 注：Environment接口是用于表示当前正在运行应用的环境信息。
 * 环境信息主要包括应用环境的2个关键方面：应用描述信息以及属性配置信息。其中读取属性相关的方法由父接口(PropertyResolver)暴露出去。
 * - Environment接口继承了PropertyResolver接口的读属性方法。
 *
 * <p>A <em>profile</em> is a named, logical group of bean definitions to be registered
 * with the container only if the given profile is <em>active</em>. Beans may be assigned
 * to a profile whether defined in XML or via annotations; see the spring-beans 3.1 schema
 * or the {@link org.springframework.context.annotation.Profile @Profile} annotation for
 * syntax details. The role of the {@code Environment} object with relation to profiles is
 * in determining which profiles (if any) are currently {@linkplain #getActiveProfiles
 * active}, and which profiles (if any) should be {@linkplain #getDefaultProfiles active
 * by default}.
 * 注：应用描述信息(profile)是一个具名的bean定义逻辑组。只有指定的profile是激活状态对应的该逻辑组的bean才会被注册到容器中。
 * spring bean可以通过XML或者注解的方式指定一个profile名称。注解方式从3.1版本开始支持，详细可参考该@Profile注解。
 * Environment对象对于应用描述信息的作用是确定哪个应用描述配置(如果有的话)是处于激活状态-getActiveProfiles，以及，
 * 哪个应用描述配置(如果有的话)是处于默认激活状态。
 *
 * <p><em>Properties</em> play an important role in almost all applications, and may
 * originate from a variety of sources: properties files, JVM system properties, system
 * environment variables, JNDI, servlet context parameters, ad-hoc Properties objects,
 * Maps, and so on. The role of the environment object with relation to properties is to
 * provide the user with a convenient service interface for configuring property sources
 * and resolving properties from them.
 * 注：属性配置信息(properties)在几乎所有的应用中都十分重要，并且可能来源于各种各样的配置源：配置文件、jvm系统属性、系统环境变量、
 * JNDI、web上下文参数、特殊属性对象、映射等等。
 * Environment对象对于属性配置信息的作用是向用户提供方便的接口配置属性源(好像没这个接口)以及解析已配置的属性。
 *
 * <p>Beans managed within an {@code ApplicationContext} may register to be {@link
 * org.springframework.context.EnvironmentAware EnvironmentAware} or {@code @Inject} the
 * {@code Environment} in order to query profile state or resolve properties directly.
 * 注：应用上下文中注册并管理的bean实例可能是EnvironmentAware类型或者通过@Inject注解注入Environment对象，
 * 以能够直接的获取应用描述属性状态或者解析属性配置。
 *
 * <p>In most cases, however, application-level beans should not need to interact with the
 * {@code Environment} directly but instead may have to have {@code ${...}} property
 * values replaced by a property placeholder configurer such as
 * {@link org.springframework.context.support.PropertySourcesPlaceholderConfigurer
 * PropertySourcesPlaceholderConfigurer}, which itself is {@code EnvironmentAware} and
 * as of Spring 3.1 is registered by default when using
 * {@code <context:property-placeholder/>}.
 * 注：然而， 在大多数情况下应用层的bean实例应该不需要直接与Environment对象直接交互，但是在一些情况下可能存在例外。
 * 比如必须通过属性占位符配置器解析"${XXX}"值，则需要通过PropertySourcesPlaceholderConfigurer实例来进行。
 * 从spring3.1版本开始，可以使用context配置自动注入该实例至spring容器中，并且该实例实现了EnvironmentAware接口。
 *
 * <p>Configuration of the environment object must be done through the
 * {@code ConfigurableEnvironment} interface, returned from all
 * {@code AbstractApplicationContext} subclass {@code getEnvironment()} methods. See
 * {@link ConfigurableEnvironment} Javadoc for usage examples demonstrating manipulation
 * of property sources prior to application context {@code refresh()}.
 * 注：环境对象的配置必须通过ConfigurableEnvironment接口提供的方法来完成。
 * AbstractApplicationContext及其子类的getEnvironment方法会返回可配置环境对象。
 * 可参考ConfigurableEnvironment说明文档中的示例，演示了在应用上下文刷新之前操作属性源。
 *
 * @author Chris Beams
 * @since 3.1
 * @see PropertyResolver
 * @see EnvironmentCapable
 * @see ConfigurableEnvironment
 * @see AbstractEnvironment
 * @see StandardEnvironment
 * @see org.springframework.context.EnvironmentAware
 * @see org.springframework.context.ConfigurableApplicationContext#getEnvironment
 * @see org.springframework.context.ConfigurableApplicationContext#setEnvironment
 * @see org.springframework.context.support.AbstractApplicationContext#createEnvironment
 */
public interface Environment extends PropertyResolver {

	/**
	 * Return the set of profiles explicitly made active for this environment. Profiles
	 * are used for creating logical groupings of bean definitions to be registered
	 * conditionally, for example based on deployment environment. Profiles can be
	 * activated by setting {@linkplain AbstractEnvironment#ACTIVE_PROFILES_PROPERTY_NAME
	 * "spring.profiles.active"} as a system property or by calling
	 * {@link ConfigurableEnvironment#setActiveProfiles(String...)}.
	 * <p>If no profiles have explicitly been specified as active, then any
	 * {@linkplain #getDefaultProfiles() default profiles} will automatically be activated.
	 * 注：该方法会返回当前环境所有激活状态的描述信息。
	 * 描述信息用于创建可条件性地注册一组逻辑上的bean定义，比如仅在开发(dev)环境上注册该bean实例。
	 * 描述信息可以通过设置系统属性配置"spring.profiles.active"或者调用setActiveProfiles方法来指定应用描述信息的激活状态。
	 * - 如果没有描述信息显示指定为激活状态，那么getDefaultProfiles方法返回的默认描述信息将会被自动激活。
	 * @see #getDefaultProfiles
	 * @see ConfigurableEnvironment#setActiveProfiles
	 * @see AbstractEnvironment#ACTIVE_PROFILES_PROPERTY_NAME
	 */
	String[] getActiveProfiles();

	/**
	 * Return the set of profiles to be active by default when no active profiles have
	 * been set explicitly.
	 * 如果没有描述信息显示指定为激活状态，那么该方法返回的默认描述信息将会被自动激活。
	 * @see #getActiveProfiles
	 * @see ConfigurableEnvironment#setDefaultProfiles
	 * @see AbstractEnvironment#DEFAULT_PROFILES_PROPERTY_NAME
	 */
	String[] getDefaultProfiles();

	/**
	 * Return whether one or more of the given profiles is active or, in the case of no
	 * explicit active profiles, whether one or more of the given profiles is included in
	 * the set of default profiles. If a profile begins with '!' the logic is inverted,
	 * i.e. the method will return {@code true} if the given profile is <em>not</em> active.
	 * For example, {@code env.acceptsProfiles("p1", "!p2")} will return {@code true} if
	 * profile 'p1' is active or 'p2' is not active.
	 * 注：返回指定的多个描述信息是否至少存在一个是激活状态，或者在没有显示指定激活描述信息时，判断指定的多个描述信息是否有一个为默认环境配置。
	 * 如果其中一个环境Profile以'!'为前缀，就意味着判断其是否为非激活状态。
	 * - 该接口入参可能存在多个描述信息，内部逻辑为or
	 * @throws IllegalArgumentException if called with zero arguments
	 * or if any profile is {@code null}, empty, or whitespace only
	 * @see #getActiveProfiles
	 * @see #getDefaultProfiles
	 * @see #acceptsProfiles(Profiles)
	 * @deprecated as of 5.1 in favor of {@link #acceptsProfiles(Profiles)}
	 * 注：从5.1之后推荐使用Profiles入参进行判断；Profiles提供了更强大的判断逻辑。
	 */
	@Deprecated
	boolean acceptsProfiles(String... profiles);

	/**
	 * Return whether the {@linkplain #getActiveProfiles() active profiles}
	 * match the given {@link Profiles} predicate.
	 * 注：返回当前环境激活的描述信息是否满足Profiles对象的断言条件。
	 * - 内部实现只需要调用Profiles的matches方法即可，不过需要指定描述信息的判定逻辑（一般是描述信息名是否激活状态的判断）
	 */
	boolean acceptsProfiles(Profiles profiles);

}
