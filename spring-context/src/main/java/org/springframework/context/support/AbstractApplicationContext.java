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

package org.springframework.context.support;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.CachedIntrospectionResults;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.support.ResourceEditorRegistrar;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.HierarchicalMessageSource;
import org.springframework.context.LifecycleProcessor;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.context.event.ContextStoppedEvent;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.context.expression.StandardBeanExpressionResolver;
import org.springframework.context.weaving.LoadTimeWeaverAware;
import org.springframework.context.weaving.LoadTimeWeaverAwareProcessor;
import org.springframework.core.ResolvableType;
import org.springframework.core.SpringProperties;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Abstract implementation of the {@link org.springframework.context.ApplicationContext}
 * interface. Doesn't mandate the type of storage used for configuration; simply
 * implements common context functionality. Uses the Template Method design pattern,
 * requiring concrete subclasses to implement abstract methods.
 * 注：spring上下文抽象实现类。① 抽象实现类不强制要求配置的存储类型。② 抽象类仅仅实现了通用的上下文功能。
 * ③ 上下文抽象类使用模版方法设计模式，具体的实现细节由子类实现。
 *
 * <p>In contrast to a plain BeanFactory, an ApplicationContext is supposed
 * to detect special beans defined in its internal bean factory:
 * Therefore, this class automatically registers
 * {@link org.springframework.beans.factory.config.BeanFactoryPostProcessor BeanFactoryPostProcessors},
 * {@link org.springframework.beans.factory.config.BeanPostProcessor BeanPostProcessors},
 * and {@link org.springframework.context.ApplicationListener ApplicationListeners}
 * which are defined as beans in the context.
 * 注：与普通bean工厂相比，应用上下文会侦测内部特殊的bean，
 * 比如bean工厂后置处理器，bean后置处理器，应用监听器等，这些类定义在上下文内部并且会被自动注册。
 *
 * <p>A {@link org.springframework.context.MessageSource} may also be supplied
 * as a bean in the context, with the name "messageSource"; otherwise, message
 * resolution is delegated to the parent context. Furthermore, a multicaster
 * for application events can be supplied as an "applicationEventMulticaster" bean
 * of type {@link org.springframework.context.event.ApplicationEventMulticaster}
 * in the context; otherwise, a default multicaster of type
 * {@link org.springframework.context.event.SimpleApplicationEventMulticaster} will be used.
 * 注：MessageSource也会被作为spring上下文中的bean进行提供，bean名为"messageSource"。否则，消息解析讲委托给父上下文。
 * 此外，应用时间的多播器(ApplicationEventMulticaster)也可以提供，多播器的默认类型为SimpleApplicationEventMulticaster。
 *
 * <p>Implements resource loading by extending
 * {@link org.springframework.core.io.DefaultResourceLoader}.
 * Consequently treats non-URL resource paths as class path resources
 * (supporting full class path resource names that include the package path,
 * e.g. "mypackage/myresource.dat"), unless the {@link #getResourceByPath}
 * method is overridden in a subclass.
 * 注：抽象应用上下文通过继承DefaultResourceLoader来实现资源加载。
 * 除非在(DefaultResourceLoader)子类中重写getResourceByPath方法，否则会将非URL资源路径视为类路径资源。
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @author Stephane Nicoll
 * @author Sam Brannen
 * @author Sebastien Deleuze
 * @since January 21, 2001
 * @see #refreshBeanFactory
 * @see #getBeanFactory
 * @see org.springframework.beans.factory.config.BeanFactoryPostProcessor
 * @see org.springframework.beans.factory.config.BeanPostProcessor
 * @see org.springframework.context.event.ApplicationEventMulticaster
 * @see org.springframework.context.ApplicationListener
 * @see org.springframework.context.MessageSource
 */
public abstract class AbstractApplicationContext extends DefaultResourceLoader
		implements ConfigurableApplicationContext {

	/**
	 * Name of the MessageSource bean in the factory.
	 * If none is supplied, message resolution is delegated to the parent.
	 * 注：当前bean工厂中消息资源的bean名称。如果没有提供消息，消息解析讲委托给父节点。
	 * @see MessageSource
	 */
	public static final String MESSAGE_SOURCE_BEAN_NAME = "messageSource";

	/**
	 * Name of the LifecycleProcessor bean in the factory.
	 * If none is supplied, a DefaultLifecycleProcessor is used.
	 * 注：当前bean工厂的生命周期处理器的bean名称。
	 * 如果用户未指定，则默认为DefaultLifecycleProcessor。
	 * @see org.springframework.context.LifecycleProcessor
	 * @see org.springframework.context.support.DefaultLifecycleProcessor
	 */
	public static final String LIFECYCLE_PROCESSOR_BEAN_NAME = "lifecycleProcessor";

	/**
	 * Name of the ApplicationEventMulticaster bean in the factory.
	 * If none is supplied, a default SimpleApplicationEventMulticaster is used.
	 * 注：当前bean工厂的应用事件广播器的bean名称
	 * 如果没有提供，则使用默认的SimpleApplicationEventMulticaster。
	 * @see org.springframework.context.event.ApplicationEventMulticaster
	 * @see org.springframework.context.event.SimpleApplicationEventMulticaster
	 */
	public static final String APPLICATION_EVENT_MULTICASTER_BEAN_NAME = "applicationEventMulticaster";

	/**
	 * Boolean flag controlled by a {@code spring.spel.ignore} system property that instructs Spring to
	 * ignore SpEL, i.e. to not initialize the SpEL infrastructure.
	 * <p>The default is "false".
	 * 注：根据系统属性（"spring.spel.ignore"）来判断是否忽略spel表达式。
	 * spel，即Spring Expression Language. 相关参考：https://zhuanlan.zhihu.com/p/174786047
	 */
	private static final boolean shouldIgnoreSpel = SpringProperties.getFlag("spring.spel.ignore");

	/**
	 * Whether this environment lives within a native image.
	 * 注：当前环境是否在本地镜像中（如：GraalVM, https://zhuanlan.zhihu.com/p/137836206），这种情况无法动态加载织入且只能使用JDK动态代理。
	 * Exposed as a private static field rather than in a {@code NativeImageDetector.inNativeImage()} static method due to https://github.com/oracle/graal/issues/2594.
	 * @see <a href="https://github.com/oracle/graal/blob/master/sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/ImageInfo.java">ImageInfo.java</a>
	 */
	private static final boolean IN_NATIVE_IMAGE = (System.getProperty("org.graalvm.nativeimage.imagecode") != null);


	static {
		// Eagerly load the ContextClosedEvent class to avoid weird classloader issues
		// on application shutdown in WebLogic 8.1. (Reported by Dustin Woods.)
		// 注：提前加载ContextClosedEvent类，以避免在应用关闭时出现意外的类加载器问题
		ContextClosedEvent.class.getName();
	}


	/** Logger used by this class. Available to subclasses.
	 * 注：当前应用上下文实例日志对象。权限为protected。
	 */
	protected final Log logger = LogFactory.getLog(getClass());

	/** Unique id for this context, if any.
	 * 注：当前上下文实例的唯一ID
	 */
	private String id = ObjectUtils.identityToString(this);

	/** Display name.
	 * 注：当前上下文的展示名称
	 */
	private String displayName = ObjectUtils.identityToString(this);

	/** Parent context.
	 * 注：当前应用上下文的父应用上下文
	 */
	@Nullable
	private ApplicationContext parent;

	/** Environment used by this context.
	 * 注：当前应用上下文持有的可配置环境对象
	 */
	@Nullable
	private ConfigurableEnvironment environment;

	/** BeanFactoryPostProcessors to apply on refresh. 、
	 * 注：当前应用上下文持有的所有bean工厂后置处理器【这个是应用上下文手动注册进来的Bean工厂后置处理器】
	 */
	private final List<BeanFactoryPostProcessor> beanFactoryPostProcessors = new ArrayList<>();

	/** System time in milliseconds when this context started.
	 * 注：当前应用上下文启动的系统时间(毫秒级)
	 */
	private long startupDate;

	/** Flag that indicates whether this context is currently active.
	 * 注：用于标识当前上下文是否为激活状态
	 */
	private final AtomicBoolean active = new AtomicBoolean();

	/** Flag that indicates whether this context has been closed already.
	 * 注：用于标识当前上下文是否已经关闭
	 */
	private final AtomicBoolean closed = new AtomicBoolean();

	/** Synchronization monitor for the "refresh" and "destroy".
	 * 注：用于刷新、销毁操作的同步锁
	 */
	private final Object startupShutdownMonitor = new Object();

	/** Reference to the JVM shutdown hook, if registered.
	 * 注：用于JVM关闭回调
	 */
	@Nullable
	private Thread shutdownHook;

	/** ResourcePatternResolver used by this context.
	 * 注：spring上下文中的资源路径样式解析器（这是抽象应用上下文中唯一必须的属性）
	 * - 疑问：resourcePatternResolver和ResourceLoader的关系是什么？装饰？
	 *   答：是装饰的关系。resourcePatternResolver通过装饰ResourceLoader添加了样式路径匹配出多个资源加载的能力。
	 *   	 抽象应用上下文继承了DefaultResourceLoader类已经实现了(单)资源加载的能力，这里通过resourcePatternResolver实现样式路径多资源加载的能力。
	 */
	private ResourcePatternResolver resourcePatternResolver;

	/** LifecycleProcessor for managing the lifecycle of beans within this context.
	 * 注：spring上下文中生命周期处理器，用于管理bean的生命周期
	 */
	@Nullable
	private LifecycleProcessor lifecycleProcessor;

	/** MessageSource we delegate our implementation of this interface to.
	 * 注：spring消息源的具体实现类对象
	 */
	@Nullable
	private MessageSource messageSource;

	/** Helper class used in event publishing.
	 * 注：事件发布的助手类，默认为
	 * @see org.springframework.context.event.SimpleApplicationEventMulticaster
	 */
	@Nullable
	private ApplicationEventMulticaster applicationEventMulticaster;

	/** Statically specified listeners.
	 * 注：静态的(如何理解？)、指定的事件监听器集合
	 */
	private final Set<ApplicationListener<?>> applicationListeners = new LinkedHashSet<>();

	/** Local listeners registered before refresh.
	 * 注：在刷新之前已经注册的内部监听器
	 */
	@Nullable
	private Set<ApplicationListener<?>> earlyApplicationListeners;

	/** ApplicationEvents published before the multicaster setup.
	 * 注：在多播器启动之前的应用事件集合
	 */
	@Nullable
	private Set<ApplicationEvent> earlyApplicationEvents;


	/**
	 * Create a new AbstractApplicationContext with no parent.
	 * 注：创建一个没有父应用的应用上下文对象
	 */
	public AbstractApplicationContext() {
		/**
		 * 注：初始化资源路径样式解析器
		 * - getResourcePatternResolver是一个模版方法，支持子类自定义该解析器，
		 * - 默认为PathMatchingResourcePatternResolver，支持Ant路径样式解析(可解析为多个资源句柄，详见getResources方法)。
		 */
		this.resourcePatternResolver = getResourcePatternResolver();
	}

	/**
	 * Create a new AbstractApplicationContext with the given parent context.
	 * 注：创建一个应用上下文对象，并指定父应用上下文。
	 *
	 * @param parent the parent context
	 */
	public AbstractApplicationContext(@Nullable ApplicationContext parent) {
		this();
		setParent(parent);
	}


	//---------------------------------------------------------------------
	// Implementation of ApplicationContext interface
	//---------------------------------------------------------------------

	/**
	 * Set the unique id of this application context.
	 * <p>Default is the object id of the context instance, or the name
	 * of the context bean if the context is itself defined as a bean.
	 * 注：设置当前应用上下文唯一ID
	 * @param id the unique id of the context
	 */
	@Override
	public void setId(String id) {
		this.id = id;
	}

	@Override
	public String getId() {
		return this.id;
	}

	@Override
	public String getApplicationName() {
		return "";
	}

	/**
	 * Set a friendly name for this context.
	 * Typically done during initialization of concrete context implementations.
	 * <p>Default is the object id of the context instance.
	 * 注：设置当前应用上下文展示Name
	 */
	public void setDisplayName(String displayName) {
		Assert.hasLength(displayName, "Display name must not be empty");
		this.displayName = displayName;
	}

	/**
	 * Return a friendly name for this context.
	 * @return a display name for this context (never {@code null})
	 * 注：获取当前应用上下文展示Name
	 */
	@Override
	public String getDisplayName() {
		return this.displayName;
	}

	/**
	 * Return the parent context, or {@code null} if there is no parent
	 * (that is, this context is the root of the context hierarchy).
	 * 注：返回父上下文实例。如果当前上下文无父上下文，则返回Null.
	 */
	@Override
	@Nullable
	public ApplicationContext getParent() {
		return this.parent;
	}

	/**
	 * Set the {@code Environment} for this application context.
	 * <p>Default value is determined by {@link #createEnvironment()}. Replacing the
	 * default with this method is one option but configuration through {@link
	 * #getEnvironment()} should also be considered. In either case, such modifications
	 * should be performed <em>before</em> {@link #refresh()}.
	 * 注：为当前应用上下文设置环境对象。
	 * 环境对象的默认值由createEnvironment()方法决定，子类可以重新这个方法来替换默认环境对象，
	 * 也可以通过setEnvironment方法来覆盖默认环境对象。但无论哪一种方法，都应该在refresh方法之前执行。
	 * @see org.springframework.context.support.AbstractApplicationContext#createEnvironment
	 */
	@Override
	public void setEnvironment(ConfigurableEnvironment environment) {
		this.environment = environment;
	}

	/**
	 * Return the {@code Environment} for this application context in configurable
	 * form, allowing for further customization.
	 * <p>If none specified, a default environment will be initialized via
	 * {@link #createEnvironment()}.
	 * 注：返回当前应用上下文的可配置环境对象。可配置就意味着允许进一步的修改环境对象。
	 * 如果没有通过setEnvironment设置环境对象，就会调用createEnvironment()返回默认的环境对象。
	 */
	@Override
	public ConfigurableEnvironment getEnvironment() {
		if (this.environment == null) {
			this.environment = createEnvironment();
		}
		return this.environment;
	}

	/**
	 * Create and return a new {@link StandardEnvironment}.
	 * <p>Subclasses may override this method in order to supply
	 * a custom {@link ConfigurableEnvironment} implementation.
	 * 注：创建并返回默认StandardEnvironment环境对象
	 * 模版模式基本方法，子类可以重写改方法提供一个自定义的可配置环境对象
	 */
	protected ConfigurableEnvironment createEnvironment() {
		return new StandardEnvironment();
	}

	/**
	 * Return this context's internal bean factory as AutowireCapableBeanFactory,
	 * if already available.
	 * 注：返回当前上下文内部的可配置Bean工厂
	 * @see #getBeanFactory()
	 */
	@Override
	public AutowireCapableBeanFactory getAutowireCapableBeanFactory() throws IllegalStateException {
		return getBeanFactory();
	}

	/**
	 * Return the timestamp (ms) when this context was first loaded.
	 * 注：返回上下文启动的时间戳
	 */
	@Override
	public long getStartupDate() {
		return this.startupDate;
	}

	/**
	 * Publish the given event to all listeners.
	 * <p>Note: Listeners get initialized after the MessageSource, to be able
	 * to access it within listener implementations. Thus, MessageSource
	 * implementations cannot publish events.
	 * 注：向所有监听器发布事件
	 * 注意：监听器是在消息源初始化之后才初始化的，以便监听器能够访问消息源实例。
	 * 因此消息源的实现类不能够发布事件。
	 * @param event the event to publish (may be application-specific or a
	 * standard framework event)
	 */
	@Override
	public void publishEvent(ApplicationEvent event) {
		publishEvent(event, null);
	}

	/**
	 * Publish the given event to all listeners.
	 * <p>Note: Listeners get initialized after the MessageSource, to be able
	 * to access it within listener implementations. Thus, MessageSource
	 * implementations cannot publish events.
	 * @param event the event to publish (may be an {@link ApplicationEvent}
	 * or a payload object to be turned into a {@link PayloadApplicationEvent})
	 */
	@Override
	public void publishEvent(Object event) {
		publishEvent(event, null);
	}

	/**
	 * Publish the given event to all listeners.、
	 * 注：向所有监听器发布某类型(可不指定)的事件
	 * @param event the event to publish (may be an {@link ApplicationEvent}
	 * or a payload object to be turned into a {@link PayloadApplicationEvent})
	 * @param eventType the resolved event type, if known
	 * @since 4.2
	 */
	protected void publishEvent(Object event, @Nullable ResolvableType eventType) {
		Assert.notNull(event, "Event must not be null");

		// Decorate event as an ApplicationEvent if necessary
		ApplicationEvent applicationEvent;
		if (event instanceof ApplicationEvent) {
			applicationEvent = (ApplicationEvent) event;
		}
		else {
			// 注：非内部ApplicationEvent使用PayloadApplicationEvent装饰
			applicationEvent = new PayloadApplicationEvent<>(this, event);
			if (eventType == null) {
				eventType = ((PayloadApplicationEvent<?>) applicationEvent).getResolvableType();
			}
		}

		// Multicast right now if possible - or lazily once the multicaster is initialized
		if (this.earlyApplicationEvents != null) {
			// 注：多播器尚未启动时，将发布的事件添加进early集合中
			this.earlyApplicationEvents.add(applicationEvent);
		}
		else {
			// 注：获取内部多播器并传播事件
			getApplicationEventMulticaster().multicastEvent(applicationEvent, eventType);
		}

		// Publish event via parent context as well...
		// 注：如果存在父上下文的话，这里触发在父上下文中发布事件
		if (this.parent != null) {
			if (this.parent instanceof AbstractApplicationContext) {
				((AbstractApplicationContext) this.parent).publishEvent(event, eventType);
			}
			else {
				this.parent.publishEvent(event);
			}
		}
	}

	/**
	 * Return the internal ApplicationEventMulticaster used by the context.
	 * 注：返回当前应用上下文内部的应用事件多播器
	 * @return the internal ApplicationEventMulticaster (never {@code null})
	 * @throws IllegalStateException if the context has not been initialized yet
	 */
	ApplicationEventMulticaster getApplicationEventMulticaster() throws IllegalStateException {
		if (this.applicationEventMulticaster == null) {
			throw new IllegalStateException("ApplicationEventMulticaster not initialized - " +
					"call 'refresh' before multicasting events via the context: " + this);
		}
		return this.applicationEventMulticaster;
	}

	/**
	 * Return the internal LifecycleProcessor used by the context.
	 * 注：返回当前应用上下文的生命周期处理器
	 * @return the internal LifecycleProcessor (never {@code null})
	 * @throws IllegalStateException if the context has not been initialized yet
	 */
	LifecycleProcessor getLifecycleProcessor() throws IllegalStateException {
		if (this.lifecycleProcessor == null) {
			throw new IllegalStateException("LifecycleProcessor not initialized - " +
					"call 'refresh' before invoking lifecycle methods via the context: " + this);
		}
		return this.lifecycleProcessor;
	}

	/**
	 * Return the ResourcePatternResolver to use for resolving location patterns
	 * into Resource instances. Default is a
	 * {@link org.springframework.core.io.support.PathMatchingResourcePatternResolver},
	 * supporting Ant-style location patterns.
	 * 注：返回spring上下文用于将资源路径解析为Resource对象的解析器对象。
	 * 默认是支持解析Ant资源路径样式的PathMatchingResourcePatternResolver类对象。
	 * <p>Can be overridden in subclasses, for extended resolution strategies,
	 * for example in a web environment.
	 * 注：资源路径解析器可以由子类实现当前方法以进行扩展。
	 * <p><b>Do not call this when needing to resolve a location pattern.</b>
	 * Call the context's {@code getResources} method instead, which
	 * will delegate to the ResourcePatternResolver.
	 * 注：你可以调用getResources方法来解析资源路径样式获取Resource对象。
	 * getResources内部就是用这里返回的解析器来解析路径的。
	 * @return the ResourcePatternResolver for this context
	 * @see #getResources
	 * @see org.springframework.core.io.support.PathMatchingResourcePatternResolver
	 */
	protected ResourcePatternResolver getResourcePatternResolver() {
		return new PathMatchingResourcePatternResolver(this);
	}


	//---------------------------------------------------------------------
	// Implementation of ConfigurableApplicationContext interface
	//---------------------------------------------------------------------

	/**
	 * Set the parent of this application context.
	 * <p>The parent {@linkplain ApplicationContext#getEnvironment() environment} is
	 * {@linkplain ConfigurableEnvironment#merge(ConfigurableEnvironment) merged} with
	 * this (child) application context environment if the parent is non-{@code null} and
	 * its environment is an instance of {@link ConfigurableEnvironment}.
	 * 注：设置父应用上下文
	 * 如果父应用上下文的环境为可配置类，则将父应用环境配置合并至子应用上下文中
	 * @see ConfigurableEnvironment#merge(ConfigurableEnvironment)
	 */
	@Override
	public void setParent(@Nullable ApplicationContext parent) {
		this.parent = parent;
		if (parent != null) {
			Environment parentEnvironment = parent.getEnvironment();
			if (parentEnvironment instanceof ConfigurableEnvironment) {
				getEnvironment().merge((ConfigurableEnvironment) parentEnvironment);
			}
		}
	}

	/**
	 * 注：手动向应用上下文增加一个新的Bean工厂后置处理器
	 * @param postProcessor the factory processor to register
	 */
	@Override
	public void addBeanFactoryPostProcessor(BeanFactoryPostProcessor postProcessor) {
		Assert.notNull(postProcessor, "BeanFactoryPostProcessor must not be null");
		this.beanFactoryPostProcessors.add(postProcessor);
	}

	/**
	 * Return the list of BeanFactoryPostProcessors that will get applied
	 * to the internal BeanFactory.
	 * 注：返回手动添加Bean工厂后置处理器的列表
	 */
	public List<BeanFactoryPostProcessor> getBeanFactoryPostProcessors() {
		return this.beanFactoryPostProcessors;
	}

	/**
	 * 注：增加一个应用监听器
	 * @param listener the ApplicationListener to register
	 */
	@Override
	public void addApplicationListener(ApplicationListener<?> listener) {
		Assert.notNull(listener, "ApplicationListener must not be null");
		if (this.applicationEventMulticaster != null) {
			this.applicationEventMulticaster.addApplicationListener(listener);
		}
		this.applicationListeners.add(listener);
	}

	/**
	 * Return the list of statically specified ApplicationListeners.
	 * 注：返回应用监听器的列表
	 */
	public Collection<ApplicationListener<?>> getApplicationListeners() {
		return this.applicationListeners;
	}

	/**
	 * 注：加载或者刷新spring上下文中配置化的持久性表示。
	 * 这也是一个模版方法！内部的一些列基础方法访问类型均为protected
	 */
	@Override
	public void refresh() throws BeansException, IllegalStateException {
		// 注：使用锁控制仅一次刷新动作，避免耗费资源
		synchronized (this.startupShutdownMonitor) {
			// Prepare this context for refreshing.
			/* 注: 1. 刷新前的环境预处理 */
			prepareRefresh();

			// Tell the subclass to refresh the internal bean factory.
			// 注: 2. 刷新并获取Bean工厂。这部分由子类完成。在GCC中，设置容器刷新标识，设置Bean工厂的序列号ID
			ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();

			// Prepare the bean factory for use in this context.
			// 注: 3. 应用上下文内部工厂的配置工作(可以看作spring上下文本身对Bean工厂的初始化)
			prepareBeanFactory(beanFactory);

			try {
				// Allows post-processing of the bean factory in context subclasses.
				/* 2022/1/2: 4. Bean工厂初始化完成后，子类根据不同的应用场景对Bean工厂进一步设置。
				* 注: 4. 其实无非还是注册Aware处理器、特殊的Bean后置处理器以及提前注册一些特殊的Bean */
				postProcessBeanFactory(beanFactory);

				// Invoke factory processors registered as beans in the context.
				// 注：5. 调用在当前应用上下文、内部bean工厂内注册的Bean工厂后置处理器功能
				invokeBeanFactoryPostProcessors(beanFactory);

				// Register bean processors that intercept bean creation.
				// 注：6. 注册用于拦截bean创建过程中的bean后置处理器
				registerBeanPostProcessors(beanFactory);

				// Initialize message source for this context.
				// 注：7. 初始化当前应用上下文的消息源bean
				initMessageSource();

				// Initialize event multicaster for this context.
				// 注：8. 初始化当前应用上下文的多播器bean
				initApplicationEventMulticaster();

				// Initialize other special beans in specific context subclasses.
				// 注：9. 子应用文类可以实现该方法初始化一些其他特殊bean
				onRefresh();

				// Check for listener beans and register them.
				// 注：10. 侦测事件监听器bean实例，并且注册到多播器中，触发早期事件传播
				registerListeners();

				// Instantiate all remaining (non-lazy-init) singletons.
				/* 注: 11. 初始化剩下的所有的单实例(非懒加载的) */
				finishBeanFactoryInitialization(beanFactory);

				// Last step: publish corresponding event.
				// 注：12. 调用生命周期回调方法并，发布容器已刷新的事件
				finishRefresh();
			}

			catch (BeansException ex) {
				if (logger.isWarnEnabled()) {
					logger.warn("Exception encountered during context initialization - " +
							"cancelling refresh attempt: " + ex);
				}

				// Destroy already created singletons to avoid dangling resources.
				// 注：销毁已经创建的单例bean实例
				destroyBeans();

				// Reset 'active' flag.
				// 注：重置当前bean工厂的激活状态为false
				cancelRefresh(ex);

				// Propagate exception to caller.
				// 注：将异常抛出给调用者
				throw ex;
			}

			finally {
				// Reset common introspection caches in Spring's core, since we
				// might not ever need metadata for singleton beans anymore...
				/**
				 * 注：重置Spring核心中的公共内省缓存，因为我们可能不再需要单例bean的元数据
				 */
				resetCommonCaches();
			}
		}
	}

	/**
	 * Prepare this context for refreshing, setting its startup date and
	 * active flag as well as performing any initialization of property sources.
	 * 注：进行一些上下文刷新前的准备，如记录上下文的启动时间、上下文状态、以及属性源的初始化
	 */
	protected void prepareRefresh() {
		// Switch to active.
		// 注: 记录容器启动时间，设置容器开关状态
		this.startupDate = System.currentTimeMillis();
		this.closed.set(false);
		this.active.set(true);

		// 注: 根据日志设置的级别打印日志
		if (logger.isDebugEnabled()) {
			if (logger.isTraceEnabled()) {
				logger.trace("Refreshing " + this);
			}
			else {
				logger.debug("Refreshing " + getDisplayName());
			}
		}

		// Initialize any placeholder property sources in the context environment.
		/**
		 * 注: 初始化上下文环境中任意占位配置源。
		 * - AAC中是一个protected空方法，子类可以进行设置自定义个性化属性源。
		 * - 这里的方法名称的设置存在争议。
		 * 	 - 初始化属性源是Environment类实例所负责，即便是自定义个性化（初始）属性源也是自定义Environment类型，比如StandardServletEnvironment
		 * 	 - 这里实际上做的事情是将Environment实例中起到占位符作用的属性源进行初始化并替换。initAndReplaceStubPropertySources
		 * - 什么叫占位属性源？
		 *   - 在某些上下文场景中(比如web)，其依赖的属性源无法保证在Environment实例化之前准备就绪，
		 *     因此Environment实例就无法初始化该数据源，只能先通过占位符替代。比如Web场景下依赖servletConfig配置准备就绪。
		 *     在Web上下文初始化之前，可能已经实例化Environment实例，并在web上下文刷新时，这里会根据servletConfig配置来替换为实际的Servlet配置数据源。
		 */
		initPropertySources();

		// Validate that all properties marked as required are resolvable:
		// see ConfigurablePropertyResolver#setRequiredProperties
		/* 注: 自定义环境变量验证，要求自定义的环境变量必须存在. AAC的子类重写initPropertySource方法 【CSDN 程序员欣宸】 */
		getEnvironment().validateRequiredProperties();

		// Store pre-refresh ApplicationListeners...
		/* 注: 存储早期的应用监听器 */
		if (this.earlyApplicationListeners == null) {
			this.earlyApplicationListeners = new LinkedHashSet<>(this.applicationListeners);
		}
		else {
			// Reset local application listeners to pre-refresh state.
			this.applicationListeners.clear();
			this.applicationListeners.addAll(this.earlyApplicationListeners);
		}

		// Allow for the collection of early ApplicationEvents,
		// to be published once the multicaster is available...
		/* 注: 保存容器中一些早期的事件，如果有事件发生，就放入List中，等有了事件派发器就派发出去 */
		this.earlyApplicationEvents = new LinkedHashSet<>();
	}

	/**
	 * <p>Replace any stub property sources with actual instances.
	 * 注：初始化上下文环境中任意占位属性配置源。
	 * - 在普通上下文场景中，不存在也不需要替换占位符属性源
	 * - 在Web场景中，这里会初始化并替换为实际的Servlet属性源。
	 * @see org.springframework.core.env.PropertySource.StubPropertySource
	 * // @see org.springframework.web.context.support.WebApplicationContextUtils#initServletPropertySources
	 */
	protected void initPropertySources() {
		// For subclasses: do nothing by default.
	}

	/**
	 * Tell the subclass to refresh the internal bean factory.
	 * 注：通知子类刷新内部的Bean工厂
	 * @return the fresh BeanFactory instance
	 * @see #refreshBeanFactory()
	 * @see #getBeanFactory()
	 */
	protected ConfigurableListableBeanFactory obtainFreshBeanFactory() {
		refreshBeanFactory();
		return getBeanFactory();
	}

	/**
	 * Configure the factory's standard context characteristics,
	 * such as the context's ClassLoader and post-processors.
	 * @param beanFactory the BeanFactory to configure
	 */
	/* 2022/1/2: 配置工厂的标准上下文特征，比如类加载器以及一些后置处理器
	* 参考链接：https://www.jianshu.com/p/3468118a31f9
	* */
	protected void prepareBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		// Tell the internal bean factory to use the context's class loader etc.
		/* 注: 设置Bean工厂的类加载器，一般是应用上下文的类加载器 */
		beanFactory.setBeanClassLoader(getClassLoader());
		if (!shouldIgnoreSpel) {
			/* 2022/1/2: 在Bean工厂中设置Bean表达式解析器，即Spring el表达式：StandardBeanExpressionResolver中的expressionParser属性 */
			beanFactory.setBeanExpressionResolver(new StandardBeanExpressionResolver(beanFactory.getBeanClassLoader()));
		}
		/* 注: 将资源编辑注册器ResourceEditorRegistrar对象添加到工厂的PropertyEditorRegistrar
		* 属性编辑器也是一个重点内容: https://cloud.tencent.com/developer/article/1952804?areaSource=102001.2&traceId=JdB-yt_X0oolo37YgwadG */
		beanFactory.addPropertyEditorRegistrar(new ResourceEditorRegistrar(this, getEnvironment()));

		// Configure the bean factory with context callbacks.
		// 注：下面将会在Bean工厂配置一些应用上下文的回调相关对象
		/* 注: 在Bean后置处理器集合中添加应用上下文识别处理器 */
		beanFactory.addBeanPostProcessor(new ApplicationContextAwareProcessor(this));
		/* 注: 对于下面这些Aware系列接口作为属性时依赖注入的时候会进行忽略，这些属性的复制是由上面Aware处理器进行的 */
		beanFactory.ignoreDependencyInterface(EnvironmentAware.class);
		beanFactory.ignoreDependencyInterface(EmbeddedValueResolverAware.class);
		beanFactory.ignoreDependencyInterface(ResourceLoaderAware.class);
		beanFactory.ignoreDependencyInterface(ApplicationEventPublisherAware.class);
		beanFactory.ignoreDependencyInterface(MessageSourceAware.class);
		beanFactory.ignoreDependencyInterface(ApplicationContextAware.class);

		// BeanFactory interface not registered as resolvable type in a plain factory.
		// MessageSource registered (and found for autowiring) as a bean.
		/* 注: 这里说的BeanFactory、ApplicationContext等接口是不会注册进普通工厂容器的，因此不能够作为可解析类型自动装配
		* 因此spring在工厂中添加了resolvableDependencies这个map用于存放这些东西，以便支持自动装配。
		* 这里例外的是MessageSource这个接口没有按照这种方式，因为它会在后面的步骤中作为一个bean注册到容器中，本身就支持自动装配。
		*  */
		beanFactory.registerResolvableDependency(BeanFactory.class, beanFactory);
		beanFactory.registerResolvableDependency(ResourceLoader.class, this);
		beanFactory.registerResolvableDependency(ApplicationEventPublisher.class, this);
		beanFactory.registerResolvableDependency(ApplicationContext.class, this);

		// Register early post-processor for detecting inner beans as ApplicationListeners.
		/* 注: 注入一个早期的后置处理器，对于实现了ApplicationListener的Bean在初始化完成后添加到AAC的ApplicationListeners集合中
		* 在Bean销毁完后，则会从AAC的ApplicationEventMulticaster(事件广播器)中提前删除 */
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(this));

		// Detect a LoadTimeWeaver and prepare for weaving, if found.
		/* 注: 检测LoadTimerWeaver 如果有就准备织入； */
		if (!IN_NATIVE_IMAGE && beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
			// 注: IN_NATIVE_IMAGE是判断特殊环境下，一般不是；如果检测到容器中有LoadTimerWeaver的Bean定义，就进入判断逻辑

			/* 注: 添加有关于织入的后置处理器，对于实现了检测LoadTimeWeaverAware接口的Bean可以获取到内部织入Bean(bean名为loadTimeWeaver)
			* 具体参考链接：https://wwww.cnblogs.com/Joe-Go/p/10244469.html */
			beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
			// Set a temporary ClassLoader for type matching.
			/* 注: 增加并使用ContextTypeMatchClassLoader类型加载器在加载class文件时修改了二进制数据从而织入了方法增强
			* 以后需要仔细研究下LTW，参考链接：https://www.jianshu.com/p/b26ac5e68013 */
			beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
		}

		// Register default environment beans.
		// 注: 注册spring环境的相关Bean
		if (!beanFactory.containsLocalBean(ENVIRONMENT_BEAN_NAME)) {
			// 注: 注册environment单例Bean
			beanFactory.registerSingleton(ENVIRONMENT_BEAN_NAME, getEnvironment());
		}
		if (!beanFactory.containsLocalBean(SYSTEM_PROPERTIES_BEAN_NAME)) {
			// 2022/1/2: 注册SystemProperties单例Bean
			beanFactory.registerSingleton(SYSTEM_PROPERTIES_BEAN_NAME, getEnvironment().getSystemProperties());
		}
		if (!beanFactory.containsLocalBean(SYSTEM_ENVIRONMENT_BEAN_NAME)) {
			// 2022/1/2: 注册SystemEnvironment单例Bean
			beanFactory.registerSingleton(SYSTEM_ENVIRONMENT_BEAN_NAME, getEnvironment().getSystemEnvironment());
		}
	}

	/**
	 * Modify the application context's internal bean factory after its standard
	 * initialization. All bean definitions will have been loaded, but no beans
	 * will have been instantiated yet. This allows for registering special
	 * BeanPostProcessors etc in certain ApplicationContext implementations.
	 * @param beanFactory the bean factory used by the application context
	 * 在应用上下文的内部Bean工长标准初始化完成之后用户自定义修改
	 * 此时所有的BeanDefinition都已经被加载，但是还都没有实例化
	 * 此时允许向应用上下文中注册一些特殊的Bean后置处理器
	 * 典型可见-ARWAC
	 */
	protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
		/* 2022/1/2: 参考链接：https://www.jianshu.com/p/c05aea93b939 */
	}

	/**
	 * Instantiate and invoke all registered BeanFactoryPostProcessor beans,
	 * respecting explicit order if given.
	 * <p>Must be called before singleton instantiation.
	 * 注：按照一定的顺序实例化并且调用已注册的Bean工厂后置处理器
	 * Bean后置处理器的调用比较在所有bean实例化之前。
	 */
	protected void invokeBeanFactoryPostProcessors(ConfigurableListableBeanFactory beanFactory) {
		// 注：调用bean工厂后置处理器；后置处理器的调用由PostProcessorRegistrationDelegate封装。
		PostProcessorRegistrationDelegate.invokeBeanFactoryPostProcessors(beanFactory, getBeanFactoryPostProcessors());

		// Detect a LoadTimeWeaver and prepare for weaving, if found in the meantime
		// (e.g. through an @Bean method registered by ConfigurationClassPostProcessor)、
		/**
		 * 注：bean工厂后置处理器流程中可能会想Bean容器新增bean定义。这其中可能就包含loadTimeWeaver动态织入bean，比如ConfigurationClassPostProcessor
		 * 因此，这里仍需要补充些是否新增了loadTimeWeaver的bean，并且通过tmpClassLoader来判断是否之前已经处理。
		 */
		if (beanFactory.getTempClassLoader() == null && beanFactory.containsBean(LOAD_TIME_WEAVER_BEAN_NAME)) {
			beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
			beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
		}
	}

	/**
	 * Instantiate and register all BeanPostProcessor beans,
	 * respecting explicit order if given.
	 * <p>Must be called before any instantiation of application beans.
	 * 注：按照一定顺序实例化并且注册所有的bean后置处理器。
	 * 注册过程必须在所有应用bean实例化之前。
	 */
	protected void registerBeanPostProcessors(ConfigurableListableBeanFactory beanFactory) {
		// 注：注册bean后置处理器也由PostProcessorRegistrationDelegate封装。
		PostProcessorRegistrationDelegate.registerBeanPostProcessors(beanFactory, this);
	}

	/**
	 * Initialize the MessageSource.
	 * Use parent's if none defined in this context.
	 * 注：初始化当前应用上下文的消息源bean实例
	 * 如果当前消息没有在当前应用上下文中定义，就使用父级上下文的消息源进行解析。
	 */
	protected void initMessageSource() {
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
		// 注：判断当前应用上下文的bean容器中是否包含消息源bean定义-messageSource，即是否用户设置了消息源
		if (beanFactory.containsLocalBean(MESSAGE_SOURCE_BEAN_NAME)) {
			// 注：实例化messageSource的bean实力
			this.messageSource = beanFactory.getBean(MESSAGE_SOURCE_BEAN_NAME, MessageSource.class);
			// Make MessageSource aware of parent MessageSource.
			// 注：如果当前应用上下文存在父级上下文，并且消息源是继承类型的消息源，下面就会初始化父消息源对象。
			// 疑问？消息源的解析，如果当前上下文无法解析，须满足下面两个条件，才会通过父上下文持有的消息源进行解析吧
			if (this.parent != null && this.messageSource instanceof HierarchicalMessageSource) {
				HierarchicalMessageSource hms = (HierarchicalMessageSource) this.messageSource;
				if (hms.getParentMessageSource() == null) {
					// Only set parent context as parent MessageSource if no parent MessageSource
					// registered already.
					// 注：如果父上下文没有持有消息源，这里就是设置父上下文本身
					hms.setParentMessageSource(getInternalParentMessageSource());
				}
			}
			if (logger.isTraceEnabled()) {
				logger.trace("Using MessageSource [" + this.messageSource + "]");
			}
		}
		else {
			// Use empty MessageSource to be able to accept getMessage calls.
			// 注：如果没有指定上下文的消息源，这里就使用一个空的消息源
			DelegatingMessageSource dms = new DelegatingMessageSource();
			// 注：DelegatingMessageSource也是一个继承类型的消息源，这里也需要设置父上下文消息源。
			dms.setParentMessageSource(getInternalParentMessageSource());
			this.messageSource = dms;
			// 这里手动向Bean容器中注入一个messageSource实例Bean
			beanFactory.registerSingleton(MESSAGE_SOURCE_BEAN_NAME, this.messageSource);
			if (logger.isTraceEnabled()) {
				logger.trace("No '" + MESSAGE_SOURCE_BEAN_NAME + "' bean, using [" + this.messageSource + "]");
			}
		}
	}

	/**
	 * Initialize the ApplicationEventMulticaster.
	 * Uses SimpleApplicationEventMulticaster if none defined in the context.
	 * 注：初始化应用事件多播器
	 * 如果应用没有在容器中注入applicationEventMulticaster实例bean，就使用默认的多播器-SimpleApplicationEventMulticaster
	 * @see org.springframework.context.event.SimpleApplicationEventMulticaster
	 */
	protected void initApplicationEventMulticaster() {
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
		// 注：判断应用是否存在applicationEventMulticaster的bean
		if (beanFactory.containsLocalBean(APPLICATION_EVENT_MULTICASTER_BEAN_NAME)) {
			// 注：初始化多播器实例
			this.applicationEventMulticaster =
					beanFactory.getBean(APPLICATION_EVENT_MULTICASTER_BEAN_NAME, ApplicationEventMulticaster.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Using ApplicationEventMulticaster [" + this.applicationEventMulticaster + "]");
			}
		}
		else {
			// 注：如果应用没有指定多播器，就是用默认的SimpleApplicationEventMulticaster多播器
			this.applicationEventMulticaster = new SimpleApplicationEventMulticaster(beanFactory);
			// 注：将默认多播器注入spring容器中
			beanFactory.registerSingleton(APPLICATION_EVENT_MULTICASTER_BEAN_NAME, this.applicationEventMulticaster);
			if (logger.isTraceEnabled()) {
				logger.trace("No '" + APPLICATION_EVENT_MULTICASTER_BEAN_NAME + "' bean, using " +
						"[" + this.applicationEventMulticaster.getClass().getSimpleName() + "]");
			}
		}
	}

	/**
	 * Initialize the LifecycleProcessor.
	 * Uses DefaultLifecycleProcessor if none defined in the context.
	 * @see org.springframework.context.support.DefaultLifecycleProcessor
	 */
	protected void initLifecycleProcessor() {
		ConfigurableListableBeanFactory beanFactory = getBeanFactory();
		if (beanFactory.containsLocalBean(LIFECYCLE_PROCESSOR_BEAN_NAME)) {
			this.lifecycleProcessor =
					beanFactory.getBean(LIFECYCLE_PROCESSOR_BEAN_NAME, LifecycleProcessor.class);
			if (logger.isTraceEnabled()) {
				logger.trace("Using LifecycleProcessor [" + this.lifecycleProcessor + "]");
			}
		}
		else {
			DefaultLifecycleProcessor defaultProcessor = new DefaultLifecycleProcessor();
			defaultProcessor.setBeanFactory(beanFactory);
			this.lifecycleProcessor = defaultProcessor;
			beanFactory.registerSingleton(LIFECYCLE_PROCESSOR_BEAN_NAME, this.lifecycleProcessor);
			if (logger.isTraceEnabled()) {
				logger.trace("No '" + LIFECYCLE_PROCESSOR_BEAN_NAME + "' bean, using " +
						"[" + this.lifecycleProcessor.getClass().getSimpleName() + "]");
			}
		}
	}

	/**
	 * Template method which can be overridden to add context-specific refresh work.
	 * Called on initialization of special beans, before instantiation of singletons.
	 * <p>This implementation is empty.
	 * 注：这是一个模版模式的基本方法，子上下文类可以重写此方法用于在实例化bean之前初始化一些特殊的bean。
	 * @throws BeansException in case of errors
	 * @see #refresh()
	 */
	protected void onRefresh() throws BeansException {
		// For subclasses: do nothing by default.
	}

	/**
	 * Add beans that implement ApplicationListener as listeners.
	 * Doesn't affect other listeners, which can be added without being beans.
	 * 注：将所有的应用监听器注册到多播器中，此时多播器已经是实例化了。
	 * 注意此注册是到多播器中，并非注册到容器的意思。
	 */
	protected void registerListeners() {
		// Register statically specified listeners first.
		/**
		 * 注：首先将静态指定的监听器注册到多播器中。
		 * 什么叫静态呢？我觉得可以理解为多播器实例化之前向应用上下文添加的应用监听器就都缓存在applicationListeners集合中。
		 * 多播器实例化之后，向应用上下文添加的监听器直接注册到了多播器了。
		 */
		for (ApplicationListener<?> listener : getApplicationListeners()) {
			getApplicationEventMulticaster().addApplicationListener(listener);
		}

		// Do not initialize FactoryBeans here: We need to leave all regular beans
		// uninitialized to let post-processors apply to them!
		/**
		 * 注：这里获取Bean容器中所有的应用事件beanName，并将beanName添加到多播器中。
		 * 1. 为什么这里不是获取bean实例而是beanName
		 *  实际上，容器内注册所有监听器bean实例注入多播器都是由ApplicationListenerDetector这个后置处理器实例来完成的。
		 *  如果这里实例化bean并且还执行添加到多播器中，那可能会导致一个事件被多个相同类型的监听器消费，不符合预期。
		 * 2. 这里向多播器添加beanName而不是bean，会不会导致事件无法被beanName对应的监听器消费？
		 *   注入到多播器的beanName和bean分别由两个不同的set缓存起来，在发布事件时，对于beanName就会调用bean容器对应方法进行初始化。
		 */
		String[] listenerBeanNames = getBeanNamesForType(ApplicationListener.class, true, false);
		for (String listenerBeanName : listenerBeanNames) {
			getApplicationEventMulticaster().addApplicationListenerBean(listenerBeanName);
		}

		// Publish early application events now that we finally have a multicaster...
		// 注：在多播器尚未实例化之前，应用上下文发布的事件会暂存在earlyApplicationEvents缓存中，那么此处就会将由多播器传播这些事件
		Set<ApplicationEvent> earlyEventsToProcess = this.earlyApplicationEvents;
		this.earlyApplicationEvents = null;		// 注：这是一个多播器是否已实例化的表示，null意味着后续的事件直接交给多播器
		if (!CollectionUtils.isEmpty(earlyEventsToProcess)) {
			for (ApplicationEvent earlyEvent : earlyEventsToProcess) {
				getApplicationEventMulticaster().multicastEvent(earlyEvent);
			}
		}
	}

	/**
	 * Finish the initialization of this context's bean factory,
	 * initializing all remaining singleton beans.
	 * 注：完成上下文内部bean工厂(容器)的初始化工作；最后初始化所有剩余的单例bean
	 */
	protected void finishBeanFactoryInitialization(ConfigurableListableBeanFactory beanFactory) {
		// Initialize conversion service for this context.
		// 注：初始化用于类型转换的bean-conversionService
		if (beanFactory.containsBean(CONVERSION_SERVICE_BEAN_NAME) &&
				beanFactory.isTypeMatch(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class)) {
			beanFactory.setConversionService(
					beanFactory.getBean(CONVERSION_SERVICE_BEAN_NAME, ConversionService.class));
		}

		// Register a default embedded value resolver if no bean post-processor
		// (such as a PropertyPlaceholderConfigurer bean) registered any before:
		// at this point, primarily for resolution in annotation attribute values.
		// 注：如果之前bean工厂后置处理器没有注册嵌入值解析器的话，这里就设置一个默认的解析器-主要用于解析注解值。
		// 默认解析器是StringValueResolver类型，这是一个函数式接口，具体由环境对象来解析给定的值。
		if (!beanFactory.hasEmbeddedValueResolver()) {
			beanFactory.addEmbeddedValueResolver(strVal -> getEnvironment().resolvePlaceholders(strVal));
		}

		// Initialize LoadTimeWeaverAware beans early to allow for registering their transformers early.
		// 注：初始化容器中所有的LoadTimeWeaverAware类型的bean实力，完成织入功能
		String[] weaverAwareNames = beanFactory.getBeanNamesForType(LoadTimeWeaverAware.class, false, false);
		for (String weaverAwareName : weaverAwareNames) {
			getBean(weaverAwareName);
		}

		// Stop using the temporary ClassLoader for type matching.
		// 注：加载时织入已经完成，将其所需要的临时类加载器置空
		beanFactory.setTempClassLoader(null);

		// Allow for caching all bean definition metadata, not expecting further changes.
		// 注：在初始化正常bean之前，设置bean工厂(容器)的冻结状态，所有bean定义不会被进一步修改
		beanFactory.freezeConfiguration();

		// Instantiate all remaining (non-lazy-init) singletons.
		// 注：初始化bean工厂(容器)中所有剩余非懒加载的单例bean
		beanFactory.preInstantiateSingletons();
	}

	/**
	 * Finish the refresh of this context, invoking the LifecycleProcessor's
	 * onRefresh() method and publishing the
	 * {@link org.springframework.context.event.ContextRefreshedEvent}.
	 * 注：完成当前上下文的刷新，调用生命周期处理器(LifecycleProcessor)的onRefresh方法并且发布容器已刷新事件。
	 */
	protected void finishRefresh() {
		// Clear context-level resource caches (such as ASM metadata from scanning).
		// 注：清理容器级别的资源缓存
		clearResourceCaches();

		// Initialize lifecycle processor for this context.
		// 注：为上下文初始化生命周期处理器
		initLifecycleProcessor();

		// Propagate refresh to lifecycle processor first.
		// 注：刷新所有实现了 Lifecycle 接口的 Bean
		getLifecycleProcessor().onRefresh();

		// Publish the final event.
		// 注：发布 ContextRefreshEvent 事件告知容器已完成刷新
		publishEvent(new ContextRefreshedEvent(this));

		// Participate in LiveBeansView MBean, if active.
		if (!IN_NATIVE_IMAGE) {
			// 注：如果配置了MBeanServer，就完成在MBeanServer上的注册
			LiveBeansView.registerApplicationContext(this);
		}
	}

	/**
	 * Cancel this context's refresh attempt, resetting the {@code active} flag
	 * after an exception got thrown.
	 * @param ex the exception that led to the cancellation
	 */
	protected void cancelRefresh(BeansException ex) {
		this.active.set(false);
	}

	/**
	 * Reset Spring's common reflection metadata caches, in particular the
	 * {@link ReflectionUtils}, {@link AnnotationUtils}, {@link ResolvableType}
	 * and {@link CachedIntrospectionResults} caches.
	 * @since 4.2
	 * @see ReflectionUtils#clearCache()
	 * @see AnnotationUtils#clearCache()
	 * @see ResolvableType#clearCache()
	 * @see CachedIntrospectionResults#clearClassLoader(ClassLoader)
	 */
	protected void resetCommonCaches() {
		// 注：清空反射缓存
		ReflectionUtils.clearCache();
		// 注：清空注解缓存
		AnnotationUtils.clearCache();
		// 注：清空并发缓存
		ResolvableType.clearCache();
		// 注：清空类加载器
		CachedIntrospectionResults.clearClassLoader(getClassLoader());
	}


	/**
	 * Register a shutdown hook {@linkplain Thread#getName() named}
	 * {@code SpringContextShutdownHook} with the JVM runtime, closing this
	 * context on JVM shutdown unless it has already been closed at that time.
	 * <p>Delegates to {@code doClose()} for the actual closing procedure.
	 * @see Runtime#addShutdownHook
	 * @see ConfigurableApplicationContext#SHUTDOWN_HOOK_THREAD_NAME
	 * @see #close()
	 * @see #doClose()
	 */
	@Override
	public void registerShutdownHook() {
		if (this.shutdownHook == null) {
			// No shutdown hook registered yet.
			this.shutdownHook = new Thread(SHUTDOWN_HOOK_THREAD_NAME) {
				@Override
				public void run() {
					synchronized (startupShutdownMonitor) {
						doClose();
					}
				}
			};
			Runtime.getRuntime().addShutdownHook(this.shutdownHook);
		}
	}

	/**
	 * Callback for destruction of this instance, originally attached
	 * to a {@code DisposableBean} implementation (not anymore in 5.0).
	 * <p>The {@link #close()} method is the native way to shut down
	 * an ApplicationContext, which this method simply delegates to.
	 * @deprecated as of Spring Framework 5.0, in favor of {@link #close()}
	 */
	@Deprecated
	public void destroy() {
		close();
	}

	/**
	 * Close this application context, destroying all beans in its bean factory.
	 * <p>Delegates to {@code doClose()} for the actual closing procedure.
	 * Also removes a JVM shutdown hook, if registered, as it's not needed anymore.
	 * @see #doClose()
	 * @see #registerShutdownHook()
	 */
	@Override
	public void close() {
		synchronized (this.startupShutdownMonitor) {
			doClose();
			// If we registered a JVM shutdown hook, we don't need it anymore now:
			// We've already explicitly closed the context.
			if (this.shutdownHook != null) {
				try {
					Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
				}
				catch (IllegalStateException ex) {
					// ignore - VM is already shutting down
				}
			}
		}
	}

	/**
	 * Actually performs context closing: publishes a ContextClosedEvent and
	 * destroys the singletons in the bean factory of this application context.
	 * <p>Called by both {@code close()} and a JVM shutdown hook, if any.
	 * @see org.springframework.context.event.ContextClosedEvent
	 * @see #destroyBeans()
	 * @see #close()
	 * @see #registerShutdownHook()
	 */
	protected void doClose() {
		// Check whether an actual close attempt is necessary...
		if (this.active.get() && this.closed.compareAndSet(false, true)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Closing " + this);
			}

			if (!IN_NATIVE_IMAGE) {
				LiveBeansView.unregisterApplicationContext(this);
			}

			try {
				// Publish shutdown event.
				publishEvent(new ContextClosedEvent(this));
			}
			catch (Throwable ex) {
				logger.warn("Exception thrown from ApplicationListener handling ContextClosedEvent", ex);
			}

			// Stop all Lifecycle beans, to avoid delays during individual destruction.
			if (this.lifecycleProcessor != null) {
				try {
					this.lifecycleProcessor.onClose();
				}
				catch (Throwable ex) {
					logger.warn("Exception thrown from LifecycleProcessor on context close", ex);
				}
			}

			// Destroy all cached singletons in the context's BeanFactory.
			destroyBeans();

			// Close the state of this context itself.
			closeBeanFactory();

			// Let subclasses do some final clean-up if they wish...
			onClose();

			// Reset local application listeners to pre-refresh state.
			if (this.earlyApplicationListeners != null) {
				this.applicationListeners.clear();
				this.applicationListeners.addAll(this.earlyApplicationListeners);
			}

			// Switch to inactive.
			this.active.set(false);
		}
	}

	/**
	 * Template method for destroying all beans that this context manages.
	 * The default implementation destroy all cached singletons in this context,
	 * invoking {@code DisposableBean.destroy()} and/or the specified
	 * "destroy-method".
	 * <p>Can be overridden to add context-specific bean destruction steps
	 * right before or right after standard singleton destruction,
	 * while the context's BeanFactory is still active.
	 * @see #getBeanFactory()
	 * @see org.springframework.beans.factory.config.ConfigurableBeanFactory#destroySingletons()
	 */
	protected void destroyBeans() {
		getBeanFactory().destroySingletons();
	}

	/**
	 * Template method which can be overridden to add context-specific shutdown work.
	 * The default implementation is empty.
	 * <p>Called at the end of {@link #doClose}'s shutdown procedure, after
	 * this context's BeanFactory has been closed. If custom shutdown logic
	 * needs to execute while the BeanFactory is still active, override
	 * the {@link #destroyBeans()} method instead.
	 */
	protected void onClose() {
		// For subclasses: do nothing by default.
	}

	@Override
	public boolean isActive() {
		return this.active.get();
	}

	/**
	 * Assert that this context's BeanFactory is currently active,
	 * throwing an {@link IllegalStateException} if it isn't.
	 * <p>Invoked by all {@link BeanFactory} delegation methods that depend
	 * on an active context, i.e. in particular all bean accessor methods.
	 * <p>The default implementation checks the {@link #isActive() 'active'} status
	 * of this context overall. May be overridden for more specific checks, or for a
	 * no-op if {@link #getBeanFactory()} itself throws an exception in such a case.
	 */
	protected void assertBeanFactoryActive() {
		if (!this.active.get()) {
			if (this.closed.get()) {
				throw new IllegalStateException(getDisplayName() + " has been closed already");
			}
			else {
				throw new IllegalStateException(getDisplayName() + " has not been refreshed yet");
			}
		}
	}


	//---------------------------------------------------------------------
	// Implementation of BeanFactory interface
	//---------------------------------------------------------------------

	@Override
	public Object getBean(String name) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(name);
	}

	@Override
	public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(name, requiredType);
	}

	@Override
	public Object getBean(String name, Object... args) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(name, args);
	}

	@Override
	public <T> T getBean(Class<T> requiredType) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(requiredType);
	}

	@Override
	public <T> T getBean(Class<T> requiredType, Object... args) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBean(requiredType, args);
	}

	@Override
	public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanProvider(requiredType);
	}

	@Override
	public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanProvider(requiredType);
	}

	@Override
	public boolean containsBean(String name) {
		return getBeanFactory().containsBean(name);
	}

	@Override
	public boolean isSingleton(String name) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().isSingleton(name);
	}

	@Override
	public boolean isPrototype(String name) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().isPrototype(name);
	}

	@Override
	public boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().isTypeMatch(name, typeToMatch);
	}

	@Override
	public boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().isTypeMatch(name, typeToMatch);
	}

	@Override
	@Nullable
	public Class<?> getType(String name) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().getType(name);
	}

	@Override
	@Nullable
	public Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
		assertBeanFactoryActive();
		return getBeanFactory().getType(name, allowFactoryBeanInit);
	}

	@Override
	public String[] getAliases(String name) {
		return getBeanFactory().getAliases(name);
	}


	//---------------------------------------------------------------------
	// Implementation of ListableBeanFactory interface
	//---------------------------------------------------------------------

	@Override
	public boolean containsBeanDefinition(String beanName) {
		return getBeanFactory().containsBeanDefinition(beanName);
	}

	@Override
	public int getBeanDefinitionCount() {
		return getBeanFactory().getBeanDefinitionCount();
	}

	@Override
	public String[] getBeanDefinitionNames() {
		return getBeanFactory().getBeanDefinitionNames();
	}

	@Override
	public String[] getBeanNamesForType(ResolvableType type) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForType(type);
	}

	@Override
	public String[] getBeanNamesForType(ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForType(type, includeNonSingletons, allowEagerInit);
	}

	@Override
	public String[] getBeanNamesForType(@Nullable Class<?> type) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForType(type);
	}

	@Override
	public String[] getBeanNamesForType(@Nullable Class<?> type, boolean includeNonSingletons, boolean allowEagerInit) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForType(type, includeNonSingletons, allowEagerInit);
	}

	@Override
	public <T> Map<String, T> getBeansOfType(@Nullable Class<T> type) throws BeansException {
		assertBeanFactoryActive();
		return getBeanFactory().getBeansOfType(type);
	}

	@Override
	public <T> Map<String, T> getBeansOfType(@Nullable Class<T> type, boolean includeNonSingletons, boolean allowEagerInit)
			throws BeansException {

		assertBeanFactoryActive();
		return getBeanFactory().getBeansOfType(type, includeNonSingletons, allowEagerInit);
	}

	@Override
	public String[] getBeanNamesForAnnotation(Class<? extends Annotation> annotationType) {
		assertBeanFactoryActive();
		return getBeanFactory().getBeanNamesForAnnotation(annotationType);
	}

	@Override
	public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType)
			throws BeansException {

		assertBeanFactoryActive();
		return getBeanFactory().getBeansWithAnnotation(annotationType);
	}

	@Override
	@Nullable
	public <A extends Annotation> A findAnnotationOnBean(String beanName, Class<A> annotationType)
			throws NoSuchBeanDefinitionException {

		assertBeanFactoryActive();
		return getBeanFactory().findAnnotationOnBean(beanName, annotationType);
	}


	//---------------------------------------------------------------------
	// Implementation of HierarchicalBeanFactory interface
	//---------------------------------------------------------------------

	@Override
	@Nullable
	public BeanFactory getParentBeanFactory() {
		return getParent();
	}

	@Override
	public boolean containsLocalBean(String name) {
		return getBeanFactory().containsLocalBean(name);
	}

	/**
	 * Return the internal bean factory of the parent context if it implements
	 * ConfigurableApplicationContext; else, return the parent context itself.
	 * @see org.springframework.context.ConfigurableApplicationContext#getBeanFactory
	 */
	@Nullable
	protected BeanFactory getInternalParentBeanFactory() {
		return (getParent() instanceof ConfigurableApplicationContext ?
				((ConfigurableApplicationContext) getParent()).getBeanFactory() : getParent());
	}


	//---------------------------------------------------------------------
	// Implementation of MessageSource interface
	//---------------------------------------------------------------------

	@Override
	public String getMessage(String code, @Nullable Object[] args, @Nullable String defaultMessage, Locale locale) {
		return getMessageSource().getMessage(code, args, defaultMessage, locale);
	}

	@Override
	public String getMessage(String code, @Nullable Object[] args, Locale locale) throws NoSuchMessageException {
		return getMessageSource().getMessage(code, args, locale);
	}

	@Override
	public String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException {
		return getMessageSource().getMessage(resolvable, locale);
	}

	/**
	 * Return the internal MessageSource used by the context.
	 * @return the internal MessageSource (never {@code null})
	 * @throws IllegalStateException if the context has not been initialized yet
	 */
	private MessageSource getMessageSource() throws IllegalStateException {
		if (this.messageSource == null) {
			throw new IllegalStateException("MessageSource not initialized - " +
					"call 'refresh' before accessing messages via the context: " + this);
		}
		return this.messageSource;
	}

	/**
	 * Return the internal message source of the parent context if it is an
	 * AbstractApplicationContext too; else, return the parent context itself.
	 * 注：返回当前应用的父上下文内部的消息源。
	 * 当然前提是父上下文也是AbstractApplicationContext类型，否则返回父上下文本身。
	 */
	@Nullable
	protected MessageSource getInternalParentMessageSource() {
		return (getParent() instanceof AbstractApplicationContext ?
				((AbstractApplicationContext) getParent()).messageSource : getParent());
	}


	//---------------------------------------------------------------------
	// Implementation of ResourcePatternResolver interface
	//---------------------------------------------------------------------

	@Override
	public Resource[] getResources(String locationPattern) throws IOException {
		return this.resourcePatternResolver.getResources(locationPattern);
	}


	//---------------------------------------------------------------------
	// Implementation of Lifecycle interface
	//---------------------------------------------------------------------

	@Override
	public void start() {
		getLifecycleProcessor().start();
		publishEvent(new ContextStartedEvent(this));
	}

	@Override
	public void stop() {
		getLifecycleProcessor().stop();
		publishEvent(new ContextStoppedEvent(this));
	}

	@Override
	public boolean isRunning() {
		return (this.lifecycleProcessor != null && this.lifecycleProcessor.isRunning());
	}


	//---------------------------------------------------------------------
	// Abstract methods that must be implemented by subclasses
	//---------------------------------------------------------------------

	/**
	 * Subclasses must implement this method to perform the actual configuration load.
	 * The method is invoked by {@link #refresh()} before any other initialization work.
	 * <p>A subclass will either create a new bean factory and hold a reference to it,
	 * or return a single BeanFactory instance that it holds. In the latter case, it will
	 * usually throw an IllegalStateException if refreshing the context more than once.
	 * 注：由子类给出具体Bean工厂(容器)刷新的具体实现。这个方法会在上下文刷新工作之间完成容器的刷新过程。
	 * 具体上下文子类要么创建一个新的Bean工厂并持有其引用，或者返回一个已经存在的Bean工厂。在后面一种情况下，
	 * 如果多次调用该方法会抛出IllegalStateException异常。
	 * @throws BeansException if initialization of the bean factory failed
	 * @throws IllegalStateException if already initialized and multiple refresh
	 * attempts are not supported
	 */
	protected abstract void refreshBeanFactory() throws BeansException, IllegalStateException;

	/**
	 * Subclasses must implement this method to release their internal bean factory.
	 * This method gets invoked by {@link #close()} after all other shutdown work.
	 * <p>Should never throw an exception but rather log shutdown failures.
	 */
	protected abstract void closeBeanFactory();

	/**
	 * Subclasses must return their internal bean factory here. They should implement the
	 * lookup efficiently, so that it can be called repeatedly without a performance penalty.
	 * 注：应用上下文具体子类必须通过当前方法返回内部的Bean工厂。
	 * 该方法返回Bean工厂的过程应该具有很高的效率，以便可以多次调用而不用考虑性能问题。
	 * <p>Note: Subclasses should check whether the context is still active before
	 * returning the internal bean factory. The internal factory should generally be
	 * considered unavailable once the context has been closed.
	 * 注：应用上下文具体类在返回内部Bean工厂之前应须检查应用上下文实例是否处于激活状态。
	 * 如果应用上下文已经被关闭了那就不应该再返回了。
	 * @return this application context's internal bean factory (never {@code null})
	 * @throws IllegalStateException if the context does not hold an internal bean factory yet
	 * (usually if {@link #refresh()} has never been called) or if the context has been
	 * closed already
	 * @see #refreshBeanFactory()
	 * @see #closeBeanFactory()
	 */
	@Override
	public abstract ConfigurableListableBeanFactory getBeanFactory() throws IllegalStateException;


	/**
	 * Return information about this context.
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(getDisplayName());
		sb.append(", started on ").append(new Date(getStartupDate()));
		ApplicationContext parent = getParent();
		if (parent != null) {
			sb.append(", parent: ").append(parent.getDisplayName());
		}
		return sb.toString();
	}

}
