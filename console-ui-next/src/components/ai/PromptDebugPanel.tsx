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

import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Play, Eraser, Variable, Brain, AlertCircle, Loader2, Eye, Check } from 'lucide-react';
import { useServerStore } from '@/stores/server-store';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import type { PromptVariable } from '@/types/prompt';

export function extractVariables(template: string): string[] {
  if (!template) return [];
  const regex = /\{\{([^\s{}]+)\}\}/g;
  const variables: string[] = [];
  let match;
  while ((match = regex.exec(template)) !== null) {
    if (!variables.includes(match[1])) variables.push(match[1]);
  }
  return variables;
}

function getAccessToken(): string {
  try {
    const tokenStr = localStorage.getItem('token');
    if (tokenStr) {
      const tokenData = JSON.parse(tokenStr);
      return tokenData.accessToken || '';
    }
  } catch { /* ignore */ }
  return '';
}


/** Interactive preview of the selected template; it never updates the referenced resource. */
export function PromptDebugPanel({ template, versionInfo }: {
  template: string; versionInfo?: { variables?: PromptVariable[] };
}) {
  const { t } = useTranslation();
  const copilotEnabled = useServerStore(state => state.copilotEnabled);
  const variables = useMemo(() => extractVariables(template), [template]);
  // Debug state
  const [variableValues, setVariableValues] = useState<Record<string, string>>({});
  const [userInput, setUserInput] = useState('');
  const [debugging, setDebugging] = useState(false);
  const [debugThinking, setDebugThinking] = useState('');
  const [debugContent, setDebugContent] = useState('');
  const [debugError, setDebugError] = useState<string | null>(null);
  const debugResultRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setVariableValues(Object.fromEntries((versionInfo?.variables || []).map(value => [value.name, value.defaultValue || ''])));
  }, [versionInfo]);
  // Rendered prompt with variable values
  const renderedPrompt = useMemo(() => {
    let result = template;
    const serverVars = versionInfo?.variables || [];
    const merged: Record<string, string> = {};
    serverVars.forEach((v) => { if (v.defaultValue) merged[v.name] = v.defaultValue; });
    Object.entries(variableValues).forEach(([k, v]) => { if (v) merged[k] = v; });
    Object.entries(merged).forEach(([key, val]) => {
      result = result.replace(new RegExp(`\\{\\{${key}\\}\\}`, 'g'), val);
    });
    return result;
  }, [template, variableValues, versionInfo]);

  // --- SSE Debug ---
  const handleStartDebug = () => {
    if (!userInput.trim()) return;
    setDebugging(true);
    setDebugThinking('');
    setDebugContent('');
    setDebugError(null);

    const ctxPath = window.location.pathname.replace(/\/(next|legacy)(\/.*)?$/, '/') || '/';
    const url = `${window.location.origin}${ctxPath}v3/console/copilot/prompt/debug`;
    const token = getAccessToken();

    fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(token ? { Authorization: `Bearer ${token}`, AccessToken: token } : {}),
      },
      body: JSON.stringify({ prompt: renderedPrompt, userInput }),
    })
      .then((response) => {
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const reader = response.body!.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        const read = (): Promise<void> =>
          reader.read().then(({ done, value }) => {
            if (done) { setDebugging(false); return; }
            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split('\n');
            buffer = lines.pop() || '';
            lines.forEach((line) => {
              if (line.startsWith('data:')) {
                try {
                  const data = JSON.parse(line.substring(5).trim());
                  const typeStr = data.type?.code || data.type || 'CONTENT';
                  if (typeStr === 'THINKING') setDebugThinking((p) => p + (data.chunk || ''));
                  else if (typeStr === 'CONTENT') setDebugContent((p) => p + (data.chunk || ''));
                  else if (typeStr === 'DONE' || data.done) setDebugging(false);
                  else if (typeStr === 'error') { setDebugging(false); setDebugError(data.message || 'Error'); }
                } catch { /* ignore */ }
              }
            });
            debugResultRef.current?.scrollTo(0, debugResultRef.current.scrollHeight);
            return read();
          });
        return read();
      })
      .catch((err) => { setDebugging(false); setDebugError(err.message || 'Request failed'); });
  };

  return <>
          {/* Debug Panel Card */}
          {copilotEnabled && (
          <Card className="overflow-hidden py-0 gap-0">
            <div className="px-5 py-3.5 border-b bg-muted/30 flex items-center justify-between">
              <h2 className="text-sm font-semibold flex items-center gap-2">
                <Play className="h-4 w-4 text-amber-500" />
                {t('prompt.debugPanel')}
              </h2>
              {(debugThinking || debugContent) && (
                <Button variant="ghost" size="sm" className="h-6 text-[11px] px-2 text-muted-foreground hover:text-foreground" onClick={() => { setDebugThinking(''); setDebugContent(''); setDebugError(null); }} disabled={debugging}>
                  <Eraser className="mr-1 h-3 w-3" />
                  {t('prompt.clearResult')}
                </Button>
              )}
            </div>
            <CardContent className="p-0">
              <div className="p-4 space-y-3">
                {variables.length > 0 && (
                  <div className="rounded-lg border bg-muted/10 p-3 space-y-2">
                    <div className="flex items-center gap-1.5 pb-1">
                      <Variable className="h-3.5 w-3.5 text-amber-500" />
                      <span className="text-xs font-medium">{t('prompt.variables')}</span>
                      <Badge variant="secondary" className="h-4 text-[10px] px-1.5 font-mono">{variables.length}</Badge>
                    </div>
                    <div className="space-y-1.5">
                      {variables.map((v) => {
                        const svrVar = (versionInfo?.variables || []).find((sv) => sv.name === v);
                        return (
                          <div key={v} className="flex items-center gap-2">
                            <code className="text-[11px] font-mono text-amber-600 dark:text-amber-400 bg-amber-500/8 px-1.5 py-0.5 rounded w-28 truncate shrink-0 text-center">{`{{${v}}}`}</code>
                            <Input
                              value={variableValues[v] || ''}
                              onChange={(e) => setVariableValues((p) => ({ ...p, [v]: e.target.value }))}
                              placeholder={svrVar?.defaultValue ? `${t('prompt.variableDefault')}: ${svrVar.defaultValue}` : v}
                              className="h-7 text-xs bg-transparent"
                              disabled={debugging}
                            />
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}
                <div className="flex flex-col gap-3">
                  <Label className="text-xs font-medium">{t('prompt.userInput')} <span className="text-destructive">*</span></Label>
                  <Textarea value={userInput} onChange={(e) => setUserInput(e.target.value)} placeholder={t('prompt.userInputPlaceholder')} rows={3} className="bg-transparent text-xs resize-none" disabled={debugging} />
                </div>
                <div className="flex justify-end">
                  <Button size="sm" className="h-7 text-xs gap-1.5" onClick={handleStartDebug} disabled={debugging || !userInput.trim()}>
                    {debugging ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Play className="h-3.5 w-3.5" />}
                    {debugging ? t('prompt.streaming') : t('prompt.startDebug')}
                  </Button>
                </div>
              </div>
              {debugError && (
                <div className="mx-4 mb-3 flex items-center gap-2 text-xs text-destructive bg-destructive/10 rounded-md px-3 py-2">
                  <AlertCircle className="h-3.5 w-3.5 shrink-0" />{debugError}
                </div>
              )}
              <div className="border-t bg-muted/10">
                <div className="px-4 py-2.5 flex items-center gap-1.5">
                  <Brain className="h-3.5 w-3.5 text-muted-foreground" />
                  <span className="text-xs font-medium">{t('prompt.modelOutput')}</span>
                  {debugging && <Loader2 className="h-3 w-3 animate-spin text-amber-500 ml-1" />}
                </div>
                <div ref={debugResultRef} className="mx-4 mb-4 min-h-[100px] max-h-[300px] overflow-auto rounded-lg border bg-background">
                  {!debugThinking && !debugContent && !debugging && (
                    <div className="flex flex-col items-center justify-center py-8 text-muted-foreground/40">
                      <Play className="h-6 w-6 mb-2" />
                      <p className="text-xs">{t('prompt.startDebug')}...</p>
                    </div>
                  )}
                  {debugThinking && (
                    <div className="px-3 pt-3 pb-2 border-b border-dashed">
                      <div className="flex items-center gap-1 text-[10px] font-medium text-muted-foreground mb-1.5"><Eye className="h-3 w-3" />{t('prompt.thinking')}</div>
                      <pre className="text-[11px] text-muted-foreground whitespace-pre-wrap break-words leading-relaxed">{debugThinking}</pre>
                    </div>
                  )}
                  {debugContent && (
                    <div className="px-3 pt-3 pb-2">
                      <div className="flex items-center gap-1 text-[10px] font-medium text-emerald-600 dark:text-emerald-400 mb-1.5"><Check className="h-3 w-3" />{t('prompt.modelOutput')}</div>
                      <pre className="text-[11px] whitespace-pre-wrap break-words leading-relaxed">{debugContent}</pre>
                    </div>
                  )}
                </div>
              </div>
            </CardContent>
          </Card>
          )}
  </>;
}
