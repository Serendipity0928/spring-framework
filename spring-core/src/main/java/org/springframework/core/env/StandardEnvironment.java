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

package org.springframework.core.env;

/**
 * {@link Environment} implementation suitable for use in 'standard' (i.e. non-web)
 * applications.
 * 注：适用于标准应用(非web应用)场景下的环境实现类。
 *
 * <p>In addition to the usual functions of a {@link ConfigurableEnvironment} such as
 * property resolution and profile-related operations, this implementation configures two
 * default property sources, to be searched in the following order:
 * <ul>
 * <li>{@linkplain AbstractEnvironment#getSystemProperties() system properties}
 * <li>{@linkplain AbstractEnvironment#getSystemEnvironment() system environment variables}
 * </ul>
 * 注：除了可配置环境(ConfigurableEnvironment)所具有的常规功能(比如属性解析以及描述信息相关操作)之外，
 * 该实现类还配置了两个默认的属性源，在获取属性时将按照如下顺序：
 * 	1. JVM系统配置
 * 	2. 系统环境变量
 *
 * That is, if the key "xyz" is present both in the JVM system properties as well as in
 * the set of environment variables for the current process, the value of key "xyz" from
 * system properties will return from a call to {@code environment.getProperty("xyz")}.
 * This ordering is chosen by default because system properties are per-JVM, while
 * environment variables may be the same across many JVMs on a given system.  Giving
 * system properties precedence allows for overriding of environment variables on a
 * per-JVM basis.
 * 注：这也就是说，如果指定属性key("xyz")不仅存在于JVM系统属性上，也存在于当前进程的环境变量内，
 * 那么该环境对象获取指定属性key将会返回JVM系统属性的值。
 * - JVM系统属性优先于环境变量是由于系统属性是属于单个JVM的，而环境变量可能由多个JVM所共享。
 * - 这也使得对于单个JVM可以通过JVM属性变量来覆盖环境变量。
 *
 * <p>These default property sources may be removed, reordered, or replaced; and
 * additional property sources may be added using the {@link MutablePropertySources}
 * instance available from {@link #getPropertySources()}. See
 * {@link ConfigurableEnvironment} Javadoc for usage examples.
 * 注：这些默认的属性源可能会被移除、重排或者替换。
 * 并且可以通过getPropertySources方法获取的可变多属性源来增加其他属性源。
 * 具体可参考ConfigurableEnvironment的文档示例。
 *
 * <p>See {@link SystemEnvironmentPropertySource} javadoc for details on special handling
 * of property names in shell environments (e.g. Bash) that disallow period characters in
 * variable names.
 * 注：请查阅SystemEnvironmentPropertySource文档来了解属性名称在不允许句符号的shell环境中的处理。
 *
 * @author Chris Beams
 * @since 3.1
 * @see ConfigurableEnvironment
 * @see SystemEnvironmentPropertySource
 * @see org.springframework.web.context.support.StandardServletEnvironment
 */
public class StandardEnvironment extends AbstractEnvironment {

	/** System environment property source name: {@value}.
	 * 注：系统环境属性源的名称
	 * */
	public static final String SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME = "systemEnvironment";

	/** JVM system properties property source name: {@value}.
	 * 注：JVM系统属性源名称
	 * */
	public static final String SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME = "systemProperties";


	/**
	 * Customize the set of property sources with those appropriate for any standard
	 * Java environment:
	 * <ul>
	 * <li>{@value #SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME}
	 * <li>{@value #SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME}
	 * </ul>
	 * <p>Properties present in {@value #SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME} will
	 * take precedence over those in {@value #SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME}.
	 * 注：自定义适用于任何标准Java环境的属性源集合。
	 * 1. SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME：JVM系统属性源
	 * 2. SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME：系统环境属性源
	 * - JVM系统属性源中的属性将优先于系统环境属性源中的属性。
	 * @see AbstractEnvironment#customizePropertySources(MutablePropertySources)
	 * @see #getSystemProperties()
	 * @see #getSystemEnvironment()
	 */
	@Override
	protected void customizePropertySources(MutablePropertySources propertySources) {
		// 注：增加JVM系统属性源
		propertySources.addLast(
				new PropertiesPropertySource(SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, getSystemProperties()));
		// 注：增加系统环境属性源(addLast是最低优先级)
		propertySources.addLast(
				new SystemEnvironmentPropertySource(SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, getSystemEnvironment()));
	}

}
