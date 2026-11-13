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

import type { AgentSpecResource } from '@/types/agentspec';

/** AgentSpec stores manifest text, not a parsed JSON object. Never rewrite it for display. */
export function agentSpecContent(content: Record<string, unknown>) {
  if (content.content != null && typeof content.content !== 'string') throw new Error('Invalid AgentSpec manifest');
  const resources: Record<string, AgentSpecResource> = {};
  if (content.resource != null) {
    if (typeof content.resource !== 'object' || Array.isArray(content.resource)) throw new Error('Invalid AgentSpec resource map');
    for (const [key, value] of Object.entries(content.resource)) {
      if (!value || typeof value !== 'object' || typeof value.name !== 'string' ||
          typeof value.type !== 'string' || typeof value.content !== 'string') throw new Error('Invalid AgentSpec resource');
      resources[key] = value as AgentSpecResource;
    }
  }
  const agents = Object.entries(resources).find(([, resource]) =>
    resource.name.split('/').pop()?.toUpperCase() === 'AGENTS.MD');
  return { manifest: content.content as string | null | undefined ?? '',
    agents: agents?.[1].content ?? '',
    resources: Object.fromEntries(Object.entries(resources).filter(([key]) => key !== agents?.[0])) };
}
