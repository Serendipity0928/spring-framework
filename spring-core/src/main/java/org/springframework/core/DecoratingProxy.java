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

package org.springframework.core;

/**
 * Interface to be implemented by decorating proxies, in particular Spring AOP
 * proxies but potentially also custom proxies with decorator semantics.
 * 该接口会被通过装饰代理实现，尤其在SpringAOP代理，但是也有可能是具备装饰器语义的自定义代理。
 *
 * <p>Note that this interface should just be implemented if the decorated class
 * is not within the hierarchy of the proxy class to begin with. In particular,
 * a "target-class" proxy such as a Spring AOP CGLIB proxy should not implement
 * it since any lookup on the target class can simply be performed on the proxy
 * class there anyway.
 * 注：如果装饰类一开始就不在代理类的层次结构中，那么才应该实现这个接口。
 * 像SpringAOP CGLIB代理之类的目标类代理(继承方式)，就不应该实现该接口，因为对目标类的任何方法都可以在代理类上执行。
 *
 * <p>Defined in the core module in order to allow
 * {@link org.springframework.core.annotation.AnnotationAwareOrderComparator}
 * (and potential other candidates without spring-aop dependencies) to use it
 * for introspection purposes, in particular annotation lookups.
 * 注：该接口定义在核心模块。
 * 这是为了允许AnnotationAwareOrderComparator(以及其他没有springAop依赖关系的潜在候选者)用于其查找自身信息的目的，比如注解信息。
 *
 * @author Juergen Hoeller
 * @since 4.3
 */
public interface DecoratingProxy {

	/**
	 * Return the (ultimate) decorated class behind this proxy.
	 * <p>In case of an AOP proxy, this will be the ultimate target class,
	 * not just the immediate target (in case of multiple nested proxies).
	 * 注：返回当前代理最终被装饰的的类型
	 * - 在AOP代理场景，这个方法会返回最终目标类型，不仅仅是最近目标类型。(有可能存在多种嵌套代理)
	 * @return the decorated class (never {@code null})
	 */
	Class<?> getDecoratedClass();

}
