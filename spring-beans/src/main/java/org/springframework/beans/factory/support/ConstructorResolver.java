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

package org.springframework.beans.factory.support;

import java.beans.ConstructorProperties;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;

import org.springframework.beans.BeanMetadataElement;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.InjectionPoint;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.ConstructorArgumentValues.ValueHolder;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.core.CollectionFactory;
import org.springframework.core.MethodParameter;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.MethodInvoker;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * Delegate for resolving constructors and factory methods.
 * <p>Performs constructor resolution through argument matching.
 * 注：ConstructorResolver用于解析构造器和工厂方法
 * - 通过参数匹配来执行解析构造器
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Mark Fisher
 * @author Costin Leau
 * @author Sebastien Deleuze
 * @author Sam Brannen
 * @since 2.0
 * @see #autowireConstructor
 * @see #instantiateUsingFactoryMethod
 * @see AbstractAutowireCapableBeanFactory
 * 注：参数-https://blog.csdn.net/qq_30321211/article/details/108350353
 */
class ConstructorResolver {

	private static final Object[] EMPTY_ARGS = new Object[0];

	/**
	 * Marker for autowired arguments in a cached argument array, to be later replaced
	 * by a {@linkplain #resolveAutowiredArgument resolved autowired argument}.
	 * 注：在缓存的参数数组中用于自动装配参数的标记。后续会通过resolveAutowiredArgument方法解析并取代
	 * // TODO: 2023/10/29 https://blog.csdn.net/m0_62396648/article/details/125154333
	 */
	private static final Object autowiredArgumentMarker = new Object();

	private static final NamedThreadLocal<InjectionPoint> currentInjectionPoint =
			new NamedThreadLocal<>("Current injection point");


	private final AbstractAutowireCapableBeanFactory beanFactory;

	private final Log logger;


	/**
	 * Create a new ConstructorResolver for the given factory and instantiation strategy.
	 * 为指定的bean工厂和实例化策略来创建一个新的ConstructorResolver实例
	 * @param beanFactory the BeanFactory to work with
	 */
	public ConstructorResolver(AbstractAutowireCapableBeanFactory beanFactory) {
		this.beanFactory = beanFactory;
		this.logger = beanFactory.getLogger();
	}


	/**
	 * "autowire constructor" (with constructor arguments by type) behavior.
	 * Also applied if explicit constructor argument values are specified,
	 * matching all remaining arguments with beans from the bean factory.
	 * 注：根据构造器参数类型来对构造器进行自动装配；
	 * 如果指定了明确的构造器参数(explicitArgs)会被匹配使用，所有剩余的参数类型将会通过bean工厂bean类型进行匹配。
	 * <p>This corresponds to constructor injection: In this mode, a Spring
	 * bean factory is able to host components that expect constructor-based
	 * dependency resolution.
	 * 注：对于构造器注入：在这种模式下，spring的bean工厂具备根据期望构造器依赖解析来管理组件。
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @param chosenCtors chosen candidate constructors (or {@code null} if none)
	 * @param explicitArgs argument values passed in programmatically via the getBean method,
	 * or {@code null} if none (-> use constructor argument values from bean definition)
	 * @return a BeanWrapper for the new instance
	 * 注：参考--> https://blog.csdn.net/qq_15719169/article/details/120517461
	 */
	public BeanWrapper autowireConstructor(String beanName, RootBeanDefinition mbd,
			@Nullable Constructor<?>[] chosenCtors, @Nullable Object[] explicitArgs) {
		// 注：新建一个bean的装饰实现对象，也是要返回的对象；
		BeanWrapperImpl bw = new BeanWrapperImpl();
		// 注：初始化装饰对象
		this.beanFactory.initBeanWrapper(bw);

		// 注：筛选出来投入使用的构造器
		Constructor<?> constructorToUse = null;
		// 注：存储解析后的参数值包装类
		ArgumentsHolder argsHolderToUse = null;
		// 注：调用构造器的参数值数组
		Object[] argsToUse = null;

		if (explicitArgs != null) {
			// 注：如果getBean指定了实例化参数，那么这里就直接确定了构造器的参数值数组
			argsToUse = explicitArgs;
		}
		else {
			/**
			 * 注：如果没有传入，就需要通过bean定义缓存来解析构造器参数了。
			 * (一般情况下这里的所有相关缓存都是null，缓存在bean定义中实际上是为了优化原型实例，避免遍历搜索匹配工厂方法的过程)
			 * - resolvedConstructorOrFactoryMethod：缓存了已解析了的构造器方法
			 * - resolvedConstructorArguments：用于缓存完全解析的构造参数的包可见字段
			 * - preparedConstructorArguments：用于缓存部分准备好的构造参数的包可见字段
			 */
			Object[] argsToResolve = null;
			synchronized (mbd.constructorArgumentLock) {
				constructorToUse = (Constructor<?>) mbd.resolvedConstructorOrFactoryMethod;
				if (constructorToUse != null && mbd.constructorArgumentsResolved) {
					// Found a cached constructor...
					// 注：如果构造器方法、构造器参数已经解析了，那么就可能存在构造器参数缓存
					argsToUse = mbd.resolvedConstructorArguments;
					if (argsToUse == null) {
						// 注：constructorArgumentsResolved处于已解析状态，可能是需要进一步解析的preparedConstructorArguments
						argsToResolve = mbd.preparedConstructorArguments;
					}
				}
			}
			if (argsToResolve != null) {
				// 注：如果存在部分就绪的构造器参数，这里需要进一步解析，比如配置占位符解析、bean定义字符串解析以及类型转换
				argsToUse = resolvePreparedArguments(beanName, mbd, bw, constructorToUse, argsToResolve, true);
			}
		}

		if (constructorToUse == null || argsToUse == null) {
			// Take specified constructors, if any.
			/**
			 * 注：在没法根据bean定义的缓存数据来获取要使用的构造器或者参数数组时，
			 * 需要尝试从指定的候选构造器方法（如果未指定通过bean类型反射获取）中获取要使用的工厂方法或参数数值。
			 */
			Constructor<?>[] candidates = chosenCtors;
			if (candidates == null) {
				Class<?> beanClass = mbd.getBeanClass();
				try {
					// 注：根据bean定义是否允许访问非公共方法，来获取声明构造器或公共构造器。
					candidates = (mbd.isNonPublicAccessAllowed() ?
							beanClass.getDeclaredConstructors() : beanClass.getConstructors());
				}
				catch (Throwable ex) {
					throw new BeanCreationException(mbd.getResourceDescription(), beanName,
							"Resolution of declared constructors on bean Class [" + beanClass.getName() +
							"] from ClassLoader [" + beanClass.getClassLoader() + "] failed", ex);
				}
			}

			/**
			 * 注：如果仅1个无参构造器方法，并且构造器调用及bean定义中均没有构造器参数；
			 * 这种情况会将工厂方法相关缓存起来，以便下次使用。
			 * - resolvedConstructorOrFactoryMethod：已解析的构造方法(无参构造器)
			 * - constructorArgumentsResolved：标识构造器参数已解析完毕
			 * - resolvedConstructorArguments：已解析的构造器参数-空数组
			 */
			if (candidates.length == 1 && explicitArgs == null && !mbd.hasConstructorArgumentValues()) {
				Constructor<?> uniqueCandidate = candidates[0];
				if (uniqueCandidate.getParameterCount() == 0) {
					synchronized (mbd.constructorArgumentLock) {
						mbd.resolvedConstructorOrFactoryMethod = uniqueCandidate;
						mbd.constructorArgumentsResolved = true;
						mbd.resolvedConstructorArguments = EMPTY_ARGS;
					}
					// 注：因为仅有构造方法，这里就直接可以通过实例化策略，通过指定的构造器及构造参数进行实例化
					// 并设置到bean包装器中即可返回。
					bw.setBeanInstance(instantiate(beanName, mbd, uniqueCandidate, EMPTY_ARGS));
					return bw;
				}
			}

			// Need to resolve the constructor.
			// 注：是否需要自动装配：getBean指定构造器或者bean定义的模式即为构造器自动装配模式
			boolean autowiring = (chosenCtors != null ||
					mbd.getResolvedAutowireMode() == AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);
			// 注：定义一个用于存放解析后的构造函数参数值的ConstructorArgumentValues对象
			ConstructorArgumentValues resolvedValues = null;

			/**
			 * 注：下面这一部分用于推断构造器最少的参数；
			 */
			int minNrOfArgs;
			if (explicitArgs != null) {
				// 注：如果构造器入参指定构造器参数，那最少参数就根据参数确定
				minNrOfArgs = explicitArgs.length;
			}
			else {
				/**
				 * 注：如果方法入参没有传入构造器参数，那我们就需要根据bean定义中存在的构造器缓存信息来解析构造器参数长度；
				 */
				ConstructorArgumentValues cargs = mbd.getConstructorArgumentValues();
				resolvedValues = new ConstructorArgumentValues();
				// 注：如果bean定义中存在构造器参数值，那就将cargs解析后赋值到resolvedValues对象中，并返回构造器参数数量
				minNrOfArgs = resolveConstructorArguments(beanName, mbd, bw, cargs, resolvedValues);
			}

			// 注：如果存在多个候选构造器方法时，按照先公开权限后私有权限、先参数多后参数少的顺序进行排序
			AutowireUtils.sortConstructors(candidates);
			// 注：构造参数类型权重的最大值【不采用】
			int minTypeDiffWeight = Integer.MAX_VALUE;
			// 注：用于存储多个符合要求的方法
			Set<Constructor<?>> ambiguousConstructors = null;
			// 注：定义一个异常集合对象
			LinkedList<UnsatisfiedDependencyException> causes = null;

			// 注：遍历所有的候选构造器方法
			for (Constructor<?> candidate : candidates) {
				// 注：获取当前候选构造方法的参数个数
				int parameterCount = candidate.getParameterCount();

				if (constructorToUse != null && argsToUse != null && argsToUse.length > parameterCount) {
					// Already found greedy constructor that can be satisfied ->
					// do not look any further, there are only less greedy constructors left.
					// 注：已经获取到满足要求的最多参数构造器了，不再继续循环寻找了。
					break;
				}
				/**
				 * 如果当前构造器的参数数量比最小参数列表数量小的时候,那么跳过这个构造器.
				 * minNrOfArgs的值有两个地方赋值了:
				 * 	1、如果我们getBean时传入了其他参数,那么其他参数的个数就是minNrOfArgs的值
				 * 	2、如果我们getBean没有传参数,那么minNrOfArgs的值就是我们配置让Spring指定使用某些参数的构造器,那么我们配置的参数列表数量也就是当前的minNrOfArgs
				 * 	3、如果上述的情况都不存在,那么minNrOfArgs就是0了,大多数时候都是这种情况,如果都没配置,那么就得Spring自己慢慢而不存在此处的筛选了.
				 * 所以总结来说此处就是做了一个根据我们自己定义的来筛选的操作
				 * */
				if (parameterCount < minNrOfArgs) {
					continue;
				}

				// 注：定义一个用于封装参数数组的ArgumentsHolder对象
				ArgumentsHolder argsHolder;
				// 注：构造器方法的参数类型列表(非泛型)
				Class<?>[] paramTypes = candidate.getParameterTypes();
				if (resolvedValues != null) {
					// 注：未在getBean方法入参传入构造器参数，则这里就使用解析后的构造器参数(resolvedValues)：需要类型转换或者自动装配
					try {
						// 注：这里校验下构造器的ConstructorProperties注解，注解的参数名数需和参数个数相同；不存在注解则返回null
						String[] paramNames = ConstructorPropertiesChecker.evaluate(candidate, parameterCount);
						if (paramNames == null) {
							// 注：获取bean工厂的参数名称发现器
							ParameterNameDiscoverer pnd = this.beanFactory.getParameterNameDiscoverer();
							if (pnd != null) {
								// 注：通过参数名发现器来解析出候选方法的参数名称
								paramNames = pnd.getParameterNames(candidate);
							}
						}
						/**
						 * 注：需根据当前方法参数类型和名称进行匹配已解析的构造参数值(resolvedValues)，并封装为一个参数数组包装对象(argsHolder)
						 * - 当candidate只有一个时，自动装配失败时，对于集合和map类型会给出默认空对象而不抛出异常
						 */
						argsHolder = createArgumentArray(beanName, mbd, resolvedValues, bw, paramTypes, paramNames,
								getUserDeclaredConstructor(candidate), autowiring, candidates.length == 1);
					}
					catch (UnsatisfiedDependencyException ex) {
						if (logger.isTraceEnabled()) {
							logger.trace("Ignoring constructor [" + candidate + "] of bean '" + beanName + "': " + ex);
						}
						// Swallow and try next constructor.
						// 注：当前方法无法正常解析，吞下当前异常，并尝试下一个重载的构造器方法。
						if (causes == null) {
							causes = new LinkedList<>();
						}
						causes.add(ex);
						continue;   		// 注：继续下一个重载方法
					}
				}
				else {
					// Explicit arguments given -> arguments length must match exactly.
					// 注：如果通过getBean方法参数传递了构造器参数，那匹配的工厂方法的参数数量和构造器参数数量必须相等。
					if (parameterCount != explicitArgs.length) {
						continue;
					}
					// 注：将传入的构造器参数数值数组使用ArgumentsHolder实例封装
					argsHolder = new ArgumentsHolder(explicitArgs);
				}

				/**
				 * 注：这里找到了有效的方法和方法参数值，还需要考虑bean定义中的自动匹配是严格匹配还是宽松匹配
				 * 1. 严格模式如果摸棱两可的构造函数在转换参数时都匹配，则抛出异常
				 * 2. 宽松模式将使用"最接近类型匹配"的构造函数
				 *  如果bd支持的构造函数解析模式时宽松模式, 引用获取类型差异权重值，否则引用获取类型转换权重值
				 */
				int typeDiffWeight = (mbd.isLenientConstructorResolution() ?
						argsHolder.getTypeDifferenceWeight(paramTypes) : argsHolder.getAssignabilityWeight(paramTypes));
				// Choose this constructor if it represents the closest match.
				// 注：如果它表示最接近的匹配项，则选择此构造方法
				if (typeDiffWeight < minTypeDiffWeight) {
					constructorToUse = candidate;			// 注：更新工厂方法
					argsHolderToUse = argsHolder;			// 注：更新工厂方法的构造参数值包装对象
					argsToUse = argsHolder.arguments;		// 注：更新工厂方法的构造参数值
					minTypeDiffWeight = typeDiffWeight; 	// 注：当前工厂方法的类型最近权重值
					ambiguousConstructors = null;       	// 注：当前权重最小，非模糊工厂方法标识
				}
				else if (constructorToUse != null && typeDiffWeight == minTypeDiffWeight) {
					// 注：找到模糊的构造器方法：如果具有相同的类型差异权重的多个构造器方法，则收集此类候选构造器并最终引发歧义异常。
					if (ambiguousConstructors == null) {
						ambiguousConstructors = new LinkedHashSet<>();
						ambiguousConstructors.add(constructorToUse);
					}
					ambiguousConstructors.add(candidate);
				}
			}

			/**
			 * 注：最终因为无法筛选出构造器方法，需抛出BeanCreationException并加以描述
			 * 抛异常会根据已有的信息排除不同的异常
			 */
			if (constructorToUse == null) {
				if (causes != null) {	// 注：1. 存在cause异常集合
					// 注：从cause中移除最新的UnsatisfiedDependencyException
					UnsatisfiedDependencyException ex = causes.removeLast();
					for (Exception cause : causes) {
						// 注：将cause添加到该Bean工厂的抑制异常列表【{@link DefaultSingletonBeanRegistry#suppressedExceptions】 中
						this.beanFactory.onSuppressedException(cause);
					}
					throw ex;
				}
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"Could not resolve matching constructor " +
						"(hint: specify index/type/name arguments for simple parameters to avoid type ambiguities)");
			}
			else if (ambiguousConstructors != null && !mbd.isLenientConstructorResolution()) {
				// 注：4. 找到的摸棱两可的构造方法匹配项(提示:为简单参数指定索引/类型/名称参数以避免类型歧义）
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"Ambiguous constructor matches found in bean '" + beanName + "' " +
						"(hint: specify index/type/name arguments for simple parameters to avoid type ambiguities): " +
						ambiguousConstructors);
			}

			// 注：将解析出来的构造器及其参数值缓存到bean定义中中
			if (explicitArgs == null && argsHolderToUse != null) {
				// 注：将argsHolderToUse所得到的参数值属性缓存到mbd对应的属性中
				// TODO: 2023/10/31 疑问这里好像会缓存有参构造器呀？
				argsHolderToUse.storeCache(mbd, constructorToUse);
			}
		}

		Assert.state(argsToUse != null, "Unresolved constructor arguments");
		// 注：使用指定构造器的实例化策略来获取bean对象, 并将该Bean对象保存到bw中
		bw.setBeanInstance(instantiate(beanName, mbd, constructorToUse, argsToUse));
		return bw;
	}

	// 注：基于指定构造器及构造参数来创建bean实例
	private Object instantiate(
			String beanName, RootBeanDefinition mbd, Constructor<?> constructorToUse, Object[] argsToUse) {

		try {
			InstantiationStrategy strategy = this.beanFactory.getInstantiationStrategy();
			if (System.getSecurityManager() != null) {
				return AccessController.doPrivileged((PrivilegedAction<Object>) () ->
						strategy.instantiate(mbd, beanName, this.beanFactory, constructorToUse, argsToUse),
						this.beanFactory.getAccessControlContext());
			}
			else {
				// 注：基于指定构造器及构造参数来创建bean实例
				return strategy.instantiate(mbd, beanName, this.beanFactory, constructorToUse, argsToUse);
			}
		}
		catch (Throwable ex) {
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					"Bean instantiation via constructor failed", ex);
		}
	}

	/**
	 * Resolve the factory method in the specified bean definition, if possible.
	 * {@link RootBeanDefinition#getResolvedFactoryMethod()} can be checked for the result.
	 * @param mbd the bean definition to check
	 */
	public void resolveFactoryMethodIfPossible(RootBeanDefinition mbd) {
		Class<?> factoryClass;
		boolean isStatic;
		if (mbd.getFactoryBeanName() != null) {
			factoryClass = this.beanFactory.getType(mbd.getFactoryBeanName());
			isStatic = false;
		}
		else {
			factoryClass = mbd.getBeanClass();
			isStatic = true;
		}
		Assert.state(factoryClass != null, "Unresolvable factory class");
		factoryClass = ClassUtils.getUserClass(factoryClass);

		Method[] candidates = getCandidateMethods(factoryClass, mbd);
		Method uniqueCandidate = null;
		for (Method candidate : candidates) {
			if (Modifier.isStatic(candidate.getModifiers()) == isStatic && mbd.isFactoryMethod(candidate)) {
				if (uniqueCandidate == null) {
					uniqueCandidate = candidate;
				}
				else if (isParamMismatch(uniqueCandidate, candidate)) {
					uniqueCandidate = null;
					break;
				}
			}
		}
		mbd.factoryMethodToIntrospect = uniqueCandidate;
	}

	private boolean isParamMismatch(Method uniqueCandidate, Method candidate) {
		int uniqueCandidateParameterCount = uniqueCandidate.getParameterCount();
		int candidateParameterCount = candidate.getParameterCount();
		return (uniqueCandidateParameterCount != candidateParameterCount ||
				!Arrays.equals(uniqueCandidate.getParameterTypes(), candidate.getParameterTypes()));
	}

	/**
	 * Retrieve all candidate methods for the given class, considering
	 * the {@link RootBeanDefinition#isNonPublicAccessAllowed()} flag.
	 * Called as the starting point for factory method determination.
	 * 注：根据指定的工厂类召回所有候选的工厂方法，其中会考虑bean定义是否允许访问非公共方法。
	 * 该方法会在工厂方法识别的开始阶段被调用
	 */
	private Method[] getCandidateMethods(Class<?> factoryClass, RootBeanDefinition mbd) {
		if (System.getSecurityManager() != null) {
			return AccessController.doPrivileged((PrivilegedAction<Method[]>) () ->
					(mbd.isNonPublicAccessAllowed() ?
						ReflectionUtils.getAllDeclaredMethods(factoryClass) : factoryClass.getMethods()));
		}
		else {
			// 注：如果bean定义不允许访问非公共方法，就调用Class的getMethods即可；否则需要反射获取所有声明的方法
			return (mbd.isNonPublicAccessAllowed() ?
					ReflectionUtils.getAllDeclaredMethods(factoryClass) : factoryClass.getMethods());
		}
	}

	/**
	 * Instantiate the bean using a named factory method. The method may be static, if the
	 * bean definition parameter specifies a class, rather than a "factory-bean", or
	 * an instance variable on a factory object itself configured using Dependency Injection.
	 * 注：使用指定的工厂方法来创建一个bean实例。
	 * 如果bean定义参数未指定工厂bean对象，或者使用依赖注入的工厂对象自身的属性变量，那么这个方法可能是一个静态方法。
	 * <p>Implementation requires iterating over the static or instance methods with the
	 * name specified in the RootBeanDefinition (the method may be overloaded) and trying
	 * to match with the parameters. We don't have the types attached to constructor args,
	 * so trial and error is the only way to go here. The explicitArgs array may contain
	 * argument values passed in programmatically via the corresponding getBean method.
	 * 注：该方法的是实现需要遍历定义在bean定义中的静态方法或者实例方法(方法也可能会被重载），并且试图去根据方法参数进行匹配。
	 * 因为我们在构造器参数(explicitArgs)上没有携带类型信息，因此我们只能反复试错来匹配方法。
	 * 明确的参数值可能会通过响应的getBean方法传入过来。
	 * @param beanName the name of the bean
	 * @param mbd the merged bean definition for the bean
	 * @param explicitArgs argument values passed in programmatically via the getBean
	 * method, or {@code null} if none (-> use constructor argument values from bean definition)
	 * @return a BeanWrapper for the new instance
	 * 注：参考--> https://blog.csdn.net/qq_30321211/article/details/108350353、https://blog.51cto.com/u_15103026/2645274
	 * https://www.jianshu.com/p/39edfa250e4e
	 * 注：方法复杂，后续可总结一下
	 */
	public BeanWrapper instantiateUsingFactoryMethod(
			String beanName, RootBeanDefinition mbd, @Nullable Object[] explicitArgs) {
		// 注：新建一个bean的装饰实现对象，也是要返回的对象；
		BeanWrapperImpl bw = new BeanWrapperImpl();
		// 注：初始化装饰对象
		this.beanFactory.initBeanWrapper(bw);

		// 注：如果存在工厂bean对象，就赋值到当前属性中
		Object factoryBean;
		/**
		 * 注：如果存在工厂bean，就缓存其工厂类型
		 * - 对于工厂bean对象实例方法场景，该对象为工厂bean的类型
		 * - 对于静态工厂方法场景，该对象为bean实例的类型。
		 */
		Class<?> factoryClass;
		// 注：标识工厂方法是否为静态
		boolean isStatic;

		// 注：通过bean定义获取当前FactoryBean名称
		String factoryBeanName = mbd.getFactoryBeanName();
		if (factoryBeanName != null) {
			// 注：存在factoryBean，则通过指定factoryBean的实例方法来获取当前bean实例。
			if (factoryBeanName.equals(beanName)) {
				// 注：factoryBean名称与bean的名称不能一致，也即不能引向同一个bean定义【bean的factoryBean不能是其本身，这个说法更好】
				throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName,
						"factory-bean reference points back to the same bean definition");
			}
			// 注：如果当前bean的实例需要通过其工厂bean的实例方法获取，那么这里需要先获取工厂bean的初始化！
			factoryBean = this.beanFactory.getBean(factoryBeanName);
			if (mbd.isSingleton() && this.beanFactory.containsSingleton(beanName)) {
				// 注：如果在单例bean创建时，有可能因为触发其他bean的实例化，可能会导致当前bean会被初始化，这类校验并抛出异常
				throw new ImplicitlyAppearedSingletonException();
			}
			// 注：获取工厂bean的类型
			factoryClass = factoryBean.getClass();
			// 注：工厂方法为工厂bean的实例方法，这里标注为false
			isStatic = false;
		}
		else {
			// It's a static factory method on the bean class.
			// 注：非工厂bean创建实例，那就是bean类型的静态方法创建。
			if (!mbd.hasBeanClass()) {
				// 注：创建实例bean要么指定bean类型要么指定工厂bean的引用
				throw new BeanDefinitionStoreException(mbd.getResourceDescription(), beanName,
						"bean definition declares neither a bean class nor a factory-bean reference");
			}
			// 注：不存在工厂bean对象
			factoryBean = null;
			// 注：工厂类型就是当前bean的类型
			factoryClass = mbd.getBeanClass();
			isStatic = true;		// 注：标识静态方法
		}

		// 注：用于创建实例的工厂方法
		Method factoryMethodToUse = null;
		ArgumentsHolder argsHolderToUse = null;
		// 注：用于创建实例、调用工厂方法的参数
		Object[] argsToUse = null;

		/**
		 * 注：下面解析用于创建bean，调用实例化方法所需要的参数数组-argsToUse
		 */
		if (explicitArgs != null) {
			// 注：如果在调用getBean方法时传入，那就是用方法调用实参
			argsToUse = explicitArgs;
		}
		else {
			/**
			 * 注：如果没有传入，就需要通过bean定义缓存来解析构造器参数了。
			 * (非原型bean情况下这里的所有相关缓存都是null，缓存在bean定义中实际上是为了优化原型实例，避免遍历搜索匹配构造方法的过程)
			 * - resolvedConstructorOrFactoryMethod：缓存了已解析了的构造器方法
			 * - resolvedConstructorArguments：用于缓存完全解析的构造参数的包可见字段
			 * - preparedConstructorArguments：用于缓存部分准备好的构造参数的包可见字段
			 */
			Object[] argsToResolve = null;
			synchronized (mbd.constructorArgumentLock) {
				factoryMethodToUse = (Method) mbd.resolvedConstructorOrFactoryMethod;
				if (factoryMethodToUse != null && mbd.constructorArgumentsResolved) {
					// Found a cached factory method...
					// 注：如果构造器方法、构造器参数已经解析了，那么就可能存在构造器参数缓存
					argsToUse = mbd.resolvedConstructorArguments;
					if (argsToUse == null) {
						// 注：constructorArgumentsResolved处于已解析状态，可能是需要进一步解析的preparedConstructorArguments
						argsToResolve = mbd.preparedConstructorArguments;
					}
				}
			}
			if (argsToResolve != null) {
				// 注：如果存在部分就绪的构造器参数，这里需要进一步解析，比如自动装配、配置占位符解析、bean定义字符串解析以及类型转换。
				argsToUse = resolvePreparedArguments(beanName, mbd, bw, factoryMethodToUse, argsToResolve, true);
			}
		}

		if (factoryMethodToUse == null || argsToUse == null) {
			// Need to determine the factory method...
			// Try all methods with this name to see if they match the given arguments.
			/**
			 * 注：在没法根据bean定义的缓存数据来获取要使用的工厂方法或者参数数组时，
			 * 需要尝试从候选工厂方法中获取要使用的工厂方法或参数数值。
			 */
			// 注：factoryClass要么是FactoryBean类型或者bean本身类型(静态)，但需注意这里的Class可能是动态生成类型
			factoryClass = ClassUtils.getUserClass(factoryClass);

			/**
			 * // TODO: 2023/10/30 https://blog.csdn.net/qq_30321211/article/details/108350353
			 * https://blog.csdn.net/m0_62396648/article/details/125154333
			 */
			// 注：候选工厂方法集合
			List<Method> candidates = null;
			if (mbd.isFactoryMethodUnique) {
				// 注：如果bean定义中工厂方法是唯一的
				if (factoryMethodToUse == null) {
					// 注：如果前面没有缓存工厂方法，这里就取bean定义中已解析的工厂方法
					factoryMethodToUse = mbd.getResolvedFactoryMethod();
				}
				if (factoryMethodToUse != null) {
					// 注：存在工厂方法，并且仅有一个，则candidates集合赋值为单个工厂方法集合
					candidates = Collections.singletonList(factoryMethodToUse);
				}
			}
			if (candidates == null) {
				// 注：如果暂无法从bean定义中获取候选方法，需要自身解析
				candidates = new ArrayList<>();
				// 注：获取工厂类型定义的所有候选方法；（会收到bean定义是否允许访问非公共方法标识的影响）
				Method[] rawCandidates = getCandidateMethods(factoryClass, mbd);
				for (Method candidate : rawCandidates) {
					if (Modifier.isStatic(candidate.getModifiers()) == isStatic && mbd.isFactoryMethod(candidate)) {
						// 注：工厂类型所定义的后选方案需要过滤掉一部分；
						// ① 是否为静态方法 ② 是否和bean定义的工厂方法名相同
						candidates.add(candidate);
					}
				}
			}

			/**
			 * 注：如果仅1个无参候选方法，并且方法调用及bean定义均没有构造器参数；
			 * 这种情况会将工厂方法相关缓存起来，以便下次使用。
			 * - factoryMethodToIntrospect：自举工厂方法
			 * - resolvedConstructorOrFactoryMethod：已解析的工厂方法
			 * - constructorArgumentsResolved：标识构造器参数已解析完毕
			 * - resolvedConstructorArguments：已解析的构造器参数-空数组
			 */
			if (candidates.size() == 1 && explicitArgs == null && !mbd.hasConstructorArgumentValues()) {
				Method uniqueCandidate = candidates.get(0);
				if (uniqueCandidate.getParameterCount() == 0) {
					mbd.factoryMethodToIntrospect = uniqueCandidate;
					synchronized (mbd.constructorArgumentLock) {
						mbd.resolvedConstructorOrFactoryMethod = uniqueCandidate;
						mbd.constructorArgumentsResolved = true;
						mbd.resolvedConstructorArguments = EMPTY_ARGS;
					}
					// 注：因为仅有一个方法，这里就直接可以通过实例化策略，通过指定的工厂方法及构造参数(无参)进行实例化实例化
					// 并设置到bean包装器中即可返回。
					bw.setBeanInstance(instantiate(beanName, mbd, factoryBean, uniqueCandidate, EMPTY_ARGS));
					return bw;
				}
			}

			if (candidates.size() > 1) {  // explicitly skip immutable singletonList
				// 注：如果存在多个候选工厂方法是，按照先公开权限后私有权限、先参数多后参数少的顺序进行排序
				candidates.sort(AutowireUtils.EXECUTABLE_COMPARATOR);
			}

			// 注：定义一个用于存放解析后的构造函数参数值的ConstructorArgumentValues对象
			ConstructorArgumentValues resolvedValues = null;
			// 注：获取bean定义中判断是否使用构造器自动注入
			boolean autowiring = (mbd.getResolvedAutowireMode() == AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);
			int minTypeDiffWeight = Integer.MAX_VALUE;
			// 注：用于存储多个符合要求的方法
			Set<Method> ambiguousFactoryMethods = null;

			/**
			 * 注：下面这一部分是判断工厂方法最小的参数；
			 */
			int minNrOfArgs;
			if (explicitArgs != null) {
				// 注：如果方法入参指定构造器参数，那最小参数就根据参数确定
				minNrOfArgs = explicitArgs.length;
			}
			else {
				// We don't have arguments passed in programmatically, so we need to resolve the
				// arguments specified in the constructor arguments held in the bean definition.
				/**
				 * 注：如果方法入参没有传入构造器参数，那我们就需要根据bean定义中可能存在的构造器缓存信息来解析构造器参数长度；
				 * 如果bean定义也没有构造器参数缓存，那就为0；
				 */
				if (mbd.hasConstructorArgumentValues()) {
					ConstructorArgumentValues cargs = mbd.getConstructorArgumentValues();
					resolvedValues = new ConstructorArgumentValues();
					// 注：如果bean定义中存在构造器参数值，那就将cargs解析后赋值到resolvedValues对象中，并返回构造器参数数量
					minNrOfArgs = resolveConstructorArguments(beanName, mbd, bw, cargs, resolvedValues);
				}
				else {
					// 注：默认情况下最小参数数量为0
					minNrOfArgs = 0;
				}
			}

			// 注：定义一个异常集合对象
			LinkedList<UnsatisfiedDependencyException> causes = null;

			// 注：遍历所有的候选工厂方法
			for (Method candidate : candidates) {
				// 注：获取当前候选工厂方法的参数个数
				int parameterCount = candidate.getParameterCount();

				// 注：候选工厂方法参数个数必须大于上述的最小构造参数
				if (parameterCount >= minNrOfArgs) {
					// 注：定义一个用于封装参数数组的ArgumentsHolder对象
					ArgumentsHolder argsHolder;

					Class<?>[] paramTypes = candidate.getParameterTypes();	// 注：候选工厂方法的参数类型列表(非泛型)
					if (explicitArgs != null) {
						// Explicit arguments given -> arguments length must match exactly.
						// 注：如果通过方法参数传递了构造器参数，那匹配的工厂方法的参数数量和构造器参数数量必须相等。
						if (paramTypes.length != explicitArgs.length) {
							continue;
						}
						// 注：将传入的构造器参数数值数组使用ArgumentsHolder实例封装
						argsHolder = new ArgumentsHolder(explicitArgs);
					}
					else {
						// Resolved constructor arguments: type conversion and/or autowiring necessary.
						// 注：未在方法入参传入构造器参数，则这里就使用解析后的构造器参数(resolvedValues)：需要类型转换或者自动装配
						try {
							String[] paramNames = null;
							// 注：获取bean工厂的参数名称发现器
							ParameterNameDiscoverer pnd = this.beanFactory.getParameterNameDiscoverer();
							if (pnd != null) {
								// 注：通过参数名发现器来解析出候选方法的参数名称
								paramNames = pnd.getParameterNames(candidate);
							}
							/**
							 * 注：需根据当前方法参数类型和名称进行匹配已解析的构造参数值(resolvedValues)，并封装为一个参数数组包装对象(argsHolder)
							 * - 当candidate只有一个时，自动装配失败时，对于集合和map类型会给出默认空对象而不抛出异常
							 */
							argsHolder = createArgumentArray(beanName, mbd, resolvedValues, bw,
									paramTypes, paramNames, candidate, autowiring, candidates.size() == 1);
						}
						catch (UnsatisfiedDependencyException ex) {
							if (logger.isTraceEnabled()) {
								logger.trace("Ignoring factory method [" + candidate + "] of bean '" + beanName + "': " + ex);
							}
							// Swallow and try next overloaded factory method.
							// 注：当前方法无法正常解析，吞下当前异常，并尝试下一个重载的工厂方法。
							if (causes == null) {
								causes = new LinkedList<>();
							}
							causes.add(ex);
							continue;		// 注：继续下一个重载方法
						}
					}

					//mbd支持的构造函数解析模式,默认使用宽松模式:
					// 1. 严格模式如果摸棱两可的构造函数在转换参数时都匹配，则抛出异常
					// 2. 宽松模式将使用"最接近类型匹配"的构造函数
					//如果bd支持的构造函数解析模式时宽松模式,引用获取类型差异权重值，否则引用获取Assignabliity权重值

					/**
					 * 注：这里找到了有效的方法和方法参数值，还需要考虑bean定义中的自动匹配是严格匹配还是宽松匹配
					 * 1. 严格模式如果摸棱两可的构造函数在转换参数时都匹配，则抛出异常
					 * 2. 宽松模式将使用"最接近类型匹配"的构造函数
					 *  如果bd支持的构造函数解析模式时宽松模式, 引用获取类型差异权重值，否则引用获取类型转换权重值
					 */
					int typeDiffWeight = (mbd.isLenientConstructorResolution() ?
							argsHolder.getTypeDifferenceWeight(paramTypes) : argsHolder.getAssignabilityWeight(paramTypes));
					// Choose this factory method if it represents the closest match.
					// 注：如果它表示最接近的匹配项，则选择此工厂方法
					if (typeDiffWeight < minTypeDiffWeight) {
						factoryMethodToUse = candidate;			// 注：更新工厂方法
						argsHolderToUse = argsHolder;			// 注：更新工厂方法的构造参数值包装对象
						argsToUse = argsHolder.arguments;		// 注：更新工厂方法的构造参数值
						minTypeDiffWeight = typeDiffWeight;  	// 注：当前工厂方法的类型最近权重值
						ambiguousFactoryMethods = null;			// 注：当前权重最小，非模糊工厂方法标识
					}
					// Find out about ambiguity: In case of the same type difference weight
					// for methods with the same number of parameters, collect such candidates
					// and eventually raise an ambiguity exception.
					// However, only perform that check in non-lenient constructor resolution mode,
					// and explicitly ignore overridden methods (with the same parameter signature).
					/**
					 * 注：找到模糊的工厂方法：如果具有相同数量参数的方法具有相同的类型差异权重，则收集此类候选工厂方法并最终引发歧义异常。
					 * 然而，仅当处于严格模式下，并且忽略相同的参数签名的重写方法。
					 * - 已经找到了工厂方法【忽略MAX_VALUE默认值】
					 * - 当前工厂方法和之前工厂方法相同。
					 * - 非宽松匹配模式
					 * - 之前工厂方法参数个数与当前方法参数类型个数相同
					 * - 之前工厂方法参数个数与当前方法参数类型不相同【非重写】
					 */
					else if (factoryMethodToUse != null && typeDiffWeight == minTypeDiffWeight &&
							!mbd.isLenientConstructorResolution() &&
							paramTypes.length == factoryMethodToUse.getParameterCount() &&
							!Arrays.equals(paramTypes, factoryMethodToUse.getParameterTypes())) {
						// 注：这种情况下就需要引发模糊匹配异常了
						if (ambiguousFactoryMethods == null) {
							ambiguousFactoryMethods = new LinkedHashSet<>();
							ambiguousFactoryMethods.add(factoryMethodToUse);
						}
						ambiguousFactoryMethods.add(candidate);
					}
				}
			}

			/**
			 * 注：最终因为无法筛选出候选方法 或者 无法解析出要使用的参数值的情况，需抛出BeanCreationException并加以描述
			 * 抛异常会根据已有的信息排除不同的异常
			 */
			if (factoryMethodToUse == null || argsToUse == null) {
				if (causes != null) {	// 注：1. 存在cause异常集合
					// 注：从cause中移除最新的UnsatisfiedDependencyException
					UnsatisfiedDependencyException ex = causes.removeLast();
					for (Exception cause : causes) {
						// 注：将cause添加到该Bean工厂的抑制异常列表【{@link DefaultSingletonBeanRegistry#suppressedExceptions】 中
						this.beanFactory.onSuppressedException(cause);
					}
					// 注：重新最新的异常ex
					throw ex;
				}
				// 注：定义一个用于存放参数类型的简单类名的ArrayList对象，长度为minNrOfArgs
				List<String> argTypes = new ArrayList<>(minNrOfArgs);
				if (explicitArgs != null) {
					for (Object arg : explicitArgs) {
						// 注：如果arg不为null，将arg的简单类名添加到argTypes中；否则将"null"添加到argTyps中
						argTypes.add(arg != null ? arg.getClass().getSimpleName() : "null");
					}
				}
				else if (resolvedValues != null) {
					// 注：定义一个用于存放resolvedValues的泛型参数值和方法参数值的LinkedHashSet对象
					Set<ValueHolder> valueHolders = new LinkedHashSet<>(resolvedValues.getArgumentCount());
					valueHolders.addAll(resolvedValues.getIndexedArgumentValues().values());
					valueHolders.addAll(resolvedValues.getGenericArgumentValues());
					for (ValueHolder value : valueHolders) {
						// 注：如果value的参数类型不为null，就获取该参数类型的简单类名；否则(如果value的参数值不为null，即获取该参数值的简单类名;否则为"null")
						String argType = (value.getType() != null ? ClassUtils.getShortName(value.getType()) :
								(value.getValue() != null ? value.getValue().getClass().getSimpleName() : "null"));
						argTypes.add(argType);
					}
				}
				// 注：2. 方法入参指定构造参数或已解析的构造参数resolvedValues，这样会抛出方法和构造参数信息。
				// 注：将argType转换成字符串，以","隔开元素.用于描述Bean创建异常
				String argDesc = StringUtils.collectionToCommaDelimitedString(argTypes);
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"No matching factory method found: " +
						(mbd.getFactoryBeanName() != null ?
							"factory bean '" + mbd.getFactoryBeanName() + "'; " : "") +
						"factory method '" + mbd.getFactoryMethodName() + "(" + argDesc + ")'. " +
						"Check that a method with the specified name " +
						(minNrOfArgs > 0 ? "and arguments " : "") +
						"exists and that it is " +
						(isStatic ? "static" : "non-static") + ".");
			}
			else if (void.class == factoryMethodToUse.getReturnType()) {
				// 注：3. 抛出BeanCreationException：无效工厂方法'mbd.getFactoryMethodName'需要具有非空返回类型
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"Invalid factory method '" + mbd.getFactoryMethodName() +
						"': needs to have a non-void return type!");
			}
			else if (ambiguousFactoryMethods != null) {
				// 注：4. 抛出BeanCreationException：在bean'beanName'中找到的摸棱两可的工厂方法匹配项(提示:为简单参数指定索引/类型/
				// 名称参数以避免类型歧义)：ambiguousFactoryMethods
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"Ambiguous factory method matches found in bean '" + beanName + "' " +
						"(hint: specify index/type/name arguments for simple parameters to avoid type ambiguities): " +
						ambiguousFactoryMethods);
			}

			// 注：将筛选出来的空参自举工厂方法和解析出来的参数值缓存到mdb中
			if (explicitArgs == null && argsHolderToUse != null) {
				// 注：如果explicitArgs为null 且 argsHolderToUser不为null，将自举方法缓存到bean定义中去
				mbd.factoryMethodToIntrospect = factoryMethodToUse;
				// 注：将argsHolderToUse所得到的参数值属性缓存到mbd对应的属性中
				argsHolderToUse.storeCache(mbd, factoryMethodToUse);
			}
		}

		// 注：使用factoryBean生成与beanName对应的Bean对象, 并将该Bean对象保存到bw中
		bw.setBeanInstance(instantiate(beanName, mbd, factoryBean, factoryMethodToUse, argsToUse));
		return bw;
	}

	// 注：使用factoryBean生成与beanName对应的Bean对象
	private Object instantiate(String beanName, RootBeanDefinition mbd,
			@Nullable Object factoryBean, Method factoryMethod, Object[] args) {

		try {
			if (System.getSecurityManager() != null) {
				// 注：使用特权方式运行：在beanFactory中返回beanName的Bean实例，并通过factoryMethod创建它
				return AccessController.doPrivileged((PrivilegedAction<Object>) () ->
						this.beanFactory.getInstantiationStrategy().instantiate(
								mbd, beanName, this.beanFactory, factoryBean, factoryMethod, args),
						this.beanFactory.getAccessControlContext());
			}
			else {
				/**
				 * 注：在beanFactory中返回beanName的Bean实例，并通过factoryMethod创建它
				 * 使用默认的实例化策略来实例化对象，默认为 CglibSubclassingInstantiationStrategy 实现，
				 *  但是instantiate()方法只在SimpleInstantiationStrategy里有实现逻辑
				 */
				return this.beanFactory.getInstantiationStrategy().instantiate(
						mbd, beanName, this.beanFactory, factoryBean, factoryMethod, args);
			}
		}
		catch (Throwable ex) {
			// 注：抛出BeanCreationException:通过工厂方法实例化Bean失败
			throw new BeanCreationException(mbd.getResourceDescription(), beanName,
					"Bean instantiation via factory method failed", ex);
		}
	}

	/**
	 * Resolve the constructor arguments for this bean into the resolvedValues object.
	 * This may involve looking up other beans.
	 * <p>This method is also used for handling invocations of static factory methods.
	 * 注：将bean定义中的构造器参数(cargs)解析到resolvedValues对象中，并返回解析后的最小(索引参数值数+泛型参数值数)
	 * 这个方法可能会涉及查找其他bean；
	 * - 此方法也会被用于处理静态工厂方法的调用。
	 */
	private int resolveConstructorArguments(String beanName, RootBeanDefinition mbd, BeanWrapper bw,
			ConstructorArgumentValues cargs, ConstructorArgumentValues resolvedValues) {

		// 注：获取bean工厂自定义的类型转换器
		TypeConverter customConverter = this.beanFactory.getCustomTypeConverter();
		// 注：如果不存在自定义类型转换器就是用当前bean的包装器实例
		TypeConverter converter = (customConverter != null ? customConverter : bw);
		// 注：获取占位符解析器
		BeanDefinitionValueResolver valueResolver =
				new BeanDefinitionValueResolver(this.beanFactory, beanName, mbd, converter);

		// 注：默认将最小参数数定义为bean定义缓存中的构造器参数数组数量
		int minNrOfArgs = cargs.getArgumentCount();

		// 注：ConstructorArgumentValues.ValueHolder：构造函数参数值的Holder,带有可选的type属性，指示实际构造函数参数的目标类型
		// 注：遍历cargs所封装的索引参数值的Map，元素为entry(key=参数值的参数索引,value=ConstructorArgumentValues.ValueHolder对象)
		for (Map.Entry<Integer, ConstructorArgumentValues.ValueHolder> entry : cargs.getIndexedArgumentValues().entrySet()) {
			int index = entry.getKey();	// 当前参数值的参数索引
			if (index < 0) {
				// 注：无效的索引值，抛出bean创建异常
				throw new BeanCreationException(mbd.getResourceDescription(), beanName,
						"Invalid constructor argument index: " + index);
			}
			if (index + 1 > minNrOfArgs) {
				// 注：根据索引值来更新最小参数数量，即minNrOfArgs = max(minNrOfArgs, index+1)
				minNrOfArgs = index + 1;
			}
			// 注：获取构造器参数的ValueHolder对象
			ConstructorArgumentValues.ValueHolder valueHolder = entry.getValue();
			if (valueHolder.isConverted()) {
				// 注：如果当前valueHolder是已经转换后的，就不需要再处理，直接增加到resolvedValues对象中去。
				resolvedValues.addIndexedArgumentValue(index, valueHolder);
			}
			else {
				// 注：使用值解析器来解析构造参数值所封装的对象
				Object resolvedValue =
						valueResolver.resolveValueIfNecessary("constructor argument", valueHolder.getValue());
				// 注：根据解析后的值，重新创建一个ValueHolder对象
				ConstructorArgumentValues.ValueHolder resolvedValueHolder =
						new ConstructorArgumentValues.ValueHolder(resolvedValue, valueHolder.getType(), valueHolder.getName());
				// 注：将valueHolder作为解析后值的配置源对象设置到resolvedValues对象中。
				resolvedValueHolder.setSource(valueHolder);
				// 注：将已解析的参数值及其索引缓存在resolvedValues对象中
				resolvedValues.addIndexedArgumentValue(index, resolvedValueHolder);
			}
		}

		// 注：遍历cargs泛型参数的列表
		for (ConstructorArgumentValues.ValueHolder valueHolder : cargs.getGenericArgumentValues()) {
			if (valueHolder.isConverted()) {
				// 注：如果泛型参数也已经解析过了，那就直接缓存在resolvedValues对象中；
				resolvedValues.addGenericArgumentValue(valueHolder);
			}
			else {
				// 注：于上相同，不再赘述
				Object resolvedValue =
						valueResolver.resolveValueIfNecessary("constructor argument", valueHolder.getValue());
				ConstructorArgumentValues.ValueHolder resolvedValueHolder = new ConstructorArgumentValues.ValueHolder(
						resolvedValue, valueHolder.getType(), valueHolder.getName());
				resolvedValueHolder.setSource(valueHolder);
				resolvedValues.addGenericArgumentValue(resolvedValueHolder);
			}
		}

		return minNrOfArgs;
	}

	/**
	 * Create an array of arguments to invoke a constructor or factory method,
	 * given the resolved constructor argument values.
	 * 注：根据给定的已解析的构造器参数值来创建参数数组对象(使用ArgumentsHolder类封装)，以便于后续调用构造器或工厂方法。
	 */
	private ArgumentsHolder createArgumentArray(
			String beanName, RootBeanDefinition mbd, @Nullable ConstructorArgumentValues resolvedValues,
			BeanWrapper bw, Class<?>[] paramTypes, @Nullable String[] paramNames, Executable executable,
			boolean autowiring, boolean fallback) throws UnsatisfiedDependencyException {

		// 注：获取bean工厂自定义的类型转换器
		TypeConverter customConverter = this.beanFactory.getCustomTypeConverter();
		// 注：如果不存在自定义类型转换器就是用当前bean的包装器实例
		TypeConverter converter = (customConverter != null ? customConverter : bw);

		// 注：先根据方法的参数类型数量来初始化ArgumentsHolder对象，便于保存后续解析后的参数值
		ArgumentsHolder args = new ArgumentsHolder(paramTypes.length);
		// 注：定义一个用于存储构造函数参数值Holder，以查找下一个任意泛型参数值时，忽略该集合的元素的HashSet,初始化长度为paramTypes的数组长度
		Set<ConstructorArgumentValues.ValueHolder> usedValueHolders = new HashSet<>(paramTypes.length);
		// 注：定义一个用于存储自动注入bean名的链表
		Set<String> autowiredBeanNames = new LinkedHashSet<>(4);

		// 注：遍历方法参数类型列表
		for (int paramIndex = 0; paramIndex < paramTypes.length; paramIndex++) {
			Class<?> paramType = paramTypes[paramIndex];		// 注：参数类型
			String paramName = (paramNames != null ? paramNames[paramIndex] : "");	// 参数名，如果没有参数名，默认为空字符串
			// Try to find matching constructor argument value, either indexed or generic.
			// 注：试图去匹配根据已解析后的构造参数值(resolvedValues)，要么是带有索引参数的，要么是泛型参数
			// 注：首先定义一个用于存储改参数类型对应的参数值valueHolder实例。
			ConstructorArgumentValues.ValueHolder valueHolder = null;
			if (resolvedValues != null) {
				// 注：在resolvedValues中按照参数类型、参数名等进行匹配
				valueHolder = resolvedValues.getArgumentValue(paramIndex, paramType, paramName, usedValueHolders);
				// If we couldn't find a direct match and are not supposed to autowire,
				// let's try the next generic, untyped argument value as fallback:
				// it could match after type conversion (for example, String -> int).
				/**
				 * 注：如果我们不能根据类型严格匹配到参数值，并且我们后续也不希望进行自动装配的话，
				 * 下面我们将进行泛型的参数匹配，比如可以试图匹配String和int。
				 */
				if (valueHolder == null && (!autowiring || paramTypes.length == resolvedValues.getArgumentCount())) {
					// 注：进行通用性的匹配参数值
					valueHolder = resolvedValues.getGenericArgumentValue(null, null, usedValueHolders);
				}
			}
			if (valueHolder != null) {
				// We found a potential match - let's give it a try.
				// Do not consider the same value definition multiple times!
				/**
				 * 注：我们找到了一个潜在的匹配参数值。
				 * 注意后续不要再匹配该参数值了--usedValueHolders集合去重
				 */
				usedValueHolders.add(valueHolder);
				// 注：从valueHolder获取原始参数值
				Object originalValue = valueHolder.getValue();
				Object convertedValue;
				if (valueHolder.isConverted()) {
					// 如果当前valueHolder已经转换了，就获取其转换后的值，并将其赋值到ArgumentsHolder对象中
					convertedValue = valueHolder.getConvertedValue();
					args.preparedArguments[paramIndex] = convertedValue;
				}
				else {
					// 注：如果当前valueHolder尚未被转换，先获取到方法参数对象
					MethodParameter methodParam = MethodParameter.forExecutable(executable, paramIndex);
					try {
						// 注：使用类型转换器将原有值对象转换为参数类型的对象
						convertedValue = converter.convertIfNecessary(originalValue, paramType, methodParam);
					}
					catch (TypeMismatchException ex) {
						// 注：无法转换时会抛出该异常
						throw new UnsatisfiedDependencyException(
								mbd.getResourceDescription(), beanName, new InjectionPoint(methodParam),
								"Could not convert argument value of type [" +
										ObjectUtils.nullSafeClassName(valueHolder.getValue()) +
										"] to required type [" + paramType.getName() + "]: " + ex.getMessage());
					}
					/**
					 * 注：如果已解析的参数值对象resolvedValues是通过bean定义中构造器参数值解析而来的，
					 * 这里解析后，根据配置源引用可以获取到预处理值（这时就需要设置需要解析标识）。
					 */
					Object sourceHolder = valueHolder.getSource();
					if (sourceHolder instanceof ConstructorArgumentValues.ValueHolder) {
						// 注：获取源构造参数值
						Object sourceValue = ((ConstructorArgumentValues.ValueHolder) sourceHolder).getValue();
						// 注：标注当前preparedArguments引用的构造参数值需要解析
						args.resolveNecessary = true;
						// 将preparedArguments属性引用源构造参数值
						args.preparedArguments[paramIndex] = sourceValue;
					}
				}
				// 注：已转换后的值赋值到arguments属性中
				args.arguments[paramIndex] = convertedValue;
				// 注：将valueHolder的原始值存储到rawArguments属性中
				args.rawArguments[paramIndex] = originalValue;
			}
			else {
				MethodParameter methodParam = MethodParameter.forExecutable(executable, paramIndex);
				// No explicit match found: we're either supposed to autowire or
				// have to fail creating an argument array for the given constructor.
				/**
				 * 注：我们没有给这个参数类型确定对应的一个的参数值。
				 * 如果我们不指望后续自动装配，那这里就只能抛出异常，即创建参数数组失败。
				 */
				if (!autowiring) {
					throw new UnsatisfiedDependencyException(
							mbd.getResourceDescription(), beanName, new InjectionPoint(methodParam),
							"Ambiguous argument values for parameter of type [" + paramType.getName() +
							"] - did you specify the correct bean references as arguments?");
				}
				try {
					// 注：解析应该自动装配的methodParam的Bean对象, 使用autowiredBeanNames保存所找到的所有候选Bean对象
					Object autowiredArgument = resolveAutowiredArgument(
							methodParam, beanName, autowiredBeanNames, converter, fallback);
					args.rawArguments[paramIndex] = autowiredArgument;
					args.arguments[paramIndex] = autowiredArgument;
					// 注：自动装配的预备参数存储为自动装配的标记
					args.preparedArguments[paramIndex] = autowiredArgumentMarker;
					// 注：表示当前的preparedArguments属性需要解析
					args.resolveNecessary = true;
				}
				catch (BeansException ex) {
					throw new UnsatisfiedDependencyException(
							mbd.getResourceDescription(), beanName, new InjectionPoint(methodParam), ex);
				}
			}
		}

		/**
		 * 注：自动装配意味着存在bean的依赖关系。
		 * 前面自动装配解析时会把所有自动装配的bean名称存储到autowiredBeanNames集合中，
		 * 这里遍历该集合并将当前bean依赖于转配bean的关系注册到bean工厂中。
		 */
		for (String autowiredBeanName : autowiredBeanNames) {
			// 注：向bean工厂中注册beanName依赖于autowiredBeanName的关系
			this.beanFactory.registerDependentBean(autowiredBeanName, beanName);
			if (logger.isDebugEnabled()) {
				logger.debug("Autowiring by type from bean name '" + beanName +
						"' via " + (executable instanceof Constructor ? "constructor" : "factory method") +
						" to bean named '" + autowiredBeanName + "'");
			}
		}

		// 注：返回匹配后的参数数组对象
		return args;
	}

	/**
	 * Resolve the prepared arguments stored in the given bean definition.
	 * 注：解析存储在指定bean定义中部分准备好的构造器参数--preparedConstructorArguments
	 */
	private Object[] resolvePreparedArguments(String beanName, RootBeanDefinition mbd, BeanWrapper bw,
			Executable executable, Object[] argsToResolve, boolean fallback) {

		// 注：获取bean工厂内自定义的类型转换器
		TypeConverter customConverter = this.beanFactory.getCustomTypeConverter();
		// 注：如果用户没有自定义类型转换器，就默认使用bean包装对象
		TypeConverter converter = (customConverter != null ? customConverter : bw);
		// 注：获取占位符解析器
		BeanDefinitionValueResolver valueResolver =
				new BeanDefinitionValueResolver(this.beanFactory, beanName, mbd, converter);
		// 注：获取方法|构造器的参数类型
		Class<?>[] paramTypes = executable.getParameterTypes();

		Object[] resolvedArgs = new Object[argsToResolve.length];
		for (int argIndex = 0; argIndex < argsToResolve.length; argIndex++) {
			Object argValue = argsToResolve[argIndex];
			// 注：获取指定方法|构造器第index个参数对象-MethodParameter
			MethodParameter methodParam = MethodParameter.forExecutable(executable, argIndex);
			if (argValue == autowiredArgumentMarker) {
				// 注：如果当前值为自动装配表示，则需进行装配解析
				argValue = resolveAutowiredArgument(methodParam, beanName, null, converter, fallback);
			}
			else if (argValue instanceof BeanMetadataElement) {
				// 注：如果当前值类型为BeanMetadataElement，则需要使用需要解析占位符等配置信息。
				argValue = valueResolver.resolveValueIfNecessary("constructor argument", argValue);
			}
			else if (argValue instanceof String) {
				// 注：解析可能需要作为表达式进行解析的bean定义中包含的字符串
				argValue = this.beanFactory.evaluateBeanDefinitionString((String) argValue, mbd);
			}
			Class<?> paramType = paramTypes[argIndex];		// 注：参数类型
			try {
				// 注：将构造参数argValue转换为参数类型(paramType)
				resolvedArgs[argIndex] = converter.convertIfNecessary(argValue, paramType, methodParam);
			}
			catch (TypeMismatchException ex) {
				// 注：类型转换异常
				throw new UnsatisfiedDependencyException(
						mbd.getResourceDescription(), beanName, new InjectionPoint(methodParam),
						"Could not convert argument value of type [" + ObjectUtils.nullSafeClassName(argValue) +
						"] to required type [" + paramType.getName() + "]: " + ex.getMessage());
			}
		}
		// 返回解析后的参数值对象
		return resolvedArgs;
	}

	protected Constructor<?> getUserDeclaredConstructor(Constructor<?> constructor) {
		Class<?> declaringClass = constructor.getDeclaringClass();
		Class<?> userClass = ClassUtils.getUserClass(declaringClass);
		if (userClass != declaringClass) {
			try {
				return userClass.getDeclaredConstructor(constructor.getParameterTypes());
			}
			catch (NoSuchMethodException ex) {
				// No equivalent constructor on user class (superclass)...
				// Let's proceed with the given constructor as we usually would.
			}
		}
		return constructor;
	}

	/**
	 * Template method for resolving the specified argument which is supposed to be autowired.
	 * 注：用于自动装配方法解析指定方法参数的模版方法
	 */
	@Nullable
	protected Object resolveAutowiredArgument(MethodParameter param, String beanName,
			@Nullable Set<String> autowiredBeanNames, TypeConverter typeConverter, boolean fallback) {

		// 注：获取当前方法参数的类型
		Class<?> paramType = param.getParameterType();
		if (InjectionPoint.class.isAssignableFrom(paramType)) {
			/**
			 * 注：如果当前方法类型为InjectionPoint，即AOP切入点
			 * 从线程本地中获取当前切入点对象，该对象一般在Bean工厂解析出与descriptor所包装的对象匹配的候选Bean对象的时候设置
			 */
			InjectionPoint injectionPoint = currentInjectionPoint.get();
			if (injectionPoint == null) {
				// 注：抛出非法状态异常: 当前没有InjectionPoint对象可用于param
				throw new IllegalStateException("No current InjectionPoint available for " + param);
			}
			// 注：返回当前injectionPoint对象
			return injectionPoint;
		}
		try {
			//DependencyDescriptor：即将注入的特定依赖项描述符。包装构造函数，方法参数或字段，以允许对其元数据 的统一访问
			//该DependencyDescriptor对象的依赖类型就是指param的类型
			//将param封装成DependencyDescriptor对象，让当前Bean工厂根据该DependencyDescriptor对象的依赖类型解析出与
			// 	该DependencyDescriptor对象所包装的对象匹配的候选Bean对象，然后返回出去
			/**
			 * 注：将param封装成DependencyDescriptor对象，让当前Bean工厂根据该DependencyDescriptor对象的依赖类型解析出与
			 * 该DependencyDescriptor对象所包装的对象匹配的候选Bean对象，然后返回出去
			 * - DependencyDescriptor：即将注入的特定依赖项描述符。包装构造函数，方法参数或字段，以允许对其元数据 的统一访问
			 * - 该DependencyDescriptor对象的依赖类型就是指param的类型
			 */
			return this.beanFactory.resolveDependency(
					new DependencyDescriptor(param, true), beanName, autowiredBeanNames, typeConverter);
		}
		catch (NoUniqueBeanDefinitionException ex) {
			throw ex;
		}
		catch (NoSuchBeanDefinitionException ex) {
			if (fallback) {
				// Single constructor or factory method -> let's return an empty array/collection
				// for e.g. a vararg or a non-null List/Set/Map parameter.
				// 如果是单个构造器或工厂方法，我们可以返回空的数组或集合
				if (paramType.isArray()) {
					return Array.newInstance(paramType.getComponentType(), 0);
				}
				else if (CollectionFactory.isApproximableCollectionType(paramType)) {
					return CollectionFactory.createCollection(paramType, 0);
				}
				else if (CollectionFactory.isApproximableMapType(paramType)) {
					return CollectionFactory.createMap(paramType, 0);
				}
			}
			throw ex;
		}
	}

	/**
	 * 注：返回当前线程上下文中的切入点，并将指定的切入点设置到线程上下文中
	 */
	static InjectionPoint setCurrentInjectionPoint(@Nullable InjectionPoint injectionPoint) {
		InjectionPoint old = currentInjectionPoint.get();
		if (injectionPoint != null) {
			currentInjectionPoint.set(injectionPoint);
		}
		else {
			currentInjectionPoint.remove();
		}
		return old;
	}


	/**
	 * Private inner class for holding argument combinations.
	 */
	private static class ArgumentsHolder {

		public final Object[] rawArguments;

		public final Object[] arguments;

		public final Object[] preparedArguments;

		public boolean resolveNecessary = false;

		public ArgumentsHolder(int size) {
			this.rawArguments = new Object[size];
			this.arguments = new Object[size];
			this.preparedArguments = new Object[size];
		}

		public ArgumentsHolder(Object[] args) {
			this.rawArguments = args;
			this.arguments = args;
			this.preparedArguments = args;
		}

		/**
		 * 注：获取类型差异权重，宽容模式下使用
		 * @param paramTypes 参数类型数组
		 * @return 类型差异权重最小值
		 */
		public int getTypeDifferenceWeight(Class<?>[] paramTypes) {
			// If valid arguments found, determine type difference weight.
			// Try type difference weight on both the converted arguments and
			// the raw arguments. If the raw weight is better, use it.
			// Decrease raw weight by 1024 to prefer it over equal converted weight.
			/**
			 * 注：如果找到有效的参数，需要确定类型差异权重。如果原始参数更小，那就返回原始参数的权重。
			 * 将原始权重减少1024，以使其优于相同的转换后参数的权重。
			 * 获取表示paramTypes和arguments之间的类层次结构差异的权重
			 * MethodInvoker.getTypeDifferenceWeight-确定表示类型和参数之间的类层次结构差异的权重：
			 * 1. arguments的类型不paramTypes类型的子类，直接返回 Integer.MAX_VALUE,最大重量，也就是直接不匹配
			 * 2. paramTypes类型是arguments类型的父类则+2
			 * 3. paramTypes类型是arguments类型的接口，则+1
			 * 4. arguments的类型直接就是paramTypes类型,则+0
			 */
			int typeDiffWeight = MethodInvoker.getTypeDifferenceWeight(paramTypes, this.arguments);
			int rawTypeDiffWeight = MethodInvoker.getTypeDifferenceWeight(paramTypes, this.rawArguments) - 1024;
			return Math.min(rawTypeDiffWeight, typeDiffWeight);
		}

		/**
		 * 注：获取类型转换权重，严格模式下使用
		 * @param paramTypes
		 * @return
		 */
		public int getAssignabilityWeight(Class<?>[] paramTypes) {
			for (int i = 0; i < paramTypes.length; i++) {
				if (!ClassUtils.isAssignableValue(paramTypes[i], this.arguments[i])) {
					// 注：如果确定arguments不是paramTypes的实例，返回Integer最大值，
					// 意味着既然连最终的转换后参数值都不能匹配，这个情况下的paramTypes所对应的工厂方法是不可以接受的
					return Integer.MAX_VALUE;
				}
			}
			for (int i = 0; i < paramTypes.length; i++) {
				if (!ClassUtils.isAssignableValue(paramTypes[i], this.rawArguments[i])) {
					// 注：如果确定rawArguments不是paramTypes的实例，返回Integer最大值-512，
					// 意味着虽然转换后的参数值不匹配，但是原始的参数值匹配，这个情况下的paramTypes所对应的工厂方法还是可以接受的
					return Integer.MAX_VALUE - 512;
				}
			}
			// 注：在完全匹配的情况下，返回Integer最大值-1024；
			// 意味着因为最终的转换后参数值和原始参数值都匹配，这种情况下paramTypes所对应的工厂方法非常可以接收
			return Integer.MAX_VALUE - 1024;
		}

		public void storeCache(RootBeanDefinition mbd, Executable constructorOrFactoryMethod) {
			synchronized (mbd.constructorArgumentLock) {
				// 注：缓存bean定义的已解析的构造器或工厂方法
				mbd.resolvedConstructorOrFactoryMethod = constructorOrFactoryMethod;
				// 注：设置bean定义缓存中构造参数已解析标识
				mbd.constructorArgumentsResolved = true;
				if (this.resolveNecessary) {
					// 如果需要进一步构造参数值解析，就缓存预准备构造参数值
					mbd.preparedConstructorArguments = this.preparedArguments;
				}
				else {
					// 注：如果不需要进一步构造参数值解析，就直接缓存最终构造参数即可
					mbd.resolvedConstructorArguments = this.arguments;
				}
			}
		}
	}


	/**
	 * Delegate for checking Java 6's {@link ConstructorProperties} annotation.
	 */
	private static class ConstructorPropertiesChecker {

		@Nullable
		public static String[] evaluate(Constructor<?> candidate, int paramCount) {
			ConstructorProperties cp = candidate.getAnnotation(ConstructorProperties.class);
			if (cp != null) {
				String[] names = cp.value();
				if (names.length != paramCount) {
					throw new IllegalStateException("Constructor annotated with @ConstructorProperties but not " +
							"corresponding to actual number of parameters (" + paramCount + "): " + candidate);
				}
				return names;
			}
			else {
				return null;
			}
		}
	}

}
