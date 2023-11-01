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

package org.springframework.beans.factory;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;

import org.springframework.core.MethodParameter;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * A simple descriptor for an injection point, pointing to a method/constructor
 * parameter or a field. Exposed by {@link UnsatisfiedDependencyException}.
 * Also available as an argument for factory methods, reacting to the
 * requesting injection point for building a customized bean instance.
 * 注：InjectionPoint用于一个切入点的简单描述，指向一个方法或构造器参数或者一个属性。
 * 该切入点可由UnsatisfiedDependencyException异常排除。
 * 也可以作为工厂方法的参数，对请求的切入点处理以构件自定义的bean实例。
 *
 * @author Juergen Hoeller
 * @since 4.3
 * @see UnsatisfiedDependencyException#getInjectionPoint()
 * @see org.springframework.beans.factory.config.DependencyDescriptor
 */
public class InjectionPoint {

	// 注：包装函数参数时用于保存所包装的函数参数，内含该参数的注解信息
	@Nullable
	protected MethodParameter methodParameter;

	// 注：包装成员属性时用于保存所包装的成员属性
	@Nullable
	protected Field field;

	// 注：包装成员属性时用于保存所包装的成员属性的注解信息
	@Nullable
	private volatile Annotation[] fieldAnnotations;


	/**
	 * Create an injection point descriptor for a method or constructor parameter.
	 * 注：用于创建了包装函数参数的切入点实例
	 * @param methodParameter the MethodParameter to wrap
	 */
	public InjectionPoint(MethodParameter methodParameter) {
		Assert.notNull(methodParameter, "MethodParameter must not be null");
		this.methodParameter = methodParameter;
	}

	/**
	 * Create an injection point descriptor for a field.
	 * 注：用于创建了包装成员属性的切入点实例
	 * @param field the field to wrap
	 */
	public InjectionPoint(Field field) {
		Assert.notNull(field, "Field must not be null");
		this.field = field;
	}

	/**
	 * Copy constructor.
	 * 注：用于复制一个切入点实例
	 * @param original the original descriptor to create a copy from
	 */
	protected InjectionPoint(InjectionPoint original) {
		this.methodParameter = (original.methodParameter != null ?
				new MethodParameter(original.methodParameter) : null);
		this.field = original.field;
		this.fieldAnnotations = original.fieldAnnotations;
	}

	/**
	 * Just available for serialization purposes in subclasses.
	 * 注：仅出于子类序列化目的
	 */
	protected InjectionPoint() {
	}


	/**
	 * Return the wrapped MethodParameter, if any.
	 * <p>Note: Either MethodParameter or Field is available.
	 * 注：返回所包装的函数参数，仅在当前对象用于包装函数参数时返回非null
	 * @return the MethodParameter, or {@code null} if none
	 */
	@Nullable
	public MethodParameter getMethodParameter() {
		return this.methodParameter;
	}

	/**
	 * Return the wrapped Field, if any.
	 * <p>Note: Either MethodParameter or Field is available.
	 * 注：返回所包装的成员属性，仅在当前对象用于包装成员属性时返回非null
	 * @return the Field, or {@code null} if none
	 */
	@Nullable
	public Field getField() {
		return this.field;
	}

	/**
	 * Return the wrapped MethodParameter, assuming it is present.
	 * 注：获取所包装的函数参数，不会为null，如果当前对象包装的不是函数参数则抛出异常IllegalStateException
	 * @return the MethodParameter (never {@code null})
	 * @throws IllegalStateException if no MethodParameter is available
	 * @since 5.0
	 */
	protected final MethodParameter obtainMethodParameter() {
		Assert.state(this.methodParameter != null, "Neither Field nor MethodParameter");
		return this.methodParameter;
	}

	/**
	 * Obtain the annotations associated with the wrapped field or method/constructor parameter.
	 * 注：获取所包装的依赖(方法参数或者成员属性)上的注解信息
	 */
	public Annotation[] getAnnotations() {
		if (this.field != null) {
			Annotation[] fieldAnnotations = this.fieldAnnotations;
			if (fieldAnnotations == null) {
				fieldAnnotations = this.field.getAnnotations();
				// 注：缓存，以便下次直接返回
				this.fieldAnnotations = fieldAnnotations;
			}
			return fieldAnnotations;
		}
		else {
			// 注：方法的注解信息，这里不需要缓存，MethodParameter对象内部会缓存
			return obtainMethodParameter().getParameterAnnotations();
		}
	}

	/**
	 * Retrieve a field/parameter annotation of the given type, if any.
	 * 注：获取所包装的依赖(方法参数或者成员属性)上的指定类型为annotationType的注解信息，
	 * 如果该类型的注解不存在，则返回null
	 * @param annotationType the annotation type to retrieve
	 * @return the annotation instance, or {@code null} if none found
	 * @since 4.3.9
	 */
	@Nullable
	public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
		return (this.field != null ? this.field.getAnnotation(annotationType) :
				obtainMethodParameter().getParameterAnnotation(annotationType));
	}

	/**
	 * Return the type declared by the underlying field or method/constructor parameter,
	 * indicating the injection type.
	 * 注：获取所包装的依赖(方法参数或者成员属性)的类型
	 */
	public Class<?> getDeclaredType() {
		return (this.field != null ? this.field.getType() : obtainMethodParameter().getParameterType());
	}

	/**
	 * Returns the wrapped member, containing the injection point.
	 * 注：1.如果所包装的依赖是成员属性则返回该成员属性，
	 *    2.如果所包装的依赖是成员方法参数,则返回对应的成员方法
	 * @return the Field / Method / Constructor as Member
	 */
	public Member getMember() {
		return (this.field != null ? this.field : obtainMethodParameter().getMember());
	}

	/**
	 * Return the wrapped annotated element.
	 * <p>Note: In case of a method/constructor parameter, this exposes
	 * the annotations declared on the method or constructor itself
	 * (i.e. at the method/constructor level, not at the parameter level).
	 * Use {@link #getAnnotations()} to obtain parameter-level annotations in
	 * such a scenario, transparently with corresponding field annotations.
	 * 注：1.如果所包装的依赖是成员属性则返回该成员属性
	 *    2.如果所包装的依赖是成员方法参数,则返回对应的成员方法
	 *    可以认为该方法和 getMember()等价
	 * @return the Field / Method / Constructor as AnnotatedElement
	 */
	public AnnotatedElement getAnnotatedElement() {
		return (this.field != null ? this.field : obtainMethodParameter().getAnnotatedElement());
	}


	@Override
	public boolean equals(@Nullable Object other) {
		if (this == other) {
			return true;
		}
		if (other == null || getClass() != other.getClass()) {
			return false;
		}
		InjectionPoint otherPoint = (InjectionPoint) other;
		return (ObjectUtils.nullSafeEquals(this.field, otherPoint.field) &&
				ObjectUtils.nullSafeEquals(this.methodParameter, otherPoint.methodParameter));
	}

	@Override
	public int hashCode() {
		return (this.field != null ? this.field.hashCode() : ObjectUtils.nullSafeHashCode(this.methodParameter));
	}

	@Override
	public String toString() {
		return (this.field != null ? "field '" + this.field.getName() + "'" : String.valueOf(this.methodParameter));
	}

}
