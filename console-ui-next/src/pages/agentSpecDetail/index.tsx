import { useEffect, useCallback, useState, useMemo } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import {
  ArrowLeft,
  History,
  Plus,
  Package,
  Clock,
  Tag,
  Globe,
  FileText,
  Send,
  CheckCircle2,
  GitBranch,
  Power,
  PowerOff,
  Trash2,
  Pencil,
  Lock,
  Save,
  X,
  AlertCircle,
  Loader2,
} from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Switch } from '@/components/ui/switch';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import MDEditor from '@uiw/react-md-editor';
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
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet';
import { useAgentSpecStore } from '@/stores/agentspec-store';
import { useNamespaceStore } from '@/stores/namespace-store';
import { agentSpecApi } from '@/api/agentspec';
import { parseBizTags, parsePipelineInfo, type AgentSpecDocument, type AgentSpecResource, type AgentSpecVersionSummary } from '@/types/agentspec';
import { cn } from '@/lib/utils';
import dayjs from 'dayjs';

import { VersionTimeline } from '../agentSpecManagement/components/VersionTimeline';
import { ResourceViewer } from '../agentSpecManagement/components/ResourceViewer';
import { sortVersionsDescending } from '../agentSpecManagement/components/version-utils';
import { LabelBindDialog } from '@/components/ai/LabelBindDialog';
import { BizTagEditDialog } from '@/components/ai/BizTagEditDialog';
import { PipelineStatusDisplay } from '../skillManagement/components/PipelineStatusDisplay';
import { DetailTagChip } from '@/components/ai/DetailTagChip';
import { CliCommandCard } from '@/components/ai/CliCommandCard';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import {
  resolveCreateLocation,
  getAncestorFolders,
  normalizeRelativePath,
} from '../newAgentSpec/create-node-utils';
import {
  getAgentSpecDescription,
  syncManifestDescription,
} from '../newAgentSpec/manifest-description-utils';

export default function AgentSpecDetailPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { name: routeName } = useParams<{ name: string }>();
  const agentSpecName = routeName ? decodeURIComponent(routeName) : '';
  const { currentNamespace } = useNamespaceStore();
  const namespaceId =
    searchParams.get('namespaceId') ||
    searchParams.get('namespace') ||
    currentNamespace ||
    'public';

  const {
    currentDetail,
    detailLoading,
    error,
    fetchDetail,
    clearDetail,
    clearError,
  } = useAgentSpecStore();

  const [actionLoading, setActionLoading] = useState(false);
  const [versionSheetOpen, setVersionSheetOpen] = useState(false);
  const [selectedVersion, setSelectedVersion] = useState<string>('');
  const [detailDocument, setDetailDocument] = useState<AgentSpecDocument | null>(null);
  const [labelDialogOpen, setLabelDialogOpen] = useState(false);
  const [bizTagDialogOpen, setBizTagDialogOpen] = useState(false);
  const [enableToggling, setEnableToggling] = useState(false);
  const [scopeToggling, setScopeToggling] = useState(false);
  const [bizTags, setBizTags] = useState<string[]>([]);

  // Draft editing state
  const [isEditingDraft, setIsEditingDraft] = useState(false);
  const [editDescription, setEditDescription] = useState('');
  const [editAgentsContent, setEditAgentsContent] = useState('');
  const [editResources, setEditResources] = useState<Record<string, AgentSpecResource>>({});
  const [editContent, setEditContent] = useState('{}');
  const [draftSaving, setDraftSaving] = useState(false);
  const [editVirtualFolders, setEditVirtualFolders] = useState<Set<string>>(new Set());
  const [createNodeOpen, setCreateNodeOpen] = useState(false);
  const [createNodeMode, setCreateNodeMode] = useState<'file' | 'folder'>('file');
  const [createNodeType, setCreateNodeType] = useState('other');
  const [createNodePath, setCreateNodePath] = useState('');
  const [createNodeFallbackType, setCreateNodeFallbackType] = useState('other');

  // Create draft dialog state
  const [createDraftDialogOpen, setCreateDraftDialogOpen] = useState(false);
  const [createDraftFromVersion, setCreateDraftFromVersion] = useState('');
  const [createDraftTargetVersion, setCreateDraftTargetVersion] = useState('');

  const loadDetail = useCallback(() => {
    if (agentSpecName) {
      return fetchDetail(namespaceId, agentSpecName);
    }
  }, [fetchDetail, namespaceId, agentSpecName]);

  useEffect(() => {
    setDetailDocument(null);
    setSelectedVersion('');
    setIsEditingDraft(false);
    loadDetail();
    return () => {
      setDetailDocument(null);
      setSelectedVersion('');
      clearDetail();
      clearError();
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [agentSpecName, namespaceId]);

  useEffect(() => {
    setBizTags(parseBizTags(currentDetail?.bizTags));
  }, [currentDetail?.bizTags]);

  useEffect(() => {
    if (!currentDetail || detailLoading || selectedVersion) {
      return;
    }
    // Prefer the "latest" labelled version, fall back to first by sort order
    const latestLabelled = currentDetail.labels?.latest;
    const versions = sortVersionsDescending(currentDetail.versions || []);
    const defaultVersion = (latestLabelled && versions.some(v => v.version === latestLabelled))
      ? latestLabelled
      : versions[0]?.version;
    if (defaultVersion) {
      setSelectedVersion(defaultVersion);
    }
  }, [currentDetail, detailLoading, selectedVersion]);

  useEffect(() => {
    if (!selectedVersion || !agentSpecName) {
      setDetailDocument(null);
      return;
    }

    let cancelled = false;
    setDetailDocument(null);

    agentSpecApi.getVersion({
      namespaceId,
      agentSpecName,
      version: selectedVersion,
    }).then((response) => {
      if (!cancelled) {
        setDetailDocument(response.data);
      }
    }).catch(() => {
      if (!cancelled) {
        setDetailDocument(null);
      }
    });

    return () => {
      cancelled = true;
    };
  }, [selectedVersion, namespaceId, agentSpecName]);

  // ===== Enable/disable handler =====

  const handleToggleEnable = async () => {
    if (!currentDetail) return;
    setEnableToggling(true);
    try {
      if (currentDetail.enable) {
        await agentSpecApi.offline({ namespaceId, agentSpecName, scope: 'agentSpec' });
      } else {
        await agentSpecApi.online({ namespaceId, agentSpecName, scope: 'agentSpec' });
      }
      toast.success(t(currentDetail.enable ? 'agentSpec.disableSuccess' : 'agentSpec.enableSuccess'));
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
      await agentSpecApi.updateScope({ namespaceId, agentSpecName, scope: newScope });
      toast.success(t('agentSpec.scopeUpdateSuccess'));
      await loadDetail();
    } catch {
      // handled by interceptor
    } finally {
      setScopeToggling(false);
    }
  };

  // ===== Version lifecycle handlers =====

  const handleCreateDraft = async (basedOnVersion?: string) => {
    if (!basedOnVersion) return;
    const suggestedVersion = suggestNextVersionFromBase(basedOnVersion);
    setCreateDraftFromVersion(basedOnVersion);
    setCreateDraftTargetVersion(suggestedVersion);
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
      await agentSpecApi.createDraft({
        namespaceId,
        agentSpecName,
        basedOnVersion: createDraftFromVersion,
        targetVersion: targetVersion || undefined,
      });
      toast.success(t('agentSpec.createDraftSuccess'));
      setCreateDraftDialogOpen(false);
      await loadDetail();
      const updated = useAgentSpecStore.getState().currentDetail;
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
    setActionLoading(true);
    try {
      await agentSpecApi.submit({ namespaceId, agentSpecName, version });
      toast.success(t('agentSpec.submitSuccess'));
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
      await agentSpecApi.deleteDraft({ namespaceId, agentSpecName });
      toast.success(t('agentSpec.deleteDraftSuccess'));
      await loadDetail();
    } catch {
      await loadDetail();
    } finally {
      setActionLoading(false);
    }
  };

  const handlePublish = async (version: string) => {
    setActionLoading(true);
    try {
      await agentSpecApi.publish({
        namespaceId,
        agentSpecName,
        version,
      });
      toast.success(t('agentSpec.publishSuccess'));
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
      await agentSpecApi.redraft({ namespaceId, agentSpecName, version });
      toast.success(t('agentSpec.redraftSuccess'));
      await loadDetail();
      const response = await agentSpecApi.getVersion({ namespaceId, agentSpecName, version });
      setDetailDocument(response.data);
      const doc = response.data;
      const docResource = doc?.resource || {};
      const agentsEntry = Object.entries(docResource).find(([, r]) => {
        const name = r.name.split('/').pop() || r.name;
        return name.toUpperCase() === 'AGENTS.MD';
      });
      const agentsStr = agentsEntry?.[1]?.content || '';
      const resEntries = Object.entries(docResource).filter(([key]) => key !== agentsEntry?.[0]);
      setEditDescription(doc?.description ?? '');
      setEditAgentsContent(agentsStr);
      setEditResources(Object.fromEntries(resEntries));
      setEditContent(doc?.content || '{}');
      setEditVirtualFolders(new Set());
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
      await agentSpecApi.online({ namespaceId, agentSpecName, version });
      toast.success(t('agentSpec.onlineSuccess'));
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
      await agentSpecApi.offline({ namespaceId, agentSpecName, version });
      toast.success(t('agentSpec.offlineSuccess'));
      await loadDetail();
    } catch {
      await loadDetail();
    } finally {
      setActionLoading(false);
    }
  };

  const handleSelectVersion = (version: string) => {
    setSelectedVersion(version);
  };

  const handleSaveLabels = async (newLabels: Record<string, string>) => {
    await agentSpecApi.updateLabels({
      namespaceId,
      agentSpecName,
      labels: JSON.stringify(newLabels),
    });
    toast.success(t('common.versionLabels.updateSuccess'));
    await loadDetail();
  };

  const handleSaveBizTags = async (nextBizTags: string[]) => {
    await agentSpecApi.updateBizTags({
      namespaceId,
      agentSpecName,
      bizTags: JSON.stringify(nextBizTags),
    });
    toast.success(t('agentSpec.bizTagsUpdateSuccess'));
    await loadDetail();
  };

  // Build CLI commands for current agentspec (must be before early returns to keep hooks order stable)
  const cliCommands = useMemo(() => {
    const versionFlag = selectedVersion ? ` --version ${selectedVersion}` : '';
    return [{
      label: t('common.cliUsage.cliInstall'),
      command: `npx @nacos-group/cli agentspec-get ${agentSpecName}${versionFlag}`,
    }];
  }, [agentSpecName, selectedVersion, t]);

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
          <Package className="h-8 w-8 text-destructive/50" />
        </div>
        <p className="text-sm text-destructive">{error}</p>
        <div className="flex gap-2">
          <Button variant="outline" onClick={() => navigate('/agentspec')}>
            {t('agentSpec.backToList')}
          </Button>
          <Button onClick={() => loadDetail()}>
            {t('common.retry')}
          </Button>
        </div>
      </div>
    );
  }

  if (!currentDetail) return null;

  const detail = currentDetail;
  const spec = detailDocument ?? {
    namespaceId,
    name: agentSpecName,
    description: '',
    content: '{}',
    resource: {},
  };
  const versions = sortVersionsDescending(detail.versions || []);
  const latestVersion = detail.labels?.latest;
  const versionOptions = (() => {
    const seen = new Set<string>();
    const result: AgentSpecVersionSummary[] = [];

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
  const currentPipelineInfo = parsePipelineInfo(currentVersionSummary?.publishPipelineInfo);
  const currentVersionStatusLabel = currentVersionStatus
    ? t(`agentSpec.versionStatus.${currentVersionStatus}`)
    : '-';
  const onlineVersionCountLabel = t('agentSpec.onlineCount', { count: detail.onlineCnt ?? 0 });
  const agentsResourceEntry = Object.entries(spec.resource || {}).find(([, resource]) => {
    const normalizedName = resource.name.split('/').pop() || resource.name;
    return normalizedName.toUpperCase() === 'AGENTS.MD';
  });
  const agentsContent = agentsResourceEntry?.[1]?.content || '';
  const resourceEntries = Object.entries(spec.resource || {}).filter(([key]) => key !== agentsResourceEntry?.[0]);
  const resourcesWithoutAgents = Object.fromEntries(resourceEntries);
  // Labels bound to the currently selected version
  const currentVersionLabels = Object.entries(detail.labels || {}).filter(
    ([, val]) => val === selectedVersion,
  );

  // ===== Draft editing handlers =====

  const handleStartEdit = () => {
    setEditDescription(spec.description ?? '');
    setEditAgentsContent(agentsContent);
    setEditResources({ ...resourcesWithoutAgents });
    setEditContent(spec.content || '{}');
    setEditVirtualFolders(new Set());
    setIsEditingDraft(true);
  };

  const handleCancelEdit = () => {
    setIsEditingDraft(false);
  };

  const handleSaveDraft = async () => {
    setDraftSaving(true);
    try {
      const trimmedDescription = editDescription.trim();
      const syncedContent = syncManifestDescription(editContent, trimmedDescription);
      const fullResource: Record<string, import('@/types/agentspec').AgentSpecResource> = { ...editResources };

      const existingAgentsKey = agentsResourceEntry?.[0];
      if (existingAgentsKey) {
        fullResource[existingAgentsKey] = {
          ...agentsResourceEntry![1],
          content: editAgentsContent,
        };
      } else if (editAgentsContent.trim()) {
        const newKey = `${agentSpecName}/AGENTS.MD`;
        fullResource[newKey] = {
          name: `${agentSpecName}/AGENTS.MD`,
          type: 'instruction',
          content: editAgentsContent,
          metadata: null,
        };
      }

      const agentSpecCard = JSON.stringify({
        name: agentSpecName,
        description: trimmedDescription,
        content: syncedContent,
        resource: fullResource,
      });

      await agentSpecApi.updateDraft({ namespaceId, agentSpecCard });
      toast.success(t('agentSpec.draftSaveSuccess'));
      setIsEditingDraft(false);
      await loadDetail();
      if (selectedVersion) {
        const response = await agentSpecApi.getVersion({ namespaceId, agentSpecName, version: selectedVersion });
        setDetailDocument(response.data);
      }
    } catch {
      // handled by interceptor
    } finally {
      setDraftSaving(false);
    }
  };

  // ===== File tree operation handlers for resource editing =====

  const RESOURCE_TYPES = ['config', 'skill', 'cron', 'dockerfile', 'other'] as const;
  const CUSTOM_RESOURCE_TYPE = '__custom__';

  const getContextFromTreeKey = (key: string | null): { resourceType: string; relativeDir: string } => {
    if (!key || key === 'manifest.json') {
      return { resourceType: 'other', relativeDir: '' };
    }
    const isFolder = key.endsWith('/');
    const normalized = key.replace(/\/$/, '');
    const [resourceType, ...rest] = normalized.split('/');
    const relativePath = rest.join('/');
    if (!resourceType) {
      return { resourceType: 'other', relativeDir: '' };
    }
    return {
      resourceType,
      relativeDir: isFolder ? relativePath : relativePath.split('/').slice(0, -1).join('/'),
    };
  };

  const handleEditCreateFile = (parentKey?: string) => {
    const { resourceType, relativeDir } = getContextFromTreeKey(parentKey ?? null);
    const isPresetResourceType = RESOURCE_TYPES.includes(resourceType as (typeof RESOURCE_TYPES)[number]);
    const defaultName = 'untitled';
    setCreateNodeMode('file');
    setCreateNodeFallbackType(resourceType || 'other');
    setCreateNodeType(isPresetResourceType ? resourceType : CUSTOM_RESOURCE_TYPE);
    setCreateNodePath(
      isPresetResourceType
        ? (relativeDir ? `${relativeDir}/${defaultName}` : defaultName)
        : [resourceType, relativeDir, defaultName].filter(Boolean).join('/'),
    );
    setCreateNodeOpen(true);
  };

  const handleEditCreateFolder = (parentKey?: string) => {
    const { resourceType, relativeDir } = getContextFromTreeKey(parentKey ?? null);
    const isPresetResourceType = RESOURCE_TYPES.includes(resourceType as (typeof RESOURCE_TYPES)[number]);
    const defaultName = 'new-folder';
    setCreateNodeMode('folder');
    setCreateNodeFallbackType(resourceType || 'other');
    setCreateNodeType(isPresetResourceType ? resourceType : CUSTOM_RESOURCE_TYPE);
    setCreateNodePath(
      isPresetResourceType
        ? (relativeDir ? `${relativeDir}/${defaultName}` : defaultName)
        : [resourceType, relativeDir, defaultName].filter(Boolean).join('/'),
    );
    setCreateNodeOpen(true);
  };

  const handleConfirmCreateNode = () => {
    const { resourceType: normalizedType, relativePath: normalizedPath } = resolveCreateLocation(
      createNodeType,
      createNodePath,
      CUSTOM_RESOURCE_TYPE,
      createNodeFallbackType,
    );
    if (!normalizedType) {
      toast.error(t('agentSpec.resourceTypeRequired'));
      return;
    }
    if (!normalizedPath && !(createNodeMode === 'folder' && createNodeType === CUSTOM_RESOURCE_TYPE)) {
      toast.error(t('agentSpec.pathRequired'));
      return;
    }

    if (createNodeMode === 'file') {
      if (normalizedPath === 'manifest.json') {
        toast.error(t('agentSpec.invalidFileName'));
        return;
      }
      if (Object.values(editResources).some(r => r.type === normalizedType && r.name === normalizedPath)) {
        toast.error(t('agentSpec.fileExists'));
        return;
      }
      setEditResources(prev => ({
        ...prev,
        [normalizedPath]: {
          name: normalizedPath,
          type: normalizedType as AgentSpecResource['type'],
          content: '',
          metadata: null,
        },
      }));
    } else {
      const folderKey = `${normalizedType}/${normalizedPath}`;
      const folderExists = editVirtualFolders.has(folderKey) ||
        Object.values(editResources).some(r =>
          r.type === normalizedType && getAncestorFolders(r.name).includes(normalizedPath)
        );
      if (folderExists) {
        toast.error(t('agentSpec.folderExists'));
        return;
      }
      setEditVirtualFolders(prev => {
        const next = new Set(prev);
        next.add(folderKey);
        return next;
      });
    }
    setCreateNodeOpen(false);
  };

  const handleEditDeleteNode = (key: string, nodeType: 'file' | 'folder') => {
    if (key === 'manifest.json') return;
    if (nodeType === 'folder') {
      const normalizedKey = key.replace(/\/$/, '');
      const [resourceType, ...segments] = normalizedKey.split('/');
      const relativePath = segments.join('/');
      if (!resourceType || !relativePath) return;

      setEditResources(prev => {
        const next = { ...prev };
        for (const [k, r] of Object.entries(next)) {
          if (r.type !== resourceType) continue;
          if (r.name === relativePath || r.name.startsWith(`${relativePath}/`)) {
            delete next[k];
          }
        }
        return next;
      });
      setEditVirtualFolders(prev => {
        const next = new Set<string>();
        for (const folder of prev) {
          if (folder !== normalizedKey && !folder.startsWith(`${normalizedKey}/`)) {
            next.add(folder);
          }
        }
        return next;
      });
    } else {
      setEditResources(prev => {
        const next = { ...prev };
        for (const [k, r] of Object.entries(next)) {
          if (`${r.type}/${r.name}` === key) {
            delete next[k];
            break;
          }
        }
        return next;
      });
    }
  };

  const handleEditRenameFile = (key: string, newName: string) => {
    if (key === 'manifest.json') return;
    setEditResources(prev => {
      const next = { ...prev };
      for (const [k, r] of Object.entries(next)) {
        if (`${r.type}/${r.name}` === key) {
          const parentPath = r.name.includes('/') ? `${r.name.split('/').slice(0, -1).join('/')}/` : '';
          const newResourceName = `${parentPath}${newName}`;
          if (Object.values(next).some(res => res.type === r.type && res.name === newResourceName)) {
            toast.error(t('agentSpec.fileExists'));
            return prev;
          }
          delete next[k];
          next[newResourceName] = { ...r, name: newResourceName };
          break;
        }
      }
      return next;
    });
  };

  // Rename a folder.
  // If folderKey is a top-level type folder (e.g. "config/"), rename changes the resource type.
  // If folderKey is a sub-folder (e.g. "config/oldPath/"), rename the last segment of oldPath.
  const handleEditRenameFolder = (folderKey: string, newName: string) => {
    const normalized = folderKey.replace(/\/$/, ''); // "type/old" or just "type"
    const [resourceType, ...segments] = normalized.split('/');
    if (!resourceType) return;

    // Top-level folder rename → change resource type
    if (segments.length === 0) {
      const oldType = resourceType;
      const newType = newName;
      setEditResources(prev => {
        const next: typeof prev = {};
        for (const [k, r] of Object.entries(prev)) {
          if (r.type === oldType) {
            next[k] = { ...r, type: newType as AgentSpecResource['type'] };
          } else {
            next[k] = r;
          }
        }
        return next;
      });
      setEditVirtualFolders(prev => {
        const next = new Set<string>();
        for (const folder of prev) {
          if (folder === oldType || folder.startsWith(`${oldType}/`)) {
            next.add(newType + folder.slice(oldType.length));
          } else {
            next.add(folder);
          }
        }
        return next;
      });
      return;
    }

    // Sub-folder rename
    const oldRelPath = segments.join('/');
    const parentSegments = segments.slice(0, -1);
    const newRelPath = [...parentSegments, newName].join('/');

    setEditResources(prev => {
      const next: typeof prev = {};
      for (const [k, r] of Object.entries(prev)) {
        if (r.type !== resourceType) {
          next[k] = r;
          continue;
        }
        if (r.name === oldRelPath || r.name.startsWith(`${oldRelPath}/`)) {
          const updatedName = newRelPath + r.name.slice(oldRelPath.length);
          next[updatedName] = { ...r, name: updatedName };
        } else {
          next[k] = r;
        }
      }
      return next;
    });

    setEditVirtualFolders(prev => {
      const next = new Set<string>();
      const oldPrefix = `${resourceType}/${oldRelPath}`;
      const newPrefix = `${resourceType}/${newRelPath}`;
      for (const folder of prev) {
        if (folder === oldPrefix || folder.startsWith(`${oldPrefix}/`)) {
          next.add(newPrefix + folder.slice(oldPrefix.length));
        } else {
          next.add(folder);
        }
      }
      return next;
    });
  };

  const createNodePathPrefix = RESOURCE_TYPES.includes(createNodeType as (typeof RESOURCE_TYPES)[number])
    ? `${createNodeType}/`
    : '';

  const handleCreateNodeTypeChange = (value: string) => {
    if (value === CUSTOM_RESOURCE_TYPE) {
      const normalized = normalizeRelativePath(createNodePath);
      const prefix = RESOURCE_TYPES.includes(createNodeType as (typeof RESOURCE_TYPES)[number])
        ? `${createNodeType}/`
        : '';
      setCreateNodeType(value);
      setCreateNodePath(prefix && normalized ? `${prefix}${normalized}` : normalized);
      return;
    }
    const normalized = normalizeRelativePath(createNodePath);
    const currentPrefix = RESOURCE_TYPES.includes(createNodeType as (typeof RESOURCE_TYPES)[number])
      ? `${createNodeType}/`
      : '';
    const nextPrefix = `${value}/`;
    const nextPath = normalized.startsWith(nextPrefix)
      ? normalized.slice(nextPrefix.length)
      : currentPrefix && normalized.startsWith(currentPrefix)
        ? normalized.slice(currentPrefix.length)
        : normalized;
    setCreateNodeType(value);
    setCreateNodeFallbackType(value);
    setCreateNodePath(nextPath);
  };

  return (
    <div className="flex min-h-[calc(100vh-88px)] flex-col gap-5 pb-5">
      {/* ===== Hero Header ===== */}
      <div className="relative rounded-xl border bg-card overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-br from-primary/[0.04] via-transparent to-blue-500/[0.03]" />
        <div className="absolute top-0 right-0 w-64 h-64 bg-gradient-to-bl from-primary/[0.06] to-transparent rounded-full -translate-y-1/2 translate-x-1/3" />

        <div className="relative px-5 py-4">
          {/* Top bar */}
          <div className="flex items-center justify-between mb-4">
            <Button
              variant="ghost"
              size="sm"
              className="h-7 gap-1.5 text-muted-foreground hover:text-foreground -ml-2"
              onClick={() => navigate('/agentspec')}
            >
              <ArrowLeft className="h-3.5 w-3.5" />
              {t('agentSpec.backToList')}
            </Button>

            <div className="flex items-center gap-2">
              {selectedVersion && (
                <Select value={selectedVersion} onValueChange={handleSelectVersion} disabled={isEditingDraft}>
                  <SelectTrigger className="w-[140px] h-7 text-xs bg-background/80">
                    <SelectValue placeholder={t('agentSpec.selectVersion')} />
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
                              {t('agentSpec.latestVersion')}
                            </Badge>
                          )}
                          {version.status === 'draft' && (
                            <Badge className="bg-amber-100 text-amber-700 dark:bg-amber-950/50 dark:text-amber-300 text-[10px] px-1 py-0 border-0">
                              {t('agentSpec.versionStatus.draft')}
                            </Badge>
                          )}
                          {isVersionRejected && (
                            <Badge className="bg-red-100 text-red-700 dark:bg-red-950/50 dark:text-red-300 text-[10px] px-1 py-0 border-0">
                              {t('agentSpec.versionStatus.rejected')}
                            </Badge>
                          )}
                          {!isVersionRejected && (version.status === 'reviewing' || version.status === 'reviewed') && (
                            <Badge className={isVersionPendingPublish
                              ? 'bg-teal-100 text-teal-700 dark:bg-teal-950/50 dark:text-teal-300 text-[10px] px-1 py-0 border-0'
                              : 'bg-blue-100 text-blue-700 dark:bg-blue-950/50 dark:text-blue-300 text-[10px] px-1 py-0 border-0'
                            }>
                              {t(isVersionPendingPublish ? 'agentSpec.versionStatus.pendingPublish' : 'agentSpec.versionStatus.reviewing')}
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
                {t('agentSpec.versionHistory')}
              </Button>

            </div>
          </div>

          {/* Identity */}
          <div className="flex items-start gap-4">
            <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-blue-500 to-cyan-400 shadow-lg shadow-blue-500/20">
              <Package className="h-7 w-7 text-white" />
            </div>

            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2.5 mb-1">
                <h1 className="text-xl font-bold tracking-tight">{spec.name}</h1>
                {selectedVersion && (
                  <span className="text-xs text-muted-foreground font-mono bg-muted/60 px-1.5 py-0.5 rounded">
                    {selectedVersion}
                  </span>
                )}
              </div>
              {/* Enable toggle switch */}
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
                    {detail.enable ? t('agentSpec.enabled') : t('agentSpec.disabled')}
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
                    {detail.scope === 'PUBLIC' ? t('agentSpec.scopePublic') : t('agentSpec.scopePrivate')}
                  </span>
                </label>
              </div>
              {/* Description - editable in draft mode */}
              {isEditingDraft ? (
                <Textarea
                  value={editDescription}
                  onChange={(e) => {
                    const newDesc = e.target.value;
                    setEditDescription(newDesc);
                    setEditContent(prev => syncManifestDescription(prev, newDesc));
                  }}
                  placeholder={t('agentSpec.descriptionPlaceholder')}
                  className="text-sm max-w-2xl min-h-8 resize-none"
                />
              ) : spec.description ? (
                <p className="text-sm text-muted-foreground leading-relaxed max-w-2xl">
                  {spec.description}
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
                    <Package className="h-3 w-3" />
                    {t('agentSpec.downloadCount', { count: detail.downloadCount })}
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
                            {t('agentSpec.cancelEdit')}
                          </Button>
                          <Button
                            size="sm"
                            className="h-7 text-xs gap-1.5"
                            onClick={handleSaveDraft}
                            disabled={draftSaving}
                          >
                            {draftSaving ? <Loader2 className="h-3 w-3 animate-spin" /> : <Save className="h-3 w-3" />}
                            {draftSaving ? t('common.loading') : t('agentSpec.saveDraft')}
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
                            {t('agentSpec.editDraft')}
                          </Button>
                          <div className="h-4 w-px bg-border mx-0.5" />
                          <Button
                            size="sm"
                            className="h-7 text-xs gap-1.5"
                            disabled={actionLoading}
                            onClick={() => handleSubmit(selectedVersion)}
                          >
                            <Send className="h-3 w-3" />
                            {currentPipelineInfo && currentPipelineInfo.status === 'REJECTED'
                              ? t('agentSpec.resubmit')
                              : t('agentSpec.submit')}
                          </Button>
                          <Button
                            variant="outline"
                            size="sm"
                            className="h-7 text-xs gap-1.5 text-destructive hover:text-destructive hover:bg-destructive/10"
                            disabled={actionLoading}
                            onClick={handleDeleteDraft}
                          >
                            <Trash2 className="h-3 w-3" />
                            {t('agentSpec.deleteDraft')}
                          </Button>
                          {currentPipelineInfo && currentPipelineInfo.status === 'REJECTED' && (
                            <PipelineStatusDisplay pipelineInfo={currentPipelineInfo} compact translationPrefix="agentSpec" />
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
                          ? t('agentSpec.pipelineInProgress')
                          : t('agentSpec.publish')}
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
                            {t('agentSpec.redraft')}
                          </Button>
                          <Button
                            variant="outline"
                            size="sm"
                            className="h-7 text-xs gap-1.5 text-destructive hover:text-destructive hover:bg-destructive/10"
                            disabled={actionLoading}
                            onClick={handleDeleteDraft}
                          >
                            <Trash2 className="h-3 w-3" />
                            {t('agentSpec.deleteDraft')}
                          </Button>
                        </>
                      )}
                      {currentPipelineInfo && currentPipelineInfo.status === 'APPROVED' && (
                        <PipelineStatusDisplay pipelineInfo={currentPipelineInfo} compact translationPrefix="agentSpec" />
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
                      {t('agentSpec.offline')}
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
                      {t('agentSpec.online')}
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
                        {t('agentSpec.createDraftFrom')}
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
                            {t('agentSpec.draftExistsTip')}
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

      {/* ===== Tabs Content ===== */}
      <Tabs defaultValue="overview" className={cn('flex flex-col', (detailLoading || actionLoading) && 'opacity-50 pointer-events-none')}>
        <TabsList className="w-fit">
          <TabsTrigger value="overview" className="gap-1.5">
            <FileText className="h-3.5 w-3.5" />
            {t('agentSpec.agentsFile')}
          </TabsTrigger>
          <TabsTrigger value="resources" className="gap-1.5">
            <Package className="h-3.5 w-3.5" />
            {t('agentSpec.resources')}
            {resourceEntries.length > 0 && (
              <Badge variant="secondary" className="text-[10px] px-1 py-0 h-4 ml-0.5">
                {resourceEntries.length}
              </Badge>
            )}
          </TabsTrigger>
        </TabsList>

        <TabsContent value="overview">
          <div className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,1fr)_320px]">
            <Card className="overflow-hidden py-0 gap-0 min-h-[580px]">
              <div className="px-5 py-3.5 border-b bg-muted/30">
                <h2 className="text-sm font-semibold flex items-center gap-2">
                  <FileText className="h-4 w-4 text-muted-foreground" />
                  {t('agentSpec.agentsFile')}
                </h2>
              </div>
              <CardContent className="p-5">
                {isEditingDraft ? (
                  <div className="space-y-2">
                    <div data-color-mode="light" className="dark:hidden">
                      <MDEditor
                        value={editAgentsContent}
                        onChange={(val) => setEditAgentsContent(val || '')}
                        height={500}
                        preview="live"
                      />
                    </div>
                    <div data-color-mode="dark" className="hidden dark:block">
                      <MDEditor
                        value={editAgentsContent}
                        onChange={(val) => setEditAgentsContent(val || '')}
                        height={500}
                        preview="live"
                      />
                    </div>
                  </div>
                ) : agentsContent ? (
                  <div className="app-markdown prose prose-sm dark:prose-invert max-w-none">
                    <Markdown remarkPlugins={[remarkGfm]}>
                      {agentsContent}
                    </Markdown>
                  </div>
                ) : (
                  <p className="text-sm text-muted-foreground">{t('agentSpec.noAgentsFile')}</p>
                )}
              </CardContent>
            </Card>

            <div className="space-y-4 lg:w-[320px]">
              {currentVersionStatus !== 'draft' && (
                <CliCommandCard commands={cliCommands} />
              )}

              {/* Basic info card */}
              <Card className="overflow-hidden py-0 gap-0">
                <div className="px-4 py-3 border-b bg-muted/30">
                  <h2 className="text-sm font-semibold flex items-center gap-2">
                    <Package className="h-4 w-4 text-muted-foreground" />
                    {t('agentSpec.basicInfo')}
                  </h2>
                </div>
                <CardContent className="p-0">
                  <div className="grid grid-cols-2 [&>*:nth-child(n+3)]:border-t [&>*:nth-child(even)]:border-l border-border">
                    <InfoCell
                      compact
                      label={t('agentSpec.status')}
                      value={<StatusBadge status={currentVersionStatus} label={currentVersionStatusLabel} />}
                      icon={<Tag className="h-3.5 w-3.5" />}
                    />
                    {currentVersionSummary && (
                      <InfoCell compact label={t('agentSpec.author')} value={currentVersionSummary.author || '-'} icon={<Globe className="h-3.5 w-3.5" />} />
                    )}
                    <InfoCell compact label={t('agentSpec.downloads')} value={String(detail.downloadCount ?? 0)} icon={<Package className="h-3.5 w-3.5" />} />
                    {currentVersionSummary && (
                      <InfoCell compact label={t('agentSpec.versionDownloads')} value={String(currentVersionSummary.downloadCount ?? 0)} icon={<Package className="h-3.5 w-3.5" />} />
                    )}
                  </div>
                </CardContent>
              </Card>

              {currentPipelineInfo && (
                <Card className="overflow-hidden py-0 gap-0">
                  <div className="px-4 py-3 border-b bg-muted/30">
                    <h2 className="text-sm font-semibold flex items-center gap-2">
                      <GitBranch className="h-4 w-4 text-muted-foreground" />
                      {t('agentSpec.pipelineStatus')}
                    </h2>
                  </div>
                  <CardContent className="p-3.5">
                    <PipelineStatusDisplay
                      pipelineInfo={currentPipelineInfo}
                      translationPrefix="agentSpec"
                      onRefresh={() => loadDetail()}
                    />
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
                    <p className="text-xs text-muted-foreground">{t('agentSpec.noBizTags')}</p>
                  )}
                </CardContent>
              </Card>

              {/* Labels card */}
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

        <TabsContent value="resources">
          <Card className="flex h-[580px] flex-col overflow-hidden py-0 gap-0">
            <div className="px-5 py-3.5 border-b bg-muted/30">
              <h2 className="text-sm font-semibold flex items-center gap-2">
                <Package className="h-4 w-4 text-muted-foreground" />
                {t('agentSpec.resources')}
              </h2>
            </div>
            <CardContent className="flex-1 min-h-0 p-0">
              <ResourceViewer
                resources={isEditingDraft ? editResources : resourcesWithoutAgents}
                content={isEditingDraft ? editContent : (spec.content || '{}')}
                editable={isEditingDraft}
                onChange={isEditingDraft ? (res, content) => { setEditResources(res); setEditContent(content); setEditDescription(getAgentSpecDescription(content)); } : undefined}
                onCreateFile={isEditingDraft ? handleEditCreateFile : undefined}
                onCreateFolder={isEditingDraft ? handleEditCreateFolder : undefined}
                onDeleteNode={isEditingDraft ? handleEditDeleteNode : undefined}
                onRenameFile={isEditingDraft ? handleEditRenameFile : undefined}
                onRenameFolder={isEditingDraft ? handleEditRenameFolder : undefined}
                virtualFolders={isEditingDraft ? [...editVirtualFolders] : undefined}
                className="h-full min-h-0"
              />
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      <BizTagEditDialog
        open={bizTagDialogOpen}
        onOpenChange={setBizTagDialogOpen}
        tags={bizTags}
        placeholder={t('agentSpec.bizTagPlaceholder')}
        emptyText={t('agentSpec.noBizTags')}
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

      <Sheet open={versionSheetOpen} onOpenChange={setVersionSheetOpen}>
        <SheetContent className="flex flex-col p-0 sm:max-w-md">
          <SheetHeader className="px-6 pt-6 pb-4 border-b shrink-0">
            <SheetTitle className="flex items-center gap-2">
              <History className="h-4.5 w-4.5 text-blue-500" />
              {t('agentSpec.versionHistory')}
            </SheetTitle>
            <SheetDescription>
              {t('agentSpec.totalVersions', { count: versions.length })}
            </SheetDescription>
          </SheetHeader>
          <div className="flex-1 overflow-y-auto px-4 py-4">
            <VersionTimeline
              versions={versions}
              currentVersion={selectedVersion}
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
              showCreateDraftButton={false}
              allLabels={detail.labels}
              onSaveLabels={handleSaveLabels}
            />
          </div>
        </SheetContent>
      </Sheet>

      {/* ===== Create Node Dialog ===== */}
      <Dialog open={createNodeOpen} onOpenChange={setCreateNodeOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {createNodeMode === 'file' ? t('agentSpec.createFile') : t('agentSpec.createFolder')}
            </DialogTitle>
            <DialogDescription>
              {createNodeMode === 'file'
                ? t('agentSpec.createFileDesc')
                : t('agentSpec.createFolderDesc')}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div className="space-y-2.5">
              <Label>{t('agentSpec.resourceType')}</Label>
              <Select
                value={RESOURCE_TYPES.includes(createNodeType as (typeof RESOURCE_TYPES)[number]) ? createNodeType : CUSTOM_RESOURCE_TYPE}
                onValueChange={handleCreateNodeTypeChange}
              >
                <SelectTrigger className="bg-transparent">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {RESOURCE_TYPES.map((resourceType) => (
                    <SelectItem key={resourceType} value={resourceType}>
                      {resourceType}
                    </SelectItem>
                  ))}
                  <SelectItem value={CUSTOM_RESOURCE_TYPE}>
                    {t('agentSpec.customFolder')}
                  </SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2.5">
              <Label>
                {createNodeMode === 'file'
                  ? t('agentSpec.filePath')
                  : t('agentSpec.folderPath')}
              </Label>
              <div className="flex h-9 items-center overflow-hidden rounded-md border border-input bg-transparent shadow-sm transition-colors focus-within:ring-1 focus-within:ring-ring">
                {createNodePathPrefix && (
                  <span className="shrink-0 border-r border-input bg-muted/30 px-3 text-xs text-muted-foreground">
                    {createNodePathPrefix}
                  </span>
                )}
                <Input
                  value={createNodePath}
                  onChange={(event) => setCreateNodePath(event.target.value)}
                  placeholder={
                    createNodeMode === 'file'
                      ? t('agentSpec.filePathPlaceholderCompact')
                      : t('agentSpec.folderPathPlaceholderCompact')
                  }
                  className="h-full border-0 px-3 shadow-none focus-visible:ring-0"
                />
              </div>
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setCreateNodeOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleConfirmCreateNode} disabled={!createNodePath.trim()}>
              {createNodeMode === 'file' ? t('agentSpec.createFile') : t('agentSpec.createFolder')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* ===== Create Draft Version Dialog ===== */}
      <Dialog open={createDraftDialogOpen} onOpenChange={setCreateDraftDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('agentSpec.createDraftFrom')}</DialogTitle>
            <DialogDescription>
              {t('agentSpec.createDraftFromDesc', { version: createDraftFromVersion })}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-2">
            <Label htmlFor="create-draft-target-version">{t('agentSpec.newVersion')}</Label>
            <Input
              id="create-draft-target-version"
              value={createDraftTargetVersion}
              placeholder={t('agentSpec.newVersionPlaceholder')}
              onChange={(e) => setCreateDraftTargetVersion(e.target.value)}
              disabled={actionLoading}
            />
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setCreateDraftDialogOpen(false)}
              disabled={actionLoading}
            >
              {t('common.cancel')}
            </Button>
            <Button onClick={handleConfirmCreateDraft} disabled={actionLoading}>
              {actionLoading ? <Loader2 className="h-4 w-4 animate-spin mr-1" /> : null}
              {t('common.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
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

function parseSemver(version: string): { major: number; minor: number; patch: number } | null {
  const match = version.trim().match(/^(\d+)\.(\d+)\.(\d+)$/);
  if (!match) return null;
  return { major: Number(match[1]), minor: Number(match[2]), patch: Number(match[3]) };
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

function validateDraftTargetVersion(targetVersion: string, basedOnVersion: string): string | null {
  if (!targetVersion) return null;
  const isTargetSemver = isSemverVersion(targetVersion);
  const isTargetLegacy = isLegacyVersion(targetVersion);
  if (!isTargetSemver && !isTargetLegacy) {
    return 'Invalid version format. Expected x.y.z or vN';
  }
  if (basedOnVersion) {
    const isBaseSemver = isSemverVersion(basedOnVersion);
    const isBaseLegacy = isLegacyVersion(basedOnVersion);
    if (isTargetSemver && isBaseSemver && compareSemverVersion(targetVersion, basedOnVersion) <= 0) {
      return `Version must be greater than ${basedOnVersion}`;
    }
    if (isTargetLegacy && isBaseLegacy && compareLegacyVersion(targetVersion, basedOnVersion) <= 0) {
      return `Version must be greater than ${basedOnVersion}`;
    }
  }
  return null;
}

function StatusBadge({
  status,
  label,
}: {
  status?: 'draft' | 'reviewing' | 'reviewed' | 'online' | 'offline';
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
