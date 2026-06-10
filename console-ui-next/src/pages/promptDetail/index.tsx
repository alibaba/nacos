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
  Clock,
  User,
  Play,
  Eraser,
  Sparkles,
  X,
  Loader2,
  Eye,
  Brain,
  AlertCircle,
  Variable,
  Check,
  Server,
  Save,
  Globe,
  FileEdit,
  History,
  Send,
  Power,
  PowerOff,
  CheckCircle2,
  ShieldAlert,
  Tag,
  GitBranch,
  Download,
} from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Badge } from '@/components/ui/badge';
import { Label } from '@/components/ui/label';
import { Separator } from '@/components/ui/separator';
import { Skeleton } from '@/components/ui/skeleton';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
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
import { useServerStore } from '@/stores/server-store';
import { useAuthStore } from '@/stores/auth-store';
import { promptApi } from '@/api/prompt';
import { cn } from '@/lib/utils';
import dayjs from 'dayjs';
import { parsePipelineInfo } from '@/types/skill';
import { PromptVersionTimeline } from '@/pages/promptManagement/components/PromptVersionTimeline';
import { PipelineStatusDisplay } from '@/pages/skillManagement/components/PipelineStatusDisplay';
import { LabelBindDialog } from '@/components/ai/LabelBindDialog';
import { BizTagEditDialog } from '@/components/ai/BizTagEditDialog';
import { DetailTagChip } from '@/components/ai/DetailTagChip';
import { CliCommandCard } from '@/components/ai/CliCommandCard';

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
  const { globalAdmin } = useAuthStore();
  const copilotEnabled = useServerStore((s) => s.copilotEnabled);
  const {
    currentGovernance,
    currentVersion: storeVersion,
    fetchGovernanceDetail,
    fetchVersionDetail,
    submitVersion,
    publishVersion,
    forcePublishVersion,
    onlineVersion,
    offlineVersion,
    deleteDraft: storeDraftDelete,
    updateDraft: storeUpdateDraft,
    updateLabels,
    clearCurrentPrompt,
  } = usePromptStore();

  const promptKey = searchParams.get('promptKey') || '';
  const namespaceId = searchParams.get('namespaceId') || currentNamespace || 'public';

  // Core state
  const [loading, setLoading] = useState(true);
  const [template, setTemplate] = useState('');
  const [selectedVersion, setSelectedVersion] = useState<string | null>(null);
  const [isEditingDraft, setIsEditingDraft] = useState(false);
  const [savingDraft, setSavingDraft] = useState(false);
  const [editCommitMsg, setEditCommitMsg] = useState('');
  const [editVariables, setEditVariables] = useState<Array<{ name: string; defaultValue: string; description: string }>>([]);

  // Delete confirm
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deleteLoading, setDeleteLoading] = useState(false);

  // Version history sheet
  const [versionSheetOpen, setVersionSheetOpen] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [forcePublishConfirmOpen, setForcePublishConfirmOpen] = useState(false);

  // Create draft from version dialog
  const [createDraftDialogOpen, setCreateDraftDialogOpen] = useState(false);
  const [createDraftFromVersion, setCreateDraftFromVersion] = useState('');
  const [createDraftTargetVersion, setCreateDraftTargetVersion] = useState('');
  const [createDraftLoading, setCreateDraftLoading] = useState(false);

  // Edit metadata dialog
  const [editDialogOpen, setEditDialogOpen] = useState(false);
  const [editSaving, setEditSaving] = useState(false);
  const [editDescription, setEditDescription] = useState('');
  const [editBizTags, setEditBizTags] = useState<string[]>([]);
  const [editTagInput, setEditTagInput] = useState('');

  // BizTag and Label dialogs
  const [bizTagDialogOpen, setBizTagDialogOpen] = useState(false);
  const [labelDialogOpen, setLabelDialogOpen] = useState(false);

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
  const meta = currentGovernance;
  const versionInfo = storeVersion;
  const labelsMap = meta?.labels || {};
  const currentVersionStatus = versionInfo?.status;
  const hasDraft = !!(meta?.editingVersion || meta?.reviewingVersion);

  // Pipeline info for current version (from versionDetails in governance)
  const currentVersionSummary = meta?.versionDetails?.find((v) => v.version === selectedVersion);
  const currentPipelineInfoRaw = currentVersionSummary?.publishPipelineInfo;
  const currentPipelineInfo = parsePipelineInfo(currentPipelineInfoRaw);

  // Labels bound to the currently selected version
  const currentVersionLabels = Object.entries(labelsMap).filter(
    ([, val]) => val === selectedVersion,
  );

  // Load governance detail
  const loadGovernance = useCallback(async () => {
    setLoading(true);
    try {
      await fetchGovernanceDetail(namespaceId, promptKey);
    } catch {
      toast.error(t('prompt.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [fetchGovernanceDetail, namespaceId, promptKey, t]);

  // Load a specific version
  const loadVersion = useCallback(async (version: string) => {
    try {
      await fetchVersionDetail(namespaceId, promptKey, version);
    } catch {
      toast.error(t('prompt.loadFailed'));
    }
  }, [fetchVersionDetail, namespaceId, promptKey, t]);

  useEffect(() => {
    if (promptKey) {
      loadGovernance();
    }
    return () => clearCurrentPrompt();
  }, [promptKey, loadGovernance, clearCurrentPrompt]);

  // When governance loads, select the first version
  useEffect(() => {
    if (meta?.versionDetails?.length && !selectedVersion) {
      const first = meta.versionDetails[0];
      setSelectedVersion(first.version);
      loadVersion(first.version);
    }
  }, [meta, selectedVersion, loadVersion]);

  // When version detail loads, update template and draft state
  useEffect(() => {
    if (versionInfo) {
      setTemplate(versionInfo.template || '');
      setIsEditingDraft(false);
      setEditCommitMsg(versionInfo.commitMsg || '');
      setEditVariables((versionInfo.variables || []).map(v => ({
        name: v.name,
        defaultValue: v.defaultValue || '',
        description: v.description || '',
      })));
      const initialVals: Record<string, string> = {};
      (versionInfo.variables || []).forEach((v) => {
        if (v.defaultValue) initialVals[v.name] = v.defaultValue;
      });
      setVariableValues(initialVals);
    }
  }, [versionInfo]);

  const handleSelectVersion = (version: string) => {
    setSelectedVersion(version);
    loadVersion(version);
  };

  // Download the currently selected version as a Markdown file
  const [downloadingMd, setDownloadingMd] = useState(false);
  const handleDownloadMarkdown = useCallback(async () => {
    if (!selectedVersion || !promptKey) {
      return;
    }
    setDownloadingMd(true);
    try {
      const blob = await promptApi.downloadVersion({
        promptKey,
        version: selectedVersion,
        namespaceId,
      });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${promptKey}_${selectedVersion}.md`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
      toast.success(t('prompt.downloadMarkdownSuccess'));
    } catch {
      toast.error(t('prompt.downloadMarkdownFailed'));
    } finally {
      setDownloadingMd(false);
    }
  }, [promptKey, selectedVersion, namespaceId, t]);

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

  // --- Lifecycle actions ---
  const refreshAfterAction = async (version: string) => {
    await loadGovernance();
    await loadVersion(version);
  };

  const handleSubmit = async (version: string) => {
    setActionLoading(true);
    try {
      const ok = await submitVersion({ promptKey, version, namespaceId });
      if (ok) { toast.success(t('prompt.submitSuccess')); await refreshAfterAction(version); }
    } finally {
      setActionLoading(false);
    }
  };

  const handlePublish = async (version: string) => {
    setActionLoading(true);
    try {
      const ok = await publishVersion({ promptKey, version, namespaceId });
      if (ok) { toast.success(t('prompt.publishSuccess')); await refreshAfterAction(version); }
    } finally {
      setActionLoading(false);
    }
  };

  const handleForcePublish = async (version: string) => {
    setActionLoading(true);
    try {
      const ok = await forcePublishVersion({ promptKey, version, namespaceId });
      if (ok) { toast.success(t('prompt.forcePublishSuccess')); await refreshAfterAction(version); }
    } finally {
      setActionLoading(false);
    }
  };

  const handleRedraft = async (version: string) => {
    setActionLoading(true);
    try {
      await promptApi.redraft({ promptKey, version, namespaceId });
      toast.success(t('prompt.redraftSuccess'));
      await refreshAfterAction(version);
      setIsEditingDraft(true);
    } catch {
      /* handled by interceptor */
    } finally {
      setActionLoading(false);
    }
  };

  const handleOnline = async (version: string) => {
    setActionLoading(true);
    try {
      const ok = await onlineVersion({ promptKey, version, namespaceId });
      if (ok) { toast.success(t('prompt.onlineSuccess')); await refreshAfterAction(version); }
    } finally {
      setActionLoading(false);
    }
  };

  const handleOffline = async (version: string) => {
    setActionLoading(true);
    try {
      const ok = await offlineVersion({ promptKey, version, namespaceId });
      if (ok) { toast.success(t('prompt.offlineSuccess')); await refreshAfterAction(version); }
    } finally {
      setActionLoading(false);
    }
  };

  const handleDeleteDraft = async () => {
    setActionLoading(true);
    try {
      const ok = await storeDraftDelete(namespaceId, promptKey);
      if (ok) {
        toast.success(t('prompt.draftDeleteSuccess'));
        setSelectedVersion(null);
        setTemplate('');
        setIsEditingDraft(false);
        await loadGovernance();
      }
    } finally {
      setActionLoading(false);
    }
  };

  const handleCreateDraft = (basedOnVersion?: string) => {
    if (!basedOnVersion) return;
    setCreateDraftFromVersion(basedOnVersion);
    setCreateDraftTargetVersion('');
    setCreateDraftDialogOpen(true);
  };

  const handleConfirmCreateDraft = async () => {
    setCreateDraftLoading(true);
    try {
      await promptApi.createDraft({
        promptKey,
        basedOnVersion: createDraftFromVersion,
        targetVersion: createDraftTargetVersion.trim() || undefined,
        namespaceId,
      });
      toast.success(t('prompt.draftSaveSuccess'));
      setCreateDraftDialogOpen(false);
      await loadGovernance();
      // Auto-select the new draft version
      const updated = usePromptStore.getState().currentGovernance;
      if (updated?.editingVersion) {
        setSelectedVersion(updated.editingVersion);
        loadVersion(updated.editingVersion);
      }
    } catch { /* handled by interceptor */ } finally {
      setCreateDraftLoading(false);
    }
  };

  const handleStartEdit = () => setIsEditingDraft(true);

  const handleCancelEdit = () => {
    if (versionInfo) {
      setTemplate(versionInfo.template || '');
      setEditCommitMsg(versionInfo.commitMsg || '');
      setEditVariables((versionInfo.variables || []).map(v => ({
        name: v.name,
        defaultValue: v.defaultValue || '',
        description: v.description || '',
      })));
      const initialVals: Record<string, string> = {};
      (versionInfo.variables || []).forEach((v) => {
        if (v.defaultValue) initialVals[v.name] = v.defaultValue;
      });
      setVariableValues(initialVals);
    }
    setIsEditingDraft(false);
  };

  const handleSaveDraft = async () => {
    if (!isEditingDraft) return;
    setSavingDraft(true);
    try {
      // Use editVariables for variable definitions (defaults + descriptions)
      const variablesDef = variables.map((name) => {
        const editVar = editVariables.find((ev) => ev.name === name);
        return {
          name,
          defaultValue: editVar?.defaultValue || '',
          description: editVar?.description || '',
        };
      });
      const ok = await storeUpdateDraft({
        promptKey,
        template,
        variables: variablesDef.length > 0 ? JSON.stringify(variablesDef) : undefined,
        commitMsg: editCommitMsg.trim() || undefined,
        namespaceId,
      });
      if (ok) {
        toast.success(t('prompt.draftSaveSuccess'));
        if (selectedVersion) {
          await loadGovernance();
          await loadVersion(selectedVersion);
        }
      }
    } finally {
      setSavingDraft(false);
    }
  };

  // --- Labels ---
  const handleSaveLabels = async (newLabels: Record<string, string>) => {
    const ok = await updateLabels({ promptKey, labels: JSON.stringify(newLabels), namespaceId });
    if (ok) {
      toast.success(t('prompt.labelsUpdateSuccess'));
      await loadGovernance();
      if (selectedVersion) await loadVersion(selectedVersion);
    }
  };

  // --- BizTags ---
  const handleSaveBizTags = async (nextBizTags: string[]) => {
    try {
      await promptApi.updateBizTags({ promptKey, bizTags: nextBizTags.join(','), namespaceId });
      toast.success(t('prompt.bizTagsUpdateSuccess'));
      await loadGovernance();
    } catch { /* handled by interceptor */ }
  };

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

  // --- Edit metadata ---
  const handleEdit = () => {
    setEditDescription(meta?.description || '');
    setEditBizTags(meta?.bizTags || []);
    setEditTagInput('');
    setEditDialogOpen(true);
  };

  const handleEditSave = async () => {
    setEditSaving(true);
    try {
      await promptApi.updateDescription({ promptKey, description: editDescription.trim(), namespaceId });
      await promptApi.updateBizTags({ promptKey, bizTags: editBizTags.join(','), namespaceId });
      toast.success(t('prompt.descriptionUpdateSuccess'));
      setEditDialogOpen(false);
      await loadGovernance();
      if (selectedVersion) await loadVersion(selectedVersion);
    } catch { /* handled by interceptor */ } finally {
      setEditSaving(false);
    }
  };

  const handleEditAddTag = () => {
    const tag = editTagInput.trim();
    if (!tag || editBizTags.includes(tag)) { setEditTagInput(''); return; }
    setEditBizTags((prev) => [...prev, tag]);
    setEditTagInput('');
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

  // Loading skeleton
  if (loading && !meta) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-32 w-full rounded-xl" />
        <div className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,1fr)_320px]">
          <div className="space-y-4">
            <Skeleton className="h-48 w-full" />
            <Skeleton className="h-64 w-full" />
          </div>
          <div className="space-y-4">
            <Skeleton className="h-48 w-full" />
            <Skeleton className="h-48 w-full" />
          </div>
        </div>
      </div>
    );
  }

  if (!meta) {
    return (
      <div className="flex flex-col items-center justify-center py-24 space-y-4">
        <p className="text-sm text-destructive">{t('prompt.loadFailed')}</p>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => navigate('/promptManagement')}>{t('prompt.backToList')}</Button>
          <Button onClick={() => loadGovernance()}>{t('prompt.retry')}</Button>
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
            <Button variant="ghost" size="sm" className="h-7 gap-1.5 text-muted-foreground hover:text-foreground -ml-2" onClick={() => navigate('/promptManagement')}>
              <ArrowLeft className="h-3.5 w-3.5" />
              {t('prompt.backToList')}
            </Button>
            <div className="flex items-center gap-2">
              {/* Version selector */}
              {meta.versionDetails && meta.versionDetails.length > 0 && (
                <Select value={selectedVersion || ''} onValueChange={handleSelectVersion}>
                  <SelectTrigger className="w-[140px] h-7 text-xs bg-background/80">
                    <SelectValue placeholder={t('prompt.selectVersion')} />
                  </SelectTrigger>
                  <SelectContent>
                    {meta.versionDetails.map((v) => {
                      const vPipeline = parsePipelineInfo(v.publishPipelineInfo);
                      const isVersionPendingPublish = (v.status === 'reviewed' && vPipeline?.status !== 'REJECTED') || (v.status === 'reviewing' && vPipeline?.status === 'APPROVED');
                      const isVersionRejected = v.status === 'reviewed' && vPipeline?.status === 'REJECTED';
                      return (
                      <SelectItem key={v.version} value={v.version}>
                        <span className="flex items-center gap-2">
                          <span>{v.version}</span>
                          {meta.labels?.latest === v.version && (
                            <Badge className="bg-emerald-100 text-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-300 text-[10px] px-1 py-0 border-0">
                              {t('prompt.latestVersion')}
                            </Badge>
                          )}
                          {v.status === 'draft' && (
                            <Badge className="bg-amber-100 text-amber-700 dark:bg-amber-950/50 dark:text-amber-300 text-[10px] px-1 py-0 border-0">
                              {t('prompt.versionStatus.draft')}
                            </Badge>
                          )}
                          {isVersionRejected && (
                            <Badge className="bg-red-100 text-red-700 dark:bg-red-950/50 dark:text-red-300 text-[10px] px-1 py-0 border-0">
                              {t('prompt.versionStatus.rejected')}
                            </Badge>
                          )}
                          {!isVersionRejected && (v.status === 'reviewing' || v.status === 'reviewed') && (
                            <Badge className={isVersionPendingPublish
                              ? 'bg-teal-100 text-teal-700 dark:bg-teal-950/50 dark:text-teal-300 text-[10px] px-1 py-0 border-0'
                              : 'bg-blue-100 text-blue-700 dark:bg-blue-950/50 dark:text-blue-300 text-[10px] px-1 py-0 border-0'
                            }>
                              {t(isVersionPendingPublish ? 'prompt.versionStatus.pendingPublish' : 'prompt.versionStatus.reviewing')}
                            </Badge>
                          )}
                        </span>
                      </SelectItem>
                    );
                    })}
                  </SelectContent>
                </Select>
              )}
              <Button variant="outline" size="sm" className="h-7 text-xs" onClick={() => setVersionSheetOpen(true)}>
                <History className="mr-1 h-3 w-3" />
                {t('prompt.versionHistory')}
              </Button>
              <Button variant="destructive" size="sm" className="h-7 w-7 p-0" onClick={() => setDeleteDialogOpen(true)}>
                <Trash2 className="h-3.5 w-3.5" />
              </Button>
            </div>
          </div>

          {/* Identity */}
          <div className="flex items-start gap-4">
            <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-amber-500 to-orange-400 shadow-lg shadow-amber-500/20">
              <MessageSquare className="h-7 w-7 text-white" />
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2.5 mb-1">
                <h1 className="text-xl font-bold tracking-tight">{promptKey}</h1>
                {selectedVersion && (
                  <span className="text-xs text-muted-foreground font-mono bg-muted/60 px-1.5 py-0.5 rounded">v{selectedVersion}</span>
                )}
              </div>
              <div className="flex items-center gap-2">
                {meta.description ? (
                  <p className="text-sm text-muted-foreground leading-relaxed max-w-2xl">{meta.description}</p>
                ) : (
                  <p className="text-sm text-muted-foreground/60 italic">{t('prompt.noDescription')}</p>
                )}
                <button onClick={handleEdit} className="text-muted-foreground hover:text-primary transition-colors shrink-0">
                  <Pencil className="h-3.5 w-3.5" />
                </button>
              </div>

              {/* Meta row */}
              <div className="flex items-center gap-4 mt-2 text-xs text-muted-foreground">
                <span className={cn(
                  'inline-flex items-center gap-1',
                  meta.onlineCnt > 0 ? 'text-emerald-700 dark:text-emerald-300' : '',
                )}>
                  <Globe className="h-3 w-3" />
                  {t('prompt.onlineCount', { count: meta.onlineCnt ?? 0 })}
                </span>
                {meta.editingVersion && (
                  <span className="inline-flex items-center gap-1 text-amber-700 dark:text-amber-300">
                    <FileEdit className="h-3 w-3" />
                    {t('prompt.hasDraft')}
                  </span>
                )}
                {meta.gmtModified && (
                  <span className="inline-flex items-center gap-1">
                    <Clock className="h-3 w-3" />
                    {dayjs(meta.gmtModified).format('YYYY-MM-DD HH:mm')}
                  </span>
                )}
                {meta.bizTags && meta.bizTags.length > 0 && (
                  <div className="flex items-center gap-1">
                    {meta.bizTags.slice(0, 3).map((tag) => (
                      <Badge key={tag} variant="outline" className="text-[10px] px-1.5 py-0">{tag}</Badge>
                    ))}
                  </div>
                )}
              </div>

              {/* Version lifecycle action buttons */}
              {selectedVersion && currentVersionStatus && (
                <div className="mt-3 pt-3 border-t border-border/40">
                  <div className="flex items-center gap-2 flex-wrap">
                    {/* Draft actions */}
                    {currentVersionStatus === 'draft' && (
                      <>
                        {isEditingDraft ? (
                          <>
                            <Button variant="outline" size="sm" className="h-7 text-xs gap-1.5" onClick={handleCancelEdit} disabled={savingDraft}>
                              <X className="h-3 w-3" />
                              {t('common.cancel')}
                            </Button>
                            <Button size="sm" className="h-7 text-xs gap-1.5" onClick={handleSaveDraft} disabled={savingDraft}>
                              {savingDraft ? <Loader2 className="h-3 w-3 animate-spin" /> : <Save className="h-3 w-3" />}
                              {t('prompt.saveDraft')}
                            </Button>
                          </>
                        ) : (
                          <>
                            <Button variant="outline" size="sm" className="h-7 text-xs gap-1.5" onClick={handleStartEdit} disabled={actionLoading}>
                              <Pencil className="h-3 w-3" />
                              {t('prompt.editDraft')}
                            </Button>
                            <div className="h-4 w-px bg-border mx-0.5" />
                            <Button size="sm" className="h-7 text-xs gap-1.5" disabled={actionLoading} onClick={() => handleSubmit(selectedVersion)}>
                              <Send className="h-3 w-3" />
                              {currentPipelineInfo && currentPipelineInfo.status === 'REJECTED'
                                ? t('prompt.resubmit')
                                : t('prompt.submit')}
                            </Button>
                            <Button variant="outline" size="sm" className="h-7 text-xs gap-1.5 text-destructive hover:text-destructive hover:bg-destructive/10" disabled={actionLoading} onClick={() => handleDeleteDraft()}>
                              <Trash2 className="h-3 w-3" />
                              {t('prompt.deleteDraft')}
                            </Button>
                            {currentPipelineInfo && currentPipelineInfo.status === 'REJECTED' && (
                              <PipelineStatusDisplay pipelineInfo={currentPipelineInfo} compact />
                            )}
                            {globalAdmin && currentPipelineInfo && currentPipelineInfo.status === 'REJECTED' && !currentPipelineInfo.historical && (
                              <Button
                                variant="outline"
                                size="sm"
                                className="h-7 text-xs gap-1.5 text-destructive hover:text-destructive hover:bg-destructive/10 border-destructive/40"
                                disabled={actionLoading}
                                onClick={() => setForcePublishConfirmOpen(true)}
                              >
                                <ShieldAlert className="h-3 w-3" />
                                {t('prompt.forcePublish')}
                              </Button>
                            )}
                          </>
                        )}
                      </>
                    )}

                    {/* Reviewing / Reviewed actions */}
                    {(currentVersionStatus === 'reviewing' || currentVersionStatus === 'reviewed') && (
                      <>
                        <Button
                          size="sm"
                          className="h-7 text-xs gap-1.5"
                          disabled={actionLoading || !!(currentPipelineInfo && currentPipelineInfo.status !== 'APPROVED')}
                          onClick={() => handlePublish(selectedVersion)}
                        >
                          <CheckCircle2 className="h-3 w-3" />
                          {currentPipelineInfo && currentPipelineInfo.status === 'IN_PROGRESS'
                            ? t('prompt.pipelineInProgress')
                            : t('prompt.publish')}
                        </Button>
                        {currentVersionStatus === 'reviewed' && (
                          <>
                            <Button
                              variant="outline"
                              size="sm"
                              className="h-7 text-xs gap-1.5"
                              disabled={actionLoading}
                              onClick={() => handleRedraft(selectedVersion)}
                            >
                              <Pencil className="h-3 w-3" />
                              {t('prompt.redraft')}
                            </Button>
                            <Button
                              variant="outline"
                              size="sm"
                              className="h-7 text-xs gap-1.5 text-destructive hover:text-destructive hover:bg-destructive/10"
                              disabled={actionLoading}
                              onClick={() => handleDeleteDraft()}
                            >
                              <Trash2 className="h-3 w-3" />
                              {t('prompt.deleteDraft')}
                            </Button>
                          </>
                        )}
                        {currentPipelineInfo && currentPipelineInfo.status === 'APPROVED' && (
                          <PipelineStatusDisplay pipelineInfo={currentPipelineInfo} compact />
                        )}
                        {globalAdmin && currentPipelineInfo && currentPipelineInfo.status === 'REJECTED' && (
                          <>
                            <PipelineStatusDisplay pipelineInfo={currentPipelineInfo} compact />
                            <Button
                              variant="outline"
                              size="sm"
                              className="h-7 text-xs gap-1.5 text-destructive hover:text-destructive hover:bg-destructive/10 border-destructive/40"
                              disabled={actionLoading}
                              onClick={() => setForcePublishConfirmOpen(true)}
                            >
                              <ShieldAlert className="h-3 w-3" />
                              {t('prompt.forcePublish')}
                            </Button>
                          </>
                        )}
                      </>
                    )}

                    {/* Online actions */}
                    {currentVersionStatus === 'online' && (
                      <Button variant="outline" size="sm" className="h-7 text-xs gap-1.5" disabled={actionLoading} onClick={() => handleOffline(selectedVersion)}>
                        <PowerOff className="h-3 w-3" />
                        {t('prompt.offline')}
                      </Button>
                    )}

                    {/* Offline actions */}
                    {currentVersionStatus === 'offline' && (
                      <Button size="sm" className="h-7 text-xs gap-1.5" disabled={actionLoading} onClick={() => handleOnline(selectedVersion)}>
                        <Power className="h-3 w-3" />
                        {t('prompt.online')}
                      </Button>
                    )}

                    {/* Create draft from (online/offline) */}
                    {(currentVersionStatus === 'online' || currentVersionStatus === 'offline') && (() => {
                      const btn = (
                        <Button variant="outline" size="sm" className="h-7 text-xs gap-1.5" disabled={hasDraft || actionLoading} onClick={() => handleCreateDraft(selectedVersion)}>
                          <Plus className="h-3 w-3" />
                          {t('prompt.createDraftFrom')}
                        </Button>
                      );
                      return hasDraft ? (
                        <Tooltip>
                          <TooltipTrigger asChild><span>{btn}</span></TooltipTrigger>
                          <TooltipContent className="bg-amber-50 border border-amber-200 text-amber-800 dark:bg-amber-950 dark:border-amber-800 dark:text-amber-200">
                            <span className="flex items-center gap-1.5">
                              <AlertCircle className="h-3 w-3 shrink-0" />
                              {t('prompt.draftExistsTip')}
                            </span>
                          </TooltipContent>
                        </Tooltip>
                      ) : btn;
                    })()}
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* ===== Content Grid ===== */}
      <div className={cn('grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,1fr)_320px]', loading && 'opacity-50 pointer-events-none')}>
        {/* Left: Template + Debug */}
        <div className="space-y-5">
          {/* Template Card */}
          <Card className="overflow-hidden py-0 gap-0">
            <div className="px-5 py-3.5 border-b bg-muted/30 flex items-center justify-between">
              <h2 className="text-sm font-semibold flex items-center gap-2">
                <Sparkles className="h-4 w-4 text-amber-500" />
                {t('prompt.template')}
              </h2>
              {isEditingDraft && copilotEnabled && (
                <Button variant="outline" size="sm" className="h-7 text-xs gap-1.5" onClick={() => setOptimizeOpen(true)} disabled={!template.trim()}>
                  <Sparkles className="h-3 w-3" />
                  {t('prompt.aiOptimize')}
                </Button>
              )}
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
                  readOnly: !isEditingDraft,
                }}
                onChange={(value) => isEditingDraft && setTemplate(value || '')}
                loading={<div className="flex items-center justify-center h-[420px] text-muted-foreground text-sm">Loading...</div>}
              />
            </CardContent>
          </Card>

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
        </div>
        <div className="space-y-4 lg:w-[320px]">
          {/* Download & CLI Card */}
          <CliCommandCard
            commands={[]}
            onDownload={handleDownloadMarkdown}
            downloadFileName={selectedVersion ? `${promptKey}-${selectedVersion}.md` : undefined}
            downloadDisabled={!selectedVersion || downloadingMd}
          />

          {/* Basic Info Card */}
          <Card className="overflow-hidden py-0 gap-0">
            <div className="px-4 py-3 border-b bg-muted/30">
              <h2 className="text-sm font-semibold flex items-center gap-2">
                <Server className="h-4 w-4 text-muted-foreground" />
                {t('prompt.basicInfo')}
              </h2>
            </div>
            <CardContent className="p-0">
              <div className="grid grid-cols-2 [&>*:nth-child(n+3)]:border-t [&>*:nth-child(even)]:border-l border-border">
                <InfoCell
                  compact
                  label={t('prompt.status')}
                  value={<StatusBadge status={currentVersionStatus} label={currentVersionStatus ? t(`prompt.versionStatus.${currentVersionStatus}`) : '-'} />}
                  icon={<Tag className="h-3.5 w-3.5" />}
                />
                <InfoCell compact label={t('prompt.publisher')} value={versionInfo?.srcUser || '-'} icon={<User className="h-3.5 w-3.5" />} />
                <InfoCell compact label={t('prompt.downloads')} value={String(meta.downloadCount ?? 0)} icon={<Download className="h-3.5 w-3.5" />} />
                {currentVersionSummary && (
                  <InfoCell compact label={t('prompt.versionDownloads')} value={String(currentVersionSummary.downloadCount ?? 0)} icon={<Download className="h-3.5 w-3.5" />} />
                )}
                <InfoCell compact label={t('prompt.commitMsg')} value={
                  isEditingDraft ? (
                    <Input
                      value={editCommitMsg}
                      onChange={(e) => setEditCommitMsg(e.target.value)}
                      placeholder={t('prompt.commitMsgPlaceholder')}
                      className="h-7 text-xs bg-transparent"
                    />
                  ) : (versionInfo?.commitMsg || '-')
                } icon={<MessageSquare className="h-3.5 w-3.5" />} colSpan={2} />
              </div>
            </CardContent>
          </Card>

          {/* Pipeline status card */}
          {currentPipelineInfo && (
            <Card className="overflow-hidden py-0 gap-0">
              <div className="px-4 py-3 border-b bg-muted/30">
                <h2 className="text-sm font-semibold flex items-center gap-2">
                  <GitBranch className="h-4 w-4 text-muted-foreground" />
                  {t('prompt.pipelineStatus')}
                </h2>
              </div>
              <CardContent className="p-3.5">
                <PipelineStatusDisplay pipelineInfo={currentPipelineInfo} onRefresh={() => loadGovernance()} />
              </CardContent>
            </Card>
          )}

          {/* BizTags card */}
          <Card className="overflow-hidden py-0 gap-0">
            <div className="px-4 py-3 border-b bg-muted/30 flex items-center justify-between">
              <h2 className="text-sm font-semibold flex items-center gap-2">
                <Tag className="h-4 w-4 text-muted-foreground" />
                {t('common.bizTags')}
              </h2>
              <Button variant="ghost" size="icon" className="h-6 w-6" onClick={() => setBizTagDialogOpen(true)}>
                <Pencil className="h-3 w-3" />
              </Button>
            </div>
            <CardContent className="p-3.5">
              {meta.bizTags && meta.bizTags.length > 0 ? (
                <div className="flex flex-wrap gap-1.5">
                  {meta.bizTags.map((tag) => (
                    <DetailTagChip key={tag} label={tag} />
                  ))}
                </div>
              ) : (
                <p className="text-xs text-muted-foreground">{t('prompt.noLabels')}</p>
              )}
            </CardContent>
          </Card>

          {/* Version Labels card */}
          <Card className="overflow-hidden py-0 gap-0">
            <div className="px-4 py-3 border-b bg-muted/30 flex items-center justify-between">
              <h2 className="text-sm font-semibold flex items-center gap-2">
                <Tag className="h-4 w-4 text-muted-foreground" />
                {t('common.versionLabels.title')}
              </h2>
              {selectedVersion && currentVersionStatus !== 'draft' && currentVersionStatus !== 'reviewing' && (
                <Button variant="ghost" size="icon" className="h-6 w-6" onClick={() => setLabelDialogOpen(true)}>
                  <Pencil className="h-3 w-3" />
                </Button>
              )}
            </div>
            <CardContent className="p-3.5">
              {currentVersionLabels.length > 0 ? (
                <div className="flex flex-wrap gap-1.5">
                  {currentVersionLabels.map(([key]) => (
                    <DetailTagChip key={key} label={key} />
                  ))}
                </div>
              ) : (
                <p className="text-xs text-muted-foreground">{t('common.versionLabels.noLabels')}</p>
              )}
            </CardContent>
          </Card>

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
              ) : isEditingDraft ? (
                <div className="divide-y divide-border">
                  {variables.map((varName) => {
                    const editVar = editVariables.find((ev) => ev.name === varName);
                    return (
                      <div key={varName} className="p-3 space-y-2 hover:bg-muted/20 transition-colors">
                        <div className="flex items-center gap-2">
                          <div className="h-1.5 w-1.5 rounded-full bg-amber-400 shrink-0" />
                          <code className="text-[11px] font-mono font-medium text-amber-700 dark:text-amber-300">{varName}</code>
                        </div>
                        <div className="pl-3.5 space-y-1.5">
                          <div className="flex items-center gap-2">
                            <span className="text-[10px] text-muted-foreground w-10 shrink-0">{t('prompt.variableDefault')}</span>
                            <Input
                              value={editVar?.defaultValue || ''}
                              onChange={(e) => {
                                setEditVariables((prev) => {
                                  const next = [...prev];
                                  const idx = next.findIndex((v) => v.name === varName);
                                  if (idx >= 0) next[idx] = { ...next[idx], defaultValue: e.target.value };
                                  else next.push({ name: varName, defaultValue: e.target.value, description: '' });
                                  return next;
                                });
                              }}
                              className="h-6 text-[11px] bg-background"
                            />
                          </div>
                          <div className="flex items-center gap-2">
                            <span className="text-[10px] text-muted-foreground w-10 shrink-0">{t('prompt.variableDescription')}</span>
                            <Input
                              value={editVar?.description || ''}
                              onChange={(e) => {
                                setEditVariables((prev) => {
                                  const next = [...prev];
                                  const idx = next.findIndex((v) => v.name === varName);
                                  if (idx >= 0) next[idx] = { ...next[idx], description: e.target.value };
                                  else next.push({ name: varName, defaultValue: '', description: e.target.value });
                                  return next;
                                });
                              }}
                              className="h-6 text-[11px] bg-background"
                            />
                          </div>
                        </div>
                      </div>
                    );
                  })}
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
              {t('prompt.totalVersions', { count: meta.versionDetails?.length ?? 0 })}
            </SheetDescription>
          </SheetHeader>
          <div className="flex-1 overflow-y-auto px-4 py-4">
            <PromptVersionTimeline
              versions={meta.versionDetails || []}
              currentVersion={selectedVersion || ''}
              hasEditingVersion={!!meta.editingVersion}
              hasReviewingVersion={!!meta.reviewingVersion}
              onSelectVersion={(version) => {
                handleSelectVersion(version);
                setVersionSheetOpen(false);
              }}
              onCreateDraft={handleCreateDraft}
              onDeleteDraft={handleDeleteDraft}
              onSubmit={handleSubmit}
              onPublish={handlePublish}
              onForcePublish={handleForcePublish}
              onOnline={handleOnline}
              onOffline={handleOffline}
              allLabels={labelsMap}
              onSaveLabels={handleSaveLabels}
              isGlobalAdmin={globalAdmin}
            />
          </div>
        </SheetContent>
      </Sheet>

      {/* Force-publish confirmation dialog */}
      <Dialog open={forcePublishConfirmOpen} onOpenChange={setForcePublishConfirmOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <ShieldAlert className="h-5 w-5 text-destructive" />
              {t('prompt.forcePublishConfirmTitle')}
            </DialogTitle>
            <DialogDescription>
              {t('prompt.forcePublishConfirmDesc', { version: selectedVersion })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setForcePublishConfirmOpen(false)} disabled={actionLoading}>
              {t('common.cancel')}
            </Button>
            <Button
              variant="destructive"
              disabled={actionLoading}
              onClick={async () => {
                setForcePublishConfirmOpen(false);
                if (selectedVersion) {
                  await handleForcePublish(selectedVersion);
                }
              }}
            >
              <ShieldAlert className="h-4 w-4 mr-1" />
              {t('prompt.forcePublishConfirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* ===== Create Draft From Version Dialog ===== */}
      <Dialog open={createDraftDialogOpen} onOpenChange={setCreateDraftDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('prompt.createDraftFrom')}</DialogTitle>
            <DialogDescription>
              {t('prompt.createDraftFromDesc', { version: createDraftFromVersion })}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-2">
              <Label>{t('prompt.version')} ({t('common.optional')})</Label>
              <Input
                value={createDraftTargetVersion}
                onChange={(e) => setCreateDraftTargetVersion(e.target.value)}
                placeholder={t('prompt.versionPlaceholder')}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCreateDraftDialogOpen(false)} disabled={createDraftLoading}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleConfirmCreateDraft} disabled={createDraftLoading}>
              {createDraftLoading ? t('common.loading') : t('prompt.createDraft')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* ===== Dialogs ===== */}

      {/* Edit Metadata Dialog */}
      <Dialog open={editDialogOpen} onOpenChange={(open) => { if (!editSaving) setEditDialogOpen(open); }}>
        <DialogContent className="max-w-lg p-0 gap-0 overflow-hidden">
          <div className="px-6 pt-6 pb-4">
            <DialogHeader className="space-y-1.5">
              <DialogTitle className="text-base">{t('prompt.editMetadata')}</DialogTitle>
              <DialogDescription className="font-mono text-xs tracking-wide">{promptKey}</DialogDescription>
            </DialogHeader>
          </div>
          <Separator />
          <div className="px-6 py-5 space-y-3">
            <div className="flex flex-col gap-3">
              <Label className="text-sm font-medium text-muted-foreground">{t('prompt.description')}</Label>
              <Textarea value={editDescription} onChange={(e) => setEditDescription(e.target.value)} placeholder={t('prompt.descriptionPlaceholder')} rows={3} className="bg-transparent resize-none text-sm" />
            </div>
            <div className="flex flex-col gap-3">
              <Label className="text-sm font-medium text-muted-foreground">{t('prompt.bizTags')}</Label>
              <div className="rounded-lg border bg-muted/20 p-3 space-y-2.5">
                {editBizTags.length > 0 && (
                  <div className="flex flex-wrap gap-1.5">
                    {editBizTags.map((tag) => (
                      <Badge key={tag} variant="secondary" className="gap-1.5 pl-2.5 pr-1 py-0.5 text-xs font-normal bg-background border shadow-sm">
                        {tag}
                        <button onClick={() => setEditBizTags((prev) => prev.filter((t) => t !== tag))} className="rounded-full hover:bg-destructive/10 hover:text-destructive p-0.5 transition-colors">
                          <X className="h-3 w-3" />
                        </button>
                      </Badge>
                    ))}
                  </div>
                )}
                <div className="flex gap-2">
                  <Input value={editTagInput} onChange={(e) => setEditTagInput(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); handleEditAddTag(); } }} placeholder={t('prompt.tagPlaceholder')} className="bg-transparent flex-1 h-8 text-sm" />
                  <Button type="button" variant="outline" size="sm" className="h-8 px-3 shrink-0" onClick={handleEditAddTag} disabled={!editTagInput.trim()}>
                    <Plus className="h-3.5 w-3.5 mr-1" />{t('common.add')}
                  </Button>
                </div>
              </div>
            </div>
          </div>
          <Separator />
          <div className="px-6 py-4 flex justify-end gap-2 bg-muted/20">
            <Button variant="outline" size="sm" onClick={() => setEditDialogOpen(false)} disabled={editSaving}>{t('common.cancel')}</Button>
            <Button size="sm" onClick={handleEditSave} disabled={editSaving}>{editSaving ? t('common.loading') : t('common.save')}</Button>
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
            <Button variant="destructive" onClick={handleDelete} disabled={deleteLoading}>{deleteLoading ? t('common.loading') : t('common.delete')}</Button>
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

      {/* BizTag Edit Dialog */}
      <BizTagEditDialog
        open={bizTagDialogOpen}
        onOpenChange={setBizTagDialogOpen}
        tags={meta?.bizTags || []}
        placeholder={t('prompt.tagPlaceholder')}
        emptyText={t('prompt.noLabels')}
        onSave={handleSaveBizTags}
      />

      {/* Label Bind Dialog */}
      {selectedVersion && (
        <LabelBindDialog
          open={labelDialogOpen}
          onOpenChange={setLabelDialogOpen}
          version={selectedVersion}
          allLabels={labelsMap}
          onSave={handleSaveLabels}
        />
      )}
    </div>
  );
}

function InfoCell({ label, value, icon, compact = false, colSpan }: { label: string; value: React.ReactNode; icon?: React.ReactNode; compact?: boolean; colSpan?: number }) {
  return (
    <div className={cn('flex items-center gap-3 px-5 py-3', compact && 'gap-2.5 px-4 py-2.5', colSpan === 2 && 'col-span-2')}>
      {icon && <span className="text-muted-foreground/60 shrink-0">{icon}</span>}
      <div className="min-w-0 flex-1">
        <p className="text-[11px] text-muted-foreground leading-none mb-1">{label}</p>
        <div className={cn('text-sm font-medium break-all', compact && 'text-[13px]')}>{value || '-'}</div>
      </div>
    </div>
  );
}

function StatusBadge({ status, label }: { status?: string; label: string }) {
  const statusStyles: Record<string, string> = {
    draft: 'bg-amber-50 text-amber-700 dark:bg-amber-950/40 dark:text-amber-300',
    reviewing: 'bg-blue-50 text-blue-700 dark:bg-blue-950/40 dark:text-blue-300',
    reviewed: 'bg-teal-50 text-teal-700 dark:bg-teal-950/40 dark:text-teal-300',
    online: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300',
    offline: 'bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400',
  };
  return (
    <span className={cn('inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium', status ? statusStyles[status] : statusStyles.offline)}>
      {label}
    </span>
  );
}
