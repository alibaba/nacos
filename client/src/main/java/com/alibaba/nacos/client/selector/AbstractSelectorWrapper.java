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

import java.util.Objects;

/**
 * Selector Wrapper.
 *
 * @param <C> the type of selector context
 * @param <E> the type of select result
 * @author lideyou
 */
public abstract class AbstractSelectorWrapper<C, E> {
    private final Selector<C, E> selector;

    private final ListenerInvoker<E> listener;

    public AbstractSelectorWrapper(Selector<C, E> selector, ListenerInvoker<E> listener) {
        this.selector = selector;
        this.listener = listener;
    }

    /**
     * Determine whether the context is selectable.
     * @param context selector
     * @return true if the context is selectable
     */
    protected abstract boolean isSelectable(C context);

    /**
     * Determine whether the result can be callback.
     * @param event select result
     * @return true if the result can be callback
     */
    protected abstract boolean isCallable(E event);

    /**
     * Notify listener.
     *
     * @param context selector context
     */
    public void notifyListener(C context) {
        if (isSelectable(context)) {
            E event = selector.select(context);
            if (isCallable(event)) {
                listener.invoke(event);
            }
        }
    }

    public ListenerInvoker<E> getListener() {
        return this.listener;
    }

    public Selector<C, E> getSelector() {
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
        AbstractSelectorWrapper<?, ?> that = (AbstractSelectorWrapper<?, ?>) o;
        return Objects.equals(selector, that.selector) && Objects.equals(listener, that.listener);
    }

    @Override
    public int hashCode() {
        return Objects.hash(selector, listener);
    }
}
