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

package org.springframework.core.env;

import org.springframework.lang.Nullable;

/**
 * Interface for resolving properties against any underlying source.
 * 注：PropertyResolver是针对任何潜在属性源的解析接口
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @since 3.1
 * @see Environment
 * @see PropertySourcesPropertyResolver
 */
public interface PropertyResolver {
	/**
	 * 该接口提供了解析属性值的能力，包括判断属性是否存在，获取属性值(允许null)，获取属性值并指定默认值或类型。
	 * - 也提供了获取属性值时必须存在该属性的校验方法-requiredXXX；
	 * - 此外，也提供了解析"${}"占位符的方法【这个稍微有点不符合单一职责原则了】
	 */

	/**
	 * Return whether the given property key is available for resolution,
	 * i.e. if the value for the given key is not {@code null}.
	 * 注：返回指定的属性key是否可以被解析
	 * 具体实现如->判断指定属性key的值是否非null
	 */
	boolean containsProperty(String key);

	/**
	 * Return the property value associated with the given key,
	 * or {@code null} if the key cannot be resolved.
	 * 注：返回指定属性key对应的解析值。
	 * - 如果该key无法被解析，返回值为null。
	 * @param key the property name to resolve
	 * @see #getProperty(String, String)
	 * @see #getProperty(String, Class)
	 * @see #getRequiredProperty(String)
	 */
	@Nullable
	String getProperty(String key);

	/**
	 * Return the property value associated with the given key, or
	 * {@code defaultValue} if the key cannot be resolved.
	 * 注：返回指定属性key对应的解析值。
	 * - 如果该key无法被解析，将返回指定的默认值。
	 * @param key the property name to resolve
	 * @param defaultValue the default value to return if no value is found
	 * @see #getRequiredProperty(String)
	 * @see #getProperty(String, Class)
	 */
	String getProperty(String key, String defaultValue);

	/**
	 * Return the property value associated with the given key,
	 * or {@code null} if the key cannot be resolved.
	 * 注：返回指定属性key对应的解析值。
	 * - 如果该key无法被解析，返回值为null。
	 * @param key the property name to resolve
	 * @param targetType the expected type of the property value
	 * 注：通过targetType传入期望返回值的类型
	 * @see #getRequiredProperty(String, Class)
	 */
	@Nullable
	<T> T getProperty(String key, Class<T> targetType);

	/**
	 * Return the property value associated with the given key,
	 * or {@code defaultValue} if the key cannot be resolved.
	 * 注：返回指定属性key对应的解析值。
	 * - 如果该key无法被解析，将返回指定的默认值。
	 * @param key the property name to resolve
	 * @param targetType the expected type of the property value
	 * @param defaultValue the default value to return if no value is found
	 * @see #getRequiredProperty(String, Class)
	 */
	<T> T getProperty(String key, Class<T> targetType, T defaultValue);

	/**
	 * Return the property value associated with the given key (never {@code null}).
	 * 注：返回指定属性key对应的解析值。
	 * - 如果该key无法被解析，将抛出异常IllegalStateException(系统状态不合法异常)
	 * @throws IllegalStateException if the key cannot be resolved
	 * @see #getRequiredProperty(String, Class)
	 */
	String getRequiredProperty(String key) throws IllegalStateException;

	/**
	 * Return the property value associated with the given key, converted to the given
	 * targetType (never {@code null}).
	 * 注：返回指定属性key对应的解析值并转换至指定的类型。
	 * - 如果该key无法被解析，将抛出异常IllegalStateException(系统状态不合法异常)
	 * @throws IllegalStateException if the given key cannot be resolved
	 */
	<T> T getRequiredProperty(String key, Class<T> targetType) throws IllegalStateException;

	/**
	 * Resolve ${...} placeholders in the given text, replacing them with corresponding
	 * property values as resolved by {@link #getProperty}. Unresolvable placeholders with
	 * no default value are ignored and passed through unchanged.
	 * 注：解析指定字符串中的“${XXX}”占位符。将该占位符通过对应属性的值进行替换。
	 * 未指定默认值且不能够解析的占位符将不会进行改变。(因此获取属性值使用getProperty方法，允许返回null，然后不替换。)
	 * @param text the String to resolve
	 * @return the resolved String (never {@code null})
	 * @throws IllegalArgumentException if given text is {@code null}
	 * @see #resolveRequiredPlaceholders
	 */
	String resolvePlaceholders(String text);

	/**
	 * Resolve ${...} placeholders in the given text, replacing them with corresponding
	 * property values as resolved by {@link #getProperty}. Unresolvable placeholders with
	 * no default value will cause an IllegalArgumentException to be thrown.
	 * 注：解析指定字符串中的“${XXX}”占位符。将该占位符通过对应属性的值进行替换。
	 * 未指定默认值且不能够解析的占位符将会抛出异常。
	 * @return the resolved String (never {@code null})
	 * @throws IllegalArgumentException if given text is {@code null}
	 * or if any placeholders are unresolvable
	 */
	String resolveRequiredPlaceholders(String text) throws IllegalArgumentException;

}
