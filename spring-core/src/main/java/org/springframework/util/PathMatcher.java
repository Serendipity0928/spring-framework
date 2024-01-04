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

package org.springframework.util;

import java.util.Comparator;
import java.util.Map;

/**
 * Strategy interface for {@code String}-based path matching.
 * 注：PathMatcher是基于String类型路径匹配的策略接口。
 *
 * <p>Used by {@link org.springframework.core.io.support.PathMatchingResourcePatternResolver},
 * {@link org.springframework.web.servlet.handler.AbstractUrlHandlerMapping},
 * and {@link org.springframework.web.servlet.mvc.WebContentInterceptor}.
 * 注：在spring核心场景中，用于资源路径匹配解析器：PathMatchingResourcePatternResolver
 * - 在web场景中，AbstractUrlHandlerMapping、WebContentInterceptor
 *
 * <p>The default implementation is {@link AntPathMatcher}, supporting the
 * Ant-style pattern syntax.
 * 注：当前路径匹配器的默认实现为AntPathMatcher，支持Ant样式语法。
 *
 * @author Juergen Hoeller
 * @since 1.2
 * @see AntPathMatcher
 */
public interface PathMatcher {

	/**
	 * Does the given {@code path} represent a pattern that can be matched
	 * by an implementation of this interface?
	 * <p>If the return value is {@code false}, then the {@link #match}
	 * method does not have to be used because direct equality comparisons
	 * on the static path Strings will lead to the same result.
	 * 注：是否指定表示某样式的路径可以被当前接口实现类所匹配？
	 * - 如果当前方法返回的false，即不匹配，那么就不需要再调用当前路径匹配器的match方法了。
	 * @param path the path to check
	 * @return {@code true} if the given {@code path} represents a pattern
	 */
	boolean isPattern(String path);

	/**
	 * Match the given {@code path} against the given {@code pattern},
	 * according to this PathMatcher's matching strategy.
	 * 注：根据当前路径匹配器的匹配策略来匹配指定的样式及路径。
	 * @param pattern the pattern to match against
	 * @param path the path to test
	 * @return {@code true} if the supplied {@code path} matched,
	 * {@code false} if it didn't
	 */
	boolean match(String pattern, String path);

	/**
	 * Match the given {@code path} against the corresponding part of the given
	 * {@code pattern}, according to this PathMatcher's matching strategy.
	 * <p>Determines whether the pattern at least matches as far as the given base
	 * path goes, assuming that a full path may then match as well.
	 * 注：根据当前路径匹配器的匹配策略来匹配指定的样式及路径。
	 * - 假设完整路径也可能匹配，判断指定的样式是否至少与给定的基本路径匹配
	 * @param pattern the pattern to match against
	 * @param path the path to test
	 * @return {@code true} if the supplied {@code path} matched,
	 * {@code false} if it didn't
	 */
	boolean matchStart(String pattern, String path);

	/**
	 * Given a pattern and a full path, determine the pattern-mapped part.
	 * 注：根据指定的样式和全路径，判断样式匹配的部分
	 * <p>This method is supposed to find out which part of the path is matched
	 * dynamically through an actual pattern, that is, it strips off a statically
	 * defined leading path from the given full path, returning only the actually
	 * pattern-matched part of the path.
	 * 注：该方法要通过实际模式找出路径的哪一部分是动态匹配的。
	 * 也就是说，它从给定的完整路径中剥离静态定义的前导路径，只返回路径的实际模式匹配部分。
	 * <p>For example: For "myroot/*.html" as pattern and "myroot/myfile.html"
	 * as full path, this method should return "myfile.html". The detailed
	 * determination rules are specified to this PathMatcher's matching strategy.
	 * 注：比如，对于样式为"myroot/*.html"，完整路径为"myroot/myfile.html"，这个方法会返回"myfile.html"。
	 * - 具体详细判定规则由当前路径匹配器策略指定。
	 * <p>A simple implementation may return the given full path as-is in case
	 * of an actual pattern, and the empty String in case of the pattern not
	 * containing any dynamic parts (i.e. the {@code pattern} parameter being
	 * a static path that wouldn't qualify as an actual {@link #isPattern pattern}).
	 * A sophisticated implementation will differentiate between the static parts
	 * and the dynamic parts of the given path pattern.
	 * 注：一个简单的实现可能会在实际模式情况下原样返回指定的完整路径，在实际模式不包含任何动态部分的情况下返回空字符串。
	 * 一个复杂的实现逻辑将区分指定路径模式的静态部分和动态部分。
	 * @param pattern the path pattern
	 * @param path the full path to introspect
	 * @return the pattern-mapped part of the given {@code path}
	 * (never {@code null})
	 */
	String extractPathWithinPattern(String pattern, String path);

	/**
	 * Given a pattern and a full path, extract the URI template variables. URI template
	 * variables are expressed through curly brackets ('{' and '}').
	 * <p>For example: For pattern "/hotels/{hotel}" and path "/hotels/1", this method will
	 * return a map containing "hotel"->"1".
	 * 注：根据指定的样式和完整路径抽取URI模版变量。URI模版变量通过波浪括号进行表达。
	 * - 示例：对于样式为"/hotels/{hotel}"，路径为"/hotels/1"来说，当前方法会返回一个匹配映射，其中包括"hotel"->"1"
	 * @param pattern the path pattern, possibly containing URI templates
	 * @param path the full path to extract template variables from
	 * @return a map, containing variable names as keys; variables values as values
	 */
	Map<String, String> extractUriTemplateVariables(String pattern, String path);

	/**
	 * Given a full path, returns a {@link Comparator} suitable for sorting patterns
	 * in order of explicitness for that path.
	 * <p>The full algorithm used depends on the underlying implementation,
	 * but generally, the returned {@code Comparator} will
	 * {@linkplain java.util.List#sort(java.util.Comparator) sort}
	 * a list so that more specific patterns come before generic patterns.
	 * 注：根据指定的完整路径，返回一个比较器。该比较器适用于按路径的显示顺序对模式进行排序。
	 * - 具体使用的完整算法取决于底层的实现。但通常来说，该方法返回的比较器会对一个列表进行排序，使得更具体的样式排在通用样式之前。
	 * @param path the full path to use for comparison
	 * @return a comparator capable of sorting patterns in order of explicitness
	 */
	Comparator<String> getPatternComparator(String path);

	/**
	 * Combines two patterns into a new pattern that is returned.
	 * <p>The full algorithm used for combining the two pattern depends on the underlying implementation.
	 * 注：返回两个样式结合成的一个新样式。
	 * - 用于合并两个样式的完整算法依赖具体的底层实现。
	 * @param pattern1 the first pattern
	 * @param pattern2 the second pattern
	 * @return the combination of the two patterns
	 * @throws IllegalArgumentException when the two patterns cannot be combined
	 */
	String combine(String pattern1, String pattern2);

}
