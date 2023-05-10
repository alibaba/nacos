/*
 * Copyright 1999-2023 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.core.namespace.injector;

import com.alibaba.nacos.core.namespace.model.Namespace;

import java.util.HashSet;
import java.util.Set;

/**
 * Holder of namespace detail injector.
 *
 * @author xiweng.yy
 */
public class NamespaceDetailInjectorHolder {
    
    private static final NamespaceDetailInjectorHolder INSTANCE = new NamespaceDetailInjectorHolder();
    
    private final Set<AbstractNamespaceDetailInjector> namespaceDetailInjectors;
    
    private NamespaceDetailInjectorHolder() {
        this.namespaceDetailInjectors = new HashSet<>();
    }
    
    public static NamespaceDetailInjectorHolder getInstance() {
        return INSTANCE;
    }
    
    public void registerInjector(AbstractNamespaceDetailInjector injector) {
        this.namespaceDetailInjectors.add(injector);
    }
    
    /**
     * Inject detail from all injectors.
     *
     * @param namespace namespace
     */
    public void injectDetail(Namespace namespace) {
        for (AbstractNamespaceDetailInjector each : this.namespaceDetailInjectors) {
            each.injectDetail(namespace);
        }
    }
}
