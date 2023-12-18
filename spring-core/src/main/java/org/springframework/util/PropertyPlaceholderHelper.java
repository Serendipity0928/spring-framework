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

package org.springframework.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.lang.Nullable;

/**
 * Utility class for working with Strings that have placeholder values in them. A placeholder takes the form
 * {@code ${name}}. Using {@code PropertyPlaceholderHelper} these placeholders can be substituted for
 * user-supplied values. <p> Values for substitution can be supplied using a {@link Properties} instance or
 * using a {@link PlaceholderResolver}.
 * 注：用于处理具有占位符字符串的工具类，其中一个占位符的形式为${name}
 * - 使用PropertyPlaceholderHelper，这些占位符可以替换为用户提供的值。
 * - 替换值可以使用Properties实例或者PlaceholderResolver解析器对象。
 *
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @since 3.0
 */
public class PropertyPlaceholderHelper {

	private static final Log logger = LogFactory.getLog(PropertyPlaceholderHelper.class);

	/**
	 * 注：著名的简单前缀，包括{、[、(
	 * - 为了防止和占位符前缀匹配冲突，这里允许占位符中存在{XXX}、[XXX]或(XXX)
	 */
	private static final Map<String, String> wellKnownSimplePrefixes = new HashMap<>(4);

	static {
		wellKnownSimplePrefixes.put("}", "{");
		wellKnownSimplePrefixes.put("]", "[");
		wellKnownSimplePrefixes.put(")", "(");
	}

	// 注：占位符前缀
	private final String placeholderPrefix;

	// 注：占位符后缀
	private final String placeholderSuffix;

	// 注：简单前缀-非占位符
	private final String simplePrefix;

	// 注：默认值分隔符
	@Nullable
	private final String valueSeparator;

	// 注：是否忽略不能解析的占位符
	private final boolean ignoreUnresolvablePlaceholders;


	/**
	 * Creates a new {@code PropertyPlaceholderHelper} that uses the supplied prefix and suffix.
	 * Unresolvable placeholders are ignored.
	 * 注：创建一个新的属性占位符助手。该助手使用提供的占位符前缀以及后缀，并且不能解析的占位符默认会被忽略。
	 * @param placeholderPrefix the prefix that denotes the start of a placeholder
	 * @param placeholderSuffix the suffix that denotes the end of a placeholder
	 */
	public PropertyPlaceholderHelper(String placeholderPrefix, String placeholderSuffix) {
		this(placeholderPrefix, placeholderSuffix, null, true);
	}

	/**
	 * Creates a new {@code PropertyPlaceholderHelper} that uses the supplied prefix and suffix.
	 * 注：创建一个新的属性占位符助手。该助手使用提供的占位符前缀以及后缀。
	 * @param placeholderPrefix the prefix that denotes the start of a placeholder
	 * @param placeholderSuffix the suffix that denotes the end of a placeholder
	 * @param valueSeparator the separating character between the placeholder variable
	 * and the associated default value, if any
	 * 注：用于指定占位符变量与默认值之间的分隔符
	 * @param ignoreUnresolvablePlaceholders indicates whether unresolvable placeholders should
	 * be ignored ({@code true}) or cause an exception ({@code false})
	 */
	public PropertyPlaceholderHelper(String placeholderPrefix, String placeholderSuffix,
			@Nullable String valueSeparator, boolean ignoreUnresolvablePlaceholders) {

		Assert.notNull(placeholderPrefix, "'placeholderPrefix' must not be null");
		Assert.notNull(placeholderSuffix, "'placeholderSuffix' must not be null");
		this.placeholderPrefix = placeholderPrefix;
		this.placeholderSuffix = placeholderSuffix;
		String simplePrefixForSuffix = wellKnownSimplePrefixes.get(this.placeholderSuffix);
		if (simplePrefixForSuffix != null && this.placeholderPrefix.endsWith(simplePrefixForSuffix)) {
			// 注：检查是否为简单前缀，{}、[]、()
			this.simplePrefix = simplePrefixForSuffix;
		}
		else {
			this.simplePrefix = this.placeholderPrefix;
		}
		this.valueSeparator = valueSeparator;
		this.ignoreUnresolvablePlaceholders = ignoreUnresolvablePlaceholders;
	}


	/**
	 * Replaces all placeholders of format {@code ${name}} with the corresponding
	 * property from the supplied {@link Properties}.
	 * 注：利用通过提供的Properties对象来替换所有${name}形式的占位符
	 * @param value the value containing the placeholders to be replaced
	 * @param properties the {@code Properties} to use for replacement
	 * @return the supplied value with placeholders replaced inline
	 */
	public String replacePlaceholders(String value, final Properties properties) {
		Assert.notNull(properties, "'properties' must not be null");
		return replacePlaceholders(value, properties::getProperty);
	}

	/**
	 * Replaces all placeholders of format {@code ${name}} with the value returned
	 * from the supplied {@link PlaceholderResolver}.
	 * 注：利用通过提供的PlaceholderResolver对象来替换所有${name}形式的占位符
	 * @param value the value containing the placeholders to be replaced
	 * @param placeholderResolver the {@code PlaceholderResolver} to use for replacement
	 * @return the supplied value with placeholders replaced inline
	 */
	public String replacePlaceholders(String value, PlaceholderResolver placeholderResolver) {
		Assert.notNull(value, "'value' must not be null");
		return parseStringValue(value, placeholderResolver, null);
	}

	// 注：递归解析value字符串中的占位符并返回。【其中占位符获取其值的方法为placeholderResolver】
	protected String parseStringValue(
			String value, PlaceholderResolver placeholderResolver, @Nullable Set<String> visitedPlaceholders) {

		// 注：获取占位符前缀所在位置索引
		int startIndex = value.indexOf(this.placeholderPrefix);
		if (startIndex == -1) {
			// 注：如果没有找到，意味着不存在该占位符，直接返回即可
			return value;
		}

		StringBuilder result = new StringBuilder(value);
		while (startIndex != -1) {
			// 注：从startIndex寻找占位符结束的索引，-1表示未寻找到
			int endIndex = findPlaceholderEndIndex(result, startIndex);
			if (endIndex != -1) {
				// 注：获取寻找到的占位符
				String placeholder = result.substring(startIndex + this.placeholderPrefix.length(), endIndex);
				String originalPlaceholder = placeholder;
				if (visitedPlaceholders == null) {
					// 注：用于保证不存在循环匹配导致死循环
					visitedPlaceholders = new HashSet<>(4);
				}
				if (!visitedPlaceholders.add(originalPlaceholder)) {
					// 注：当前需要解析的占位符，如果之前就尝试解析过，说明存在死循环，需要抛出异常。
					throw new IllegalArgumentException(
							"Circular placeholder reference '" + originalPlaceholder + "' in property definitions");
				}
				// Recursive invocation, parsing placeholders contained in the placeholder key.
				// 注：递归调用解析包含在当前占位符中的占位符。
				placeholder = parseStringValue(placeholder, placeholderResolver, visitedPlaceholders);
				// Now obtain the value for the fully resolved key...
				// 注：通过占位符解析方法来获取当前占位符(此时内部不存在占位符)对应的解析值。
				String propVal = placeholderResolver.resolvePlaceholder(placeholder);
				if (propVal == null && this.valueSeparator != null) {
					// 注：如果当前占位符无法解析到值，并且该属性占位符解析助手存在默认值分隔符，寻找默认值分隔符索引
					int separatorIndex = placeholder.indexOf(this.valueSeparator);
					if (separatorIndex != -1) {
						// 注：无法解析占位符，但是该占位符存在默认值分隔符，获取分隔符之前的真正的占位符
						String actualPlaceholder = placeholder.substring(0, separatorIndex);
						// 注：获取分隔符之后的默认值
						String defaultValue = placeholder.substring(separatorIndex + this.valueSeparator.length());
						// 注：再次尝试解析真正的占位符
						propVal = placeholderResolver.resolvePlaceholder(actualPlaceholder);
						if (propVal == null) {
							// 注：如果占位符仍然无法解析，则获取其默认值
							propVal = defaultValue;
						}
					}
				}
				if (propVal != null) {
					// Recursive invocation, parsing placeholders contained in the
					// previously resolved placeholder value.
					// 注：递归解析已解析占位符值中的占位符。【有可能属性值中也存在占位符，保证返回的属性值不存在占位符】
					propVal = parseStringValue(propVal, placeholderResolver, visitedPlaceholders);
					// 注：执行替换占位符对应的解析值
					result.replace(startIndex, endIndex + this.placeholderSuffix.length(), propVal);
					if (logger.isTraceEnabled()) {
						logger.trace("Resolved placeholder '" + placeholder + "'");
					}
					// 注：获取下一个需要解析的占位符前缀。【不存在占位符就返回-1，跳出循环了】
					startIndex = result.indexOf(this.placeholderPrefix, startIndex + propVal.length());
				}
				else if (this.ignoreUnresolvablePlaceholders) {
					// Proceed with unprocessed value.
					// 注：如果当前占位符解析器助手允许忽略不能解析的占位符，则直接尝试获取下一个要解析的占位符前缀
					startIndex = result.indexOf(this.placeholderPrefix, endIndex + this.placeholderSuffix.length());
				}
				else {
					// 注：如果当前占位符解析器助手不允许忽略不能解析的占位符，则抛出异常
					throw new IllegalArgumentException("Could not resolve placeholder '" +
							placeholder + "'" + " in value \"" + value + "\"");
				}
				// 注：当前占位符已经被解析完成，从visitedPlaceholders中移除。
				visitedPlaceholders.remove(originalPlaceholder);
			}
			else {
				// 注：未寻找到占位符索引，返回-1，跳出循环。
				startIndex = -1;
			}
		}
		return result.toString();
	}

	/**
	 * 注：从startIndex寻找占位符结束的索引。
	 * - 占位符中允许存在“{}”、“[]”或者“()”，因此可能存在内嵌这些“占位符”
	 */
	private int findPlaceholderEndIndex(CharSequence buf, int startIndex) {
		int index = startIndex + this.placeholderPrefix.length();
		int withinNestedPlaceholder = 0;
		while (index < buf.length()) {
			if (StringUtils.substringMatch(buf, index, this.placeholderSuffix)) {
				// 注：匹配到占位符后缀，注意可能并非是真正的占位符
				if (withinNestedPlaceholder > 0) {
					// 注：这说明前面已经有了简单“占位符”前缀，需要继续匹配
					withinNestedPlaceholder--;
					index = index + this.placeholderSuffix.length();
				}
				else {
					// 注：寻找到占位符后缀所在位置，返回索引
					return index;
				}
			}
			else if (StringUtils.substringMatch(buf, index, this.simplePrefix)) {
				// 注：字符串中存在简单“占位符”，这里将withinNestedPlaceholder变量++，后续再匹配到冲突后缀时，需要继续匹配。
				withinNestedPlaceholder++;
				// 注：跳过该前缀，继续匹配
				index = index + this.simplePrefix.length();
			}
			else {
				// 注：索引增加，继续匹配
				index++;
			}
		}
		// 注：未寻找到占位符后缀所在位置，返回-1
		return -1;
	}


	/**
	 * Strategy interface used to resolve replacement values for placeholders contained in Strings.
	 * 注：用于解析字符串中占位符值的策略接口(函数式接口)
	 */
	@FunctionalInterface
	public interface PlaceholderResolver {

		/**
		 * Resolve the supplied placeholder name to the replacement value.
		 * 注：将提供的占位符解析为替换值
		 * @param placeholderName the name of the placeholder to resolve
		 * @return the replacement value, or {@code null} if no replacement is to be made
		 */
		@Nullable
		String resolvePlaceholder(String placeholderName);
	}

}
