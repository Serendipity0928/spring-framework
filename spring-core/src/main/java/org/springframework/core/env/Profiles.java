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

package org.springframework.core.env;

import java.util.function.Predicate;

/**
 * Profile predicate that may be {@linkplain Environment#acceptsProfiles(Profiles)
 * accepted} by an {@link Environment}.
 * 注：用于判断当前环境已激活的描述信息是否满足指定的断言判断
 *
 * <p>May be implemented directly or, more usually, created using the
 * {@link #of(String...) of(...)} factory method.
 * 注：该接口可能会被直接实现，或更通常的用法是通过of工厂方法来创建该实例。
 *
 * @author Phillip Webb
 * @since 5.1
 */
@FunctionalInterface
public interface Profiles {

	/**
	 * Test if this {@code Profiles} instance <em>matches</em> against the given
	 * active profiles predicate.
	 * 注：用于测试当前Profiles对象是否匹配指定的激活描述信息判断。
	 * @param activeProfiles predicate that tests whether a given profile is
	 * currently active
	 * 注：activeProfiles是一个测试判断实例，用于对给定的profile名称来判断是否为激活状态。
	 * - 注意这里别搞晕了，实际上这里入参(activeProfiles)属于最基本的判断。即给定一个profile名，通过这个判断来返回是否处于激活状态。
	 * - 而Profiles对象的作用是提高了这种判断激活状态的能力，支持多种profile名的混合判断逻辑，也支持各种逻辑表达式。
	 * - 所以我们可以认为Profiles#matches(...)方法是在activeProfiles的基础上的进一步扩展和封装。
	 */
	boolean matches(Predicate<String> activeProfiles);


	/**
	 * Create a new {@link Profiles} instance that checks for matches against
	 * the given <em>profile strings</em>.
	 * 注：创建一个Profiles实例，用于检查或匹配指定的应用描述信息。
	 * <p>The returned instance will {@linkplain Profiles#matches(Predicate) match}
	 * if any one of the given profile strings matches.
	 * 注：该方法返回的实例的match方法将会匹配至少一个指定的描述信息为激活状态。
	 * <p>A profile string may contain a simple profile name (for example
	 * {@code "production"}) or a profile expression. A profile expression allows
	 * for more complicated profile logic to be expressed, for example
	 * {@code "production & cloud"}.
	 * <p>The following operators are supported in profile expressions:
	 * <ul>
	 * <li>{@code !} - A logical <em>not</em> of the profile</li>
	 * <li>{@code &} - A logical <em>and</em> of the profiles</li>
	 * <li>{@code |} - A logical <em>or</em> of the profiles</li>
	 * </ul>
	 * 注：一个描述信息string变量可能包括简单的描述名称(比如“prod”)或者一个描述表达式。
	 * 一个描述信息的表达式允许具有复杂的表达逻辑，比如“prod & cloud”。下面的列举了一些支持的描述信息表达式：
	 *  1. !-->表示逻辑反，即非激活状态为匹配
	 *  2. &-->表示逻辑与，即均为激活状态为匹配
	 *  3. |-->表示逻辑或，即有任意一个为激活状态为匹配
	 * <p>Please note that the {@code &} and {@code |} operators may not be mixed
	 * without using parentheses. For example {@code "a & b | c"} is not a valid
	 * expression; it must be expressed as {@code "(a & b) | c"} or
	 * {@code "a & (b | c)"}.
	 * 注：请注意“&”与“|”在混合使用的时候必须使用()来分隔，否则为无效表示
	 * 例如："a & b | c"为无效表示，可更改为"(a & b) | c"或"a & (b | c)"
	 * @param profiles the <em>profile strings</em> to include
	 * @return a new {@link Profiles} instance
	 */
	static Profiles of(String... profiles) {
		return ProfilesParser.parse(profiles);
	}

}
