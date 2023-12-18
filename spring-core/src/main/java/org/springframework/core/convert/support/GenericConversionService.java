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

package org.springframework.core.convert.support;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.core.DecoratingProxy;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.ConversionException;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.ConverterNotFoundException;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.ConditionalConverter;
import org.springframework.core.convert.converter.ConditionalGenericConverter;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.core.convert.converter.ConverterRegistry;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.core.convert.converter.GenericConverter.ConvertiblePair;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.StringUtils;

/**
 * Base {@link ConversionService} implementation suitable for use in most environments.
 * Indirectly implements {@link ConverterRegistry} as registration API through the
 * {@link ConfigurableConversionService} interface.
 * 注：适用于大多数场景下的基本转换服务的实现类。
 * 通过ConfigurableConversionService接口实现的ConverterRegistry接口作为注册类型转换器的api
 *
 * @author Keith Donald
 * @author Juergen Hoeller
 * @author Chris Beams
 * @author Phillip Webb
 * @author David Haraburda
 * @since 3.0
 */
public class GenericConversionService implements ConfigurableConversionService {

	/**
	 * General NO-OP converter used when conversion is not required.
	 * 注：通用无操作类型转换器;
	 * - 源类型为目标类型的子类，就意味着不需要处理。
	 */
	private static final GenericConverter NO_OP_CONVERTER = new NoOpConverter("NO_OP");

	/**
	 * Used as a cache entry when no converter is available.
	 * This converter is never returned.
	 * 注：用于当没有转换器可用时的缓存项
	 * - 该转换器永远不会被返回，而会返回null
	 */
	private static final GenericConverter NO_MATCH = new NoOpConverter("NO_MATCH");

	/**
	 * 注：用于管理所有的转换器对象(GenericConverter)。
	 * - 管理注册到转换服务的所有的类型转换器，并且提供搜索满足条件的类型转换器方法
	 */
	private final Converters converters = new Converters();

	/**
	 * 注：缓存转换类型对的类型转换器。
	 * - 由于通过converters#find方法寻找类型转换器涉及到遍历及类型继承匹配逻辑，因此这里需要增加一层缓存。
	 * - 对于无法匹配的转换类型对，也会缓存为NO_MATCH
	 * - 有空深入研究下ConcurrentReferenceHashMap缓存工具-软引用
	 */
	private final Map<ConverterCacheKey, GenericConverter> converterCache = new ConcurrentReferenceHashMap<>(64);


	// ConverterRegistry implementation

	// 注：向转换注册中心添加一个普通的类型转换器
	@Override
	public void addConverter(Converter<?, ?> converter) {
		// 注：获取当前转换器的源、目标类型的可解析类型。
		ResolvableType[] typeInfo = getRequiredTypeInfo(converter.getClass(), Converter.class);
		if (typeInfo == null && converter instanceof DecoratingProxy) {
			// 注：在转换器为装饰代理的场景，需要找到其最终代理的目标类，然后再尝试获取转换参数类型。
			typeInfo = getRequiredTypeInfo(((DecoratingProxy) converter).getDecoratedClass(), Converter.class);
		}
		if (typeInfo == null) {
			// 注：此时校验转换器的源、目标类型。【所以这里校验是合理的，长度也应该在这里校验】
			throw new IllegalArgumentException("Unable to determine source type <S> and target type <T> for your " +
					"Converter [" + converter.getClass().getName() + "]; does the class parameterize those types?");
		}
		// 注：添加到转换器缓存中。使用ConverterAdapter来适配缓存中的GenericConverter类型，
		addConverter(new ConverterAdapter(converter, typeInfo[0], typeInfo[1]));
	}

	// 注：向转换注册中心添加一个普通的类型转换器
	@Override
	public <S, T> void addConverter(Class<S> sourceType, Class<T> targetType, Converter<? super S, ? extends T> converter) {
		// 注：入参指定了源类型、目标类型。因此不需要解析并校验了
		addConverter(new ConverterAdapter(
				converter, ResolvableType.forClass(sourceType), ResolvableType.forClass(targetType)));
	}

	@Override
	public void addConverter(GenericConverter converter) {
		// 注：向类型转换器管理对象添加一个通用类型转换器
		this.converters.add(converter);
		invalidateCache();
	}

	// 注：向转换注册中心添加一个范围转换工厂
	@Override
	public void addConverterFactory(ConverterFactory<?, ?> factory) {
		// 注：获取当前转换工厂的泛型参数类型S、R
		ResolvableType[] typeInfo = getRequiredTypeInfo(factory.getClass(), ConverterFactory.class);
		if (typeInfo == null && factory instanceof DecoratingProxy) {
			// 注：在转换器为装饰代理的场景，需要找到其最终代理的目标类，然后再尝试获取转换参数类型。
			typeInfo = getRequiredTypeInfo(((DecoratingProxy) factory).getDecoratedClass(), ConverterFactory.class);
		}
		if (typeInfo == null) {
			// 注：此时校验转换器的源、目标类型。【所以这里校验是合理的，长度也应该在这里校验】
			throw new IllegalArgumentException("Unable to determine source type <S> and target type <T> for your " +
					"ConverterFactory [" + factory.getClass().getName() + "]; does the class parameterize those types?");
		}
		// 注：添加到转换器缓存中。使用ConverterFactoryAdapter来适配缓存中的GenericConverter类型，
		addConverter(new ConverterFactoryAdapter(factory,
				new ConvertiblePair(typeInfo[0].toClass(), typeInfo[1].toClass())));
	}

	// 注：从类型转换器缓存中，移除类型对对应的类型转换器
	@Override
	public void removeConvertible(Class<?> sourceType, Class<?> targetType) {
		this.converters.remove(sourceType, targetType);
		invalidateCache();
	}


	// ConversionService implementation

	// 注：判断源类型是否可以转换为目标类型
	@Override
	public boolean canConvert(@Nullable Class<?> sourceType, Class<?> targetType) {
		Assert.notNull(targetType, "Target type to convert to cannot be null");
		return canConvert((sourceType != null ? TypeDescriptor.valueOf(sourceType) : null),
				TypeDescriptor.valueOf(targetType));
	}

	// 注：判断源类型是否可以转换为目标类型
	@Override
	public boolean canConvert(@Nullable TypeDescriptor sourceType, TypeDescriptor targetType) {
		Assert.notNull(targetType, "Target type to convert to cannot be null");
		if (sourceType == null) {
			// 注：实际转换后，目标对象一般是null
			return true;
		}
		// 注：尝试获取该类型对对应的转换器，能找到意即可以转换。
		GenericConverter converter = getConverter(sourceType, targetType);
		return (converter != null);
	}

	/**
	 * Return whether conversion between the source type and the target type can be bypassed.
	 * <p>More precisely, this method will return true if objects of sourceType can be
	 * converted to the target type by returning the source object unchanged.
	 * 注：判断源类型到目标类型之间的转换是否可以绕过
	 * - 更准确来说，如果源类型转换目标类型可以直接返回源对象，该方法就会返回true
	 * @param sourceType context about the source type to convert from
	 * (may be {@code null} if source is {@code null})
	 * @param targetType context about the target type to convert to (required)
	 * @return {@code true} if conversion can be bypassed; {@code false} otherwise
	 * @throws IllegalArgumentException if targetType is {@code null}
	 * @since 3.2
	 */
	public boolean canBypassConvert(@Nullable TypeDescriptor sourceType, TypeDescriptor targetType) {
		Assert.notNull(targetType, "Target type to convert to cannot be null");
		if (sourceType == null) {
			return true;
		}
		GenericConverter converter = getConverter(sourceType, targetType);
		// 注：如果寻找类型转换器返回NO_OP_CONVERTER，即可以直接返回源对象
		return (converter == NO_OP_CONVERTER);
	}

	// 注：将指定的源对象转换为目标类型对象并返回。
	@Override
	@SuppressWarnings("unchecked")
	@Nullable
	public <T> T convert(@Nullable Object source, Class<T> targetType) {
		Assert.notNull(targetType, "Target type to convert to cannot be null");
		return (T) convert(source, TypeDescriptor.forObject(source), TypeDescriptor.valueOf(targetType));
	}

	// 注：将指定的源对象转换为目标类型对象并返回。
	@Override
	@Nullable
	public Object convert(@Nullable Object source, @Nullable TypeDescriptor sourceType, TypeDescriptor targetType) {
		Assert.notNull(targetType, "Target type to convert to cannot be null");
		if (sourceType == null) {
			// 注：如果源类型为null，那么源对象也必须是null
			Assert.isTrue(source == null, "Source must be [null] if source type == [null]");
			// 注：处理结果(结果由convertNullSource返回null值处理)【注意目标类型为原型时不能返回null】
			return handleResult(null, targetType, convertNullSource(null, targetType));
		}
		if (source != null && !sourceType.getObjectType().isInstance(source)) {
			// 注：校验源对象是否符合指定的源类型的实例
			throw new IllegalArgumentException("Source to convert from must be an instance of [" +
					sourceType + "]; instead it was a [" + source.getClass().getName() + "]");
		}
		// 注：该方法用于查找指定源类型、目标类型对的类型转换器
		GenericConverter converter = getConverter(sourceType, targetType);
		if (converter != null) {
			// 注：执行类型转换操作
			Object result = ConversionUtils.invokeConverter(converter, source, sourceType, targetType);
			// 注：最后执行结果处理并返回
			return handleResult(sourceType, targetType, result);
		}
		// 注：在找不到类型转换器的情况下，通过无转换器处理逻辑返回
		return handleConverterNotFound(source, sourceType, targetType);
	}

	/**
	 * Convenience operation for converting a source object to the specified targetType,
	 * where the target type is a descriptor that provides additional conversion context.
	 * Simply delegates to {@link #convert(Object, TypeDescriptor, TypeDescriptor)} and
	 * encapsulates the construction of the source type descriptor using
	 * {@link TypeDescriptor#forObject(Object)}.
	 * 注：该方法时用于将源对象转换为指定的目标类型的便捷操作，其中目标类型是描述符，提供了额外的转换上下文。
	 * 具体实现上是委托给convert方法，内部封装了源类型描述符的创建过程。
	 * @param source the source object
	 * @param targetType the target type
	 * @return the converted value
	 * @throws ConversionException if a conversion exception occurred
	 * @throws IllegalArgumentException if targetType is {@code null},
	 * or sourceType is {@code null} but source is not {@code null}
	 */
	@Nullable
	public Object convert(@Nullable Object source, TypeDescriptor targetType) {
		// 注：在大多数情况下，调用方不需要考虑源类型描述符。只需要给出源对象以及目标类型描述符即可
		return convert(source, TypeDescriptor.forObject(source), targetType);
	}

	@Override
	public String toString() {
		return this.converters.toString();
	}


	// Protected template methods

	/**
	 * Template method to convert a {@code null} source.
	 * <p>The default implementation returns {@code null} or the Java 8
	 * {@link java.util.Optional#empty()} instance if the target type is
	 * {@code java.util.Optional}. Subclasses may override this to return
	 * custom {@code null} objects for specific target types.
	 * 注：用于转换null源对象的模版方法（子类可以覆盖实现）。
	 * - 默认的实现会返回null值。如果目标对象类型为Optional类型，则返回其空实例。
	 * - 子类可以自行重写返回指定目标类型的null对象。
	 * @param sourceType the source type to convert from
	 * @param targetType the target type to convert to
	 * @return the converted null object
	 */
	@Nullable
	protected Object convertNullSource(@Nullable TypeDescriptor sourceType, TypeDescriptor targetType) {
		if (targetType.getObjectType() == Optional.class) {
			return Optional.empty();
		}
		return null;
	}

	/**
	 * Hook method to lookup the converter for a given sourceType/targetType pair.
	 * First queries this ConversionService's converter cache.
	 * On a cache miss, then performs an exhaustive search for a matching converter.
	 * If no converter matches, returns the default converter.
	 * 注：该方法用于查找指定源类型、目标类型对的类型转换器
	 * - 首先通过转换服务的转换器缓存查找(converterCache)，如果缓存中查找不到的情况下，就通过converters管理进行匹配(耗时)。
	 * - 如果在转换器管理中也找不到的话，就返回默认类型转换器-null。
	 * @param sourceType the source type to convert from
	 * @param targetType the target type to convert to
	 * @return the generic converter that will perform the conversion,
	 * or {@code null} if no suitable converter was found
	 * @see #getDefaultConverter(TypeDescriptor, TypeDescriptor)
	 */
	@Nullable
	protected GenericConverter getConverter(TypeDescriptor sourceType, TypeDescriptor targetType) {
		// 注：构造转换器缓存key。在其具体实现上仅与源类型、目标类型有关。
		ConverterCacheKey key = new ConverterCacheKey(sourceType, targetType);
		GenericConverter converter = this.converterCache.get(key);
		if (converter != null) {
			// 注：在缓存命中的情况下，NO_MATCH意味着之前查找过没查找到，因此返回null
			return (converter != NO_MATCH ? converter : null);
		}

		// 注：缓存未命中的情况下，通过转换器管理对象的find方法来寻找、匹配类型转换器。
		converter = this.converters.find(sourceType, targetType);
		if (converter == null) {
			// 注：对于无法从已注册的类型转换器找到，返回默认转换器(NO_OP_CONVERTER或null)。
			converter = getDefaultConverter(sourceType, targetType);
		}

		if (converter != null) {
			// 注：如果存在可以使用的类型转换器，则缓存在converterCache之中
			this.converterCache.put(key, converter);
			return converter;
		}

		// 注：如果无法找到适合的类型转换器，也会缓存一个NO_MATCH在缓存中，以便于下次直接返回不存在。
		this.converterCache.put(key, NO_MATCH);
		return null;
	}

	/**
	 * Return the default converter if no converter is found for the given sourceType/targetType pair.
	 * <p>Returns a NO_OP Converter if the source type is assignable to the target type.
	 * Returns {@code null} otherwise, indicating no suitable converter could be found.
	 * 注：如果没有对应转换类型对对应的转换器，该方法返回默认转换器。
	 * - 如果源类型是为目标类型的子类，就意味着不需要处理，因此会返回NO_OP_CONVERTER
	 * - 其他情况下返回null，表示没有适合的类型转换器能够返回
	 * @param sourceType the source type to convert from
	 * @param targetType the target type to convert to
	 * @return the default generic converter that will perform the conversion
	 */
	@Nullable
	protected GenericConverter getDefaultConverter(TypeDescriptor sourceType, TypeDescriptor targetType) {
		// 注：如果源类型为目标类型的子类，就意味着不需要处理。
		return (sourceType.isAssignableTo(targetType) ? NO_OP_CONVERTER : null);
	}


	// Internal helpers

	/**
	 * 注：获取指定转换器类型所实现的泛型接口上的泛型参数数组。
	 * - 一般情况下converterClass指的就是类型转换器的class对象；
	 * - 如果genericIfc为Converter，那就是获取该接口指定的两个泛型参数的ResolvableType;
	 */
	@Nullable
	private ResolvableType[] getRequiredTypeInfo(Class<?> converterClass, Class<?> genericIfc) {
		// 注：获取converterClass类型指定接口的可解析类型。
		ResolvableType resolvableType = ResolvableType.forClass(converterClass).as(genericIfc);
		// 注：获取指定接口可解析类型的泛型参数类型数组
		ResolvableType[] generics = resolvableType.getGenerics();
		if (generics.length < 2) {
			// 注：这里判断参数长度至少为2。这个判断我认为不应该放在这里。
			return null;
		}
		Class<?> sourceType = generics[0].resolve();
		Class<?> targetType = generics[1].resolve();
		if (sourceType == null || targetType == null) {
			// 注：校验两个参数均不能为null
			return null;
		}
		return generics;
	}

	private void invalidateCache() {
		this.converterCache.clear();
	}

	// 注：在无转换器时，通过该部分逻辑处理结果并返回
	@Nullable
	private Object handleConverterNotFound(
			@Nullable Object source, @Nullable TypeDescriptor sourceType, TypeDescriptor targetType) {

		if (source == null) {
			// 注：如果源对象为null，无类型转换器也会返回null，但是注意原型目标类型会抛异常。
			assertNotPrimitiveTargetType(sourceType, targetType);
			return null;
		}
		if ((sourceType == null || sourceType.isAssignableTo(targetType)) &&
				targetType.getObjectType().isInstance(source)) {
			// 注：如果源对象为目标类型的实例，并且源类型是目标类型子类或null，则可以直接返回源对象
			return source;
		}
		// 注：其他情况下无法进行类型转换，只能抛出异常
		throw new ConverterNotFoundException(sourceType, targetType);
	}

	// 注：处理转换后的结果。【目标类型为原型时不能返回null】
	@Nullable
	private Object handleResult(@Nullable TypeDescriptor sourceType, TypeDescriptor targetType, @Nullable Object result) {
		if (result == null) {
			// 注：这里断言目标类型不能为原型，原型不能返回null
			assertNotPrimitiveTargetType(sourceType, targetType);
		}
		return result;
	}

	// 注：断言目标类型不能为原型。【原型不能返回null】
	private void assertNotPrimitiveTargetType(@Nullable TypeDescriptor sourceType, TypeDescriptor targetType) {
		if (targetType.isPrimitive()) {
			throw new ConversionFailedException(sourceType, targetType, null,
					new IllegalArgumentException("A null value cannot be assigned to a primitive type"));
		}
	}


	/**
	 * Adapts a {@link Converter} to a {@link GenericConverter}.
	 * 用于Converter类型到GenericConverter类型的适配器。【适配器也就是说将Converter当做GenericConverter类型来用】
	 * - 这里实现的是ConditionalGenericConverter接口，因此除了通用类型转换器之外，还具有条件性选择能力。
	 *
	 */
	@SuppressWarnings("unchecked")
	private final class ConverterAdapter implements ConditionalGenericConverter {

		// 注：真实类型转换器-Converter
		private final Converter<Object, Object> converter;

		// 注：通用类型转换器的转换类型对
		private final ConvertiblePair typeInfo;

		// 注：目标类型(可解析类型对象)
		private final ResolvableType targetType;

		public ConverterAdapter(Converter<?, ?> converter, ResolvableType sourceType, ResolvableType targetType) {
			this.converter = (Converter<Object, Object>) converter;
			this.typeInfo = new ConvertiblePair(sourceType.toClass(), targetType.toClass());
			this.targetType = targetType;
		}

		@Override
		public Set<ConvertiblePair> getConvertibleTypes() {
			return Collections.singleton(this.typeInfo);
		}

		@Override
		public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
			// Check raw type first...
			// 注：首先检查目标类型原始类型是否相同，不相同直接忽略
			if (this.typeInfo.getTargetType() != targetType.getObjectType()) {
				return false;
			}
			// Full check for complex generic type match required?
			// 注：其次检查目标类型的泛型类型是否匹配
			ResolvableType rt = targetType.getResolvableType();
			if (!(rt.getType() instanceof Class) && !rt.isAssignableFrom(this.targetType) &&
					!this.targetType.hasUnresolvableGenerics()) {
				// 注：① 目标类型是泛型； ② 目标类型不是当前转换器目标类型子类型； ③ 判断当前转换器目标类型存在不可解析泛型
				return false;
			}
			// 注：真实转换器如果实现了ConditionalConverter接口，就调用其matches方法来判断。否则通过。
			return !(this.converter instanceof ConditionalConverter) ||
					((ConditionalConverter) this.converter).matches(sourceType, targetType);
		}

		@Override
		@Nullable
		public Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
			if (source == null) {
				// 注：如果源对象为null，则根据convertNullSource方法获取null的处理返回值。（一般是null）
				return convertNullSource(sourceType, targetType);
			}
			// 注：调用实际Converter实例转换对象
			return this.converter.convert(source);
		}

		@Override
		public String toString() {
			return (this.typeInfo + " : " + this.converter);
		}
	}


	/**
	 * Adapts a {@link ConverterFactory} to a {@link GenericConverter}.
	 * 用于ConverterFactory类型到GenericConverter类型的适配器。【适配器也就是说将ConverterFactory当做GenericConverter类型来用】
	 * - 这里实现的是ConditionalGenericConverter接口，因此除了通用类型转换器之外，还具有条件性选择能力。
	 */
	@SuppressWarnings("unchecked")
	private final class ConverterFactoryAdapter implements ConditionalGenericConverter {

		// 注：真实类型转换器工厂-ConverterFactory
		private final ConverterFactory<Object, Object> converterFactory;

		// 注：通用类型转换器的转换类型对
		private final ConvertiblePair typeInfo;

		public ConverterFactoryAdapter(ConverterFactory<?, ?> converterFactory, ConvertiblePair typeInfo) {
			this.converterFactory = (ConverterFactory<Object, Object>) converterFactory;
			this.typeInfo = typeInfo;
		}

		@Override
		public Set<ConvertiblePair> getConvertibleTypes() {
			return Collections.singleton(this.typeInfo);
		}

		@Override
		public boolean matches(TypeDescriptor sourceType, TypeDescriptor targetType) {
			boolean matches = true;
			if (this.converterFactory instanceof ConditionalConverter) {
				// 注：如果当前转换器工厂也实现了ConditionalConverter接口，需要考虑其本身是否通过条件判断
				matches = ((ConditionalConverter) this.converterFactory).matches(sourceType, targetType);
			}
			if (matches) {
				Converter<?, ?> converter = this.converterFactory.getConverter(targetType.getType());
				if (converter instanceof ConditionalConverter) {
					// 注：在转换器工厂条件满足的情况下，还需要判断其返回的Converter是否实现了ConditionalConverter接口，也需要考虑其是否通过条件判断
					matches = ((ConditionalConverter) converter).matches(sourceType, targetType);
				}
			}
			return matches;
		}

		@Override
		@Nullable
		public Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
			if (source == null) {
				// 注：如果源对象为null，则根据convertNullSource方法获取null的处理返回值。（一般是null）
				return convertNullSource(sourceType, targetType);
			}
			// 注：调用转换器工厂获取类型转换器，然后将源对象进行转换为目标对象。
			return this.converterFactory.getConverter(targetType.getObjectType()).convert(source);
		}

		@Override
		public String toString() {
			return (this.typeInfo + " : " + this.converterFactory);
		}
	}


	/**
	 * Key for use with the converter cache.
	 */
	private static final class ConverterCacheKey implements Comparable<ConverterCacheKey> {

		private final TypeDescriptor sourceType;

		private final TypeDescriptor targetType;

		public ConverterCacheKey(TypeDescriptor sourceType, TypeDescriptor targetType) {
			this.sourceType = sourceType;
			this.targetType = targetType;
		}

		@Override
		public boolean equals(@Nullable Object other) {
			if (this == other) {
				return true;
			}
			if (!(other instanceof ConverterCacheKey)) {
				return false;
			}
			ConverterCacheKey otherKey = (ConverterCacheKey) other;
			return (this.sourceType.equals(otherKey.sourceType)) &&
					this.targetType.equals(otherKey.targetType);
		}

		@Override
		public int hashCode() {
			return (this.sourceType.hashCode() * 29 + this.targetType.hashCode());
		}

		@Override
		public String toString() {
			return ("ConverterCacheKey [sourceType = " + this.sourceType +
					", targetType = " + this.targetType + "]");
		}

		@Override
		public int compareTo(ConverterCacheKey other) {
			int result = this.sourceType.getResolvableType().toString().compareTo(
					other.sourceType.getResolvableType().toString());
			if (result == 0) {
				result = this.targetType.getResolvableType().toString().compareTo(
						other.targetType.getResolvableType().toString());
			}
			return result;
		}
	}


	/**
	 * Manages all converters registered with the service.
	 * 注：管理注册到转换服务的所有的类型转换器
	 */
	private static class Converters {

		/**
		 * 注：管理不具有转换类型对的类型转换器。
		 * 在获取转换器时，会遍历该缓存，并且通过其matches方法进行匹配，因此该缓存的类型转换器必须实现ConditionalConverter接口
		 */
		private final Set<GenericConverter> globalConverters = new LinkedHashSet<>();

		/**
		 * 注：管理所有具有转换类型对的类型转换器
		 * - ConvertiblePair维护了转换的源类型、目标类型。并且hashcode、toString方法仅与两类型相关。
		 * - 同样的源类型、目标类型的转换可能存在多个类型转换器实例，这些实例维护在ConvertersForPair中。
		 * - 有多个转换类型相同的转换器，检索时会返回首个的转换器。(如果实现条件转换，会过滤matches不匹配的转换器)
		 */
		private final Map<ConvertiblePair, ConvertersForPair> converters = new LinkedHashMap<>(36);

		// 注：添加类型转换器。【需要将所有非通用转换器类型通过适配器转换后添加】
		public void add(GenericConverter converter) {
			// 注：先获取通用转换器的支持的转换类型对
			Set<ConvertiblePair> convertibleTypes = converter.getConvertibleTypes();
			if (convertibleTypes == null) {
				// 注：如果未获取到类型转换对，这里校验该类型转换器必须实现ConditionalConverter接口
				// 一般情况下检索类型转换器是根据类型对来检索(即，ConvertiblePair)，但如果继承了ConditionalConverter接口就也可以通过matches来匹配转换器。
				Assert.state(converter instanceof ConditionalConverter,
						"Only conditional converters may return null convertible types");
				// 注：将不存在转换对的转换器存储在globalConverters缓存中，不限制转换前后的类型，是否能够转换由ConditionalConverter#matches来保证。
				this.globalConverters.add(converter);
			}
			else {
				for (ConvertiblePair convertiblePair : convertibleTypes) {
					// 注：将该转换类型对对应的类型转换器存储在this.converters缓存中
					ConvertersForPair convertersForPair = getMatchableConverters(convertiblePair);
					convertersForPair.add(converter);
				}
			}
		}

		// 注：从类型转换器缓存中，获取或者创建类型对对应的类型转换器存储对象。
		private ConvertersForPair getMatchableConverters(ConvertiblePair convertiblePair) {
			return this.converters.computeIfAbsent(convertiblePair, k -> new ConvertersForPair());
		}

		// 注：从类型转换器缓存中，移除类型对对应的类型转换器
		public void remove(Class<?> sourceType, Class<?> targetType) {
			this.converters.remove(new ConvertiblePair(sourceType, targetType));
		}

		/**
		 * Find a {@link GenericConverter} given a source and target type.
		 * <p>This method will attempt to match all possible converters by working
		 * through the class and interface hierarchy of the types.
		 * 注：根据给定的源、目标类型来找到一个通用的类型转换器。
		 * - 这个方法会尝试通过类型或接口的继承关系来匹配所有可能的转换器。
		 * @param sourceType the source type
		 * @param targetType the target type
		 * @return a matching {@link GenericConverter}, or {@code null} if none found
		 */
		@Nullable
		public GenericConverter find(TypeDescriptor sourceType, TypeDescriptor targetType) {
			// Search the full type hierarchy
			// 注：获取源类型、目标类型的继承类型队列
			List<Class<?>> sourceCandidates = getClassHierarchy(sourceType.getType());
			List<Class<?>> targetCandidates = getClassHierarchy(targetType.getType());
			for (Class<?> sourceCandidate : sourceCandidates) {
				for (Class<?> targetCandidate : targetCandidates) {
					// 注：依次遍历可能匹配的转换类型对
					ConvertiblePair convertiblePair = new ConvertiblePair(sourceCandidate, targetCandidate);
					// 注：根据类型转换对来获取满足条件的通用类型转换器
					GenericConverter converter = getRegisteredConverter(sourceType, targetType, convertiblePair);
					if (converter != null) {
						// 注：只要找到一个符合条件的类型转换器，立即返回
						return converter;
					}
				}
			}
			return null;
		}

		// 注：根据类型转换对来获取满足条件的通用类型转换器
		@Nullable
		private GenericConverter getRegisteredConverter(TypeDescriptor sourceType,
				TypeDescriptor targetType, ConvertiblePair convertiblePair) {

			// Check specifically registered converters
			// 注：首先检查通过转换类型对缓存的类型转换器
			ConvertersForPair convertersForPair = this.converters.get(convertiblePair);
			if (convertersForPair != null) {
				// 注：任一类型对对应的转换器可能存在多个，通过getConverter方法会获取首个类型转换器。当然前提是满足条件转换。
				GenericConverter converter = convertersForPair.getConverter(sourceType, targetType);
				if (converter != null) {
					// 注：只要找到类型转换器，立即返回
					return converter;
				}
			}
			// Check ConditionalConverters for a dynamic match
			// 注：检查未指定转换类型对的类型转换器，返回首个类型转换器。当然前提是满足条件转换。
			for (GenericConverter globalConverter : this.globalConverters) {
				if (((ConditionalConverter) globalConverter).matches(sourceType, targetType)) {
					return globalConverter;
				}
			}
			return null;
		}

		/**
		 * Returns an ordered class hierarchy for the given type.
		 * 注：返回给定类型的顺序的继承类型
		 * @param type the type
		 * @return an ordered list of all classes that the given type extends or implements
		 */
		private List<Class<?>> getClassHierarchy(Class<?> type) {
			// 注：存储当前类型的继承类型，按顺序存储在List中，最后返回。
			List<Class<?>> hierarchy = new ArrayList<>(20);
			// 注：缓存所有已遍历的类型，防止hierarchy重复。
			Set<Class<?>> visited = new HashSet<>(20);
			// 注：将当前类型添加到hierarchy索引0中。如果是原始类型，则添加的是包装类型。【注意这里不会是数组类型】
			addToClassHierarchy(0, ClassUtils.resolvePrimitiveIfNecessary(type), false, hierarchy, visited);
			boolean array = type.isArray();		// 注：如果当前是数组类型，之后继承队列中均为数组类型

			int i = 0;
			while (i < hierarchy.size()) {
				Class<?> candidate = hierarchy.get(i);
				// 注：获取候选类型；数组类型获取其组件类型，即内部类型。原型获取其包装类型；
				candidate = (array ? candidate.getComponentType() : ClassUtils.resolvePrimitiveIfNecessary(candidate));
				// 注：如果该类型存在父类且不为Object且不是枚举类，则将该父类添加到继承队列中
				Class<?> superclass = candidate.getSuperclass();
				if (superclass != null && superclass != Object.class && superclass != Enum.class) {
					// 注：将父类添加到继承队列的下一个(i+1)的索引处
					addToClassHierarchy(i + 1, candidate.getSuperclass(), array, hierarchy, visited);
				}
				// 注：将该类型所需要的接口添加到队列中
				addInterfacesToClassHierarchy(candidate, array, hierarchy, visited);
				i++;	// 逐个遍历队列中类型
			}

			if (Enum.class.isAssignableFrom(type)) {
				// 注：如果当前类型为枚举类型，则会将Enum[，Enum[]]，以及其实现的接口依次添加到继承队列中
				addToClassHierarchy(hierarchy.size(), Enum.class, array, hierarchy, visited);
				addToClassHierarchy(hierarchy.size(), Enum.class, false, hierarchy, visited);
				addInterfacesToClassHierarchy(Enum.class, array, hierarchy, visited);
			}

			// 注：将Object[，Object[]]，依次添加到继承队列中
			addToClassHierarchy(hierarchy.size(), Object.class, array, hierarchy, visited);
			addToClassHierarchy(hierarchy.size(), Object.class, false, hierarchy, visited);
			return hierarchy;
		}

		// 注：将指定类型的所实现的接口添加到继承图中
		private void addInterfacesToClassHierarchy(Class<?> type, boolean asArray,
				List<Class<?>> hierarchy, Set<Class<?>> visited) {

			for (Class<?> implementedInterface : type.getInterfaces()) {
				// 注：每次都添加到下一个位置
				addToClassHierarchy(hierarchy.size(), implementedInterface, asArray, hierarchy, visited);
			}
		}

		// 注：将指定的类型type尝试添加到hierarchy的索引Index处
		private void addToClassHierarchy(int index, Class<?> type, boolean asArray,
				List<Class<?>> hierarchy, Set<Class<?>> visited) {

			if (asArray) {
				// 注：如果当前是个数组的内部类型，则缓存数组类型
				type = Array.newInstance(type, 0).getClass();
			}
			if (visited.add(type)) {
				// 注：如果之前没有遍历到该类型，就添加到该index中。
				hierarchy.add(index, type);
			}
		}

		@Override
		public String toString() {
			StringBuilder builder = new StringBuilder();
			builder.append("ConversionService converters =\n");
			for (String converterString : getConverterStrings()) {
				builder.append('\t').append(converterString).append('\n');
			}
			return builder.toString();
		}

		private List<String> getConverterStrings() {
			List<String> converterStrings = new ArrayList<>();
			for (ConvertersForPair convertersForPair : this.converters.values()) {
				converterStrings.add(convertersForPair.toString());
			}
			Collections.sort(converterStrings);
			return converterStrings;
		}
	}


	/**
	 * Manages converters registered with a specific {@link ConvertiblePair}.
	 * 注：管理所有注册的具有相同转换类型对的类型转换器。
	 */
	private static class ConvertersForPair {

		// 注：管理所有具有相同转换类型对的类型转换器。
		private final LinkedList<GenericConverter> converters = new LinkedList<>();

		// 注：添加一个通用类型转换器【注意这里是addFirst，因此在遍历时遵循”栈“的行为】
		public void add(GenericConverter converter) {
			this.converters.addFirst(converter);
		}

		@Nullable
		public GenericConverter getConverter(TypeDescriptor sourceType, TypeDescriptor targetType) {
			// 在同样转换类型对的转换器实例中，后返回第一个能够条件匹配(如果有的话)的转换器。
			for (GenericConverter converter : this.converters) {
				if (!(converter instanceof ConditionalGenericConverter) ||
						((ConditionalGenericConverter) converter).matches(sourceType, targetType)) {
					return converter;
				}
			}
			return null;
		}

		@Override
		public String toString() {
			return StringUtils.collectionToCommaDelimitedString(this.converters);
		}
	}


	/**
	 * Internal converter that performs no operation.
	 * 注：表示无操作的内部(默认)转换器
	 * - 源类型为目标类型的子类，就意味着不需要处理。
	 */
	private static class NoOpConverter implements GenericConverter {

		private final String name;

		public NoOpConverter(String name) {
			this.name = name;
		}

		@Override
		public Set<ConvertiblePair> getConvertibleTypes() {
			return null;
		}

		@Override
		@Nullable
		public Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
			// 注：直接返回源对象即可
			return source;
		}

		@Override
		public String toString() {
			return this.name;
		}
	}

}
