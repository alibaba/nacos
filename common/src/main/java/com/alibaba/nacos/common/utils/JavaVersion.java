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

import com.alibaba.nacos.common.constant.JavaVersionConstant;

/**
 * Java version utils.
 * @author zzq
 */
public enum  JavaVersion {
    
    /**
     * The Java version reported by Android. This is not an official Java version number.
     */
    JAVA_0_9(1.5f, JavaVersionConstant.JAVA_09),
    
    /**
     * Java 1.1.
     */
    JAVA_1_1(1.1f, JavaVersionConstant.JAVA_11),
    
    /**
     * Java 1.2.
     */
    JAVA_1_2(1.2f, JavaVersionConstant.JAVA_12),
    
    /**
     * Java 1.3.
     */
    JAVA_1_3(1.3f, JavaVersionConstant.JAVA_13),
    
    /**
     * Java 1.4.
     */
    JAVA_1_4(1.4f, JavaVersionConstant.JAVA_14),
    
    /**
     * Java 1.5.
     */
    JAVA_1_5(1.5f, JavaVersionConstant.JAVA_15),
    
    /**
     * Java 1.6.
     */
    JAVA_1_6(1.6f, JavaVersionConstant.JAVA_16),
    
    /**
     * Java 1.7.
     */
    JAVA_1_7(1.7f, JavaVersionConstant.JAVA_17),
    
    /**
     * Java 1.8.
     */
    JAVA_1_8(1.8f, JavaVersionConstant.JAVA_18),
    
    /**
     * Java 1.9.
     */
    JAVA_1_9(1.9f, JavaVersionConstant.JAVA_19),
    
    /**
     * Java 1.x, x &gt; 9. Mainly introduced to avoid to break when a new version of Java is used.
     */
    JAVA_RECENT(maxVersion(), Float.toString(maxVersion()));
    
    /**
     * The float value.
     */
    private final float value;
    /**
     * The standard name.
     */
    private final String name;
    
    /**
     * Constructor.
     *
     * @param value  the float value
     * @param name  the standard name, not null
     */
    JavaVersion(final float value, final String name) {
        this.value = value;
        this.name = name;
    }
    
    //-----------------------------------------------------------------------
    /**
     * <p>Whether this version of Java is at least the version of Java passed in.</p>
     *
     * @param requiredVersion  the version to check against, not null
     * @return true if this version is equal to or greater than the specified version
     */
    public boolean atLeast(final JavaVersion requiredVersion) {
        return this.value >= requiredVersion.value;
    }
    
    /**
     * Transforms the given string with a Java version number to the
     * corresponding constant of this enumeration class. This method is used
     * internally.
     *
     * @param nom the Java version as string
     * @return the corresponding enumeration constant or <b>null</b> if the
     * version is unknown
     */
    static JavaVersion getJavaVersion(final String nom) {
        return get(nom);
    }
    
    /**
     * Transforms the given string with a Java version number to the
     * corresponding constant of this enumeration class. This method is used
     * internally.
     *
     * @param nom the Java version as string
     * @return the corresponding enumeration constant or <b>null</b> if the
     * version is unknown
     */
    static JavaVersion get(final String nom) {
        if (JavaVersionConstant.JAVA_09.equals(nom)) {
            return JAVA_0_9;
        } else if (JavaVersionConstant.JAVA_11.equals(nom)) {
            return JAVA_1_1;
        } else if (JavaVersionConstant.JAVA_12.equals(nom)) {
            return JAVA_1_2;
        } else if (JavaVersionConstant.JAVA_13.equals(nom)) {
            return JAVA_1_3;
        } else if (JavaVersionConstant.JAVA_14.equals(nom)) {
            return JAVA_1_4;
        } else if (JavaVersionConstant.JAVA_15.equals(nom)) {
            return JAVA_1_5;
        } else if (JavaVersionConstant.JAVA_16.equals(nom)) {
            return JAVA_1_6;
        } else if (JavaVersionConstant.JAVA_17.equals(nom)) {
            return JAVA_1_7;
        } else if (JavaVersionConstant.JAVA_18.equals(nom)) {
            return JAVA_1_8;
        } else if (JavaVersionConstant.JAVA_19.equals(nom)) {
            return JAVA_1_9;
        }
        if (nom == null) {
            return null;
        }
        final float v = toFloatVersion(nom);
        // then we need to check decimals > .9
        if ((v - JavaVersionConstant.JAVA_1X) < JavaVersionConstant.JAVA_1X) {
            final int firstComma = Math.max(nom.indexOf('.'), nom.indexOf(','));
            final int end = Math.max(nom.length(), nom.indexOf(',', firstComma));
            if (Float.parseFloat(nom.substring(firstComma + 1, end)) > JavaVersionConstant.JAVA_X9) {
                return JAVA_RECENT;
            }
        }
        return null;
    }
    
    //-----------------------------------------------------------------------
    /**
     * <p>The string value is overridden to return the standard name.</p>
     *
     * <p>For example, <code>"1.5"</code>.</p>
     *
     * @return the name, not null
     */
    @Override
    public String toString() {
        return name;
    }
    
    /**
     * Gets the Java Version from the system or 2.0 if the {@code java.version} system property is not set.
     *
     * @return the value of {@code java.version} system property or 2.0 if it is not set.
     */
    private static float maxVersion() {
        final float v = toFloatVersion(System.getProperty("java.version", "2.0"));
        if (v > 0) {
            return v;
        }
        return 2f;
    }
    
    /**
     * Parses a float value from a String.
     *
     * @param value the String to parse.
     * @return the float value represented by teh string or -1 if the given String can not be parsed.
     */
    private static float toFloatVersion(final String value) {
        final String[] toParse = value.split("\\.");
        if (toParse.length >= JavaVersionConstant.JAVA_2) {
            try {
                return Float.parseFloat(toParse[0] + '.' + toParse[1]);
            } catch (final NumberFormatException nfe) {
                // no-op, let use default
            }
        }
        return -1;
    }
}
