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

package com.alibaba.nacos.common.json;

import com.alibaba.nacos.api.exception.runtime.NacosLoadException;
import com.alibaba.nacos.api.utils.json.NacosJsonAdapter;
import com.alibaba.nacos.api.utils.json.NacosJsonAdapterNames;
import com.alibaba.nacos.api.utils.json.NacosJsonSubtype;
import com.alibaba.nacos.api.utils.json.NacosTypeReference;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceConfigurationError;

/**
 * Java 8 safe Jackson 3 provider facade.
 *
 * @author nacos
 */
public class Jackson3JsonAdapter implements NacosJsonAdapter {
    
    private static final String JACKSON3_OBJECT_MAPPER_CLASS =
        "tools.jackson.databind.ObjectMapper";
    
    private static final String JACKSON3_EXCEPTION_CLASS = "tools.jackson.core.JacksonException";
    
    private final ClassLoader classLoader;
    
    private final List<NacosJsonSubtype> subtypes = new ArrayList<NacosJsonSubtype>();
    
    private volatile NacosJsonAdapter delegate;
    
    public Jackson3JsonAdapter() {
        this(Jackson3JsonAdapter.class.getClassLoader());
    }
    
    Jackson3JsonAdapter(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }
    
    @Override
    public String name() {
        return NacosJsonAdapterNames.JACKSON3;
    }
    
    @Override
    public boolean isAvailable() {
        try {
            Class.forName(JACKSON3_OBJECT_MAPPER_CLASS, false, classLoader);
            Class.forName(JACKSON3_EXCEPTION_CLASS, false, classLoader);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (ServiceConfigurationError e) {
            return false;
        } catch (LinkageError e) {
            return false;
        } catch (RuntimeException e) {
            return false;
        }
    }
    
    @Override
    public String toJson(Object obj) {
        return delegate().toJson(obj);
    }
    
    @Override
    public byte[] toJsonBytes(Object obj) {
        return delegate().toJsonBytes(obj);
    }
    
    @Override
    public String toCanonicalJson(Object obj) {
        return delegate().toCanonicalJson(obj);
    }
    
    @Override
    public <T> T toObj(byte[] json, Class<T> cls) {
        return delegate().toObj(json, cls);
    }
    
    @Override
    public <T> T toObj(byte[] json, Type type) {
        return delegate().toObj(json, type);
    }
    
    @Override
    public <T> T toObj(byte[] json, NacosTypeReference<T> typeReference) {
        return delegate().toObj(json, typeReference);
    }
    
    @Override
    public <T> T toObj(String json, Class<T> cls) {
        return delegate().toObj(json, cls);
    }
    
    @Override
    public <T> T toObj(String json, Type type) {
        return delegate().toObj(json, type);
    }
    
    @Override
    public <T> T toObj(String json, NacosTypeReference<T> typeReference) {
        return delegate().toObj(json, typeReference);
    }
    
    @Override
    public <T> T toObj(InputStream inputStream, Class<T> cls) {
        return delegate().toObj(inputStream, cls);
    }
    
    @Override
    public <T> T toObj(InputStream inputStream, Type type) {
        return delegate().toObj(inputStream, type);
    }
    
    @Override
    public void registerSubtype(NacosJsonSubtype subtype) {
        synchronized (subtypes) {
            if (!subtypes.contains(subtype)) {
                subtypes.add(subtype);
            }
            NacosJsonAdapter currentDelegate = delegate;
            if (currentDelegate != null) {
                currentDelegate.registerSubtype(subtype);
            }
        }
    }
    
    private NacosJsonAdapter delegate() {
        NacosJsonAdapter currentDelegate = delegate;
        if (currentDelegate != null) {
            return currentDelegate;
        }
        synchronized (this) {
            if (delegate == null) {
                if (!isAvailable()) {
                    throw new NacosLoadException(
                        "Jackson 3 is not available on the runtime classpath.");
                }
                delegate = createDelegate();
            }
            return delegate;
        }
    }
    
    private NacosJsonAdapter createDelegate() {
        NacosJsonAdapter createdDelegate = new Jackson3JsonAdapterDelegate();
        synchronized (subtypes) {
            for (NacosJsonSubtype subtype : subtypes) {
                createdDelegate.registerSubtype(subtype);
            }
        }
        return createdDelegate;
    }
}
