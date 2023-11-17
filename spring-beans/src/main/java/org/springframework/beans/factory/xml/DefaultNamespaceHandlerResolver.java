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

package org.springframework.beans.factory.xml;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.FatalBeanException;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

/**
 * Default implementation of the {@link NamespaceHandlerResolver} interface.
 * Resolves namespace URIs to implementation classes based on the mappings
 * contained in mapping file.
 * 注：默认的命名空间处理器解析器的实现类。
 * 主要会缓存处理器映射文件(xxx.handlers)的内容，并将命名空间URI解析为对应的命名空间解析器实现类上。
 *
 * <p>By default, this implementation looks for the mapping file at
 * {@code META-INF/spring.handlers}, but this can be changed using the
 * {@link #DefaultNamespaceHandlerResolver(ClassLoader, String)} constructor.
 * 注：默认情况下，该实现类会寻找{META-INF/spring.handlers}内置文件来缓存对应的映射关系，
 * 当然你也可以自定义命名空间解析器或者自己实例化默认命名空间解析器并指定映射文件路径。
 *
 * 在XML场景中，XML的bean定义读取器在初始化读取上下文的时候会实例化该实例。
 * 具体可见：XmlBeanDefinitionReader#createReaderContext
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @since 2.0
 * @see NamespaceHandler
 * @see DefaultBeanDefinitionDocumentReader
 */
public class DefaultNamespaceHandlerResolver implements NamespaceHandlerResolver {

	/**
	 * The location to look for the mapping files. Can be present in multiple JAR files.
	 * 注：用于寻找所有映射关系的文件路径。可以存在于多个jar文件。
	 */
	public static final String DEFAULT_HANDLER_MAPPINGS_LOCATION = "META-INF/spring.handlers";


	/** Logger available to subclasses. */
	protected final Log logger = LogFactory.getLog(getClass());

	/** ClassLoader to use for NamespaceHandler classes.
	 * 注：用于加载映射文件以及命名空间处理器类型的类加载器
	 * */
	@Nullable
	private final ClassLoader classLoader;

	/** Resource location to search for.
	 * 注：用于存储寻找handlers映射文件路径，默认为DEFAULT_HANDLER_MAPPINGS_LOCATION
	 * */
	private final String handlerMappingsLocation;

	/** Stores the mappings from namespace URI to NamespaceHandler class name / instance. 、
	 * 注：缓存了命名空间URI到命名空间解析器(类名或Class对象)的映射关系
	 * 这个映射是采用的懒加载的形式更新。
	 * */
	@Nullable
	private volatile Map<String, Object> handlerMappings;


	/**
	 * Create a new {@code DefaultNamespaceHandlerResolver} using the
	 * default mapping file location.
	 * <p>This constructor will result in the thread context ClassLoader being used
	 * to load resources.
	 * 注：指定默认处理器映射文件(xxx.handlers)位置来创建默认的命名空间处理器解析器实例
	 * - 由于未指定类加载器，则在加载资源时会采用线程上下文类加载器进行加载
	 * @see #DEFAULT_HANDLER_MAPPINGS_LOCATION
	 */
	public DefaultNamespaceHandlerResolver() {
		this(null, DEFAULT_HANDLER_MAPPINGS_LOCATION);
	}

	/**
	 * Create a new {@code DefaultNamespaceHandlerResolver} using the
	 * default mapping file location.
	 * 注：指定默认处理器映射文件(xxx.handlers)位置来创建默认的命名空间处理器解析器实例
	 * - 可显示指定用于加载资源的类加载器【默认使用】
	 * @param classLoader the {@link ClassLoader} instance used to load mapping resources
	 * (may be {@code null}, in which case the thread context ClassLoader will be used)
	 * @see #DEFAULT_HANDLER_MAPPINGS_LOCATION
	 */
	public DefaultNamespaceHandlerResolver(@Nullable ClassLoader classLoader) {
		this(classLoader, DEFAULT_HANDLER_MAPPINGS_LOCATION);
	}

	/**
	 * Create a new {@code DefaultNamespaceHandlerResolver} using the
	 * supplied mapping file location.
	 * 注：指定处理器映射文件(xxx.handlers)位置来创建默认的命名空间处理器解析器实例
	 * - 可显示指定用于加载资源的类加载器
	 * @param classLoader the {@link ClassLoader} instance used to load mapping resources
	 * may be {@code null}, in which case the thread context ClassLoader will be used)
	 * @param handlerMappingsLocation the mapping file location
	 */
	public DefaultNamespaceHandlerResolver(@Nullable ClassLoader classLoader, String handlerMappingsLocation) {
		Assert.notNull(handlerMappingsLocation, "Handler mappings location must not be null");
		// 注：默认情况下是线程上下文加载器
		this.classLoader = (classLoader != null ? classLoader : ClassUtils.getDefaultClassLoader());
		// 注：指定处理器映射文件路径
		this.handlerMappingsLocation = handlerMappingsLocation;
	}


	/**
	 * Locate the {@link NamespaceHandler} for the supplied namespace URI
	 * from the configured mappings.
	 * 注：根据指定的命名空间URI返回配置的命名空间处理器实例
	 * @param namespaceUri the relevant namespace URI
	 * @return the located {@link NamespaceHandler}, or {@code null} if none found
	 */
	@Override
	@Nullable
	public NamespaceHandler resolve(String namespaceUri) {
		// 注：获取所有在xxx.handlers文件中配置的映射关系(命名空间uri到命名空间处理器类型的映射)
		Map<String, Object> handlerMappings = getHandlerMappings();
		Object handlerOrClassName = handlerMappings.get(namespaceUri);
		if (handlerOrClassName == null) {
			// 注：不存在当前命名空间uri对应的处理器类型
			return null;
		}
		else if (handlerOrClassName instanceof NamespaceHandler) {
			// 注：如果当前处理器已经实例化了，就直接返回即可
			return (NamespaceHandler) handlerOrClassName;
		}
		else {
			// 注：如果当前处理器value仍然是一个String类型，即处理器类名(也是类型路径)
			String className = (String) handlerOrClassName;
			try {
				// 注：使用指定的类加载器来加载该处理器类型
				Class<?> handlerClass = ClassUtils.forName(className, this.classLoader);
				if (!NamespaceHandler.class.isAssignableFrom(handlerClass)) {
					// 注：致命异常-自定义的处理器未实现NamespaceHandler接口
					throw new FatalBeanException("Class [" + className + "] for namespace [" + namespaceUri +
							"] does not implement the [" + NamespaceHandler.class.getName() + "] interface");
				}
				// 注：实例化命名空间处理器，使用无参构造器
				NamespaceHandler namespaceHandler = (NamespaceHandler) BeanUtils.instantiateClass(handlerClass);
				// 内部实例化之后会调用命名空间处理器的初始化方法。主要初始化bean定义解析器或者bean定义装饰器。
				namespaceHandler.init();
				// 注：将实例化后的实例替换之前的String类名
				handlerMappings.put(namespaceUri, namespaceHandler);
				return namespaceHandler;
			}
			catch (ClassNotFoundException ex) {
				throw new FatalBeanException("Could not find NamespaceHandler class [" + className +
						"] for namespace [" + namespaceUri + "]", ex);
			}
			catch (LinkageError err) {
				throw new FatalBeanException("Unresolvable class definition for NamespaceHandler class [" +
						className + "] for namespace [" + namespaceUri + "]", err);
			}
		}
	}

	/**
	 * Load the specified NamespaceHandler mappings lazily.
	 * 注：加载获取命名空间解析器的时候会懒加载所有的命名空间URI到命名空间处理器类的映射关系
	 */
	private Map<String, Object> getHandlerMappings() {
		Map<String, Object> handlerMappings = this.handlerMappings;
		if (handlerMappings == null) {
			synchronized (this) {
				handlerMappings = this.handlerMappings;
				if (handlerMappings == null) {
					// 注：这里也是双重检查锁
					if (logger.isTraceEnabled()) {
						logger.trace("Loading NamespaceHandler mappings from [" + this.handlerMappingsLocation + "]");
					}
					try {
						/**
						 * 注：通过指定的类加载器来加载所有jar下指定路径(默认：META-INF/spring.handlers)下的properties文件；
						 * spring在默认情况下有三个jar包存在handlers文件：
						 * 1. ../spring-beans/.../META-INF/spring.handlers
						 *	① http://www.springframework.org/schema/c=org.springframework.beans.factory.xml.SimpleConstructorNamespaceHandler
						 *  ② http://www.springframework.org/schema/p=org.springframework.beans.factory.xml.SimplePropertyNamespaceHandler
						 *  ③ http://www.springframework.org/schema/util=org.springframework.beans.factory.xml.UtilNamespaceHandler
						 * 2. ../spring-context/.../META-INF/spring.handlers
						 *	④ http://www.springframework.org/schema/context=org.springframework.context.config.ContextNamespaceHandler
						 *  ⑤ http://www.springframework.org/schema/jee=org.springframework.ejb.config.JeeNamespaceHandler
						 *  ⑥ http://www.springframework.org/schema/lang=org.springframework.scripting.config.LangNamespaceHandler
						 *  ⑦ http://www.springframework.org/schema/task=org.springframework.scheduling.config.TaskNamespaceHandler
						 *  ⑧ http://www.springframework.org/schema/cache=org.springframework.cache.config.CacheNamespaceHandler
						 * 3. ../spring-aop/.../META-INF/spring.handlers
						 *	⑨ http://www.springframework.org/schema/aop=org.springframework.aop.config.AopNamespaceHandler
						 */
						Properties mappings =
								PropertiesLoaderUtils.loadAllProperties(this.handlerMappingsLocation, this.classLoader);
						if (logger.isTraceEnabled()) {
							logger.trace("Loaded NamespaceHandler mappings: " + mappings);
						}
						handlerMappings = new ConcurrentHashMap<>(mappings.size());
						CollectionUtils.mergePropertiesIntoMap(mappings, handlerMappings);
						this.handlerMappings = handlerMappings;
					}
					catch (IOException ex) {
						throw new IllegalStateException(
								"Unable to load NamespaceHandler mappings from location [" + this.handlerMappingsLocation + "]", ex);
					}
				}
			}
		}
		return handlerMappings;
	}


	@Override
	public String toString() {
		return "NamespaceHandlerResolver using mappings " + getHandlerMappings();
	}

}
