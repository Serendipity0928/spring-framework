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

import java.util.Map;
import java.util.Properties;

/**
 * Configuration interface to be implemented by most if not all {@link Environment} types.
 * Provides facilities for setting active and default profiles and manipulating underlying
 * property sources. Allows clients to set and validate required properties, customize the
 * conversion service and more through the {@link ConfigurablePropertyResolver}
 * superinterface.
 * 注：几乎所有Environment的具体类型都会实现该配置型环境接口-ConfigurableEnvironment；
 * - 该配置型接口提供了设置激活的应用描述信息、设置默认的应用描述信息以及操作底层的属性源。
 * - 允许客户端去设置或者验证必须属性，自定义转换服务对象，以及更多通过ConfigurablePropertyResolver接口配置属性相关方法。
 *
 * <h2>Manipulating property sources</h2>
 * <p>Property sources may be removed, reordered, or replaced; and additional
 * property sources may be added using the {@link MutablePropertySources}
 * instance returned from {@link #getPropertySources()}. The following examples
 * are against the {@link StandardEnvironment} implementation of
 * {@code ConfigurableEnvironment}, but are generally applicable to any implementation,
 * though particular default property sources may differ.
 * 注：操作属性源
 * 属性源可以会被移除、重排或者替换。并且可以通过getPropertySources方法返回的MutablePropertySources实例来添额外的属性源。
 * 接下来的示例主要是以ConfigurableEnvironment的实现类-StandardEnvironment为背景，但是通常也会适用于任何其他实现类，尽管不同实现类的默认源可能不同。
 *
 * <h4>Example: adding a new property source with highest search priority</h4>
 * <pre class="code">
 * ConfigurableEnvironment environment = new StandardEnvironment();
 * MutablePropertySources propertySources = environment.getPropertySources();
 * Map&lt;String, String&gt; myMap = new HashMap&lt;&gt;();
 * myMap.put("xyz", "myValue");
 * propertySources.addFirst(new MapPropertySource("MY_MAP", myMap));
 * </pre>
 * 注：示例：以最高搜索优先级增加新的属性源--addFirst
 *
 * <h4>Example: removing the default system properties property source</h4>
 * <pre class="code">
 * MutablePropertySources propertySources = environment.getPropertySources();
 * propertySources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME)
 * </pre>
 * 注：示例：移除默认的系统属性源--remove
 *
 * <h4>Example: mocking the system environment for testing purposes</h4>
 * <pre class="code">
 * MutablePropertySources propertySources = environment.getPropertySources();
 * MockPropertySource mockEnvVars = new MockPropertySource().withProperty("xyz", "myValue");
 * propertySources.replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, mockEnvVars);
 * </pre>
 * 注：示例：为了测试目的，可以模拟系统环境-replace
 *
 * When an {@link Environment} is being used by an {@code ApplicationContext}, it is
 * important that any such {@code PropertySource} manipulations be performed
 * <em>before</em> the context's {@link
 * org.springframework.context.support.AbstractApplicationContext#refresh() refresh()}
 * method is called. This ensures that all property sources are available during the
 * container bootstrap process, including use by {@linkplain
 * org.springframework.context.support.PropertySourcesPlaceholderConfigurer property
 * placeholder configurers}.
 * 注：当在应用上下文中使用环境实例之前，必须确保在应用刷新之前已经操作了任何属性源。
 * 这确保了在容器启动流程时所有的属性源都可以被获取。比如属性源占位符配置器的需要。
 *
 * @author Chris Beams
 * @since 3.1
 * @see StandardEnvironment
 * @see org.springframework.context.ConfigurableApplicationContext#getEnvironment
 */
public interface ConfigurableEnvironment extends Environment, ConfigurablePropertyResolver {
	/**
	 * 注：可配置环境接口继承环境的读接口-Environment；
	 * 	  同时，可配置环境接口也拥有配置属性相关的方法，包括类型转换、占位符相关信息，必须属性配置等。
	 */

	/**
	 * Specify the set of profiles active for this {@code Environment}. Profiles are
	 * evaluated during container bootstrap to determine whether bean definitions
	 * should be registered with the container.
	 * 注：指定当前环境激活的一系列描述信息。这些激活的描述信息将会在容器启动时用于决定bean定义是否应该注册到该容器中。
	 * <p>Any existing active profiles will be replaced with the given arguments; call
	 * with zero arguments to clear the current set of active profiles. Use
	 * {@link #addActiveProfile} to add a profile while preserving the existing set.
	 * @throws IllegalArgumentException if any profile is null, empty or whitespace-only
	 * 注：任何已经激活的应用描述信息都会被该方法参数所替代。如果参数数量为0(即，空数组)，则会清除当前已激活描述信息的集合。
	 * 用户可使用addActiveProfile方法来添加单个描述信息同时保留之前已存在的激活描述信息集合。
	 * - 注意如果参数中任意一个描述信息名为null或空字符串或仅只有空字符，则会排除参数异常。
	 * @see #addActiveProfile
	 * @see #setDefaultProfiles
	 * @see org.springframework.context.annotation.Profile
	 * @see AbstractEnvironment#ACTIVE_PROFILES_PROPERTY_NAME
	 */
	void setActiveProfiles(String... profiles);

	/**
	 * Add a profile to the current set of active profiles.
	 * @throws IllegalArgumentException if the profile is null, empty or whitespace-only
	 * 注：将指定的描述信息添加到激活描述信息集合中
	 * - 注意如果参数中任意一个描述信息名为null或空字符串或仅只有空字符，则会排除参数异常。
	 * @see #setActiveProfiles
	 */
	void addActiveProfile(String profile);

	/**
	 * Specify the set of profiles to be made active by default if no other profiles
	 * are explicitly made active through {@link #setActiveProfiles}.
	 * @throws IllegalArgumentException if any profile is null, empty or whitespace-only
	 * 注：通过该方法指定一系列默认应用描述信息，如果用户没有明确通过setActiveProfiles方法来设置激活描述信息，则会采用默认描述信息为激活状态。
	 * - 注意如果参数中任意一个描述信息名为null或空字符串或仅只有空字符，则会排除参数异常。
	 * @see AbstractEnvironment#DEFAULT_PROFILES_PROPERTY_NAME
	 */
	void setDefaultProfiles(String... profiles);

	/**
	 * Return the {@link PropertySources} for this {@code Environment} in mutable form,
	 * allowing for manipulation of the set of {@link PropertySource} objects that should
	 * be searched when resolving properties against this {@code Environment} object.
	 * 注：该方法会返回当前环境的可变属性源对象，可变意味着允许对一系列属性源进行操作。
	 * 环境对象在解析属性信息时会搜索对应的属性源。
	 * The various {@link MutablePropertySources} methods such as
	 * {@link MutablePropertySources#addFirst addFirst},
	 * {@link MutablePropertySources#addLast addLast},
	 * {@link MutablePropertySources#addBefore addBefore} and
	 * {@link MutablePropertySources#addAfter addAfter} allow for fine-grained control
	 * over property source ordering. This is useful, for example, in ensuring that
	 * certain user-defined property sources have search precedence over default property
	 * sources such as the set of system properties or the set of system environment
	 * variables.
	 * 注：可变属性源对象的许多方法，比如addFirst、addLast、addBefore、addAfter等，允许对属性源顺序进行细粒度的控制。
	 * 这在一些情况下是非常有用的，比如确保用户特定的属性源搜索优先级高于默认属性源。默认属性源比如系统属性的集合或者系统环境变量的集合。
	 * @see AbstractEnvironment#customizePropertySources
	 */
	MutablePropertySources getPropertySources();

	/**
	 * Return the value of {@link System#getProperties()} if allowed by the current
	 * {@link SecurityManager}, otherwise return a map implementation that will attempt
	 * to access individual keys using calls to {@link System#getProperty(String)}.
	 * 注：该方法在当前环境安全的情况下返回系统属性值，否则会返回一个map类型的实现-通过System#getProperty方法来获取属性值。
	 * <p>Note that most {@code Environment} implementations will include this system
	 * properties map as a default {@link PropertySource} to be searched. Therefore, it is
	 * recommended that this method not be used directly unless bypassing other property
	 * sources is expressly intended.
	 * 注：大多数环境实现类会将系统属性映射作为默认属性源，并可以被搜索。
	 * 因此，如果不是存在要绕过其他属性源的目的，建议不要使用该方法。
	 * <p>Calls to {@link Map#get(Object)} on the Map returned will never throw
	 * {@link IllegalAccessException}; in cases where the SecurityManager forbids access
	 * to a property, {@code null} will be returned and an INFO-level log message will be
	 * issued noting the exception.
	 * 注：该方法返回的map类型的get方法实现不会抛出IllegalAccessException。
	 * 如果要获取安全管理器禁止访问的属性，该方法会返回null，并打印info日志。
	 */
	Map<String, Object> getSystemProperties();

	/**
	 * Return the value of {@link System#getenv()} if allowed by the current
	 * {@link SecurityManager}, otherwise return a map implementation that will attempt
	 * to access individual keys using calls to {@link System#getenv(String)}.
	 * 注：该方法在当前环境安全的情况下返回系统环境配置值，否则会返回一个map类型的实现-通过System#getenv方法来获取环境配置值。
	 * <p>Note that most {@link Environment} implementations will include this system
	 * environment map as a default {@link PropertySource} to be searched. Therefore, it
	 * is recommended that this method not be used directly unless bypassing other
	 * property sources is expressly intended.
	 * 注：大多数环境实现类会将系统环境变量映射作为默认属性源，并可以被搜索。
	 * 因此，如果不是存在要绕过其他属性源的目的，建议不要使用该方法。
	 * <p>Calls to {@link Map#get(Object)} on the Map returned will never throw
	 * {@link IllegalAccessException}; in cases where the SecurityManager forbids access
	 * to a property, {@code null} will be returned and an INFO-level log message will be
	 * issued noting the exception.
	 * 注：该方法返回的map类型的get方法实现不会抛出IllegalAccessException。
	 * 如果要获取安全管理器禁止访问的属性，该方法会返回null，并打印info日志。
	 */
	Map<String, Object> getSystemEnvironment();

	/**
	 * Append the given parent environment's active profiles, default profiles and
	 * property sources to this (child) environment's respective collections of each.
	 * 注：该方法将父环境的激活描述信息、默认描述信息以及属性源添加到子环境中对应的集合中去。
	 * <p>For any identically-named {@code PropertySource} instance existing in both
	 * parent and child, the child instance is to be preserved and the parent instance
	 * discarded. This has the effect of allowing overriding of property sources by the
	 * child as well as avoiding redundant searches through common property source types,
	 * e.g. system environment and system properties.
	 * 注：对于在父、子环境均存在的唯一属性源，在合并过程中，子环境中的会被保留，而父环境中会被舍弃。
	 * 这有具有子环境覆盖父环境的效果，以及避免对公共属性源的冗余搜索。
	 * <p>Active and default profile names are also filtered for duplicates, to avoid
	 * confusion and redundant storage.
	 * 注：存在重复的激活或默认的描述信息名也会被过滤，这避免了歧义以及冗余存储。
	 * <p>The parent environment remains unmodified in any case. Note that any changes to
	 * the parent environment occurring after the call to {@code merge} will not be
	 * reflected in the child. Therefore, care should be taken to configure parent
	 * property sources and profile information prior to calling {@code merge}.
	 * 注：在任何情况下该方法不会修改父环境配置。
	 * 注意在调用该方法之后，父环境配置的改变不会影响到子环境。
	 * 因此，用户应该注意要在调用合并操作之前配置父属性源以及描述信息。
	 * @param parent the environment to merge with
	 * @since 3.1.2
	 * @see org.springframework.context.support.AbstractApplicationContext#setParent
	 */
	void merge(ConfigurableEnvironment parent);

}
