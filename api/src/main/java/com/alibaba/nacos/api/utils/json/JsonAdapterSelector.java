/*
 * Copyright 1999-2026 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.api.utils.json;

import com.alibaba.nacos.api.exception.runtime.NacosLoadException;
import com.alibaba.nacos.api.utils.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Selects the active JSON adapter.
 *
 * @author nacos
 */
final class JsonAdapterSelector {
    
    static final String ADAPTER_PROPERTY_NAME = "nacos.client.json.adapter";
    
    private final List<NacosJsonAdapter> adapters;
    
    private final List<String> loadFailures;
    
    private volatile NacosJsonAdapter selectedAdapter;
    
    JsonAdapterSelector() {
        this(loadAdapters());
    }
    
    private JsonAdapterSelector(LoadedAdapters loadedAdapters) {
        this(loadedAdapters.adapters, loadedAdapters.loadFailures);
    }
    
    JsonAdapterSelector(Collection<NacosJsonAdapter> adapters) {
        this(adapters, Collections.<String>emptyList());
    }
    
    JsonAdapterSelector(Collection<NacosJsonAdapter> adapters, Collection<String> loadFailures) {
        this.adapters = new ArrayList<NacosJsonAdapter>(adapters);
        this.loadFailures = new ArrayList<String>(loadFailures);
    }
    
    NacosJsonAdapter select() {
        NacosJsonAdapter adapter = selectedAdapter;
        if (adapter != null) {
            return adapter;
        }
        synchronized (this) {
            if (selectedAdapter == null) {
                selectedAdapter = doSelect();
            }
            return selectedAdapter;
        }
    }
    
    NacosJsonAdapter getSelectedAdapter() {
        return selectedAdapter;
    }
    
    private NacosJsonAdapter doSelect() {
        String configuredName = configuredAdapterName();
        List<NacosJsonAdapter> availableAdapters = availableAdapters();
        if (!NacosJsonAdapterNames.AUTO.equals(configuredName)) {
            return selectExplicit(configuredName, availableAdapters);
        }
        return selectAuto(availableAdapters);
    }
    
    private String configuredAdapterName() {
        String configured = System.getProperty(ADAPTER_PROPERTY_NAME);
        if (StringUtils.isBlank(configured)) {
            return NacosJsonAdapterNames.AUTO;
        }
        return configured.trim().toLowerCase(Locale.ROOT);
    }
    
    private List<NacosJsonAdapter> availableAdapters() {
        List<NacosJsonAdapter> result = new ArrayList<NacosJsonAdapter>();
        for (NacosJsonAdapter adapter : adapters) {
            if (isAdapterAvailable(adapter)) {
                result.add(adapter);
            }
        }
        return result;
    }
    
    private boolean isAdapterAvailable(NacosJsonAdapter adapter) {
        try {
            return adapter != null && adapter.isAvailable();
        } catch (ServiceConfigurationError e) {
            loadFailures.add(formatFailure(adapter, e));
            return false;
        } catch (LinkageError e) {
            loadFailures.add(formatFailure(adapter, e));
            return false;
        } catch (RuntimeException e) {
            loadFailures.add(formatFailure(adapter, e));
            return false;
        }
    }
    
    private NacosJsonAdapter selectExplicit(String name, List<NacosJsonAdapter> availableAdapters) {
        if (!NacosJsonAdapterNames.JACKSON2.equals(name)
            && !NacosJsonAdapterNames.JACKSON3.equals(name)) {
            throw unavailable("Unsupported JSON adapter '" + name + "'.");
        }
        for (NacosJsonAdapter adapter : availableAdapters) {
            if (name.equals(normalize(adapter.name()))) {
                return adapter;
            }
        }
        throw unavailable("Configured JSON adapter '" + name + "' is not available.");
    }
    
    private NacosJsonAdapter selectAuto(List<NacosJsonAdapter> availableAdapters) {
        if (availableAdapters.isEmpty()) {
            throw unavailable("No available JSON adapter found.");
        }
        NacosJsonAdapter jackson3 = findByName(availableAdapters, NacosJsonAdapterNames.JACKSON3);
        if (jackson3 != null) {
            return jackson3;
        }
        NacosJsonAdapter jackson2 = findByName(availableAdapters, NacosJsonAdapterNames.JACKSON2);
        if (jackson2 != null) {
            return jackson2;
        }
        if (availableAdapters.size() == 1) {
            return availableAdapters.get(0);
        }
        throw unavailable("Multiple JSON adapters are available but none is preferred.");
    }
    
    private NacosJsonAdapter findByName(List<NacosJsonAdapter> availableAdapters, String name) {
        for (NacosJsonAdapter adapter : availableAdapters) {
            if (name.equals(normalize(adapter.name()))) {
                return adapter;
            }
        }
        return null;
    }
    
    private String normalize(String name) {
        return name == null ? StringUtils.EMPTY : name.trim().toLowerCase(Locale.ROOT);
    }
    
    private NacosLoadException unavailable(String reason) {
        StringBuilder message = new StringBuilder(reason);
        message.append(" Please add nacos-common or a custom NacosJsonAdapter provider");
        message.append(" to the runtime classpath.");
        if (!loadFailures.isEmpty()) {
            message.append(" Adapter diagnostics: ").append(loadFailures);
        }
        return new NacosLoadException(message.toString());
    }
    
    private String formatFailure(NacosJsonAdapter adapter, Throwable throwable) {
        String adapterName = adapter == null ? "unknown" : adapter.name();
        return adapterName + ": " + throwable.getClass().getName() + ": "
            + throwable.getMessage();
    }
    
    private static LoadedAdapters loadAdapters() {
        List<NacosJsonAdapter> result = new ArrayList<NacosJsonAdapter>();
        List<String> failures = new ArrayList<String>();
        ServiceLoader<NacosJsonAdapter> loader = ServiceLoader.load(NacosJsonAdapter.class);
        Iterator<NacosJsonAdapter> iterator = loader.iterator();
        while (true) {
            try {
                if (!iterator.hasNext()) {
                    break;
                }
                result.add(iterator.next());
            } catch (ServiceConfigurationError e) {
                failures.add(formatProviderFailure(e));
                break;
            } catch (LinkageError e) {
                failures.add(formatProviderFailure(e));
                break;
            }
        }
        return new LoadedAdapters(result, failures);
    }
    
    private static String formatProviderFailure(Throwable throwable) {
        return "provider: " + throwable.getClass().getName() + ": " + throwable.getMessage();
    }
    
    private static final class LoadedAdapters {
        
        private final List<NacosJsonAdapter> adapters;
        
        private final List<String> loadFailures;
        
        private LoadedAdapters(List<NacosJsonAdapter> adapters, List<String> loadFailures) {
            this.adapters = adapters;
            this.loadFailures = loadFailures;
        }
    }
}
