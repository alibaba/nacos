/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.nacos.core.utils;

import org.springframework.core.ResolvableType;

import java.util.Objects;

/**
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
@SuppressWarnings("all")
public final class ClassUtils {

	public static <T> Class<T> resolveGenericType(Class<?> declaredClass) {
		return (Class<T>) ResolvableType.forClass(declaredClass).getSuperType().resolveGeneric(0);
	}

	public static <T> Class<T> resolveGenericTypeByInterface(Class<?> declaredClass) {
		return (Class<T>) ResolvableType.forClass(declaredClass).getInterfaces()[0].resolveGeneric(0);
	}

	public static Class findClassByName(String className) {
		try {
			return Class.forName(className);
		} catch (Exception e) {
			throw new RuntimeException("this class name not found");
		}
	}

	public static String getName(Object obj) {
		Objects.requireNonNull(obj, "obj");
		return obj.getClass().getName();
	}

	public static String getCanonicalName(Object obj) {
		Objects.requireNonNull(obj, "obj");
		return obj.getClass().getCanonicalName();
	}

	public static String getSimplaName(Object obj) {
		Objects.requireNonNull(obj, "obj");
		return obj.getClass().getSimpleName();
	}

	public static String getName(Class cls) {
		Objects.requireNonNull(cls, "cls");
		return cls.getName();
	}

	public static String getCanonicalName(Class cls) {
		Objects.requireNonNull(cls, "cls");
		return cls.getCanonicalName();
	}

	public static String getSimplaName(Class cls) {
		Objects.requireNonNull(cls, "cls");
		return cls.getSimpleName();
	}

}