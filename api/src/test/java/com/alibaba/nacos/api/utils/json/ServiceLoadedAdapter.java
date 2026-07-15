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

import java.io.InputStream;
import java.lang.reflect.Type;

/**
 * Test service provider for {@link JsonAdapterSelector}.
 *
 * @author nacos
 */
public class ServiceLoadedAdapter implements NacosJsonAdapter {
    
    @Override
    public String name() {
        return "service-loaded";
    }
    
    @Override
    public boolean isAvailable() {
        return true;
    }
    
    @Override
    public String toJson(Object obj) {
        return null;
    }
    
    @Override
    public byte[] toJsonBytes(Object obj) {
        return new byte[0];
    }
    
    @Override
    public String toCanonicalJson(Object obj) {
        return null;
    }
    
    @Override
    public <T> T toObj(byte[] json, Class<T> cls) {
        return null;
    }
    
    @Override
    public <T> T toObj(byte[] json, Type type) {
        return null;
    }
    
    @Override
    public <T> T toObj(byte[] json, NacosTypeReference<T> typeReference) {
        return null;
    }
    
    @Override
    public <T> T toObj(String json, Class<T> cls) {
        return null;
    }
    
    @Override
    public <T> T toObj(String json, Type type) {
        return null;
    }
    
    @Override
    public <T> T toObj(String json, NacosTypeReference<T> typeReference) {
        return null;
    }
    
    @Override
    public <T> T toObj(InputStream inputStream, Class<T> cls) {
        return null;
    }
    
    @Override
    public <T> T toObj(InputStream inputStream, Type type) {
        return null;
    }
    
    @Override
    public void registerSubtype(NacosJsonSubtype subtype) {
    }
}
