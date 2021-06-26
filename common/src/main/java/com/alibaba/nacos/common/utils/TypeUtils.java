/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
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

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;

/**
 * Type utils.
 * @author zzq
 */
public class TypeUtils {
    
    /**
     * Create a parameterized type instance.
     *
     * @param raw the raw class to create a parameterized type instance for
     * @param typeArguments the types used for parameterization
     * @return {@link ParameterizedType}
     * @since 3.2
     */
    public static final ParameterizedType parameterize(final Class<?> raw, final Type... typeArguments) {
        return parameterizeWithOwner(null, raw, typeArguments);
    }
    
    /**
     * Create a parameterized type instance.
     *
     * @param owner the owning type
     * @param raw the raw class to create a parameterized type instance for
     * @param typeArguments the types used for parameterization
     *
     * @return {@link ParameterizedType}
     * @since 3.2
     */
    public static final ParameterizedType parameterizeWithOwner(final Type owner, final Class<?> raw,
            final Type... typeArguments) {
        Validate.notNull(raw, "raw class is null");
        final Type useOwner;
        if (raw.getEnclosingClass() == null) {
            Validate.isTrue(owner == null, "no owner allowed for top-level %s", raw);
            useOwner = null;
        } else if (owner == null) {
            useOwner = raw.getEnclosingClass();
        } else {
            Validate.isTrue(TypeUtils.isAssignable(owner, raw.getEnclosingClass()),
                    "%s is invalid owner type for parameterized %s", owner, raw);
            useOwner = owner;
        }
        Validate.noNullElements(typeArguments, "null type argument at index %s");
        Validate.isTrue(raw.getTypeParameters().length == typeArguments.length,
                "invalid number of type parameters specified: expected %s, got %s", raw.getTypeParameters().length,
                typeArguments.length);
        
        return new ParameterizedTypeImpl(raw, useOwner, typeArguments);
    }
    
    /**
     * <p>Checks if the subject type may be implicitly cast to the target class
     * following the Java generics rules.</p>
     *
     * @param type the subject type to be assigned to the target type
     * @param toClass the target class
     * @return {@code true} if {@code type} is assignable to {@code toClass}.
     */
    private static boolean isAssignable(final Type type, final Class<?> toClass) {
        if (type == null) {
            // consistency with ClassUtils.isAssignable() behavior
            return toClass == null || !toClass.isPrimitive();
        }
        
        // only a null type can be assigned to null type which
        // would have cause the previous to return true
        if (toClass == null) {
            return false;
        }
        
        // all types are assignable to themselves
        if (toClass.equals(type)) {
            return true;
        }
        
        if (type instanceof Class<?>) {
            // just comparing two classes
            return ClassUtils.isAssignable((Class<?>) type, toClass);
        }
        
        if (type instanceof ParameterizedType) {
            // only have to compare the raw type to the class
            return isAssignable(getRawType((ParameterizedType) type), toClass);
        }
        
        // *
        if (type instanceof TypeVariable<?>) {
            // if any of the bounds are assignable to the class, then the
            // type is assignable to the class.
            for (final Type bound : ((TypeVariable<?>) type).getBounds()) {
                if (isAssignable(bound, toClass)) {
                    return true;
                }
            }
            
            return false;
        }
        
        // the only classes to which a generic array type can be assigned
        // are class Object and array classes
        if (type instanceof GenericArrayType) {
            return toClass.equals(Object.class)
                    || toClass.isArray()
                    && isAssignable(((GenericArrayType) type).getGenericComponentType(), toClass
                    .getComponentType());
        }
        
        // wildcard types are not assignable to a class (though one would think
        // "? super Object" would be assignable to Object)
        if (type instanceof WildcardType) {
            return false;
        }
        
        throw new IllegalStateException("found an unhandled type: " + type);
    }
    
    /**
     * ParameterizedType implementation class.
     * @since 3.2
     */
    private static final class ParameterizedTypeImpl implements ParameterizedType {
        
        private final Class<?> raw;
        
        private final Type useOwner;
        
        private final Type[] typeArguments;
        
        /**
         * Constructor.
         * @param raw type
         * @param useOwner owner type to use, if any
         * @param typeArguments formal type arguments
         */
        private ParameterizedTypeImpl(final Class<?> raw, final Type useOwner, final Type[] typeArguments) {
            this.raw = raw;
            this.useOwner = useOwner;
            this.typeArguments = typeArguments;
        }
        
        /**
         * {@inheritDoc}
         */
        @Override
        public Type getRawType() {
            return raw;
        }
        
        /**
         * {@inheritDoc}
         */
        @Override
        public Type getOwnerType() {
            return useOwner;
        }
        
        /**
         * {@inheritDoc}
         */
        @Override
        public Type[] getActualTypeArguments() {
            return typeArguments.clone();
        }
        
        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return TypeUtils.toString(this);
        }
        
        /**
         * {@inheritDoc}
         */
        @Override
        public boolean equals(final Object obj) {
            return obj == this || obj instanceof ParameterizedType && TypeUtils.equals(this, ((ParameterizedType) obj));
        }
        
        /**
         * {@inheritDoc}
         */
        @SuppressWarnings("deprecation")  // ObjectUtils.hashCode(Object) has been deprecated in 3.2
        @Override
        public int hashCode() {
            int result = 71 << 4;
            result |= raw.hashCode();
            result <<= 4;
            result |= ObjectUtils.hashCode(useOwner);
            result <<= 8;
            result |= Arrays.hashCode(typeArguments);
            return result;
        }
    }
    
    /**
     * <p>Transforms the passed in type to a {@link Class} object. Type-checking method of convenience.</p>
     *
     * @param parameterizedType the type to be converted
     * @return the corresponding {@code Class} object
     * @throws IllegalStateException if the conversion fails
     */
    private static Class<?> getRawType(final ParameterizedType parameterizedType) {
        final Type rawType = parameterizedType.getRawType();
        
        // check if raw type is a Class object
        // not currently necessary, but since the return type is Type instead of
        // Class, there's enough reason to believe that future versions of Java
        // may return other Type implementations. And type-safety checking is
        // rarely a bad idea.
        if (!(rawType instanceof Class<?>)) {
            throw new IllegalStateException("Wait... What!? Type of rawType: " + rawType);
        }
        
        return (Class<?>) rawType;
    }
    
    /**
     * Present a given type as a Java-esque String.
     *
     * @param type the type to create a String representation for, not {@code null}
     * @return String
     * @since 3.2
     */
    public static String toString(final Type type) {
        Validate.notNull(type);
        if (type instanceof Class<?>) {
            return classToString((Class<?>) type);
        }
        if (type instanceof ParameterizedType) {
            return parameterizedTypeToString((ParameterizedType) type);
        }
        if (type instanceof WildcardType) {
            return wildcardTypeToString((WildcardType) type);
        }
        if (type instanceof TypeVariable<?>) {
            return typeVariableToString((TypeVariable<?>) type);
        }
        if (type instanceof GenericArrayType) {
            return genericArrayTypeToString((GenericArrayType) type);
        }
        throw new IllegalArgumentException(ObjectUtils.identityToString(type));
    }
    
    /**
     * Format a {@link TypeVariable} as a {@link String}.
     * @param v {@code TypeVariable} to format
     * @return String
     * @since 3.2
     */
    private static String typeVariableToString(final TypeVariable<?> v) {
        final StringBuilder buf = new StringBuilder(v.getName());
        final Type[] bounds = v.getBounds();
        if (typeVariableToStringCondition(bounds)) {
            buf.append(" extends ");
            appendAllTo(buf, " & ", v.getBounds());
        }
        return buf.toString();
    }
    
    private static Boolean typeVariableToStringCondition(Type[] bounds) {
        return bounds.length > 0 && !(bounds.length == 1 && Object.class.equals(bounds[0]));
    }
    
    /**
     * Format a {@link GenericArrayType} as a {@link String}.
     * @param g {@code GenericArrayType} to format
     * @return String
     * @since 3.2
     */
    private static String genericArrayTypeToString(final GenericArrayType g) {
        return String.format("%s[]", toString(g.getGenericComponentType()));
    }
    
    /**
     * Format a {@link WildcardType} as a {@link String}.
     * @param w {@code WildcardType} to format
     * @return String
     * @since 3.2
     */
    private static String wildcardTypeToString(final WildcardType w) {
        final StringBuilder buf = new StringBuilder().append('?');
        final Type[] lowerBounds = w.getLowerBounds();
        final Type[] upperBounds = w.getUpperBounds();
        if (typeVariableToStringCondition1(lowerBounds)) {
            appendAllTo(buf.append(" super "), " & ", lowerBounds);
        } else if (typeVariableToStringCondition2(upperBounds)) {
            appendAllTo(buf.append(" extends "), " & ", upperBounds);
        }
        return buf.toString();
    }
    
    private static Boolean typeVariableToStringCondition1(Type[] lowerBounds) {
        return lowerBounds.length > 1 || lowerBounds.length == 1 && lowerBounds[0] != null;
    }
    
    private static Boolean typeVariableToStringCondition2(Type[] upperBounds) {
        return upperBounds.length > 1 || upperBounds.length == 1 && !Object.class.equals(upperBounds[0]);
    }
    
    /**
     * Format a {@link ParameterizedType} as a {@link String}.
     * @param p {@code ParameterizedType} to format
     * @return String
     * @since 3.2
     */
    private static String parameterizedTypeToString(final ParameterizedType p) {
        final StringBuilder buf = new StringBuilder();
        
        final Type useOwner = p.getOwnerType();
        final Class<?> raw = (Class<?>) p.getRawType();
        final Type[] typeArguments = p.getActualTypeArguments();
        if (useOwner == null) {
            buf.append(raw.getName());
        } else {
            if (useOwner instanceof Class<?>) {
                buf.append(((Class<?>) useOwner).getName());
            } else {
                buf.append(useOwner.toString());
            }
            buf.append('.').append(raw.getSimpleName());
        }
        
        appendAllTo(buf.append('<'), ", ", typeArguments).append('>');
        return buf.toString();
    }
    
    /**
     * Format a {@link Class} as a {@link String}.
     * @param c {@code Class} to format
     * @return String
     * @since 3.2
     */
    private static String classToString(final Class<?> c) {
        final StringBuilder buf = new StringBuilder();
        
        if (c.getEnclosingClass() != null) {
            buf.append(classToString(c.getEnclosingClass())).append('.').append(c.getSimpleName());
        } else {
            buf.append(c.getName());
        }
        if (c.getTypeParameters().length > 0) {
            buf.append('<');
            appendAllTo(buf, ", ", c.getTypeParameters());
            buf.append('>');
        }
        return buf.toString();
    }
    
    /**
     * Append {@code types} to @{code buf} with separator {@code sep}.
     * @param buf destination
     * @param sep separator
     * @param types to append
     * @return {@code buf}
     * @since 3.2
     */
    private static StringBuilder appendAllTo(final StringBuilder buf, final String sep, final Type... types) {
        Validate.notEmpty(Validate.noNullElements(types));
        if (types.length > 0) {
            buf.append(toString(types[0]));
            for (int i = 1; i < types.length; i++) {
                buf.append(sep).append(toString(types[i]));
            }
        }
        return buf;
    }
    
    /**
     * Check equality of types.
     * @param t1 the first type
     * @param t2 the second type
     * @return boolean
     * @since 3.2
     */
    @SuppressWarnings("deprecation")
    public static boolean equals(final Type t1, final Type t2) {
        if (ObjectUtils.equals(t1, t2)) {
            return true;
        }
        if (t1 instanceof ParameterizedType) {
            return equals((ParameterizedType) t1, t2);
        }
        if (t1 instanceof GenericArrayType) {
            return equals((GenericArrayType) t1, t2);
        }
        if (t1 instanceof WildcardType) {
            return equals((WildcardType) t1, t2);
        }
        return false;
    }
    
}
