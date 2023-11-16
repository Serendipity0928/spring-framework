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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.env.Profiles;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ResourceUtils;
import org.springframework.util.StringUtils;

/**
 * Default implementation of the {@link BeanDefinitionDocumentReader} interface that
 * reads bean definitions according to the "spring-beans" DTD and XSD format
 * (Spring's default XML bean definition format).
 * 注：BeanDefinitionDocumentReader接口默认的实现类，将根据不同的XML定义格式来读取bean定义。、
 * (spring默认的XML定义格式为XSD)
 *
 * <p>The structure, elements, and attribute names of the required XML document
 * are hard-coded in this class. (Of course a transform could be run if necessary
 * to produce this format). {@code <beans>} does not need to be the root
 * element of the XML document: this class will parse all bean definition elements
 * in the XML file, regardless of the actual root element.
 * 注：spring配置bean的xml文件所需要的结构、元素以及属性名称在当前类中是硬编码的。(当然，可以通过transform机制转换为这些格式。)
 * <beans>不需要是XML文档的根元素：当前默认类将解析当前XML文件中所有的bean定义元素，而不管实际的根元素。
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Erik Wiersma
 * @since 18.12.2003
 */
public class DefaultBeanDefinitionDocumentReader implements BeanDefinitionDocumentReader {

	public static final String BEAN_ELEMENT = BeanDefinitionParserDelegate.BEAN_ELEMENT;

	public static final String NESTED_BEANS_ELEMENT = "beans";

	public static final String ALIAS_ELEMENT = "alias";

	public static final String NAME_ATTRIBUTE = "name";

	public static final String ALIAS_ATTRIBUTE = "alias";

	public static final String IMPORT_ELEMENT = "import";

	public static final String RESOURCE_ATTRIBUTE = "resource";

	public static final String PROFILE_ATTRIBUTE = "profile";


	protected final Log logger = LogFactory.getLog(getClass());

	@Nullable
	private XmlReaderContext readerContext;

	@Nullable
	private BeanDefinitionParserDelegate delegate;


	/**
	 * This implementation parses bean definitions according to the "spring-beans" XSD
	 * (or DTD, historically).
	 * <p>Opens a DOM Document; then initializes the default settings
	 * specified at the {@code <beans/>} level; then parses the contained bean definitions.
	 * 注：【唯一的公共方法】
	 * 这个实现方法将根据spring的XSD格式(由于历史原因，也可能是DTD)来解析Bean定义。
	 * 基本步骤为：先open一个DOM文档对象；然后根据指定的beans级别来初始化默认的配置；最后解析xml文件内部包含的bean定义。
	 */
	@Override
	public void registerBeanDefinitions(Document doc, XmlReaderContext readerContext) {
		// 注：包含许多用户自定义的对象，会影响bean解析、注册的过程。比如bean定义注册中心等。
		this.readerContext = readerContext;
		// 注：获取DOM第一个标签元素，开始解析并注册bean定义。
		doRegisterBeanDefinitions(doc.getDocumentElement());
	}

	/**
	 * Return the descriptor for the XML resource that this parser works on.
	 */
	protected final XmlReaderContext getReaderContext() {
		Assert.state(this.readerContext != null, "No XmlReaderContext available");
		return this.readerContext;
	}

	/**
	 * Invoke the {@link org.springframework.beans.factory.parsing.SourceExtractor}
	 * to pull the source metadata from the supplied {@link Element}.
	 */
	@Nullable
	protected Object extractSource(Element ele) {
		return getReaderContext().extractSource(ele);
	}


	/**
	 * Register each bean definition within the given root {@code <beans/>} element.
	 * 注：根据给定的根元素(<beans>标签)来解析、注册bean定义。
	 */
	@SuppressWarnings("deprecation")  // for Environment.acceptsProfiles(String...)
	protected void doRegisterBeanDefinitions(Element root) {
		// Any nested <beans> elements will cause recursion in this method. In
		// order to propagate and preserve <beans> default-* attributes correctly,
		// keep track of the current (parent) delegate, which may be null. Create
		// the new (child) delegate with a reference to the parent for fallback purposes,
		// then ultimately reset this.delegate back to its original (parent) reference.
		// this behavior emulates a stack of delegates without actually necessitating one.
		/**
		 * 注：任何嵌套的<beans>标签都会递归调用当前方法。
		 * 为了正确的传播和保存<beans>标签的default-XXX属性，将通过this.delegate来记录父beans标签引用(可能会为null)。
		 * 创建当前BeanDefinitionParserDelegate代理对象时，会引用其父代理对象，以便后续进行回调。
		 * 因为是个递归调用过程，在开始解析当前<beans>标签时会将this.delegate指向当前的解析代理对象，在解析结束后，
		 * 会重新将该属性重置为父解析代理对象。这个模拟了代理栈的行为，然而实际上并不真正需要一个代理栈。
		 */
		BeanDefinitionParserDelegate parent = this.delegate; // 注：获取当前父<beans>代理信息
		/**
		 * 注：根据当前reader上下文、当前<beans>节点、父代理对象来创建当前<beans>的解析代理对象
		 * 创建代理对象时，还会初始化一些属性默认值，并考虑通过父解析对象中继承
		 */
		this.delegate = createDelegate(getReaderContext(), root, parent);

		/**
		 * 注：根据<beans>标签的xmlns属性来判断是否为默认的命名空间（可以不指定xmlns也是默认的命名空间）
		 * 如果是spring默认的命名空间，则会判断beans标签上的profile多环境配置。
		 * 如果当前beans标签所指定的profile属性值为非激活状态，则在此处会过滤，不再进一步解析<beans>标签下的bean定义
		 */
		if (this.delegate.isDefaultNamespace(root)) {
			// 注：获取<beans>标签上的profile属性值
			String profileSpec = root.getAttribute(PROFILE_ATTRIBUTE);
			if (StringUtils.hasText(profileSpec)) {
				// 注：profile属性值可以按照指定的分隔符(','或';'或' ')分隔从而指定多个环境配置
				String[] specifiedProfiles = StringUtils.tokenizeToStringArray(
						profileSpec, BeanDefinitionParserDelegate.MULTI_VALUE_ATTRIBUTE_DELIMITERS);
				// We cannot use Profiles.of(...) since profile expressions are not supported
				// in XML config. See SPR-12458 for details.
				/**
				 * 注：下面将通过Environment实例来判断当前<beans>下配置的bean是否继续解析，也就是当前环境是否与<beans>的profile配置匹配
				 * 1. 环境对象从哪来的？是谁？
				 * 环境对象是初始化BeanDefinitionReader传入的BeanDefinitionRegistry实例，如果该实例本身就实现了EnvironmentCapable
				 * 接口，那会通过该实例获取环境对象。【对于上下文场景，ApplicationContext接口继承了EnvironmentCapable接口】；
				 * 如果该bean注册中心实例未实现EnvironmentCapable接口，这里就初始化StandardEnvironment实例作为环境对象。
				 * 这里通过上下文中的BeanDefinitionReader来获取环境对象，然后判断当前环境是否可接受<beans>的配置。
				 * 2. 如何判断环境？
				 * 返回指定的多环境配置是否至少存在一个是激活状态，或者在没有显示指定激活环境配置时，判断指定的多环境配置是否存在默认环境配置。
				 * 如果其中一个环境Profile以'!'为前缀，就意味着判断其是否为非激活状态。
				 * 3. 环境配置？
				 * spring.profiles.active：配置当前激活的环境，多个以逗号分隔
				 * spring.profiles.default：当前默认激活的就环境，默认为default，多个以逗号分隔
				 */
				if (!getReaderContext().getEnvironment().acceptsProfiles(specifiedProfiles)) {
					if (logger.isDebugEnabled()) {
						logger.debug("Skipped XML bean definition file due to specified profiles [" + profileSpec +
								"] not matching: " + getReaderContext().getResource());
					}
					return;
				}
			}
		}

		// 注：解析bean定义的前置扩展
		preProcessXml(root);
		// 解析bean定义【重点】
		parseBeanDefinitions(root, this.delegate);
		// 注：解析bean定义的后置扩展
		postProcessXml(root);

		// 注：当前<beans>标签解析完成后，需要将this.delegate从新指向父解析代理对象，便于其他<beans>标签的解析
		this.delegate = parent;
	}

	protected BeanDefinitionParserDelegate createDelegate(
			XmlReaderContext readerContext, Element root, @Nullable BeanDefinitionParserDelegate parentDelegate) {

		// 注：根据当前读取上下文创建bean定义的解析代理对象
		BeanDefinitionParserDelegate delegate = new BeanDefinitionParserDelegate(readerContext);
		// 注：初始化当前bean定义解析对象的默认值，包括 lazy-init, autowire等
		delegate.initDefaults(root, parentDelegate);
		return delegate;
	}

	/**
	 * Parse the elements at the root level in the document:
	 * "import", "alias", "bean".
	 * 注：解析文档根级别的元素："import", "alias", "bean"
	 * @param root the DOM root element of the document
	 */
	protected void parseBeanDefinitions(Element root, BeanDefinitionParserDelegate delegate) {
		// 注：根据节点是否为默认命名空间来判断是否为自定义节点
		if (delegate.isDefaultNamespace(root)) {
			// 注：获取当前beans标签下所有的子标签
			NodeList nl = root.getChildNodes();
			for (int i = 0; i < nl.getLength(); i++) {
				Node node = nl.item(i);
				if (node instanceof Element) {
					Element ele = (Element) node;
					if (delegate.isDefaultNamespace(ele)) {
						// 注：如果子标签是spring默认标签，就通过parseDefaultElement进行解析
						parseDefaultElement(ele, delegate);
					}
					else {
						// 注：解析自定义标签
						delegate.parseCustomElement(ele);
					}
				}
			}
		}
		else {
			// 注：解析自定义节点
			delegate.parseCustomElement(root);
		}
	}

	// 注：解析默认的spring标签
	// [四大标签]：import、alias、bean、beans
	private void parseDefaultElement(Element ele, BeanDefinitionParserDelegate delegate) {
		if (delegate.nodeNameEquals(ele, IMPORT_ELEMENT)) {
			// 注: 解析<import>标签
			importBeanDefinitionResource(ele);
		}
		else if (delegate.nodeNameEquals(ele, ALIAS_ELEMENT)) {
			// 注: 解析<alias>标签
			processAliasRegistration(ele);
		}
		else if (delegate.nodeNameEquals(ele, BEAN_ELEMENT)) {
			// 注: 解析<bean>标签
			processBeanDefinition(ele, delegate);
		}
		else if (delegate.nodeNameEquals(ele, NESTED_BEANS_ELEMENT)) {
			// recurse
			// 注：解析<beans>标签；注意这里就递归回去了，也会创建代理解析对象，也会判断<beans>的环境配置。
			doRegisterBeanDefinitions(ele);
		}
	}

	/**
	 * Parse an "import" element and load the bean definitions
	 * from the given resource into the bean factory.
	 * 注：解析<import>标签，并且加载加载对应的bean定义并注册到bean工厂内
	 * 参考：https://www.jianshu.com/p/2a6d9dd71774
	 */
	protected void importBeanDefinitionResource(Element ele) {
		// 注：获取<import>标签的“resource”属性
		String location = ele.getAttribute(RESOURCE_ATTRIBUTE);
		if (!StringUtils.hasText(location)) {
			// 注：<import>标签必须存在“resource”属性
			getReaderContext().error("Resource location must not be empty", ele);
			return;
		}

		// Resolve system properties: e.g. "${user.dir}"
		// 注：通过环境配置或系统配置属性来尝试解析“resource”属性的占位符。占位符格式为${XXX}。
		location = getReaderContext().getEnvironment().resolveRequiredPlaceholders(location);

		// 注：实际加载的资源对象集合，汇总后通过事件发送出去
		Set<Resource> actualResources = new LinkedHashSet<>(4);

		// Discover whether the location is an absolute or relative URI
		boolean absoluteLocation = false;
		try {
			/**
			 * 注：判断资源定位路径是否为绝对路径。
			 * 1. spring内部定义的”伪“URL："classpath*:"或者"classpath:"开头
			 * 2. 资源路径为URL类型
			 * 3. 通过将资源路径转换为URI，再判断URI是否为绝对路径
			 * 有关URL、URI的区别于联系：https://zhuanlan.zhihu.com/p/465487888
			 */
			absoluteLocation = ResourcePatternUtils.isUrl(location) || ResourceUtils.toURI(location).isAbsolute();
		}
		catch (URISyntaxException ex) {
			// cannot convert to an URI, considering the location relative
			// unless it is the well-known Spring prefix "classpath*:"
			// 出现URI异常时，后续按相对路径对待
		}

		// Absolute or relative?
		if (absoluteLocation) {
			try {
				// 注：解析资源路径，并加载资源所持有的bean定义并返回加载bean定义的个数
				int importCount = getReaderContext().getReader().loadBeanDefinitions(location, actualResources);
				if (logger.isTraceEnabled()) {
					logger.trace("Imported " + importCount + " bean definitions from URL location [" + location + "]");
				}
			}
			catch (BeanDefinitionStoreException ex) {
				getReaderContext().error(
						"Failed to import bean definitions from URL location [" + location + "]", ele, ex);
			}
		}
		else {
			// No URL -> considering resource location as relative to the current file.
			// 注：如果是一个相对路径，则考虑以当前配置文件的相对路径寻找资源文件
			try {
				int importCount;
				// 注：通过当前配置文件资源对象(即getReaderContext().getResource())，来获取相对位置的资源对象
				Resource relativeResource = getReaderContext().getResource().createRelative(location);
				if (relativeResource.exists()) {
					// 注：当前资源存在，就加载当前资源下的bean定义，并返回加载bean定义的个数
					// 注：这里exists()方法并不区分大小写....,也有可能实际不存在，后续会报错
					importCount = getReaderContext().getReader().loadBeanDefinitions(relativeResource);
					// 注：同上
					actualResources.add(relativeResource);
				}
				else {
					// 注：获取当前资源的完成URL路径名；注意这里命名为baseLocation并非是资源的目录路径而是后续计算路径的基本路径。
					String baseLocation = getReaderContext().getResource().getURL().toString();
					/**
					 * 注：通过StringUtils.applyRelativePath(baseLocation, location)来获取基本路径.
					 * 这个通常是Resource不同实现类的兜底逻辑，大部分情况下走到这里的应该都不会存在该资源，后续会排除异常。
					 */
					importCount = getReaderContext().getReader().loadBeanDefinitions(
							StringUtils.applyRelativePath(baseLocation, location), actualResources);
				}
				if (logger.isTraceEnabled()) {
					logger.trace("Imported " + importCount + " bean definitions from relative location [" + location + "]");
				}
			}
			catch (IOException ex) {
				getReaderContext().error("Failed to resolve current resource location", ele, ex);
			}
			catch (BeanDefinitionStoreException ex) {
				getReaderContext().error(
						"Failed to import bean definitions from relative location [" + location + "]", ele, ex);
			}
		}

		// 注：向读取上下文中发送import了哪些资源文件事件
		Resource[] actResArray = actualResources.toArray(new Resource[0]);
		getReaderContext().fireImportProcessed(location, actResArray, extractSource(ele));
	}

	/**
	 * Process the given alias element, registering the alias with the registry.
	 * 注：处理<alias>标签，并注册到bean注册中心中
	 */
	protected void processAliasRegistration(Element ele) {
		// 注：获取<alias>标签的“name”属性
		String name = ele.getAttribute(NAME_ATTRIBUTE);
		// 注：获取<alias>标签的“alias”属性
		String alias = ele.getAttribute(ALIAS_ATTRIBUTE);
		boolean valid = true;
		if (!StringUtils.hasText(name)) {
			getReaderContext().error("Name must not be empty", ele);
			valid = false;
		}
		if (!StringUtils.hasText(alias)) {
			getReaderContext().error("Alias must not be empty", ele);
			valid = false;
		}
		if (valid) {	// 注：验证“name”属性与“alias”属性
			try {
				// 注：向bean注册中心中注册bean的别名
				getReaderContext().getRegistry().registerAlias(name, alias);
			}
			catch (Exception ex) {
				getReaderContext().error("Failed to register alias '" + alias +
						"' for bean with name '" + name + "'", ele, ex);
			}
			// 注：发送bean别名的事件
			getReaderContext().fireAliasRegistered(name, alias, extractSource(ele));
		}
	}

	/**
	 * Process the given bean element, parsing the bean definition
	 * and registering it with the registry.
	 * 注：处理指定的<bean>节点，解析bean定义，并将该bean定义注册进bean定义中心中。
	 */
	protected void processBeanDefinition(Element ele, BeanDefinitionParserDelegate delegate) {
		// 注：利用当前<beans>的bean定义代理解析器进行解析当前<bean>，获取bean定义持有对象(包括Bean定义实例、bean名称、别名)
		BeanDefinitionHolder bdHolder = delegate.parseBeanDefinitionElement(ele);
		if (bdHolder != null) {
			// 注：检查自定义属性及标签，对当前已获得的内部bean定义进行装饰
			// 注：解析默认标签中的自定义标签元素
			bdHolder = delegate.decorateBeanDefinitionIfRequired(ele, bdHolder);
			try {
				// Register the final decorated instance.
				// 注：向bean定义注册中心中注册当前解析的bean定义【重要】
				BeanDefinitionReaderUtils.registerBeanDefinition(bdHolder, getReaderContext().getRegistry());
			}
			catch (BeanDefinitionStoreException ex) {
				getReaderContext().error("Failed to register bean definition with name '" +
						bdHolder.getBeanName() + "'", ele, ex);
			}
			// Send registration event.
			// 注：发送注册bean定义事件
			getReaderContext().fireComponentRegistered(new BeanComponentDefinition(bdHolder));
		}
	}


	/**
	 * Allow the XML to be extensible by processing any custom element types first,
	 * before we start to process the bean definitions. This method is a natural
	 * extension point for any other custom pre-processing of the XML.
	 * 注：在我们开始处理bean定义之前，我们可以通过该方法扩展来首先处理任何自定义的元素类型。
	 * <p>The default implementation is empty. Subclasses can override this method to
	 * convert custom elements into standard Spring bean definitions, for example.
	 * Implementors have access to the parser's bean definition reader and the
	 * underlying XML resource, through the corresponding accessors.
	 * 注：当前方法的默认实现为空。子类可以重写该方法来讲自定义元素转换为标准的spring的bean定义。
	 * 比如，实现子类可以通过响应的访问器去获取解析的bean定义读取器以及底层的XML资源对象。
	 * @see #getReaderContext()
	 */
	protected void preProcessXml(Element root) {
	}

	/**
	 * Allow the XML to be extensible by processing any custom element types last,
	 * after we finished processing the bean definitions. This method is a natural
	 * extension point for any other custom post-processing of the XML.
	 * 注：注：在我们处理bean定义之后，我们可以通过该方法扩展来后续处理任何自定义的元素类型。
	 * <p>The default implementation is empty. Subclasses can override this method to
	 * convert custom elements into standard Spring bean definitions, for example.
	 * Implementors have access to the parser's bean definition reader and the
	 * underlying XML resource, through the corresponding accessors.
	 * 注：当前方法的默认实现为空。子类可以重写该方法来讲自定义元素转换为标准的spring的bean定义。
	 * 比如，实现子类可以通过响应的访问器去获取解析的bean定义读取器以及底层的XML资源对象。
	 * @see #getReaderContext()
	 */
	protected void postProcessXml(Element root) {
	}

}
