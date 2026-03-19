import { useState, useCallback, useMemo, useRef, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { Editor } from '@monaco-editor/react';
import { Save, ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { FileTreePanel } from '../agentSpecManagement/components/FileTreePanel';
import type { FileTreeNode } from '../agentSpecManagement/components/FileTreePanel';
import { buildFileTree } from '../agentSpecManagement/components/file-tree-utils';
import { getLanguageFromFileName } from '../agentSpecManagement/components/resource-viewer-utils';
import { agentSpecApi } from '@/api/agentspec';
import type { AgentSpecResource } from '@/types/agentspec';
import { serializeFileTree, deserializeToFiles } from './editor-utils';
import type { EditorFile } from './editor-utils';

// ===== Constants =====

const MANIFEST_KEY = 'manifest.json';
const MIN_PANEL_WIDTH = 160;
const MAX_PANEL_WIDTH = 480;
const DEFAULT_PANEL_WIDTH = 220;

// ===== Component =====

export default function NewAgentSpecPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const mode = searchParams.get('mode') || 'new';
  const editName = searchParams.get('name') || '';
  const namespaceId = searchParams.get('namespaceId') || 'public';

  // File state: Map<fileName, EditorFile>
  const [files, setFiles] = useState<Map<string, EditorFile>>(() => {
    const m = new Map<string, EditorFile>();
    m.set(MANIFEST_KEY, { content: '{}', type: 'manifest' });
    return m;
  });
  const [selectedKey, setSelectedKey] = useState<string>(MANIFEST_KEY);
  const [agentSpecName, setAgentSpecName] = useState(editName);
  const [saving, setSaving] = useState(false);
  const [modified, setModified] = useState(false);
  const [panelWidth, setPanelWidth] = useState(DEFAULT_PANEL_WIDTH);
  const [loaded, setLoaded] = useState(mode === 'new');

  const dragRef = useRef<{ startX: number; startWidth: number } | null>(null);

  // ===== Load existing data in edit mode =====
  useEffect(() => {
    if (mode !== 'edit' || !editName) return;
    let cancelled = false;
    (async () => {
      try {
        const res = await agentSpecApi.getDetail({
          namespaceId,
          agentSpecName: editName,
        });
        if (cancelled) return;
        const detail = res.data.data;
        const spec = detail.agentSpec;
        const deserialized = deserializeToFiles(
          spec.content || '{}',
          spec.resource || {},
        );
        setFiles(deserialized);
        setAgentSpecName(spec.name);
        setLoaded(true);
      } catch {
        if (!cancelled) {
          toast.error(t('agentSpec.loadError'));
          setLoaded(true);
        }
      }
    })();
    return () => { cancelled = true; };
  }, [mode, editName, namespaceId, t]);

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
    return buildFileTree(resources, manifestContent);
  }, [files]);

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
  const handleCreateFile = useCallback(() => {
    const baseName = 'untitled';
    let name = baseName;
    let counter = 1;
    while (files.has(name)) {
      name = `${baseName}-${counter}`;
      counter++;
    }
    setFiles((prev) => {
      const next = new Map(prev);
      next.set(name, { content: '', type: 'other' });
      return next;
    });
    setSelectedKey(`other/${name}`);
    setModified(true);
  }, [files]);

  const handleDeleteFile = useCallback(
    (key: string) => {
      // Prevent deleting manifest.json
      if (key === MANIFEST_KEY) return;
      setFiles((prev) => {
        const next = new Map(prev);
        // key from tree is "type/name", actual map key is just name
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
            next.delete(mapKey);
            next.set(newName, file);
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
            setSelectedKey(`${file.type}/${newName}`);
            break;
          }
        }
      }
      setModified(true);
    },
    [selectedKey, files],
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

  // ===== Save handler =====
  const handleSave = useCallback(async () => {
    if (!agentSpecName.trim()) {
      toast.error(t('agentSpec.nameRequired'));
      return;
    }
    setSaving(true);
    try {
      const { content, resource } = serializeFileTree(files);
      if (mode === 'edit') {
        await agentSpecApi.updateDraft({
          namespaceId,
          agentSpecName: agentSpecName.trim(),
          content,
          resource: JSON.stringify(resource),
        });
      } else {
        // Create draft first, then update with content
        await agentSpecApi.createDraft({
          namespaceId,
          agentSpecName: agentSpecName.trim(),
        });
        await agentSpecApi.updateDraft({
          namespaceId,
          agentSpecName: agentSpecName.trim(),
          content,
          resource: JSON.stringify(resource),
        });
      }
      toast.success(t('agentSpec.saveSuccess'));
      setModified(false);
      navigate(`/agentspec/${encodeURIComponent(agentSpecName.trim())}`);
    } catch {
      // axios interceptor handles error toast
    } finally {
      setSaving(false);
    }
  }, [agentSpecName, files, mode, namespaceId, navigate, t]);

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

  return (
    <div className="flex flex-col h-[calc(100vh-64px)]">
      {/* ===== Top Toolbar ===== */}
      <div className="flex items-center gap-3 px-4 py-2 border-b bg-muted/30 shrink-0">
        <Button
          variant="ghost"
          size="sm"
          className="h-7 gap-1.5 text-muted-foreground hover:text-foreground -ml-1"
          onClick={() => navigate(-1)}
        >
          <ArrowLeft className="h-3.5 w-3.5" />
        </Button>

        {mode === 'new' ? (
          <Input
            value={agentSpecName}
            onChange={(e) => setAgentSpecName(e.target.value)}
            placeholder={t('agentSpec.namePlaceholder')}
            className="h-7 w-56 text-sm font-medium"
          />
        ) : (
          <span className="text-sm font-semibold">{agentSpecName}</span>
        )}

        <Badge variant="outline" className="text-[10px] h-5 font-mono">
          {mode === 'edit' ? 'draft' : 'new'}
        </Badge>

        {modified && (
          <span className="text-[10px] text-amber-600 dark:text-amber-400">
            {t('agentSpec.unsaved')}
          </span>
        )}

        <div className="ml-auto">
          <Button
            size="sm"
            className="h-7 gap-1.5"
            onClick={handleSave}
            disabled={saving || !agentSpecName.trim()}
          >
            <Save className="h-3.5 w-3.5" />
            {saving ? t('common.loading') : t('common.save')}
          </Button>
        </div>
      </div>

      {/* ===== Main Content: File Tree + Editor ===== */}
      <div className="flex flex-1 min-h-0">
        {/* Left: File Tree Panel */}
        <div style={{ width: panelWidth }} className="shrink-0">
          <FileTreePanel
            nodes={treeNodes}
            selectedKey={selectedKey}
            onSelect={setSelectedKey}
            editable={true}
            onCreateFile={handleCreateFile}
            onDeleteFile={handleDeleteFile}
            onRenameFile={handleRenameFile}
          />
        </div>

        {/* Drag Handle */}
        <div
          className="w-1 cursor-col-resize bg-border hover:bg-primary/30 transition-colors shrink-0"
          onMouseDown={handleMouseDown}
          role="separator"
          aria-orientation="vertical"
          aria-label="Resize file tree panel"
          tabIndex={0}
        />

        {/* Right: Monaco Editor */}
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
                Loading editor...
              </div>
            }
          />
        </div>
      </div>

      {/* ===== Bottom Status Bar ===== */}
      <div className="flex items-center gap-4 px-4 py-1 border-t bg-muted/30 text-[11px] text-muted-foreground shrink-0">
        <span>{selectedFileName}</span>
        <span>{language}</span>
        <span>UTF-8</span>
        {modified && <span className="text-amber-600 dark:text-amber-400">Modified</span>}
      </div>
    </div>
  );
}
