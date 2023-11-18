/*
 * Copyright 2002-2011 the original author or authors.
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

package org.springframework.beans.factory.xml;

import org.w3c.dom.Element;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.lang.Nullable;

/**
 * Interface used by the {@link DefaultBeanDefinitionDocumentReader} to handle custom,
 * top-level (directly under {@code <beans/>}) tags.
 * 注：默认bean定义DOM读取器会使用自定义的BeanDefinitionParser实现类来解析<beans>下的表示bean定义的子标签。
 *
 * <p>Implementations are free to turn the metadata in the custom tag into as many
 * {@link BeanDefinition BeanDefinitions} as required.
 * 注：具体实现类可自行定义将xml中的自定义标签元数据信息转换为需要bean定义实例
 *
 * <p>The parser locates a {@link BeanDefinitionParser} from the associated
 * {@link NamespaceHandler} for the namespace in which the custom tag resides.
 * 注：自定义标签对应的解析器是通过该自定义标签存在的命名空间处理器中获取的。
 *
 * @author Rob Harrop
 * @since 2.0
 * @see NamespaceHandler
 * @see AbstractBeanDefinitionParser
 */
public interface BeanDefinitionParser {

	/**
	 * Parse the specified {@link Element} and register the resulting
	 * {@link BeanDefinition BeanDefinition(s)} with the
	 * {@link org.springframework.beans.factory.xml.ParserContext#getRegistry() BeanDefinitionRegistry}
	 * embedded in the supplied {@link ParserContext}.
	 * 注：解析指定的DOM节点标签表示的bean定义，并注入到解析上下文中的bean注册工厂中去
	 * <p>Implementations must return the primary {@link BeanDefinition} that results
	 * from the parse if they will ever be used in a nested fashion (for example as
	 * an inner tag in a {@code <property/>} tag). Implementations may return
	 * {@code null} if they will <strong>not</strong> be used in a nested fashion.
	 * 注：如果自定义标签希望嵌套含有值标签(比如<property>)的内部使用时，对应的命名空间实现类的parse方法应在解析阶段返回最初的bean定义(非BeanDefinitionHolder)。
	 * 如果自定义标签不会再嵌套场景中作为值来使用，该解析方法返回的bean定义可能是个null值。
	 * @param element the element that is to be parsed into one or more {@link BeanDefinition BeanDefinitions}
	 * @param parserContext the object encapsulating the current state of the parsing process;
	 * provides access to a {@link org.springframework.beans.factory.support.BeanDefinitionRegistry}
	 * @return the primary {@link BeanDefinition}
	 * 注：一般自定义bean解析器都不会直接实现此接口，除非开发者能够熟悉ParserContext的内容。
	 * 一般都是实现更简单方便的AbstractSingleBeanDefinitionParser抽象类的doParse方法。
	 */
	@Nullable
	BeanDefinition  parse(Element element, ParserContext parserContext);

}
