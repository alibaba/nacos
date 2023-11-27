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

package com.alibaba.nacos.client.selector;

import com.alibaba.nacos.api.selector.client.Selector;
import com.alibaba.nacos.common.notify.Event;

import java.util.Objects;

/**
 * Selector Wrapper.
 *
 * @param <S> the type of selector
 * @param <T> the type of original event
 * @param <E> the type of listener callback event
 * @author lideyou
 */
public abstract class AbstractSelectorWrapper<S extends Selector<?, ?>, E, T extends Event> {
    
    private final S selector;
    
    private final ListenerInvoker<E> listener;
    
    public AbstractSelectorWrapper(S selector, ListenerInvoker<E> listener) {
        this.selector = selector;
        this.listener = listener;
    }
    
    /**
     * Check whether the event can be callback.
     *
     * @param event original event
     * @return true if the event can be callback
     */
    protected abstract boolean isSelectable(T event);
    
    /**
     * Check whether the result can be callback.
     *
     * @param event select result
     * @return true if the result can be callback
     */
    protected abstract boolean isCallable(E event);
    
    /**
     * Build an event received by the listener.
     *
     * @param event original event
     * @return listener event
     */
    protected abstract E buildListenerEvent(T event);
    
    /**
     * Notify listener.
     *
     * @param event original event
     */
    public void notifyListener(T event) {
        if (!isSelectable(event)) {
            return;
        }
        E newEvent = buildListenerEvent(event);
        if (isCallable(newEvent)) {
            listener.invoke(newEvent);
        }
    }
    
    public ListenerInvoker<E> getListener() {
        return this.listener;
    }
    
    public S getSelector() {
        return this.selector;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbstractSelectorWrapper<?, ?, ?> that = (AbstractSelectorWrapper<?, ?, ?>) o;
        return Objects.equals(selector, that.selector) && Objects.equals(listener, that.listener);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(selector, listener);
    }
}
