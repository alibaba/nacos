import type { ReactNode } from 'react';
import { AgentDetailLayout, AgentCallInterfaces } from '@/components/ai/AgentDetailContent';
import { AgentRuntimeEndpoints } from '@/components/ai/AgentRuntimeEndpoints';
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

import { useEffect, useState } from 'react';
import { agentApi } from '@/api/agent';
import type { ConsoleRuntimeEndpointView } from '@/types/agent';
import { useTranslation } from 'react-i18next';
import type { AgentVersionDetail } from '@/types/agent';
import { getProtocols } from '@/pages/newAgent/agent-console-model';

export function HubAgentContent({ content, hubEntryId, sidebar }: { content: Record<string, unknown>; hubEntryId: string; sidebar?: ReactNode }) {
  const { t } = useTranslation();
  const currentVersion = content as unknown as AgentVersionDetail;
  const protocols = getProtocols(currentVersion.callInterfaces || []);
  const [selection, setSelectedProtocol] = useState('');
  const selectedProtocol = protocols.includes(selection) ? selection : protocols[0] || '';
  const [runtime, setRuntime] = useState<ConsoleRuntimeEndpointView | null>(null);
  const [runtimeError, setRuntimeError] = useState('');
  const [runtimeLoading, setRuntimeLoading] = useState(false);
  const [refresh, setRefresh] = useState(0);
  useEffect(() => {
    let cancelled = false;
    setRuntime(null); setRuntimeError('');
    if (selectedProtocol && hubEntryId) {
      setRuntimeLoading(true);
      agentApi.getRuntimeEndpoints({ namespaceId: '__nacos_ai_hub__', agentName: hubEntryId,
        version: currentVersion.version, protocol: selectedProtocol }).then(result => {
        if (!cancelled) {
          if (result.code !== 0) setRuntimeError(result.message || t('hub.requestFailed'));
          else setRuntime(result.data);
        }
      }).catch(() => { if (!cancelled) setRuntimeError(t('hub.requestFailed')); })
        .finally(() => { if (!cancelled) setRuntimeLoading(false); });
    }
    return () => { cancelled = true; };
  }, [hubEntryId, currentVersion.version, selectedProtocol, refresh, t]);
  return <AgentDetailLayout sidebar={sidebar}>
    <AgentCallInterfaces currentVersion={currentVersion} selectedProtocol={selectedProtocol} setSelectedProtocol={setSelectedProtocol} />
    {selectedProtocol && <AgentRuntimeEndpoints runtimeView={runtime} runtimeLoading={runtimeLoading}
      error={runtimeError} onRefresh={() => setRefresh(value => value + 1)} />}
  </AgentDetailLayout>;
}
