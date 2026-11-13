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

import { cn } from '@/lib/utils';

const PROTOCOL_STYLES: Record<string, { bg: string; text: string; dot: string }> = {
  stdio: { bg: 'bg-purple-50 dark:bg-purple-950/40', text: 'text-purple-700 dark:text-purple-300', dot: 'bg-purple-500' },
  'mcp-sse': { bg: 'bg-blue-50 dark:bg-blue-950/40', text: 'text-blue-700 dark:text-blue-300', dot: 'bg-blue-500' },
  'mcp-streamable': { bg: 'bg-cyan-50 dark:bg-cyan-950/40', text: 'text-cyan-700 dark:text-cyan-300', dot: 'bg-cyan-500' },
  http: { bg: 'bg-orange-50 dark:bg-orange-950/40', text: 'text-orange-700 dark:text-orange-300', dot: 'bg-orange-500' },
  dubbo: { bg: 'bg-green-50 dark:bg-green-950/40', text: 'text-green-700 dark:text-green-300', dot: 'bg-green-500' },
};

export function McpProtocolBadge({ protocol }: { protocol: string }) {
  const style = PROTOCOL_STYLES[protocol];
  return <span className={cn(
    'inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium',
    style?.bg || 'bg-gray-100 dark:bg-gray-800',
    style?.text || 'text-gray-600 dark:text-gray-400'
  )}>
    <span className={cn('h-1.5 w-1.5 rounded-full', style?.dot || 'bg-gray-400')} />
    {protocol}
  </span>;
}
