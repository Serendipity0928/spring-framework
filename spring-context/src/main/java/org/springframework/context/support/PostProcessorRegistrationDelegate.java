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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.lang.Nullable;

/**
 * Delegate for AbstractApplicationContext's post-processor handling.
 * 注：封装了抽象应用上下文对于后置处理器的处理。【1. 调用Bean工厂的后置处理器；2. 注册Bean后置处理器】
 * 参考：https://www.cnblogs.com/kjcc/p/13895903.html、https://www.jianshu.com/p/91a1d785f6a9
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 4.0
 */
final class PostProcessorRegistrationDelegate {

	// 注：作为静态工具类使用
	private PostProcessorRegistrationDelegate() {
	}


	/**
	 * 注：调用应用上下文中Bean工厂后置处理器
	 * 注意spring应用上下文对于Bean定义的处理分为2种：① Bean定义属性的修改；② Bean定义的新增
	 * 前者就是Bean工厂后置处理器的功能，后者是Bean工厂后置处理器的扩展接口-Bean定义注册后置处理器
	 * @param beanFactory Bean工厂实例
	 * @param beanFactoryPostProcessors 应用上下文中手动注册的Bean工厂后置处理器
	 */
	public static void invokeBeanFactoryPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {
		// TODO: 2023/10/15 https://www.jianshu.com/p/91a1d785f6a9
		// Invoke BeanDefinitionRegistryPostProcessors first, if any.
		// 注: 如果有Bean定义注册后置处理器就先执行。（这是因为这个处理器可能会新增Bean工厂后置处理器定义）

		// 注：用于存储已调用的Bean工厂后置处理器实例Bean名，以免重复调用
		Set<String> processedBeans = new HashSet<>();

		/**
		 * 注：这里需要根据Bean工厂是否为支持手动注册Bean定义的工厂类型；
		 * 如果是的话，就需要考虑BeanDefinitionRegistryPostProcessor实例可能会新增Bean定义的能力。
		 * 反之，就只需要执行Bean工厂后置处理器即可。
		 */
		if (beanFactory instanceof BeanDefinitionRegistry) {
			BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
			List<BeanFactoryPostProcessor> regularPostProcessors = new ArrayList<>();			// 用于存储常规Bean工厂后置处理器集合
			List<BeanDefinitionRegistryPostProcessor> registryProcessors = new ArrayList<>(); 	// 用于存储Bean定义注册后置处理器集合
			// TODO: 2023/10/15 思考：这两个集合不能合并为一个集合吗？List<BeanFactoryPostProcessor>不就是为了最后调用Bean工厂后置处理器方法？

			/**
			 * 注：for循环遍历手动注入应用上下文的Bean工厂后置处理器集合。
			 * 如果集合内存在Bean定义注册后置处理器，就执行对应的方法。
			 */
			for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
				if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
					BeanDefinitionRegistryPostProcessor registryProcessor =
							(BeanDefinitionRegistryPostProcessor) postProcessor;
					registryProcessor.postProcessBeanDefinitionRegistry(registry);
					registryProcessors.add(registryProcessor);
				}
				else {
					regularPostProcessors.add(postProcessor);
				}
			}

			// Do not initialize FactoryBeans here: We need to leave all regular beans
			// uninitialized to let the bean factory post-processors apply to them!
			// Separate between BeanDefinitionRegistryPostProcessors that implement
			// PriorityOrdered, Ordered, and the rest.
			/**
			 * 注：下面将处理Bean工厂内部注册的Bean定义注册后置处理器。
			 * 处理的顺序按照继承PriorityOrdered接口、继承Ordered接口、其他的三个部分顺序处理（当然每部分内部会根据order从小到大顺序处理）。
			 */
			// 注：每部分都会从Bean工厂(容器)中获取BeanDefinitionRegistryPostProcessor类型的Bean，并会按照一定规则过滤，满足条件的Bean置入当前集合并处理
			List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();

			// First, invoke the BeanDefinitionRegistryPostProcessors that implement PriorityOrdered.
			// 注：第一部分，优先调用实现了PriorityOrdered接口的Bean定义注册后置处理器
			// 注：通过bean工厂提供的getBeanNamesForType()根据指定类型获取bean名获取满足条件的beanName列表
			String[] postProcessorNames =
					beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			for (String ppName : postProcessorNames) {
				if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {	// 过滤实现了PriorityOrdered接口的bean
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					processedBeans.add(ppName);	// 记录已处理的beanName
				}
			}
			sortPostProcessors(currentRegistryProcessors, beanFactory);	// 按照一定的顺序排序，要么根据bean工厂执行顺序，要么默认顺序OrderComparator
			registryProcessors.addAll(currentRegistryProcessors);		// 所处理的bean后置处理器都要添加到这个集合中，后续还要作为Bean工厂后置处理器调用指定方法
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);	// 处理Bean定义后置处理器
			currentRegistryProcessors.clear();		// Bean定义后置处理器已调用，清空集合，便于后续使用

			// Next, invoke the BeanDefinitionRegistryPostProcessors that implement Ordered.
			// 注：第二部分，调用实现了Ordered接口的Bean定义注册后置处理器
			// 注：为什么这里又重新获取了一遍？因为上述处理Bean定义注册后置处理器过程可能又产生了新的Bean定义注册后置处理器！
			postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			for (String ppName : postProcessorNames) {
				if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {	// 未处理的bean，且实现了Ordered接口
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					processedBeans.add(ppName);		// 以下一系列均与上类似。
				}
			}
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			registryProcessors.addAll(currentRegistryProcessors);
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
			currentRegistryProcessors.clear();

			// Finally, invoke all other BeanDefinitionRegistryPostProcessors until no further ones appear.
			// 注：最后，也就是第三部分，调用所有其他的BeanDefinitionRegistryPostProcessors。
			// 注：注意到，下面是循环查找，知道查找不到位置。为什么这么做？原因也是”Bean定义注册后置处理器过程可能又产生了新的Bean定义注册后置处理器“
			boolean reiterate = true;
			while (reiterate) {
				reiterate = false;
				postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
				for (String ppName : postProcessorNames) {
					if (!processedBeans.contains(ppName)) {
						currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
						processedBeans.add(ppName);
						reiterate = true;
					}
				}
				sortPostProcessors(currentRegistryProcessors, beanFactory);
				registryProcessors.addAll(currentRegistryProcessors);
				invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
				currentRegistryProcessors.clear();
			}

			// Now, invoke the postProcessBeanFactory callback of all processors handled so far.
			/**
			 * 这里调用“所有”Bean工厂后置处理器的postProcessBeanFactory回调方法。注意registryProcessors存储的也是后置处理器。
			 * 之所以使用双引号来标识“所有”，是因为还没有执行注册入Bean工厂的Bean工厂后置处理器。
			 * 这里“所有”指的是，通过手动注入应用上下文以及通过Bean定义注册后置处理器动态定义的Bean工厂后置处理器。
			 */
			invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);
			invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
		}

		else {
			// Invoke factory processors registered with the context instance.
			// 仅处理(手动注册入上下文中的)Bean工厂后置处理器
			invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
		}

		/**
		 * 为什么这个方法的逻辑分为上下两个部分？而且逻辑似乎类似呢？
		 * 因为Bean后置处理器的注入在应用上下文中有两种情况：一种是手动向应用上下文增加的，存储在list集合中；另外一种是存储在Bean工厂的Bean定义中。
		 * 前面一部分仅调用了通过入参传过来的手动增加的Bean工厂后置处理器。当然也顺便处理了BeanDefinitionRegistryPostProcessor相关内容。
		 * 下面一部分是需要自动侦测注册在Bean工厂内的Bean工厂后置处理器，并按照一定的规则顺序调用相应方法。
		 */

		// Do not initialize FactoryBeans here: We need to leave all regular beans
		// uninitialized to let the bean factory post-processors apply to them!
		// 注：获取Bean容器中所有BeanFactoryPostProcessor类型的beanName
		String[] postProcessorNames =
				beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

		// Separate between BeanFactoryPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		// 注：按照实现了PriorityOrdered、Ordered以及其他来顺序处理Bean工厂后置处理器。
		List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		List<String> orderedPostProcessorNames = new ArrayList<>();
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		for (String ppName : postProcessorNames) {
			if (processedBeans.contains(ppName)) {
				// 注：过滤上述已经处理bean定义的后置处理器，已经存储在regularPostProcessors或registryProcessors集合中
				// skip - already processed in first phase above
			}
			else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				// 注：继承了PriorityOrdered接口的bean存储在priorityOrderedPostProcessors集合中
				priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
			}
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				/**
				 * 注：为什么下面这两个就是个List<String>类型的BeanName集合，而不是Bean实例呢？
				 * “Do not initialize FactoryBeans here...”，getBean(..)方法会实例化Bean，
				 * 这样做的目的可以使得后置处理器操作范围更大，如priorityOrderedPostProcessors可以处理orderedPostProcessorNames内部的bean定义。
 				 */
				orderedPostProcessorNames.add(ppName);
			}
			else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, invoke the BeanFactoryPostProcessors that implement PriorityOrdered.
		// 注：第一部分，首先按照排序后的顺序调用实现了PriorityOrdered接口的Bean工厂后置处理器
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

		// Next, invoke the BeanFactoryPostProcessors that implement Ordered.
		// 注：第二部分，按照排序后的顺序调用实现了Ordered接口的Bean工厂后置处理器
		List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
		for (String postProcessorName : orderedPostProcessorNames) {
			orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		sortPostProcessors(orderedPostProcessors, beanFactory);
		invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

		// Finally, invoke all other BeanFactoryPostProcessors.
		// 注：最后调用所有其他的Bean工厂后置处理器
		List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
		for (String postProcessorName : nonOrderedPostProcessorNames) {
			nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		// 注：注意这里没有再调用排序了，即仅考虑PriorityOrdered、Ordered这两类接口的排序结果。
		invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

		// Clear cached merged bean definitions since the post-processors might have
		// modified the original metadata, e.g. replacing placeholders in values...
		// 注：清楚合并bean定义的缓存。具体作用后续再研究：https://blog.csdn.net/zzzzzyyyz/article/details/117049402
		beanFactory.clearMetadataCache();
	}

	/**
	 * 注：应用上下文自动侦测Bean容器中的Bean后置处理器实例，并将这部分处理器实例化后添加入Bean后置处理器缓存集合中-即AbstractBeanFactory#beanPostProcessors。
	 * 使用beanFactory的addBeanPostProcessor等方法是直接手动添加入Bean后置处理器缓存集合中。
	 * 这里"register"的含义是指，已经将其bean定义注册容器中的bean, 现在是自动侦测并实例化，缓存在指定集合中。
	 * @param beanFactory：bean工厂
	 * @param applicationContext：当前应用上下文
	 */
	public static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {
		// 注：通过beanFactory的getBeanNamesForType方法获取BeanPostProcessor类型的Bean，实际上即使容器已注入的Bean后置处理器，但尚未被实例化
		String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);

		// Register BeanPostProcessorChecker that logs an info message when
		// a bean is created during BeanPostProcessor instantiation, i.e. when
		// a bean is not eligible for getting processed by all BeanPostProcessors.
		/**
		 * 注：这里注册一个BeanPostProcessorChecker后置处理器实例，相关作用见下面类注释即可。
		 * Bean后置处理检查器需要Bean容器中总共的Bean后置处理器个数，以便来判断是否所有Bean后置处理器全部初始化完毕状态。
		 * Bean后置处理器个数分为三个部分：
		 * 1. 当前Bean容器已经初始化的Bean后置处理器个数-也即缓存集合的数量
		 * 2. 1，是指Bean后置处理检查器本身
		 * 3. 容器中存在但尚未被初始化的后置处理器个数。
		 */
		int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;
		beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));

		// Separate between BeanPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		/**
		 * 注：下面将容器中尚未实例化的Bean后置处理器按照一定排序规则进行实例化，并添加仅缓存集合中。
		 * bean后置处理器的实例化分为三个部分，实现了PriorityOrdered接口、Ordered接口、以及其他
		 * 之所以分为三个部分顺序处理，这将影响各个bean后置处理器在缓存集合的顺序，进而影响处理bean实例的顺序。
		 * - 此外，internalPostProcessors用于存储spring内部的MergedBeanDefinitionPostProcessor特殊类型的后置处理器。
		 */
		List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		List<BeanPostProcessor> internalPostProcessors = new ArrayList<>();
		List<String> orderedPostProcessorNames = new ArrayList<>();
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		for (String ppName : postProcessorNames) {
			if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
				priorityOrderedPostProcessors.add(pp);
				if (pp instanceof MergedBeanDefinitionPostProcessor) {
					internalPostProcessors.add(pp);
				}
			}
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				/**
				 * 注：为什么后面两个集合只存储name，而不是存储bean实例呢？
				 * 因为后置处理器实例本身也是bean, bean的初始化过程会调用后置处理器，此处只存储name，
				 * 使得优先级较低的bean初始化时可以被优先级较高的后置处理器处理。
				 */
				orderedPostProcessorNames.add(ppName);
			}
			else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, register the BeanPostProcessors that implement PriorityOrdered.
		// 注：首先将实现了PriorityOrdered的bean后置处理器按照一定的顺序规则顺序注册入bean工厂的缓存集合中
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);

		// Next, register the BeanPostProcessors that implement Ordered.
		// 注：将实现了Ordered的bean后置处理器按照一定的顺序规则顺序注册入bean工厂的缓存集合中
		List<BeanPostProcessor> orderedPostProcessors = new ArrayList<>(orderedPostProcessorNames.size());
		for (String ppName : orderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			orderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		sortPostProcessors(orderedPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, orderedPostProcessors);

		// Now, register all regular BeanPostProcessors.
		// 注：将常规的所有后置处理器注册入bean工厂的缓存集合中
		List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<>(nonOrderedPostProcessorNames.size());
		for (String ppName : nonOrderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			nonOrderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);

		// Finally, re-register all internal BeanPostProcessors.
		/**
		 * 这里将内部特殊bean后置处理器置后！主要是MergedBeanDefinitionPostProcessor类型的内部后置处理器一般是用于处理内部注解相关，
		 * 并且在bean初始化前后可能需要触发回调的逻辑，这部分逻辑可能会依赖当前bean的一些其他数据。
		 * 比如，CommonAnnotationBeanPostProcessor实现了postProcessBeforeInitialization方法用于处理@PostConstruct注解。
		 * 因此，对于这种情况，spring这里的处理策略是优先让其他bean后置处理器执行完，然后再执行内部的特殊用途(注解功能实现等)的后置处理器。
		 * 可参考：https://zhuanlan.zhihu.com/p/367076177
		 */
		sortPostProcessors(internalPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, internalPostProcessors);

		// Re-register post-processor for detecting inner beans as ApplicationListeners,
		// moving it to the end of the processor chain (for picking up proxies etc).
		/**
		 * 注：重新将用于侦测应用事件监听器实例的Bean后置处理器重新注册，也是为了将该后置处理器放置到最后。
		 * 我们注意到ApplicationListenerDetector实现了MergedBeanDefinitionPostProcessor接口，所以放置在最后处理的原因和上面类似。
		 * 原因：ApplicationListenerDetector需要判断那些bean是监听器类型，通过spring的getBeanNamesForType判断是不行的，
		 * 因为有可能bean会被其他后置处理器包装，因此需要通过bean定义后置处理器根据Bean的真实类型来判断。这里由于其他MergedBeanDefinitionPostProcessor
		 * 后置处理器实例也可能会修改bean定义，所以ApplicationListenerDetector必须在所有后置处理器之后。
		 */
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));
	}

	// 注：针对后置处理器进行排序，排序优先根据bean工厂提供的排序器，默认为OrderComparator实例。
	private static void sortPostProcessors(List<?> postProcessors, ConfigurableListableBeanFactory beanFactory) {
		// Nothing to sort?
		if (postProcessors.size() <= 1) {
			return;
		}
		Comparator<Object> comparatorToUse = null;
		if (beanFactory instanceof DefaultListableBeanFactory) {
			comparatorToUse = ((DefaultListableBeanFactory) beanFactory).getDependencyComparator();
		}
		if (comparatorToUse == null) {
			comparatorToUse = OrderComparator.INSTANCE;
		}
		postProcessors.sort(comparatorToUse);
	}

	/**
	 * Invoke the given BeanDefinitionRegistryPostProcessor beans.
	 * 注：调用bean定义注册后置处理器对应的方法
	 */
	private static void invokeBeanDefinitionRegistryPostProcessors(
			Collection<? extends BeanDefinitionRegistryPostProcessor> postProcessors, BeanDefinitionRegistry registry) {

		for (BeanDefinitionRegistryPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessBeanDefinitionRegistry(registry);
		}
	}

	/**
	 * Invoke the given BeanFactoryPostProcessor beans.
	 * 注：调用bean工厂后置处理器对应的方法
	 */
	private static void invokeBeanFactoryPostProcessors(
			Collection<? extends BeanFactoryPostProcessor> postProcessors, ConfigurableListableBeanFactory beanFactory) {

		for (BeanFactoryPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessBeanFactory(beanFactory);
		}
	}

	/**
	 * Register the given BeanPostProcessor beans.
	 * 注：将自动侦测的Bean后置处理器实例添加(注册)到Bean工厂(容器)的缓存集合中，即AbstractBeanFactory#beanPostProcessors
	 * 这里"register"可能存在歧义，像是注册到容器中，实际已经是容器中的bean,现在是自动侦测并缓存在指定集合中。
	 */
	private static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanPostProcessor> postProcessors) {

		if (beanFactory instanceof AbstractBeanFactory) {
			// Bulk addition is more efficient against our CopyOnWriteArrayList there
			/**
			 * 注：支持批量添加Bean后置处理器的Bean工厂(容器)，则使用批量添加至Bean后置处理器缓存中
			 * Bean后置处理器缓存集合实际类型为CopyOnWriteArrayList，CopyOnWriteArrayList的批量添加操作效率更高
			 * (https://blog.csdn.net/Jakob_Hu/article/details/101473368)
			 */
			((AbstractBeanFactory) beanFactory).addBeanPostProcessors(postProcessors);
		}
		else {
			for (BeanPostProcessor postProcessor : postProcessors) {
				beanFactory.addBeanPostProcessor(postProcessor);
			}
		}
	}


	/**
	 * BeanPostProcessor that logs an info message when a bean is created during
	 * BeanPostProcessor instantiation, i.e. when a bean is not eligible for
	 * getting processed by all BeanPostProcessors.
	 * 注：由于Bean工厂后置处理器的作用声明周期应该在所有业务Bean的初始化之前，spring提供了BeanPostProcessorChecker工具类做了相应日志监控。
	 * BeanPostProcessorChecker也是一个Bean后置处理器。当一个bean初始化后，
	 * 该后置处理器就会检查是否存在Bean后置处理器还没有初始化完就初始化了业务Bean的情况，如果存在这种情况就打印日志，
	 * 因为这个业务Bean可能不会被所有Bean后置处理器处理。
	 * - 其实这种情况是可能存在的，比如用户自定义的后置处理器依赖容器中某个业务bean。
	 */
	private static final class BeanPostProcessorChecker implements BeanPostProcessor {

		private static final Log logger = LogFactory.getLog(BeanPostProcessorChecker.class);

		private final ConfigurableListableBeanFactory beanFactory;

		private final int beanPostProcessorTargetCount;

		public BeanPostProcessorChecker(ConfigurableListableBeanFactory beanFactory, int beanPostProcessorTargetCount) {
			this.beanFactory = beanFactory;
			// 注：传入bean容器所有的后置处理器个数，用于后续校验是否所有后置处理器均被初始化了。
			this.beanPostProcessorTargetCount = beanPostProcessorTargetCount;
		}

		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName) {
			return bean;
		}

		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName) {
			/**
			 * 注：当一个bean初始化后，需要校验以下几个条件，来判断是否存在可能无法被所有bean后置处理器处理的bean
			 * 1. 当前bean不是Bean后置处理器
			 * 2. 当前bean不是spring内部基础bean，也即是业务bean(外部注册入容器中)
			 * 3. 所有bean后置处理器仍未被初始化
			 * 当同时满足以上三个条件后，就需要打印info级别的log日志，通知spring使用者！
			 */
			if (!(bean instanceof BeanPostProcessor) && !isInfrastructureBean(beanName) &&
					this.beanFactory.getBeanPostProcessorCount() < this.beanPostProcessorTargetCount) {
				if (logger.isInfoEnabled()) {		// 打印info日志
					logger.info("Bean '" + beanName + "' of type [" + bean.getClass().getName() +
							"] is not eligible for getting processed by all BeanPostProcessors " +
							"(for example: not eligible for auto-proxying)");
				}
			}
			return bean;
		}

		// 注：判断当前bean是否为spring内部基础bean
		private boolean isInfrastructureBean(@Nullable String beanName) {
			if (beanName != null && this.beanFactory.containsBeanDefinition(beanName)) {
				BeanDefinition bd = this.beanFactory.getBeanDefinition(beanName);
				// 通过bean定义中的role标识-ROLE_INFRASTRUCTURE，判断是否为内部基础bean
				return (bd.getRole() == RootBeanDefinition.ROLE_INFRASTRUCTURE);
			}
			return false;
		}
	}

}
