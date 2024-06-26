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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCreationNotAllowedException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.core.SimpleAliasRegistry;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Generic registry for shared bean instances, implementing the
 * {@link org.springframework.beans.factory.config.SingletonBeanRegistry}.
 * Allows for registering singleton instances that should be shared
 * for all callers of the registry, to be obtained via bean name.
 *
 * <p>Also supports registration of
 * {@link org.springframework.beans.factory.DisposableBean} instances,
 * (which might or might not correspond to registered singletons),
 * to be destroyed on shutdown of the registry. Dependencies between
 * beans can be registered to enforce an appropriate shutdown order.
 *
 * <p>This class mainly serves as base class for
 * {@link org.springframework.beans.factory.BeanFactory} implementations,
 * factoring out the common management of singleton bean instances. Note that
 * the {@link org.springframework.beans.factory.config.ConfigurableBeanFactory}
 * interface extends the {@link SingletonBeanRegistry} interface.
 *
 * <p>Note that this class assumes neither a bean definition concept
 * nor a specific creation process for bean instances, in contrast to
 * {@link AbstractBeanFactory} and {@link DefaultListableBeanFactory}
 * (which inherit from it). Can alternatively also be used as a nested
 * helper to delegate to.
 *
 * @author Juergen Hoeller
 * @since 2.0
 * @see #registerSingleton
 * @see #registerDisposableBean
 * @see org.springframework.beans.factory.DisposableBean
 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory
 */
public class DefaultSingletonBeanRegistry extends SimpleAliasRegistry implements SingletonBeanRegistry {

	/** Maximum number of suppressed exceptions to preserve. */
	private static final int SUPPRESSED_EXCEPTIONS_LIMIT = 100;


	/** Cache of singleton objects: bean name to bean instance.
	 * 注：第一级缓存，缓存bean名称到bean实例的映射
	 * */
	private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);

	/** Cache of singleton factories: bean name to ObjectFactory.
	 * 注：第三级缓存，缓存bean名称到bean实例创建工厂的映射
	 * */
	private final Map<String, ObjectFactory<?>> singletonFactories = new HashMap<>(16);

	/** Cache of early singleton objects: bean name to bean instance.
	 * 注：第二级缓存，缓存bean名称到早期bean实例的映射
	 * */
	private final Map<String, Object> earlySingletonObjects = new HashMap<>(16);

	/** Set of registered singletons, containing the bean names in registration order.
	 * 注：缓存已注册bean名称的集合，按照注册的顺序缓存
	 * */
	private final Set<String> registeredSingletons = new LinkedHashSet<>(256);

	/** Names of beans that are currently in creation. */
	private final Set<String> singletonsCurrentlyInCreation =
			Collections.newSetFromMap(new ConcurrentHashMap<>(16));

	/** Names of beans currently excluded from in creation checks. */
	private final Set<String> inCreationCheckExclusions =
			Collections.newSetFromMap(new ConcurrentHashMap<>(16));

	/** Collection of suppressed Exceptions, available for associating related causes.
	 * 注：异常压缩集合，用于关联相关异常原因
	 * */
	@Nullable
	private Set<Exception> suppressedExceptions;

	/** Flag that indicates whether we're currently within destroySingletons.
	 * 注：用于标识当前是否正在销毁单例bean阶段
	 * */
	private boolean singletonsCurrentlyInDestruction = false;

	/** Disposable bean instances: bean name to disposable instance. */
	private final Map<String, Object> disposableBeans = new LinkedHashMap<>();

	/** Map between containing bean names: bean name to Set of bean names that the bean contains. */
	private final Map<String, Set<String>> containedBeanMap = new ConcurrentHashMap<>(16);

	/** Map between dependent bean names: bean name to Set of dependent bean names.
	 * 注：缓存依赖bean到被依赖bean的映射
	 * */
	private final Map<String, Set<String>> dependentBeanMap = new ConcurrentHashMap<>(64);

	/** Map between depending bean names: bean name to Set of bean names for the bean's dependencies.
	 * 注：缓存被依赖bean到依赖bean的映射
	 * */
	private final Map<String, Set<String>> dependenciesForBeanMap = new ConcurrentHashMap<>(64);


	@Override
	public void registerSingleton(String beanName, Object singletonObject) throws IllegalStateException {
		Assert.notNull(beanName, "Bean name must not be null");
		Assert.notNull(singletonObject, "Singleton object must not be null");
		synchronized (this.singletonObjects) {
			Object oldObject = this.singletonObjects.get(beanName);
			if (oldObject != null) {
				throw new IllegalStateException("Could not register object [" + singletonObject +
						"] under bean name '" + beanName + "': there is already object [" + oldObject + "] bound");
			}
			addSingleton(beanName, singletonObject);
		}
	}

	/**
	 * Add the given singleton object to the singleton cache of this factory.
	 * <p>To be called for eager registration of singletons.
	 * 注：将指定的单例bean实例添加到单例bean缓存中
	 * @param beanName the name of the bean
	 * @param singletonObject the singleton object
	 */
	protected void addSingleton(String beanName, Object singletonObject) {
		synchronized (this.singletonObjects) {
			/**
			 * 注：将已创建的单例bean实例添加到单例缓存池中
			 * - 既然bean实例已经存在了，顺便会清空二级、三级缓存。
			 * - 添加已注册单例bean缓存
			 */
			this.singletonObjects.put(beanName, singletonObject);
			this.singletonFactories.remove(beanName);
			this.earlySingletonObjects.remove(beanName);
			this.registeredSingletons.add(beanName);
		}
	}

	/**
	 * Add the given singleton factory for building the specified singleton
	 * if necessary.
	 * <p>To be called for eager registration of singletons, e.g. to be able to
	 * resolve circular references.
	 * 注：将指定的单例工厂添加到三级缓存中。
	 * - 该方法会在早期单例注册时调用，主要是为了能够解决循环引用。
	 * @param beanName the name of the bean
	 * @param singletonFactory the factory for the singleton object
	 */
	protected void addSingletonFactory(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(singletonFactory, "Singleton factory must not be null");
		synchronized (this.singletonObjects) {
			// 注：判断一级缓存中没有当前bean定义
			if (!this.singletonObjects.containsKey(beanName)) {
				// 注：将获取当前bean的对象工厂添加到三级缓存中
				this.singletonFactories.put(beanName, singletonFactory);
				// 注：移除可能存在的二级缓存
				this.earlySingletonObjects.remove(beanName);
				// 注：将该bean的名称缓存到已注册单例bean缓存中
				this.registeredSingletons.add(beanName);
			}
		}
	}

	/**
	 * 注：通过指定的bean名称从单例bean缓存中获取单例对象；
	 * - 默认情况下允许获取当前bean的早期引用对象，即尚未执行属性填充以及初始化方法的对象。【注意这里是是否对当前bean获取早期引用，而不是是否返回！】
	 * - 如果无法从单例缓存中找到，那么就会返回null
	 */
	@Override
	@Nullable
	public Object getSingleton(String beanName) {
		return getSingleton(beanName, true);
	}

	/**
	 * Return the (raw) singleton object registered under the given name.
	 * <p>Checks already instantiated singletons and also allows for an early
	 * reference to a currently created singleton (resolving a circular reference).
	 * 注：返回指定beanName的已注册的原生bean；（原生的含义是：如果是要获取工厂bean对象，这里会返回工厂对象。）
	 * - 检查已经实例化的单例bean，并且也允许对正在创建的bean进行早期引用（用于解决循环引用）
	 * @param beanName the name of the bean to look for
	 * @param allowEarlyReference whether early references should be created or not
	 * 注：是否需从三级缓存中获取(创建说法不太准确)早期引用。【注意这里是是否对当前bean早期引用进行获取，而不是是否返回！】
	 * @return the registered singleton object, or {@code null} if none found
	 * Q1：三级缓存中的ObjectFactory实例到底是什么？
	 * 三级缓存中存储的是getEarlyBeanReference方法，该方法就是将未填充属性的刚实例化的对象返回，并执行SmartInstantiationAwareBeanPostProcessor后置处理器。
	 * @see AbstractAutowireCapableBeanFactory#getEarlyBeanReference
	 * AbstractAutowireCapableBeanFactory#doCreateBean
	 * Q2：为什么要三级缓存，直接要二级缓存不行吗？
	 * 从两个方面考虑：
	 * 	 1. 通过getEarlyBeanReference方法，执行了后置处理器
	 * 	 2. 【重要】三级缓存解决了早期引用后续可能会被覆盖的问题。即在后续初始化时，可能后置处理器返回了不同于早期引用的其他对象，如代理对象。
	 * 	 3. 有些情况下是不需要获取缓存中的对象，也更不需要获取早期对象，比如原型bean(以原型bean为例)
	 * 	 	用户获取bean实例时，只会传入bean的名称，此时根本不知道是否为单例bean，如果获取了早期对象，就破坏了原型模式
	 * 	 	如果在先合并bean定义后再判断是否需要从缓存中获取，就可能会影响性能了，因为设计父子bean定义还需要合并。
	 * 	    因此，在bean创建后，初始化前，就会判断是否需要暴露单例bean的早期引用对象，允许则先存在三级缓存中。
	 * 	    - 凡是存在三级缓存的bean，默认getBean均可以获取早期引用。
	 * 	    - 三级缓存实际就是【是否可返回早期对象的判断语句】。而方法入参allowEarlyReference含义为【是否从三级缓存中获取早期对象】
	 */
	@Nullable
	protected Object getSingleton(String beanName, boolean allowEarlyReference) {
		// 注：① 先尝试从单例对象缓存(一级，完全初始化)中获取，存在则直接返回该单例对象
		Object singletonObject = this.singletonObjects.get(beanName);
		if (singletonObject == null && isSingletonCurrentlyInCreation(beanName)) {
			// 注：如果当前bean是正在创建过程中的单例bean，则尝试是否能获取到其早期实例引用(尚未属性填充及初始化)。
			synchronized (this.singletonObjects) {	// 注：所有设计单例bean的创建动作，spring内部均是对单例bean缓存对象进行加锁【重要操作大家都停一停】
				// 注：② 再尝试从早期引用对象缓存(二级，尚未属性填充及初始化)中获取，存在则直接返回。【注意早期引用的获取不需要受参数allowEarlyReference限制】
				singletonObject = this.earlySingletonObjects.get(beanName);
				if (singletonObject == null && allowEarlyReference) {
					// 注：③ 如果允许创建当前bean的早期引用对象【参数决定】，则这里会尝试从单例创建工厂缓存(二级)中获取早期引用对象的函数式接口实例-ObjectFactory
					ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
					if (singletonFactory != null) {		// 注：有可能bean工厂不允许某些bean早期暴露-earlySingletonExposure
						// 注：获取三级缓存中的ObjectFactory实例，并获取早期引用对象
						singletonObject = singletonFactory.getObject();
						// 注：将早期引用对象缓存在二级缓存中
						this.earlySingletonObjects.put(beanName, singletonObject);
						// 注：将ObjectFactory实例从缓存中移除，不需要再缓存了，已经在二级缓存中。
						this.singletonFactories.remove(beanName);
					}
				}
			}
		}
		return singletonObject;
	}

	/**
	 * Return the (raw) singleton object registered under the given name,
	 * creating and registering a new one if none registered yet.
	 * 注：根据指定的beanName返回(原生)已注册的单例bean，如果还没有注册就进行创建和注册一个新的bean实例。
	 * - 什么是原生？如果是工厂bean那就返回工厂bean
	 * @param beanName the name of the bean
	 * @param singletonFactory the ObjectFactory to lazily create the singleton
	 * with, if necessary
	 * 注：用于延迟创建单例的对象工厂
	 * - 为什么要封装bean的创建过程？
	 *   因为单例bean的创建由DefaultSingletonBeanRegistry#getSingleton来进行。
	 *   而实际bean实例的创建和初始化是个非常复杂的过程，由AbstractAutowireCapableBeanFactory#createBean来进行。
	 *   创建单例bean的过程肯定要复用createBean能力，还要处理一些单例bean特有的逻辑，比如缓存单例bean，同步锁、回调、异常处理等。这些createBean是不负责处理的。
	 * @return the registered singleton object
	 */
	public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
		Assert.notNull(beanName, "Bean name must not be null");
		// 注：获取单例bean实例，对singletonObjects添加同步锁【所有涉及单例bean的操作都是对this.singletonObjects加锁】
		synchronized (this.singletonObjects) {
			Object singletonObject = this.singletonObjects.get(beanName);
			if (singletonObject == null) {
				// 注：双重检查不存在该bean实例
				if (this.singletonsCurrentlyInDestruction) {
					/**
					 * 注：获取到锁之后，还要检查当前是否处理销毁单例bean阶段；
					 * （不允许在执行销毁方法时执行单例bean的创建，不然可能发生内存泄漏）
					 * 【所以同步锁的处理不仅要关注到dcl以及第二次检查条件的处理，还要注意到异常可能带来的内存问题。】
					 */
					throw new BeanCreationNotAllowedException(beanName,
							"Singleton bean creation not allowed while singletons of this factory are in destruction " +
							"(Do not request a bean from a BeanFactory in a destroy method implementation!)");
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Creating shared instance of singleton bean '" + beanName + "'");
				}
				// 注：单例bean创建之前的回调；（默认用于将当前bean名称添加到正在创建bean名称缓存中）
				beforeSingletonCreation(beanName);
				// 注：设置创建新单例bean表示（如果创建失败或者创建过程中bean实例已经存在，该值为false，就不需要缓存单例bean）
				boolean newSingleton = false;
				// 注：根据suppressedExceptions是否为null来判断是否要记录以及排除关联异常【疑问：什么情况下是非null？】
				/**
				 * 注：suppressedExceptions是用于缓存创建一系列依赖bean时出现异常后，可以抛出完整的bean创建异常链条信息，便于我们排查。
				 * 依赖链上的异常集合由最开始的单例bean创建前初始化，逐层抛出后，也将由最开始单例bean捕捉到BeanCreationException后汇总抛出。
				 * - 当前bean是否为最开始需要初始化的单例bean呢？就通过suppressedExceptions属性是否初始化了，未初始化(null)就是最初bean。
				 * - 在最初bean抛出异常或返回之前，finally中都需要将suppressedExceptions属性重置为null。
				 */
				boolean recordSuppressedExceptions = (this.suppressedExceptions == null);
				if (recordSuppressedExceptions) {
					// 注：最初bean初始化异常集合，非最初bean不需要初始化
					this.suppressedExceptions = new LinkedHashSet<>();
				}
				try {
					// 注：执行创建&初始化当前bean的回调方法【非常重点】
					singletonObject = singletonFactory.getObject();
					// 注：设置创建新的bean实例标识，后续会将该实例添加到单例缓存中
					newSingleton = true;
				}
				catch (IllegalStateException ex) {
					// Has the singleton object implicitly appeared in the meantime ->
					// if yes, proceed with it since the exception indicates that state.
					/**
					 * 注：有可能在运行时单例bean实例已经隐式创建了，如果已经创建就继续返回即可
					 * 这里你可能存在很大的疑惑，前面不是已经将该beanName放置在正在创建bean的过程中么，而且还有缓存锁？为什么这里单例bean已经在单缓存中了？
					 * 根据git记录，我大概清楚了这里是考虑可能在初始化工厂对象时，内部也隐式地创建了单例bean对象。这种情况下会抛出ImplicitlyAppearedSingletonException异常。
					 * - 问题是解决了，但是我仍觉得此处真的是非常不好的代码组织方式。
					 * 1. 单例bean创建过程中抛出IllegalStateException异常，到底算不算创建失败？这里好像大部分情况下算是成功的，成功的为什么要通过异常抛出来。
					 * 	  本来非常清晰的单例bean创建流程，这里的异常处理我相信会让很多人摸不着头脑，实际上这里只是为了解决一个非常小的问题，却极大影响代码质量，得不偿失。
					 * 2. 这里异常的处理，破坏了之前设计的异常集合汇总处理。
					 * 	  试想如果创建流程内部抛出IllegalStateException异常，并且无法从单例缓存中获取实例。
					 * 	  这里异常抛出后外层只能感知到内部bean的异常，根本不知道哪个业务bean依赖链路上依赖的这个内部bean。
					 */
					singletonObject = this.singletonObjects.get(beanName);
					if (singletonObject == null) {	// 注：如果没有单例bean，就抛出异常
						throw ex;
					}
				}
				catch (BeanCreationException ex) {
					// 注：如果在创建bean实例的时候出现异常，逐层抛出，并在最外层一起抛给外层
					if (recordSuppressedExceptions) {
						// 注：在创建bean时出现异常时，可能需要抛出压缩异常集合
						for (Exception suppressedException : this.suppressedExceptions) {
							ex.addRelatedCause(suppressedException);
						}
					}
					throw ex;
				}
				finally {
					if (recordSuppressedExceptions) {
						// 注：无论是成功还是异常，最初bean创建结束后，这里都需要重置异常集合，以便下一个bean创建流程使用
						this.suppressedExceptions = null;
					}
					// 注：：单例bean创建之后的回调；（默认用于将当前bean名称从正在创建bean名称缓存中去除）
					afterSingletonCreation(beanName);
				}
				if (newSingleton) {
					/**
					 * 注：如果成功创建了单例bean实例，就将该实例
					 * 1. 添加到单例bean缓存中
					 * 2. 将该bean从二、三级缓存中移除
					 * 3. 将该bean名称添加到从已注册缓存中
					 */
					addSingleton(beanName, singletonObject);
				}
			}
			return singletonObject;
		}
	}

	/**
	 * Register an exception that happened to get suppressed during the creation of a
	 * singleton bean instance, e.g. a temporary circular reference resolution problem.
	 * <p>The default implementation preserves any given exception in this registry's
	 * collection of suppressed exceptions, up to a limit of 100 exceptions, adding
	 * them as related causes to an eventual top-level {@link BeanCreationException}.
	 * @param ex the Exception to register
	 * @see BeanCreationException#getRelatedCauses()
	 */
	protected void onSuppressedException(Exception ex) {
		synchronized (this.singletonObjects) {
			if (this.suppressedExceptions != null && this.suppressedExceptions.size() < SUPPRESSED_EXCEPTIONS_LIMIT) {
				this.suppressedExceptions.add(ex);
			}
		}
	}

	/**
	 * Remove the bean with the given name from the singleton cache of this factory,
	 * to be able to clean up eager registration of a singleton if creation failed.
	 * @param beanName the name of the bean
	 * @see #getSingletonMutex()
	 */
	protected void removeSingleton(String beanName) {
		synchronized (this.singletonObjects) {
			this.singletonObjects.remove(beanName);
			this.singletonFactories.remove(beanName);
			this.earlySingletonObjects.remove(beanName);
			this.registeredSingletons.remove(beanName);
		}
	}

	@Override
	public boolean containsSingleton(String beanName) {
		return this.singletonObjects.containsKey(beanName);
	}

	@Override
	public String[] getSingletonNames() {
		synchronized (this.singletonObjects) {
			return StringUtils.toStringArray(this.registeredSingletons);
		}
	}

	@Override
	public int getSingletonCount() {
		synchronized (this.singletonObjects) {
			return this.registeredSingletons.size();
		}
	}


	public void setCurrentlyInCreation(String beanName, boolean inCreation) {
		Assert.notNull(beanName, "Bean name must not be null");
		if (!inCreation) {
			this.inCreationCheckExclusions.add(beanName);
		}
		else {
			this.inCreationCheckExclusions.remove(beanName);
		}
	}

	public boolean isCurrentlyInCreation(String beanName) {
		Assert.notNull(beanName, "Bean name must not be null");
		return (!this.inCreationCheckExclusions.contains(beanName) && isActuallyInCreation(beanName));
	}

	protected boolean isActuallyInCreation(String beanName) {
		return isSingletonCurrentlyInCreation(beanName);
	}

	/**
	 * Return whether the specified singleton bean is currently in creation
	 * (within the entire factory).
	 * @param beanName the name of the bean
	 */
	public boolean isSingletonCurrentlyInCreation(String beanName) {
		return this.singletonsCurrentlyInCreation.contains(beanName);
	}

	/**
	 * Callback before singleton creation.
	 * <p>The default implementation register the singleton as currently in creation.
	 * 注：单例bean创建之前的回调
	 * 默认实现是将当前beanName添加到正在创建的集合中
	 * @param beanName the name of the singleton about to be created
	 * @see #isSingletonCurrentlyInCreation
	 */
	protected void beforeSingletonCreation(String beanName) {
		// 注：这里检查是否正在创建中；并把beanName添加到singletonsCurrentlyInCreation集合中
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.add(beanName)) {
			throw new BeanCurrentlyInCreationException(beanName);
		}
	}

	/**
	 * Callback after singleton creation.
	 * <p>The default implementation marks the singleton as not in creation anymore.
	 * 注：单例bean创建之后的回调
	 * 默认的实现是将当前beanName从正在创建实例列表中移除
	 * @param beanName the name of the singleton that has been created
	 * @see #isSingletonCurrentlyInCreation
	 */
	protected void afterSingletonCreation(String beanName) {
		// 注：这里将beanName从singletonsCurrentlyInCreation列表中移除
		if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.remove(beanName)) {
			throw new IllegalStateException("Singleton '" + beanName + "' isn't currently in creation");
		}
	}


	/**
	 * Add the given bean to the list of disposable beans in this registry.
	 * <p>Disposable beans usually correspond to registered singletons,
	 * matching the bean name but potentially being a different instance
	 * (for example, a DisposableBean adapter for a singleton that does not
	 * naturally implement Spring's DisposableBean interface).
	 * @param beanName the name of the bean
	 * @param bean the bean instance
	 */
	public void registerDisposableBean(String beanName, DisposableBean bean) {
		// 注册用于销毁的bean到disposableBeans缓存
		synchronized (this.disposableBeans) {
			this.disposableBeans.put(beanName, bean);
		}
	}

	/**
	 * Register a containment relationship between two beans,
	 * e.g. between an inner bean and its containing outer bean.
	 * <p>Also registers the containing bean as dependent on the contained bean
	 * in terms of destruction order.
	 * @param containedBeanName the name of the contained (inner) bean
	 * @param containingBeanName the name of the containing (outer) bean
	 * @see #registerDependentBean
	 */
	public void registerContainedBean(String containedBeanName, String containingBeanName) {
		synchronized (this.containedBeanMap) {
			Set<String> containedBeans =
					this.containedBeanMap.computeIfAbsent(containingBeanName, k -> new LinkedHashSet<>(8));
			if (!containedBeans.add(containedBeanName)) {
				return;
			}
		}
		registerDependentBean(containedBeanName, containingBeanName);
	}

	/**
	 * Register a dependent bean for the given bean,
	 * to be destroyed before the given bean is destroyed.
	 * 注：将依赖bean(beanName)以及被依赖bean(dependentBeanName)注册到两个缓存中；
	 * - dependentBeanMap：维护依赖bean到被依赖bean的映射。【方便bean创建时判断循环依赖】
	 * - dependenciesForBeanMap：维护被依赖bean到依赖bean的映射。【方便bean销毁时先销毁依赖bean】
	 * 【dependentBeanName 依赖 beanName】
	 * @param beanName the name of the bean  -- B (假设A 依赖  B)
	 * @param dependentBeanName the name of the dependent bean -- A (假设A 依赖  B)
	 */
	public void registerDependentBean(String beanName, String dependentBeanName) {
		// 注：为什么dependentBeanName不处理别名呢？
		String canonicalName = canonicalName(beanName);

		synchronized (this.dependentBeanMap) {
			Set<String> dependentBeans =
					this.dependentBeanMap.computeIfAbsent(canonicalName, k -> new LinkedHashSet<>(8));
			if (!dependentBeans.add(dependentBeanName)) {
				// 注：已经添加过就直接返回
				return;
			}
		}

		synchronized (this.dependenciesForBeanMap) {
			Set<String> dependenciesForBean =
					this.dependenciesForBeanMap.computeIfAbsent(dependentBeanName, k -> new LinkedHashSet<>(8));
			dependenciesForBean.add(canonicalName);
		}
	}

	/**
	 * Determine whether the specified dependent bean has been registered as
	 * dependent on the given bean or on any of its transitive dependencies.
	 * 注：判断是否指定的依赖bean(beanName)是否为指定的bean(dependentBeanName)的依赖bean.
	 * 或者其依赖bean的依赖bean.
	 * 【判断dependentBeanName 是否依赖 beanName】
	 * @param beanName the name of the bean to check
	 * @param dependentBeanName the name of the dependent bean
	 * @since 4.0
	 */
	protected boolean isDependent(String beanName, String dependentBeanName) {
		synchronized (this.dependentBeanMap) {
			// 注：下面通过深度搜索遍历来判断beanName的依赖树下是否包含dependentBeanName
			return isDependent(beanName, dependentBeanName, null);
		}
	}

	/**
	 * 注：深度搜索遍历来判断beanName的依赖树下是否包含dependentBeanName
	 * 【判断dependentBeanName 是否依赖 beanName】
	 * @param beanName 判断的目标bean名称【根节点】
	 * @param dependentBeanName 依赖的bean名称
	 * @param alreadySeen 如果当前根节点已经搜索过了，就不需要继续搜索其子树，直接返回即可
	 * @return true: 存在依赖；反之，不存在依赖。
	 */
	private boolean isDependent(String beanName, String dependentBeanName, @Nullable Set<String> alreadySeen) {
		if (alreadySeen != null && alreadySeen.contains(beanName)) {
			// 注：在搜索的过程中如果已经搜索过该beanName，那就说明该beanName依赖树下没有dependentBeanName，直接返回false即可。
			return false;
		}
		String canonicalName = canonicalName(beanName);		// 注：处理下bean名称的别名
		Set<String> dependentBeans = this.dependentBeanMap.get(canonicalName);
		if (dependentBeans == null) {
			// 注：当前bean不存在依赖，返回false;
			return false;
		}
		if (dependentBeans.contains(dependentBeanName)) {
			// 注：当前bean存在依赖，返回true;
			return true;
		}
		// 注：判断所有需依赖于beanName的bean是否有被dependentBeanName依赖的，如果有那就意味着dependentBeanName间接依赖于beanName
		for (String transitiveDependency : dependentBeans) {
			if (alreadySeen == null) {
				alreadySeen = new HashSet<>();
			}
			alreadySeen.add(beanName);	// 注：将当前bean记录下来，下次再碰到就可以直接返回了
			if (isDependent(transitiveDependency, dependentBeanName, alreadySeen)) {
				// 注：遍历递归调用，只要有一个依赖的依赖存在dependentBeanName，那就返回true！
				return true;
			}
		}
		return false;
	}

	/**
	 * Determine whether a dependent bean has been registered for the given name.
	 * @param beanName the name of the bean to check
	 */
	protected boolean hasDependentBean(String beanName) {
		return this.dependentBeanMap.containsKey(beanName);
	}

	/**
	 * Return the names of all beans which depend on the specified bean, if any.
	 * @param beanName the name of the bean
	 * @return the array of dependent bean names, or an empty array if none
	 */
	public String[] getDependentBeans(String beanName) {
		Set<String> dependentBeans = this.dependentBeanMap.get(beanName);
		if (dependentBeans == null) {
			return new String[0];
		}
		synchronized (this.dependentBeanMap) {
			return StringUtils.toStringArray(dependentBeans);
		}
	}

	/**
	 * Return the names of all beans that the specified bean depends on, if any.
	 * @param beanName the name of the bean
	 * @return the array of names of beans which the bean depends on,
	 * or an empty array if none
	 */
	public String[] getDependenciesForBean(String beanName) {
		Set<String> dependenciesForBean = this.dependenciesForBeanMap.get(beanName);
		if (dependenciesForBean == null) {
			return new String[0];
		}
		synchronized (this.dependenciesForBeanMap) {
			return StringUtils.toStringArray(dependenciesForBean);
		}
	}

	public void destroySingletons() {
		if (logger.isTraceEnabled()) {
			logger.trace("Destroying singletons in " + this);
		}
		synchronized (this.singletonObjects) {
			this.singletonsCurrentlyInDestruction = true;
		}

		String[] disposableBeanNames;
		synchronized (this.disposableBeans) {
			disposableBeanNames = StringUtils.toStringArray(this.disposableBeans.keySet());
		}
		for (int i = disposableBeanNames.length - 1; i >= 0; i--) {
			destroySingleton(disposableBeanNames[i]);
		}

		this.containedBeanMap.clear();
		this.dependentBeanMap.clear();
		this.dependenciesForBeanMap.clear();

		clearSingletonCache();
	}

	/**
	 * Clear all cached singleton instances in this registry.
	 * @since 4.3.15
	 */
	protected void clearSingletonCache() {
		synchronized (this.singletonObjects) {
			this.singletonObjects.clear();
			this.singletonFactories.clear();
			this.earlySingletonObjects.clear();
			this.registeredSingletons.clear();
			this.singletonsCurrentlyInDestruction = false;
		}
	}

	/**
	 * Destroy the given bean. Delegates to {@code destroyBean}
	 * if a corresponding disposable bean instance is found.
	 * 注：
	 * @param beanName the name of the bean
	 * @see #destroyBean
	 */
	public void destroySingleton(String beanName) {
		// Remove a registered singleton of the given name, if any.
		removeSingleton(beanName);

		// Destroy the corresponding DisposableBean instance.
		DisposableBean disposableBean;
		synchronized (this.disposableBeans) {
			disposableBean = (DisposableBean) this.disposableBeans.remove(beanName);
		}
		destroyBean(beanName, disposableBean);
	}

	/**
	 * Destroy the given bean. Must destroy beans that depend on the given
	 * bean before the bean itself. Should not throw any exceptions.
	 * @param beanName the name of the bean
	 * @param bean the bean instance to destroy
	 */
	protected void destroyBean(String beanName, @Nullable DisposableBean bean) {
		// Trigger destruction of dependent beans first...
		Set<String> dependencies;
		synchronized (this.dependentBeanMap) {
			// Within full synchronization in order to guarantee a disconnected Set
			dependencies = this.dependentBeanMap.remove(beanName);
		}
		if (dependencies != null) {
			if (logger.isTraceEnabled()) {
				logger.trace("Retrieved dependent beans for bean '" + beanName + "': " + dependencies);
			}
			for (String dependentBeanName : dependencies) {
				destroySingleton(dependentBeanName);
			}
		}

		// Actually destroy the bean now...
		if (bean != null) {
			try {
				bean.destroy();
			}
			catch (Throwable ex) {
				if (logger.isWarnEnabled()) {
					logger.warn("Destruction of bean with name '" + beanName + "' threw an exception", ex);
				}
			}
		}

		// Trigger destruction of contained beans...
		Set<String> containedBeans;
		synchronized (this.containedBeanMap) {
			// Within full synchronization in order to guarantee a disconnected Set
			containedBeans = this.containedBeanMap.remove(beanName);
		}
		if (containedBeans != null) {
			for (String containedBeanName : containedBeans) {
				destroySingleton(containedBeanName);
			}
		}

		// Remove destroyed bean from other beans' dependencies.
		synchronized (this.dependentBeanMap) {
			for (Iterator<Map.Entry<String, Set<String>>> it = this.dependentBeanMap.entrySet().iterator(); it.hasNext();) {
				Map.Entry<String, Set<String>> entry = it.next();
				Set<String> dependenciesToClean = entry.getValue();
				dependenciesToClean.remove(beanName);
				if (dependenciesToClean.isEmpty()) {
					it.remove();
				}
			}
		}

		// Remove destroyed bean's prepared dependency information.
		this.dependenciesForBeanMap.remove(beanName);
	}

	/**
	 * Exposes the singleton mutex to subclasses and external collaborators.
	 * 注：向子类以及外部相关类暴露出单例互斥锁对象
	 * <p>Subclasses should synchronize on the given Object if they perform
	 * any sort of extended singleton creation phase. In particular, subclasses
	 * should <i>not</i> have their own mutexes involved in singleton creation,
	 * to avoid the potential for deadlocks in lazy-init situations.
	 * - 如果子类在执行任何扩展类型的单例创建阶段时都应该添加当前返回对象的同步锁。
	 * 特别地，子类不应该在创建单例bean时使用他们自己的互斥对象，以避免在懒加载场景出现潜在的死锁问题。
	 */
	@Override
	public final Object getSingletonMutex() {
		// 注：在创建单例bean时会对单例bean缓存map加锁
		return this.singletonObjects;
	}

}
