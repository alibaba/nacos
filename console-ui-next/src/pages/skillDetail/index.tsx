import { useEffect, useCallback, useState, useMemo, useRef } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkFrontmatter from 'remark-frontmatter';
import {
  ArrowLeft,
  History,
  Wand2,
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
  AlertTriangle,
  AlertCircle,
  Lock,
  Loader2,
  ShieldAlert,
  MessageSquare,
} from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Textarea } from '@/components/ui/textarea';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import MDEditor from '@uiw/react-md-editor';
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
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { useSkillStore } from '@/stores/skill-store';
import { useNamespaceStore } from '@/stores/namespace-store';
import { useAuthStore } from '@/stores/auth-store';
import { useServerStore } from '@/stores/server-store';
import { skillApi } from '@/api/skill';
import type { SkillDocument, SkillResource, SkillVersionSummary } from '@/types/skill';
import { parseBizTags, parsePipelineInfo } from '@/types/skill';
import { cn } from '@/lib/utils';
import {
  hasNonFrontmatterMarkdownBody,
  parseFrontmatter,
  updateFrontmatterField,
} from '@/lib/markdown-utils';
import dayjs from 'dayjs';

import { SkillVersionTimeline } from '../skillManagement/components/SkillVersionTimeline';
import { PipelineStatusDisplay } from '../skillManagement/components/PipelineStatusDisplay';
import { SkillOptimizeDialog } from '@/components/ai/skill/SkillOptimizeDialog';
import { LabelBindDialog } from '@/components/ai/LabelBindDialog';
import { BizTagEditDialog } from '@/components/ai/BizTagEditDialog';
import { DetailTagChip } from '@/components/ai/DetailTagChip';
import { CliCommandCard } from '@/components/ai/CliCommandCard';
import { sortVersionsDescending } from '../skillManagement/components/version-utils';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { SkillResourcePanel } from './SkillResourcePanel';

export default function SkillDetailPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { name: routeName } = useParams<{ name: string }>();
  const skillName = routeName ? decodeURIComponent(routeName) : '';
  const { currentNamespace } = useNamespaceStore();
  const namespaceId =
    searchParams.get('namespaceId') ||
    searchParams.get('namespace') ||
    currentNamespace ||
    'public';
  const { globalAdmin } = useAuthStore();
  const copilotEnabled = useServerStore((s) => s.copilotEnabled);

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

  // Ref to prevent circular updates between description textarea and md editor frontmatter
  const syncSourceRef = useRef<'description' | 'instruction' | null>(null);

  // Handler: MD editor content changed -> extract description from frontmatter & protect name
  const handleInstructionChange = useCallback((val: string | undefined) => {
    let newVal = val || '';
    const fm = parseFrontmatter(newVal);
    // Protect name: revert to original skillName if user removed or changed it
    if (fm.name === undefined || fm.name !== skillName) {
      newVal = updateFrontmatterField(newVal, 'name', skillName);
    }
    setEditInstruction(newVal);
    if (syncSourceRef.current === 'description') return;
    syncSourceRef.current = 'instruction';
    if (fm.description !== undefined) {
      setEditDescription(fm.description);
    }
    // Reset after microtask to allow the other side to update freely
    queueMicrotask(() => { syncSourceRef.current = null; });
  }, [skillName]);

  // Handler: description textarea changed -> update frontmatter in md editor
  const handleDescriptionChange = useCallback((newDesc: string) => {
    setEditDescription(newDesc);
    if (syncSourceRef.current === 'instruction') return;
    syncSourceRef.current = 'description';
    setEditInstruction((prev) => updateFrontmatterField(prev, 'description', newDesc));
    queueMicrotask(() => { syncSourceRef.current = null; });
  }, []);

  // Draft editing state
  const [isEditingDraft, setIsEditingDraft] = useState(false);
  const [isCreatingNewDraft, setIsCreatingNewDraft] = useState(false); // true when creating brand-new draft (no version to fork)
  const [editInstruction, setEditInstruction] = useState('');
  const [editDescription, setEditDescription] = useState('');
  const [draftSaving, setDraftSaving] = useState(false);

  // Resource editing state
  const [editResources, setEditResources] = useState<Record<string, SkillResource>>({});

  // Enable/disable toggle state
  const [enableToggling, setEnableToggling] = useState(false);
  const [scopeToggling, setScopeToggling] = useState(false);
  const [bizTags, setBizTags] = useState<string[]>([]);
  const [bizTagDialogOpen, setBizTagDialogOpen] = useState(false);

  // AI Optimize dialog state
  const [optimizeDialogOpen, setOptimizeDialogOpen] = useState(false);

  // Label bind dialog state
  const [labelDialogOpen, setLabelDialogOpen] = useState(false);
  const [createDraftDialogOpen, setCreateDraftDialogOpen] = useState(false);
  const [createDraftFromVersion, setCreateDraftFromVersion] = useState('');
  const [createDraftTargetVersion, setCreateDraftTargetVersion] = useState('');
  const [draftCommitMsg, setDraftCommitMsg] = useState('');
  const [createDraftCommitMsg, setCreateDraftCommitMsg] = useState('');
  const [forcePublishConfirmOpen, setForcePublishConfirmOpen] = useState(false);

  const loadDetail = useCallback(() => {
    if (skillName) {
      return fetchDetail(namespaceId, skillName);
    }
  }, [fetchDetail, namespaceId, skillName]);

  useEffect(() => {
    setVersionDoc(null);
    setSelectedVersion('');
    setIsEditingDraft(false);
    setIsCreatingNewDraft(false);
    loadDetail();
    return () => {
      clearDetail();
      clearError();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [skillName, namespaceId]);

  // Auto-select version when detail loads: prefer "latest" label, then first by sort order
  useEffect(() => {
    if (!currentDetail || detailLoading) return;
    const versions = sortVersionsDescending(currentDetail.versions || []);
    const latestLabelled = currentDetail.labels?.latest;
    const preferredVersion = (latestLabelled && versions.some(v => v.version === latestLabelled))
      ? latestLabelled
      : versions[0]?.version;
    // If no version selected, auto-select preferred
    if (preferredVersion && !selectedVersion) {
      setSelectedVersion(preferredVersion);
      return;
    }
    // If selected version no longer exists (e.g. deleted), switch to preferred or clear
    if (selectedVersion && !versions.some((v) => v.version === selectedVersion)) {
      setSelectedVersion(preferredVersion || '');
    }
  }, [currentDetail, detailLoading, selectedVersion]);

  useEffect(() => {
    setBizTags(parseBizTags(currentDetail?.bizTags));
  }, [currentDetail?.bizTags]);

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
    setEditInstruction(versionDoc?.skillMd ?? '');
    setEditDescription(versionDoc?.description ?? '');
    setEditResources({ ...(versionDoc?.resource ?? {}) });
    const versionRow = currentDetail?.versions?.find((v) => v.version === selectedVersion);
    setDraftCommitMsg(versionRow?.commitMsg?.trim() ? versionRow.commitMsg : '');
    setIsEditingDraft(true);
  };

  const handleCancelEdit = () => {
    setIsEditingDraft(false);
    setIsCreatingNewDraft(false);
    setEditResources({});
    setDraftCommitMsg('');
  };

  const handleSaveDraft = async () => {
    if (!editDescription.trim()) {
      toast.error(t('skill.descriptionRequired'));
      return;
    }
    if (!hasNonFrontmatterMarkdownBody(editInstruction)) {
      toast.error(t('skill.skillMdRequired'));
      return;
    }
    setDraftSaving(true);
    try {
      const skillCard = JSON.stringify({
        name: skillName,
        description: editDescription.trim(),
        skillMd: editInstruction,
        resource: editResources,
      });

      const commitOpt = draftCommitMsg.trim() || undefined;
      if (isCreatingNewDraft) {
        // Brand-new draft: single createDraft call with skillCard
        await skillApi.createDraft({ namespaceId, skillName, skillCard, commitMsg: commitOpt });
        toast.success(t('skill.createDraftSuccess'));
        setIsCreatingNewDraft(false);
        setIsEditingDraft(false);
        setDraftCommitMsg('');
        await fetchDetail(namespaceId, skillName);
        const updated = useSkillStore.getState().currentDetail;
        if (updated?.editingVersion) {
          setSelectedVersion(updated.editingVersion);
        }
      } else {
        // Editing existing draft: updateDraft
        await skillApi.updateDraft({ namespaceId, skillCard, commitMsg: commitOpt });
        toast.success(t('skill.draftSaveSuccess'));
        setIsEditingDraft(false);
        setDraftCommitMsg('');
        await loadDetail();
        const response = await skillApi.getVersion({ namespaceId, skillName, version: selectedVersion });
        setVersionDoc(response.data);
      }
    } catch {
      // handled by interceptor
    } finally {
      setDraftSaving(false);
    }
  };

  // ===== AI Optimize handler =====

  const handleOptimizationApply = async (optimizedSkill: SkillDocument) => {
    if (!hasNonFrontmatterMarkdownBody(optimizedSkill.skillMd || '')) {
      toast.error(t('skill.skillMdRequired'));
      return;
    }
    try {
      const skillCard = JSON.stringify({
        name: skillName,
        description: optimizedSkill.description,
        skillMd: optimizedSkill.skillMd,
        resource: optimizedSkill.resource,
      });
      await skillApi.updateDraft({ namespaceId, skillCard });
      toast.success(t('skill.optimizeSuccess'));
      await loadDetail();
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
      toast.success(t(currentDetail.enable ? 'skill.disableSuccess' : 'skill.enableSuccess'));
      await loadDetail();
    } catch {
      // handled by interceptor
    } finally {
      setEnableToggling(false);
    }
  };

  const handleToggleScope = async () => {
    if (!currentDetail) return;
    setScopeToggling(true);
    try {
      const newScope = currentDetail.scope === 'PUBLIC' ? 'PRIVATE' : 'PUBLIC';
      await skillApi.updateScope({ namespaceId, skillName, scope: newScope });
      toast.success(t('skill.scopeUpdateSuccess'));
      await loadDetail();
    } catch {
      // handled by interceptor
    } finally {
      setScopeToggling(false);
    }
  };

  // ===== Labels handler =====

  const handleSaveLabels = async (labels: Record<string, string>) => {
    await skillApi.updateLabels({
      namespaceId,
      skillName,
      labels: JSON.stringify(labels),
    });
    toast.success(t('common.versionLabels.updateSuccess'));
    await loadDetail();
  };

  const handleSaveBizTags = async (nextBizTags: string[]) => {
    await skillApi.updateBizTags({
      namespaceId,
      skillName,
      bizTags: JSON.stringify(nextBizTags),
    });
    toast.success(t('skill.bizTagsUpdateSuccess'));
    await loadDetail();
  };

  // ===== Version lifecycle handlers =====

  const validateDraftTargetVersion = (targetVersion: string, basedOnVersion: string): string | null => {
    if (!targetVersion) {
      return t('skill.newVersionRequired');
    }
    const isTargetSemver = isSemverVersion(targetVersion);
    const isTargetLegacy = isLegacyVersion(targetVersion);
    if (!isTargetSemver && !isTargetLegacy) {
      return t('skill.versionInvalid');
    }
    if (basedOnVersion) {
      const isBaseSemver = isSemverVersion(basedOnVersion);
      const isBaseLegacy = isLegacyVersion(basedOnVersion);
      if (isTargetSemver && isBaseSemver && compareSemverVersion(targetVersion, basedOnVersion) <= 0) {
        return t('skill.versionMustGreater', { current: basedOnVersion });
      }
      if (isTargetLegacy && isBaseLegacy && compareLegacyVersion(targetVersion, basedOnVersion) <= 0) {
        return t('skill.versionMustGreater', { current: basedOnVersion });
      }
    }
    return null;
  };

  const handleCreateDraft = async (basedOnVersion?: string) => {
    if (!basedOnVersion) {
      // No version to fork from — enter edit mode for a brand-new draft
      setEditDescription('');
      setEditInstruction('');
      setEditResources({});
      setDraftCommitMsg('');
      setIsCreatingNewDraft(true);
      setIsEditingDraft(true);
      return;
    }
    const suggestedVersion = suggestNextVersionFromBase(basedOnVersion);
    setCreateDraftFromVersion(basedOnVersion);
    setCreateDraftTargetVersion(suggestedVersion);
    setCreateDraftCommitMsg('');
    setCreateDraftDialogOpen(true);
  };
  
  const handleConfirmCreateDraft = async () => {
    const targetVersion = createDraftTargetVersion.trim();
    const errorMsg = validateDraftTargetVersion(targetVersion, createDraftFromVersion);
    if (errorMsg) {
      toast.error(errorMsg);
      return;
    }
    setActionLoading(true);
    try {
      await skillApi.createDraft({
        namespaceId,
        skillName,
        basedOnVersion: createDraftFromVersion,
        targetVersion,
        commitMsg: createDraftCommitMsg.trim() || undefined,
      });
      toast.success(t('skill.createDraftSuccess'));
      setCreateDraftDialogOpen(false);
      setCreateDraftCommitMsg('');
      await fetchDetail(namespaceId, skillName);
      const updated = useSkillStore.getState().currentDetail;
      if (updated?.editingVersion) {
        setSelectedVersion(updated.editingVersion);
      }
    } catch {
      await loadDetail();
    } finally {
      setActionLoading(false);
    }
  };

  const handleSubmit = async (version: string) => {
    // Validate required fields before submit
    if (versionDoc && (!versionDoc.description?.trim()
      || !hasNonFrontmatterMarkdownBody(versionDoc.skillMd || ''))) {
      toast.error(t('skill.submitRequiresFields'));
      return;
    }
    setActionLoading(true);
    try {
      await skillApi.submit({ namespaceId, skillName, version });
      toast.success(t('skill.submitSuccess'));
      await loadDetail();
    } catch {
      await loadDetail();
    } finally {
      setActionLoading(false);
    }
  };

  const handleDeleteDraft = async () => {
    setActionLoading(true);
    try {
      await skillApi.deleteDraft({ namespaceId, skillName });
      toast.success(t('skill.deleteDraftSuccess'));
      // Refresh detail FIRST so currentDetail is up-to-date,
      // then pick the first remaining version (or '' if none).
      // This avoids the auto-select effect re-selecting the deleted version from stale data.
      await fetchDetail(namespaceId, skillName);
      const updated = useSkillStore.getState().currentDetail;
      const remaining = sortVersionsDescending(updated?.versions || []);
      setSelectedVersion(remaining[0]?.version || '');
    } catch {
      await loadDetail();
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
      });
      toast.success(t('skill.publishSuccess'));
      await loadDetail();
    } catch {
      await loadDetail();
    } finally {
      setActionLoading(false);
    }
  };

  const handleForcePublish = async (version: string) => {
    setActionLoading(true);
    try {
      await skillApi.forcePublish({
        namespaceId,
        skillName,
        version,
      });
      toast.success(t('skill.forcePublishSuccess'));
      await loadDetail();
    } catch {
      await loadDetail();
    } finally {
      setActionLoading(false);
    }
  };

  const handleRedraft = async (version: string) => {
    setActionLoading(true);
    try {
      await skillApi.redraft({ namespaceId, skillName, version });
      toast.success(t('skill.redraftSuccess'));
      await loadDetail();
      const response = await skillApi.getVersion({ namespaceId, skillName, version });
      setVersionDoc(response.data);
      const doc = response.data;
      setEditInstruction(doc?.skillMd ?? '');
      setEditDescription(doc?.description ?? '');
      setEditResources({ ...(doc?.resource ?? {}) });
      setDraftCommitMsg('');
      setIsEditingDraft(true);
    } catch {
      await loadDetail();
    } finally {
      setActionLoading(false);
    }
  };

  const handleOnline = async (version: string) => {
    setActionLoading(true);
    try {
      await skillApi.online({ namespaceId, skillName, version });
      toast.success(t('skill.onlineSuccess'));
      await loadDetail();
    } catch {
      await loadDetail();
    } finally {
      setActionLoading(false);
    }
  };

  const handleOffline = async (version: string) => {
    setActionLoading(true);
    try {
      await skillApi.offline({ namespaceId, skillName, version });
      toast.success(t('skill.offlineSuccess'));
      await loadDetail();
    } catch {
      await loadDetail();
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

  // Build CLI commands for current skill (must be before early returns to keep hooks order stable)
  const cliCommands = useMemo(() => {
    const versionFlag = selectedVersion ? ` --version ${selectedVersion}` : '';
    return [{
      label: t('common.cliUsage.cliInstall'),
      command: `npx @nacos-group/cli skill-get ${skillName}${versionFlag}`,
    }];
  }, [skillName, selectedVersion, t]);

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
  // Labels bound to the currently selected version
  const currentVersionLabels = Object.entries(detail.labels || {}).filter(
    ([, val]) => val === selectedVersion,
  );

  // Pipeline info for current version
  const currentPipelineInfo = parsePipelineInfo(currentVersionSummary?.publishPipelineInfo);

  // Parse resources from version document
  const resources = versionDoc?.resource ?? {};
  const resourceEntries = Object.entries(resources);

  return (
    <div className="space-y-5 pb-5">
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
                <Select value={selectedVersion} onValueChange={handleSelectVersion} disabled={isEditingDraft}>
                  <SelectTrigger className="w-[140px] h-7 text-xs bg-background/80">
                    <SelectValue placeholder={t('skill.selectVersion')} />
                  </SelectTrigger>
                  <SelectContent>
                    {versionOptions.map((version) => {
                      const vPipeline = parsePipelineInfo(version.publishPipelineInfo);
                      const isVersionPendingPublish = (version.status === 'reviewed' && vPipeline?.status !== 'REJECTED') || (version.status === 'reviewing' && vPipeline?.status === 'APPROVED');
                      const isVersionRejected = version.status === 'reviewed' && vPipeline?.status === 'REJECTED';
                      return (
                      <SelectItem key={version.version} value={version.version}>
                        <span className="flex items-center gap-2">
                          <span>{version.version}</span>
                          {latestVersion === version.version && (
                            <Badge className="bg-emerald-100 text-emerald-700 dark:bg-emerald-950/50 dark:text-emerald-300 text-[10px] px-1 py-0 border-0">
                              {t('skill.latestVersion')}
                            </Badge>
                          )}
                          {version.status === 'draft' && (
                            <Badge className="bg-amber-100 text-amber-700 dark:bg-amber-950/50 dark:text-amber-300 text-[10px] px-1 py-0 border-0">
                              {t('skill.versionStatus.draft')}
                            </Badge>
                          )}
                          {isVersionRejected && (
                            <Badge className="bg-red-100 text-red-700 dark:bg-red-950/50 dark:text-red-300 text-[10px] px-1 py-0 border-0">
                              {t('skill.versionStatus.rejected')}
                            </Badge>
                          )}
                          {!isVersionRejected && (version.status === 'reviewing' || version.status === 'reviewed') && (
                            <Badge className={isVersionPendingPublish
                              ? 'bg-teal-100 text-teal-700 dark:bg-teal-950/50 dark:text-teal-300 text-[10px] px-1 py-0 border-0'
                              : 'bg-blue-100 text-blue-700 dark:bg-blue-950/50 dark:text-blue-300 text-[10px] px-1 py-0 border-0'
                            }>
                              {t(isVersionPendingPublish ? 'skill.versionStatus.pendingPublish' : 'skill.versionStatus.reviewing')}
                            </Badge>
                          )}
                        </span>
                      </SelectItem>
                      );
                    })}
                  </SelectContent>
                </Select>
              )}

              <Button
                variant="outline"
                size="sm"
                className="h-7 text-xs"
                onClick={() => setVersionSheetOpen(true)}
                disabled={isEditingDraft}
              >
                <History className="mr-1 h-3 w-3" />
                {t('skill.versionHistory')}
              </Button>
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
                {selectedVersion && (
                  <span className="text-xs text-muted-foreground font-mono bg-muted/60 px-1.5 py-0.5 rounded">
                    {selectedVersion}
                  </span>
                )}
              </div>
              {/* Enable & Scope toggle switches */}
              <div className="flex items-center gap-4 mt-1.5 mb-1">
                <label className="inline-flex items-center gap-2 cursor-pointer select-none">
                  <Switch
                    checked={detail.enable}
                    disabled={enableToggling}
                    onCheckedChange={handleToggleEnable}
                    className={cn(
                      detail.enable
                        ? 'data-[state=checked]:bg-emerald-500'
                        : '',
                    )}
                  />
                  <span className={cn(
                    'text-xs font-medium',
                    detail.enable ? 'text-emerald-700 dark:text-emerald-300' : 'text-muted-foreground',
                  )}>
                    {detail.enable ? t('skill.enabled') : t('skill.disabled')}
                  </span>
                </label>
                <div className="h-4 w-px bg-border" />
                <label className="inline-flex items-center gap-2 cursor-pointer select-none">
                  <Switch
                    checked={detail.scope === 'PUBLIC'}
                    disabled={scopeToggling}
                    onCheckedChange={handleToggleScope}
                  />
                  <span className="inline-flex items-center gap-1 text-xs font-medium text-muted-foreground">
                    {detail.scope === 'PUBLIC' ? <Globe className="h-3 w-3" /> : <Lock className="h-3 w-3" />}
                    {detail.scope === 'PUBLIC' ? t('skill.scopePublic') : t('skill.scopePrivate')}
                  </span>
                </label>
              </div>
              {/* Description - editable in draft mode */}
              {isEditingDraft ? (
                <Textarea
                  value={editDescription}
                  onChange={(e) => handleDescriptionChange(e.target.value)}
                  placeholder={t('skill.descPlaceholder')}
                  className="text-sm max-w-2xl min-h-8 resize-none"
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
                {detail.from && (
                  <span className="inline-flex items-center gap-1">
                    <Tag className="h-3 w-3" />
                    {t('common.from')}: {detail.from}
                  </span>
                )}
              </div>

              {/* Version lifecycle action buttons */}
              {selectedVersion && currentVersionStatus && (
                <div className="mt-3 pt-3 border-t border-border/40">
                  {!detail.enable && (
                    <p className="flex items-center gap-1 text-[11px] text-amber-600 dark:text-amber-400 mb-2">
                      <AlertTriangle className="h-3 w-3 shrink-0" />
                      {t('skill.skillDisabledWarning')}
                    </p>
                  )}
                  <div className="flex items-center gap-2">
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
                          {copilotEnabled && (
                            <Button
                              variant="outline"
                              size="sm"
                              className="h-7 text-xs gap-1.5"
                              onClick={() => setOptimizeDialogOpen(true)}
                            >
                              <Sparkles className="h-3 w-3" />
                              {t('skill.aiOptimize')}
                            </Button>
                          )}
                          <div className="h-4 w-px bg-border mx-0.5" />
                          <Button
                            size="sm"
                            className="h-7 text-xs gap-1.5"
                            disabled={actionLoading}
                            onClick={() => handleSubmit(selectedVersion)}
                          >
                            <Send className="h-3 w-3" />
                            {currentPipelineInfo && currentPipelineInfo.status === 'REJECTED'
                              ? t('skill.resubmit')
                              : t('skill.submit')}
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
                          {currentPipelineInfo && currentPipelineInfo.status === 'REJECTED' && (
                            <PipelineStatusDisplay pipelineInfo={currentPipelineInfo} compact />
                          )}
                          {/* Admin-only force-publish when pipeline rejected */}
                          {globalAdmin && currentPipelineInfo && currentPipelineInfo.status === 'REJECTED' && !currentPipelineInfo.historical && (
                            <Button
                              variant="outline"
                              size="sm"
                              className="h-7 text-xs gap-1.5 text-destructive hover:text-destructive hover:bg-destructive/10 border-destructive/40"
                              disabled={actionLoading}
                              onClick={() => setForcePublishConfirmOpen(true)}
                            >
                              <ShieldAlert className="h-3 w-3" />
                              {t('skill.forcePublish')}
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
                          ? t('skill.pipelineInProgress')
                          : t('skill.publish')}
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
                            {t('skill.redraft')}
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
                      {currentPipelineInfo && currentPipelineInfo.status === 'APPROVED' && (
                        <PipelineStatusDisplay pipelineInfo={currentPipelineInfo} compact />
                      )}
                      {/* Admin-only force-publish when pipeline rejected during reviewing */}
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
                            {t('skill.forcePublish')}
                          </Button>
                        </>
                      )}
                    </>
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

                  {/* Create new draft (when viewing online/offline version) */}
                  {(currentVersionStatus === 'online' || currentVersionStatus === 'offline') && (() => {
                    const hasDraft = !!(detail.editingVersion || detail.reviewingVersion);
                    const btn = (
                      <Button
                        variant="outline"
                        size="sm"
                        className="h-7 text-xs gap-1.5"
                        disabled={actionLoading || hasDraft}
                        onClick={() => handleCreateDraft(selectedVersion)}
                      >
                        <Plus className="h-3 w-3" />
                        {t('skill.createDraftFrom')}
                      </Button>
                    );
                    return hasDraft ? (
                      <Tooltip>
                        <TooltipTrigger asChild>
                          <span>{btn}</span>
                        </TooltipTrigger>
                        <TooltipContent className="bg-amber-50 border border-amber-200 text-amber-800 dark:bg-amber-950 dark:border-amber-800 dark:text-amber-200">
                          <span className="flex items-center gap-1.5">
                            <AlertCircle className="h-3 w-3 shrink-0" />
                            {t('skill.draftExistsTip')}
                          </span>
                        </TooltipContent>
                      </Tooltip>
                    ) : btn;
                  })()}
                  </div>
                </div>
              )}

              {/* Empty state: no versions, show create draft button or editing actions */}
              {!selectedVersion && !detail.editingVersion && !detail.reviewingVersion && versions.length === 0 && (
                <div className="mt-3 pt-3 border-t border-border/40">
                  <div className="flex items-center gap-2">
                    {isCreatingNewDraft ? (
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
                          disabled={draftSaving}
                          onClick={handleSaveDraft}
                        >
                          {draftSaving ? <Loader2 className="h-3 w-3 animate-spin" /> : <Save className="h-3 w-3" />}
                          {t('skill.createDraft')}
                        </Button>
                      </>
                    ) : (
                      <Button
                        size="sm"
                        className="h-7 text-xs gap-1.5"
                        disabled={actionLoading}
                        onClick={() => handleCreateDraft()}
                      >
                        <Plus className="h-3 w-3" />
                        {t('skill.createDraft')}
                      </Button>
                    )}
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* ===== Tabs Content ===== */}
      <Tabs defaultValue="overview" className={cn('flex flex-col', (detailLoading || actionLoading) && 'opacity-50 pointer-events-none')}>
        <TabsList className="w-fit">
          <TabsTrigger value="overview" className="gap-1.5">
            <FileText className="h-3.5 w-3.5" />
            {t('skill.skillMd')}
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
          <div
            className={cn(
              'grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,1fr)_320px]',
              isEditingDraft && 'max-lg:[&>div:first-child]:order-2 max-lg:[&>div:last-child]:order-1',
            )}
          >
            {/* Left: Instruction card */}
            <Card className="overflow-hidden py-0 gap-0 min-h-[580px]">
              <div className="px-5 py-3.5 border-b bg-muted/30">
                <h2 className="text-sm font-semibold flex items-center gap-2">
                  <FileText className="h-4 w-4 text-muted-foreground" />
                  {t('skill.skillMd')}
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
                    <p className="text-xs text-muted-foreground">{t('skill.skillMdHint')}</p>
                    <p className="text-[11px] text-muted-foreground rounded-md border bg-muted/20 px-3 py-2">
                      {t('skill.commitMsgEditHint')}
                    </p>
                    <div data-color-mode="light" className="dark:hidden">
                      <MDEditor
                        value={editInstruction}
                        onChange={handleInstructionChange}
                        height={500}
                        preview="live"
                        previewOptions={{ remarkPlugins: [remarkGfm, remarkFrontmatter] }}
                      />
                    </div>
                    <div data-color-mode="dark" className="hidden dark:block">
                      <MDEditor
                        value={editInstruction}
                        onChange={handleInstructionChange}
                        height={500}
                        preview="live"
                        previewOptions={{ remarkPlugins: [remarkGfm, remarkFrontmatter] }}
                      />
                    </div>
                  </div>
                ) : versionDoc?.skillMd ? (
                  <div className="app-markdown prose prose-sm dark:prose-invert max-w-none">
                    <Markdown remarkPlugins={[remarkGfm, remarkFrontmatter]}>
                      {versionDoc.skillMd}
                    </Markdown>
                  </div>
                ) : (
                  <p className="text-sm text-muted-foreground">{t('skill.noDescription')}</p>
                )}
              </CardContent>
            </Card>

            {/* Right: Sidebar */}
            <div className="space-y-4 lg:w-[320px]">
              <CliCommandCard
                commands={currentVersionStatus !== 'draft' ? cliCommands : []}
                onDownload={selectedVersion ? () => handleDownload(selectedVersion) : undefined}
                downloadFileName={selectedVersion ? `${skillName}-${selectedVersion}.zip` : undefined}
              />

              {/* Basic info card */}
              <Card className="overflow-hidden py-0 gap-0">
                <div className="px-4 py-3 border-b bg-muted/30">
                  <h2 className="text-sm font-semibold flex items-center gap-2">
                    <Wand2 className="h-4 w-4 text-muted-foreground" />
                    {t('skill.basicInfo')}
                  </h2>
                </div>
                <CardContent className="p-0">
                  <div className="grid grid-cols-2 [&>*:nth-child(n+3)]:border-t [&>*:nth-child(even)]:border-l border-border">
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
                    {(currentVersionSummary || isEditingDraft) && (
                      <InfoCell
                        compact
                        colSpan={2}
                        label={t('skill.commitMsg')}
                        value={
                          isEditingDraft ? (
                            <Textarea
                              value={draftCommitMsg}
                              onChange={(e) => setDraftCommitMsg(e.target.value)}
                              placeholder={t('skill.commitMsgPlaceholder')}
                              className="mt-0.5 min-h-[64px] max-w-full resize-y text-xs font-normal"
                              disabled={draftSaving}
                            />
                          ) : (
                            <span className="text-xs font-normal font-sans text-muted-foreground whitespace-pre-wrap">
                              {currentVersionSummary?.commitMsg?.trim()
                                ? currentVersionSummary.commitMsg
                                : '-'}
                            </span>
                          )
                        }
                        icon={<MessageSquare className="h-3.5 w-3.5" />}
                      />
                    )}
                  </div>
                  {isEditingDraft && (
                    <p className="border-t border-border px-4 py-2 text-[11px] text-muted-foreground leading-relaxed">
                      {t('skill.commitMsgHint')}
                    </p>
                  )}
                </CardContent>
              </Card>

              {/* Pipeline status card */}
              {currentPipelineInfo && (
                <Card className="overflow-hidden py-0 gap-0">
                  <div className="px-4 py-3 border-b bg-muted/30">
                    <h2 className="text-sm font-semibold flex items-center gap-2">
                      <GitBranch className="h-4 w-4 text-muted-foreground" />
                      {t('skill.pipelineStatus')}
                    </h2>
                  </div>
                  <CardContent className="p-3.5">
                    <PipelineStatusDisplay pipelineInfo={currentPipelineInfo} onRefresh={() => loadDetail()} />
                  </CardContent>
                </Card>
              )}

              <Card className="overflow-hidden py-0 gap-0">
                <div className="px-4 py-3 border-b bg-muted/30 flex items-center justify-between">
                  <h2 className="text-sm font-semibold flex items-center gap-2">
                    <Tag className="h-4 w-4 text-muted-foreground" />
                    {t('common.bizTags')}
                  </h2>
                  <Button
                    variant="ghost"
                    size="icon"
                    className="h-6 w-6"
                    onClick={() => setBizTagDialogOpen(true)}
                  >
                    <Pencil className="h-3 w-3" />
                  </Button>
                </div>
                <CardContent className="p-3.5">
                  {bizTags.length > 0 ? (
                    <div className="flex flex-wrap gap-1.5">
                      {bizTags.map((tag) => (
                        <DetailTagChip key={tag} label={tag} />
                      ))}
                    </div>
                  ) : (
                    <p className="text-xs text-muted-foreground">{t('skill.noBizTags')}</p>
                  )}
                </CardContent>
              </Card>

              {/* Labels card (read-only for current version) */}
              <Card className="overflow-hidden py-0 gap-0">
                <div className="px-4 py-3 border-b bg-muted/30 flex items-center justify-between">
                  <h2 className="text-sm font-semibold flex items-center gap-2">
                    <Tag className="h-4 w-4 text-muted-foreground" />
                    {t('common.versionLabels.title')}
                  </h2>
                  {selectedVersion && currentVersionStatus !== 'draft' && currentVersionStatus !== 'reviewing' && (
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-6 w-6"
                      onClick={() => setLabelDialogOpen(true)}
                    >
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
                    <p className="text-xs text-muted-foreground">
                      {t('common.versionLabels.noLabels')}
                    </p>
                  )}
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

      <BizTagEditDialog
        open={bizTagDialogOpen}
        onOpenChange={setBizTagDialogOpen}
        tags={bizTags}
        placeholder={t('skill.bizTagPlaceholder')}
        emptyText={t('skill.noBizTags')}
        onSave={handleSaveBizTags}
      />

      {selectedVersion && (
        <LabelBindDialog
          open={labelDialogOpen}
          onOpenChange={setLabelDialogOpen}
          version={selectedVersion}
          allLabels={detail.labels ?? {}}
          onSave={handleSaveLabels}
        />
      )}
      
      <Dialog
        open={createDraftDialogOpen}
        onOpenChange={(open) => {
          setCreateDraftDialogOpen(open);
          if (!open) {
            setCreateDraftCommitMsg('');
          }
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('skill.newVersionTitle')}</DialogTitle>
            <DialogDescription>
              {t('skill.newVersionDesc', { current: createDraftFromVersion })}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="create-draft-target-version">{t('skill.newVersion')}</Label>
            <Input
              id="create-draft-target-version"
              value={createDraftTargetVersion}
              placeholder={t('skill.newVersionPlaceholder')}
              onChange={(e) => setCreateDraftTargetVersion(e.target.value)}
              disabled={actionLoading}
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="create-draft-commit-msg" className="text-xs text-muted-foreground">
              {t('skill.commitMsg')}
            </Label>
            <Textarea
              id="create-draft-commit-msg"
              value={createDraftCommitMsg}
              onChange={(e) => setCreateDraftCommitMsg(e.target.value)}
              placeholder={t('skill.commitMsgPlaceholder')}
              className="text-sm min-h-[72px] resize-y"
              disabled={actionLoading}
            />
            <p className="text-[11px] text-muted-foreground leading-relaxed">
              {t('skill.commitMsgHint')}
            </p>
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setCreateDraftDialogOpen(false);
                setCreateDraftCommitMsg('');
              }}
              disabled={actionLoading}
            >
              {t('common.cancel')}
            </Button>
            <Button onClick={handleConfirmCreateDraft} disabled={actionLoading}>
              {t('skill.createDraft')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

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
              onForcePublish={handleForcePublish}
              onOnline={handleOnline}
              onOffline={handleOffline}
              onDownload={handleDownload}
              showCreateDraftButton
              allLabels={detail.labels}
              onSaveLabels={handleSaveLabels}
              skillEnabled={detail.enable}
              isGlobalAdmin={globalAdmin}
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

      {/* Force-publish confirmation dialog */}
      <Dialog open={forcePublishConfirmOpen} onOpenChange={setForcePublishConfirmOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <ShieldAlert className="h-5 w-5 text-destructive" />
              {t('skill.forcePublishConfirmTitle')}
            </DialogTitle>
            <DialogDescription>
              {t('skill.forcePublishConfirmDesc', { version: selectedVersion })}
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
                await handleForcePublish(selectedVersion);
              }}
            >
              <ShieldAlert className="h-4 w-4 mr-1" />
              {t('skill.forcePublishConfirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function parseSemver(version: string): { major: number; minor: number; patch: number } | null {
  const match = version.trim().match(/^(\d+)\.(\d+)\.(\d+)$/);
  if (!match) return null;
  return {
    major: Number(match[1]),
    minor: Number(match[2]),
    patch: Number(match[3]),
  };
}

function isSemverVersion(version: string): boolean {
  return parseSemver(version) !== null;
}

function compareSemverVersion(a: string, b: string): number {
  const pa = parseSemver(a);
  const pb = parseSemver(b);
  if (!pa || !pb) return 0;
  if (pa.major !== pb.major) return pa.major - pb.major;
  if (pa.minor !== pb.minor) return pa.minor - pb.minor;
  return pa.patch - pb.patch;
}

function parseLegacyVersion(version: string): number | null {
  const match = version.trim().match(/^[vV](\d+)$/);
  if (!match) return null;
  const parsed = Number(match[1]);
  if (!Number.isInteger(parsed) || parsed <= 0) return null;
  return parsed;
}

function isLegacyVersion(version: string): boolean {
  return parseLegacyVersion(version) !== null;
}

function compareLegacyVersion(a: string, b: string): number {
  const pa = parseLegacyVersion(a);
  const pb = parseLegacyVersion(b);
  if (pa === null || pb === null) return 0;
  return pa - pb;
}

function suggestNextVersionFromBase(baseVersion: string): string {
  const semver = parseSemver(baseVersion);
  if (semver) {
    return `${semver.major}.${semver.minor}.${semver.patch + 1}`;
  }
  const legacy = parseLegacyVersion(baseVersion);
  if (legacy !== null) {
    return `v${legacy + 1}`;
  }
  return baseVersion;
}

function InfoCell({
  label,
  value,
  icon,
  compact = false,
  colSpan,
}: {
  label: string;
  value: React.ReactNode;
  icon?: React.ReactNode;
  compact?: boolean;
  colSpan?: number;
}) {
  return (
    <div
      className={cn(
        'flex items-center gap-3 px-5 py-3',
        compact && 'gap-2.5 px-4 py-2.5',
        colSpan === 2 && 'col-span-2',
      )}
    >
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
    reviewed: 'bg-teal-50 text-teal-700 dark:bg-teal-950/40 dark:text-teal-300',
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
