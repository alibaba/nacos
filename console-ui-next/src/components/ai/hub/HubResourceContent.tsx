import { PromptVariables } from '@/components/ai/PromptVariables';
import { PromptDebugPanel } from '@/components/ai/PromptDebugPanel';
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

import type { ReactNode } from 'react';
import { Editor } from '@monaco-editor/react';
import { FileText, Package, Sparkles } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Card, CardContent } from '@/components/ui/card';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { ResourceViewer } from '@/pages/agentSpecManagement/components/ResourceViewer';
import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { agentSpecContent } from '@/lib/hub-resource-content';
import { HubAgentContent } from './HubAgentContent';
import { HubMcpContent } from './HubMcpContent';
import type { PromptVariable } from '@/types/prompt';
import type { SharedResource } from '@/api/hub-resource';

function ContentCard({ title, icon, children }: { title: string; icon: ReactNode; children: ReactNode }) {
  return <Card className="overflow-hidden py-0 gap-0"><div className="px-5 py-3.5 border-b bg-muted/30"><h2 className="text-sm font-semibold flex items-center gap-2">{icon}{title}</h2></div><CardContent className="p-0">{children}</CardContent></Card>;
}

export function HubResourceContent({ detail, content, sidebar }: { detail: SharedResource; content: Record<string, unknown>; sidebar: ReactNode }) {
  const { t } = useTranslation();
  const grid = (body: ReactNode, aside: ReactNode = sidebar) => <div className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,1fr)_320px]"><div className="min-w-0 space-y-5">{body}</div>{aside}</div>;
  if (detail.resourceType === 'agentspec') {
    const spec = agentSpecContent(content);
    return <Tabs defaultValue="overview" className="flex flex-col">
      <TabsList className="w-fit"><TabsTrigger value="overview" className="gap-1.5"><FileText className="h-3.5 w-3.5" />{t('agentSpec.agentsFile')}</TabsTrigger><TabsTrigger value="resources" className="gap-1.5"><Package className="h-3.5 w-3.5" />{t('agentSpec.resources')}{Object.keys(spec.resources).length > 0 && <span className="ml-0.5 text-[10px]">{Object.keys(spec.resources).length}</span>}</TabsTrigger></TabsList>
      <TabsContent value="overview">{grid(<ContentCard title={t('agentSpec.agentsFile')} icon={<FileText className="h-4 w-4 text-muted-foreground" />}><div className="min-h-[580px] p-5">{spec.agents ? <div className="app-markdown prose prose-sm dark:prose-invert max-w-none"><Markdown remarkPlugins={[remarkGfm]}>{spec.agents}</Markdown></div> : <p className="text-sm text-muted-foreground">{t('agentSpec.noAgentsFile')}</p>}</div></ContentCard>)}</TabsContent>
      <TabsContent value="resources"><Card className="flex h-[580px] flex-col overflow-hidden py-0 gap-0"><div className="px-5 py-3.5 border-b bg-muted/30"><h2 className="text-sm font-semibold flex items-center gap-2"><Package className="h-4 w-4 text-muted-foreground" />{t('agentSpec.resources')}</h2></div><CardContent className="flex-1 min-h-0 p-0"><ResourceViewer resources={spec.resources} content={spec.manifest} editable={false} className="h-full min-h-0" /></CardContent></Card></TabsContent>
    </Tabs>;
  }
  if (detail.resourceType === 'prompt') {
    return <Tabs defaultValue="template" className="space-y-4"><TabsList className="w-fit"><TabsTrigger value="template" className="gap-1.5"><Sparkles className="h-3.5 w-3.5" />{t('prompt.template')}</TabsTrigger></TabsList><TabsContent value="template">{grid(<>
      <ContentCard title={t('prompt.template')} icon={<Sparkles className="h-4 w-4 text-amber-500" />}><Editor height="420px" language="plaintext" theme="vs" value={typeof content.template === 'string' ? content.template : ''} options={{ minimap: { enabled: false }, lineNumbers: 'on', wordWrap: 'on', scrollBeyondLastLine: false, automaticLayout: true, fontSize: 13, tabSize: 2, readOnly: true }} /></ContentCard>
      <PromptDebugPanel key={`${detail.hubEntryId}@${detail.version}`} template={typeof content.template === 'string' ? content.template : ''} versionInfo={content as { variables?: PromptVariable[] }} />
</>, <div className="space-y-4">{sidebar}      <PromptVariables template={typeof content.template === 'string' ? content.template : ''} versionInfo={content as { variables?: PromptVariable[] }} /></div>)}</TabsContent></Tabs>;
  }
  return detail.resourceType === 'agent'
    ? <HubAgentContent key={`${detail.hubEntryId}@${detail.version}`} content={content} hubEntryId={detail.hubEntryId} sidebar={sidebar} />
    : <HubMcpContent detail={detail} content={content} />;
}
