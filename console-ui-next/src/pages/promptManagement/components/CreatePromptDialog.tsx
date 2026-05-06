import { useState, useCallback, useMemo, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { Editor } from '@monaco-editor/react';
import {
  Variable,
  Sparkles,
  X,
  Loader2,
  AlertCircle,
} from 'lucide-react';
import { useServerStore } from '@/stores/server-store';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { promptApi } from '@/api/prompt';

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

function extractVariables(template: string): string[] {
  if (!template) return [];
  const regex = /\{\{([^\s{}]+)\}\}/g;
  const variables: string[] = [];
  let match;
  while ((match = regex.exec(template)) !== null) {
    if (!variables.includes(match[1])) variables.push(match[1]);
  }
  return variables;
}

interface CreatePromptDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  namespaceId: string;
  onSuccess: (promptKey: string) => void;
}

export function CreatePromptDialog({
  open,
  onOpenChange,
  namespaceId,
  onSuccess,
}: CreatePromptDialogProps) {
  const { t } = useTranslation();
  const copilotEnabled = useServerStore((s) => s.copilotEnabled);

  const [promptKey, setPromptKey] = useState('');
  const [description, setDescription] = useState('');
  const [commitMsg, setCommitMsg] = useState('');
  const [template, setTemplate] = useState('');
  const [bizTags, setBizTags] = useState<string[]>([]);
  const [tagInput, setTagInput] = useState('');
  const [variableDefaults, setVariableDefaults] = useState<Record<string, string>>({});
  const [variableDescriptions, setVariableDescriptions] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // AI Optimize
  const [optimizeOpen, setOptimizeOpen] = useState(false);
  const [optimizeGoal, setOptimizeGoal] = useState('');
  const [optimizing, setOptimizing] = useState(false);
  const [optimizeStream, setOptimizeStream] = useState('');
  const [optimizedResult, setOptimizedResult] = useState<string | null>(null);
  const [optimizeError, setOptimizeError] = useState<string | null>(null);
  const optimizePanelRef = useRef<HTMLDivElement>(null);

  const variables = useMemo(() => extractVariables(template), [template]);

  const reset = useCallback(() => {
    setPromptKey(''); setDescription(''); setCommitMsg(''); setTemplate('');
    setBizTags([]); setTagInput(''); setVariableDefaults({}); setVariableDescriptions({});
    setError(null); setLoading(false);
    setOptimizeOpen(false); setOptimizeGoal(''); setOptimizing(false);
    setOptimizeStream(''); setOptimizedResult(null); setOptimizeError(null);
  }, []);

  const handleClose = useCallback((nextOpen: boolean) => {
    if (!nextOpen) reset();
    onOpenChange(nextOpen);
  }, [onOpenChange, reset]);

  const handleAddTag = () => {
    const tag = tagInput.trim();
    if (!tag || bizTags.includes(tag)) { setTagInput(''); return; }
    setBizTags((prev) => [...prev, tag]);
    setTagInput('');
  };

  const handleCreate = useCallback(async () => {
    const key = promptKey.trim();
    if (!key) { setError(t('prompt.keyRequired')); return; }
    if (!/^[a-zA-Z0-9_.-]+$/.test(key)) { setError(t('prompt.keyInvalid')); return; }
    if (!template.trim()) { setError(t('prompt.templateRequired')); return; }
    setLoading(true); setError(null);
    try {
      const variablesDef = variables.map((name) => ({
        name, defaultValue: variableDefaults[name] || null, description: variableDescriptions[name] || null,
      }));
      await promptApi.createDraft({
        promptKey: key, template,
        variables: variablesDef.length > 0 ? JSON.stringify(variablesDef) : undefined,
        commitMsg: commitMsg.trim() || undefined,
        description: description.trim() || undefined,
        bizTags: bizTags.length > 0 ? bizTags.join(',') : undefined,
        namespaceId,
      });
      toast.success(t('prompt.createSuccess'));
      handleClose(false);
      onSuccess(key);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to create prompt';
      setError(msg);
    } finally { setLoading(false); }
  }, [promptKey, template, variables, variableDefaults, variableDescriptions, commitMsg, description, bizTags, namespaceId, t, handleClose, onSuccess]);

  // SSE AI Optimize
  const handleStartOptimize = () => {
    if (!template.trim()) return;
    setOptimizing(true); setOptimizeStream(''); setOptimizedResult(null); setOptimizeError(null);
    const ctxPath = window.location.pathname.replace(/\/(next|legacy)(\/.*)?$/, '/') || '/';
    const url = `${window.location.origin}${ctxPath}v3/console/copilot/prompt/optimize`;
    const token = getAccessToken();
    fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream', ...(token ? { Authorization: `Bearer ${token}`, AccessToken: token } : {}) },
      body: JSON.stringify({ prompt: template, optimizationGoal: optimizeGoal }),
    }).then((response) => {
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const reader = response.body!.getReader();
      const decoder = new TextDecoder();
      let buffer = '', accumulated = '';
      const read = (): Promise<void> => reader.read().then(({ done, value }) => {
        if (done) { setOptimizing(false); setOptimizedResult(accumulated || null); return; }
        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n'); buffer = lines.pop() || '';
        lines.forEach((line) => {
          if (line.startsWith('data:')) {
            try {
              const data = JSON.parse(line.substring(5).trim());
              const typeStr = data.type?.code || data.type || 'CONTENT';
              if (typeStr === 'CONTENT') { accumulated += data.chunk || ''; setOptimizeStream(accumulated); }
              else if (typeStr === 'DONE' || data.done) { setOptimizing(false); setOptimizedResult(accumulated || null); }
              else if (typeStr === 'error') { setOptimizing(false); setOptimizeError(data.message || 'Error'); }
            } catch { /* ignore */ }
          }
        });
        optimizePanelRef.current?.scrollTo(0, optimizePanelRef.current.scrollHeight);
        return read();
      });
      return read();
    }).catch((err) => { setOptimizing(false); setOptimizeError(err.message || 'Request failed'); });
  };

  const handleApplyOptimize = () => {
    if (optimizedResult) {
      setTemplate(optimizedResult);
      setOptimizeOpen(false); setOptimizeStream(''); setOptimizedResult(null); setOptimizeGoal('');
      toast.success(t('prompt.applyOptimize'));
    }
  };

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-w-3xl max-h-[85vh] overflow-auto">
        <DialogHeader>
          <DialogTitle>{t('prompt.createPrompt')}</DialogTitle>
          <DialogDescription>{t('prompt.createPromptDesc')}</DialogDescription>
        </DialogHeader>

        <div className="space-y-4">
          {/* Prompt Key */}
          <div className="space-y-2">
            <Label>{t('prompt.promptKey')} *</Label>
            <Input placeholder={t('prompt.keyPlaceholder')} value={promptKey} onChange={(e) => { setPromptKey(e.target.value); setError(null); }} />
          </div>

          {/* Description */}
          <div className="space-y-2">
            <Label>{t('prompt.description')}</Label>
            <Input placeholder={t('prompt.descriptionPlaceholder')} value={description} onChange={(e) => setDescription(e.target.value)} />
          </div>

          {/* Template + AI Optimize button */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <Label>{t('prompt.template')} *</Label>
              {copilotEnabled && (
                <Button variant="outline" size="sm" className="h-7 text-xs gap-1.5" onClick={() => setOptimizeOpen(true)} disabled={!template.trim()}>
                  <Sparkles className="h-3 w-3" />
                  {t('prompt.aiOptimize')}
                </Button>
              )}
            </div>
            <p className="text-xs text-muted-foreground">{t('prompt.templatePlaceholder')}</p>
            <div className="rounded-md border overflow-hidden">
              <Editor
                height="200px"
                language="plaintext"
                value={template}
                theme="vs"
                options={{ minimap: { enabled: false }, lineNumbers: 'on', wordWrap: 'on', scrollBeyondLastLine: false, automaticLayout: true, fontSize: 13, tabSize: 2 }}
                onChange={(value) => setTemplate(value || '')}
                loading={<div className="flex items-center justify-center h-[200px] text-muted-foreground text-sm">Loading...</div>}
              />
            </div>
          </div>

          {/* Variables */}
          {variables.length > 0 && (
            <div className="space-y-2">
              <Label className="flex items-center gap-1.5">
                <Variable className="h-3.5 w-3.5 text-amber-500" />
                {t('prompt.variables')}
                <Badge variant="secondary" className="ml-1 h-5 text-[10px] px-1.5">{variables.length}</Badge>
              </Label>
              <div className="space-y-2">
                {variables.map((v) => (
                  <div key={v} className="flex items-center gap-2">
                    <code className="text-[11px] font-mono text-amber-600 dark:text-amber-400 bg-amber-500/8 px-1.5 py-0.5 rounded w-28 truncate shrink-0 text-center">{`{{${v}}}`}</code>
                    <Input value={variableDefaults[v] || ''} onChange={(e) => setVariableDefaults((p) => ({ ...p, [v]: e.target.value }))} placeholder={t('prompt.variableDefault')} className="h-7 text-xs" />
                    <Input value={variableDescriptions[v] || ''} onChange={(e) => setVariableDescriptions((p) => ({ ...p, [v]: e.target.value }))} placeholder={t('prompt.variableDescription')} className="h-7 text-xs" />
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Commit Message */}
          <div className="space-y-2">
            <Label>{t('prompt.commitMsg')}</Label>
            <Input value={commitMsg} onChange={(e) => setCommitMsg(e.target.value)} placeholder={t('prompt.commitMsgPlaceholder')} />
          </div>

          {/* Biz Tags */}
          <div className="space-y-2">
            <Label>{t('prompt.bizTags')}</Label>
            {bizTags.length > 0 && (
              <div className="flex flex-wrap gap-1.5">
                {bizTags.map((tag) => (
                  <Badge key={tag} variant="secondary" className="gap-1 pr-1">
                    {tag}
                    <button onClick={() => setBizTags((prev) => prev.filter((t) => t !== tag))} className="ml-0.5 rounded-full hover:bg-muted-foreground/20 p-0.5">
                      <X className="h-2.5 w-2.5" />
                    </button>
                  </Badge>
                ))}
              </div>
            )}
            <div className="flex gap-2">
              <Input value={tagInput} onChange={(e) => setTagInput(e.target.value)} onKeyDown={(e) => e.key === 'Enter' && (e.preventDefault(), handleAddTag())} placeholder={t('prompt.tagPlaceholder')} className="flex-1" />
            </div>
          </div>

          {error && <p className="text-sm text-destructive">{error}</p>}
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => handleClose(false)} disabled={loading}>{t('common.cancel')}</Button>
          <Button onClick={handleCreate} disabled={!promptKey.trim() || !template.trim() || loading}>
            {loading ? t('common.loading') : t('prompt.createPrompt')}
          </Button>
        </DialogFooter>
      </DialogContent>

      {/* AI Optimize Dialog */}
      <Dialog open={optimizeOpen} onOpenChange={(o) => { if (!optimizing) setOptimizeOpen(o); }}>
        <DialogContent className="max-w-4xl max-h-[85vh] overflow-auto">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Sparkles className="h-4 w-4 text-amber-500" />
              {t('prompt.aiOptimize')}
            </DialogTitle>
          </DialogHeader>
          <div className="flex gap-2">
            <Input value={optimizeGoal} onChange={(e) => setOptimizeGoal(e.target.value)} placeholder={t('prompt.optimizeGoalPlaceholder')} className="flex-1" disabled={optimizing} />
            <Button onClick={handleStartOptimize} disabled={optimizing}>
              {optimizing ? <Loader2 className="mr-1.5 h-4 w-4 animate-spin" /> : <Sparkles className="mr-1.5 h-4 w-4" />}
              {optimizing ? t('prompt.optimizing') : t('prompt.startOptimize')}
            </Button>
          </div>
          {optimizeError && (
            <div className="flex items-center gap-2 text-sm text-destructive bg-destructive/10 rounded-md px-3 py-2">
              <AlertCircle className="h-4 w-4 shrink-0" />{optimizeError}
            </div>
          )}
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <h3 className="text-xs font-medium text-muted-foreground">{t('prompt.originalTemplate')}</h3>
              <div className="rounded-md border bg-muted/20 p-3 max-h-[400px] overflow-auto">
                <pre className="text-xs whitespace-pre-wrap break-words leading-relaxed">{template}</pre>
              </div>
            </div>
            <div className="space-y-2">
              <h3 className="text-xs font-medium text-muted-foreground flex items-center gap-1.5">
                {t('prompt.optimizedResult')}
                {optimizing && <Loader2 className="h-3 w-3 animate-spin" />}
              </h3>
              <div ref={optimizePanelRef} className="rounded-md border bg-muted/20 p-3 max-h-[400px] overflow-auto">
                {optimizeStream ? (
                  <pre className="text-xs whitespace-pre-wrap break-words leading-relaxed">{optimizeStream}</pre>
                ) : (
                  <p className="text-xs text-muted-foreground/60 text-center py-8">{t('prompt.startOptimize')}...</p>
                )}
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => { if (!optimizing) { setOptimizeOpen(false); setOptimizeStream(''); setOptimizedResult(null); setOptimizeGoal(''); } }}>{t('common.cancel')}</Button>
            {optimizedResult && !optimizing && <Button onClick={handleApplyOptimize}>{t('prompt.applyOptimize')}</Button>}
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Dialog>
  );
}
