/*
 * Copyright 1999-2022 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.client.env;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Set;

/**
 * A wrapper around the SearchableProperties. it can be compatible
 * java.util.Properties.
 * @author onewe
 */
class SearchablePropertiesWrapper extends Properties {
    
    private final SearchableProperties properties;
    
    public SearchablePropertiesWrapper(SearchableProperties searchableProperties) {
        if (searchableProperties == null) {
            throw new IllegalArgumentException("can't create an SearchablePropertiesWrapper, SearchableProperties must not be null!");
        }
        this.properties = searchableProperties;
    }
    
    @Override
    public String getProperty(String key, String defaultValue) {
        return this.properties.getProperty(key, defaultValue);
    }
    
    @Override
    public String getProperty(String key) {
        return this.properties.getProperty(key);
    }
    
    @Override
    public void list(PrintStream out) {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public void list(PrintWriter out) {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public synchronized void load(Reader reader) throws IOException {
        Properties properties = new Properties();
        properties.load(reader);
        this.properties.addProperties(properties);
    }
    
    @Override
    public synchronized void load(InputStream inStream) throws IOException {
        Properties properties = new Properties();
        properties.load(inStream);
        this.properties.addProperties(properties);
    }
    
    @Override
    public synchronized void loadFromXML(InputStream in) throws IOException {
        Properties properties = new Properties();
        properties.load(in);
        this.properties.addProperties(properties);
    }
    
    @Override
    public Enumeration<?> propertyNames() {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public void save(OutputStream out, String comments) {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public synchronized Object setProperty(String key, String value) {
        Object previousValue = this.properties.getProperty(key);
        this.properties.setProperty(key, value);
        return previousValue;
    }
    
    @Override
    public void store(Writer writer, String comments) throws IOException {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public void store(OutputStream out, String comments) throws IOException {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public void storeToXML(OutputStream os, String comment) throws IOException {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public void storeToXML(OutputStream os, String comment, String encoding) throws IOException {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public Set<String> stringPropertyNames() {
        throw new UnsupportedOperationException();
    }
    
    public SearchableProperties unwrap() {
        return this.properties;
    }
}
