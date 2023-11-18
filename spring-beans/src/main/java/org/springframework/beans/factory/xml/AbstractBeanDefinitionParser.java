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

package org.springframework.beans.factory.xml;

import org.w3c.dom.Element;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

/**
 * Abstract {@link BeanDefinitionParser} implementation providing
 * a number of convenience methods and a
 * {@link AbstractBeanDefinitionParser#parseInternal template method}
 * that subclasses must override to provide the actual parsing logic.
 * 注：BeanDefinitionParser接口的抽象实现类，其提供了许多便捷的方法以及解析的模版方法。
 * 子类需实现实际解析逻辑的parseInternal方法。
 *
 * <p>Use this {@link BeanDefinitionParser} implementation when you want
 * to parse some arbitrarily complex XML into one or more
 * {@link BeanDefinition BeanDefinitions}. If you just want to parse some
 * XML into a single {@code BeanDefinition}, you may wish to consider
 * the simpler convenience extensions of this class, namely
 * {@link AbstractSingleBeanDefinitionParser} and
 * {@link AbstractSimpleBeanDefinitionParser}.
 * 注：当你打算将任意复杂的XML节点解析为一个或多个bean定义实例时可以考虑继承该抽象解析器。
 * 如果你仅仅需要将XML节点解析为一个bean定义，你可以考虑更简单方便的扩展子类，
 * 比如AbstractSingleBeanDefinitionParser、AbstractSimpleBeanDefinitionParser
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Rick Evans
 * @author Dave Syer
 * @since 2.0
 */
public abstract class AbstractBeanDefinitionParser implements BeanDefinitionParser {

	/** Constant for the "id" attribute.
	 * 注：默认情况下，唯一beanName的属性名；【注意自定义标签表示的bean类型不要存在和id重复的属性】
	 * */
	public static final String ID_ATTRIBUTE = "id";

	/** Constant for the "name" attribute.
	 * 注：bean名称(唯一或别名)的属性名；【注意自定义标签表示的bean类型不要存在和name重复的属性】
	 * */
	public static final String NAME_ATTRIBUTE = "name";


	/**
	 * 注：实现bean定义解析器唯一的解析方法。
	 * - 注意这里使用"final"修饰，这就是一个模版方法。
	 * - 一般自定义bean解析器都不会直接实现此接口，除非开发者能够熟悉ParserContext的内容。
	 */
	@Override
	@Nullable
	public final BeanDefinition parse(Element element, ParserContext parserContext) {
		// 注：调用模版的基础方法(需具体子类实现)来解析出bean定义实例
		AbstractBeanDefinition definition = parseInternal(element, parserContext);
		if (definition != null && !parserContext.isNested()) {
			/**
			 * 注：如果bean定义不为null(意即正常解析成功)，并且当前标签非嵌套内的bean定义(按照spring的说法是top-level)
			 * 为什么会有这个判断呢？因为前套内bean实例一般是属性值或者构造参数值，作为某个topBean的属性值使用，不需要注册到bean定义注册中心去。
			 * 而topBean需要注册，并且会暴露出去，因此需要在初始bean之上封装一层(BeanDefinitionHolder)，带有bean唯一名(id)、bean别名信息。
			 * - 从这里也就看出BeanDefinitionHolder作为封装bean定义存在的意义了。
			 */
			try {
				// 注：对于外部bean，需要解析出bean的唯一名。
				String id = resolveId(element, definition, parserContext);
				if (!StringUtils.hasText(id)) {
					// 注：默认情况下，外部bean一定要指定“id”属性值。否则无法注册到bean容器中，也无法暴露给用户。
					parserContext.getReaderContext().error(
							"Id is required for element '" + parserContext.getDelegate().getLocalName(element)
									+ "' when used as a top-level tag", element);
				}
				String[] aliases = null;
				if (shouldParseNameAsAliases()) {
					// 注：通过解析自定义标签的”name“属性来获得bean的别名信息
					String name = element.getAttribute(NAME_ATTRIBUTE);
					if (StringUtils.hasLength(name)) {
						// 注：可以指定多个别名，通过","分隔
						aliases = StringUtils.trimArrayElements(StringUtils.commaDelimitedListToStringArray(name));
					}
				}
				// 注：使用BeanDefinitionHolder实例封装bean定义实例、唯一名称、别名
				BeanDefinitionHolder holder = new BeanDefinitionHolder(definition, id, aliases);
				// 注：将bean定义等信息注册到解析上下文当中的bean注册中心
				registerBeanDefinition(holder, parserContext.getRegistry());
				if (shouldFireEvents()) {
					// 注：是否在bean定义解析后触发对应的事件
					BeanComponentDefinition componentDefinition = new BeanComponentDefinition(holder);
					postProcessComponentDefinition(componentDefinition);
					// 注：通过解析上下文来触发bean定义注册时间
					parserContext.registerComponent(componentDefinition);
				}
			}
			catch (BeanDefinitionStoreException ex) {
				String msg = ex.getMessage();
				parserContext.getReaderContext().error((msg != null ? msg : ex.toString()), element);
				return null;
			}
		}
		return definition;
	}

	/**
	 * Resolve the ID for the supplied {@link BeanDefinition}.
	 * 注：为提供的bean定义实例解析唯一id名
	 * <p>When using {@link #shouldGenerateId generation}, a name is generated automatically.
	 * Otherwise, the ID is extracted from the "id" attribute, potentially with a
	 * {@link #shouldGenerateIdAsFallback() fallback} to a generated id.
	 * 注：解析唯一ID名的步骤为：
	 * 1. 首先根据shouldGenerateId方法判断是否通过bean名生成器获取唯一的bean名称。默认情况下shouldGenerateId返回false，子类可重写
	 * 	  bean名生成器是从解析上下文获取的，默认为DefaultBeanNameGenerator实例；具体生成名的逻辑见：BeanDefinitionReaderUtils#generateBeanName
	 * 2. 再尝试获取当前标签上的"id"属性作为唯一bean名。【一定要注意top-自定义标签表示的bean类型尽可能不使用"id"属性】
	 * 3. 如果“id”属性值为空，再根据根据shouldGenerateIdAsFallback方法判断是否通过bean名生成器获取唯一的bean名称。默认情况下shouldGenerateId返回false，子类可重写
	 * 如果该策略返回的仍是空，调用处会抛出异常，因此自定义标签一般要求显示指定“id”属性值。
	 * @param element the element that the bean definition has been built from
	 * @param definition the bean definition to be registered
	 * @param parserContext the object encapsulating the current state of the parsing process;
	 * provides access to a {@link org.springframework.beans.factory.support.BeanDefinitionRegistry}
	 * @return the resolved id
	 * @throws BeanDefinitionStoreException if no unique name could be generated
	 * for the given bean definition
	 */
	protected String resolveId(Element element, AbstractBeanDefinition definition, ParserContext parserContext)
			throws BeanDefinitionStoreException {

		if (shouldGenerateId()) {
			// 注：通过Bean名生成器来生成bean的ID；默认为false.
			return parserContext.getReaderContext().generateBeanName(definition);
		}
		else {
			// 注：尝试获取当前标签的“id”属性。【① 注意自定义标签表示的bean类型不要使用“id”重名属性变量；② 自定义标签一定要标注“id”属性】
			String id = element.getAttribute(ID_ATTRIBUTE);
			if (!StringUtils.hasText(id) && shouldGenerateIdAsFallback()) {
				// 注：在"id"属性值为空的情况下，再通过Bean名生成器来生成bean的ID；默认为false.
				id = parserContext.getReaderContext().generateBeanName(definition);
			}
			return id;
		}
	}

	/**
	 * Register the supplied {@link BeanDefinitionHolder bean} with the supplied
	 * {@link BeanDefinitionRegistry registry}.
	 * 注：将提供的bean封装对象注册到指定的bean定义注册中心
	 * <p>Subclasses can override this method to control whether or not the supplied
	 * {@link BeanDefinitionHolder bean} is actually even registered, or to
	 * register even more beans.
	 * 注：子类可以重写该方法去控制是否提供的bean定义已经注册过了或者注册了多次。
	 * <p>The default implementation registers the supplied {@link BeanDefinitionHolder bean}
	 * with the supplied {@link BeanDefinitionRegistry registry} only if the {@code isNested}
	 * parameter is {@code false}, because one typically does not want inner beans
	 * to be registered as top level beans.
	 * 注：当前默认实现下仅会对外部bean(即top level bean)进行注册。
	 * @param definition the bean definition to be registered
	 * @param registry the registry that the bean is to be registered with
	 * @see BeanDefinitionReaderUtils#registerBeanDefinition(BeanDefinitionHolder, BeanDefinitionRegistry)
	 */
	protected void registerBeanDefinition(BeanDefinitionHolder definition, BeanDefinitionRegistry registry) {
		BeanDefinitionReaderUtils.registerBeanDefinition(definition, registry);
	}


	/**
	 * Central template method to actually parse the supplied {@link Element}
	 * into one or more {@link BeanDefinition BeanDefinitions}.
	 * @param element the element that is to be parsed into one or more {@link BeanDefinition BeanDefinitions}
	 * @param parserContext the object encapsulating the current state of the parsing process;
	 * provides access to a {@link org.springframework.beans.factory.support.BeanDefinitionRegistry}
	 * @return the primary {@link BeanDefinition} resulting from the parsing of the supplied {@link Element}
	 * @see #parse(org.w3c.dom.Element, ParserContext)
	 * @see #postProcessComponentDefinition(org.springframework.beans.factory.parsing.BeanComponentDefinition)
	 */
	@Nullable
	protected abstract AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext);

	/**
	 * Should an ID be generated instead of read from the passed in {@link Element}?
	 * 是否应该通过Bean名生成器来生成bean的ID，而不是读取标签上的"id"属性值
	 * <p>Disabled by default; subclasses can override this to enable ID generation.
	 * Note that this flag is about <i>always</i> generating an ID; the parser
	 * won't even check for an "id" attribute in this case.
	 * 注：默认情况下该方法返回false。
	 * 子类可以重写返回true，而使得总会通过bean生成器来生成对应的bean的唯一名。这种情况下，不会再去检查"id"的属性了。
	 * - 一般重写为true的子类可能都会自定义bean名称生成器【很少用】。
	 * @return whether the parser should always generate an id
	 */
	protected boolean shouldGenerateId() {
		return false;
	}

	/**
	 * Should an ID be generated instead if the passed in {@link Element} does not
	 * specify an "id" attribute explicitly?
	 * <p>Disabled by default; subclasses can override this to enable ID generation
	 * as fallback: The parser will first check for an "id" attribute in this case,
	 * only falling back to a generated ID if no value was specified.
	 * @return whether the parser should generate an id if no id was specified
	 */
	protected boolean shouldGenerateIdAsFallback() {
		return false;
	}

	/**
	 * Determine whether the element's "name" attribute should get parsed as
	 * bean definition aliases, i.e. alternative bean definition names.
	 * 注：该方法返回是否需要解析自定义标签的"name"属性来作为bean定义的别名。
	 * <p>The default implementation returns {@code true}.
	 * 注：默认的实现返回true
	 * @return whether the parser should evaluate the "name" attribute as aliases
	 * @since 4.1.5
	 */
	protected boolean shouldParseNameAsAliases() {
		return true;
	}

	/**
	 * Determine whether this parser is supposed to fire a
	 * {@link org.springframework.beans.factory.parsing.BeanComponentDefinition}
	 * event after parsing the bean definition.
	 * 注：返回解析器在解析bean定义后是否支持触发BeanComponentDefinition定义事件
	 * <p>This implementation returns {@code true} by default; that is,
	 * an event will be fired when a bean definition has been completely parsed.
	 * Override this to return {@code false} in order to suppress the event.
	 * @return {@code true} in order to fire a component registration event
	 * after parsing the bean definition; {@code false} to suppress the event
	 * @see #postProcessComponentDefinition
	 * @see org.springframework.beans.factory.parsing.ReaderContext#fireComponentRegistered
	 */
	protected boolean shouldFireEvents() {
		return true;
	}

	/**
	 * Hook method called after the primary parsing of a
	 * {@link BeanComponentDefinition} but before the
	 * {@link BeanComponentDefinition} has been registered with a
	 * {@link org.springframework.beans.factory.support.BeanDefinitionRegistry}.
	 * <p>Derived classes can override this method to supply any custom logic that
	 * is to be executed after all the parsing is finished.
	 * <p>The default implementation is a no-op.
	 * @param componentDefinition the {@link BeanComponentDefinition} that is to be processed
	 */
	protected void postProcessComponentDefinition(BeanComponentDefinition componentDefinition) {
	}

}
