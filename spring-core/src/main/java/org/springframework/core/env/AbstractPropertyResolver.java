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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.PropertyPlaceholderHelper;
import org.springframework.util.SystemPropertyUtils;

/**
 * Abstract base class for resolving properties against any underlying source.
 * 注：针对于任何底层属性源解析属性的抽象基本类
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @since 3.1
 */
public abstract class AbstractPropertyResolver implements ConfigurablePropertyResolver {

	protected final Log logger = LogFactory.getLog(getClass());

	/**
	 * 注：可配置型转换服务。用于将属性值转换为指定的类型。
	 * - 允许用户通过setConversionService设置自定义转换服务
	 * - 默认会懒初始化为DefaultConversionService类型。
	 */
	@Nullable
	private volatile ConfigurableConversionService conversionService;

	// 注：解析属性占位符(形式为${XXX})-忽略无法解析的占位符
	@Nullable
	private PropertyPlaceholderHelper nonStrictHelper;

	// 注：解析属性占位符(形式为${XXX})-不会忽略无法解析的占位符
	@Nullable
	private PropertyPlaceholderHelper strictHelper;

	// 注：是否忽略无法解析的占位符
	private boolean ignoreUnresolvableNestedPlaceholders = false;

	// 注：占位符前缀：“${”
	private String placeholderPrefix = SystemPropertyUtils.PLACEHOLDER_PREFIX;

	// 注：占位符后缀："}"
	private String placeholderSuffix = SystemPropertyUtils.PLACEHOLDER_SUFFIX;

	// 注：系统属性占位符的默认值分隔符：":"
	@Nullable
	private String valueSeparator = SystemPropertyUtils.VALUE_SEPARATOR;

	// 注：必须存在的属性集合
	private final Set<String> requiredProperties = new LinkedHashSet<>();


	@Override
	public ConfigurableConversionService getConversionService() {
		// Need to provide an independent DefaultConversionService, not the
		// shared DefaultConversionService used by PropertySourcesPropertyResolver.
		// 注：默认转换服务对于属性源解析器来说是独立的，而非多个解析器共享。
		ConfigurableConversionService cs = this.conversionService;
		if (cs == null) {
			synchronized (this) {
				cs = this.conversionService;
				if (cs == null) {
					cs = new DefaultConversionService();
					this.conversionService = cs;
				}
			}
		}
		return cs;
	}

	// 注：设置在属性转换时所需用到的可配置型转换服务。
	@Override
	public void setConversionService(ConfigurableConversionService conversionService) {
		Assert.notNull(conversionService, "ConversionService must not be null");
		this.conversionService = conversionService;
	}

	/**
	 * Set the prefix that placeholders replaced by this resolver must begin with.
	 * <p>The default is "${".
	 * 注：设置占位符的前缀
	 * - 默认为“${”
	 * @see org.springframework.util.SystemPropertyUtils#PLACEHOLDER_PREFIX
	 */
	@Override
	public void setPlaceholderPrefix(String placeholderPrefix) {
		Assert.notNull(placeholderPrefix, "'placeholderPrefix' must not be null");
		this.placeholderPrefix = placeholderPrefix;
	}

	/**
	 * Set the suffix that placeholders replaced by this resolver must end with.
	 * <p>The default is "}".
	 * 注：设置占位符的后缀
	 * - 默认为“}”
	 * @see org.springframework.util.SystemPropertyUtils#PLACEHOLDER_SUFFIX
	 */
	@Override
	public void setPlaceholderSuffix(String placeholderSuffix) {
		Assert.notNull(placeholderSuffix, "'placeholderSuffix' must not be null");
		this.placeholderSuffix = placeholderSuffix;
	}

	/**
	 * Specify the separating character between the placeholders replaced by this
	 * resolver and their associated default value, or {@code null} if no such
	 * special character should be processed as a value separator.
	 * <p>The default is ":".
	 * 注：注：指定占位符与其关联的默认值之间的分隔符。
	 * - 默认为“:”
	 * @see org.springframework.util.SystemPropertyUtils#VALUE_SEPARATOR
	 */
	@Override
	public void setValueSeparator(@Nullable String valueSeparator) {
		this.valueSeparator = valueSeparator;
	}

	/**
	 * Set whether to throw an exception when encountering an unresolvable placeholder
	 * nested within the value of a given property. A {@code false} value indicates strict
	 * resolution, i.e. that an exception will be thrown. A {@code true} value indicates
	 * that unresolvable nested placeholders should be passed through in their unresolved
	 * ${...} form.
	 * <p>The default is {@code false}.
	 * 注：设置在遇到无法解析的属性值内嵌套的占位符时是否抛出异常。
	 * - 默认为false，则意味着严格的解析，遇到无法解析的占位符将抛出异常。
	 * @since 3.2
	 */
	@Override
	public void setIgnoreUnresolvableNestedPlaceholders(boolean ignoreUnresolvableNestedPlaceholders) {
		this.ignoreUnresolvableNestedPlaceholders = ignoreUnresolvableNestedPlaceholders;
	}

	// 注：指定哪些属性必须存在。
	@Override
	public void setRequiredProperties(String... requiredProperties) {
		Collections.addAll(this.requiredProperties, requiredProperties);
	}

	// 注：验证必须存在的属性的存在性，并且解析为非Null值。
	@Override
	public void validateRequiredProperties() {
		// 注：如果存在未找到的必须属性，则增加在异常信息中
		MissingRequiredPropertiesException ex = new MissingRequiredPropertiesException();
		for (String key : this.requiredProperties) {
			if (this.getProperty(key) == null) {
				ex.addMissingRequiredProperty(key);
			}
		}
		if (!ex.getMissingRequiredProperties().isEmpty()) {
			// 注：存在无法获取的属性，则抛出异常
			throw ex;
		}
	}

	// 注：返回指定的属性key是否可以被解析
	@Override
	public boolean containsProperty(String key) {
		return (getProperty(key) != null);
	}

	// 注：返回指定属性key对应的解析值。
	@Override
	@Nullable
	public String getProperty(String key) {
		// 注：返回指定属性key对应的解析值-字符串类型。【由其子类实现，子类通过属性源获取属性值，并将值转换到类型中】
		return getProperty(key, String.class);
	}

	// 注：返回指定属性key对应的解析值。
	// 如果该key无法被解析，将返回指定的默认值。
	@Override
	public String getProperty(String key, String defaultValue) {
		String value = getProperty(key);
		return (value != null ? value : defaultValue);
	}

	// 注：返回指定属性key对应的解析值。
	// 如果该key无法被解析，将返回指定的默认值。
	@Override
	public <T> T getProperty(String key, Class<T> targetType, T defaultValue) {
		T value = getProperty(key, targetType);
		return (value != null ? value : defaultValue);
	}

	// 注：返回指定属性key对应的解析值。
	@Override
	public String getRequiredProperty(String key) throws IllegalStateException {
		String value = getProperty(key);
		if (value == null) {
			throw new IllegalStateException("Required key '" + key + "' not found");
		}
		return value;
	}

	// 注：返回指定属性key对应的解析值并转换至指定的类型。
	@Override
	public <T> T getRequiredProperty(String key, Class<T> valueType) throws IllegalStateException {
		T value = getProperty(key, valueType);
		if (value == null) {
			throw new IllegalStateException("Required key '" + key + "' not found");
		}
		return value;
	}

	// 注：解析指定字符串中的“${XXX}”占位符。
	@Override
	public String resolvePlaceholders(String text) {
		if (this.nonStrictHelper == null) {
			// 注：延迟初始化非严格属性占位符解析助手。非严格-允许忽略无法解析的占位符
			this.nonStrictHelper = createPlaceholderHelper(true);
		}
		// 注：通过占位符解析助手来解析text中的占位符
		return doResolvePlaceholders(text, this.nonStrictHelper);
	}

	@Override
	public String resolveRequiredPlaceholders(String text) throws IllegalArgumentException {
		if (this.strictHelper == null) {
			// 注：延迟初始化严格属性占位符解析助手。严格-不允许忽略无法解析的占位符
			this.strictHelper = createPlaceholderHelper(false);
		}
		// 注：通过占位符解析助手来解析text中的占位符
		return doResolvePlaceholders(text, this.strictHelper);
	}

	/**
	 * Resolve placeholders within the given string, deferring to the value of
	 * {@link #setIgnoreUnresolvableNestedPlaceholders} to determine whether any
	 * unresolvable placeholders should raise an exception or be ignored.
	 * <p>Invoked from {@link #getProperty} and its variants, implicitly resolving
	 * nested placeholders. In contrast, {@link #resolvePlaceholders} and
	 * {@link #resolveRequiredPlaceholders} do <i>not</i> delegate
	 * to this method but rather perform their own handling of unresolvable
	 * placeholders, as specified by each of those methods.
	 * 注：解析指定字符串中的占位符，根据setIgnoreUnresolvableNestedPlaceholders方法设置的值来决定是否忽略无法解析的占位符。
	 * - 调用getProperty方法及其变体来获取属性值时会隐式解析可能存在嵌套占位符。
	 * - 相反，调用resolvePlaceholders以及resolveRequiredPlaceholders不会通过该方法解析嵌套占位符，而是通过其内部占位符解析助手来进行处理。
	 * @since 3.2
	 * @see #setIgnoreUnresolvableNestedPlaceholders
	 */
	protected String resolveNestedPlaceholders(String value) {
		if (value.isEmpty()) {
			return value;
		}
		// 注：根据ignoreUnresolvableNestedPlaceholders属性来决定通过那种方式来解析(嵌套)占位符
		return (this.ignoreUnresolvableNestedPlaceholders ?
				resolvePlaceholders(value) : resolveRequiredPlaceholders(value));
	}

	private PropertyPlaceholderHelper createPlaceholderHelper(boolean ignoreUnresolvablePlaceholders) {
		// 注：根据参数ignoreUnresolvablePlaceholders创建属性占位符解析助手
		return new PropertyPlaceholderHelper(this.placeholderPrefix, this.placeholderSuffix,
				this.valueSeparator, ignoreUnresolvablePlaceholders);
	}

	// 注：实际进行解析占位符的逻辑
	private String doResolvePlaceholders(String text, PropertyPlaceholderHelper helper) {
		// 注：通过传入的占位符解析助手来替换占位符。占位符通过getPropertyAsRawString方法来获取占位符对应的值
		return helper.replacePlaceholders(text, this::getPropertyAsRawString);
	}

	/**
	 * Convert the given value to the specified target type, if necessary.
	 * // 注：将指定值转换到特定的目标类型
	 * @param value the original property value
	 * @param targetType the specified target type for property retrieval
	 * @return the converted value, or the original value if no conversion
	 * is necessary
	 * @since 4.3.5
	 */
	@SuppressWarnings("unchecked")
	@Nullable
	protected <T> T convertValueIfNecessary(Object value, @Nullable Class<T> targetType) {
		if (targetType == null) {
			// 注：如果目标类型为null，则无需转换，直接返回值
			return (T) value;
		}
		ConversionService conversionServiceToUse = this.conversionService;
		if (conversionServiceToUse == null) {
			// Avoid initialization of shared DefaultConversionService if
			// no standard type conversion is needed in the first place...
			// 注：如果不确实需要标准类型转换，尽可能避免初始化共享默认转换服务
			if (ClassUtils.isAssignableValue(targetType, value)) {
				// 注：如果目标对象类型(targetType)是value对象的父类型，那么可以直接返回value，不需要转换。
				return (T) value;
			}
			// 注：未初始化当前属性解析器独有转换服务，默认使用共享默认转换服务。
			conversionServiceToUse = DefaultConversionService.getSharedInstance();
		}
		// 注：将指定的源对象转换为目标类型对象并返回。
		return conversionServiceToUse.convert(value, targetType);
	}


	/**
	 * Retrieve the specified property as a raw String,
	 * i.e. without resolution of nested placeholders.
	 * 注：检索指定的属性值-原始字符串类型，如未处理嵌套的占位符；
	 * - 在其子类中，resolveNestedPlaceholders=false
	 * @param key the property name to resolve
	 * @return the property value or {@code null} if none found
	 */
	@Nullable
	protected abstract String getPropertyAsRawString(String key);

}
