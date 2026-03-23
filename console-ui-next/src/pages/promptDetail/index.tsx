import { useEffect, useState, useRef, useCallback, useMemo } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { Editor } from '@monaco-editor/react';
import {
  ArrowLeft,
  MessageSquare,
  Pencil,
  Trash2,
  Plus,
  Tag,
  Clock,
  User,
  Play,
  Eraser,
  Sparkles,
  X,
  ChevronLeft,
  ChevronRight,
  Loader2,
  Eye,
  Brain,
  AlertCircle,
  Variable,
  Check,
  Server,
  History,
} from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Badge } from '@/components/ui/badge';
import { Label } from '@/components/ui/label';
import { Separator } from '@/components/ui/separator';
import { Skeleton } from '@/components/ui/skeleton';
import { ScrollArea } from '@/components/ui/scroll-area';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetDescription,
} from '@/components/ui/sheet';
import { useNamespaceStore } from '@/stores/namespace-store';
import { usePromptStore } from '@/stores/prompt-store';
import { promptApi } from '@/api/prompt';
import type { PromptMetaInfo, PromptVersionInfo, PromptVersionSummary } from '@/types/prompt';
import { cn } from '@/lib/utils';
import { VersionLabelEditor } from '@/components/ai/VersionLabelEditor';

// Extract {{variable}} from template
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

function formatTime(time: number): string {
  if (!time) return '--';
  const d = new Date(time);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
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

export default function PromptDetailPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { currentNamespace } = useNamespaceStore();
  const { clearCurrentPrompt } = usePromptStore();

  const promptKey = searchParams.get('promptKey') || '';
  const namespaceId = searchParams.get('namespaceId') || currentNamespace || 'public';

  // Core state
  const [loading, setLoading] = useState(true);
  const [meta, setMeta] = useState<PromptMetaInfo | null>(null);
  const [versionInfo, setVersionInfo] = useState<PromptVersionInfo | null>(null);
  const [template, setTemplate] = useState('');
  const [selectedVersion, setSelectedVersion] = useState<string | null>(null);
  const [isLatestVersion, setIsLatestVersion] = useState(true);

  // Version history (inline sidebar)
  const [versions, setVersions] = useState<PromptVersionSummary[]>([]);
  const [versionsLoading, setVersionsLoading] = useState(false);
  const [versionPageNo, setVersionPageNo] = useState(1);
  const [versionPageSize] = useState(10);
  const [versionTotal, setVersionTotal] = useState(0);

  // Label management dialog
  const [labelDialogOpen, setLabelDialogOpen] = useState(false);
  const [labelDialogVersion, setLabelDialogVersion] = useState('');
  const [labelDialogSaving, setLabelDialogSaving] = useState(false);
  const [labelsSaving, setLabelsSaving] = useState(false);

  // Edit metadata dialog
  const [editDialogOpen, setEditDialogOpen] = useState(false);
  const [editSaving, setEditSaving] = useState(false);
  const [editDescription, setEditDescription] = useState('');
  const [editBizTags, setEditBizTags] = useState<string[]>([]);
  const [editTagInput, setEditTagInput] = useState('');

  // Version history sheet
  const [versionSheetOpen, setVersionSheetOpen] = useState(false);

  // Delete confirm
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deleteLoading, setDeleteLoading] = useState(false);

  // Debug state
  const [variableValues, setVariableValues] = useState<Record<string, string>>({});
  const [userInput, setUserInput] = useState('');
  const [debugging, setDebugging] = useState(false);
  const [debugThinking, setDebugThinking] = useState('');
  const [debugContent, setDebugContent] = useState('');
  const [debugError, setDebugError] = useState<string | null>(null);
  const debugResultRef = useRef<HTMLDivElement>(null);

  // AI Optimize dialog
  const [optimizeOpen, setOptimizeOpen] = useState(false);
  const [optimizeGoal, setOptimizeGoal] = useState('');
  const [optimizing, setOptimizing] = useState(false);
  const [optimizeStream, setOptimizeStream] = useState('');
  const [optimizedResult, setOptimizedResult] = useState<string | null>(null);
  const [optimizeError, setOptimizeError] = useState<string | null>(null);
  const optimizePanelRef = useRef<HTMLDivElement>(null);

  const variables = useMemo(() => extractVariables(template), [template]);
  const labelsMap = meta?.labels || {};

  // Load prompt data
  const loadPromptDetail = useCallback(async (version?: string | null, label?: string | null) => {
    setLoading(true);
    try {
      const metaRes = await promptApi.getPromptMetadata({ promptKey, namespaceId });
      const metaData = (metaRes as unknown as { data: PromptMetaInfo }).data;

      const detailParams: { promptKey: string; namespaceId: string; version?: string; label?: string } = { promptKey, namespaceId };
      if (label) detailParams.label = label;
      else if (version) detailParams.version = version;

      const detailRes = await promptApi.getPromptDetail(detailParams);
      const detail = (detailRes as unknown as { data: PromptVersionInfo }).data;

      setMeta(metaData);
      setVersionInfo(detail);
      setTemplate(detail.template || '');
      setSelectedVersion(detail.version);
      setIsLatestVersion(!detail.version || metaData.latestVersion === detail.version);

      // Initialize variable values from defaults
      const initialVals: Record<string, string> = {};
      (detail.variables || []).forEach((v) => {
        if (v.defaultValue) initialVals[v.name] = v.defaultValue;
      });
      setVariableValues(initialVals);
    } catch {
      toast.error(t('prompt.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [promptKey, namespaceId, t]);

  // Load version history
  const loadVersions = useCallback(async (page = 1) => {
    setVersionsLoading(true);
    setVersionPageNo(page);
    try {
      const res = await promptApi.listVersions({ promptKey, namespaceId, pageNo: page, pageSize: versionPageSize });
      const data = (res as unknown as { data: { pageItems: PromptVersionSummary[]; totalCount: number } }).data;
      setVersions(data.pageItems || []);
      setVersionTotal(data.totalCount || 0);
    } catch { /* ignore */ } finally {
      setVersionsLoading(false);
    }
  }, [promptKey, namespaceId, versionPageSize]);

  useEffect(() => {
    if (promptKey) {
      loadPromptDetail();
      loadVersions(1);
    }
    return () => clearCurrentPrompt();
  }, [promptKey, loadPromptDetail, loadVersions, clearCurrentPrompt]);

  // Render prompt with variable values
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
                } catch { /* ignore parse errors */ }
              }
            });
            debugResultRef.current?.scrollTo(0, debugResultRef.current.scrollHeight);
            return read();
          });

        return read();
      })
      .catch((err) => { setDebugging(false); setDebugError(err.message || 'Request failed'); });
  };

  // --- SSE AI Optimize ---
  const handleStartOptimize = () => {
    if (!template.trim()) return;
    setOptimizing(true);
    setOptimizeStream('');
    setOptimizedResult(null);
    setOptimizeError(null);

    const ctxPath = window.location.pathname.replace(/\/(next|legacy)(\/.*)?$/, '/') || '/';
    const url = `${window.location.origin}${ctxPath}v3/console/copilot/prompt/optimize`;
    const token = getAccessToken();

    fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(token ? { Authorization: `Bearer ${token}`, AccessToken: token } : {}),
      },
      body: JSON.stringify({ prompt: template, optimizationGoal: optimizeGoal }),
    })
      .then((response) => {
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const reader = response.body!.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        let accumulated = '';

        const read = (): Promise<void> =>
          reader.read().then(({ done, value }) => {
            if (done) { setOptimizing(false); setOptimizedResult(accumulated || null); return; }
            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split('\n');
            buffer = lines.pop() || '';
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
      })
      .catch((err) => { setOptimizing(false); setOptimizeError(err.message || 'Request failed'); });
  };

  const handleApplyOptimize = () => {
    if (optimizedResult) {
      setTemplate(optimizedResult);
      setOptimizeOpen(false);
      setOptimizeGoal('');
      setOptimizeStream('');
      setOptimizedResult(null);
      toast.success(t('prompt.applyOptimize'));
    }
  };

  // --- Label management ---
  const openLabelEditor = (version: string) => {
    setLabelDialogVersion(version);
    setLabelDialogOpen(true);
  };

  const handleSaveLabelsBulk = async (newLabels: Record<string, string>) => {
    setLabelsSaving(true);
    try {
      const oldLabels = { ...labelsMap };
      // Compute diff: unbind labels removed or whose version changed
      const toUnbind = Object.keys(oldLabels).filter(
        (l) => !(l in newLabels) || newLabels[l] !== oldLabels[l]
      );
      // Compute diff: bind labels added or whose version changed
      const toBind = Object.entries(newLabels).filter(
        ([l, v]) => v && oldLabels[l] !== v
      );
      await Promise.all([
        ...toUnbind.map((label) => promptApi.unbindLabel({ promptKey, label, namespaceId })),
        ...toBind.map(([label, version]) => promptApi.bindLabel({ promptKey, label, version, namespaceId })),
      ]);
      toast.success(t('common.versionLabels.updateSuccess'));
      loadPromptDetail(selectedVersion);
    } catch (err) {
      toast.error(String(err));
    } finally {
      setLabelsSaving(false);
    }
  };

  // --- Delete ---
  const handleDelete = async () => {
    setDeleteLoading(true);
    try {
      await promptApi.deletePrompt({ promptKey, namespaceId });
      toast.success(t('prompt.deleteSuccess'));
      navigate('/promptManagement');
    } catch { /* handled by interceptor */ } finally {
      setDeleteLoading(false);
    }
  };

  // --- Navigate ---
  const handlePublishNewVersion = () => {
    const params = new URLSearchParams({ mode: 'version', promptKey, namespaceId });
    navigate(`/newPrompt?${params}`);
  };

  const handleEdit = () => {
    setEditDescription(meta?.description || '');
    setEditBizTags(meta?.bizTags || []);
    setEditTagInput('');
    setEditDialogOpen(true);
  };

  const handleEditSave = async () => {
    setEditSaving(true);
    try {
      await promptApi.updateMetadata({
        promptKey,
        description: editDescription.trim(),
        bizTags: editBizTags.join(','),
        namespaceId,
      });
      toast.success(t('prompt.updateSuccess'));
      setEditDialogOpen(false);
      loadPromptDetail(selectedVersion);
    } catch {
      // handled by interceptor
    } finally {
      setEditSaving(false);
    }
  };

  const handleEditAddTag = () => {
    const tag = editTagInput.trim();
    if (!tag || editBizTags.includes(tag)) { setEditTagInput(''); return; }
    setEditBizTags((prev) => [...prev, tag]);
    setEditTagInput('');
  };

  const handleVersionChange = (version: string) => {
    loadPromptDetail(version);
    loadVersions(versionPageNo);
    setVersionSheetOpen(false);
  };

  // Helper: get labels bound to a specific version
  const getLabelsForVersion = (version: string): string[] =>
    Object.entries(labelsMap).filter(([, v]) => v === version).map(([label]) => label);

  const versionTotalPages = Math.ceil(versionTotal / versionPageSize);

  // Loading skeleton
  if (loading && !meta) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-32 w-full rounded-xl" />
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          <div className="lg:col-span-2 space-y-4">
            <Skeleton className="h-48 w-full" />
            <Skeleton className="h-64 w-full" />
          </div>
          <div className="space-y-4">
            <Skeleton className="h-32 w-full" />
            <Skeleton className="h-32 w-full" />
            <Skeleton className="h-48 w-full" />
          </div>
        </div>
      </div>
    );
  }

  // Error state
  if (!meta) {
    return (
      <div className="flex flex-col items-center justify-center py-24 space-y-4">
        <p className="text-sm text-destructive">{t('prompt.loadFailed')}</p>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => navigate('/promptManagement')}>
            {t('prompt.backToList')}
          </Button>
          <Button onClick={() => loadPromptDetail()}>
            {t('prompt.retry')}
          </Button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-5">
      {/* ===== Hero Header ===== */}
      <div className="relative rounded-xl border bg-card overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-br from-amber-500/[0.04] via-transparent to-orange-500/[0.03]" />
        <div className="absolute top-0 right-0 w-64 h-64 bg-gradient-to-bl from-amber-500/[0.06] to-transparent rounded-full -translate-y-1/2 translate-x-1/3" />

        <div className="relative px-5 py-4">
          {/* Top bar */}
          <div className="flex items-center justify-between mb-4">
            <Button
              variant="ghost"
              size="sm"
              className="h-7 gap-1.5 text-muted-foreground hover:text-foreground -ml-2"
              onClick={() => navigate('/promptManagement')}
            >
              <ArrowLeft className="h-3.5 w-3.5" />
              {t('prompt.backToList')}
            </Button>

            <div className="flex items-center gap-2">
              {/* Version selector */}
              {meta.versions && meta.versions.length > 0 && (
                <Select
                  value={selectedVersion || ''}
                  onValueChange={handleVersionChange}
                >
                  <SelectTrigger className="w-[140px] h-7 text-xs bg-background/80">
                    <SelectValue placeholder={t('prompt.selectVersion')} />
                  </SelectTrigger>
                  <SelectContent>
                    {meta.versions.map((v) => (
                      <SelectItem key={v} value={v}>
                        v{v}
                        {v === meta.latestVersion && (
                          <Badge className="ml-2 bg-emerald-100 text-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-300 text-[10px] px-1 py-0 border-0">
                            Latest
                          </Badge>
                        )}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}

              <Button variant="outline" size="sm" className="h-7 text-xs" onClick={() => setVersionSheetOpen(true)}>
                <History className="mr-1 h-3 w-3" />
                {t('prompt.versionHistory')}
              </Button>

              <Button size="sm" className="h-7 text-xs" onClick={handlePublishNewVersion}>
                <Plus className="mr-1 h-3 w-3" />
                {t('prompt.publishVersion')}
              </Button>
              <Button variant="destructive" size="sm" className="h-7 w-7 p-0" onClick={() => setDeleteDialogOpen(true)}>
                <Trash2 className="h-3.5 w-3.5" />
              </Button>
            </div>
          </div>

          {/* Prompt identity */}
          <div className="flex items-start gap-4">
            <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-amber-500 to-orange-400 shadow-lg shadow-amber-500/20">
              <MessageSquare className="h-7 w-7 text-white" />
            </div>

            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2.5 mb-1">
                <h1 className="text-xl font-bold tracking-tight">{promptKey}</h1>
                {selectedVersion && (
                  <span className="text-xs text-muted-foreground font-mono bg-muted/60 px-1.5 py-0.5 rounded">
                    v{selectedVersion}
                  </span>
                )}
                {isLatestVersion && (
                  <Badge className="bg-emerald-100 text-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-300 text-[10px] px-1.5 py-0 border-0">
                    Latest
                  </Badge>
                )}
              </div>

              <div className="flex items-center gap-2">
                {meta.description ? (
                  <p className="text-sm text-muted-foreground leading-relaxed max-w-2xl">
                    {meta.description}
                  </p>
                ) : (
                  <p className="text-sm text-muted-foreground/60 italic">{t('prompt.noDescription')}</p>
                )}
                <button onClick={handleEdit} className="text-muted-foreground hover:text-primary transition-colors shrink-0">
                  <Pencil className="h-3.5 w-3.5" />
                </button>
              </div>

              {meta.bizTags && meta.bizTags.length > 0 && (
                <div className="flex flex-wrap gap-1.5 mt-2">
                  {meta.bizTags.map((tag) => (
                    <Badge key={tag} variant="outline" className="text-[10px] px-1.5 py-0">
                      {tag}
                    </Badge>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* ===== Content Grid ===== */}
      <div className={cn('grid grid-cols-1 lg:grid-cols-3 gap-5', loading && 'opacity-50 pointer-events-none')}>
        {/* Left column - 2/3 */}
        <div className="lg:col-span-2 space-y-5">
          {/* Template Card */}
          <Card className="overflow-hidden py-0 gap-0">
            <div className="px-5 py-3.5 border-b bg-muted/30 flex items-center justify-between">
              <h2 className="text-sm font-semibold flex items-center gap-2">
                <Sparkles className="h-4 w-4 text-amber-500" />
                {t('prompt.template')}
              </h2>
              <Button variant="outline" size="sm" className="h-7 text-xs" onClick={() => setOptimizeOpen(true)} disabled={!template.trim()}>
                <Sparkles className="mr-1 h-3 w-3" />
                {t('prompt.aiOptimize')}
              </Button>
            </div>
            <CardContent className="p-0">
              <Editor
                height="420px"
                language="plaintext"
                value={template}
                theme="vs"
                options={{
                  minimap: { enabled: false },
                  lineNumbers: 'on',
                  wordWrap: 'on',
                  scrollBeyondLastLine: false,
                  automaticLayout: true,
                  fontSize: 13,
                  tabSize: 2,
                }}
                onChange={(value) => setTemplate(value || '')}
                loading={<div className="flex items-center justify-center h-[420px] text-muted-foreground text-sm">Loading...</div>}
              />
            </CardContent>
          </Card>

          {/* Debug Panel Card */}
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
              {/* Input Section */}
              <div className="p-4 space-y-3">
                {/* Variable inputs */}
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

                {/* User input */}
                <div className="flex flex-col gap-3">
                  <Label className="text-xs font-medium">{t('prompt.userInput')} <span className="text-destructive">*</span></Label>
                  <Textarea
                    value={userInput}
                    onChange={(e) => setUserInput(e.target.value)}
                    placeholder={t('prompt.userInputPlaceholder')}
                    rows={3}
                    className="bg-transparent text-xs resize-none"
                    disabled={debugging}
                  />
                </div>

                {/* Run button */}
                <div className="flex justify-end">
                  <Button size="sm" className="h-7 text-xs gap-1.5" onClick={handleStartDebug} disabled={debugging || !userInput.trim()}>
                    {debugging ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Play className="h-3.5 w-3.5" />}
                    {debugging ? t('prompt.streaming') : t('prompt.startDebug')}
                  </Button>
                </div>
              </div>

              {/* Debug error */}
              {debugError && (
                <div className="mx-4 mb-3 flex items-center gap-2 text-xs text-destructive bg-destructive/10 rounded-md px-3 py-2">
                  <AlertCircle className="h-3.5 w-3.5 shrink-0" />
                  {debugError}
                </div>
              )}

              {/* Output Section */}
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
                      <div className="flex items-center gap-1 text-[10px] font-medium text-muted-foreground mb-1.5">
                        <Eye className="h-3 w-3" />
                        {t('prompt.thinking')}
                      </div>
                      <pre className="text-[11px] text-muted-foreground whitespace-pre-wrap break-words leading-relaxed">{debugThinking}</pre>
                    </div>
                  )}
                  {debugContent && (
                    <div className="px-3 pt-3 pb-2">
                      <div className="flex items-center gap-1 text-[10px] font-medium text-emerald-600 dark:text-emerald-400 mb-1.5">
                        <Check className="h-3 w-3" />
                        {t('prompt.modelOutput')}
                      </div>
                      <pre className="text-[11px] whitespace-pre-wrap break-words leading-relaxed">{debugContent}</pre>
                    </div>
                  )}
                </div>
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Right column - 1/3 */}
        <div className="space-y-5">
          {/* Basic Info Card */}
          <Card className="overflow-hidden py-0 gap-0">
            <div className="px-5 py-3.5 border-b bg-muted/30">
              <h2 className="text-sm font-semibold flex items-center gap-2">
                <Server className="h-4 w-4 text-muted-foreground" />
                {t('prompt.basicInfo')}
              </h2>
            </div>
            <CardContent className="p-0">
              <div className="divide-y divide-border">
                <InfoCell
                  label={t('prompt.commitMsg')}
                  value={versionInfo?.commitMsg}
                  icon={<MessageSquare className="h-3.5 w-3.5" />}
                />
                <InfoCell
                  label={t('prompt.publishTime')}
                  value={versionInfo?.gmtModified ? formatTime(versionInfo.gmtModified) : undefined}
                  icon={<Clock className="h-3.5 w-3.5" />}
                />
                <InfoCell
                  label={t('prompt.publisher')}
                  value={versionInfo?.srcUser}
                  icon={<User className="h-3.5 w-3.5" />}
                />
              </div>
            </CardContent>
          </Card>

          {/* Variables Card */}
          <Card className="overflow-hidden py-0 gap-0">
            <div className="px-5 py-3.5 border-b bg-muted/30">
              <h2 className="text-sm font-semibold flex items-center gap-2">
                <Variable className="h-4 w-4 text-amber-500" />
                {t('prompt.variables')}
                {(versionInfo?.variables?.length ?? 0) > 0 && (
                  <span className="inline-flex items-center justify-center h-5 min-w-5 rounded-full bg-amber-100 dark:bg-amber-900/40 text-amber-700 dark:text-amber-300 text-[11px] font-semibold px-1.5">
                    {versionInfo!.variables.length}
                  </span>
                )}
              </h2>
            </div>
            <CardContent className="p-4">
              {!versionInfo?.variables?.length ? (
                <p className="text-sm text-muted-foreground text-center py-4">{t('prompt.noVariables')}</p>
              ) : (
                <div className="space-y-2.5">
                  {versionInfo.variables.map((v) => (
                    <div key={v.name} className="rounded-lg border bg-muted/20 px-3 py-2 space-y-1">
                      <span className="text-xs font-mono font-semibold text-amber-600 dark:text-amber-400">{`{{${v.name}}}`}</span>
                      {v.defaultValue && (
                        <div className="flex items-center gap-1.5 text-[11px]">
                          <span className="text-muted-foreground shrink-0">{t('prompt.variableDefault')}:</span>
                          <code className="text-xs bg-muted/50 px-1.5 py-0.5 rounded font-mono">{v.defaultValue}</code>
                        </div>
                      )}
                      {v.description && (
                        <p className="text-[11px] text-muted-foreground">{v.description}</p>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>

          {/* Version labels (editable) */}
          <Card className="overflow-hidden py-0 gap-0">
            <div className="px-5 py-3.5 border-b bg-muted/30">
              <h2 className="text-sm font-semibold flex items-center gap-2">
                <Tag className="h-4 w-4 text-muted-foreground" />
                {t('common.versionLabels.title')}
              </h2>
            </div>
            <CardContent className="p-4">
              <VersionLabelEditor
                labels={labelsMap}
                availableVersions={versions.map((v) => v.version)}
                onSave={handleSaveLabelsBulk}
                isSaving={labelsSaving}
              />
            </CardContent>
          </Card>
        </div>
      </div>

      {/* ===== Version History Sheet ===== */}
      <Sheet open={versionSheetOpen} onOpenChange={setVersionSheetOpen}>
        <SheetContent className="flex flex-col p-0 sm:max-w-md">
          <SheetHeader className="px-6 pt-6 pb-4 border-b shrink-0">
            <SheetTitle className="flex items-center gap-2">
              <History className="h-4.5 w-4.5 text-amber-500" />
              {t('prompt.versionHistory')}
            </SheetTitle>
            <SheetDescription>
              {t('prompt.totalVersions', { count: versionTotal })}
            </SheetDescription>
          </SheetHeader>

          <ScrollArea className="flex-1">
            <div className="p-4 space-y-2">
              {versionsLoading ? (
                <div className="flex items-center justify-center py-12">
                  <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                </div>
              ) : versions.length === 0 ? (
                <p className="text-sm text-muted-foreground text-center py-12">{t('prompt.noVersions')}</p>
              ) : (
                versions.map((v) => {
                  const vLabels = getLabelsForVersion(v.version);
                  const isCurrent = v.version === selectedVersion;
                  const isLatest = v.version === meta.latestVersion;
                  return (
                    <div
                      key={v.version}
                      className={cn(
                        'rounded-lg border px-4 py-3 cursor-pointer transition-colors hover:bg-muted/50',
                        isCurrent && 'border-amber-500/50 bg-amber-50/50 dark:bg-amber-950/20'
                      )}
                      onClick={() => handleVersionChange(v.version)}
                    >
                      <div className="flex items-center justify-between mb-1">
                        <div className="flex items-center gap-2">
                          <span className="text-sm font-mono font-semibold">{v.version}</span>
                          {isLatest && (
                            <Badge className="bg-emerald-100 text-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-300 text-[10px] px-1.5 py-0 border-0">
                              Latest
                            </Badge>
                          )}
                          {isCurrent && (
                            <Badge variant="outline" className="text-[10px] px-1.5 py-0 border-amber-500/50 text-amber-600 dark:text-amber-400">
                              {t('prompt.currentVersion')}
                            </Badge>
                          )}
                        </div>
                      </div>

                      {/* Labels for this version */}
                      {vLabels.length > 0 && (
                        <div className="flex items-center gap-1 mt-1.5 flex-wrap">
                          <Tag className="h-3 w-3 text-muted-foreground shrink-0" />
                          {vLabels.map((label) => (
                            <Badge key={label} variant="outline" className="text-[10px] px-1.5 py-0 h-4 font-mono">
                              {label}
                            </Badge>
                          ))}
                        </div>
                      )}

                      {/* Manage labels button */}
                      <div className="mt-2 flex items-center justify-between">
                        <button
                          className="text-[11px] text-muted-foreground hover:text-primary transition-colors flex items-center gap-1"
                          onClick={(e) => { e.stopPropagation(); openLabelEditor(v.version); }}
                        >
                          <Tag className="h-3 w-3" />
                          {t('common.versionLabels.editLabels')}
                        </button>
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </ScrollArea>

          {/* Pagination footer */}
          {versionTotal > versionPageSize && (
            <div className="flex items-center justify-center gap-2 px-6 py-3 border-t shrink-0">
              <Button
                variant="outline"
                size="icon"
                className="h-7 w-7"
                disabled={versionPageNo <= 1}
                onClick={() => loadVersions(versionPageNo - 1)}
              >
                <ChevronLeft className="h-4 w-4" />
              </Button>
              <span className="text-xs text-muted-foreground tabular-nums">
                {versionPageNo} / {versionTotalPages || 1}
              </span>
              <Button
                variant="outline"
                size="icon"
                className="h-7 w-7"
                disabled={versionPageNo >= versionTotalPages}
                onClick={() => loadVersions(versionPageNo + 1)}
              >
                <ChevronRight className="h-4 w-4" />
              </Button>
            </div>
          )}
        </SheetContent>
      </Sheet>

      {/* ===== Dialogs ===== */}

      {/* Label Management Dialog */}
      <Dialog open={labelDialogOpen} onOpenChange={setLabelDialogOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>{t('common.versionLabels.editLabels')} - {labelDialogVersion}</DialogTitle>
            <DialogDescription>
              {t('common.versionLabels.title')}
            </DialogDescription>
          </DialogHeader>
          {labelDialogVersion && (
            <VersionLabelEditor
              labels={(() => {
                const result: Record<string, string> = {};
                for (const [key, val] of Object.entries(labelsMap)) {
                  if (val === labelDialogVersion && key !== 'latest') {
                    result[key] = val;
                  }
                }
                return result;
              })()}
              onSave={async (versionLabels) => {
                setLabelDialogSaving(true);
                try {
                  // Compute the full merged labels: keep labels NOT pointing to this version, add the new ones
                  const merged: Record<string, string> = {};
                  for (const [key, val] of Object.entries(labelsMap)) {
                    if (val !== labelDialogVersion) {
                      merged[key] = val;
                    }
                  }
                  Object.assign(merged, versionLabels);
                  await handleSaveLabelsBulk(merged);
                  setLabelDialogOpen(false);
                } finally {
                  setLabelDialogSaving(false);
                }
              }}
              isSaving={labelDialogSaving}
            />
          )}
        </DialogContent>
      </Dialog>

      {/* Edit Metadata Dialog */}
      <Dialog open={editDialogOpen} onOpenChange={(open) => { if (!editSaving) setEditDialogOpen(open); }}>
        <DialogContent className="max-w-lg p-0 gap-0 overflow-hidden">
          {/* Header */}
          <div className="px-6 pt-6 pb-4">
            <DialogHeader className="space-y-1.5">
              <DialogTitle className="text-base">{t('prompt.editMetadata')}</DialogTitle>
              <DialogDescription className="font-mono text-xs tracking-wide">{promptKey}</DialogDescription>
            </DialogHeader>
          </div>

          <Separator />

          {/* Body */}
          <div className="px-6 py-5 space-y-3">
            {/* Description */}
            <div className="flex flex-col gap-3">
              <Label className="text-sm font-medium text-muted-foreground">{t('prompt.description')}</Label>
              <Textarea
                value={editDescription}
                onChange={(e) => setEditDescription(e.target.value)}
                placeholder={t('prompt.descriptionPlaceholder')}
                rows={3}
                className="bg-transparent resize-none text-sm"
              />
            </div>

            {/* Biz Tags */}
            <div className="flex flex-col gap-3">
              <Label className="text-sm font-medium text-muted-foreground">{t('prompt.bizTags')}</Label>
              <div className="rounded-lg border bg-muted/20 p-3 space-y-2.5">
                {editBizTags.length > 0 && (
                  <div className="flex flex-wrap gap-1.5">
                    {editBizTags.map((tag) => (
                      <Badge
                        key={tag}
                        variant="secondary"
                        className="gap-1.5 pl-2.5 pr-1 py-0.5 text-xs font-normal bg-background border shadow-sm"
                      >
                        {tag}
                        <button
                          onClick={() => setEditBizTags((prev) => prev.filter((t) => t !== tag))}
                          className="rounded-full hover:bg-destructive/10 hover:text-destructive p-0.5 transition-colors"
                        >
                          <X className="h-3 w-3" />
                        </button>
                      </Badge>
                    ))}
                  </div>
                )}
                <div className="flex gap-2">
                  <Input
                    value={editTagInput}
                    onChange={(e) => setEditTagInput(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') { e.preventDefault(); handleEditAddTag(); }
                    }}
                    placeholder={t('prompt.tagPlaceholder')}
                    className="bg-transparent flex-1 h-8 text-sm"
                  />
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    className="h-8 px-3 shrink-0"
                    onClick={handleEditAddTag}
                    disabled={!editTagInput.trim()}
                  >
                    <Plus className="h-3.5 w-3.5 mr-1" />
                    {t('common.add', { defaultValue: '添加' })}
                  </Button>
                </div>
              </div>
            </div>
          </div>

          <Separator />

          {/* Footer */}
          <div className="px-6 py-4 flex justify-end gap-2 bg-muted/20">
            <Button variant="outline" size="sm" onClick={() => setEditDialogOpen(false)} disabled={editSaving}>
              {t('common.cancel')}
            </Button>
            <Button size="sm" onClick={handleEditSave} disabled={editSaving}>
              {editSaving ? t('common.loading') : t('common.save')}
            </Button>
          </div>
        </DialogContent>
      </Dialog>

      {/* Delete Confirm Dialog */}
      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>{t('common.delete')}</DialogTitle>
            <DialogDescription>{t('prompt.deleteConfirm', { name: promptKey })}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteDialogOpen(false)} disabled={deleteLoading}>{t('common.cancel')}</Button>
            <Button variant="destructive" onClick={handleDelete} disabled={deleteLoading}>
              {deleteLoading ? t('common.loading') : t('common.delete')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* AI Optimize Dialog */}
      <Dialog open={optimizeOpen} onOpenChange={(open) => { if (!optimizing) setOptimizeOpen(open); }}>
        <DialogContent className="max-w-4xl max-h-[85vh] overflow-auto">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Sparkles className="h-4 w-4 text-amber-500" />
              {t('prompt.aiOptimize')}
            </DialogTitle>
          </DialogHeader>

          {/* Goal + Start button */}
          <div className="flex gap-2">
            <Input
              value={optimizeGoal}
              onChange={(e) => setOptimizeGoal(e.target.value)}
              placeholder={t('prompt.optimizeGoalPlaceholder')}
              className="flex-1"
              disabled={optimizing}
            />
            <Button onClick={handleStartOptimize} disabled={optimizing}>
              {optimizing ? <Loader2 className="mr-1.5 h-4 w-4 animate-spin" /> : <Sparkles className="mr-1.5 h-4 w-4" />}
              {optimizing ? t('prompt.optimizing') : t('prompt.startOptimize')}
            </Button>
          </div>

          {optimizeError && (
            <div className="flex items-center gap-2 text-sm text-destructive bg-destructive/10 rounded-md px-3 py-2">
              <AlertCircle className="h-4 w-4 shrink-0" />
              {optimizeError}
            </div>
          )}

          {/* Side-by-side comparison */}
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
                  <p className="text-xs text-muted-foreground/60 text-center py-8">
                    {t('prompt.startOptimize')}...
                  </p>
                )}
              </div>
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => { if (!optimizing) { setOptimizeOpen(false); setOptimizeStream(''); setOptimizedResult(null); setOptimizeGoal(''); } }}>
              {t('common.cancel')}
            </Button>
            {optimizedResult && !optimizing && (
              <Button onClick={handleApplyOptimize}>
                {t('prompt.applyOptimize')}
              </Button>
            )}
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

// ===== Sub-components =====

function InfoCell({
  label,
  value,
  icon,
}: {
  label: string;
  value: React.ReactNode;
  icon?: React.ReactNode;
}) {
  return (
    <div className="flex items-center gap-3 px-5 py-3">
      {icon && (
        <span className="text-muted-foreground/60 shrink-0">{icon}</span>
      )}
      <div className="min-w-0 flex-1">
        <p className="text-[11px] text-muted-foreground leading-none mb-1">{label}</p>
        <div className="text-sm font-medium break-all">{value || '-'}</div>
      </div>
    </div>
  );
}
