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

package org.springframework.core.convert.converter;

import java.util.Set;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Generic converter interface for converting between two or more types.
 * 注：用于转换两个或更多类型的通用转换器。
 *
 * <p>This is the most flexible of the Converter SPI interfaces, but also the most complex.
 * It is flexible in that a GenericConverter may support converting between multiple source/target
 * type pairs (see {@link #getConvertibleTypes()}. In addition, GenericConverter implementations
 * have access to source/target {@link TypeDescriptor field context} during the type conversion
 * process. This allows for resolving source and target field metadata such as annotations and
 * generics information, which can be used to influence the conversion logic.
 * 注：GenericConverter是最灵活的类型转换SPI接口，当然也是最复杂的。
 * - 通用转换器可以支持在多种源-目标类型对之间的转换(参见getConvertibleTypes)。
 * - 除此之外，通用转换器在类型转换过程中必须能够访问源、目标类型的上下文信息。
 * - 由于通用转换器能够获取到对象字段的元数据，比如注解以及通用信息，因此可以利用这些信息干预类型转换的逻辑。
 *
 * <p>This interface should generally not be used when the simpler {@link Converter} or
 * {@link ConverterFactory} interface is sufficient.
 * 注：如果能够通过Converter或者ConverterFactory接口来满足类型转换的要求，通常不会使用当前复杂性更高的接口。
 *
 * <p>Implementations may additionally implement {@link ConditionalConverter}.
 * 注：该接口的实现类可能会额外实现ConditionalConverter接口。
 *
 * @author Keith Donald
 * @author Juergen Hoeller
 * @since 3.0
 * @see TypeDescriptor
 * @see Converter
 * @see ConverterFactory
 * @see ConditionalConverter
 */
public interface GenericConverter {

	/**
	 * Return the source and target types that this converter can convert between.
	 * <p>Each entry is a convertible source-to-target type pair.
	 * <p>For {@link ConditionalConverter conditional converters} this method may return
	 * {@code null} to indicate all source-to-target pairs should be considered.
	 * 注：返回当前转换器可以转换的源、目标类型。
	 * - 返回值集合中的每一项都是可以满足源类型向目标类型进行转换的ConvertiblePair对象。
	 * - 如果当前实现类实现了ConditionalConverter接口，当前方法可能会返回null，表示所有ConvertiblePair可以被忽略。
	 */
	@Nullable
	Set<ConvertiblePair> getConvertibleTypes();

	/**
	 * Convert the source object to the targetType described by the {@code TypeDescriptor}.
	 * 注：将源对象转换为TypeDescriptor表示的目标类型对象。
	 * @param source the source object to convert (may be {@code null})
	 * @param sourceType the type descriptor of the field we are converting from
	 * @param targetType the type descriptor of the field we are converting to
	 * @return the converted object
	 */
	@Nullable
	Object convert(@Nullable Object source, TypeDescriptor sourceType, TypeDescriptor targetType);


	/**
	 * Holder for a source-to-target class pair.
	 * 注：用于持有源类型、目标类型的pair类型。
	 */
	final class ConvertiblePair {

		// 注：源类型
		private final Class<?> sourceType;

		// 注：目标类型
		private final Class<?> targetType;

		/**
		 * Create a new source-to-target pair.
		 * 注：创建一个新的源->目标类型对实例
		 * @param sourceType the source type
		 * @param targetType the target type
		 */
		public ConvertiblePair(Class<?> sourceType, Class<?> targetType) {
			Assert.notNull(sourceType, "Source type must not be null");
			Assert.notNull(targetType, "Target type must not be null");
			this.sourceType = sourceType;
			this.targetType = targetType;
		}

		public Class<?> getSourceType() {
			return this.sourceType;
		}

		public Class<?> getTargetType() {
			return this.targetType;
		}

		@Override
		public boolean equals(@Nullable Object other) {
			if (this == other) {
				return true;
			}
			if (other == null || other.getClass() != ConvertiblePair.class) {
				return false;
			}
			ConvertiblePair otherPair = (ConvertiblePair) other;
			return (this.sourceType == otherPair.sourceType && this.targetType == otherPair.targetType);
		}

		@Override
		public int hashCode() {
			return (this.sourceType.hashCode() * 31 + this.targetType.hashCode());
		}

		@Override
		public String toString() {
			return (this.sourceType.getName() + " -> " + this.targetType.getName());
		}
	}

}
