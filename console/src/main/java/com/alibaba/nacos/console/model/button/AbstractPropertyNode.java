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

package com.alibaba.nacos.console.model.button;

import com.alibaba.nacos.console.button.SwitchInterface;

/**
 * property node.
 * @author 985492783@qq.com
 * @date 2023/3/28 10:30
 */
public abstract class AbstractPropertyNode<V> {
    
    private final String property;
    
    private final V value;
    
    private final boolean isSwitch;
    
    private final String description;
    
    public AbstractPropertyNode(String property, V value, boolean isSwitch, String description) {
        this.property = property;
        this.value = value;
        this.description = description;
        this.isSwitch = isSwitch;
    }
    
    /**
     * create SwitchPropertyNode.
     */
    public static <V> AbstractPropertyNode<V> valueOf(String property, V value, String description, SwitchInterface switchInterface) {
        return new SwitchPropertyNode<>(property, value, description, switchInterface);
    }
    
    /**
     * create NonSwitchPropertyNode.
     */
    public static <V> AbstractPropertyNode<V> valueOf(String property, V value, String description) {
        return new NonSwitchPropertyNode<>(property, value, description);
    }
    
    public String getProperty() {
        return property;
    }
    
    public V getValue() {
        return value;
    }
    
    public String getDescription() {
        return description;
    }
    
    public boolean isSwitch() {
        return isSwitch;
    }
    
    public abstract SwitchResult changeProperty(Object value);
    
    /**
     * switch property.
     */
    public static class SwitchPropertyNode<V> extends AbstractPropertyNode<V> {
        
        private final SwitchInterface switchInterface;
    
        private SwitchPropertyNode(String property, V value, String description, SwitchInterface switchInterface) {
            super(property, value, true, description);
            this.switchInterface = switchInterface;
        }
        
        @Override
        public SwitchResult changeProperty(Object value) {
            return switchInterface.changeProperty(value);
        }
        
    }
    
    /**
     * just-for-show property.
     */
    public static class NonSwitchPropertyNode<V> extends AbstractPropertyNode<V> {
        
        private NonSwitchPropertyNode(String property, V value, String description) {
            super(property, value, false, description);
        }
    
        @Override
        public SwitchResult changeProperty(Object value) {
            return SwitchResult.nonSwitchFail();
        }
        
    }
}
