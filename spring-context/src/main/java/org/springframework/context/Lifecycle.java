/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.context;

/**
 * A common interface defining methods for start/stop lifecycle control.
 * The typical use case for this is to control asynchronous processing.
 * <b>NOTE: This interface does not imply specific auto-startup semantics.
 * Consider implementing {@link SmartLifecycle} for that purpose.</b>
 * 注：Lifecycle是一个通用于开启|停止控制的接口。其典型的应用就是控制异步流程。
 * 注意：这个接口并不能提供自动开启的语义，如果想实现自动开启的功能，可实现SmartLifecycle接口。
 *
 * <p>Can be implemented by both components (typically a Spring bean defined in a
 * Spring context) and containers  (typically a Spring {@link ApplicationContext}
 * itself). Containers will propagate start/stop signals to all components that
 * apply within each container, e.g. for a stop/restart scenario at runtime.
 * 注：该接口可以被组件(如spring bean实例)或容器(比如spring上下文)实现。
 * 其中容器会将开启|停止信号传递给每一个在容器中的组件。
 *
 * <p>Can be used for direct invocations or for management operations via JMX.
 * In the latter case, the {@link org.springframework.jmx.export.MBeanExporter}
 * will typically be defined with an
 * {@link org.springframework.jmx.export.assembler.InterfaceBasedMBeanInfoAssembler},
 * restricting the visibility of activity-controlled components to the Lifecycle
 * interface.
 * 注：该接口的方法可以直接调用或者通过JMX进行管理操作。
 *
 * <p>Note that the present {@code Lifecycle} interface is only supported on
 * <b>top-level singleton beans</b>. On any other component, the {@code Lifecycle}
 * interface will remain undetected and hence ignored. Also, note that the extended
 * {@link SmartLifecycle} interface provides sophisticated integration with the
 * application context's startup and shutdown phases.
 * 注：当前Lifecycle接口仅仅提供了上级单例bean的启停功能。
 *
 * @author Juergen Hoeller
 * @since 2.0
 * @see SmartLifecycle
 * @see ConfigurableApplicationContext
 * @see org.springframework.jms.listener.AbstractMessageListenerContainer
 * @see org.springframework.scheduling.quartz.SchedulerFactoryBean
 */
public interface Lifecycle {

	/**
	 * Start this component.
	 * <p>Should not throw an exception if the component is already running.
	 * <p>In the case of a container, this will propagate the start signal to all
	 * components that apply.
	 * 启动一个组件；
	 * - 当组件已经运行起来时，调用当前方法不用抛出异常
	 * - 如果启动一个容器，容器内所有的组件都会执行启动
	 * @see SmartLifecycle#isAutoStartup()
	 */
	void start();

	/**
	 * Stop this component, typically in a synchronous fashion, such that the component is
	 * fully stopped upon return of this method. Consider implementing {@link SmartLifecycle}
	 * and its {@code stop(Runnable)} variant when asynchronous stop behavior is necessary.
	 * 注：停止容器一般都是同步操作，这意味着组件应该完全关闭后结束stop方法。如果想要实现异步关闭考虑实现SmartLifecycle接口。
	 * <p>Note that this stop notification is not guaranteed to come before destruction:
	 * On regular shutdown, {@code Lifecycle} beans will first receive a stop notification
	 * before the general destruction callbacks are being propagated; however, on hot
	 * refresh during a context's lifetime or on aborted refresh attempts, a given bean's
	 * destroy method will be called without any consideration of stop signals upfront.
	 * 注：注意这个停止方法不保证在destruction方法之前执行。
	 * 在正常启动场景下，实现了Lifecycle的bean会在destruct调用之前执行stop调用。
	 * 然而，在容器热部署或停止刷新时，是会直接调用bean的destruct方法而不会考虑调用其stop。
	 * <p>Should not throw an exception if the component is not running (not started yet).
	 * <p>In the case of a container, this will propagate the stop signal to all components
	 * that apply.
	 * 注：- 如果当前容器尚未启动而调用stop方法，不需要抛出异常。
	 * 	- 在容器场景下，stop调用会传递到容器中每一个组件内。
	 * @see SmartLifecycle#stop(Runnable)
	 * @see org.springframework.beans.factory.DisposableBean#destroy()
	 */
	void stop();

	/**
	 * Check whether this component is currently running.
	 * 注：检查当前组件是否正在运行
	 * <p>In the case of a container, this will return {@code true} only if <i>all</i>
	 * components that apply are currently running.
	 * 注：在容器场景下，必须所有涉及组件都在运行时，容器才算为正在运行状态。
	 * @return whether the component is currently running
	 */
	boolean isRunning();

}
