/*
 * Copyright 1999-2025 Alibaba Group Holding Ltd.
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

package com.alibaba.nacos.copilot.connection;

/**
 * Where the effective connection selection comes from.
 *
 * <p>Deployment configuration overrides the value stored by the console, so the console
 * needs the origin to explain why a saved selection may not be the effective one.
 *
 * @author nacos
 */
public enum SelectionSource {
    
    /** The selection is declared in the deployment configuration. */
    DEPLOYMENT,
    
    /** The selection is the value stored by the console. */
    STORED,
    
    /** No selection is configured, so the default routing order applies. */
    NONE
}
