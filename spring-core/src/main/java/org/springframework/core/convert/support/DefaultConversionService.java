/*
 * Copyright 2002-2017 the original author or authors.
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

package org.springframework.core.convert.support;

import java.nio.charset.Charset;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;

import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.ConverterRegistry;
import org.springframework.lang.Nullable;

/**
 * A specialization of {@link GenericConversionService} configured by default
 * with converters appropriate for most environments.
 * 注：DefaultConversionService是通用转换服务的具体实现类。默认情况下配置了适用于大多数环境的转换器。
 * - GenericConversionService：通用转换服务，实现了转换器的注册、匹配、转换、缓存等逻辑。
 * - DefaultConversionService：默认转换服务，配置(注册)默认的转换器
 *
 * <p>Designed for direct instantiation but also exposes the static
 * {@link #addDefaultConverters(ConverterRegistry)} utility method for ad-hoc
 * use against any {@code ConverterRegistry} instance.
 * 注：该类设计上是直接实例化使用，但是也提供了静态的addDefaultConverters方法，向指定转换注册中心注册默认转换器。
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @author Stephane Nicoll
 * @since 3.1
 */
public class DefaultConversionService extends GenericConversionService {

	// 注：静态公用的默认类型转换服务
	@Nullable
	private static volatile DefaultConversionService sharedInstance;


	/**
	 * Create a new {@code DefaultConversionService} with the set of
	 * {@linkplain DefaultConversionService#addDefaultConverters(ConverterRegistry) default converters}.
	 * 注：创建一个新的默认转换服务，并且自动注册添加默认的转换器。
	 */
	public DefaultConversionService() {
		addDefaultConverters(this);
	}


	/**
	 * Return a shared default {@code ConversionService} instance,
	 * lazily building it once needed.
	 * <p><b>NOTE:</b> We highly recommend constructing individual
	 * {@code ConversionService} instances for customization purposes.
	 * This accessor is only meant as a fallback for code paths which
	 * need simple type coercion but cannot access a longer-lived
	 * {@code ConversionService} instance any other way.
	 * 注：返回共享的默认转换服务，并且仅在需要时懒加载实例化。
	 * - 我们强烈推荐单独创建转换服务，用于自定义。
	 * - 该读方法仅用于降级、后备的目的。
	 * @return the shared {@code ConversionService} instance (never {@code null})
	 * @since 4.3.5
	 */
	public static ConversionService getSharedInstance() {
		DefaultConversionService cs = sharedInstance;
		if (cs == null) {
			synchronized (DefaultConversionService.class) {
				cs = sharedInstance;
				if (cs == null) {
					cs = new DefaultConversionService();
					sharedInstance = cs;
				}
			}
		}
		return cs;
	}

	/**
	 * Add converters appropriate for most environments.
	 * 注：向转换器注册中心注册用于大部分的环境中的默认转换器
	 * - 注意转换器的优先级问题。同一转换类型对的多个转换器，先注册的优先级更低
	 * @param converterRegistry the registry of converters to add to
	 * (must also be castable to ConversionService, e.g. being a {@link ConfigurableConversionService})
	 * @throws ClassCastException if the given ConverterRegistry could not be cast to a ConversionService
	 */
	public static void addDefaultConverters(ConverterRegistry converterRegistry) {
		// 注：注册常量相关的转换器
		addScalarConverters(converterRegistry);
		// 注：注册普通集合相关的转换器
		addCollectionConverters(converterRegistry);

		// 注：添加将【ByteBuffer类型】与【byte[]、Object类型】转换的转换器
		converterRegistry.addConverter(new ByteBufferConverter((ConversionService) converterRegistry));
		// 注：添加将【String对象】转换为【TimeZone对象】的转换器
		converterRegistry.addConverter(new StringToTimeZoneConverter());
		// 注：添加将【ZoneId对象】转换为【TimeZone对象】的转换器
		converterRegistry.addConverter(new ZoneIdToTimeZoneConverter());
		// 注：添加将【ZonedDateTime对象】转换为【Calendar对象】的转换器
		converterRegistry.addConverter(new ZonedDateTimeToCalendarConverter());

		// 注：添加将【Object对象】转换为【Object对象】的转换器
		converterRegistry.addConverter(new ObjectToObjectConverter());
		// 注：添加将【Object对象】转换为【Object对象】的转换器
		converterRegistry.addConverter(new IdToEntityConverter((ConversionService) converterRegistry));
		// 注：添加将【Object对象】转换为【String对象】的转换器
		converterRegistry.addConverter(new FallbackObjectToStringConverter());
		// 注：添加将【Optional对象】与【Collection、Object[]类型】转换的转换器
		converterRegistry.addConverter(new ObjectToOptionalConverter((ConversionService) converterRegistry));
	}

	/**
	 * Add common collection converters.
	 * 注：注册普通集合相关的转换器
	 * @param converterRegistry the registry of converters to add to
	 * (must also be castable to ConversionService, e.g. being a {@link ConfigurableConversionService})
	 * @throws ClassCastException if the given ConverterRegistry could not be cast to a ConversionService
	 * @since 4.2.3
	 */
	public static void addCollectionConverters(ConverterRegistry converterRegistry) {
		ConversionService conversionService = (ConversionService) converterRegistry;

		// 注：添加将【Object[]类型对象】转换为【Collection对象】的转换器
		converterRegistry.addConverter(new ArrayToCollectionConverter(conversionService));
		// 注：添加将【Collection对象】转换为【Object[]类型对象】的转换器
		converterRegistry.addConverter(new CollectionToArrayConverter(conversionService));

		// 注：添加将【Object[]对象】转换为【Object[]类型对象】的转换器
		converterRegistry.addConverter(new ArrayToArrayConverter(conversionService));
		// 注：添加将【Collection对象】转换为【Collection类型对象】的转换器
		converterRegistry.addConverter(new CollectionToCollectionConverter(conversionService));
		// 注：添加将【Map对象】转换为【Map类型对象】的转换器
		converterRegistry.addConverter(new MapToMapConverter(conversionService));

		// 注：添加将【Object[]对象】转换为【String类型对象】的转换器
		converterRegistry.addConverter(new ArrayToStringConverter(conversionService));
		// 注：添加将【String类型对象】转换为【Object[]对象】的转换器
		converterRegistry.addConverter(new StringToArrayConverter(conversionService));

		// 注：添加将【Object[]对象】转换为【Object类型对象】的转换器
		converterRegistry.addConverter(new ArrayToObjectConverter(conversionService));
		// 注：添加将【Object类型对象】转换为【Object[]对象】的转换器
		converterRegistry.addConverter(new ObjectToArrayConverter(conversionService));

		// 注：添加将【Collection对象】转换为【String类型对象】的转换器
		converterRegistry.addConverter(new CollectionToStringConverter(conversionService));
		// 注：添加将【String类型对象】转换为【Collection对象】的转换器
		converterRegistry.addConverter(new StringToCollectionConverter(conversionService));

		// 注：添加将【Collection对象】转换为【Object类型对象】的转换器
		converterRegistry.addConverter(new CollectionToObjectConverter(conversionService));
		// 注：添加将【Object类型对象】转换为【Collection对象】的转换器
		converterRegistry.addConverter(new ObjectToCollectionConverter(conversionService));

		// 注：添加将【Stream类型】与【Collection、Object[]类型】转换的转换器
		converterRegistry.addConverter(new StreamConverter(conversionService));
	}

	// 注：注册常量相关的转换器
	private static void addScalarConverters(ConverterRegistry converterRegistry) {
		// 注：添加转换器工厂。用于将【Number类型对象】转换为【Number某子类对象】
		// 由于具体哪个子类只有在转换时才确定，所以ConverterFactory也是范围类型转换器。
		converterRegistry.addConverterFactory(new NumberToNumberConverterFactory());

		// 注：添加将【String类型对象】转换为【Number某子类对象】的转换工厂
		converterRegistry.addConverterFactory(new StringToNumberConverterFactory());
		// 注：添加将【Number类型对象】转换为【String对象】的转换器
		converterRegistry.addConverter(Number.class, String.class, new ObjectToStringConverter());

		// 注：添加将【String对象】转换为【Character对象】的转换器
		converterRegistry.addConverter(new StringToCharacterConverter());
		// 注：添加将【Character对象】转换为【String对象】的转换器
		converterRegistry.addConverter(Character.class, String.class, new ObjectToStringConverter());

		// 注：添加将【Number对象】转换为【Character对象】的转换器
		converterRegistry.addConverter(new NumberToCharacterConverter());
		// 注：添加将【Character类型对象】转换为【Number某子类对象】的转换工厂
		converterRegistry.addConverterFactory(new CharacterToNumberFactory());

		// 注：添加将【String对象】转换为【Boolean对象】的转换器
		converterRegistry.addConverter(new StringToBooleanConverter());
		// 注：添加将【Boolean对象】转换为【String对象】的转换器
		converterRegistry.addConverter(Boolean.class, String.class, new ObjectToStringConverter());

		// 注：添加将【String对象】转换为【某Enum类对象】的转换工厂
		converterRegistry.addConverterFactory(new StringToEnumConverterFactory());
		// 注：添加将【Enum类对象】转换为【String对象】的转换器【注意这里将转换器注册中心传入进去，是为了条件判断是否可以转换】
		converterRegistry.addConverter(new EnumToStringConverter((ConversionService) converterRegistry));

		// 注：添加将【Integer对象】转换为【某Enum类对象】的转换工厂
		converterRegistry.addConverterFactory(new IntegerToEnumConverterFactory());
		// 注：添加将【Enum类对象】转换为【Integer对象】的转换器【注意这里将转换器注册中心传入进去，是为了条件判断是否可以转换】
		converterRegistry.addConverter(new EnumToIntegerConverter((ConversionService) converterRegistry));

		// 注：添加将【String对象】转换为【Locale对象】的转换器
		converterRegistry.addConverter(new StringToLocaleConverter());
		// 注：添加将【Locale对象】转换为【String对象】的转换器
		converterRegistry.addConverter(Locale.class, String.class, new ObjectToStringConverter());

		// 注：添加将【String对象】转换为【Charset对象】的转换器
		converterRegistry.addConverter(new StringToCharsetConverter());
		// 注：添加将【Charset对象】转换为【String对象】的转换器
		converterRegistry.addConverter(Charset.class, String.class, new ObjectToStringConverter());

		// 注：添加将【String对象】转换为【Currency对象】的转换器
		converterRegistry.addConverter(new StringToCurrencyConverter());
		// 注：添加将【Currency对象】转换为【String对象】的转换器
		converterRegistry.addConverter(Currency.class, String.class, new ObjectToStringConverter());

		// 注：添加将【String对象】转换为【Properties对象】的转换器
		converterRegistry.addConverter(new StringToPropertiesConverter());
		// 注：添加将【Properties对象】转换为【String对象】的转换器
		converterRegistry.addConverter(new PropertiesToStringConverter());

		// 注：添加将【String对象】转换为【UUID对象】的转换器
		converterRegistry.addConverter(new StringToUUIDConverter());
		// 注：添加将【UUID对象】转换为【String对象】的转换器
		converterRegistry.addConverter(UUID.class, String.class, new ObjectToStringConverter());
	}

}
