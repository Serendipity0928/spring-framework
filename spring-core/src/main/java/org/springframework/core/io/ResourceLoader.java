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

package org.springframework.core.io;

import org.springframework.lang.Nullable;
import org.springframework.util.ResourceUtils;

/**
 * Strategy interface for loading resources (e.. class path or file system
 * resources). An {@link org.springframework.context.ApplicationContext}
 * is required to provide this functionality, plus extended
 * {@link org.springframework.core.io.support.ResourcePatternResolver} support.
 * 注：用于加载资源的策略接口，比如类路径或者文件系统的资源。
 * - 此加载资源的功能功能需要应用上下文(ApplicationContext)，再加上扩展接口(ResourcePatternResolver)的支持。
 *
 * <p>{@link DefaultResourceLoader} is a standalone implementation that is
 * usable outside an ApplicationContext, also used by {@link ResourceEditor}.
 * 注：默认资源加载器(DefaultResourceLoader)是一个标准的实现，可在应用上下文之外使用，也可以被资源编辑器(ResourceEditor)使用。
 *
 * <p>Bean properties of type Resource and Resource array can be populated
 * from Strings when running in an ApplicationContext, using the particular
 * context's resource loading strategy.
 * 注：在应用上下文场景下，bean的资源(Resource)或者资源数组(Resource[])
 * 可以使用特定的上下文资源加载策略从String类型的属性值中加载为Resource类型。
 *
 * @author Juergen Hoeller
 * @since 10.03.2004
 * @see Resource
 * @see org.springframework.core.io.support.ResourcePatternResolver
 * @see org.springframework.context.ApplicationContext
 * @see org.springframework.context.ResourceLoaderAware
 */
public interface ResourceLoader {

	/** Pseudo URL prefix for loading from the class path: "classpath:".
	 * 注：从类路径下加载资源的伪URL前缀："classpath:"
	 * */
	String CLASSPATH_URL_PREFIX = ResourceUtils.CLASSPATH_URL_PREFIX;


	/**
	 * Return a Resource handle for the specified resource location.
	 * <p>The handle should always be a reusable resource descriptor,
	 * allowing for multiple {@link Resource#getInputStream()} calls.
	 * <p><ul>
	 * <li>Must support fully qualified URLs, e.g. "file:C:/test.dat".
	 * <li>Must support classpath pseudo-URLs, e.g. "classpath:test.dat".
	 * <li>Should support relative file paths, e.g. "WEB-INF/test.dat".
	 * (This will be implementation-specific, typically provided by an
	 * ApplicationContext implementation.)
	 * </ul>
	 * <p>Note that a Resource handle does not imply an existing resource;
	 * you need to invoke {@link Resource#exists} to check for existence.
	 * 注：返回一个指定资源位置的资源句柄
	 * - 资源句柄应该始终是一个可重复使用的资源描述符，允许多次调用getInputStream获取输入流。
	 * - 资源位置的格式：
	 * 	  1. 必须支持URL的全限定符，比如“file:C:/test.dat”
	 * 	  2. 必须支持类路径的为URL，比如"classpath:test.dat"
	 * 	  3. 应该支持相对文件路径，比如"WEB-INF/test.dat"
	 * 	  	（该特性会被特定实现，通常由应用上下文提供）
	 * - 注意返回了资源对象，并不意味着该资源一定存在，你需要调用exists方法来检查其存在性。
	 *
	 * @param location the resource location
	 * @return a corresponding Resource handle (never {@code null})
	 * @see #CLASSPATH_URL_PREFIX
	 * @see Resource#exists()
	 * @see Resource#getInputStream()
	 */
	Resource getResource(String location);

	/**
	 * Expose the ClassLoader used by this ResourceLoader.
	 * 暴露当前资源加载器使用的类加载器。
	 * <p>Clients which need to access the ClassLoader directly can do so
	 * in a uniform manner with the ResourceLoader, rather than relying
	 * on the thread context ClassLoader.
	 * 注：需要直接访问类类加载器的客户端可以通过ResourceLoader以统一的方式访问，而不是依赖于线程上下文的类加载器。
	 * @return the ClassLoader
	 * (only {@code null} if even the system ClassLoader isn't accessible)
	 * 注：
	 * @see org.springframework.util.ClassUtils#getDefaultClassLoader()
	 * @see org.springframework.util.ClassUtils#forName(String, ClassLoader)
	 */
	@Nullable
	ClassLoader getClassLoader();

}
