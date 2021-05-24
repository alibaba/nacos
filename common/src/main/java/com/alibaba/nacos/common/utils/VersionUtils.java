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

import java.io.InputStream;
import java.util.Comparator;
import java.util.Properties;

/**
 * Version utils.
 *
 * @author xingxuechao on:2019/2/27 12:32 PM
 */
public class VersionUtils {
    
    public static String version;
    
    private static String clientVersion;
    
    /**
     * current version.
     */
    public static final String VERSION_PLACEHOLDER = "${project.version}";
    
    private static final String NACOS_VERSION_FILE = "nacos-version.txt";
    
    static {
        InputStream in = null;
        try {
            in = VersionUtils.class.getClassLoader().getResourceAsStream(NACOS_VERSION_FILE);
            Properties props = new Properties();
            props.load(in);
            String val = props.getProperty("version");
            if (val != null && !VERSION_PLACEHOLDER.equals(val)) {
                version = val;
                clientVersion = "Nacos-Java-Client:v" + VersionUtils.version;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    private static final Comparator<String> STRING_COMPARATOR = new Comparator<String>() {
        @Override
        public int compare(String o1, String o2) {
            return o1.compareTo(o2);
        }
    };
    
    /**
     * compare two version who is latest.
     *
     * @param versionA version A, like x.y.z(-beta)
     * @param versionB version B, like x.y.z(-beta)
     * @return compare result
     */
    public static int compareVersion(final String versionA, final String versionB) {
        final String[] sA = versionA.split("\\.");
        final String[] sB = versionB.split("\\.");
        int expectSize = 3;
        if (sA.length != expectSize || sB.length != expectSize) {
            throw new IllegalArgumentException("version must be like x.y.z(-beta)");
        }
        int first = Objects.compare(sA[0], sB[0], STRING_COMPARATOR);
        if (first != 0) {
            return first;
        }
        int second = Objects.compare(sA[1], sB[1], STRING_COMPARATOR);
        if (second != 0) {
            return second;
        }
        return Objects.compare(sA[2].split("-")[0], sB[2].split("-")[0], STRING_COMPARATOR);
    }
    
    public static String getFullClientVersion() {
        return clientVersion;
    }
}
