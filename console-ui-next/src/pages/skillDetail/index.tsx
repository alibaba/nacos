import { useEffect, useCallback, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import {
  ArrowLeft,
  History,
  Wand2,
  Hash,
  Clock,
  Tag,
  Globe,
  FileText,
  Download,
  Pencil,
  Save,
  X,
  GitBranch,
  Send,
  CheckCircle2,
  Power,
  PowerOff,
  Trash2,
  Plus,
  Sparkles,
} from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Switch } from '@/components/ui/switch';
import { Input } from '@/components/ui/input';
import MDEditor from '@uiw/react-md-editor';
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet';
import { useSkillStore } from '@/stores/skill-store';
import { useNamespaceStore } from '@/stores/namespace-store';
import { skillApi } from '@/api/skill';
import type { SkillDocument, SkillResource, SkillVersionSummary } from '@/types/skill';
import { parsePipelineInfo } from '@/types/skill';
import { cn } from '@/lib/utils';
import dayjs from 'dayjs';

import { SkillVersionTimeline } from '../skillManagement/components/SkillVersionTimeline';
import { PipelineStatusDisplay } from '../skillManagement/components/PipelineStatusDisplay';
import { VersionLabelEditor } from '@/components/ai/VersionLabelEditor';
import { SkillOptimizeDialog } from '@/components/ai/skill/SkillOptimizeDialog';
import { sortVersionsDescending } from '../skillManagement/components/version-utils';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { SkillResourcePanel } from './SkillResourcePanel';

export default function SkillDetailPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { name: routeName } = useParams<{ name: string }>();
  const skillName = routeName ? decodeURIComponent(routeName) : '';
  const { currentNamespace } = useNamespaceStore();
  const namespaceId = currentNamespace || 'public';

  const {
    currentDetail,
    detailLoading,
    error,
    fetchDetail,
    clearDetail,
    clearError,
  } = useSkillStore();

  const [actionLoading, setActionLoading] = useState(false);
  const [versionSheetOpen, setVersionSheetOpen] = useState(false);
  const [selectedVersion, setSelectedVersion] = useState<string>('');
  const [versionDoc, setVersionDoc] = useState<SkillDocument | null>(null);
  const [docLoading, setDocLoading] = useState(false);

  // Draft editing state
  const [isEditingDraft, setIsEditingDraft] = useState(false);
  const [editInstruction, setEditInstruction] = useState('');
  const [editDescription, setEditDescription] = useState('');
  const [draftSaving, setDraftSaving] = useState(false);

  // Resource editing state
  const [editResources, setEditResources] = useState<Record<string, SkillResource>>({});

  // Enable/disable toggle state
  const [enableToggling, setEnableToggling] = useState(false);

  // Labels saving state
  const [labelsSaving, setLabelsSaving] = useState(false);

  // AI Optimize dialog state
  const [optimizeDialogOpen, setOptimizeDialogOpen] = useState(false);

  const loadDetail = useCallback(() => {
    if (skillName) {
      fetchDetail(namespaceId, skillName);
    }
  }, [fetchDetail, namespaceId, skillName]);

  useEffect(() => {
    setVersionDoc(null);
    setSelectedVersion('');
    setIsEditingDraft(false);
    loadDetail();
    return () => {
      clearDetail();
      clearError();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [skillName, namespaceId]);

  // Auto-select first version when detail loads
  useEffect(() => {
    if (!currentDetail || detailLoading) return;
    const versions = sortVersionsDescending(currentDetail.versions || []);
    const first = versions[0]?.version;
    if (first && !selectedVersion) {
      setSelectedVersion(first);
    }
  }, [currentDetail, detailLoading, selectedVersion]);

  // Load version document when selected version changes
  useEffect(() => {
    if (!selectedVersion || !skillName) {
      setVersionDoc(null);
      return;
    }

    let cancelled = false;
    setDocLoading(true);
    setVersionDoc(null);
    setIsEditingDraft(false);

    skillApi.getVersion({
      namespaceId,
      skillName,
      version: selectedVersion,
    }).then((response) => {
      if (!cancelled) {
        setVersionDoc(response.data);
      }
    }).catch(() => {
      if (!cancelled) {
        setVersionDoc(null);
      }
    }).finally(() => {
      if (!cancelled) {
        setDocLoading(false);
      }
    });

    return () => {
      cancelled = true;
    };
  }, [selectedVersion, namespaceId, skillName]);

  // ===== Draft editing handlers =====

  const handleStartEdit = () => {
    setEditInstruction(versionDoc?.instruction ?? '');
    setEditDescription(versionDoc?.description ?? '');
    setEditResources({ ...(versionDoc?.resource ?? {}) });
    setIsEditingDraft(true);
  };

  const handleCancelEdit = () => {
    setIsEditingDraft(false);
    setEditResources({});
  };

  const handleSaveDraft = async () => {
    setDraftSaving(true);
    try {
      const skillCard = JSON.stringify({
        name: skillName,
        description: editDescription.trim(),
        instruction: editInstruction,
        resource: editResources,
      });
      await skillApi.updateDraft({ namespaceId, skillCard });
      toast.success(t('skill.draftSaveSuccess'));
      setIsEditingDraft(false);
      // Reload both detail and version doc
      loadDetail();
      // Re-fetch version doc
      const response = await skillApi.getVersion({ namespaceId, skillName, version: selectedVersion });
      setVersionDoc(response.data);
    } catch {
      // handled by interceptor
    } finally {
      setDraftSaving(false);
    }
  };

  // ===== AI Optimize handler =====

  const handleOptimizationApply = async (optimizedSkill: SkillDocument) => {
    try {
      const skillCard = JSON.stringify({
        name: skillName,
        description: optimizedSkill.description,
        instruction: optimizedSkill.instruction,
        resource: optimizedSkill.resource,
      });
      await skillApi.updateDraft({ namespaceId, skillCard });
      toast.success(t('skill.optimizeSuccess'));
      loadDetail();
      const response = await skillApi.getVersion({ namespaceId, skillName, version: selectedVersion });
      setVersionDoc(response.data);
      setIsEditingDraft(false);
    } catch {
      // handled by interceptor
    }
  };

  // ===== Enable/disable handler =====

  const handleToggleEnable = async () => {
    if (!currentDetail) return;
    setEnableToggling(true);
    try {
      if (currentDetail.enable) {
        await skillApi.offline({ namespaceId, skillName, scope: 'skill' });
      } else {
        await skillApi.online({ namespaceId, skillName, scope: 'skill' });
      }
      toast.success(t(currentDetail.enable ? 'skill.offlineSuccess' : 'skill.onlineSuccess'));
      loadDetail();
    } catch {
      // handled by interceptor
    } finally {
      setEnableToggling(false);
    }
  };

  // ===== Labels handler =====

  const handleSaveLabels = async (labels: Record<string, string>) => {
    setLabelsSaving(true);
    try {
      await skillApi.updateLabels({
        namespaceId,
        skillName,
        labels: JSON.stringify(labels),
      });
      toast.success(t('common.versionLabels.updateSuccess'));
      loadDetail();
    } catch {
      // handled by interceptor
    } finally {
      setLabelsSaving(false);
    }
  };

  // ===== Version lifecycle handlers =====

  const handleCreateDraft = async (basedOnVersion?: string) => {
    setActionLoading(true);
    try {
      await skillApi.createDraft({ namespaceId, skillName, basedOnVersion });
      toast.success(t('skill.createDraftSuccess'));
      await fetchDetail(namespaceId, skillName);
      // Switch to the newly created draft version
      const updated = useSkillStore.getState().currentDetail;
      if (updated?.editingVersion) {
        setSelectedVersion(updated.editingVersion);
      }
    } catch {
      loadDetail();
    } finally {
      setActionLoading(false);
    }
  };

  const handleSubmit = async (version: string) => {
    setActionLoading(true);
    try {
      await skillApi.submit({ namespaceId, skillName, version });
      toast.success(t('skill.submitSuccess'));
      loadDetail();
    } catch {
      loadDetail();
    } finally {
      setActionLoading(false);
    }
  };

  const handleDeleteDraft = async () => {
    setActionLoading(true);
    try {
      await skillApi.deleteDraft({ namespaceId, skillName });
      toast.success(t('skill.deleteDraftSuccess'));
      // Clear selection first, then refresh so auto-select picks the first remaining version
      setSelectedVersion('');
      await fetchDetail(namespaceId, skillName);
    } catch {
      loadDetail();
    } finally {
      setActionLoading(false);
    }
  };

  const handlePublish = async (version: string) => {
    setActionLoading(true);
    try {
      await skillApi.publish({
        namespaceId,
        skillName,
        version,
        updateLatestLabel: true,
      });
      toast.success(t('skill.publishSuccess'));
      loadDetail();
    } catch {
      loadDetail();
    } finally {
      setActionLoading(false);
    }
  };

  const handleOnline = async (version: string) => {
    setActionLoading(true);
    try {
      await skillApi.online({ namespaceId, skillName, version });
      toast.success(t('skill.onlineSuccess'));
      loadDetail();
    } catch {
      loadDetail();
    } finally {
      setActionLoading(false);
    }
  };

  const handleOffline = async (version: string) => {
    setActionLoading(true);
    try {
      await skillApi.offline({ namespaceId, skillName, version });
      toast.success(t('skill.offlineSuccess'));
      loadDetail();
    } catch {
      loadDetail();
    } finally {
      setActionLoading(false);
    }
  };

  const handleDownload = async (version: string) => {
    try {
      const blob = await skillApi.downloadVersion({ namespaceId, skillName, version });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${skillName}-${version}.zip`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch {
      // handled by axios interceptor
    }
  };

  const handleSelectVersion = (version: string) => {
    setSelectedVersion(version);
  };

  // ===== Loading skeleton =====
  if (detailLoading && !currentDetail) {
    return (
      <div className="space-y-5">
        <Skeleton className="h-32 w-full rounded-xl" />
        <div className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,1fr)_320px]">
          <div className="space-y-5">
            <Skeleton className="h-48 w-full" />
            <Skeleton className="h-64 w-full" />
          </div>
          <div className="space-y-5">
            <Skeleton className="h-48 w-full" />
          </div>
        </div>
      </div>
    );
  }

  // ===== Error state =====
  if (error && !currentDetail) {
    return (
      <div className="flex flex-col items-center justify-center py-24 space-y-4">
        <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-destructive/10 mb-2">
          <Wand2 className="h-8 w-8 text-destructive/50" />
        </div>
        <p className="text-sm text-destructive">{error}</p>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => navigate('/skill')}>
            {t('skill.backToList')}
          </Button>
          <Button onClick={() => loadDetail()}>
            {t('skill.retry')}
          </Button>
        </div>
      </div>
    );
  }

  if (!currentDetail) return null;

  const detail = currentDetail;
  const versions = sortVersionsDescending(detail.versions || []);
  const latestVersion = detail.labels?.latest;
  const displayVersion = selectedVersion || '-';
  const versionOptions = (() => {
    const seen = new Set<string>();
    const result: SkillVersionSummary[] = [];

    if (selectedVersion) {
      seen.add(selectedVersion);
      const current = versions.find((item) => item.version === selectedVersion);
      if (current) result.push(current);
    }

    for (const item of versions) {
      if (!seen.has(item.version)) {
        seen.add(item.version);
        result.push(item);
      }
    }

    return result;
  })();
  const currentVersionSummary = versionOptions.find((item) => item.version === selectedVersion);
  const currentVersionStatus = currentVersionSummary?.status;
  const currentVersionStatusLabel = currentVersionStatus
    ? t(`skill.versionStatus.${currentVersionStatus}`)
    : '-';
  const onlineVersionCountLabel = t('skill.onlineCount', { count: detail.onlineCnt ?? 0 });
  const labelEntries = Object.entries(detail.labels || {}).filter(([key]) => key !== 'latest');

  // Pipeline info for current version
  const currentPipelineInfo = parsePipelineInfo(currentVersionSummary?.publishPipelineInfo);

  // Parse resources from version document
  const resources = versionDoc?.resource ?? {};
  const resourceEntries = Object.entries(resources);

  return (
    <div className="flex min-h-[calc(100vh-88px)] flex-col gap-5 pb-5">
      {/* ===== Hero Header ===== */}
      <div className="relative rounded-xl border bg-card overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-br from-violet-500/[0.04] via-transparent to-fuchsia-500/[0.03]" />
        <div className="absolute top-0 right-0 w-64 h-64 bg-gradient-to-bl from-violet-500/[0.06] to-transparent rounded-full -translate-y-1/2 translate-x-1/3" />

        <div className="relative px-5 py-4">
          {/* Top bar */}
          <div className="flex items-center justify-between mb-4">
            <Button
              variant="ghost"
              size="sm"
              className="h-7 gap-1.5 text-muted-foreground hover:text-foreground -ml-2"
              onClick={() => navigate('/skill')}
            >
              <ArrowLeft className="h-3.5 w-3.5" />
              {t('skill.backToList')}
            </Button>

            <div className="flex items-center gap-2">
              {selectedVersion && (
                <Select value={selectedVersion} onValueChange={handleSelectVersion}>
                  <SelectTrigger className="w-[140px] h-7 text-xs bg-background/80">
                    <SelectValue placeholder={t('skill.selectVersion')} />
                  </SelectTrigger>
                  <SelectContent>
                    {versionOptions.map((version) => (
                      <SelectItem key={version.version} value={version.version}>
                        <span className="flex items-center gap-2">
                          <span>{version.version}</span>
                          {latestVersion === version.version && (
                            <Badge className="bg-emerald-100 text-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-300 text-[10px] px-1 py-0 border-0">
                              {t('skill.latestVersion')}
                            </Badge>
                          )}
                        </span>
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}

              <Button
                variant="outline"
                size="sm"
                className="h-7 text-xs"
                onClick={() => setVersionSheetOpen(true)}
              >
                <History className="mr-1 h-3 w-3" />
                {t('skill.versionHistory')}
              </Button>

              {selectedVersion && currentVersionStatus !== 'draft' && (
                <Button
                  variant="outline"
                  size="sm"
                  className="h-7 text-xs"
                  onClick={() => handleDownload(selectedVersion)}
                >
                  <Download className="mr-1 h-3 w-3" />
                  {t('skill.download')}
                </Button>
              )}
            </div>
          </div>

          {/* Identity */}
          <div className="flex items-start gap-4">
            <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-violet-500 to-fuchsia-400 shadow-lg shadow-violet-500/20">
              <Wand2 className="h-7 w-7 text-white" />
            </div>

            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2.5 mb-1">
                <h1 className="text-xl font-bold tracking-tight">{detail.name}</h1>
                <Badge
                  className={cn(
                    'text-[10px] px-1.5 py-0 h-4 font-medium border-0',
                    detail.enable
                      ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300'
                      : 'bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400',
                  )}
                >
                  {detail.enable ? t('skill.enabled') : t('skill.disabled')}
                </Badge>
                {/* Enable/disable switch */}
                <Switch
                  checked={detail.enable}
                  disabled={enableToggling}
                  onCheckedChange={handleToggleEnable}
                  aria-label={t('skill.toggleEnable')}
                  className="scale-75"
                />
                {selectedVersion && (
                  <span className="text-xs text-muted-foreground font-mono bg-muted/60 px-1.5 py-0.5 rounded">
                    {selectedVersion}
                  </span>
                )}
              </div>
              {/* Description - editable in draft mode */}
              {isEditingDraft ? (
                <Input
                  value={editDescription}
                  onChange={(e) => setEditDescription(e.target.value)}
                  placeholder={t('skill.descPlaceholder')}
                  className="text-sm max-w-2xl h-8"
                />
              ) : versionDoc?.description ? (
                <p className="text-sm text-muted-foreground leading-relaxed max-w-2xl">
                  {versionDoc.description}
                </p>
              ) : null}

              {/* Meta row */}
              <div className="flex items-center gap-4 mt-2 text-xs text-muted-foreground">
                <span className="inline-flex items-center gap-1">
                  <Globe className="h-3 w-3" />
                  {onlineVersionCountLabel}
                </span>
                {detail.downloadCount > 0 && (
                  <span className="inline-flex items-center gap-1">
                    <Download className="h-3 w-3" />
                    {t('skill.downloadCount', { count: detail.downloadCount })}
                  </span>
                )}
                {detail.updateTime > 0 && (
                  <span className="inline-flex items-center gap-1">
                    <Clock className="h-3 w-3" />
                    {dayjs(detail.updateTime).format('YYYY-MM-DD HH:mm')}
                  </span>
                )}
              </div>

              {/* Version lifecycle action buttons */}
              {selectedVersion && currentVersionStatus && (
                <div className="flex items-center gap-2 mt-3 pt-3 border-t border-border/40">
                  {/* Draft actions */}
                  {currentVersionStatus === 'draft' && (
                    <>
                      {isEditingDraft ? (
                        <>
                          <Button
                            variant="outline"
                            size="sm"
                            className="h-7 text-xs gap-1.5"
                            onClick={handleCancelEdit}
                            disabled={draftSaving}
                          >
                            <X className="h-3 w-3" />
                            {t('skill.cancelEdit')}
                          </Button>
                          <Button
                            size="sm"
                            className="h-7 text-xs gap-1.5"
                            onClick={handleSaveDraft}
                            disabled={draftSaving}
                          >
                            <Save className="h-3 w-3" />
                            {draftSaving ? t('common.loading') : t('skill.saveDraft')}
                          </Button>
                        </>
                      ) : (
                        <>
                          <Button
                            variant="outline"
                            size="sm"
                            className="h-7 text-xs gap-1.5"
                            onClick={handleStartEdit}
                          >
                            <Pencil className="h-3 w-3" />
                            {t('skill.editDraft')}
                          </Button>
                          <Button
                            variant="outline"
                            size="sm"
                            className="h-7 text-xs gap-1.5"
                            onClick={() => setOptimizeDialogOpen(true)}
                          >
                            <Sparkles className="h-3 w-3" />
                            {t('skill.aiOptimize')}
                          </Button>
                          <div className="h-4 w-px bg-border mx-0.5" />
                          <Button
                            size="sm"
                            className="h-7 text-xs gap-1.5"
                            disabled={actionLoading}
                            onClick={() => handleSubmit(selectedVersion)}
                          >
                            <Send className="h-3 w-3" />
                            {t('skill.submit')}
                          </Button>
                          <Button
                            variant="outline"
                            size="sm"
                            className="h-7 text-xs gap-1.5 text-destructive hover:text-destructive hover:bg-destructive/10"
                            disabled={actionLoading}
                            onClick={handleDeleteDraft}
                          >
                            <Trash2 className="h-3 w-3" />
                            {t('skill.deleteDraft')}
                          </Button>
                        </>
                      )}
                    </>
                  )}

                  {/* Reviewing actions */}
                  {currentVersionStatus === 'reviewing' && (
                    <TooltipProvider>
                      <Tooltip>
                        <TooltipTrigger asChild>
                          <span>
                            <Button
                              size="sm"
                              className="h-7 text-xs gap-1.5"
                              disabled={actionLoading || (!!currentPipelineInfo && currentPipelineInfo.status !== 'APPROVED')}
                              onClick={() => handlePublish(selectedVersion)}
                            >
                              <CheckCircle2 className="h-3 w-3" />
                              {t('skill.publish')}
                            </Button>
                          </span>
                        </TooltipTrigger>
                        {currentPipelineInfo && currentPipelineInfo.status !== 'APPROVED' && (
                          <TooltipContent>
                            {t('skill.publishDisabledPipeline')}
                          </TooltipContent>
                        )}
                      </Tooltip>
                    </TooltipProvider>
                  )}

                  {/* Online actions */}
                  {currentVersionStatus === 'online' && (
                    <Button
                      variant="outline"
                      size="sm"
                      className="h-7 text-xs gap-1.5"
                      disabled={actionLoading}
                      onClick={() => handleOffline(selectedVersion)}
                    >
                      <PowerOff className="h-3 w-3" />
                      {t('skill.offline')}
                    </Button>
                  )}

                  {/* Offline actions */}
                  {currentVersionStatus === 'offline' && (
                    <Button
                      size="sm"
                      className="h-7 text-xs gap-1.5"
                      disabled={actionLoading}
                      onClick={() => handleOnline(selectedVersion)}
                    >
                      <Power className="h-3 w-3" />
                      {t('skill.online')}
                    </Button>
                  )}

                  {/* Create new draft (when viewing online/offline and no editing/reviewing version) */}
                  {(currentVersionStatus === 'online' || currentVersionStatus === 'offline') &&
                    !detail.editingVersion && !detail.reviewingVersion && (
                    <Button
                      variant="outline"
                      size="sm"
                      className="h-7 text-xs gap-1.5"
                      disabled={actionLoading}
                      onClick={() => handleCreateDraft(selectedVersion)}
                    >
                      <Plus className="h-3 w-3" />
                      {t('skill.createDraftFrom')}
                    </Button>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* ===== Tabs Content ===== */}
      <Tabs defaultValue="overview" className={cn('flex-1', (detailLoading || actionLoading) && 'opacity-50 pointer-events-none')}>
        <TabsList>
          <TabsTrigger value="overview" className="gap-1.5">
            <FileText className="h-3.5 w-3.5" />
            {t('skill.instruction')}
          </TabsTrigger>
          <TabsTrigger value="resources" className="gap-1.5">
            <Wand2 className="h-3.5 w-3.5" />
            {t('skill.resources')}
            {resourceEntries.length > 0 && (
              <Badge variant="secondary" className="text-[10px] px-1 py-0 h-4 ml-0.5">
                {resourceEntries.length}
              </Badge>
            )}
          </TabsTrigger>
        </TabsList>

        {/* Overview tab: Instruction + Sidebar */}
        <TabsContent value="overview">
          <div className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,1fr)_320px]">
            {/* Left: Instruction card */}
            <Card className="overflow-hidden py-0 gap-0">
              <div className="px-5 py-3.5 border-b bg-muted/30">
                <h2 className="text-sm font-semibold flex items-center gap-2">
                  <FileText className="h-4 w-4 text-muted-foreground" />
                  {t('skill.instruction')}
                </h2>
              </div>
              <CardContent className="p-5">
                {docLoading ? (
                  <div className="space-y-2">
                    <Skeleton className="h-4 w-full" />
                    <Skeleton className="h-4 w-3/4" />
                    <Skeleton className="h-4 w-1/2" />
                  </div>
                ) : isEditingDraft ? (
                  <div className="space-y-2">
                    <p className="text-xs text-muted-foreground">{t('skill.instructionHint')}</p>
                    <div data-color-mode="light" className="dark:hidden">
                      <MDEditor
                        value={editInstruction}
                        onChange={(val) => setEditInstruction(val || '')}
                        height={500}
                        preview="live"
                      />
                    </div>
                    <div data-color-mode="dark" className="hidden dark:block">
                      <MDEditor
                        value={editInstruction}
                        onChange={(val) => setEditInstruction(val || '')}
                        height={500}
                        preview="live"
                      />
                    </div>
                  </div>
                ) : versionDoc?.instruction ? (
                  <div className="prose prose-sm dark:prose-invert max-w-none">
                    <Markdown remarkPlugins={[remarkGfm]}>
                      {versionDoc.instruction}
                    </Markdown>
                  </div>
                ) : (
                  <p className="text-sm text-muted-foreground">{t('skill.noDescription')}</p>
                )}
              </CardContent>
            </Card>

            {/* Right: Sidebar */}
            <div className="space-y-4 lg:w-[320px]">
              {/* Basic info card */}
              <Card className="overflow-hidden py-0 gap-0">
                <div className="px-4 py-3 border-b bg-muted/30">
                  <h2 className="text-sm font-semibold flex items-center gap-2">
                    <Wand2 className="h-4 w-4 text-muted-foreground" />
                    {t('skill.basicInfo')}
                  </h2>
                </div>
                <CardContent className="p-0">
                  <div className="grid grid-cols-1 divide-y divide-border">
                    <InfoCell compact label={t('skill.skillName')} value={detail.name || '-'} icon={<Wand2 className="h-3.5 w-3.5" />} />
                    <InfoCell compact label={t('skill.version')} value={displayVersion} icon={<Hash className="h-3.5 w-3.5" />} />
                    <InfoCell
                      compact
                      label={t('skill.status')}
                      value={<StatusBadge status={currentVersionStatus} label={currentVersionStatusLabel} />}
                      icon={<Tag className="h-3.5 w-3.5" />}
                    />
                    {currentVersionSummary && (
                      <InfoCell compact label={t('skill.author')} value={currentVersionSummary.author || '-'} icon={<Globe className="h-3.5 w-3.5" />} />
                    )}
                    <InfoCell compact label={t('skill.downloads')} value={String(detail.downloadCount ?? 0)} icon={<Download className="h-3.5 w-3.5" />} />
                    {currentVersionSummary && (
                      <InfoCell compact label={t('skill.versionDownloads')} value={String(currentVersionSummary.downloadCount ?? 0)} icon={<Download className="h-3.5 w-3.5" />} />
                    )}
                    <InfoCell compact label={t('skill.updateTime')} value={detail.updateTime > 0 ? dayjs(detail.updateTime).format('YYYY-MM-DD HH:mm') : '-'} icon={<Clock className="h-3.5 w-3.5" />} />
                  </div>
                  {!isEditingDraft && versionDoc?.description && (
                    <div className="border-t px-4 py-2.5">
                      <p className="text-[11px] text-muted-foreground leading-none mb-1">{t('skill.description')}</p>
                      <div className="text-sm text-foreground whitespace-pre-wrap break-words">{versionDoc.description}</div>
                    </div>
                  )}
                </CardContent>
              </Card>

              {/* Pipeline status card (only for reviewing versions) */}
              {currentVersionStatus === 'reviewing' && (
                <Card className="overflow-hidden py-0 gap-0">
                  <div className="px-4 py-3 border-b bg-muted/30">
                    <h2 className="text-sm font-semibold flex items-center gap-2">
                      <GitBranch className="h-4 w-4 text-muted-foreground" />
                      {t('skill.pipelineStatus')}
                    </h2>
                  </div>
                  <CardContent className="p-3.5">
                    <PipelineStatusDisplay pipelineInfo={currentPipelineInfo} />
                  </CardContent>
                </Card>
              )}

              {/* Labels card (editable) */}
              <Card className="overflow-hidden py-0 gap-0">
                <div className="px-4 py-3 border-b bg-muted/30">
                  <h2 className="text-sm font-semibold flex items-center gap-2">
                    <Tag className="h-4 w-4 text-muted-foreground" />
                    {t('common.versionLabels.title')}
                  </h2>
                </div>
                <CardContent className="p-3.5">
                  <VersionLabelEditor
                    labels={Object.fromEntries(labelEntries)}
                    availableVersions={versions.map((v) => v.version)}
                    onSave={handleSaveLabels}
                    isSaving={labelsSaving}
                  />
                </CardContent>
              </Card>
            </div>
          </div>
        </TabsContent>

        {/* Resources tab: IDE-like resource panel */}
        <TabsContent value="resources">
          <SkillResourcePanel
            resources={isEditingDraft ? editResources : resources}
            editable={isEditingDraft}
            onChange={isEditingDraft ? setEditResources : undefined}
          />
        </TabsContent>
      </Tabs>

      <Sheet open={versionSheetOpen} onOpenChange={setVersionSheetOpen}>
        <SheetContent className="flex flex-col p-0 sm:max-w-md">
          <SheetHeader className="px-6 pt-6 pb-4 border-b shrink-0">
            <SheetTitle className="flex items-center gap-2">
              <History className="h-4.5 w-4.5 text-violet-500" />
              {t('skill.versionHistory')}
            </SheetTitle>
            <SheetDescription>
              {t('skill.totalVersions', { count: versions.length })}
            </SheetDescription>
          </SheetHeader>
          <div className="flex-1 overflow-y-auto px-4 py-4">
            <SkillVersionTimeline
              versions={versions}
              currentVersion={selectedVersion}
              hasEditingVersion={!!detail.editingVersion}
              hasReviewingVersion={!!detail.reviewingVersion}
              onSelectVersion={(version) => {
                handleSelectVersion(version);
                setVersionSheetOpen(false);
              }}
              onCreateDraft={handleCreateDraft}
              onDeleteDraft={handleDeleteDraft}
              onSubmit={handleSubmit}
              onPublish={handlePublish}
              onOnline={handleOnline}
              onOffline={handleOffline}
              onDownload={handleDownload}
              showCreateDraftButton
              allLabels={detail.labels}
              onSaveLabels={handleSaveLabels}
            />
          </div>
        </SheetContent>
      </Sheet>

      {/* AI Optimize Dialog */}
      {versionDoc && (
        <SkillOptimizeDialog
          open={optimizeDialogOpen}
          onOpenChange={setOptimizeDialogOpen}
          skill={versionDoc}
          namespaceId={namespaceId}
          onApply={handleOptimizationApply}
        />
      )}
    </div>
  );
}

function InfoCell({
  label,
  value,
  icon,
  compact = false,
}: {
  label: string;
  value: React.ReactNode;
  icon?: React.ReactNode;
  compact?: boolean;
}) {
  return (
    <div className={cn('flex items-center gap-3 px-5 py-3', compact && 'gap-2.5 px-4 py-2.5')}>
      {icon && (
        <span className="text-muted-foreground/60 shrink-0">{icon}</span>
      )}
      <div className="min-w-0 flex-1">
        <p className="text-[11px] text-muted-foreground leading-none mb-1">{label}</p>
        <div className={cn('text-sm font-medium break-all', compact && 'text-[13px]')}>{value || '-'}</div>
      </div>
    </div>
  );
}

function StatusBadge({
  status,
  label,
}: {
  status?: string;
  label: string;
}) {
  const statusStyles: Record<string, string> = {
    draft: 'bg-amber-50 text-amber-700 dark:bg-amber-950/40 dark:text-amber-300',
    reviewing: 'bg-blue-50 text-blue-700 dark:bg-blue-950/40 dark:text-blue-300',
    online: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300',
    offline: 'bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400',
  };

  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium',
        status ? statusStyles[status] : statusStyles.offline,
      )}
    >
      {label}
    </span>
  );
}
