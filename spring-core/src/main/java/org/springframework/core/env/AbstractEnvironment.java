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

package org.springframework.core.env;

import java.security.AccessControlException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.core.SpringProperties;
import org.springframework.core.convert.support.ConfigurableConversionService;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * Abstract base class for {@link Environment} implementations. Supports the notion of
 * reserved default profile names and enables specifying active and default profiles
 * through the {@link #ACTIVE_PROFILES_PROPERTY_NAME} and
 * {@link #DEFAULT_PROFILES_PROPERTY_NAME} properties.
 * 注：Environment的抽象基本实现类。支持预先设定的默认描述信息名，并且能够指通过系统配置指定激活的描述信息或默认描述信息。
 * - spring.profiles.active：激活的描述信息属性key
 * - spring.profiles.default：默认的描述信息属性key
 *
 * <p>Concrete subclasses differ primarily on which {@link PropertySource} objects they
 * add by default. {@code AbstractEnvironment} adds none. Subclasses should contribute
 * property sources through the protected {@link #customizePropertySources(MutablePropertySources)}
 * hook, while clients should customize using {@link ConfigurableEnvironment#getPropertySources()}
 * and working against the {@link MutablePropertySources} API.
 * See {@link ConfigurableEnvironment} javadoc for usage examples.
 * 注：该环境抽象类的子类区别主要在于默认的属性源。
 * AbstractEnvironment不会添加任何默认属性源。则其子类需要实现customizePropertySources方法设置默认属性源。
 * 而用户应该通过getPropertySources方法获取可变多数据源对象后，通过MutablePropertySources相关api去添加自定义的数据源。
 * 具体可参考ConfigurableEnvironment#getPropertySources的使用示例(具备优先级顺序)。
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @since 3.1
 * @see ConfigurableEnvironment
 * @see StandardEnvironment
 */
public abstract class AbstractEnvironment implements ConfigurableEnvironment {

	/**
	 * System property that instructs Spring to ignore system environment variables,
	 * i.e. to never attempt to retrieve such a variable via {@link System#getenv()}.
	 * <p>The default is "false", falling back to system environment variable checks if a
	 * Spring environment property (e.g. a placeholder in a configuration String) isn't
	 * resolvable otherwise. Consider switching this flag to "true" if you experience
	 * log warnings from {@code getenv} calls coming from Spring, e.g. on WebSphere
	 * with strict SecurityManager settings and AccessControlExceptions warnings.
	 * 注：该系统属性key只是spring是否忽略系统环境变量。比如不再尝试去通过System#geten检索环境变量。
	 * 默认配置为false，即会检查系统环境属性(比如配置字符串上的占位符)是否可以被解析。
	 * 如果你遇到spring在调用getenv的日志告警时可以考虑将该标识置位true。比如在具有特别严格的安全控制系统上。
	 * - 日志参考：getSystemEnvironment中的getSystemAttribute方法中。
	 * @see #suppressGetenvAccess()
	 */
	public static final String IGNORE_GETENV_PROPERTY_NAME = "spring.getenv.ignore";

	/**
	 * Name of property to set to specify active profiles: {@value}. Value may be comma
	 * delimited.
	 * 注：用于指定激活描述信息的属性名，多个激活描述信息名可能通过逗号分隔。
	 * <p>Note that certain shell environments such as Bash disallow the use of the period
	 * character in variable names. Assuming that Spring's {@link SystemEnvironmentPropertySource}
	 * is in use, this property may be specified as an environment variable as
	 * {@code SPRING_PROFILES_ACTIVE}.
	 * 注：在一些特定的shell环境(如Bash)中不允许使用句点符号。
	 * 如果Spring使用SystemEnvironmentPropertySource，则该属性可指定为环境变量(SPRING_PROFILES_ACTIVE)
	 * @see ConfigurableEnvironment#setActiveProfiles
	 */
	public static final String ACTIVE_PROFILES_PROPERTY_NAME = "spring.profiles.active";

	/**
	 * Name of property to set to specify profiles active by default: {@value}. Value may
	 * be comma delimited.
	 * 注：用于设置默认激活描述信息的属性名，多个默认激活描述信息名可能通过逗号分隔。
	 * <p>Note that certain shell environments such as Bash disallow the use of the period
	 * character in variable names. Assuming that Spring's {@link SystemEnvironmentPropertySource}
	 * is in use, this property may be specified as an environment variable as
	 * {@code SPRING_PROFILES_DEFAULT}.
	 * 注：在一些特定的shell环境(如Bash)中不允许使用句点符号。
	 * 如果Spring使用SystemEnvironmentPropertySource，则该属性可指定为环境变量(SPRING_PROFILES_DEFAULT)
	 * @see ConfigurableEnvironment#setDefaultProfiles
	 */
	public static final String DEFAULT_PROFILES_PROPERTY_NAME = "spring.profiles.default";

	/**
	 * Name of reserved default profile name: {@value}. If no default profile names are
	 * explicitly and no active profile names are explicitly set, this profile will
	 * automatically be activated by default.
	 * 预设默认激活描述信息名-default。
	 * 如果没有激活描述信息以及默认描述信息指定，则默认激活default描述。
	 * @see #getReservedDefaultProfiles
	 * @see ConfigurableEnvironment#setDefaultProfiles
	 * @see ConfigurableEnvironment#setActiveProfiles
	 * @see AbstractEnvironment#DEFAULT_PROFILES_PROPERTY_NAME
	 * @see AbstractEnvironment#ACTIVE_PROFILES_PROPERTY_NAME
	 */
	protected static final String RESERVED_DEFAULT_PROFILE_NAME = "default";


	protected final Log logger = LogFactory.getLog(getClass());

	// 注：缓存激活描述信息的集合
	private final Set<String> activeProfiles = new LinkedHashSet<>();

	// 注：缓存默认描述信息的集合。初始化就包括“default”
	private final Set<String> defaultProfiles = new LinkedHashSet<>(getReservedDefaultProfiles());

	// 注：可变多属性源，支持属性源搜索优先级
	private final MutablePropertySources propertySources = new MutablePropertySources();

	// 注：属性源属性解析器，用于解析属性值、解析并替换属性值中的占位符以及支持属性值转换到各种类型
	// 初始化需要传入属性源。【多属性源：管理属性源；属性解析器：解析属性值】
	private final ConfigurablePropertyResolver propertyResolver =
			new PropertySourcesPropertyResolver(this.propertySources);


	/**
	 * Create a new {@code Environment} instance, calling back to
	 * {@link #customizePropertySources(MutablePropertySources)} during construction to
	 * allow subclasses to contribute or manipulate {@link PropertySource} instances as
	 * appropriate.
	 * 注：创建一个新的Environment实例。
	 * - 在环境对象创建过程中会调用customizePropertySources方法允许子类在合适的情况下增加或修改可变多属性源实例。
	 * @see #customizePropertySources(MutablePropertySources)
	 */
	public AbstractEnvironment() {
		// 注：调用子类处理属性源的动作
		customizePropertySources(this.propertySources);
	}


	/**
	 * Customize the set of {@link PropertySource} objects to be searched by this
	 * {@code Environment} during calls to {@link #getProperty(String)} and related
	 * methods.
	 * 注：自定义环境对象在调用getProperty相关方法获取属性值时寻找的属性源对象集合。
	 *
	 * <p>Subclasses that override this method are encouraged to add property
	 * sources using {@link MutablePropertySources#addLast(PropertySource)} such that
	 * further subclasses may call {@code super.customizePropertySources()} with
	 * predictable results. For example:
	 * // 注：建议重写该方法的子类使用可变属性源的addLast方法来增加属性源，以便于其子类可以调用super.customizePropertySources并获得可预测的结果。
	 * <pre class="code">
	 * public class Level1Environment extends AbstractEnvironment {
	 *     &#064;Override
	 *     protected void customizePropertySources(MutablePropertySources propertySources) {
	 *         super.customizePropertySources(propertySources); // no-op from base class
	 *         propertySources.addLast(new PropertySourceA(...));
	 *         propertySources.addLast(new PropertySourceB(...));
	 *     }
	 * }
	 *
	 * public class Level2Environment extends Level1Environment {
	 *     &#064;Override
	 *     protected void customizePropertySources(MutablePropertySources propertySources) {
	 *         super.customizePropertySources(propertySources); // add all from superclass
	 *         propertySources.addLast(new PropertySourceC(...));
	 *         propertySources.addLast(new PropertySourceD(...));
	 *     }
	 * }
	 * </pre>
	 * In this arrangement, properties will be resolved against sources A, B, C, D in that
	 * order. That is to say that property source "A" has precedence over property source
	 * "D". If the {@code Level2Environment} subclass wished to give property sources C
	 * and D higher precedence than A and B, it could simply call
	 * {@code super.customizePropertySources} after, rather than before adding its own:
	 * 注：在这样的安排中，属性将按照源A、B、C、D的顺序进行解析。也就是说，属性源A的优先级高于属性源D。
	 * 如果子类希望其C\D的优先级高于父类的A\B，那么它可以在调用super.customizePropertySources之后增加其自己的属性源。
	 * <pre class="code">
	 * public class Level2Environment extends Level1Environment {
	 *     &#064;Override
	 *     protected void customizePropertySources(MutablePropertySources propertySources) {
	 *         propertySources.addLast(new PropertySourceC(...));
	 *         propertySources.addLast(new PropertySourceD(...));
	 *         super.customizePropertySources(propertySources); // add all from superclass
	 *     }
	 * }
	 * </pre>
	 * The search order is now C, D, A, B as desired.
	 *
	 * <p>Beyond these recommendations, subclasses may use any of the {@code add&#42;},
	 * {@code remove}, or {@code replace} methods exposed by {@link MutablePropertySources}
	 * in order to create the exact arrangement of property sources desired.
	 * 注：在这些建议之外，子类可以也会使用可变多属性源的add\remove\replace等方法来实现创建精确的属性源排列顺序。
	 *
	 * <p>The base implementation registers no property sources.
	 * 注：默认的基本实现没有注册任何属性源
	 *
	 * <p>Note that clients of any {@link ConfigurableEnvironment} may further customize
	 * property sources via the {@link #getPropertySources()} accessor, typically within
	 * an {@link org.springframework.context ApplicationContextInitializer
	 * ApplicationContextInitializer}. For example:
	 * 注：任何可配置环境的用户可以通过getPropertySources方法进一步自定义属性源。典型情况是在应用上下文中的初始化。
	 * <pre class="code">
	 * ConfigurableEnvironment env = new StandardEnvironment();
	 * env.getPropertySources().addLast(new PropertySourceX(...));
	 * </pre>
	 *
	 * <h2>A warning about instance variable access</h2>
	 * Instance variables declared in subclasses and having default initial values should
	 * <em>not</em> be accessed from within this method. Due to Java object creation
	 * lifecycle constraints, any initial value will not yet be assigned when this
	 * callback is invoked by the {@link #AbstractEnvironment()} constructor, which may
	 * lead to a {@code NullPointerException} or other problems. If you need to access
	 * default values of instance variables, leave this method as a no-op and perform
	 * property source manipulation and instance variable access directly within the
	 * subclass constructor. Note that <em>assigning</em> values to instance variables is
	 * not problematic; it is only attempting to read default values that must be avoided.
	 * 注：关于实例变量访问的警告
	 * - 在当前子类中重写的当前方法中不应该试图去访问子类的实例变量的默认值。
	 * - 由于JAVA对象创建机制问题，在当前方法中不会为任何实例变量进行赋值，因此就很容易导致空指针或其他问题。
	 * - 如果你需要去访问实例变量的默认值，请保持当前方法为无操作状态，并且在子类的构造函数中执行属性源操作以及实例变量的访问。
	 * - 注意在该方法中为实例变量赋值是没有问题的，仅仅要避免试图读取其默认值。
	 *
	 * @see MutablePropertySources
	 * @see PropertySourcesPropertyResolver
	 * @see org.springframework.context ApplicationContextInitializer
	 */
	protected void customizePropertySources(MutablePropertySources propertySources) {
	}

	/**
	 * Return the set of reserved default profile names. This implementation returns
	 * {@value #RESERVED_DEFAULT_PROFILE_NAME}. Subclasses may override in order to
	 * customize the set of reserved names.
	 * 注：返回预设描述信息名。该方法默认实现返回RESERVED_DEFAULT_PROFILE_NAME属性值。
	 * 子类可能会重写该方法，已达到自定义预设默认描述信息的目的。
	 * @see #RESERVED_DEFAULT_PROFILE_NAME
	 * @see #doGetDefaultProfiles()
	 */
	protected Set<String> getReservedDefaultProfiles() {
		return Collections.singleton(RESERVED_DEFAULT_PROFILE_NAME);
	}


	//---------------------------------------------------------------------
	// Implementation of ConfigurableEnvironment interface
	//---------------------------------------------------------------------

	// 注：该方法会返回当前环境所有激活状态的描述信息。
	@Override
	public String[] getActiveProfiles() {
		return StringUtils.toStringArray(doGetActiveProfiles());
	}

	/**
	 * Return the set of active profiles as explicitly set through
	 * {@link #setActiveProfiles} or if the current set of active profiles
	 * is empty, check for the presence of the {@value #ACTIVE_PROFILES_PROPERTY_NAME}
	 * property and assign its value to the set of active profiles.
	 * 注：该方法会返回显示通过setActiveProfiles方法设置的激活描述信息集合。
	 * - 如果当前激活描述信息为空，就检查“spring.profiles.active”属性的存在性，并将该属性值赋值给激活描述信息
	 * @see #getActiveProfiles()
	 * @see #ACTIVE_PROFILES_PROPERTY_NAME
	 */
	protected Set<String> doGetActiveProfiles() {
		// 注：这里应该双重判断，效率会更好一些吧
		synchronized (this.activeProfiles) {
			if (this.activeProfiles.isEmpty()) {
				// 注：如果激活描述信息为空，检查“spring.profiles.active”属性的存在性及其值
				String profiles = getProperty(ACTIVE_PROFILES_PROPERTY_NAME);
				if (StringUtils.hasText(profiles)) {
					// 注：如果存在激活属性值，则以逗号分隔，并设置为激活描述信息
					setActiveProfiles(StringUtils.commaDelimitedListToStringArray(
							StringUtils.trimAllWhitespace(profiles)));
				}
			}
			return this.activeProfiles;
		}
	}

	// 注：指定当前环境激活的一系列描述信息。
	@Override
	public void setActiveProfiles(String... profiles) {
		Assert.notNull(profiles, "Profile array must not be null");
		if (logger.isDebugEnabled()) {
			logger.debug("Activating profiles " + Arrays.asList(profiles));
		}
		synchronized (this.activeProfiles) {
			// 注：以当前显示赋值的激活描述信息为准
			this.activeProfiles.clear();
			for (String profile : profiles) {
				// 注：验证描述信息的合法性，并添加到激活描述信息集合中
				validateProfile(profile);
				this.activeProfiles.add(profile);
			}
		}
	}

	// 注：将指定的描述信息添加到激活描述信息集合中
	@Override
	public void addActiveProfile(String profile) {
		if (logger.isDebugEnabled()) {
			logger.debug("Activating profile '" + profile + "'");
		}
		// 注：验证描述信息的合法性
		validateProfile(profile);
		// 注：这里调用获取激活描述信息的目的，是为了初始化激活描述信息(可能会执行根据属性来初始化激活描述信息集合)
		doGetActiveProfiles();
		synchronized (this.activeProfiles) {
			// 注：将该描述信息添加到激活描述信息集合中
			this.activeProfiles.add(profile);
		}
	}

	// 注：如果没有描述信息显示指定为激活状态，那么该方法返回的默认描述信息将会被自动激活。
	@Override
	public String[] getDefaultProfiles() {
		return StringUtils.toStringArray(doGetDefaultProfiles());
	}

	/**
	 * Return the set of default profiles explicitly set via
	 * {@link #setDefaultProfiles(String...)} or if the current set of default profiles
	 * consists only of {@linkplain #getReservedDefaultProfiles() reserved default
	 * profiles}, then check for the presence of the
	 * {@value #DEFAULT_PROFILES_PROPERTY_NAME} property and assign its value (if any)
	 * to the set of default profiles.
	 * 注：该方法会返回显示通过setDefaultProfiles方法设置的默认描述信息集合。
	 * - 如果当前默认描述信息仅仅只有getReservedDefaultProfiles保留描述信息，
	 * 就检查“spring.profiles.default”属性的存在性，并将该属性值赋值给激活描述信息
	 * @see #AbstractEnvironment()
	 * @see #getDefaultProfiles()
	 * @see #DEFAULT_PROFILES_PROPERTY_NAME
	 * @see #getReservedDefaultProfiles()
	 */
	protected Set<String> doGetDefaultProfiles() {
		// 注：这里应该双重判断，效率会更好一些吧
		synchronized (this.defaultProfiles) {
			if (this.defaultProfiles.equals(getReservedDefaultProfiles())) {
				// 注：如果默认描述信息仅只有预留描述信息，检查“spring.profiles.default”属性的存在性及其值
				String profiles = getProperty(DEFAULT_PROFILES_PROPERTY_NAME);
				if (StringUtils.hasText(profiles)) {
					// 注：如果存在默认属性值，则以逗号分隔，并设置为默认描述信息
					setDefaultProfiles(StringUtils.commaDelimitedListToStringArray(
							StringUtils.trimAllWhitespace(profiles)));
				}
			}
			return this.defaultProfiles;
		}
	}

	/**
	 * Specify the set of profiles to be made active by default if no other profiles
	 * are explicitly made active through {@link #setActiveProfiles}.
	 * <p>Calling this method removes overrides any reserved default profiles
	 * that may have been added during construction of the environment.
	 * 注：通过该方法指定一系列默认应用描述信息，如果用户没有明确通过setActiveProfiles方法来设置激活描述信息，则会采用默认描述信息为激活状态。
	 * - 调用该方法将删除任何在环境对象创建过程中保留的默认描述信息。
	 * @see #AbstractEnvironment()
	 * @see #getReservedDefaultProfiles()
	 */
	@Override
	public void setDefaultProfiles(String... profiles) {
		Assert.notNull(profiles, "Profile array must not be null");
		synchronized (this.defaultProfiles) {
			// 注：以当前显示赋值的默认描述信息为准
			this.defaultProfiles.clear();
			for (String profile : profiles) {
				// 注：验证描述信息的合法性，并添加到默认描述信息集合中
				validateProfile(profile);
				this.defaultProfiles.add(profile);
			}
		}
	}

	// 注：返回指定的多个描述信息是否至少存在一个是激活状态，
	// 或者在没有显示指定激活描述信息时，判断指定的多个描述信息是否有一个为默认环境配置。
	@Override
	@Deprecated
	public boolean acceptsProfiles(String... profiles) {
		Assert.notEmpty(profiles, "Must specify at least one profile");
		for (String profile : profiles) {
			if (StringUtils.hasLength(profile) && profile.charAt(0) == '!') {
				if (!isProfileActive(profile.substring(1))) {
					// 注：判断当前描述信息是否为非激活状态
					return true;
				}
			}
			else if (isProfileActive(profile)) {
				// 注：判断当前描述信息是否为激活状态
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean acceptsProfiles(Profiles profiles) {
		/**
		 * 注：使用Profiles对象类判断描述信息的激活状态
		 * - isProfileActive是基本判断，Profiles在此基础上封装了与、或、非等逻辑。
		 */
		Assert.notNull(profiles, "Profiles must not be null");
		return profiles.matches(this::isProfileActive);
	}

	/**
	 * Return whether the given profile is active, or if active profiles are empty
	 * whether the profile should be active by default.
	 * 注：用于判断指定的描述信息是否处于激活状态。
	 * - 如果用户未指定激活的应用描述信息，则会使用默认激活描述信息来判断。
	 * - 这里的profile是基本名，不能为空也不能有非逻辑
	 * @throws IllegalArgumentException per {@link #validateProfile(String)}
	 */
	protected boolean isProfileActive(String profile) {
		// 注：验证描述信息的合法性
		validateProfile(profile);
		// 注：获取当前激活状态的描述信息集合
		Set<String> currentActiveProfiles = doGetActiveProfiles();
		// 注：判断激活描述信息集合是否包含当前描述信息；在激活描述信息为空的情况下，判断默认描述信息是否包含该描述信息
		return (currentActiveProfiles.contains(profile) ||
				(currentActiveProfiles.isEmpty() && doGetDefaultProfiles().contains(profile)));
	}

	/**
	 * Validate the given profile, called internally prior to adding to the set of
	 * active or default profiles.
	 * <p>Subclasses may override to impose further restrictions on profile syntax.
	 * @throws IllegalArgumentException if the profile is null, empty, whitespace-only or
	 * begins with the profile NOT operator (!).
	 * 注：用于验证指定的描述信息，在向激活或默认描述信息集合之前内部调用
	 * - 子类可能会重写去进一步限制描述信息的语法
	 * - 如果描述信息为null，空，仅空字符或者以！开头，则抛出IllegalArgumentException异常。
	 * @see #acceptsProfiles
	 * @see #addActiveProfile
	 * @see #setDefaultProfiles
	 */
	protected void validateProfile(String profile) {
		if (!StringUtils.hasText(profile)) {
			throw new IllegalArgumentException("Invalid profile [" + profile + "]: must contain text");
		}
		if (profile.charAt(0) == '!') {
			throw new IllegalArgumentException("Invalid profile [" + profile + "]: must not begin with ! operator");
		}
	}

	// 注：该方法会返回当前环境的可变属性源对象，可变意味着允许对一系列属性源进行操作。
	@Override
	public MutablePropertySources getPropertySources() {
		return this.propertySources;
	}

	// 注：该方法在当前环境安全的情况下返回系统属性值，否则会返回一个map类型的实现-通过System#getProperty方法来获取属性值。
	@Override
	@SuppressWarnings({"rawtypes", "unchecked"})
	public Map<String, Object> getSystemProperties() {
		try {
			// 注：尝试返回完整map
			return (Map) System.getProperties();
		}
		catch (AccessControlException ex) {
			return (Map) new ReadOnlySystemAttributesMap() {
				@Override
				@Nullable
				protected String getSystemAttribute(String attributeName) {
					try {
						// 注：根据指定key调用getProperty返回系统属性值
						return System.getProperty(attributeName);
					}
					catch (AccessControlException ex) {
						if (logger.isInfoEnabled()) {
							logger.info("Caught AccessControlException when accessing system property '" +
									attributeName + "'; its value will be returned [null]. Reason: " + ex.getMessage());
						}
						return null;
					}
				}
			};
		}
	}

	// 注：该方法在当前环境安全的情况下返回系统环境配置值，否则会返回一个map类型的实现-通过System#getenv方法来获取环境配置值。
	@Override
	@SuppressWarnings({"rawtypes", "unchecked"})
	public Map<String, Object> getSystemEnvironment() {
		if (suppressGetenvAccess()) {
			// 注：如果配置为忽略环境属性，直接返回空map
			return Collections.emptyMap();
		}
		try {
			// 注：尝试返回完整map
			return (Map) System.getenv();
		}
		catch (AccessControlException ex) {
			return (Map) new ReadOnlySystemAttributesMap() {
				@Override
				@Nullable
				protected String getSystemAttribute(String attributeName) {
					try {
						// 注：根据指定key调用getenv返回系统环境变量值
						return System.getenv(attributeName);
					}
					catch (AccessControlException ex) {
						if (logger.isInfoEnabled()) {
							// 注：如果权限日志较多时，可考虑忽略环境配置
							logger.info("Caught AccessControlException when accessing system environment variable '" +
									attributeName + "'; its value will be returned [null]. Reason: " + ex.getMessage());
						}
						return null;
					}
				}
			};
		}
	}

	/**
	 * Determine whether to suppress {@link System#getenv()}/{@link System#getenv(String)}
	 * access for the purposes of {@link #getSystemEnvironment()}.
	 * 注：用于getSystemEnvironment方法中是否忽略系统环境属性的判断
	 * <p>If this method returns {@code true}, an empty dummy Map will be used instead
	 * of the regular system environment Map, never even trying to call {@code getenv}
	 * and therefore avoiding security manager warnings (if any).
	 * 注：如果该方法返回true，则getSystemEnvironment方法会返回一个空map而不是常规的系统环境映射，
	 * 更不会试图去调用getenv的任何方法，因此避免了安全管理器的告警信息。
	 * <p>The default implementation checks for the "spring.getenv.ignore" system property,
	 * returning {@code true} if its value equals "true" in any case.
	 * 注：判断是否忽略系统环境变量在抽象类中的默认实现是检查系统属性“spring.getenv.ignore”对应的属性值是否为"true"。
	 * @see #IGNORE_GETENV_PROPERTY_NAME
	 * @see SpringProperties#getFlag
	 * 注：protected权限，可由环境具体子类来重写是否返回系统环境变量。
	 */
	protected boolean suppressGetenvAccess() {
		// 注：检查"spring.getenv.ignore"属性值；先检查本地spring属性，再检查JVM系统属性
		return SpringProperties.getFlag(IGNORE_GETENV_PROPERTY_NAME);
	}

	// 注：该方法将父环境的激活描述信息、默认描述信息以及属性源添加到子环境中对应的集合中去。
	@Override
	public void merge(ConfigurableEnvironment parent) {
		// 注：将父可配置环境中的属性源添加到当前环境中的属性源中
		for (PropertySource<?> ps : parent.getPropertySources()) {
			if (!this.propertySources.contains(ps.getName())) {
				this.propertySources.addLast(ps);
			}
		}
		// 注：将当前父环境中的激活描述信息添加到当前环境的激活描述信息中
		String[] parentActiveProfiles = parent.getActiveProfiles();
		if (!ObjectUtils.isEmpty(parentActiveProfiles)) {
			synchronized (this.activeProfiles) {
				// 注：这里应该提前尝试获取激活配置，否则可能会忽略激活描述信息的属性配置值
				Collections.addAll(this.activeProfiles, parentActiveProfiles);
			}
		}
		// 注：将当前父环境中的默认描述信息添加到当前环境的默认描述信息中
		String[] parentDefaultProfiles = parent.getDefaultProfiles();
		if (!ObjectUtils.isEmpty(parentDefaultProfiles)) {
			synchronized (this.defaultProfiles) {
				// 注：移除预留的默认描述信息
				this.defaultProfiles.remove(RESERVED_DEFAULT_PROFILE_NAME);
				// 注：这里应该提前尝试获取默认描述信息，否则可能会忽略默认描述信息的属性配置值
				Collections.addAll(this.defaultProfiles, parentDefaultProfiles);
			}
		}
	}


	//---------------------------------------------------------------------
	// Implementation of ConfigurablePropertyResolver interface
	// ConfigurablePropertyResolver接口的实现方法，由于属性解析器也是该类型，基本方法实现委托给属性解析器即可。
	//---------------------------------------------------------------------

	// 注：返回在执行属性值类型转换时所用的类型转换服务实例-ConfigurableConversionService
	@Override
	public ConfigurableConversionService getConversionService() {
		return this.propertyResolver.getConversionService();
	}

	// 注：设置在属性转换时所需用到的可配置型转换服务。
	@Override
	public void setConversionService(ConfigurableConversionService conversionService) {
		this.propertyResolver.setConversionService(conversionService);
	}

	// 注：设置需要解析的占位符的前缀，比如“${”
	@Override
	public void setPlaceholderPrefix(String placeholderPrefix) {
		this.propertyResolver.setPlaceholderPrefix(placeholderPrefix);
	}

	// 注：设置需要解析的占位符的后缀，比如“}”
	@Override
	public void setPlaceholderSuffix(String placeholderSuffix) {
		this.propertyResolver.setPlaceholderSuffix(placeholderSuffix);
	}

	// 注：指定占位符与其关联的默认值之间的分隔符。一般为“:”
	@Override
	public void setValueSeparator(@Nullable String valueSeparator) {
		this.propertyResolver.setValueSeparator(valueSeparator);
	}

	// 注：设置在遇到无法解析的属性值内嵌套的占位符时是否抛出异常。
	@Override
	public void setIgnoreUnresolvableNestedPlaceholders(boolean ignoreUnresolvableNestedPlaceholders) {
		this.propertyResolver.setIgnoreUnresolvableNestedPlaceholders(ignoreUnresolvableNestedPlaceholders);
	}

	// 注：指定哪些属性必须存在。
	@Override
	public void setRequiredProperties(String... requiredProperties) {
		this.propertyResolver.setRequiredProperties(requiredProperties);
	}

	// 注：验证必须存在的属性的存在性，并且解析为非Null值。
	@Override
	public void validateRequiredProperties() throws MissingRequiredPropertiesException {
		this.propertyResolver.validateRequiredProperties();
	}


	//---------------------------------------------------------------------
	// Implementation of PropertyResolver interface
	// PropertyResolver接口的实现方法，由于属性解析器也是该类型，基本方法实现委托给属性解析器即可。
	//---------------------------------------------------------------------

	// 注：返回指定的属性key是否可以被解析
	@Override
	public boolean containsProperty(String key) {
		return this.propertyResolver.containsProperty(key);
	}

	// 注：返回指定属性key对应的解析值。
	@Override
	@Nullable
	public String getProperty(String key) {
		return this.propertyResolver.getProperty(key);
	}

	// 注：返回指定属性key对应的解析值，不存在则返回String类型默认值。
	@Override
	public String getProperty(String key, String defaultValue) {
		return this.propertyResolver.getProperty(key, defaultValue);
	}

	// 注：返回指定属性key对应的目标类型的解析值。
	@Override
	@Nullable
	public <T> T getProperty(String key, Class<T> targetType) {
		return this.propertyResolver.getProperty(key, targetType);
	}

	// 注：返回指定属性key对应的目标类型的解析值-不存在则返回默认值。
	@Override
	public <T> T getProperty(String key, Class<T> targetType, T defaultValue) {
		return this.propertyResolver.getProperty(key, targetType, defaultValue);
	}

	// 注：返回指定属性key对应的解析值，不存在则抛出异常。
	@Override
	public String getRequiredProperty(String key) throws IllegalStateException {
		return this.propertyResolver.getRequiredProperty(key);
	}

	// 注：返回指定属性key对应的目标类型的解析值，不存在则抛出异常。
	@Override
	public <T> T getRequiredProperty(String key, Class<T> targetType) throws IllegalStateException {
		return this.propertyResolver.getRequiredProperty(key, targetType);
	}

	// 注：解析指定字符串中的“${XXX}”占位符-无法解析的占位符将忽略。
	@Override
	public String resolvePlaceholders(String text) {
		return this.propertyResolver.resolvePlaceholders(text);
	}

	// 注：解析指定字符串中的“${XXX}”占位符-无法解析的占位符将抛出异常
	@Override
	public String resolveRequiredPlaceholders(String text) throws IllegalArgumentException {
		return this.propertyResolver.resolveRequiredPlaceholders(text);
	}


	// 注：从toString方法可以看出，环境对象=激活描述信息+默认描述信息+多属性源
	@Override
	public String toString() {
		return getClass().getSimpleName() + " {activeProfiles=" + this.activeProfiles +
				", defaultProfiles=" + this.defaultProfiles + ", propertySources=" + this.propertySources + "}";
	}

}
