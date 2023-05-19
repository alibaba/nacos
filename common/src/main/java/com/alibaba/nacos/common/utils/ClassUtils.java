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

package com.alibaba.nacos.common.utils;

import com.alibaba.nacos.api.exception.runtime.NacosRuntimeException;

import java.io.Closeable;
import java.io.Externalizable;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.alibaba.nacos.api.exception.NacosException.SERVER_ERROR;

/**
 * Utils for Class.
 *
 * @author <a href="mailto:liaochuntao@live.com">liaochuntao</a>
 */
public final class ClassUtils {

    private ClassUtils() {
    }
    
    public static final String ARRAY_SUFFIX = "[]";
    
    private static final String INTERNAL_ARRAY_PREFIX = "[";
    
    private static final String NON_PRIMITIVE_ARRAY_PREFIX = "[L";
    
    private static final char PACKAGE_SEPARATOR = '.';
    
    private static final char PATH_SEPARATOR = '/';
    
    private static final char NESTED_CLASS_SEPARATOR = '$';
    
    private static final String SEMICOLON_SEPARATOR = ";";
    
    /**
     * Map with primitive wrapper type as key and corresponding primitive type as value, for example: Integer.class ->
     * int.class.
     */
    private static final Map<Class<?>, Class<?>> PRIMITIVE_WRAPPER_TYPE_MAP = new IdentityHashMap<>(9);
    
    /**
     * Map with primitive type as key and corresponding wrapper type as value, for example: int.class -> Integer.class.
     */
    private static final Map<Class<?>, Class<?>> PRIMITIVE_TYPE_TO_WRAPPER_MAP = new IdentityHashMap<>(9);
    
    /**
     * Map with primitive type name as key and corresponding primitive type as value, for example: "int" ->
     * "int.class".
     */
    private static final Map<String, Class<?>> PRIMITIVE_TYPE_NAME_MAP = new HashMap<>(32);
    
    private static final int PRIMITIVE_TYPE_NAME_MAP_LENGTH = 7;
    
    /**
     * Map with common Java language class name as key and corresponding Class as value. Primarily for efficient
     * deserialization of remote invocations.
     */
    private static final Map<String, Class<?>> COMMON_CLASS_CACHE = new HashMap<>(64);
    
    static {
        PRIMITIVE_WRAPPER_TYPE_MAP.put(Boolean.class, boolean.class);
        PRIMITIVE_WRAPPER_TYPE_MAP.put(Byte.class, byte.class);
        PRIMITIVE_WRAPPER_TYPE_MAP.put(Character.class, char.class);
        PRIMITIVE_WRAPPER_TYPE_MAP.put(Double.class, double.class);
        PRIMITIVE_WRAPPER_TYPE_MAP.put(Float.class, float.class);
        PRIMITIVE_WRAPPER_TYPE_MAP.put(Integer.class, int.class);
        PRIMITIVE_WRAPPER_TYPE_MAP.put(Long.class, long.class);
        PRIMITIVE_WRAPPER_TYPE_MAP.put(Short.class, short.class);
        PRIMITIVE_WRAPPER_TYPE_MAP.put(Void.class, void.class);
        
        // Map entry iteration is less expensive to initialize than forEach with lambdas
        for (Map.Entry<Class<?>, Class<?>> entry : PRIMITIVE_WRAPPER_TYPE_MAP.entrySet()) {
            PRIMITIVE_TYPE_TO_WRAPPER_MAP.put(entry.getValue(), entry.getKey());
            registerCommonClasses(entry.getKey());
        }
        
        Set<Class<?>> primitiveTypes = new HashSet<>(32);
        primitiveTypes.addAll(PRIMITIVE_WRAPPER_TYPE_MAP.values());
        Collections.addAll(primitiveTypes, boolean[].class, byte[].class, char[].class, double[].class, float[].class,
                int[].class, long[].class, short[].class);
        for (Class<?> primitiveType : primitiveTypes) {
            PRIMITIVE_TYPE_NAME_MAP.put(primitiveType.getName(), primitiveType);
        }
        
        registerCommonClasses(Boolean[].class, Byte[].class, Character[].class, Double[].class, Float[].class,
                Integer[].class, Long[].class, Short[].class);
        registerCommonClasses(Number.class, Number[].class, String.class, String[].class, Class.class, Class[].class,
                Object.class, Object[].class);
        registerCommonClasses(Throwable.class, Exception.class, RuntimeException.class, Error.class,
                StackTraceElement.class, StackTraceElement[].class);
        registerCommonClasses(Enum.class, Iterable.class, Iterator.class, Enumeration.class, Collection.class,
                List.class, Set.class, Map.class, Map.Entry.class, Optional.class);
        
        Class<?>[] javaLanguageInterfaceArray = {Serializable.class, Externalizable.class, Closeable.class,
                AutoCloseable.class, Cloneable.class, Comparable.class};
        registerCommonClasses(javaLanguageInterfaceArray);
    }
    
    /**
     * Register the given common classes with the ClassUtils cache.
     */
    private static void registerCommonClasses(Class<?>... commonClasses) {
        for (Class<?> clazz : commonClasses) {
            COMMON_CLASS_CACHE.put(clazz.getName(), clazz);
        }
    }
    
    /**
     * Finds and returns class by className.
     *
     * @param className String value for className.
     * @return class Instances of the class represent classes and interfaces.
     */
    public static Class findClassByName(String className) {
        try {
            return Class.forName(className);
        } catch (Exception e) {
            throw new NacosRuntimeException(SERVER_ERROR, "this class name not found");
        }
    }
    
    /**
     * Determines if the class or interface represented by this object is either the same as, or is a superclass or
     * superinterface of, the class or interface represented by the specified parameter.
     *
     * @param clazz Instances of the class represent classes and interfaces.
     * @param cls   Instances of the class represent classes and interfaces.
     * @return the value indicating whether objects of the type can be assigned to objects of this class.
     */
    public static boolean isAssignableFrom(Class clazz, Class cls) {
        Objects.requireNonNull(cls, "cls");
        return clazz.isAssignableFrom(cls);
    }
    
    /**
     * Gets and returns the class name.
     *
     * @param cls Instances of the class represent classes and interfaces.
     * @return the name of the class or interface represented by this object.
     */
    public static String getName(Class cls) {
        Objects.requireNonNull(cls, "cls");
        return cls.getName();
    }
    
    /**
     * Gets and returns className.
     *
     * @param obj Object instance.
     * @return className.
     */
    public static String getName(Object obj) {
        Objects.requireNonNull(obj, "obj");
        return obj.getClass().getName();
    }
    
    /**
     * Gets and returns the canonical name of the underlying class.
     *
     * @param cls Instances of the class represent classes and interfaces.
     * @return The canonical name of the underlying class.
     */
    public static String getCanonicalName(Class cls) {
        Objects.requireNonNull(cls, "cls");
        return cls.getCanonicalName();
    }
    
    /**
     * Gets and returns the canonical name of the underlying class.
     *
     * @param obj Object instance.
     * @return The canonical name of the underlying class.
     */
    public static String getCanonicalName(Object obj) {
        Objects.requireNonNull(obj, "obj");
        return obj.getClass().getCanonicalName();
    }
    
    /**
     * Gets and returns the simple name of the underlying class.
     *
     * @param cls Instances of the class represent classes and interfaces.
     * @return the simple name of the underlying class.
     */
    public static String getSimpleName(Class cls) {
        Objects.requireNonNull(cls, "cls");
        return cls.getSimpleName();
    }
    
    /**
     * Gets and returns the simple name of the underlying class as given in the source code.
     *
     * @param obj Object instance.
     * @return the simple name of the underlying class.
     */
    public static String getSimpleName(Object obj) {
        Objects.requireNonNull(obj, "obj");
        return obj.getClass().getSimpleName();
    }
    
    /**
     * Replacement for {@code Class.forName()} that also returns Class instances for primitives (e.g. "int") and array
     * class names (e.g. "String[]"). Furthermore, it is also capable of resolving nested class names in Java source
     * style (e.g. "java.lang.Thread.State" instead of "java.lang.Thread$State").
     *
     * @param name        the name of the Class
     * @param classLoader the class loader to use (may be {@code null}, which indicates the default class loader)
     * @return a class instance for the supplied name
     * @throws ClassNotFoundException if the class was not found
     * @throws LinkageError           if the class file could not be loaded
     * @see Class#forName(String, boolean, ClassLoader)
     */
    public static Class<?> forName(String name, ClassLoader classLoader) throws ClassNotFoundException, LinkageError {
        
        AbstractAssert.notNull(name, "Name must not be null");
        
        Class<?> clazz = resolvePrimitiveClassName(name);
        if (clazz == null) {
            clazz = COMMON_CLASS_CACHE.get(name);
        }
        if (clazz != null) {
            return clazz;
        }
        
        // "java.lang.String[]" style arrays
        if (name.endsWith(ARRAY_SUFFIX)) {
            String elementClassName = name.substring(0, name.length() - ARRAY_SUFFIX.length());
            Class<?> elementClass = forName(elementClassName, classLoader);
            return Array.newInstance(elementClass, 0).getClass();
        }
        
        // "[Ljava.lang.String;" style arrays
        if (name.startsWith(NON_PRIMITIVE_ARRAY_PREFIX) && name.endsWith(SEMICOLON_SEPARATOR)) {
            String elementName = name.substring(NON_PRIMITIVE_ARRAY_PREFIX.length(), name.length() - 1);
            Class<?> elementClass = forName(elementName, classLoader);
            return Array.newInstance(elementClass, 0).getClass();
        }
        
        // "[[I" or "[[Ljava.lang.String;" style arrays
        if (name.startsWith(INTERNAL_ARRAY_PREFIX)) {
            String elementName = name.substring(INTERNAL_ARRAY_PREFIX.length());
            Class<?> elementClass = forName(elementName, classLoader);
            return Array.newInstance(elementClass, 0).getClass();
        }
        
        ClassLoader clToUse = classLoader;
        if (clToUse == null) {
            clToUse = getDefaultClassLoader();
        }
        try {
            return Class.forName(name, false, clToUse);
        } catch (ClassNotFoundException ex) {
            int lastDotIndex = name.lastIndexOf(PACKAGE_SEPARATOR);
            if (lastDotIndex != -1) {
                String nestedClassName =
                        name.substring(0, lastDotIndex) + NESTED_CLASS_SEPARATOR + name.substring(lastDotIndex + 1);
                try {
                    return Class.forName(nestedClassName, false, clToUse);
                } catch (ClassNotFoundException e) {
                    // Swallow - let original exception get through
                }
            }
            throw ex;
        }
    }
    
    /**
     * Resolve the given class name as primitive class, if appropriate, according to the JVM's naming rules for
     * primitive classes.
     *
     * <p>Also supports the JVM's internal class names for primitive arrays.
     * Does <i>not</i> support the "[]" suffix notation for primitive arrays; this is only supported by {@link
     * #forName(String, ClassLoader)}.
     *
     * @param name the name of the potentially primitive class
     * @return the primitive class, or {@code null} if the name does not denote a primitive class or primitive array
     * class
     */
    
    public static Class<?> resolvePrimitiveClassName(String name) {
        Class<?> result = null;
        // Most class names will be quite long, considering that they
        // SHOULD sit in a package, so a length check is worthwhile.
        if (name != null && name.length() <= PRIMITIVE_TYPE_NAME_MAP_LENGTH) {
            // Could be a primitive - likely.
            result = PRIMITIVE_TYPE_NAME_MAP.get(name);
        }
        return result;
    }
    
    /**
     * Return the default ClassLoader to use: typically the thread context ClassLoader, if available; the ClassLoader
     * that loaded the ClassUtils class will be used as fallback.
     *
     * <p>Call this method if you intend to use the thread context ClassLoader
     * in a scenario where you clearly prefer a non-null ClassLoader reference: for example, for class path resource
     * loading (but not necessarily for {@code Class.forName}, which accepts a {@code null} ClassLoader reference as
     * well).
     *
     * @return the default ClassLoader (only {@code null} if even the system ClassLoader isn't accessible)
     * @see Thread#getContextClassLoader()
     * @see ClassLoader#getSystemClassLoader()
     */
    
    public static ClassLoader getDefaultClassLoader() {
        ClassLoader cl = null;
        try {
            cl = Thread.currentThread().getContextClassLoader();
        } catch (Throwable ex) {
            // Cannot access thread context ClassLoader - falling back...
        }
        if (cl == null) {
            // No thread context class loader -> use class loader of this class.
            cl = ClassUtils.class.getClassLoader();
            if (cl == null) {
                // getClassLoader() returning null indicates the bootstrap ClassLoader
                try {
                    cl = ClassLoader.getSystemClassLoader();
                } catch (Throwable ex) {
                    // Cannot access system ClassLoader - oh well, maybe the caller can live with null...
                }
            }
        }
        return cl;
    }
    
    /**
     * Given an input class object, return a string which consists of the class's package name as a pathname, i.e., all
     * dots ('.') are replaced by slashes ('/'). Neither a leading nor trailing slash is added. The result could be
     * concatenated with a slash and the name of a resource and fed directly to {@code ClassLoader.getResource()}. For
     * it to be fed to {@code Class.getResource} instead, a leading slash would also have to be prepended to the
     * returned value.
     *
     * @param clazz the input class. A {@code null} value or the default (empty) package will result in an empty string
     *              ("") being returned.
     * @return a path which represents the package name
     * @see ClassLoader#getResource
     * @see Class#getResource
     */
    public static String classPackageAsResourcePath(Class<?> clazz) {
        if (clazz == null) {
            return "";
        }
        String className = clazz.getName();
        int packageEndIndex = className.lastIndexOf(PACKAGE_SEPARATOR);
        if (packageEndIndex == -1) {
            return "";
        }
        String packageName = className.substring(0, packageEndIndex);
        return packageName.replace(PACKAGE_SEPARATOR, PATH_SEPARATOR);
    }
    
    /**
     * Convert a "."-based fully qualified class name to a "/"-based resource path.
     *
     * @param className the fully qualified class name
     * @return the corresponding resource path, pointing to the class
     */
    public static String convertClassNameToResourcePath(String className) {
        AbstractAssert.notNull(className, "Class name must not be null");
        return className.replace(PACKAGE_SEPARATOR, PATH_SEPARATOR);
    }
    
    /**
     * Convert a "."-based fully qualified class name to a "/"-based resource path.
     *
     * @param className the fully qualified class name
     * @return the corresponding resource path, pointing to the class
     */
    public static String resourcePathToConvertClassName(String className) {
        AbstractAssert.notNull(className, "Class name must not be null");
        return className.replace(PATH_SEPARATOR, PACKAGE_SEPARATOR);
    }
}
