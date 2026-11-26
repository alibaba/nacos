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

import { useTranslation } from 'react-i18next';
import { Variable } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { extractVariables } from './PromptDebugPanel';
import type { PromptVariable } from '@/types/prompt';
export function PromptVariables({ template, versionInfo }: { template: string; versionInfo?: { variables?: PromptVariable[] } }) {
  const { t } = useTranslation();
  const variables = extractVariables(template);
  return (<>
          {/* Variables Card */}
          <Card className="overflow-hidden py-0 gap-0">
            <div className="px-4 py-3 border-b bg-muted/30">
              <h2 className="text-sm font-semibold flex items-center gap-2">
                <Variable className="h-4 w-4 text-amber-500" />
                {t('prompt.variables')}
                {variables.length > 0 && (
                  <Badge variant="secondary" className="h-5 text-[10px] px-1.5 font-mono">{variables.length}</Badge>
                )}
              </h2>
            </div>
            <CardContent className="p-0">
              {variables.length === 0 ? (
                <div className="flex flex-col items-center justify-center py-8 text-muted-foreground">
                  <Variable className="h-6 w-6 text-muted-foreground/30 mb-2" />
                  <p className="text-xs">{t('prompt.noVariables')}</p>
                </div>
              ) : (
                <div className="divide-y divide-border">
                  {(versionInfo?.variables || []).map((v) => (
                    <div key={v.name} className="p-3 hover:bg-muted/20 transition-colors">
                      <div className="flex items-center gap-2">
                        <div className="h-1.5 w-1.5 rounded-full bg-amber-400 shrink-0" />
                        <code className="text-[11px] font-mono font-medium text-amber-700 dark:text-amber-300">{v.name}</code>
                        {v.defaultValue && (
                          <code className="text-[10px] font-mono text-muted-foreground bg-muted px-1.5 py-0.5 rounded ml-auto truncate max-w-[120px]">{v.defaultValue}</code>
                        )}
                      </div>
                      {v.description && (
                        <p className="text-[10px] text-muted-foreground mt-1 pl-3.5 leading-relaxed">{v.description}</p>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
  </>);
}
