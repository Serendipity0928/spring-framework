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
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;

import org.springframework.lang.Nullable;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Internal utility class that can be used to obtain wrapped {@link Serializable}
 * variants of {@link java.lang.reflect.Type java.lang.reflect.Types}.
 * 注：用于获取Type的系列化变型的内部工具类。
 *
 * <p>{@link #forField(Field) Fields} or {@link #forMethodParameter(MethodParameter)
 * MethodParameters} can be used as the root source for a serializable type.
 * Alternatively, a regular {@link Class} can also be used as source.
 * forField或者forMethodParameter可以作为序列化type类型的根源。
 * 同样，常规类对象也可以作为类型源。
 *
 * <p>The returned type will either be a {@link Class} or a serializable proxy of
 * {@link GenericArrayType}, {@link ParameterizedType}, {@link TypeVariable} or
 * {@link WildcardType}. With the exception of {@link Class} (which is final) calls
 * to methods that return further {@link Type Types} (for example
 * {@link GenericArrayType#getGenericComponentType()}) will be automatically wrapped.
 * 注：包装返回的类型要么是Class(已实现Serializable接口)，要么是一个Type泛型子类的支持序列化的代理。
 * 除了Class类型之外，对返回的类型Type进一步调用方法将会自动的进行包装。
 *
 * @author Phillip Webb
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 4.0
 * 参考：https://blog.csdn.net/dilixinxixitong2009/article/details/87345898、https://segmentfault.com/q/1010000044086117
 * Type接口参考资料：https://blog.csdn.net/tianzhonghaoqing/article/details/119705014
 */
final class SerializableTypeWrapper {

	// 注：支持序列化的类型-泛型数组类型、参数化类型、类型变量、通配符类型。
	private static final Class<?>[] SUPPORTED_SERIALIZABLE_TYPES = {
			GenericArrayType.class, ParameterizedType.class, TypeVariable.class, WildcardType.class};

	/**
	 * Whether this environment lives within a native image.
	 * 注：是否当前环境是在本地镜像中
	 * Exposed as a private static field rather than in a {@code NativeImageDetector.inNativeImage()} static method due to https://github.com/oracle/graal/issues/2594.
	 * @see <a href="https://github.com/oracle/graal/blob/master/sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/ImageInfo.java">ImageInfo.java</a>
	 */
	private static final boolean IN_NATIVE_IMAGE = (System.getProperty("org.graalvm.nativeimage.imagecode") != null);

	/**
	 * 注：用于存贮原泛化类型到包装后的类型(支持序列化)映射的全局缓存
	 */
	static final ConcurrentReferenceHashMap<Type, Type> cache = new ConcurrentReferenceHashMap<>(256);

	// 注：构造器私有化。此类当做工具类来用
	private SerializableTypeWrapper() {
	}


	/**
	 * Return a {@link Serializable} variant of {@link Field#getGenericType()}.
	 * 注：公共方法之1/2，支持返回属性(Field实例)的序列化变体。
	 */
	@Nullable
	public static Type forField(Field field) {
		// 注：首先将传入的属性对象使用对应TypeProvider包装起来，然后调用forTypeProvider返回可序列化变体
		return forTypeProvider(new FieldTypeProvider(field));
	}

	/**
	 * Return a {@link Serializable} variant of
	 * {@link MethodParameter#getGenericParameterType()}.
	 */
	@Nullable
	public static Type forMethodParameter(MethodParameter methodParameter) {
		return forTypeProvider(new MethodParameterTypeProvider(methodParameter));
	}

	/**
	 * Unwrap the given type, effectively returning the original non-serializable type.
	 * 注：对提供的类型进行反包装。即将可序列化的代理类型解包为非序列化类型
	 * @param type the type to unwrap
	 * @return the original non-serializable type
	 */
	@SuppressWarnings("unchecked")
	public static <T extends Type> T unwrap(T type) {
		Type unwrapped = null;
		if (type instanceof SerializableTypeProxy) {	// 注：代理类型会实现SerializableTypeProxy，并返回被代理类型
			unwrapped = ((SerializableTypeProxy) type).getTypeProvider().getType();
		}
		return (unwrapped != null ? (T) unwrapped : type);
	}

	/**
	 * Return a {@link Serializable} {@link Type} backed by a {@link TypeProvider} .
	 * <p>If type artifacts are generally not serializable in the current runtime
	 * environment, this delegate will simply return the original {@code Type} as-is.
	 * 注：根据TypeProvider实例(属性、方法参数、类型方法调用三者其一)来返回一个可序列化的类型。
	 * - 如果类型在当前运行环境时就是不可序列化的，那么该代理方法将直接跳过，返回原类型。
	 */
	@Nullable
	static Type forTypeProvider(TypeProvider provider) {
		Type providedType = provider.getType();		// 注：返回属性或方法参数的泛化类型
		if (providedType == null || providedType instanceof Serializable) {
			// No serializable type wrapping necessary (e.g. for java.lang.Class)
			// 注：null或已支持序列化能力的类型就不需要再进行包装，直接返回。
			return providedType;
		}
		if (IN_NATIVE_IMAGE || !Serializable.class.isAssignableFrom(Class.class)) {
			// Let's skip any wrapping attempts if types are generally not serializable in
			// the current runtime environment (even java.lang.Class itself, e.g. on GraalVM native images)
			// 注：如果类型在当前运行环境时就是不可序列化的，那么该代理方法将直接跳过，返回原类型。
			return providedType;
		}

		// Obtain a serializable type proxy for the given provider...
		// 注：下面就是针对给定的TypeProvider，获取一个可序列化类型代理
		Type cached = cache.get(providedType);		// 注：先查缓存中是否已有
		if (cached != null) {
			return cached;
		}

		/**
		 * 注：注意，只有这些SUPPORTED_SERIALIZABLE_TYPES类型才会进行处理，都是Type引入的泛化类型。
		 * 所以说，SerializableTypeWrapper类就是为了除了Type的泛型子类无法序列化问题，似乎也不太过分
		 */
		for (Class<?> type : SUPPORTED_SERIALIZABLE_TYPES) {
			if (type.isInstance(providedType)) {
				/**
				 * 注：下面就是具体如何给泛型类型支持可序列化代理的逻辑【JDK动态代理】：
				 * 1. 获取被代理的泛型类型的类加载器
				 * 2. 代理类需要实现的接口，包括需要代理的type接口，可序列化接口、支持返回代理TypeProvider接口
				 * 3. 实例化类型代理调用处理器实例。
				 * 	  处理器实例化需要传入TypeProvider，也即是需要被代理的对象。
				 * 调用JDK动态代理API获取动态类型代理实例，并投入缓存中去。
				 */
				ClassLoader classLoader = provider.getClass().getClassLoader();
				//
				Class<?>[] interfaces = new Class<?>[] {type, SerializableTypeProxy.class, Serializable.class};
				InvocationHandler handler = new TypeProxyInvocationHandler(provider);
				cached = (Type) Proxy.newProxyInstance(classLoader, interfaces, handler);
				cache.put(providedType, cached);
				return cached;
			}
		}
		throw new IllegalArgumentException("Unsupported Type class: " + providedType.getClass().getName());
	}


	/**
	 * Additional interface implemented by the type proxy.
	 * 注：用于代理类型实现的额外的接口-为支持代理类返回其要代理的TypeProvider
	 * （为代理类型解包做准备）
	 */
	interface SerializableTypeProxy {

		/**
		 * Return the underlying type provider.
		 * 注：返回内部代理的TypeProvider
		 */
		TypeProvider getTypeProvider();
	}


	/**
	 * A {@link Serializable} interface providing access to a {@link Type}
	 * 注：用于包装某具体类型(Type)的序列化接口
	 * Java序列化：https://www.anquanke.com/post/id/169563?display=mobile#h3-1
	 */
	@SuppressWarnings("serial")
	interface TypeProvider extends Serializable {

		/**
		 * Return the (possibly non {@link Serializable}) {@link Type}.
		 * 注：返回包装内部的类型（可能未实现Serializable）
		 */
		@Nullable
		Type getType();

		/**
		 * Return the source of the type, or {@code null} if not known.
		 * <p>The default implementations returns {@code null}.
		 * 注：类型的来源，即使当前类型是某属性的类型，亦或者方法返回类型；如果不知道就返回null
		 * - 来源给出了默认实现-即返回null
		 */
		@Nullable
		default Object getSource() {
			return null;
		}
	}


	/**
	 * {@link Serializable} {@link InvocationHandler} used by the proxied {@link Type}.
	 * Provides serialization support and enhances any methods that return {@code Type}
	 * or {@code Type[]}.
	 * 注：类型代理对象使用的支持序列化的调用处理器！
	 * 该调用处理器提供了序列化的支持，并且会增强任何返回类型为Type或Type[]方法
	 * (类型代理类要想实现可序列化，调用处理器必须也是可序列化的！所以这里实现了Serializable接口)
	 */
	@SuppressWarnings("serial")
	private static class TypeProxyInvocationHandler implements InvocationHandler, Serializable {

		// 注：实际被代理的类型，通过处理器构造方法传入
		private final TypeProvider provider;

		public TypeProxyInvocationHandler(TypeProvider provider) {
			this.provider = provider;
		}

		// 注：invoke方法是类型代理对象(proxy)执行某方法(method)的具体逻辑
		@Override
		@Nullable
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			switch (method.getName()) {
				case "equals":
					Object other = args[0];
					// Unwrap proxies for speed
					if (other instanceof Type) {	// 注：other可能是代理类，这里反向解包
						other = unwrap((Type) other);
					}
					return ObjectUtils.nullSafeEquals(this.provider.getType(), other);
				case "hashCode":
					return ObjectUtils.nullSafeHashCode(this.provider.getType());
				case "getTypeProvider":		// 注：这里就直接返回了被代理对象
					return this.provider;
			}

			if (Type.class == method.getReturnType() && ObjectUtils.isEmpty(args)) {
				/**
				 * 注：如果方法的返回值是Type类型，并且为无参方法；
				 * 这里就需要使用MethodInvokeTypeProvider对象对类型方法返回类型进行包装(代理)处理。
				 */
				return forTypeProvider(new MethodInvokeTypeProvider(this.provider, method, -1));
			}
			else if (Type[].class == method.getReturnType() && ObjectUtils.isEmpty(args)) {
				/**
				 * 注：如果方法的返回值是Type[]类型，并且为无参方法；
				 * 下面会先执行方法获取返回类型数组的长度。然后逐个索引对类型方法返回类型进行包装(代理)处理。
				 */
				Type[] result = new Type[((Type[]) method.invoke(this.provider.getType())).length];
				for (int i = 0; i < result.length; i++) {
					result[i] = forTypeProvider(new MethodInvokeTypeProvider(this.provider, method, i));
				}
				return result;
			}

			try {
				// 注：其他就是正常、非Type类型返回类型(也即是Class，本身就支持序列化)，交给provider的实际类型执行对应方法即可。
				return method.invoke(this.provider.getType(), args);
			}
			catch (InvocationTargetException ex) {
				throw ex.getTargetException();
			}
		}
	}


	/**
	 * {@link TypeProvider} for {@link Type Types} obtained from a {@link Field}.
	 * 注：将一个属性Field以及类型包装为FieldTypeProvider
	 * (使用代理的目的，感觉就是为了实现序列化能力)
	 */
	@SuppressWarnings("serial")
	static class FieldTypeProvider implements TypeProvider {

		// 注：属性(字段)名
		private final String fieldName;

		// 注：属性所在的类
		private final Class<?> declaringClass;

		/**
		 * 注：属性对象引用。
		 * transient意味着该字段不会被序列化，
		 * 反序列化后会利用readObject方法重新根据前两个字段通过反射获取。【可能不一定有原有的类结构，相当于检验】
		 * 【代理field】
		 */
		private transient Field field;

		// 注：根据一个field实例化FieldTypeProvider
		public FieldTypeProvider(Field field) {
			this.fieldName = field.getName();
			this.declaringClass = field.getDeclaringClass();
			this.field = field;
		}

		// 注：返回属性的通用类型-可能为泛型类型
		@Override
		public Type getType() {
			return this.field.getGenericType();
		}

		@Override
		public Object getSource() {
			return this.field;
		}

		// 注：提供readObject方法，反序列化时会调用该方法获取field属性。
		private void readObject(ObjectInputStream inputStream) throws IOException, ClassNotFoundException {
			inputStream.defaultReadObject();
			try {
				this.field = this.declaringClass.getDeclaredField(this.fieldName);
			}
			catch (Throwable ex) {
				throw new IllegalStateException("Could not find original class structure", ex);
			}
		}
	}


	/**
	 * {@link TypeProvider} for {@link Type Types} obtained from a {@link MethodParameter}.
	 * 注：将一个方法参数及其方法名、参数类型数组、声明类、参数索引等包装为MethodParameterTypeProvider
	 * (使用代理的目的，感觉就是为了实现序列化能力)
	 */
	@SuppressWarnings("serial")
	static class MethodParameterTypeProvider implements TypeProvider {

		// 注：方法名称(构造器为null)
		@Nullable
		private final String methodName;

		// 注：对应方法的参数类型列表【非泛型，和this.getType()区分】
		private final Class<?>[] parameterTypes;

		// 注：声明该方法的类型对象
		private final Class<?> declaringClass;

		// 注：返回方法/构造器参数的索引（-1为返回类型）
		private final int parameterIndex;

		// 注：当前方法参数类型对象（不可序列化）【代理methodParameter】
		private transient MethodParameter methodParameter;

		// 注：使用方法参数类型作为MethodParameterTypeProvider实例初始化依据
		public MethodParameterTypeProvider(MethodParameter methodParameter) {
			// 注：初始化方法名，这里调用的是getMethod方法。这就意味着包装构造器的实例methodName字段为null;
			this.methodName = (methodParameter.getMethod() != null ? methodParameter.getMethod().getName() : null);
			this.parameterTypes = methodParameter.getExecutable().getParameterTypes();
			this.declaringClass = methodParameter.getDeclaringClass();
			this.parameterIndex = methodParameter.getParameterIndex();
			this.methodParameter = methodParameter;
		}

		@Override
		public Type getType() {
			return this.methodParameter.getGenericParameterType();
		}

		@Override
		public Object getSource() {
			return this.methodParameter;
		}

		private void readObject(ObjectInputStream inputStream) throws IOException, ClassNotFoundException {
			inputStream.defaultReadObject();
			try {
				// 根据methodName是否为null来判断是方法还是构造器，然后创建MethodParameter实例。
				if (this.methodName != null) {
					this.methodParameter = new MethodParameter(
							this.declaringClass.getDeclaredMethod(this.methodName, this.parameterTypes), this.parameterIndex);
				}
				else {
					this.methodParameter = new MethodParameter(
							this.declaringClass.getDeclaredConstructor(this.parameterTypes), this.parameterIndex);
				}
			}
			catch (Throwable ex) {
				throw new IllegalStateException("Could not find original class structure", ex);
			}
		}
	}


	/**
	 * {@link TypeProvider} for {@link Type Types} obtained by invoking a no-arg method.
	 * 注：重要内部类！用于根据Type类型的不同具体方法的返回类型进行了包装（支持无参方法）。
	 * 有哪些Type呢？如Class以及这个SUPPORTED_SERIALIZABLE_TYPES集合内的类型。
	 * 具体方法呢？不同具体类型有不同方法。
	 * 为什么要有这个类？为了更好的操作类型，甚至泛型。（将一次方法调用，转换为对象。① 缓存结果；② 提高类型操作灵活性）
	 * (上面两个类是分别对属性、方法参数的基本代理，这个是对上面两个TypeProvider的进一步代理)
	 */
	@SuppressWarnings("serial")
	static class MethodInvokeTypeProvider implements TypeProvider {

		// 注：目标操作类型
		private final TypeProvider provider;

		// 注：对类型执行的方法名
		private final String methodName;

		// 注：方法所声明的类
		private final Class<?> declaringClass;

		/**
		 * 注：如果方法返回为数组类型，需根据该索引值返回对应类型
		 */
		private final int index;

		// 注：对类型执行的方法对象【transient，不可序列化】
		private transient Method method;

		// 注：目标操作类型执行指定方法返回的(类型)结果【transient，不可序列化】
		@Nullable
		private transient volatile Object result;

		public MethodInvokeTypeProvider(TypeProvider provider, Method method, int index) {
			this.provider = provider;
			this.methodName = method.getName();
			this.declaringClass = method.getDeclaringClass();
			this.index = index;
			this.method = method;
		}

		@Override
		@Nullable
		public Type getType() {
			// 注：这个结果是类型Type的子类；注意Type的子类和Object的子类的区别和联系哈。
			Object result = this.result;
			if (result == null) {
				// Lazy invocation of the target method on the provided type
				// 注：在提供的类型（可能是泛型类型）上执行目标方法【懒加载执行】
				result = ReflectionUtils.invokeMethod(this.method, this.provider.getType());
				// Cache the result for further calls to getType()
				// 注：为后面再次调用getType()时缓存结果
				this.result = result;
			}
			// 注：返回的是Type[]类型时，会根据索引选择第几个。
			return (result instanceof Type[] ? ((Type[]) result)[this.index] : (Type) result);
		}

		@Override
		@Nullable
		public Object getSource() {
			return null;
		}

		private void readObject(ObjectInputStream inputStream) throws IOException, ClassNotFoundException {
			inputStream.defaultReadObject();
			// 注：反序列化时需要根据声明类、方法名通过反射的方法获取方法对象【需要检查合法性】。
			Method method = ReflectionUtils.findMethod(this.declaringClass, this.methodName);
			if (method == null) {	// 注：检查1-方法需存在
				throw new IllegalStateException("Cannot find method on deserialization: " + this.methodName);
			}
			if (method.getReturnType() != Type.class && method.getReturnType() != Type[].class) {
				// 注：检查2-方法的返回类型需为Type或者Type数组
				throw new IllegalStateException(
						"Invalid return type on deserialized method - needs to be Type or Type[]: " + method);
			}
			this.method = method;
		}
	}

}
