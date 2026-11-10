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

import org.springframework.stereotype.Component;

/**
 * Default routing: explicit selection, then a deployment connection, then the legacy one.
 *
 * <p>The selected connection is only used when it is enabled and passed load-time
 * validation. Otherwise routing continues with the fallback order, so a broken selection
 * never takes Copilot down while another connection can still serve.
 *
 * @author nacos
 */
@Component
public class DefaultCopilotModelRouter implements CopilotModelRouter {
    
    @Override
    public CopilotModelConnection route(ConnectionTable table, String selectedConnection) {
        if (table == null) {
            return null;
        }
        CopilotModelConnection selected = table.get(selectedConnection);
        if (isSelectable(selected)) {
            return selected;
        }
        CopilotModelConnection deployment = firstSelectable(table, ConnectionSource.DEPLOYMENT);
        if (deployment != null) {
            return deployment;
        }
        return firstSelectable(table, ConnectionSource.LEGACY);
    }
    
    private CopilotModelConnection firstSelectable(ConnectionTable table, ConnectionSource source) {
        for (CopilotModelConnection connection : table.getAll()) {
            if (connection.getSource() == source && isSelectable(connection)) {
                return connection;
            }
        }
        return null;
    }
    
    private boolean isSelectable(CopilotModelConnection connection) {
        return connection != null && connection.isEnabled() && connection.isAvailable();
    }
}
