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

package com.alibaba.nacos.common.packagescan;

import com.alibaba.nacos.common.notify.NotifyCenter;
import com.alibaba.nacos.common.packagescan.classreading.ClassReader;
import com.alibaba.nacos.common.packagescan.util.NestedIoException;
import com.alibaba.nacos.common.utils.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Set;

/**
 * package scan.
 * @author hujun
 */
public class PackageScan {

    protected static final Logger LOGGER = LoggerFactory.getLogger(NotifyCenter.class);

    private final PathMatchingResourcePatternResolver resourcePatternResolver = new PathMatchingResourcePatternResolver();

    private String pkg;

    public PackageScan(String pkg) {
        this.pkg = pkg;
    }

    public <T> Set<Class<T>> getSubTypesOf(Class<T> requestClass) {
        Set<Class<T>> set = new HashSet<>(16);
        String packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
                + ClassUtils.convertClassNameToResourcePath(pkg) + '/' + "**/*.class";
        try {
            Resource[] resources = resourcePatternResolver.getResources(packageSearchPath);
            for (Resource resource : resources) {
                Class<?> scanClass = getClassByResource(resource);
                if (requestClass.isAssignableFrom(scanClass)) {
                    set.add((Class<T>) scanClass);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            LOGGER.error("scan path: {} failed", packageSearchPath, e);
        }
        return set;
    }

    public <T> Set<Class<T>> getTypesAnnotatedWith(final Class<? extends Annotation> annotation) {
        Set<Class<T>> set = new HashSet<>(16);
        String packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX
                + ClassUtils.convertClassNameToResourcePath(pkg) + '/' + "**/*.class";
        try {
            Resource[] resources = resourcePatternResolver.getResources(packageSearchPath);
            for (Resource resource : resources) {
                Class<?> scanClass = getClassByResource(resource);
                if (scanClass.isAnnotationPresent(annotation)) {
                    set.add((Class<T>) scanClass);
                }
            }
        } catch (IOException e) {
            LOGGER.error("scan path: {} failed", packageSearchPath, e);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return set;
    }

    private Class<?> getClassByResource(Resource resource) throws IOException, ClassNotFoundException {
        String className = getClassReader(resource).getClassName();
        return Class.forName(ClassUtils.resourcePathToConvertClassName(className));
    }

    private static ClassReader getClassReader(Resource resource) throws IOException {
        try (InputStream is = resource.getInputStream()) {
            try {
                return new ClassReader(is);
            } catch (IllegalArgumentException ex) {
                throw new NestedIoException("ASM ClassReader failed to parse class file - "
                        + "probably due to a new Java class file version that isn't supported yet: " + resource, ex);
            }
        }
    }

}
