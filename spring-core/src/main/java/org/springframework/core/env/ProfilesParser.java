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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringTokenizer;
import java.util.function.Predicate;

import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Internal parser used by {@link Profiles#of}.
 * 注：用于内部创建Profiles对象的实际方法
 *
 * @author Phillip Webb
 * @since 5.1
 */
final class ProfilesParser {

	private ProfilesParser() {
	}

	// 注：解析入参中的描述信息表达式，并返回ParsedProfiles对象。
	static Profiles parse(String... expressions) {
		Assert.notEmpty(expressions, "Must specify at least one profile");
		Profiles[] parsed = new Profiles[expressions.length];
		for (int i = 0; i < expressions.length; i++) {
			// 注：将每一个表达式解析为Profiles对象
			parsed[i] = parseExpression(expressions[i]);
		}
		// 注：整体返回的是ParsedProfiles对象，实际上也是Profiles对象，内部只要有一个表达式满足匹配即返回匹配；
		//     expressions传入进去是为了toString使用
		return new ParsedProfiles(expressions, parsed);
	}

	// 注：将描述信息表达式解析为Profiles对象
	private static Profiles parseExpression(String expression) {
		Assert.hasText(expression, () -> "Invalid profile expression [" + expression + "]: must contain text");
		// 注：将表达式按照“()&|!”这五个字符进行分隔，并且会返回分隔符
		StringTokenizer tokens = new StringTokenizer(expression, "()&|!", true);
		// 注：按照表达式及其分隔表示来进行解析成(单)Profiles对象
		return parseTokens(expression, tokens);
	}

	private static Profiles parseTokens(String expression, StringTokenizer tokens) {
		// 注：按照表达式及其分隔表示来进行解析成(单)Profiles对象。【这里初始递归上下文信息为NONE】
		return parseTokens(expression, tokens, Context.NONE);
	}

	/**
	 * 递归解析表达式的方法
	 * @param expression 要解析的表达式原文
	 * @param tokens 用于迭代的分隔字符串
	 * @param context 递归上文信息，参见Context
	 * @return 当前表达式的Profiles对象(实际上就是一个lambda表达式，表达式入参为activeProfiles-基本激活状态的判断)
	 * 大致叙述下这里解析表达式的逻辑：
	 * 1. 表达式支持的逻辑有四种：与(&)、或(|)、非(!)、以及括号
	 * 2. 完整表达式可拆分为子表达式，每一次递归返回的就是该子表达式的Profiles。子表达式有四种：、
	 * 	  ① 基本字符串表达式。如“spring”
	 *    ② 由多个与(&)逻辑连接的子表达式。【表达式之间是嵌套的】
	 *    ③ 由多个或(|)逻辑连接的子表达式
	 *    ④ 由非(!)逻辑引导的子表达式
	 *    ⑤ 由括号包括的子表达式
	 *    其中①、②、③通过循环遍历以及operator进行合并集合中的Profiles来返回。
	 *    而④、⑤则通过递归以及Context来进行返回Profiles。
	 */
	private static Profiles parseTokens(String expression, StringTokenizer tokens, Context context) {
		// 注：用于记录表达式中多次while内循环解析的(单)Profiles对象，便于后续根据逻辑规则进行合并
		List<Profiles> elements = new ArrayList<>();
		// 注：上一次循环所涉及的操作，可能是and、or、null。【相当于循环上文】
		Operator operator = null;
		while (tokens.hasMoreTokens()) {
			String token = tokens.nextToken().trim();
			if (token.isEmpty()) {
				// 注：去除空字符串
				continue;
			}
			switch (token) {
				case "(":
					// 注：碰到“(”字符，则接下来要进行解析的肯定是个完整的表达式，因此递归，将递归的上文信息(“括号”)传递下去
					Profiles contents = parseTokens(expression, tokens, Context.BRACKET);
					/**
					 * 注：如果“(xxx)”前是一个“!”，那么这里直接返回给非逻辑进行处理。【递归的结束都是返回】
					 * 如果“(xxx)”前不是一个“!”，可能是空，也可能是 & 或者 |，添加到集合中后续合并即可
					 */
					if (context == Context.INVERT) {
						return contents;
					}
					elements.add(contents);
					break;
				case "&":
					/**
					 * 注：碰到“&”字符，则接下来要进行解析的是一个“与”逻辑表达式。
					 * 注意这里校验了与逻辑不能与非逻辑共存。必须使用括号来分开
					 */
					assertWellFormed(expression, operator == null || operator == Operator.AND);
					operator = Operator.AND;
					break;
				case "|":
					/**
					 * 注：碰到“|”字符，则接下来要进行解析的是一个“或”逻辑表达式。
					 * 注意这里校验了与逻辑不能与非逻辑共存。必须使用括号来分开
					 */
					assertWellFormed(expression, operator == null || operator == Operator.OR);
					operator = Operator.OR;
					break;
				case "!":
					/**
					 * 注：“！”后面也是一个完整表示，递归时碰见“(”或其他普通形式都会返回。
					 * 这也就意味着“！”后面仅允许存在“!(XXX)”或者“!XXX的形式。
					 */
					elements.add(not(parseTokens(expression, tokens, Context.INVERT)));
					break;
				case ")":
					/**
					 * 注：“)”字符认为是一个“(XXX)”表达式的结束，执行合并表达式操作(处理其中的与、非逻辑)
					 * - 如果前文是一个“(”，那就说明解析了一个括号内的表示，直接返回
					 * - 如果前文非“(”，则将前面合并后的表示添加到清空的集合中，继续遍历。
					 */
					Profiles merged = merge(expression, elements, operator);
					if (context == Context.BRACKET) {
						return merged;
					}
					elements.clear();
					elements.add(merged);
					operator = null;
					break;
				default:
					/**
					 * 注：普通非特殊字符，equals实际上就是一个最基本的Profiles对象。
					 * 其余包括&、|、！、(、)都是在该对象基础上做的整合操作。
					 */
					Profiles value = equals(token);
					/**
					 * 注：前文是“!”，直接返回做非逻辑。
					 * 为什么其他情况不返回呢？
					 * - 因为与、非逻辑继续循环通过集合合并后返回；而括号是碰到右括号合并集合后返回；普通情况也是先添加到集合中
					 */
					if (context == Context.INVERT) {
						return value;
					}
					elements.add(value);
			}
		}
		return merge(expression, elements, operator);
	}

	private static Profiles merge(String expression, List<Profiles> elements, @Nullable Operator operator) {
		assertWellFormed(expression, !elements.isEmpty());
		if (elements.size() == 1) {
			// 注：仅有一个元素，直接返回不需要合并。（一般情况下这里operator为null）
			return elements.get(0);
		}
		Profiles[] profiles = elements.toArray(new Profiles[0]);
		// 注：根据operator的类型来进行合并等多个Profiles。（这个地方operator不可能为null）
		return (operator == Operator.AND ? and(profiles) : or(profiles));
	}

	private static void assertWellFormed(String expression, boolean wellFormed) {
		Assert.isTrue(wellFormed, () -> "Malformed profile expression [" + expression + "]");
	}

	private static Profiles or(Profiles... profiles) {
		// 注：多个Profiles之间的或逻辑-anyMatch
		return activeProfile -> Arrays.stream(profiles).anyMatch(isMatch(activeProfile));
	}

	private static Profiles and(Profiles... profiles) {
		// 注：多个Profiles之间的与逻辑
		return activeProfile -> Arrays.stream(profiles).allMatch(isMatch(activeProfile));
	}

	private static Profiles not(Profiles profiles) {
		// 注：Profiles的非逻辑实现
		return activeProfile -> !profiles.matches(activeProfile);
	}

	private static Profiles equals(String profile) {
		// 注：Profiles的基本实现 （上述or、and、not中处理的单个Profiles都是这个实现。逻辑实际上是在处理多个基本实现）
		return activeProfile -> activeProfile.test(profile);
	}

	private static Predicate<Profiles> isMatch(Predicate<String> activeProfile) {
		// 注：封装(单)Profiles的匹配逻辑，实际上就是执行equals方法中的“activeProfile.test(profile);”内容
		return profiles -> profiles.matches(activeProfile);
	}


	/**
	 * 注：表示当前Profiles对象与上一个或多个Profiles对象的关系，后续根据该操作进行合并
	 * 1. AND：将多个Profiles对象进行合并操作，即在判断的时候用的是allMatch
	 * 2. OR：将多个Profiles对象进行合并操作，即在判断的时候用的是anyMatch
	 */
	private enum Operator {AND, OR}


	/**
	 * 注：解析表达式的前文信息
	 * 1. NONE：表示无特殊符
	 * 2. INVERT：表示前面有个非逻辑-->"!"
	 * 3. BRACKET：表示前面有个括号 --> "("
	 */
	private enum Context {NONE, INVERT, BRACKET}


	// 注：多个表达式向外层暴露的实际对象
	private static class ParsedProfiles implements Profiles {

		// 注：传入的源表达式数组，为了打印toString方法
		private final String[] expressions;

		// 注：内部已解析后的表达式Profiles对象
		private final Profiles[] parsed;

		ParsedProfiles(String[] expressions, Profiles[] parsed) {
			this.expressions = expressions;
			this.parsed = parsed;
		}

		@Override
		public boolean matches(Predicate<String> activeProfiles) {
			// 外层暴露的匹配方法，需传入基本应用描述信息的判断逻辑（一般是判断指定的描述名是否是激活状态）
			for (Profiles candidate : this.parsed) {
				if (candidate.matches(activeProfiles)) {
					// 注：只要有一个表达式满足匹配，即返回匹配。
					// 【注意，这里是指的入参的多个表达式之间的或逻辑，而不是表达式内部的或逻辑】
					return true;
				}
			}
			return false;
		}

		@Override
		public String toString() {
			return StringUtils.arrayToDelimitedString(this.expressions, " or ");
		}
	}

}
