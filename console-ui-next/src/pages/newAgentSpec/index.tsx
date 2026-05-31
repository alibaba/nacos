import { useState, useCallback, useMemo, useRef, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { Editor } from '@monaco-editor/react';
import { Save, ArrowLeft, Package, Settings2 } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { FileTreePanel } from '../agentSpecManagement/components/FileTreePanel';
import type { FileTreeNode } from '../agentSpecManagement/components/FileTreePanel';
import { buildFileTree } from '../agentSpecManagement/components/file-tree-utils';
import { getLanguageFromFileName } from '../agentSpecManagement/components/resource-viewer-utils';
import { agentSpecApi } from '@/api/agentspec';
import type { AgentSpecResource } from '@/types/agentspec';
import { serializeFileTree, deserializeToFiles } from './editor-utils';
import type { EditorFile } from './editor-utils';
import {
  getAncestorFolders,
  normalizeRelativePath,
  resolveCreateLocation,
} from './create-node-utils';
import {
  getAgentSpecDescription,
  syncManifestDescription,
} from './manifest-description-utils';
import {
  planAgentSpecEditorVersionMode,
  type AgentSpecEditorMode,
} from './version-mode';

// ===== Constants =====

const MANIFEST_KEY = 'manifest.json';
const MIN_PANEL_WIDTH = 160;
const MAX_PANEL_WIDTH = 480;
const DEFAULT_PANEL_WIDTH = 220;
const RESOURCE_TYPES = ['config', 'skill', 'cron', 'dockerfile', 'other'] as const;
const CUSTOM_RESOURCE_TYPE = '__custom__';

type ResourceType = string;
type CreateNodeMode = 'file' | 'folder';

function getContextFromTreeKey(key: string | null): { resourceType: ResourceType; relativeDir: string } {
  if (!key || key === MANIFEST_KEY) {
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
    resourceType: resourceType as ResourceType,
    relativeDir: isFolder
      ? relativePath
      : relativePath.split('/').slice(0, -1).join('/'),
  };
}

// ===== Component =====

export default function NewAgentSpecPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const mode = (searchParams.get('mode') as AgentSpecEditorMode) || 'new';
  const editName = searchParams.get('name') || '';
  const namespaceId = searchParams.get('namespaceId') || 'public';
  const sourceVersion = searchParams.get('sourceVersion') || '';

  // File state: Map<fileName, EditorFile>
  const [files, setFiles] = useState<Map<string, EditorFile>>(() => {
    const m = new Map<string, EditorFile>();
    m.set(MANIFEST_KEY, { content: '{}', type: 'manifest' });
    return m;
  });
  const [selectedKey, setSelectedKey] = useState<string>(MANIFEST_KEY);
  const [agentSpecName, setAgentSpecName] = useState(editName);
  const [description, setDescription] = useState('');
  const [saving, setSaving] = useState(false);
  const [modified, setModified] = useState(false);
  const [panelWidth, setPanelWidth] = useState(DEFAULT_PANEL_WIDTH);
  const [loaded, setLoaded] = useState(mode === 'new');
  const [infoDialogOpen, setInfoDialogOpen] = useState(false);
  const [draftAgentSpecName, setDraftAgentSpecName] = useState(editName);
  const [draftDescription, setDraftDescription] = useState('');
  const [labels, setLabels] = useState<Record<string, string>>({});
  const [draftLabels, setDraftLabels] = useState<Record<string, string>>({});
  const [savedLabels, setSavedLabels] = useState<Record<string, string>>({});
  const [virtualFolders, setVirtualFolders] = useState<Set<string>>(new Set());
  const [createNodeMode, setCreateNodeMode] = useState<CreateNodeMode>('file');
  const [createNodeOpen, setCreateNodeOpen] = useState(false);
  const [createNodeType, setCreateNodeType] = useState<ResourceType>('other');
  const [createNodePath, setCreateNodePath] = useState('');
  const [createNodeFallbackType, setCreateNodeFallbackType] = useState<ResourceType>('other');
  const [draftVersion, setDraftVersion] = useState('');

  const dragRef = useRef<{ startX: number; startWidth: number } | null>(null);
  const hasAutoOpenedInfoDialogRef = useRef(false);

  // ===== Load existing data in edit mode =====
  useEffect(() => {
    if ((mode !== 'edit' && mode !== 'version') || !editName) return;
    let cancelled = false;
    (async () => {
      try {
        const detailRes = await agentSpecApi.getDetail({
          namespaceId,
          agentSpecName: editName,
        });
        if (cancelled) return;
        const detail = detailRes.data;
        const plan = planAgentSpecEditorVersionMode({
          mode,
          editingVersion: detail.editingVersion,
          currentVersion: detail.labels?.latest,
          sourceVersion,
        });

        let versionToLoad = plan.versionToLoad;
        if (plan.shouldCreateDraft) {
          const createRes = await agentSpecApi.createDraft({
            namespaceId,
            agentSpecName: editName,
            basedOnVersion: plan.basedOnVersion,
          });
          if (cancelled) return;
          versionToLoad = createRes.data;
          toast.success(t('agentSpec.createDraftSuccess'));
        }

        if (!versionToLoad) {
          toast.error(t('agentSpec.loadError'));
          setLoaded(true);
          return;
        }

        const specRes = await agentSpecApi.getVersion({
          namespaceId,
          agentSpecName: editName,
          version: versionToLoad,
        });
        if (cancelled) return;

        const spec = specRes.data;
        const deserialized = deserializeToFiles(
          spec.content || '{}',
          spec.resource || {},
        );
        const nextDescription = spec.description || getAgentSpecDescription(spec.content || '{}');
        const nextLabels = detail.labels || {};
        setFiles(deserialized);
        setAgentSpecName(spec.name);
        setDraftAgentSpecName(spec.name);
        setDescription(nextDescription);
        setDraftDescription(nextDescription);
        setDraftVersion(versionToLoad);
        setLabels(nextLabels);
        setDraftLabels(nextLabels);
        setSavedLabels(nextLabels);
        setVirtualFolders(new Set());
        setLoaded(true);
      } catch {
        if (!cancelled) {
          toast.error(t('agentSpec.loadError'));
          setLoaded(true);
        }
      }
    })();
    return () => { cancelled = true; };
  }, [mode, editName, namespaceId, sourceVersion, t]);

  useEffect(() => {
    if (mode !== 'new' || !loaded || hasAutoOpenedInfoDialogRef.current) {
      return;
    }

    hasAutoOpenedInfoDialogRef.current = true;
    setDraftAgentSpecName(agentSpecName);
    setDraftDescription(description);
    setDraftLabels(labels);
    setInfoDialogOpen(true);
  }, [mode, loaded, agentSpecName, description, labels]);

  // ===== Build file tree nodes from files map =====
  const treeNodes: FileTreeNode[] = useMemo(() => {
    // Convert files map to resource map for buildFileTree
    const resources: Record<string, AgentSpecResource> = {};
    for (const [key, file] of files) {
      if (key === MANIFEST_KEY) continue;
      resources[key] = {
        name: key,
        type: file.type as AgentSpecResource['type'],
        content: file.content,
        metadata: null,
      };
    }
    const manifestContent = files.get(MANIFEST_KEY)?.content || '{}';
    return buildFileTree(resources, manifestContent, [...virtualFolders]);
  }, [files, virtualFolders]);

  // ===== Current file content & language =====
  const { fileContent, language } = useMemo(() => {
    if (selectedKey === MANIFEST_KEY) {
      return {
        fileContent: files.get(MANIFEST_KEY)?.content || '{}',
        language: getLanguageFromFileName(MANIFEST_KEY),
      };
    }
    // Try direct key match first
    const direct = files.get(selectedKey);
    if (direct) {
      return {
        fileContent: direct.content,
        language: getLanguageFromFileName(selectedKey),
      };
    }
    // selectedKey format from tree: "type/name" — find by matching
    for (const [key, file] of files) {
      if (key === MANIFEST_KEY) continue;
      if (`${file.type}/${key}` === selectedKey) {
        return {
          fileContent: file.content,
          language: getLanguageFromFileName(key),
        };
      }
    }
    return { fileContent: '', language: 'plaintext' };
  }, [selectedKey, files]);

  // ===== Editor change handler =====
  const handleEditorChange = useCallback(
    (value: string | undefined) => {
      const newValue = value ?? '';
      setFiles((prev) => {
        const next = new Map(prev);
        if (selectedKey === MANIFEST_KEY) {
          next.set(MANIFEST_KEY, { ...next.get(MANIFEST_KEY)!, content: newValue });
          const nextDescription = getAgentSpecDescription(newValue);
          setDescription(nextDescription);
          setDraftDescription(nextDescription);
        } else {
          // Try direct key
          if (next.has(selectedKey)) {
            const f = next.get(selectedKey)!;
            next.set(selectedKey, { ...f, content: newValue });
          } else {
            // Match by type/name pattern
            for (const [key, file] of next) {
              if (key === MANIFEST_KEY) continue;
              if (`${file.type}/${key}` === selectedKey) {
                next.set(key, { ...file, content: newValue });
                break;
              }
            }
          }
        }
        return next;
      });
      setModified(true);
    },
    [selectedKey],
  );

  // ===== File tree operations =====
  const openCreateNodeDialog = useCallback(
    (modeValue: CreateNodeMode, parentKey?: string) => {
      const { resourceType, relativeDir } = getContextFromTreeKey(parentKey ?? selectedKey);
      const isPresetResourceType = RESOURCE_TYPES.includes(resourceType as (typeof RESOURCE_TYPES)[number]);
      const defaultName = modeValue === 'file' ? 'untitled' : 'new-folder';
      setCreateNodeMode(modeValue);
      setCreateNodeFallbackType(resourceType || 'other');
      setCreateNodeType(isPresetResourceType ? resourceType : CUSTOM_RESOURCE_TYPE);
      setCreateNodePath(
        isPresetResourceType
          ? (relativeDir ? `${relativeDir}/${defaultName}` : defaultName)
          : [resourceType, relativeDir, defaultName].filter(Boolean).join('/'),
      );
      setCreateNodeOpen(true);
    },
    [selectedKey],
  );

  const handleCreateFile = useCallback((parentKey?: string) => {
    openCreateNodeDialog('file', parentKey);
  }, [openCreateNodeDialog]);

  const handleCreateFolder = useCallback((parentKey?: string) => {
    openCreateNodeDialog('folder', parentKey);
  }, [openCreateNodeDialog]);

  const folderExists = useCallback((resourceType: ResourceType, relativePath: string) => {
    const folderKey = `${resourceType}/${relativePath}`;
    if (virtualFolders.has(folderKey)) {
      return true;
    }

    for (const [fileName, file] of files) {
      if (fileName === MANIFEST_KEY || file.type !== resourceType) {
        continue;
      }
      if (getAncestorFolders(fileName).includes(relativePath)) {
        return true;
      }
    }

    return false;
  }, [files, virtualFolders]);

  const handleConfirmCreateNode = useCallback(() => {
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
      if (normalizedPath === MANIFEST_KEY) {
        toast.error(t('agentSpec.invalidFileName'));
        return;
      }
      if (files.has(normalizedPath)) {
        toast.error(t('agentSpec.fileExists'));
        return;
      }

      setFiles((prev) => {
        const next = new Map(prev);
        next.set(normalizedPath, { content: '', type: normalizedType });
        return next;
      });
      setSelectedKey(`${normalizedType}/${normalizedPath}`);
    } else {
      if (folderExists(normalizedType, normalizedPath)) {
        toast.error(t('agentSpec.folderExists'));
        return;
      }

      setVirtualFolders((prev) => {
        const next = new Set(prev);
        next.add(`${normalizedType}/${normalizedPath}`);
        return next;
      });
    }

    setModified(true);
    setCreateNodeOpen(false);
  }, [createNodeFallbackType, createNodeMode, createNodePath, createNodeType, files, folderExists, t]);

  const handleDeleteNode = useCallback(
    (key: string, nodeType: 'file' | 'folder') => {
      // Prevent deleting manifest.json
      if (key === MANIFEST_KEY) return;

      if (nodeType === 'folder') {
        const normalizedKey = key.replace(/\/$/, '');
        const [resourceType, ...segments] = normalizedKey.split('/');
        const relativePath = segments.join('/');
        if (!resourceType || !relativePath) {
          return;
        }

        setFiles((prev) => {
          const next = new Map(prev);
          for (const [mapKey, file] of next) {
            if (mapKey === MANIFEST_KEY || file.type !== resourceType) continue;
            if (mapKey === relativePath || mapKey.startsWith(`${relativePath}/`)) {
              next.delete(mapKey);
            }
          }
          return next;
        });

        setVirtualFolders((prev) => {
          const next = new Set<string>();
          for (const folder of prev) {
            if (folder !== normalizedKey && !folder.startsWith(`${normalizedKey}/`)) {
              next.add(folder);
            }
          }
          return next;
        });

        if (
          selectedKey &&
          selectedKey.startsWith(`${resourceType}/${relativePath}`)
        ) {
          setSelectedKey(MANIFEST_KEY);
        }
      } else {
        setFiles((prev) => {
          const next = new Map(prev);
          for (const [mapKey, file] of next) {
            if (mapKey === MANIFEST_KEY) continue;
            if (`${file.type}/${mapKey}` === key) {
              next.delete(mapKey);
              break;
            }
          }
          return next;
        });
        if (selectedKey === key) {
          setSelectedKey(MANIFEST_KEY);
        }
      }

      setModified(true);
    },
    [selectedKey],
  );

  const handleRenameFile = useCallback(
    (key: string, newName: string) => {
      if (key === MANIFEST_KEY) return;
      setFiles((prev) => {
        const next = new Map(prev);
        for (const [mapKey, file] of next) {
          if (mapKey === MANIFEST_KEY) continue;
          if (`${file.type}/${mapKey}` === key) {
            const parentPath = mapKey.includes('/')
              ? `${mapKey.split('/').slice(0, -1).join('/')}/`
              : '';
            const nextPath = `${parentPath}${newName}`;
            if (next.has(nextPath)) {
              toast.error(t('agentSpec.fileExists'));
              return prev;
            }
            next.delete(mapKey);
            next.set(nextPath, file);
            break;
          }
        }
        return next;
      });
      // Update selected key if the renamed file was selected
      if (selectedKey === key) {
        // Find the type to reconstruct the key
        for (const [mapKey, file] of files) {
          if (mapKey === MANIFEST_KEY) continue;
          if (`${file.type}/${mapKey}` === key) {
            const parentPath = mapKey.includes('/')
              ? `${mapKey.split('/').slice(0, -1).join('/')}/`
              : '';
            setSelectedKey(`${file.type}/${parentPath}${newName}`);
            break;
          }
        }
      }
      setModified(true);
    },
    [selectedKey, files, t],
  );

  // Rename a sub-folder.
  // Folder key format: "type/oldPath/" – rename the last segment.
  // Updates all files & virtual folders under that path.
  const handleRenameFolder = useCallback(
    (folderKey: string, newName: string) => {
      const normalized = folderKey.replace(/\/$/, ''); // "type/old" or just "type"
      const [resourceType, ...segments] = normalized.split('/');
      if (!resourceType) return;

      // Top-level folder rename → change resource type
      if (segments.length === 0) {
        const oldType = resourceType;
        const newType = newName;
        setFiles((prev) => {
          const next = new Map<string, typeof prev extends Map<string, infer V> ? V : never>();
          for (const [mapKey, file] of prev) {
            if (file.type === oldType) {
              next.set(mapKey, { ...file, type: newType });
            } else {
              next.set(mapKey, file);
            }
          }
          return next;
        });
        setVirtualFolders((prev) => {
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
        setModified(true);
        return;
      }

      // Sub-folder rename
      const oldRelPath = segments.join('/');
      const parentSegments = segments.slice(0, -1);
      const newRelPath = [...parentSegments, newName].join('/');

      setFiles((prev) => {
        const next = new Map<string, typeof prev extends Map<string, infer V> ? V : never>();
        for (const [mapKey, file] of prev) {
          if (mapKey === MANIFEST_KEY || file.type !== resourceType) {
            next.set(mapKey, file);
            continue;
          }
          if (mapKey === oldRelPath || mapKey.startsWith(`${oldRelPath}/`)) {
            const updatedKey = newRelPath + mapKey.slice(oldRelPath.length);
            next.set(updatedKey, file);
          } else {
            next.set(mapKey, file);
          }
        }
        return next;
      });

      setVirtualFolders((prev) => {
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

      setModified(true);
    },
    [],
  );

  // ===== Drag handle for resizable panel =====
  const handleMouseDown = useCallback(
    (e: React.MouseEvent) => {
      e.preventDefault();
      dragRef.current = { startX: e.clientX, startWidth: panelWidth };

      const handleMouseMove = (ev: MouseEvent) => {
        if (!dragRef.current) return;
        const delta = ev.clientX - dragRef.current.startX;
        const newWidth = Math.min(
          MAX_PANEL_WIDTH,
          Math.max(MIN_PANEL_WIDTH, dragRef.current.startWidth + delta),
        );
        setPanelWidth(newWidth);
      };

      const handleMouseUp = () => {
        dragRef.current = null;
        document.removeEventListener('mousemove', handleMouseMove);
        document.removeEventListener('mouseup', handleMouseUp);
      };

      document.addEventListener('mousemove', handleMouseMove);
      document.addEventListener('mouseup', handleMouseUp);
    },
    [panelWidth],
  );

  const handleOpenInfoDialog = useCallback(() => {
    setDraftAgentSpecName(agentSpecName);
    setDraftDescription(description);
    setDraftLabels(labels);
    setInfoDialogOpen(true);
  }, [agentSpecName, description, labels]);

  const createNodePathPrefix = useMemo(() => {
    if (RESOURCE_TYPES.includes(createNodeType as (typeof RESOURCE_TYPES)[number])) {
      return `${createNodeType}/`;
    }

    return '';
  }, [createNodeType]);

  const handleCreateNodeTypeChange = useCallback((value: string) => {
    if (value === CUSTOM_RESOURCE_TYPE) {
      const normalizedPath = normalizeRelativePath(createNodePath);
      const prefix = RESOURCE_TYPES.includes(createNodeType as (typeof RESOURCE_TYPES)[number])
        ? `${createNodeType}/`
        : '';

      setCreateNodeType(value);
      setCreateNodePath(prefix && normalizedPath ? `${prefix}${normalizedPath}` : normalizedPath);
      return;
    }

    const normalizedPath = normalizeRelativePath(createNodePath);
    const currentPrefix = RESOURCE_TYPES.includes(createNodeType as (typeof RESOURCE_TYPES)[number])
      ? `${createNodeType}/`
      : '';
    const nextPrefix = `${value}/`;
    const nextPath = normalizedPath.startsWith(nextPrefix)
      ? normalizedPath.slice(nextPrefix.length)
      : currentPrefix && normalizedPath.startsWith(currentPrefix)
        ? normalizedPath.slice(currentPrefix.length)
        : normalizedPath;

    setCreateNodeType(value);
    setCreateNodeFallbackType(value);
    setCreateNodePath(nextPath);
  }, [createNodePath, createNodeType]);

  const handleSaveInfoDialog = useCallback(() => {
    const trimmedName = draftAgentSpecName.trim();
    const trimmedDescription = draftDescription.trim();
    if (!trimmedName) {
      toast.error(t('agentSpec.nameRequired'));
      return;
    }
    if (
      trimmedName !== agentSpecName
      || trimmedDescription !== description
      || JSON.stringify(draftLabels) !== JSON.stringify(labels)
    ) {
      setAgentSpecName(trimmedName);
      setDescription(trimmedDescription);
      setFiles((prev) => {
        const next = new Map(prev);
        const manifestFile = next.get(MANIFEST_KEY);
        if (manifestFile) {
          next.set(MANIFEST_KEY, {
            ...manifestFile,
            content: syncManifestDescription(manifestFile.content, trimmedDescription),
          });
        }
        return next;
      });
      setLabels(draftLabels);
      setModified(true);
    }
    setInfoDialogOpen(false);
  }, [agentSpecName, description, draftAgentSpecName, draftDescription, draftLabels, labels, t]);

  const persistDraft = useCallback(async (showSaveToast = true) => {
    if (!agentSpecName.trim()) {
      toast.error(t('agentSpec.nameRequired'));
      return null;
    }
    try {
      const filesToPersist = new Map(files);
      const manifestFile = filesToPersist.get(MANIFEST_KEY);
      if (manifestFile) {
        filesToPersist.set(MANIFEST_KEY, {
          ...manifestFile,
          content: syncManifestDescription(manifestFile.content, description),
        });
      }
      const { content, resource } = serializeFileTree(filesToPersist);
      const trimmedName = agentSpecName.trim();
      const agentSpecCard = JSON.stringify({
        namespaceId,
        name: trimmedName,
        description,
        content,
        resource,
      });
      setFiles(filesToPersist);
      const updateRes = await agentSpecApi.updateDraft({
        namespaceId,
        agentSpecCard,
      });
      // Backend returns "ok", not the actual version — keep existing draftVersion
      const nextDraftVersion = draftVersion || updateRes.data;
      if (JSON.stringify(labels) !== JSON.stringify(savedLabels)) {
        await agentSpecApi.updateLabels({
          namespaceId,
          agentSpecName: trimmedName,
          labels: JSON.stringify(labels),
        });
        setSavedLabels(labels);
      }
      if (nextDraftVersion) {
        setDraftVersion(nextDraftVersion);
      }
      if (showSaveToast) {
        toast.success(t('agentSpec.saveSuccess'));
      }
      setModified(false);
      return {
        name: trimmedName,
        draftVersion: nextDraftVersion,
      };
    } catch {
      // axios interceptor handles error toast
      return null;
    }
  }, [agentSpecName, description, draftVersion, files, labels, namespaceId, savedLabels, t]);

  // ===== Save handler =====
  const handleSave = useCallback(async () => {
    setSaving(true);
    try {
      const result = await persistDraft(true);
      if (!result) {
        return;
      }
      const params = new URLSearchParams({ namespaceId });
      navigate(`/agentspec/${encodeURIComponent(result.name)}?${params}`);
    } finally {
      setSaving(false);
    }
  }, [navigate, namespaceId, persistDraft]);

  // ===== Loading state =====
  if (!loaded) {
    return (
      <div className="flex items-center justify-center h-[60vh] text-muted-foreground text-sm">
        {t('common.loading')}...
      </div>
    );
  }

  // ===== Resolve display name for selected file =====
  const selectedFileName = selectedKey === MANIFEST_KEY
    ? MANIFEST_KEY
    : selectedKey.includes('/')
      ? selectedKey.split('/').pop() || selectedKey
      : selectedKey;

  const isEditMode = mode === 'edit';
  const isVersionMode = mode === 'version';

  return (
    <div className="flex h-[calc(100vh-88px)] min-h-[720px] flex-col gap-4">
      <div className="flex items-center gap-3 shrink-0">
        <Button
          variant="ghost"
          size="sm"
          className="h-7 gap-1.5 text-muted-foreground hover:text-foreground -ml-2"
          onClick={() => navigate(-1)}
        >
          <ArrowLeft className="h-3.5 w-3.5" />
          {t('agentSpec.backToList')}
        </Button>

        <div className="flex items-center gap-2">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-sky-500 to-cyan-400">
            <Package className="h-4 w-4 text-white" />
          </div>
          <h1 className="text-lg font-bold">
            {isEditMode
              ? t('agentSpec.editAgentSpec')
              : isVersionMode
                ? t('agentSpec.newVersion')
                : t('agentSpec.createAgentSpec')}
          </h1>
        </div>
      </div>

      <Card className="flex-1 overflow-hidden py-0 gap-0 min-h-0">
        <div className="border-b bg-muted/20 px-5 py-3">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <div className="min-w-0 space-y-1">
              <div className="flex items-center gap-2 min-w-0">
                <h2 className="truncate text-lg font-semibold tracking-tight text-foreground">
                  {agentSpecName || t('agentSpec.unnamedAgentSpec')}
                </h2>
                <Badge variant="outline" className="h-5 px-2 text-[10px] font-mono uppercase">
                  {isEditMode
                    ? t('agentSpec.modeEdit')
                    : isVersionMode
                      ? t('agentSpec.modeVersion')
                      : t('agentSpec.modeNew')}
                </Badge>
                {modified && (
                  <span className="text-[11px] font-medium text-amber-600 dark:text-amber-400">
                    {t('agentSpec.unsaved')}
                  </span>
                )}
              </div>

            </div>

            <div className="flex shrink-0 items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                className="gap-1.5"
                onClick={handleOpenInfoDialog}
              >
                <Settings2 className="h-3.5 w-3.5" />
                {t('agentSpec.editBasicInfo')}
              </Button>
              <Button
                size="sm"
                className="gap-1.5"
                onClick={handleSave}
                disabled={saving || !agentSpecName.trim()}
              >
                <Save className="h-3.5 w-3.5" />
                {saving ? t('common.loading') : t('common.save')}
              </Button>
            </div>
          </div>
        </div>

        <CardContent className="flex-1 min-h-0 p-0">
          <div className="flex h-full min-h-0">
            <div style={{ width: panelWidth }} className="shrink-0 border-r">
              <FileTreePanel
                nodes={treeNodes}
                selectedKey={selectedKey}
                onSelect={setSelectedKey}
                editable={true}
                onCreateFile={handleCreateFile}
                onCreateFolder={handleCreateFolder}
                onDeleteNode={handleDeleteNode}
                onRenameFile={handleRenameFile}
                onRenameFolder={handleRenameFolder}
              />
            </div>

            <div
              className="w-1 cursor-col-resize bg-border hover:bg-primary/30 transition-colors shrink-0"
              onMouseDown={handleMouseDown}
              role="separator"
              aria-orientation="vertical"
              aria-label={t('agentSpec.resizeFileTreePanel')}
              tabIndex={0}
            />

            <div className="flex-1 min-w-0">
              <Editor
                language={language}
                value={fileContent}
                theme="vs"
                options={{
                  minimap: { enabled: false },
                  lineNumbers: 'on',
                  scrollBeyondLastLine: false,
                  wordWrap: 'on',
                  automaticLayout: true,
                  fontSize: 13,
                  tabSize: 2,
                }}
                onChange={handleEditorChange}
                loading={
                  <div className="flex items-center justify-center h-full text-muted-foreground text-sm">
                    {t('agentSpec.editorLoading')}
                  </div>
                }
              />
            </div>
          </div>
        </CardContent>

        <div className="flex items-center justify-between border-t bg-muted/20 px-3 py-0.5 text-[11px] leading-none text-muted-foreground">
          <div className="flex items-center gap-2">
            <span>{selectedFileName}</span>
            <span>{language}</span>
            <span>UTF-8</span>
          </div>
          {modified && <span className="leading-none text-amber-600 dark:text-amber-400">{t('agentSpec.modified')}</span>}
        </div>
      </Card>

      <Dialog open={infoDialogOpen} onOpenChange={setInfoDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('agentSpec.basicInfo')}</DialogTitle>
          </DialogHeader>

          <div className="space-y-4">
            <div className="space-y-2.5">
              <label className="text-sm font-medium">{t('agentSpec.agentSpecName')}</label>
              <Input
                value={draftAgentSpecName}
                onChange={(e) => setDraftAgentSpecName(e.target.value)}
                placeholder={t('agentSpec.namePlaceholder')}
                disabled={isEditMode || isVersionMode}
                className="bg-transparent"
              />
            </div>

            <div className="space-y-2.5">
              <Label className="text-sm font-medium">{t('agentSpec.description')}</Label>
              <Textarea
                value={draftDescription}
                onChange={(e) => setDraftDescription(e.target.value)}
                placeholder={t('agentSpec.descriptionPlaceholder')}
                className="min-h-24 resize-y bg-transparent"
              />
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setInfoDialogOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleSaveInfoDialog} disabled={!draftAgentSpecName.trim()}>
              {t('common.confirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

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
              <Select value={RESOURCE_TYPES.includes(createNodeType as (typeof RESOURCE_TYPES)[number]) ? createNodeType : CUSTOM_RESOURCE_TYPE} onValueChange={handleCreateNodeTypeChange}>
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
    </div>
  );
}
