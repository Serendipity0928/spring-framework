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

package org.springframework.core.env;

import java.util.Map;

import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

/**
 * {@link PropertySource} that reads keys and values from a {@code Map} object.
 * The underlying map should not contain any {@code null} values in order to
 * comply with {@link #getProperty} and {@link #containsProperty} semantics.
 * 注：通过map对象来读取属性key以及属性值的属性源。
 * - 内部的map对象不应该包含任何null值，以保证和getProperty、containsProperty的语义相同(null值表示不存在)。
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @since 3.1
 * @see PropertiesPropertySource
 */
public class MapPropertySource extends EnumerablePropertySource<Map<String, Object>> {

	/**
	 * Create a new {@code MapPropertySource} with the given name and {@code Map}.
	 * // 注：创建一个新的map属性源
	 * @param name the associated name
	 * @param source the Map source (without {@code null} values in order to get
	 * consistent {@link #getProperty} and {@link #containsProperty} behavior)
	 */
	public MapPropertySource(String name, Map<String, Object> source) {
		super(name, source);
	}


	@Override
	@Nullable
	public Object getProperty(String name) {
		return this.source.get(name);
	}

	@Override
	public boolean containsProperty(String name) {
		return this.source.containsKey(name);
	}

	@Override
	public String[] getPropertyNames() {
		return StringUtils.toStringArray(this.source.keySet());
	}

}
