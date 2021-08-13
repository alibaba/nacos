/*
 *  Copyright 1999-2021 Alibaba Group Holding Ltd.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.alibaba.nacos.naming.selector;

import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.alibaba.nacos.api.selector.Selector;
import com.alibaba.nacos.api.selector.context.SelectorContextBuilder;
import com.alibaba.nacos.common.spi.NacosServiceLoader;
import com.alibaba.nacos.common.utils.JacksonUtils;
import com.alibaba.nacos.common.utils.StringUtils;
import com.alibaba.nacos.naming.misc.Loggers;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.alibaba.nacos.api.exception.NacosException.SERVER_ERROR;

/**
 * {@link SelectorManager} work on init {@link Selector#parse(Object)}, execute {@link Selector#select(Object)} and maintain
 * the type of {@link Selector} and {@link SelectorContextBuilder}.
 * It will provide the {@link Selector} types for web and openapi user to select.
 *
 * @author chenglu
 * @date 2021-07-12 18:42
 */
@Component
public class SelectorManager {
    
    /**
     * The relationship of context type and {@link SelectorContextBuilder}.
     */
    private Map<String, SelectorContextBuilder> contextBuilders = new HashMap<>(8);
    
    /**
     * The relationship of selector type and {@link Selector} class.
     */
    private Map<String, Class<? extends Selector>> selectorTypes = new HashMap<>(8);
    
    /**
     * init the {@link Selector} class and {@link SelectorContextBuilder}.
     */
    @PostConstruct
    public void init() {
        initSelectorContextBuilders();
        initSelectorTypes();
    }
    
    /**
     * init SelectorContextBuilders.
     */
    private void initSelectorContextBuilders() {
        Collection<SelectorContextBuilder> selectorContextBuilders = NacosServiceLoader.load(SelectorContextBuilder.class);
        for (SelectorContextBuilder selectorContextBuilder : selectorContextBuilders) {
            if (contextBuilders.containsKey(selectorContextBuilder.getContextType())) {
                Loggers.SRV_LOG.warn("[SelectorManager] init selectorContextBuilders, SelectorContextBuilder type {} has value, ignore it.",
                        selectorContextBuilder.getContextType());
                continue;
            }
            contextBuilders.put(selectorContextBuilder.getContextType(), selectorContextBuilder);
            Loggers.SRV_LOG.info("[SelectorManager] Load SelectorContextBuilder({}) contextType({}) successfully.", selectorContextBuilder.getClass(),
                    selectorContextBuilder.getContextType());
        }
    }
    
    /**
     * init SelectorTypes. The subclass of {@link Selector} must have public access default constructor.
     */
    private void initSelectorTypes() {
        Collection<Selector> selectors = NacosServiceLoader.load(Selector.class);
        for (Selector selector : selectors) {
            if (selectorTypes.containsKey(selector.getType())) {
                Loggers.SRV_LOG.warn("[SelectorManager] init Selectors, Selector type {} has value, ignore it.", selector.getType());
                continue;
            }
            Class<? extends Selector> selectorClass = selector.getClass();
            try {
                Constructor constructor = selectorClass.getConstructor();
                if (Objects.isNull(constructor)) {
                    throw new NoSuchMethodException();
                }
                // register json serial.
                JacksonUtils.registerSubtype(selectorClass, selector.getType());
                selectorTypes.put(selector.getType(), selectorClass);
                Loggers.SRV_LOG.info("[SelectorManager] Load Selector({}) type({}) contextType({}) successfully.", selectorClass, selector.getType(),
                        selector.getContextType());
            } catch (Exception e) {
                Loggers.SRV_LOG.warn("[SelectorManager] Selector {} cannot find public access default constructor, will be ignored.",
                        selectorClass);
            }
        }
    }
    
    /**
     * return all selector type provided by {@link #selectorTypes}.
     *
     * @return select types.
     */
    public List<String> getAllSelectorTypes() {
        return new ArrayList<>(selectorTypes.keySet());
    }
    
    /**
     * parse {@link Selector} by selector type and condition. if not find the Selector type or parse failed, then will return null.
     *
     * @param type selector type. {@link Selector#getType()}.
     * @param condition the condition provide for {@link Selector#parse(Object)}.
     * @return {@link Selector}.
     */
    public Selector parseSelector(String type, String condition) throws NacosException {
        if (StringUtils.isBlank(type)) {
            return null;
        }
        Class<? extends Selector> clazz = selectorTypes.get(type);
        if (Objects.isNull(clazz)) {
            return null;
        }
        try {
            Selector selector = clazz.newInstance();
            selector.parse(condition);
            return selector;
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("[SelectorManager] Parse Selector failed, type: {}, condition: {}.", type, condition, e);
            throw new NacosException(SERVER_ERROR, "Selector parses failed: " + e.getMessage());
        }
    }
    
    /**
     * invoke the {@link Selector#select(Object)}. it will help {@link Selector} to build the context it need.
     *
     * @param selector {@link Selector}.
     * @param consumerIp the consumer Ip address.
     * @param providers the provider list for select.
     * @return the select instance list.
     */
    public <T extends Instance> List<T> select(Selector selector, String consumerIp, List<T> providers) {
        if (Objects.isNull(selector)) {
            return providers;
        }
        SelectorContextBuilder selectorContextBuilder = contextBuilders.get(selector.getContextType());
        if (Objects.isNull(selectorContextBuilder)) {
            Loggers.SRV_LOG.info("[SelectorManager] cannot find the contextBuilder of type {}.", selector.getType());
            return providers;
        }
        try {
            Object context = selectorContextBuilder.build(consumerIp, providers);
            return (List<T>) selector.select(context);
        } catch (Exception e) {
            Loggers.SRV_LOG.warn("[SelectorManager] execute select failed, will return all providers.", e);
            return providers;
        }
    }
}
