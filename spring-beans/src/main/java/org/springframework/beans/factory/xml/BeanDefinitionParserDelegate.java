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

package org.springframework.beans.factory.xml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.springframework.beans.BeanMetadataAttribute;
import org.springframework.beans.BeanMetadataAttributeAccessor;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.RuntimeBeanNameReference;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.parsing.BeanEntry;
import org.springframework.beans.factory.parsing.ConstructorArgumentEntry;
import org.springframework.beans.factory.parsing.ParseState;
import org.springframework.beans.factory.parsing.PropertyEntry;
import org.springframework.beans.factory.parsing.QualifierEntry;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.beans.factory.support.BeanDefinitionDefaults;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.LookupOverride;
import org.springframework.beans.factory.support.ManagedArray;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.ManagedMap;
import org.springframework.beans.factory.support.ManagedProperties;
import org.springframework.beans.factory.support.ManagedSet;
import org.springframework.beans.factory.support.MethodOverrides;
import org.springframework.beans.factory.support.ReplaceOverride;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.PatternMatchUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.DomUtils;

/**
 * Stateful delegate class used to parse XML bean definitions.
 * Intended for use by both the main parser and any extension
 * {@link BeanDefinitionParser BeanDefinitionParsers} or
 * {@link BeanDefinitionDecorator BeanDefinitionDecorators}.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Rod Johnson
 * @author Mark Fisher
 * @author Gary Russell
 * @since 2.0
 * @see ParserContext
 * @see DefaultBeanDefinitionDocumentReader
 */
public class BeanDefinitionParserDelegate {

	public static final String BEANS_NAMESPACE_URI = "http://www.springframework.org/schema/beans";

	public static final String MULTI_VALUE_ATTRIBUTE_DELIMITERS = ",; ";

	/**
	 * Value of a T/F attribute that represents true.
	 * Anything else represents false.
	 */
	public static final String TRUE_VALUE = "true";

	public static final String FALSE_VALUE = "false";

	public static final String DEFAULT_VALUE = "default";

	public static final String DESCRIPTION_ELEMENT = "description";

	public static final String AUTOWIRE_NO_VALUE = "no";

	public static final String AUTOWIRE_BY_NAME_VALUE = "byName";

	public static final String AUTOWIRE_BY_TYPE_VALUE = "byType";

	public static final String AUTOWIRE_CONSTRUCTOR_VALUE = "constructor";

	public static final String AUTOWIRE_AUTODETECT_VALUE = "autodetect";

	public static final String NAME_ATTRIBUTE = "name";

	public static final String BEAN_ELEMENT = "bean";

	public static final String META_ELEMENT = "meta";

	public static final String ID_ATTRIBUTE = "id";

	public static final String PARENT_ATTRIBUTE = "parent";

	public static final String CLASS_ATTRIBUTE = "class";

	public static final String ABSTRACT_ATTRIBUTE = "abstract";

	public static final String SCOPE_ATTRIBUTE = "scope";

	private static final String SINGLETON_ATTRIBUTE = "singleton";

	public static final String LAZY_INIT_ATTRIBUTE = "lazy-init";

	public static final String AUTOWIRE_ATTRIBUTE = "autowire";

	public static final String AUTOWIRE_CANDIDATE_ATTRIBUTE = "autowire-candidate";

	public static final String PRIMARY_ATTRIBUTE = "primary";

	public static final String DEPENDS_ON_ATTRIBUTE = "depends-on";

	public static final String INIT_METHOD_ATTRIBUTE = "init-method";

	public static final String DESTROY_METHOD_ATTRIBUTE = "destroy-method";

	public static final String FACTORY_METHOD_ATTRIBUTE = "factory-method";

	public static final String FACTORY_BEAN_ATTRIBUTE = "factory-bean";

	public static final String CONSTRUCTOR_ARG_ELEMENT = "constructor-arg";

	public static final String INDEX_ATTRIBUTE = "index";

	public static final String TYPE_ATTRIBUTE = "type";

	public static final String VALUE_TYPE_ATTRIBUTE = "value-type";

	public static final String KEY_TYPE_ATTRIBUTE = "key-type";

	public static final String PROPERTY_ELEMENT = "property";

	public static final String REF_ATTRIBUTE = "ref";

	public static final String VALUE_ATTRIBUTE = "value";

	public static final String LOOKUP_METHOD_ELEMENT = "lookup-method";

	public static final String REPLACED_METHOD_ELEMENT = "replaced-method";

	public static final String REPLACER_ATTRIBUTE = "replacer";

	public static final String ARG_TYPE_ELEMENT = "arg-type";

	public static final String ARG_TYPE_MATCH_ATTRIBUTE = "match";

	public static final String REF_ELEMENT = "ref";

	public static final String IDREF_ELEMENT = "idref";

	public static final String BEAN_REF_ATTRIBUTE = "bean";

	public static final String PARENT_REF_ATTRIBUTE = "parent";

	public static final String VALUE_ELEMENT = "value";

	public static final String NULL_ELEMENT = "null";

	public static final String ARRAY_ELEMENT = "array";

	public static final String LIST_ELEMENT = "list";

	public static final String SET_ELEMENT = "set";

	public static final String MAP_ELEMENT = "map";

	public static final String ENTRY_ELEMENT = "entry";

	public static final String KEY_ELEMENT = "key";

	public static final String KEY_ATTRIBUTE = "key";

	public static final String KEY_REF_ATTRIBUTE = "key-ref";

	public static final String VALUE_REF_ATTRIBUTE = "value-ref";

	public static final String PROPS_ELEMENT = "props";

	public static final String PROP_ELEMENT = "prop";

	public static final String MERGE_ATTRIBUTE = "merge";

	public static final String QUALIFIER_ELEMENT = "qualifier";

	public static final String QUALIFIER_ATTRIBUTE_ELEMENT = "attribute";

	public static final String DEFAULT_LAZY_INIT_ATTRIBUTE = "default-lazy-init";

	public static final String DEFAULT_MERGE_ATTRIBUTE = "default-merge";

	public static final String DEFAULT_AUTOWIRE_ATTRIBUTE = "default-autowire";

	public static final String DEFAULT_AUTOWIRE_CANDIDATES_ATTRIBUTE = "default-autowire-candidates";

	public static final String DEFAULT_INIT_METHOD_ATTRIBUTE = "default-init-method";

	public static final String DEFAULT_DESTROY_METHOD_ATTRIBUTE = "default-destroy-method";


	protected final Log logger = LogFactory.getLog(getClass());

	private final XmlReaderContext readerContext;

	private final DocumentDefaultsDefinition defaults = new DocumentDefaultsDefinition();

	private final ParseState parseState = new ParseState();

	/**
	 * Stores all used bean names so we can enforce uniqueness on a per
	 * beans-element basis. Duplicate bean ids/names may not exist within the
	 * same level of beans element nesting, but may be duplicated across levels.
	 * 注：该属性将存储所有的bean名称，所以我们可以在每一个<beans>元素的基础上强制保证唯一性。
	 * 在同一个<beans>元素内部不会存在重复的id、name属性，但是在不同<beans>元素可能有重复的。
	 * - BeanDefinitionParserDelegate对象是每一个<beans>元素创建一个
	 */
	private final Set<String> usedNames = new HashSet<>();


	/**
	 * Create a new BeanDefinitionParserDelegate associated with the supplied
	 * {@link XmlReaderContext}.
	 */
	public BeanDefinitionParserDelegate(XmlReaderContext readerContext) {
		Assert.notNull(readerContext, "XmlReaderContext must not be null");
		this.readerContext = readerContext;
	}


	/**
	 * Get the {@link XmlReaderContext} associated with this helper instance.
	 */
	public final XmlReaderContext getReaderContext() {
		return this.readerContext;
	}

	/**
	 * Invoke the {@link org.springframework.beans.factory.parsing.SourceExtractor}
	 * to pull the source metadata from the supplied {@link Element}.
	 */
	@Nullable
	protected Object extractSource(Element ele) {
		return this.readerContext.extractSource(ele);
	}

	/**
	 * Report an error with the given message for the given source element.
	 */
	protected void error(String message, Node source) {
		this.readerContext.error(message, source, this.parseState.snapshot());
	}

	/**
	 * Report an error with the given message for the given source element.
	 */
	protected void error(String message, Element source) {
		this.readerContext.error(message, source, this.parseState.snapshot());
	}

	/**
	 * Report an error with the given message for the given source element.
	 */
	protected void error(String message, Element source, Throwable cause) {
		this.readerContext.error(message, source, this.parseState.snapshot(), cause);
	}


	/**
	 * Initialize the default settings assuming a {@code null} parent delegate.
	 */
	public void initDefaults(Element root) {
		initDefaults(root, null);
	}

	/**
	 * Initialize the default lazy-init, autowire, dependency check settings,
	 * init-method, destroy-method and merge settings. Support nested 'beans'
	 * element use cases by falling back to the given parent in case the
	 * defaults are not explicitly set locally.
	 * 注：初始化解析bean定义时需要的默认配置，比如懒加载、自动装配、依赖检查设置、初始化方法、销毁方法、合并bean配置
	 * 在当前<beans>未明确指定的默认配置时，会通过其父级<beans>节点来获取这些默认配置
	 * @see #populateDefaults(DocumentDefaultsDefinition, DocumentDefaultsDefinition, org.w3c.dom.Element)
	 * @see #getDefaults()
	 */
	public void initDefaults(Element root, @Nullable BeanDefinitionParserDelegate parent) {
		// 注：填充默认值-this.defaults
		populateDefaults(this.defaults, (parent != null ? parent.defaults : null), root);
		// 注：触发默认配置注册事件 【暂时还不知道有什么用】
		this.readerContext.fireDefaultsRegistered(this.defaults);
	}

	/**
	 * Populate the given DocumentDefaultsDefinition instance with the default lazy-init,
	 * autowire, dependency check settings, init-method, destroy-method and merge settings.
	 * Support nested 'beans' element use cases by falling back to {@code parentDefaults}
	 * in case the defaults are not explicitly set locally.
	 * 注：初始化解析bean定义时需要的默认配置，比如懒加载、自动装配、依赖检查设置、初始化方法、销毁方法、合并bean配置
	 * 在当前<beans>未明确指定的默认配置时，会通过其父级<beans>节点来获取这些默认配置
	 * @param defaults the defaults to populate
	 * @param parentDefaults the parent BeanDefinitionParserDelegate (if any) defaults to fall back to
	 * @param root the root element of the current bean definition document (or nested beans element)
	 */
	protected void populateDefaults(DocumentDefaultsDefinition defaults, @Nullable DocumentDefaultsDefinition parentDefaults, Element root) {
		/**
		 * 注：解析懒加载默认配置
		 * 如果当前<beans>节点的"default-lazy-init"属性是否指定且不为“default”，则采用该值作为默认值。
		 * 否则，继承父<beans>节点的默认值(不存在父<beans>则为false)
		 */
		String lazyInit = root.getAttribute(DEFAULT_LAZY_INIT_ATTRIBUTE);
		if (isDefaultValue(lazyInit)) {
			// Potentially inherited from outer <beans> sections, otherwise falling back to false.
			lazyInit = (parentDefaults != null ? parentDefaults.getLazyInit() : FALSE_VALUE);
		}
		defaults.setLazyInit(lazyInit);

		/**
		 * 注：解析集合元素值是否合并的默认配置，如<array>等
		 * 如果当前<beans>节点的"default-merge"属性是否指定且不为“default”，则采用该值作为默认值。
		 * 否则，继承父<beans>节点的默认值(不存在父<beans>则为false)
		 */
		String merge = root.getAttribute(DEFAULT_MERGE_ATTRIBUTE);
		if (isDefaultValue(merge)) {
			// Potentially inherited from outer <beans> sections, otherwise falling back to false.
			merge = (parentDefaults != null ? parentDefaults.getMerge() : FALSE_VALUE);
		}
		defaults.setMerge(merge);

		/**
		 * 注：解析自动装配模式的默认配置
		 * 如果当前<beans>节点的"default-autowire"属性是否指定且不为“default”，则采用该值作为默认值。
		 * 否则，继承父<beans>节点的默认值(不存在父<beans>则为no，即不进行自动装配)
		 */
		String autowire = root.getAttribute(DEFAULT_AUTOWIRE_ATTRIBUTE);
		if (isDefaultValue(autowire)) {
			// Potentially inherited from outer <beans> sections, otherwise falling back to 'no'.
			autowire = (parentDefaults != null ? parentDefaults.getAutowire() : AUTOWIRE_NO_VALUE);
		}
		defaults.setAutowire(autowire);

		/**
		 * 注：解析自动装配候选的默认配置
		 * 如果当前<beans>节点的"default-autowire-candidates"属性是否指定，则采用该值作为默认值。
		 * 否则，继承父<beans>节点的默认值(不存在父<beans>则为null)
		 */
		if (root.hasAttribute(DEFAULT_AUTOWIRE_CANDIDATES_ATTRIBUTE)) {
			defaults.setAutowireCandidates(root.getAttribute(DEFAULT_AUTOWIRE_CANDIDATES_ATTRIBUTE));
		}
		else if (parentDefaults != null) {
			defaults.setAutowireCandidates(parentDefaults.getAutowireCandidates());
		}

		/**
		 * 注：解析初始化方法的默认配置
		 * 如果当前<beans>节点的"default-init-method"属性是否指定，则采用该值作为默认值。
		 * 否则，继承父<beans>节点的默认值(不存在父<beans>则为null)
		 */
		if (root.hasAttribute(DEFAULT_INIT_METHOD_ATTRIBUTE)) {
			defaults.setInitMethod(root.getAttribute(DEFAULT_INIT_METHOD_ATTRIBUTE));
		}
		else if (parentDefaults != null) {
			defaults.setInitMethod(parentDefaults.getInitMethod());
		}

		/**
		 * 注：解析销毁方法的默认配置
		 * 如果当前<beans>节点的"default-destroy-method"属性是否指定，则采用该值作为默认值。
		 * 否则，继承父<beans>节点的默认值(不存在父<beans>则为null)
		 */
		if (root.hasAttribute(DEFAULT_DESTROY_METHOD_ATTRIBUTE)) {
			defaults.setDestroyMethod(root.getAttribute(DEFAULT_DESTROY_METHOD_ATTRIBUTE));
		}
		else if (parentDefaults != null) {
			defaults.setDestroyMethod(parentDefaults.getDestroyMethod());
		}

		// 注：获取当前<beans>配置所在的资源对象。
		// XML的bean定义读取器的资源抽取器对象为NullSourceExtractor，这里会返回null
		defaults.setSource(this.readerContext.extractSource(root));
	}

	/**
	 * Return the defaults definition object.
	 */
	public DocumentDefaultsDefinition getDefaults() {
		return this.defaults;
	}

	/**
	 * Return the default settings for bean definitions as indicated within
	 * the attributes of the top-level {@code <beans/>} element.
	 */
	public BeanDefinitionDefaults getBeanDefinitionDefaults() {
		BeanDefinitionDefaults bdd = new BeanDefinitionDefaults();
		bdd.setLazyInit(TRUE_VALUE.equalsIgnoreCase(this.defaults.getLazyInit()));
		bdd.setAutowireMode(getAutowireMode(DEFAULT_VALUE));
		bdd.setInitMethodName(this.defaults.getInitMethod());
		bdd.setDestroyMethodName(this.defaults.getDestroyMethod());
		return bdd;
	}

	/**
	 * Return any patterns provided in the 'default-autowire-candidates'
	 * attribute of the top-level {@code <beans/>} element.
	 */
	@Nullable
	public String[] getAutowireCandidatePatterns() {
		String candidatePattern = this.defaults.getAutowireCandidates();
		return (candidatePattern != null ? StringUtils.commaDelimitedListToStringArray(candidatePattern) : null);
	}


	/**
	 * Parses the supplied {@code <bean>} element. May return {@code null}
	 * if there were errors during parse. Errors are reported to the
	 * {@link org.springframework.beans.factory.parsing.ProblemReporter}.
	 * 注：解析指定的<bean>节点。
	 * 如果在解析的过程中出现异常，可能会返回null。相对应的异常日志会存储在ProblemReporter中
	 */
	@Nullable
	public BeanDefinitionHolder parseBeanDefinitionElement(Element ele) {
		return parseBeanDefinitionElement(ele, null);
	}

	/**
	 * Parses the supplied {@code <bean>} element. May return {@code null}
	 * if there were errors during parse. Errors are reported to the
	 * {@link org.springframework.beans.factory.parsing.ProblemReporter}.
	 * 注：解析指定的<bean>节点。
	 * 如果在解析的过程中出现异常，可能会返回null。相对应的异常日志会存储在ProblemReporter中
	 */
	@Nullable
	public BeanDefinitionHolder parseBeanDefinitionElement(Element ele, @Nullable BeanDefinition containingBean) {
		// 注：获取bean定义的"id"属性
		String id = ele.getAttribute(ID_ATTRIBUTE);
		// 注：获取bean定义的“name”属性
		String nameAttr = ele.getAttribute(NAME_ATTRIBUTE);

		List<String> aliases = new ArrayList<>();
		if (StringUtils.hasLength(nameAttr)) {
			// 注：name属性可以指定多个别名，多个别名之间通过","或";"或" "进行分隔。
			// 注意tokenizeToStringArray这个方法不会去重...
			String[] nameArr = StringUtils.tokenizeToStringArray(nameAttr, MULTI_VALUE_ATTRIBUTE_DELIMITERS);
			aliases.addAll(Arrays.asList(nameArr));
		}

		// 注：默认情况下id属性就是bean的名称。
		String beanName = id;
		if (!StringUtils.hasText(beanName) && !aliases.isEmpty()) {
			// 注：如果未指定bean的id属性，并且指定了别名，那就使用别名的第一个名称作为bean的名称
			beanName = aliases.remove(0);
			if (logger.isTraceEnabled()) {
				logger.trace("No XML 'id' specified - using '" + beanName +
						"' as bean name and " + aliases + " as aliases");
			}
		}

		if (containingBean == null) {
			// 注：对于外部bean，会校验bean名称、别名不与之前的bean冲突；
			// 注意这里的不冲突是指的是同一个<beans>元素下
			checkNameUniqueness(beanName, aliases, ele);
		}

		// 注：解析bean定义本身-AbstractBeanDefinition
		AbstractBeanDefinition beanDefinition = parseBeanDefinitionElement(ele, beanName, containingBean);
		if (beanDefinition != null) {
			/**
			 * 注：对于已经解析完毕的bean定义，不允许不存在bean名称。
			 * 下面就是未在<bean>标签指定"id"和"name"属性的前提下，通过一定的策略来生成bean的名称。
			 * 缺少bean名称就会抛出异常。
			 */
			if (!StringUtils.hasText(beanName)) {
				try {
					if (containingBean != null) {
						/**
						 * 注：生成内部bean的bean名称，详细策略见generateBeanName方法。
						 * 非内部bean的bean名称生成策略默认也是该方法(区别在于最后参数为false)
						 */
						beanName = BeanDefinitionReaderUtils.generateBeanName(
								beanDefinition, this.readerContext.getRegistry(), true);
					}
					else {
						/**
						 * 注：非内部bean的bean名称生成策略可由用户自定义，默认为DefaultBeanNameGenerator类型的生成器实例。
						 * 该默认bean名称生成器的逻辑基本和BeanDefinitionReaderUtils#generateBeanName相同。
						 * 用户可通过XML的bean定义读取器来设置自定义的bean名称生成器，即setBeanNameGenerator
						 */
						beanName = this.readerContext.generateBeanName(beanDefinition);
						// Register an alias for the plain bean class name, if still possible,
						// if the generator returned the class name plus a suffix.
						// This is expected for Spring 1.2/2.0 backwards compatibility.
						/**
						 * 注：如果bean名称生成器返回的bean名称以指定的bean类型名称为前缀(也就是指定了“class”属性)，
						 * 此时会将bean的类型名尝试作为该bean的别名(当然这也是在该bean类型名未被其他bean定义使用的前提下)。
						 */
						String beanClassName = beanDefinition.getBeanClassName();
						if (beanClassName != null &&
								beanName.startsWith(beanClassName) && beanName.length() > beanClassName.length() &&
								!this.readerContext.getRegistry().isBeanNameInUse(beanClassName)) {
							aliases.add(beanClassName);
						}
					}
					if (logger.isTraceEnabled()) {
						logger.trace("Neither XML 'id' nor 'name' specified - " +
								"using generated bean name [" + beanName + "]");
					}
				}
				catch (Exception ex) {
					error(ex.getMessage(), ele);
					return null;
				}
			}
			String[] aliasesArray = StringUtils.toStringArray(aliases);
			// 注：使用BeanDefinitionHolder对象来存储bean定义、bean名称以及一系列别名
			return new BeanDefinitionHolder(beanDefinition, beanName, aliasesArray);
		}

		// 注：如果未解析到bean定义，则返回null [未捕获的异常]
		return null;
	}

	/**
	 * Validate that the specified bean name and aliases have not been used already
	 * within the current level of beans element nesting.
	 * 注：验证指定的bean名称以及别名在当前<beans>内部尚未被使用。
	 */
	protected void checkNameUniqueness(String beanName, List<String> aliases, Element beanElement) {
		String foundName = null;

		if (StringUtils.hasText(beanName) && this.usedNames.contains(beanName)) {
			// 注：从this.usedNames属性中尝试寻找当前beanName
			foundName = beanName;
		}
		if (foundName == null) {
			// 注：从this.usedNames属性中尝试匹配别名中任何一种
			foundName = CollectionUtils.findFirstMatch(this.usedNames, aliases);
		}
		if (foundName != null) {
			// 注：内部会抛出异常
			error("Bean name '" + foundName + "' is already used in this <beans> element", beanElement);
		}

		// 注：将当前bean名称，以及别名添加到this.usedNames属性中去
		this.usedNames.add(beanName);
		this.usedNames.addAll(aliases);
	}

	/**
	 * Parse the bean definition itself, without regard to name or aliases. May return
	 * {@code null} if problems occurred during the parsing of the bean definition.
	 * 注：解析bean定义本身，不考虑bean名称以及别名。
	 * 如果在解析的过程中出现异常，可能会返回null。相对应的异常日志会存储在ProblemReporter中
	 */
	@Nullable
	public AbstractBeanDefinition parseBeanDefinitionElement(
			Element ele, String beanName, @Nullable BeanDefinition containingBean) {
		/**
		 * 注：将当前bean名称封装到解析状态this.parseState属性中。
		 * ParseState实际上就是通过linkedList模拟了栈的操作，跟踪嵌套beanName的解析流程；
		 * 在当前bean定义解析完成后，再将beanName移除。
		 */
		this.parseState.push(new BeanEntry(beanName));

		String className = null;
		if (ele.hasAttribute(CLASS_ATTRIBUTE)) {
			// 注：解析当前bean的“class”属性
			className = ele.getAttribute(CLASS_ATTRIBUTE).trim();
		}
		String parent = null;
		if (ele.hasAttribute(PARENT_ATTRIBUTE)) {
			// 注：解析当前bean的“parent”属性(父bean定义名称，也可能是别名)
			parent = ele.getAttribute(PARENT_ATTRIBUTE);
		}

		try {
			/**
			 * 注：先根据配置的className，parentName获取bean定义实例。
			 * - 对于XML来说，该实例类型为GenericBeanDefinition
			 * - 实例化bean定义时，会初始化父bean定义名称、当前bean定义名称。
			 * - 如果bean定义读取器指定了类加载器，这里会直接根据className加载bean的类型。
			 * - 注意，这里并不会校验是否必须指定className，只有后续getBean流程会获取Bean的类型是会抛出异常。(No bean class specified on bean definition)
			 */
			AbstractBeanDefinition bd = createBeanDefinition(className, parent);

			/**
			 * 注：根据<bean>标签上的属性解析bean定义
			 * scope、abstractFlag、abstractFlag、autowireMode、dependsOn、autowireCandidate、primary
			 * initMethodName、enforceInitMethod、destroyMethodName、enforceDestroyMethod、factoryMethodName、factoryBeanName
			 */
			parseBeanDefinitionAttributes(ele, beanName, containingBean, bd);

			/**
			 * 注：上面解析的是<bean>标签的本身的属性，下面解析的是<bean>标签内部的子标签所声明的属性值，有：
			 * description、meta、lookup-method、replaced-method、constructor-arg、property、qualifier
			 */

			// 注：从当前<bean>标签的子元素中匹配<description>标签，获取该标签的text作为当前bean的描述信息
			bd.setDescription(DomUtils.getChildElementValueByTagName(ele, DESCRIPTION_ELEMENT));

			// 注：解析当前<bean>标签下所有的<meta>标签，并将其中的k-v元数据存储在bean定义
			parseMetaElements(ele, bd);
			// 注：解析当前<bean>标签下所有的<lookup-method>标签，并将数据存储在bean定义中的methodOverrides属性中。
			// <lookup-method>标签表示要重写用于获取同IOC容器中某bean实例的无参方法
			parseLookupOverrideSubElements(ele, bd.getMethodOverrides());
			// 注：解析当前<bean>标签中的replaced-method标签，并将数据存储在bean定义中的methodOverrides属性中。
			// <replaced-method>标签表示利用MethodReplacer对象来拦截当前bean的某方法。
			parseReplacedMethodSubElements(ele, bd.getMethodOverrides());

			// 注：解析当前<bean>标签下的构造参数，即<constructor-arg>标签内容
			parseConstructorArgElements(ele, bd);
			// 注：解析当前<bean>标签下的属性值，即<property>标签内容
			parsePropertyElements(ele, bd);
			// 注：解析当前<bean>标签下的qualifier值，即<qualifier>标签内容
			parseQualifierElements(ele, bd);

			// 注：将当前Bean定义所在的xml文件资源对象缓存在bean定义中
			bd.setResource(this.readerContext.getResource());
			// 注：获取bean定义抽取源；在XmlBeanDefinitionReader的sourceExtractor属性实例为NullSourceExtractor，这里返回的是null
			bd.setSource(extractSource(ele));

			// 注：返回解析后的bean定义
			return bd;
		}
		catch (ClassNotFoundException ex) {
			error("Bean class [" + className + "] not found", ele, ex);
		}
		catch (NoClassDefFoundError err) {
			error("Class that bean class [" + className + "] depends on not found", ele, err);
		}
		catch (Throwable ex) {
			error("Unexpected failure during bean definition parsing", ele, ex);
		}
		finally {
			// 注：将当前beanName从解析栈中移除。
			this.parseState.pop();
		}

		return null;
	}

	/**
	 * Apply the attributes of the given bean element to the given bean * definition.
	 * 注：将指定<bean>节点上的属性赋值到bean定义上
	 * @param ele bean declaration element
	 * @param beanName bean name
	 * @param containingBean containing bean definition
	 * @return a bean definition initialized according to the bean element attributes
	 */
	public AbstractBeanDefinition parseBeanDefinitionAttributes(Element ele, String beanName,
			@Nullable BeanDefinition containingBean, AbstractBeanDefinition bd) {

		// 注：解析当前bean定义的作用域
		if (ele.hasAttribute(SINGLETON_ATTRIBUTE)) {
			// 注：“singleton”不再使用，使用“scope”来声明
			error("Old 1.x 'singleton' attribute in use - upgrade to 'scope' declaration", ele);
		}
		else if (ele.hasAttribute(SCOPE_ATTRIBUTE)) {
			// 注：解析“scope”属性，赋值到bean定义的scope属性中
			bd.setScope(ele.getAttribute(SCOPE_ATTRIBUTE));
		}
		else if (containingBean != null) {
			// Take default from containing bean in case of an inner bean definition.
			// 注：如果内部bean未声明scope属性，且存在外部bean定义的情况下，内部bean会继承外部bean的scope属性
			bd.setScope(containingBean.getScope());
		}

		if (ele.hasAttribute(ABSTRACT_ATTRIBUTE)) {
			// 注：根据是否存在值为"true"的"abstract"，来设置bean定义是否可实例化
			bd.setAbstract(TRUE_VALUE.equals(ele.getAttribute(ABSTRACT_ATTRIBUTE)));
		}

		/**
		 * 注: 获取当前节点的“lazy-init”属性值bean定义中懒加载属性，如果为空或不为"default"就获取<beans>上的默认值。
		 * 默认懒加载配置为false.
		 */
		String lazyInit = ele.getAttribute(LAZY_INIT_ATTRIBUTE);
		if (isDefaultValue(lazyInit)) {
			lazyInit = this.defaults.getLazyInit();
		}
		bd.setLazyInit(TRUE_VALUE.equals(lazyInit));

		/**
		 * 注：根据当前<bean>的"autowire"属性(string)来赋值bean定义的自动装配模式(int)
		 * getAutowireMode方法就是将string类型属性值映射到int类型的装配模式上
		 * 默认属性值为"no"，对应的code值为0，即不自动装配
		 */
		String autowire = ele.getAttribute(AUTOWIRE_ATTRIBUTE);
		bd.setAutowireMode(getAutowireMode(autowire));

		/**
		 * 注：根据当前<bean>的"depends-on"属性来解析当前bean依赖的bean名称。
		 * 可以指定多个，多个依赖的bean名称以“,”或";"或“ ”分隔。
		 */
		if (ele.hasAttribute(DEPENDS_ON_ATTRIBUTE)) {
			String dependsOn = ele.getAttribute(DEPENDS_ON_ATTRIBUTE);
			bd.setDependsOn(StringUtils.tokenizeToStringArray(dependsOn, MULTI_VALUE_ATTRIBUTE_DELIMITERS));
		}

		/**
		 * 注：根据当前<bean>的“autowire-candidate”属性来解析当前bean是否可作为自动装配候选者。默认为false，除非配置为"true"
		 * 如果当前未配置该属性或配置为"default"，则通过<beans>获取默认自动装配者的配置进行匹配，匹配成功即为true.
		 */
		String autowireCandidate = ele.getAttribute(AUTOWIRE_CANDIDATE_ATTRIBUTE);
		if (isDefaultValue(autowireCandidate)) {
			String candidatePattern = this.defaults.getAutowireCandidates();
			if (candidatePattern != null) {
				String[] patterns = StringUtils.commaDelimitedListToStringArray(candidatePattern);
				bd.setAutowireCandidate(PatternMatchUtils.simpleMatch(patterns, beanName));
			}
		}
		else {
			bd.setAutowireCandidate(TRUE_VALUE.equals(autowireCandidate));
		}

		// 注：根据当前<bean>的“primary”属性来解析当前bean是否可作为自动装配候选者。默认为false，除非配置为"true"
		if (ele.hasAttribute(PRIMARY_ATTRIBUTE)) {
			bd.setPrimary(TRUE_VALUE.equals(ele.getAttribute(PRIMARY_ATTRIBUTE)));
		}

		/**
		 * 注：根据当前<bean>的“init-method”属性来解析当前bean的初始化方法名。
		 * 如果未指定，则考虑<beans>节点上的默认初始化方法配置。
		 * 需注意的是，如果是<beans>的默认初始化方法，则enforceInitMethod会被设置为false(默认为true)，即不会强制执行该初始化方法。
		 */
		if (ele.hasAttribute(INIT_METHOD_ATTRIBUTE)) {
			String initMethodName = ele.getAttribute(INIT_METHOD_ATTRIBUTE);
			bd.setInitMethodName(initMethodName);
		}
		else if (this.defaults.getInitMethod() != null) {
			bd.setInitMethodName(this.defaults.getInitMethod());
			// 注：通过<beans>获取的默认初始化方法，不会强制执行该方法。
			bd.setEnforceInitMethod(false);
		}

		/**
		 * 注：根据当前<bean>的“destroy-method”属性来解析当前bean的销毁方法名。
		 * 如果未指定，则考虑<beans>节点上的默认销毁方法配置。
		 * 需注意的是，如果是<beans>的默认销毁方法，则enforceInitMethod会被设置为false(默认为true)，即不会强制执行该销毁方法。
		 */
		if (ele.hasAttribute(DESTROY_METHOD_ATTRIBUTE)) {
			String destroyMethodName = ele.getAttribute(DESTROY_METHOD_ATTRIBUTE);
			bd.setDestroyMethodName(destroyMethodName);
		}
		else if (this.defaults.getDestroyMethod() != null) {
			bd.setDestroyMethodName(this.defaults.getDestroyMethod());
			// 注：通过<beans>获取的默认初始化方法，不会强制执行该方法。
			bd.setEnforceDestroyMethod(false);
		}

		// 注：根据当前<bean>的“factory-method”属性来解析当前bean的工厂方法名。
		if (ele.hasAttribute(FACTORY_METHOD_ATTRIBUTE)) {
			bd.setFactoryMethodName(ele.getAttribute(FACTORY_METHOD_ATTRIBUTE));
		}
		// 注：根据当前<bean>的“factory-bean”属性来解析当前bean的工厂Bean的名称。
		if (ele.hasAttribute(FACTORY_BEAN_ATTRIBUTE)) {
			bd.setFactoryBeanName(ele.getAttribute(FACTORY_BEAN_ATTRIBUTE));
		}

		return bd;
	}

	/**
	 * Create a bean definition for the given class name and parent name.
	 * @param className the name of the bean class
	 * @param parentName the name of the bean's parent bean
	 * @return the newly created bean definition
	 * @throws ClassNotFoundException if bean class resolution was attempted but failed
	 */
	protected AbstractBeanDefinition createBeanDefinition(@Nullable String className, @Nullable String parentName)
			throws ClassNotFoundException {

		return BeanDefinitionReaderUtils.createBeanDefinition(
				parentName, className, this.readerContext.getBeanClassLoader());
	}

	/**
	 * Parse the meta elements underneath the given element, if any.
	 * 注：解析指定DOM节点下的meta标签，并存储在BeanMetadataAttributeAccessor实例中(一般就是bean定义或属性PropertyValue)。
	 */
	public void parseMetaElements(Element ele, BeanMetadataAttributeAccessor attributeAccessor) {
		NodeList nl = ele.getChildNodes();
		for (int i = 0; i < nl.getLength(); i++) {
			Node node = nl.item(i);
			if (isCandidateElement(node) && nodeNameEquals(node, META_ELEMENT)) {
				Element metaElement = (Element) node;
				// 注：获取meta标签上的key属性值
				String key = metaElement.getAttribute(KEY_ATTRIBUTE);
				// 注：获取meta标签上的value属性值
				String value = metaElement.getAttribute(VALUE_ATTRIBUTE);
				BeanMetadataAttribute attribute = new BeanMetadataAttribute(key, value);
				attribute.setSource(extractSource(metaElement));
				attributeAccessor.addMetadataAttribute(attribute);
			}
		}
	}

	/**
	 * Parse the given autowire attribute value into
	 * {@link AbstractBeanDefinition} autowire constants.
	 * 将自动装配属性值(string类型)转换为自动装配模式code(int类型)
	 */
	@SuppressWarnings("deprecation")
	public int getAutowireMode(String attrValue) {
		String attr = attrValue;
		if (isDefaultValue(attr)) {
			// 注：如果属性值为空或为"default"，则会尝试获取<beans>上的默认值，默认为'no'
			attr = this.defaults.getAutowire();
		}
		// 注：自动装配模式默认为0
		int autowire = AbstractBeanDefinition.AUTOWIRE_NO;
		if (AUTOWIRE_BY_NAME_VALUE.equals(attr)) {
			// 注：如果自动装配属性值为“byName”，则自动装配模式映射为1
			autowire = AbstractBeanDefinition.AUTOWIRE_BY_NAME;
		}
		else if (AUTOWIRE_BY_TYPE_VALUE.equals(attr)) {
			// 注：如果自动装配属性值为“byType”，则自动装配模式映射为2
			autowire = AbstractBeanDefinition.AUTOWIRE_BY_TYPE;
		}
		else if (AUTOWIRE_CONSTRUCTOR_VALUE.equals(attr)) {
			// 注：如果自动装配属性值为“constructor”，则自动装配模式映射为3
			autowire = AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR;
		}
		else if (AUTOWIRE_AUTODETECT_VALUE.equals(attr)) {
			// 注：如果自动装配属性值为“autodetect”，则自动装配模式映射为4
			autowire = AbstractBeanDefinition.AUTOWIRE_AUTODETECT;
		}
		// Else leave default value.
		// 未指定自动装配属性值、或属性值非[byName、byType、constructor、autodetect]其中一种，则即为不自动装配。
		return autowire;
	}

	/**
	 * Parse constructor-arg sub-elements of the given bean element.
	 * 注：解析<bean>标签内部的构造参数标签，<constructor-arg>
	 */
	public void parseConstructorArgElements(Element beanEle, BeanDefinition bd) {
		NodeList nl = beanEle.getChildNodes();
		for (int i = 0; i < nl.getLength(); i++) {
			Node node = nl.item(i);
			if (isCandidateElement(node) && nodeNameEquals(node, CONSTRUCTOR_ARG_ELEMENT)) {
				// 解析<constructor-arg>标签
				parseConstructorArgElement((Element) node, bd);
			}
		}
	}

	/**
	 * Parse property sub-elements of the given bean element.
	 * 注：解析当前<bean>标签下的子<property>标签内容
	 */
	public void parsePropertyElements(Element beanEle, BeanDefinition bd) {
		NodeList nl = beanEle.getChildNodes();
		for (int i = 0; i < nl.getLength(); i++) {
			Node node = nl.item(i);
			if (isCandidateElement(node) && nodeNameEquals(node, PROPERTY_ELEMENT)) {
				// 注：解析当前的<property>标签内容
				parsePropertyElement((Element) node, bd);
			}
		}
	}

	/**
	 * Parse qualifier sub-elements of the given bean element.
	 * 注：解析当前<bean>标签下的子<qualifier>标签内容
	 */
	public void parseQualifierElements(Element beanEle, AbstractBeanDefinition bd) {
		NodeList nl = beanEle.getChildNodes();
		for (int i = 0; i < nl.getLength(); i++) {
			Node node = nl.item(i);
			if (isCandidateElement(node) && nodeNameEquals(node, QUALIFIER_ELEMENT)) {
				// 注：解析当前的<qualifier>标签内容
				parseQualifierElement((Element) node, bd);
			}
		}
	}

	/**
	 * Parse lookup-override sub-elements of the given bean element.
	 * 注：解析当前<bean>标签的中lookup-method标签，表示要重写用于获取同IOC容器中某bean实例的无参方法
	 * 注：参考 --> https://blog.csdn.net/weixin_56644618/article/details/127115857
	 */
	public void parseLookupOverrideSubElements(Element beanEle, MethodOverrides overrides) {
		NodeList nl = beanEle.getChildNodes();
		for (int i = 0; i < nl.getLength(); i++) {
			Node node = nl.item(i);
			if (isCandidateElement(node) && nodeNameEquals(node, LOOKUP_METHOD_ELEMENT)) {
				Element ele = (Element) node;
				// 注：获取lookup-method标签上的name属性值，即需要被重写的方法名
				String methodName = ele.getAttribute(NAME_ATTRIBUTE);
				// 注：获取lookup-method标签上的bean属性值，即该方法需要获取的当前bean工厂的某个bean名称
				String beanRef = ele.getAttribute(BEAN_ELEMENT);
				// 注：lookup-method标签声明的重写方法使用LookupOverride实例表示
				LookupOverride override = new LookupOverride(methodName, beanRef);
				override.setSource(extractSource(ele));
				overrides.addOverride(override);
			}
		}
	}

	/**
	 * Parse replaced-method sub-elements of the given bean element.
	 * 注：解析当前<bean>标签中的replaced-method标签，表示利用MethodReplacer对象来拦截当前bean的某方法。
	 * 注：参考 --> https://blog.csdn.net/weixin_56644618/article/details/127115857
	 */
	public void parseReplacedMethodSubElements(Element beanEle, MethodOverrides overrides) {
		NodeList nl = beanEle.getChildNodes();
		for (int i = 0; i < nl.getLength(); i++) {
			Node node = nl.item(i);
			if (isCandidateElement(node) && nodeNameEquals(node, REPLACED_METHOD_ELEMENT)) {
				Element replacedMethodEle = (Element) node;
				// 注：获取replaced-method标签上的name属性值，即需要被拦截的方法名
				String name = replacedMethodEle.getAttribute(NAME_ATTRIBUTE);
				// 注：获取replaced-method标签上的replacer属性值，即该方法需要获取的当前bean工厂的某个bean名称，类型为MethodReplacer
				String callback = replacedMethodEle.getAttribute(REPLACER_ATTRIBUTE);
				// 注：replaced-method标签声明的重写方法使用ReplaceOverride实例表示
				ReplaceOverride replaceOverride = new ReplaceOverride(name, callback);
				// Look for arg-type match elements.
				/**
				 * 注：用于匹配方法的入参类型。【因此replaced-method是支持有参方法的，而lookup-method仅是无参方法】
				 * - 获取replaced-method标签内的所有<arg-type>标签上的"match"属性获取拦截方法的参数类型，并存储到typeIdentifiers属性中
 				 */
				List<Element> argTypeEles = DomUtils.getChildElementsByTagName(replacedMethodEle, ARG_TYPE_ELEMENT);
				for (Element argTypeEle : argTypeEles) {
					String match = argTypeEle.getAttribute(ARG_TYPE_MATCH_ATTRIBUTE);
					match = (StringUtils.hasText(match) ? match : DomUtils.getTextValue(argTypeEle));
					if (StringUtils.hasText(match)) {
						replaceOverride.addTypeIdentifier(match);
					}
				}
				replaceOverride.setSource(extractSource(replacedMethodEle));
				overrides.addOverride(replaceOverride);
			}
		}
	}

	/**
	 * Parse a constructor-arg element.
	 * 注：解析<constructor-arg>标签
	 */
	public void parseConstructorArgElement(Element ele, BeanDefinition bd) {
		/**
		 * 注：尝试获取<constructor-arg>标签上的入参信息，如：
		 * - “index”属性表示的参数索引、”type“属性表示的参数类型、"name"属性表示的参数名称
		 */
		String indexAttr = ele.getAttribute(INDEX_ATTRIBUTE);
		String typeAttr = ele.getAttribute(TYPE_ATTRIBUTE);
		String nameAttr = ele.getAttribute(NAME_ATTRIBUTE);
		if (StringUtils.hasLength(indexAttr)) {
			// 注：如果存在参数索引，解析构造参数值后连同索引缓存到bean定义中
			try {
				int index = Integer.parseInt(indexAttr);	// 注：非Integer类型可能会抛出异常
				if (index < 0) {
					// 注：索引不能小于0
					error("'index' cannot be lower than 0", ele);
				}
				else {
					try {
						// 注：将当前构造参数解析添加入解析状态
						this.parseState.push(new ConstructorArgumentEntry(index));
						// 注：解析<constructor-arg>标签的值；和解析<property>类似。
						Object value = parsePropertyValue(ele, bd, null);
						// 注：通过ConstructorArgumentValues.ValueHolder对象包装构造参数值，后续会添加值的各种辅助属性
						ConstructorArgumentValues.ValueHolder valueHolder = new ConstructorArgumentValues.ValueHolder(value);
						if (StringUtils.hasLength(typeAttr)) {
							// 注：如果存在参数类型，这里设置进去
							valueHolder.setType(typeAttr);
						}
						if (StringUtils.hasLength(nameAttr)) {
							// 注：如果存在参数名称，这里设置进去
							valueHolder.setName(nameAttr);
						}
						valueHolder.setSource(extractSource(ele));
						if (bd.getConstructorArgumentValues().hasIndexedArgumentValue(index)) {
							// 注：检查bean定义中是否已经存在当前索引的参数值，不允许重复指定
							error("Ambiguous constructor-arg entries for index " + index, ele);
						}
						else {
							// 注：将解析后的构造参数及索引缓存到bean定义中
							bd.getConstructorArgumentValues().addIndexedArgumentValue(index, valueHolder);
						}
					}
					finally {
						// 注：解析完毕，释放解析状态
						this.parseState.pop();
					}
				}
			}
			catch (NumberFormatException ex) {
				error("Attribute 'index' of tag 'constructor-arg' must be an integer", ele);
			}
		}
		else {
			// 注：不存在构造参数索引，此时仅能先解析通用的构造参数值，后续再根据类型、参数名等进行匹配
			try {
				// 注：将当前构造参数解析添加入解析状态
				this.parseState.push(new ConstructorArgumentEntry());
				// 注：解析<constructor-arg>标签的值；和解析<property>类似。
				Object value = parsePropertyValue(ele, bd, null);
				// 注：通过ConstructorArgumentValues.ValueHolder对象包装构造参数值，后续会添加值的各种辅助属性
				ConstructorArgumentValues.ValueHolder valueHolder = new ConstructorArgumentValues.ValueHolder(value);
				if (StringUtils.hasLength(typeAttr)) {
					// 注：如果存在参数类型，这里设置进去
					valueHolder.setType(typeAttr);
				}
				if (StringUtils.hasLength(nameAttr)) {
					// 注：如果存在参数名称，这里设置进去
					valueHolder.setName(nameAttr);
				}
				valueHolder.setSource(extractSource(ele));
				// 注：将解析后的构造参数到bean定义中 【无构造参数索引】
				bd.getConstructorArgumentValues().addGenericArgumentValue(valueHolder);
			}
			finally {
				// 注：解析完毕，释放解析状态
				this.parseState.pop();
			}
		}
	}

	/**
	 * Parse a property element.
	 * 注：解析当前的<property>标签内容
	 */
	public void parsePropertyElement(Element ele, BeanDefinition bd) {
		// 注：获取<property>的name属性值。[注，必须指定该属性值，否则会排除异常]
		String propertyName = ele.getAttribute(NAME_ATTRIBUTE);
		if (!StringUtils.hasLength(propertyName)) {
			error("Tag 'property' must have a 'name' attribute", ele);
			return;
		}
		// 注：解析<property>标签之前，将属性值名称存入解析状态栈中，解析完之后移除。
		this.parseState.push(new PropertyEntry(propertyName));
		try {
			// 注：通过bean定义判断当前属性名称是否已经处理过了，即不允许重复指定同一属性值
			if (bd.getPropertyValues().contains(propertyName)) {
				error("Multiple 'property' definitions for property '" + propertyName + "'", ele);
				return;
			}
			// 注：获取<property>标签通过属性或者子标签(表示值的标签)所表示的属性值，这个值可能是一个数组、集合等。
			Object val = parsePropertyValue(ele, bd, propertyName);
			// 注：使用PropertyValue来封装<property>标签表示的属性和值，支持meta元数据属性
			PropertyValue pv = new PropertyValue(propertyName, val);
			// 注：解析<property>标签内的<meta>标签的属性值
			parseMetaElements(ele, pv);
			pv.setSource(extractSource(ele));
			// 注：将<property>标签表示的信息赋值到bean定义的propertyValues属性中
			bd.getPropertyValues().addPropertyValue(pv);
		}
		finally {
			// 注：将当前属性的解析状态移除
			this.parseState.pop();
		}
	}

	/**
	 * Parse a qualifier element.
	 * 注：解析<qualifier>标签内容
	 */
	public void parseQualifierElement(Element ele, AbstractBeanDefinition bd) {
		// 注：获取<qualifier>标签的“type”属性标识的类型信息
		String typeName = ele.getAttribute(TYPE_ATTRIBUTE);
		if (!StringUtils.hasLength(typeName)) {
			/**
			 * 注：<qualifier>标签必须存在“type”属性信息
			 * XML中，如果用户未在<qualifier>标签上指定“type”属性，则会有默认的属性值：org.springframework.beans.factory.annotation.Qualifier
			 */
			error("Tag 'qualifier' must have a 'type' attribute", ele);
			return;
		}
		// 注：将qualifier解析状态添加至解析栈中
		this.parseState.push(new QualifierEntry(typeName));
		try {
			// 注：通过AutowireCandidateQualifier类型封装qualifier(合格)类型
			AutowireCandidateQualifier qualifier = new AutowireCandidateQualifier(typeName);
			qualifier.setSource(extractSource(ele));
			// 注：获取<qualifier>标签的“value”属性
			String value = ele.getAttribute(VALUE_ATTRIBUTE);
			if (StringUtils.hasLength(value)) {
				// 注：存在“value”属性就将qualifier的value属性填充进去
				qualifier.setAttribute(AutowireCandidateQualifier.VALUE_KEY, value);
			}
			NodeList nl = ele.getChildNodes();
			for (int i = 0; i < nl.getLength(); i++) {
				Node node = nl.item(i);
				// 注：遍历<qualifier>标签内所有的子<attribute>标签，获取qualifier的其余属性信息
				if (isCandidateElement(node) && nodeNameEquals(node, QUALIFIER_ATTRIBUTE_ELEMENT)) {
					Element attributeEle = (Element) node;
					// 注：获取<attribute>标签的“key”或“value”属性值
					String attributeName = attributeEle.getAttribute(KEY_ATTRIBUTE);
					String attributeValue = attributeEle.getAttribute(VALUE_ATTRIBUTE);
					if (StringUtils.hasLength(attributeName) && StringUtils.hasLength(attributeValue)) {
						// 注：通过BeanMetadataAttribute来存储属性的kv值
						BeanMetadataAttribute attribute = new BeanMetadataAttribute(attributeName, attributeValue);
						attribute.setSource(extractSource(attributeEle));
						qualifier.addMetadataAttribute(attribute);
					}
					else {
						// 注：<attribute>标签必须存在“key”或“value”属性值
						error("Qualifier 'attribute' tag must have a 'name' and 'value'", attributeEle);
						return;
					}
				}
			}
			// 注：将解析<qualifier>标签的内容存储在bean定义中
			bd.addQualifier(qualifier);
		}
		finally {
			// 注：释放qualifier解析状态
			this.parseState.pop();
		}
	}

	/**
	 * Get the value of a property element. May be a list etc.
	 * Also used for constructor arguments, "propertyName" being null in this case.
	 * 注：获取<property>所表示属性的值，这个值可能是一个列表。
	 * 这个方法也会在<constructor-arg>中使用，这种情况下propertyName为null。
	 */
	@Nullable
	public Object parsePropertyValue(Element ele, BeanDefinition bd, @Nullable String propertyName) {
		// 注：当前标签类型名称，根据propertyName是否为null区分<property>以及<constructor-arg>。该信息用于后续日志使用。
		String elementName = (propertyName != null ?
				"<property> element for property '" + propertyName + "'" :
				"<constructor-arg> element");

		// Should only have one child element: ref, value, list, etc.
		/**
		 * 注：在<property>或者<constructor-arg>标签下，除了<description>以及<meta>，其他子标签仅允许有一个，包括：
		 * ref、bean、value、array、idref、list、map、null、props、set
		 */
		NodeList nl = ele.getChildNodes();
		Element subElement = null;	// 注：我们要找的表示当前属性值的子元素
		for (int i = 0; i < nl.getLength(); i++) {
			Node node = nl.item(i);
			if (node instanceof Element && !nodeNameEquals(node, DESCRIPTION_ELEMENT) &&
					!nodeNameEquals(node, META_ELEMENT)) {
				// Child element is what we're looking for.
				if (subElement != null) {
					// 注：发现多个表示值的标签，抛出异常
					error(elementName + " must not contain more than one sub-element", ele);
				}
				else {
					subElement = (Element) node;
				}
			}
		}

		/**
		 * 注：在<property>或者<constructor-arg>标签上可以通过ref、value来声明值。
		 * 这里会判断标签上、标签上&标签中不会存在冲突的多个值。
		 */
		// 注：获取标签的ref属性值
		boolean hasRefAttribute = ele.hasAttribute(REF_ATTRIBUTE);
		// 注：获取标签的value属性值
		boolean hasValueAttribute = ele.hasAttribute(VALUE_ATTRIBUTE);
		if ((hasRefAttribute && hasValueAttribute) ||
				((hasRefAttribute || hasValueAttribute) && subElement != null)) {
			// 注：存在冲突的多个值时，抛出异常
			error(elementName +
					" is only allowed to contain either 'ref' attribute OR 'value' attribute OR sub-element", ele);
		}

		if (hasRefAttribute) {		// 注：如果在标签上通过ref来声明需引用的bean实例
			// 注：获取属性引用实例的bean名称
			String refName = ele.getAttribute(REF_ATTRIBUTE);
			if (!StringUtils.hasText(refName)) {
				error(elementName + " contains empty 'ref' attribute", ele);
			}
			// 注：由于展示无法根据refName获取真实的实例，这里先试用运行时bean引用占用。
			RuntimeBeanReference ref = new RuntimeBeanReference(refName);
			ref.setSource(extractSource(ele));
			return ref;
		}
		else if (hasValueAttribute) {		// 注：如果在标签上通过value来声明当前属性值
			// 注：通过TypedStringValue类型封装对应的string值，实际上这里就是String类型(targetType为null)
			TypedStringValue valueHolder = new TypedStringValue(ele.getAttribute(VALUE_ATTRIBUTE));
			valueHolder.setSource(extractSource(ele));
			return valueHolder;
		}
		else if (subElement != null) {
			// 注：没有指定属性值，且存在子元素。下面将解析<property>或者<constructor-arg>标签内的子元素来获取值
			return parsePropertySubElement(subElement, bd);
		}
		else {
			// Neither child element nor "ref" or "value" attribute found.
			// 注：既没有子元素也未指定"ref"或"value"属性值
			error(elementName + " must specify a ref or value", ele);
			return null;
		}
	}

	/**
	 * Parse a value, ref or collection sub-element of a property or
	 * constructor-arg element.
	 * 注：解析<property>或者<constructor-arg>标签内的子元素来获取值。
	 * 包括：ref、bean、value、array、idref、list、map、null、props、set
	 * @param ele subelement of property element; we don't know which yet
	 * @param bd the current bean definition (if any)
	 */
	@Nullable
	public Object parsePropertySubElement(Element ele, @Nullable BeanDefinition bd) {
		return parsePropertySubElement(ele, bd, null);
	}

	/**
	 * Parse a value, ref or collection sub-element of a property or
	 * constructor-arg element.
	 * 注：解析<property>或者<constructor-arg>标签内的子元素来获取值。
	 * 包括：bean、ref、idref、value、null、array、list、set、map、props
	 * - 集合标签也会递归到这里来，如<array>、<list>、<set>、<map>等
	 * @param ele subelement of property element; we don't know which yet
	 * @param bd the current bean definition (if any)
	 * @param defaultValueType the default type (class name) for any
	 * {@code <value>} tag that might be created
	 */
	@Nullable
	public Object parsePropertySubElement(Element ele, @Nullable BeanDefinition bd, @Nullable String defaultValueType) {
		if (!isDefaultNamespace(ele)) {
			/**
			 * 注：暂时不能确定ele是哪个标签，根据默认命名空间来判定是否为自定义的标签
			 * parseNestedCustomElement解析自定义的标签
			 */
			return parseNestedCustomElement(ele, bd);
		}
		else if (nodeNameEquals(ele, BEAN_ELEMENT)) {		// 注：子标签为<bean>标签，即存在嵌套<bean>
			/**
			 * 注：解析<bean>标签，获取bean定义实例
			 * 注意这里将外部bean的bean定义实例传入了进去，也即使containingBean。
			 */
			BeanDefinitionHolder nestedBd = parseBeanDefinitionElement(ele, bd);
			if (nestedBd != null) {
				// 注：检查自定义属性及标签，对当前已获得的内部bean定义进行装饰
				nestedBd = decorateBeanDefinitionIfRequired(ele, nestedBd, bd);
			}
			return nestedBd;
		}
		else if (nodeNameEquals(ele, REF_ELEMENT)) {		// 注：子标签为<ref>标签
			// A generic reference to any name of any bean.
			// 注：获取<ref>标签上的"bean"属性；ref是任何bean的通用名称引用。
			String refName = ele.getAttribute(BEAN_REF_ATTRIBUTE);
			boolean toParent = false;
			if (!StringUtils.hasLength(refName)) {
				// A reference to the id of another bean in a parent context.
				// 注：如果<ref>标签上未指定"bean"属性，检查是否存在父工厂中的"parent"属性
				refName = ele.getAttribute(PARENT_REF_ATTRIBUTE);
				toParent = true;
				if (!StringUtils.hasLength(refName)) {
					// 注：“bean” or “parent” 属性均为指定，抛出异常
					error("'bean' or 'parent' is required for <ref> element", ele);
					return null;
				}
			}
			if (!StringUtils.hasText(refName)) {
				// 注：<ref>未指定合法的“bean”或“parent”引用属性值，抛出异常
				error("<ref> element contains empty target attribute", ele);
				return null;
			}
			// 注：由于展示无法根据refName获取真实的实例，这里先试用运行时bean引用占用。(toParent为true表示依赖bean存在于父工厂中)
			RuntimeBeanReference ref = new RuntimeBeanReference(refName, toParent);
			ref.setSource(extractSource(ele));
			return ref;
		}
		else if (nodeNameEquals(ele, IDREF_ELEMENT)) {		// 注：子标签为<idref>标签
			// 注：解析出<idref>标签通过"bean"属性指定的bean实例的名称。
			return parseIdRefElement(ele);
		}
		else if (nodeNameEquals(ele, VALUE_ELEMENT)) {  	// 注：子标签为<value>标签
			// 注：解析<value>标签返回具有类型的字符串实例
			return parseValueElement(ele, defaultValueType);
		}
		else if (nodeNameEquals(ele, NULL_ELEMENT)) {		// 注：子标签为<null>标签
			// It's a distinguished null value. Let's wrap it in a TypedStringValue
			// object in order to preserve the source location.
			/**
			 * 注：这是一个明确指定的null值。
			 * 之所以通过TypedStringValue包装，是为了保留该值的源位置，即source属性
			 */
			TypedStringValue nullHolder = new TypedStringValue(null);
			nullHolder.setSource(extractSource(ele));
			return nullHolder;
		}
		else if (nodeNameEquals(ele, ARRAY_ELEMENT)) {		// 注：子标签为<array>标签
			return parseArrayElement(ele, bd);
		}
		else if (nodeNameEquals(ele, LIST_ELEMENT)) {		// 注：子标签为<list>标签
			return parseListElement(ele, bd);
		}
		else if (nodeNameEquals(ele, SET_ELEMENT)) {		// 注：子标签为<set>标签
			return parseSetElement(ele, bd);
		}
		else if (nodeNameEquals(ele, MAP_ELEMENT)) {		// 注：子标签为<map>标签
			// 注：解析<map>标签
			return parseMapElement(ele, bd);
		}
		else if (nodeNameEquals(ele, PROPS_ELEMENT)) {		// 注：子标签为<props>标签
			// 注：解析<props>标签，该标签是填充Properties类型对象的值
			return parsePropsElement(ele);
		}
		else {
			// 注：其他位置表示值的字标签，无法解析，抛出异常
			error("Unknown property sub-element: [" + ele.getNodeName() + "]", ele);
			return null;
		}
	}

	/**
	 * Return a typed String value Object for the given 'idref' element.
	 * 注：根据指定的<idref>标签返回具有类型的String值。【注释有误？实际上是解析后的String】
	 */
	@Nullable
	public Object parseIdRefElement(Element ele) {
		// A generic reference to any name of any bean.
		// 注：获取<idref>标签的"bean"属性值
		String refName = ele.getAttribute(BEAN_REF_ATTRIBUTE);
		if (!StringUtils.hasLength(refName)) {
			// 注：必须存在有效的属性值，否则排除异常。
			error("'bean' is required for <idref> element", ele);
			return null;
		}
		if (!StringUtils.hasText(refName)) {
			// 注：这里是判断类空格了，完全可以合并一起..
			error("<idref> element contains empty target attribute", ele);
			return null;
		}
		/**
		 * 注：<idref>标签通过bean引用的不是实例，而是bean实例的名称。
		 * 因此注入的是string类型，但相比于value来说，多了bean存在性校验
		 */
		RuntimeBeanNameReference ref = new RuntimeBeanNameReference(refName);
		ref.setSource(extractSource(ele));
		return ref;
	}

	/**
	 * Return a typed String value Object for the given value element.
	 * 注：根据指定的<value>标签返回具有类型的String值。
	 * 使用<value>标签和使用value属性，区别在于前者可增加类型
	 */
	public Object parseValueElement(Element ele, @Nullable String defaultTypeName) {
		// It's a literal value.
		// 注：获取<value>标签内部的文本字符串
		String value = DomUtils.getTextValue(ele);
		// 注：获取<value>标签type属性值，即为字符串的类型
		String specifiedTypeName = ele.getAttribute(TYPE_ATTRIBUTE);
		String typeName = specifiedTypeName;
		if (!StringUtils.hasText(typeName)) {
			// 注：如果未指定类型，则考虑使用默认类型
			typeName = defaultTypeName;
		}
		try {
			// 注：根据字符串值以及类型来创建TypedStringValue类型对象返回
			TypedStringValue typedValue = buildTypedStringValue(value, typeName);
			typedValue.setSource(extractSource(ele));
			// 注：设置实际指定的类型
			typedValue.setSpecifiedTypeName(specifiedTypeName);
			return typedValue;
		}
		catch (ClassNotFoundException ex) {
			error("Type class [" + typeName + "] not found for <value> element", ele, ex);
			return value;
		}
	}

	/**
	 * Build a typed String value Object for the given raw value.
	 * @see org.springframework.beans.factory.config.TypedStringValue
	 */
	protected TypedStringValue buildTypedStringValue(String value, @Nullable String targetTypeName)
			throws ClassNotFoundException {

		ClassLoader classLoader = this.readerContext.getBeanClassLoader();
		TypedStringValue typedValue;
		if (!StringUtils.hasText(targetTypeName)) {
			typedValue = new TypedStringValue(value);
		}
		else if (classLoader != null) {
			Class<?> targetType = ClassUtils.forName(targetTypeName, classLoader);
			typedValue = new TypedStringValue(value, targetType);
		}
		else {
			typedValue = new TypedStringValue(value, targetTypeName);
		}
		return typedValue;
	}

	/**
	 * Parse an array element.
	 * 注：解析<array>标签
	 */
	public Object parseArrayElement(Element arrayEle, @Nullable BeanDefinition bd) {
		// 注：获取<array>标签上的"value-type"属性，表示这个数组元素的类型。未指定即为String类型。
		String elementType = arrayEle.getAttribute(VALUE_TYPE_ATTRIBUTE);
		NodeList nl = arrayEle.getChildNodes();		// 注：<array>标签的长度即为数组的长度
		// 注：根据类型和长度来实例化ManagedArray实例-用于管理数组元素
		ManagedArray target = new ManagedArray(elementType, nl.getLength());
		target.setSource(extractSource(arrayEle));
		// 注：设置数组元素类型
		target.setElementTypeName(elementType);
		// 注：根据<array>标签上的"merge“属性，默认为false
		target.setMergeEnabled(parseMergeAttribute(arrayEle));
		// 注：解析<array>标签内的子标签，即数组的元素值
		parseCollectionElements(nl, target, bd, elementType);
		return target;
	}

	/**
	 * Parse a list element.
	 * 注：解析<list>标签
	 */
	public List<Object> parseListElement(Element collectionEle, @Nullable BeanDefinition bd) {
		// 注：获取<list>标签上的"value-type"属性，表示这个列表元素的类型。未指定即为String类型。
		String defaultElementType = collectionEle.getAttribute(VALUE_TYPE_ATTRIBUTE);
		NodeList nl = collectionEle.getChildNodes();
		// 注：根据长度来实例化ManagedList实例-用于管理列表元素
		ManagedList<Object> target = new ManagedList<>(nl.getLength());
		target.setSource(extractSource(collectionEle));
		target.setElementTypeName(defaultElementType);
		// 注：根据<list>标签上的"merge“属性，默认为false
		target.setMergeEnabled(parseMergeAttribute(collectionEle));
		// 注：解析<list>标签内的子标签，即列表的元素值
		parseCollectionElements(nl, target, bd, defaultElementType);
		return target;
	}

	/**
	 * Parse a set element.
	 * 注：解析<set>标签
	 * 与上述<array>、<list>类似，不再赘述
	 */
	public Set<Object> parseSetElement(Element collectionEle, @Nullable BeanDefinition bd) {
		String defaultElementType = collectionEle.getAttribute(VALUE_TYPE_ATTRIBUTE);
		NodeList nl = collectionEle.getChildNodes();
		ManagedSet<Object> target = new ManagedSet<>(nl.getLength());
		target.setSource(extractSource(collectionEle));
		target.setElementTypeName(defaultElementType);
		target.setMergeEnabled(parseMergeAttribute(collectionEle));
		parseCollectionElements(nl, target, bd, defaultElementType);
		return target;
	}

	/**
	 * 注：解析集合类标签内的子标签，即集合的元素值
	 * <array>、<list>
	 */
	protected void parseCollectionElements(
			NodeList elementNodes, Collection<Object> target, @Nullable BeanDefinition bd, String defaultElementType) {

		for (int i = 0; i < elementNodes.getLength(); i++) {
			Node node = elementNodes.item(i);
			if (node instanceof Element && !nodeNameEquals(node, DESCRIPTION_ELEMENT)) {
				/**
				 * 注：遍历所有集合标签内非<description>节点，解析值
				 * - 这里又会递归回去，解析各种值的标签
				 */
				target.add(parsePropertySubElement((Element) node, bd, defaultElementType));
			}
		}
	}

	/**
	 * Parse a map element.
	 * 注：解析<map>标签
	 */
	public Map<Object, Object> parseMapElement(Element mapEle, @Nullable BeanDefinition bd) {
		// 注：获取<map>标签上的"key-type"属性，表示这个map的Key默认类型(用于解析<value>值标签)
		String defaultKeyType = mapEle.getAttribute(KEY_TYPE_ATTRIBUTE);
		// 注：获取<map>标签上的"value-type"属性，表示这个map的Value默认类型(用于解析<value>值标签)
		String defaultValueType = mapEle.getAttribute(VALUE_TYPE_ATTRIBUTE);

		// 注：获取<map>标签内的所有<entry>标签，即为map的映射项
		List<Element> entryEles = DomUtils.getChildElementsByTagName(mapEle, ENTRY_ELEMENT);
		// 注：根据map大小来实例化ManagedMap实例-用于管理映射元素
		ManagedMap<Object, Object> map = new ManagedMap<>(entryEles.size());
		map.setSource(extractSource(mapEle));
		map.setKeyTypeName(defaultKeyType);
		map.setValueTypeName(defaultValueType);
		// 注：根据<map>标签上的"merge“属性，默认为false
		map.setMergeEnabled(parseMergeAttribute(mapEle));

		for (Element entryEle : entryEles) {
			// Should only have one value child element: ref, value, list, etc.
			// Optionally, there might be a key child element.
			/**
			 * 注：遍历map的所有映射项，即<entry>标签。
			 * <entry>标签中仅允许存在一个表示值的标签，比如<ref>、<value>等
			 * <entry>标签中可以指定一个<key>标签的子元素【可选】。
			 * - 下面遍历<entry>内部的子元素来解析出<key>标签(keyEle，可能有)以及表示值的标签(valueEle)
			 */
			NodeList entrySubNodes = entryEle.getChildNodes();
			Element keyEle = null;		// 注：表示key的<key>标签
			Element valueEle = null;	// 注：表示value的标签，如<ref>、<value>等
			for (int j = 0; j < entrySubNodes.getLength(); j++) {
				Node node = entrySubNodes.item(j);
				if (node instanceof Element) {
					Element candidateEle = (Element) node;
					if (nodeNameEquals(candidateEle, KEY_ELEMENT)) {
						if (keyEle != null) {
							// 注：<entry>标签中不允许存在多个<key>标签
							error("<entry> element is only allowed to contain one <key> sub-element", entryEle);
						}
						else {
							keyEle = candidateEle;
						}
					}
					else {
						// Child element is what we're looking for.
						if (nodeNameEquals(candidateEle, DESCRIPTION_ELEMENT)) {
							// the element is a <description> -> ignore it
							// 注：忽略用于表示描述信息的<description>标签
						}
						else if (valueEle != null) {
							// 注：不允许存在多个表示值的标签
							error("<entry> element must not contain more than one value sub-element", entryEle);
						}
						else {
							// 注：<entry>标签中除了<key>、<description>之外的标签认为是表示值的标签。
							valueEle = candidateEle;
						}
					}
				}
			}

			// Extract key from attribute or sub-element.
			/**
			 * 注：下面从<entry>标签的属性或其字标签<key>中解析出映射的Key值
			 */
			Object key = null;
			// 注：判断<entry>标签是否存在"key"或者"key-ref"属性。
			boolean hasKeyAttribute = entryEle.hasAttribute(KEY_ATTRIBUTE);
			boolean hasKeyRefAttribute = entryEle.hasAttribute(KEY_REF_ATTRIBUTE);
			if ((hasKeyAttribute && hasKeyRefAttribute) ||
					(hasKeyAttribute || hasKeyRefAttribute) && keyEle != null) {
				// 注：当前<entry>表示key值的属性或子标签仅能有一个。
				error("<entry> element is only allowed to contain either " +
						"a 'key' attribute OR a 'key-ref' attribute OR a <key> sub-element", entryEle);
			}
			if (hasKeyAttribute) {
				/**
				 * 注：解析<entry>标签的"key"属性
				 * 将"key"属性值构造为具有类型的String对象-即TypedStringValue对象
				 * 注意这里类型会采用<map>标签上通过"key-type"配置的默认KEY类型
				 */
				key = buildTypedStringValueForMap(entryEle.getAttribute(KEY_ATTRIBUTE), defaultKeyType, entryEle);
			}
			else if (hasKeyRefAttribute) {
				/**
				 * 注：解析<entry>标签的"key-ref"属性
				 * 将"key-ref"属性值解析为运行时bean实例引用对象
				 */
				String refName = entryEle.getAttribute(KEY_REF_ATTRIBUTE);
				if (!StringUtils.hasText(refName)) {
					// 注：key-ref是引用的bean名称，不允许为空!
					error("<entry> element contains empty 'key-ref' attribute", entryEle);
				}
				// 注：暂时使用运行时bean实例引用对象来占位
				RuntimeBeanReference ref = new RuntimeBeanReference(refName);
				ref.setSource(extractSource(entryEle));
				key = ref;
			}
			else if (keyEle != null) {
				/**
				 * 注：解析<entry>标签内的<key>标签来解析key值
				 * <key>标签无属性，通过解析其内部表示值的子标签(最多一个)解析其值，如<ref>、<value>
				 * 注意<key>标签如果无子标签时，表示Key值为null
				 */
				key = parseKeyElement(keyEle, bd, defaultKeyType);
			}
			else {
				// 注：map必须指定key值，要么通过属性要么通过<key>标签
				error("<entry> element must specify a key", entryEle);
			}

			// Extract value from attribute or sub-element.
			/**
			 * 注：下面从<entry>标签的属性或其字标签<key>中解析出映射的Key值
			 */
			Object value = null;
			// 注：判断<entry>标签是否存在"value"属性、"value-ref"属性、“value-type”属性。
			boolean hasValueAttribute = entryEle.hasAttribute(VALUE_ATTRIBUTE);
			boolean hasValueRefAttribute = entryEle.hasAttribute(VALUE_REF_ATTRIBUTE);
			boolean hasValueTypeAttribute = entryEle.hasAttribute(VALUE_TYPE_ATTRIBUTE);
			if ((hasValueAttribute && hasValueRefAttribute) ||
					(hasValueAttribute || hasValueRefAttribute) && valueEle != null) {
				// 注：当前<entry>表示value值的属性或子标签仅能有一个。
				error("<entry> element is only allowed to contain either " +
						"'value' attribute OR 'value-ref' attribute OR <value> sub-element", entryEle);
			}
			if ((hasValueTypeAttribute && hasValueRefAttribute) ||
				(hasValueTypeAttribute && !hasValueAttribute) ||
					(hasValueTypeAttribute && valueEle != null)) {
				// 注：<entry>标签上的"value-type"仅允许在指定"value"属性值时使用
				// 这块判断有点烂，只需要中间一个条件即可吧
				error("<entry> element is only allowed to contain a 'value-type' " +
						"attribute when it has a 'value' attribute", entryEle);
			}
			if (hasValueAttribute) {
				/**
				 * 注：解析<entry>标签的"value"属性
				 * 将"value"属性值构造为具有类型的String对象-即TypedStringValue对象
				 * 注意这里类型会优先采用当前<entry>标签上"value-type"属性配置的类型，其次采用<map>标签上通过"value-type"配置的默认KEY类型
				 */
				String valueType = entryEle.getAttribute(VALUE_TYPE_ATTRIBUTE);
				if (!StringUtils.hasText(valueType)) {
					valueType = defaultValueType;
				}
				value = buildTypedStringValueForMap(entryEle.getAttribute(VALUE_ATTRIBUTE), valueType, entryEle);
			}
			else if (hasValueRefAttribute) {
				/**
				 * 注：解析<entry>标签的"value-ref"属性
				 * 将"value-ref"属性值解析为运行时bean实例引用对象
				 */
				String refName = entryEle.getAttribute(VALUE_REF_ATTRIBUTE);
				if (!StringUtils.hasText(refName)) {
					// 注：value-ref是引用的bean名称，不允许为空!
					error("<entry> element contains empty 'value-ref' attribute", entryEle);
				}
				RuntimeBeanReference ref = new RuntimeBeanReference(refName);
				ref.setSource(extractSource(entryEle));
				value = ref;
			}
			else if (valueEle != null) {
				// 注：解析<entry>标签内表示值的字标签，比如<ref>、<value>等
				value = parsePropertySubElement(valueEle, bd, defaultValueType);
			}
			else {
				//注：map必须指定value值，要么通过属性要么通过子标签
				error("<entry> element must specify a value", entryEle);
			}

			// Add final key and value to the Map.
			// 注：将最终解析后的key值和value值添加到map映射中
			map.put(key, value);
		}

		return map;
	}

	/**
	 * Build a typed String value Object for the given raw value.
	 * @see org.springframework.beans.factory.config.TypedStringValue
	 */
	protected final Object buildTypedStringValueForMap(String value, String defaultTypeName, Element entryEle) {
		try {
			TypedStringValue typedValue = buildTypedStringValue(value, defaultTypeName);
			typedValue.setSource(extractSource(entryEle));
			return typedValue;
		}
		catch (ClassNotFoundException ex) {
			error("Type class [" + defaultTypeName + "] not found for Map key/value type", entryEle, ex);
			return value;
		}
	}

	/**
	 * Parse a key sub-element of a map element.
	 * 注：解析<map>标签的子<key>标签值
	 */
	@Nullable
	protected Object parseKeyElement(Element keyEle, @Nullable BeanDefinition bd, String defaultKeyTypeName) {
		// 注：通过<key>标签内的表示值的子标签来解析Key值；<key>标签没有属性
		NodeList nl = keyEle.getChildNodes();
		Element subElement = null;
		for (int i = 0; i < nl.getLength(); i++) {
			Node node = nl.item(i);
			if (node instanceof Element) {
				// Child element is what we're looking for.
				if (subElement != null) {
					// 注：<key>内表示值的子标签仅允许存在一个
					error("<key> element must not contain more than one value sub-element", keyEle);
				}
				else {
					subElement = (Element) node;
				}
			}
		}
		if (subElement == null) {
			// 注：不存在子标签，则Key值为null
			return null;
		}
		// 注：解析表示属性值的子标签
		return parsePropertySubElement(subElement, bd, defaultKeyTypeName);
	}

	/**
	 * Parse a props element.
	 * 注：解析<props>标签，该标签是填充Properties类型对象的值。
	 */
	public Properties parsePropsElement(Element propsEle) {
		// 注：根据长度来实例化ManagedProperties实例-用于Properties的KV数据
		ManagedProperties props = new ManagedProperties();
		props.setSource(extractSource(propsEle));
		// 注：根据<props>标签上的"merge“属性，默认为false
		props.setMergeEnabled(parseMergeAttribute(propsEle));

		// 注：获取<props>标签内所有表示kv配置的<prop>标签
		List<Element> propEles = DomUtils.getChildElementsByTagName(propsEle, PROP_ELEMENT);
		for (Element propEle : propEles) {
			// 注：获取<prop>标签的“key”属性值，即Key
			String key = propEle.getAttribute(KEY_ATTRIBUTE);
			// Trim the text value to avoid unwanted whitespace
			// caused by typical XML formatting.
			/**
			 * 注：获取<prop>标签内部的文本作为value值，实际还是使用了TypedStringValue类型，但实际类型就是String。
			 * 为了避免非意料之外的空格，这里会使用trim进行去除文本的前后空格。
 			 */
			String value = DomUtils.getTextValue(propEle).trim();
			TypedStringValue keyHolder = new TypedStringValue(key);
			keyHolder.setSource(extractSource(propEle));
			TypedStringValue valueHolder = new TypedStringValue(value);
			valueHolder.setSource(extractSource(propEle));
			props.put(keyHolder, valueHolder);
		}

		return props;
	}

	/**
	 * Parse the merge attribute of a collection element, if any.
	 * 注：解析集合元素的“merge”属性
	 */
	public boolean parseMergeAttribute(Element collectionElement) {
		// 注：获取集合元素上的“merge”属性
		String value = collectionElement.getAttribute(MERGE_ATTRIBUTE);
		if (isDefaultValue(value)) {
			// 注：如果当前标签未指定“merge”属性或为[default]值,则采用<beans>上的默认合并属性。默认为false
			value = this.defaults.getMerge();
		}
		// 注：只有配置为true，才表示需要合并
		return TRUE_VALUE.equals(value);
	}

	/**
	 * Parse a custom element (outside of the default namespace).
	 * 注：解析自定义标签(非默认命名空间的标签)
	 * @param ele the element to parse
	 * @return the resulting bean definition
	 */
	@Nullable
	public BeanDefinition parseCustomElement(Element ele) {
		return parseCustomElement(ele, null);
	}

	/**
	 * Parse a custom element (outside of the default namespace).
	 * 注：解析自定义标签(非默认命名空间的标签)
	 * @param ele the element to parse
	 * @param containingBd the containing bean definition (if any)
	 * @return the resulting bean definition
	 */
	@Nullable
	public BeanDefinition parseCustomElement(Element ele, @Nullable BeanDefinition containingBd) {
		// 注：获取当前标签的命名空间uri(资源定位符)
		String namespaceUri = getNamespaceURI(ele);
		if (namespaceUri == null) {
			// 注：不存在命名空间，无法解析bean定义返回null
			return null;
		}
		/**
		 * 注：先从上下文中获取命名空间处理器解析器，默认为DefaultNamespaceHandlerResolver
		 *  然后再通过命名空间来解析返回命名空间处理器
		 */
		NamespaceHandler handler = this.readerContext.getNamespaceHandlerResolver().resolve(namespaceUri);
		if (handler == null) {
			// 注：如果解析命名空间处理器，无法解析bean定义，返回null
			error("Unable to locate Spring NamespaceHandler for XML schema namespace [" + namespaceUri + "]", ele);
			return null;
		}
		// 注：通过命名空间处理器来解析该标签，解析所需要的上下文、代理解析器、外部bean定义都封装到了ParserContext实例中\
		// TODO: 2023/11/17 继续
		return handler.parse(ele, new ParserContext(this.readerContext, this, containingBd));
	}

	/**
	 * Decorate the given bean definition through a namespace handler, if applicable.
	 * @param ele the current element
	 * @param originalDef the current bean definition
	 * @return the decorated bean definition
	 */
	public BeanDefinitionHolder decorateBeanDefinitionIfRequired(Element ele, BeanDefinitionHolder originalDef) {
		return decorateBeanDefinitionIfRequired(ele, originalDef, null);
	}

	/**
	 * Decorate the given bean definition through a namespace handler, if applicable.
	 * 注：通过命名空间处理器来装饰指定的bean定义。
	 * - 对于<bean>标签上自定义的属性以及内部自定义的标签都会在这里通过自定义的命名空间处理器进行解析，并作用在bean定义上。
	 * 参考：https://zhuanlan.zhihu.com/p/658190222
	 * @param ele the current element
	 * @param originalDef the current bean definition
	 * @param containingBd the containing bean definition (if any)
	 * @return the decorated bean definition
	 */
	public BeanDefinitionHolder decorateBeanDefinitionIfRequired(
			Element ele, BeanDefinitionHolder originalDef, @Nullable BeanDefinition containingBd) {

		// 注：<bean>标签在原spring命名空间内解析的bean定义实例(封装后)
		BeanDefinitionHolder finalDefinition = originalDef;

		// Decorate based on custom attributes first.
		// 注：首先基于自定义的属性来进行装饰
		NamedNodeMap attributes = ele.getAttributes();
		for (int i = 0; i < attributes.getLength(); i++) {
			Node node = attributes.item(i);
			// 注：遍历<bean>标签每一个属性，根据是否为默认命名空间，即自定义属性，来判断是否进行装饰
			finalDefinition = decorateIfRequired(node, finalDefinition, containingBd);
		}

		// Decorate based on custom nested elements.
		// 注：基于自定义的嵌套元素来进行装饰
		NodeList children = ele.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node node = children.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE) {	// 注：节点类型为元素节点
				// 注：遍历<bean>标签每一个节点，根据是否为默认命名空间，即自定义标签节点，来判断是否进行装饰
				finalDefinition = decorateIfRequired(node, finalDefinition, containingBd);
			}
		}
		return finalDefinition;
	}

	/**
	 * Decorate the given bean definition through a namespace handler,
	 * if applicable.
	 * 注：通过命名空间处理器处理<bean>标签上的自定义属性|节点装饰指定的bean定义。
	 * @param node the current child node
	 * @param originalDef the current bean definition
	 * @param containingBd the containing bean definition (if any)
	 * @return the decorated bean definition
	 */
	public BeanDefinitionHolder decorateIfRequired(
			Node node, BeanDefinitionHolder originalDef, @Nullable BeanDefinition containingBd) {

		// 注：获取当前属性节点的命名空间
		String namespaceUri = getNamespaceURI(node);
		if (namespaceUri != null && !isDefaultNamespace(namespaceUri)) {	// 注：存在命名空间，且非spring默认命名空间
			// 注：从bean定义读取上下文中获取命名空间处理解析器，并返回当前命名空间处理器
			NamespaceHandler handler = this.readerContext.getNamespaceHandlerResolver().resolve(namespaceUri);
			if (handler != null) {
				// 注：存在命名空间处理器，调用decorate方法进行对原bean定义进行装饰
				BeanDefinitionHolder decorated =
						handler.decorate(node, originalDef, new ParserContext(this.readerContext, this, containingBd));
				if (decorated != null) {
					// 注：返回decorate方法返回的非null值
					return decorated;
				}
			}
			else if (namespaceUri.startsWith("http://www.springframework.org/schema/")) {
				// 注：未能找到处理器，且为spring的命名空间，抛出异常
				error("Unable to locate Spring NamespaceHandler for XML schema namespace [" + namespaceUri + "]", node);
			}
			else {
				// A custom namespace, not to be handled by Spring - maybe "xml:...".
				// 注：未能找到处理器，且为自定义命名空间，过滤即可
				if (logger.isDebugEnabled()) {
					logger.debug("No Spring NamespaceHandler found for XML schema namespace [" + namespaceUri + "]");
				}
			}
		}
		return originalDef;
	}

	@Nullable
	private BeanDefinitionHolder parseNestedCustomElement(Element ele, @Nullable BeanDefinition containingBd) {
		BeanDefinition innerDefinition = parseCustomElement(ele, containingBd);
		if (innerDefinition == null) {
			error("Incorrect usage of element '" + ele.getNodeName() + "' in a nested manner. " +
					"This tag cannot be used nested inside <property>.", ele);
			return null;
		}
		String id = ele.getNodeName() + BeanDefinitionReaderUtils.GENERATED_BEAN_NAME_SEPARATOR +
				ObjectUtils.getIdentityHexString(innerDefinition);
		if (logger.isTraceEnabled()) {
			logger.trace("Using generated bean name [" + id +
					"] for nested custom element '" + ele.getNodeName() + "'");
		}
		return new BeanDefinitionHolder(innerDefinition, id);
	}


	/**
	 * Get the namespace URI for the supplied node.
	 * <p>The default implementation uses {@link Node#getNamespaceURI}.
	 * Subclasses may override the default implementation to provide a
	 * different namespace identification mechanism.
	 * @param node the node
	 */
	@Nullable
	public String getNamespaceURI(Node node) {
		return node.getNamespaceURI();
	}

	/**
	 * Get the local name for the supplied {@link Node}.
	 * <p>The default implementation calls {@link Node#getLocalName}.
	 * Subclasses may override the default implementation to provide a
	 * different mechanism for getting the local name.
	 * @param node the {@code Node}
	 */
	public String getLocalName(Node node) {
		return node.getLocalName();
	}

	/**
	 * Determine whether the name of the supplied node is equal to the supplied name.
	 * <p>The default implementation checks the supplied desired name against both
	 * {@link Node#getNodeName()} and {@link Node#getLocalName()}.
	 * <p>Subclasses may override the default implementation to provide a different
	 * mechanism for comparing node names.
	 * @param node the node to compare
	 * @param desiredName the name to check for
	 */
	public boolean nodeNameEquals(Node node, String desiredName) {
		return desiredName.equals(node.getNodeName()) || desiredName.equals(getLocalName(node));
	}

	/**
	 * Determine whether the given URI indicates the default namespace.
	 */
	public boolean isDefaultNamespace(@Nullable String namespaceUri) {
		return (!StringUtils.hasLength(namespaceUri) || BEANS_NAMESPACE_URI.equals(namespaceUri));
	}

	/**
	 * Determine whether the given node indicates the default namespace.
	 * 注：判断指定的DOM节点是否为默认的命名空间
	 */
	public boolean isDefaultNamespace(Node node) {
		/**
		 * 注：根据当前节点的xmlns属性来判断是否为默认命名空间
		 * 1. 未指定xmlns属性 【所以，beans标签后面一大串真的有必要指定嘛】
		 * 2. 指定xmlns属性值为：http://www.springframework.org/schema/beans
 		 */
		return isDefaultNamespace(getNamespaceURI(node));
	}

	private boolean isDefaultValue(String value) {
		return (DEFAULT_VALUE.equals(value) || "".equals(value));
	}

	private boolean isCandidateElement(Node node) {
		return (node instanceof Element && (isDefaultNamespace(node) || !isDefaultNamespace(node.getParentNode())));
	}

}
