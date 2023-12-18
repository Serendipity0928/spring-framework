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

package org.springframework.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.lang.Nullable;

/**
 * Static holder for local Spring properties, i.e. defined at the Spring library level.
 * 注：用于加载本地spring配置的“静态”持有对象。比如定义spring相关底层jar中的配置。
 * - “静态”：类使用final修饰，构造器私有，仅允许通过公共方法使用。
 *
 * <p>Reads a {@code spring.properties} file from the root of the Spring library classpath,
 * and also allows for programmatically setting properties through {@link #setProperty}.
 * When checking a property, local entries are being checked first, then falling back
 * to JVM-level system properties through a {@link System#getProperty} check.
 * 注：读取spring类路径下的“spring.properties”文件中的属性配置，且允许通过setProperty方法设置属性及属性值。
 * 在检查属性值时，会先检查本地的属性值，再检查JVM的系统属性值-System#getProperty。
 *
 * <p>This is an alternative way to set Spring-related system properties such as
 * "spring.getenv.ignore" and "spring.beaninfo.ignore", in particular for scenarios
 * where JVM system properties are locked on the target platform (e.g. WebSphere).
 * See {@link #setFlag} for a convenient way to locally set such flags to "true".
 * 注：这是一个设置spring相关系统属性的方式，比如“spring.getenv.ignore”、“spring.beaninfo.ignore”等属性的设置。
 * 尤其是在JVM系统属性无法在对应平台上获取时会使用该方式。并且该方式提供了setFlag方法方便去设置某些标识为true。
 *
 * @author Juergen Hoeller
 * @since 3.2.7
 * @see org.springframework.core.env.AbstractEnvironment#IGNORE_GETENV_PROPERTY_NAME
 * @see org.springframework.beans.CachedIntrospectionResults#IGNORE_BEANINFO_PROPERTY_NAME
 * @see org.springframework.jdbc.core.StatementCreatorUtils#IGNORE_GETPARAMETERTYPE_PROPERTY_NAME
 * @see org.springframework.test.context.cache.ContextCache#MAX_CONTEXT_CACHE_SIZE_PROPERTY_NAME
 */
public final class SpringProperties {

	// 注：本地配置属性文件名
	private static final String PROPERTIES_RESOURCE_LOCATION = "spring.properties";

	private static final Log logger = LogFactory.getLog(SpringProperties.class);

	// 注：属性配置存储变量
	private static final Properties localProperties = new Properties();


	static {
		// 注：将classpath目录下的spring.properties文件加载到localProperties属性中
		// 【疑问，如果多个jar或应用resource都有该文件，就不太确定了。】因此，推荐使用SpringProperties#setProperty...
		try {
			ClassLoader cl = SpringProperties.class.getClassLoader();
			URL url = (cl != null ? cl.getResource(PROPERTIES_RESOURCE_LOCATION) :
					ClassLoader.getSystemResource(PROPERTIES_RESOURCE_LOCATION));
			if (url != null) {
				logger.debug("Found 'spring.properties' file in local classpath");
				try (InputStream is = url.openStream()) {
					localProperties.load(is);
				}
			}
		}
		catch (IOException ex) {
			if (logger.isInfoEnabled()) {
				logger.info("Could not load 'spring.properties' file from local classpath: " + ex);
			}
		}
	}


	private SpringProperties() {
	}


	/**
	 * Programmatically set a local property, overriding an entry in the
	 * 注：设置(覆盖)本地属性key及其值。如果值指定为null，则删除该key
	 * {@code spring.properties} file (if any).
	 * @param key the property key
	 * @param value the associated property value, or {@code null} to reset it
	 */
	public static void setProperty(String key, @Nullable String value) {
		if (value != null) {
			localProperties.setProperty(key, value);
		}
		else {
			localProperties.remove(key);
		}
	}

	/**
	 * Retrieve the property value for the given key, checking local Spring
	 * properties first and falling back to JVM-level system properties.
	 * 注：返回指定key对应的属性值。先检查本地spring属性，再检查JVM系统属性
	 * @param key the property key
	 * @return the associated property value, or {@code null} if none found
	 */
	@Nullable
	public static String getProperty(String key) {
		String value = localProperties.getProperty(key);
		if (value == null) {
			try {
				value = System.getProperty(key);
			}
			catch (Throwable ex) {
				if (logger.isDebugEnabled()) {
					logger.debug("Could not retrieve system property '" + key + "': " + ex);
				}
			}
		}
		return value;
	}

	/**
	 * Programmatically set a local flag to "true", overriding an
	 * entry in the {@code spring.properties} file (if any).
	 * 注：程序中设置本地属性值为true
	 * @param key the property key
	 */
	public static void setFlag(String key) {
		localProperties.put(key, Boolean.TRUE.toString());
	}

	/**
	 * Retrieve the flag for the given property key.
	 * 注：返回指定key的属性值是否为“true”
	 * @param key the property key
	 * @return {@code true} if the property is set to "true",
	 * {@code} false otherwise
	 */
	public static boolean getFlag(String key) {
		return Boolean.parseBoolean(getProperty(key));
	}

}
