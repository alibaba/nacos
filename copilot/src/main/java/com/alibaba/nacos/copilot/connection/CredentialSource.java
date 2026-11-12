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
 * Where the effective credential of a connection comes from.
 *
 * <p>The source is reported to the console, while the credential value itself never leaves
 * the process. Only the legacy connection can resolve its key from the environment, so
 * {@link #ENVIRONMENT} appears only there.
 *
 * @author nacos
 */
public enum CredentialSource {
    
    /**
     * The credential was read from the process environment.
     */
    ENVIRONMENT,
    
    /**
     * The credential comes from the effective configuration value.
     */
    CONFIGURATION
}
