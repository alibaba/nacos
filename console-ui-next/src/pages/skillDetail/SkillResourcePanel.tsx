import { useState, useCallback, useMemo, useRef, useEffect } from 'react';
import { Editor } from '@monaco-editor/react';
import { useTranslation } from 'react-i18next';
import { FileWarning, Image as ImageIcon } from 'lucide-react';
import { cn } from '@/lib/utils';
import type { SkillResource } from '@/types/skill';
import { FileTreePanel } from '../agentSpecManagement/components/FileTreePanel';
import {
  buildSkillFileTree,
  getLanguageFromFileName,
  getFileCategory,
  resolveResourceByKey,
} from './skill-resource-utils';

// ===== Constants =====

const DEFAULT_PANEL_WIDTH = 220;
const MIN_PANEL_WIDTH = 160;
const MAX_PANEL_WIDTH = 360;
const PANEL_HEIGHT = 580;

// ===== Props =====

export interface SkillResourcePanelProps {
  resources: Record<string, SkillResource>;
  editable: boolean;
  onChange?: (resources: Record<string, SkillResource>) => void;
  className?: string;
}

// ===== Component =====

export function SkillResourcePanel({
  resources,
  editable,
  onChange,
  className,
}: SkillResourcePanelProps) {
  const { t } = useTranslation();
  const [selectedKey, setSelectedKey] = useState<string>('');
  const [panelWidth, setPanelWidth] = useState(DEFAULT_PANEL_WIDTH);
  const dragRef = useRef<{ startX: number; startWidth: number } | null>(null);

  const nodes = useMemo(() => buildSkillFileTree(resources), [resources]);

  // Auto-select first file when resources change or selection becomes invalid
  useEffect(() => {
    if (nodes.length === 0) {
      setSelectedKey('');
      return;
    }
    if (selectedKey) {
      const found = resolveResourceByKey(resources, selectedKey);
      if (found) return;
    }
    const first = findFirstFile(nodes);
    if (first) setSelectedKey(first.key);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [nodes, resources]);

  // Drag resize
  useEffect(() => {
    const onMove = (e: MouseEvent) => {
      if (!dragRef.current) return;
      const dx = e.clientX - dragRef.current.startX;
      setPanelWidth(
        Math.min(
          MAX_PANEL_WIDTH,
          Math.max(MIN_PANEL_WIDTH, dragRef.current.startWidth + dx),
        ),
      );
    };
    const onUp = () => {
      dragRef.current = null;
    };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
    return () => {
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
    };
  }, []);

  // Resolve current selection
  const resolved = useMemo(
    () => (selectedKey ? resolveResourceByKey(resources, selectedKey) : null),
    [selectedKey, resources],
  );

  const fileCategory = useMemo(
    () => (resolved ? getFileCategory(resolved.resource.name) : 'text'),
    [resolved],
  );

  const language = useMemo(
    () =>
      resolved ? getLanguageFromFileName(resolved.resource.name) : 'plaintext',
    [resolved],
  );

  // Editor content change
  const handleEditorChange = useCallback(
    (value: string | undefined) => {
      if (!onChange || !resolved) return;
      onChange({
        ...resources,
        [resolved.mapKey]: { ...resolved.resource, content: value ?? '' },
      });
    },
    [onChange, resolved, resources],
  );

  // File tree: create file
  //
  // parentKey is a folder tree-key like "skill/" or "skill/subfolder/".
  // We treat the full folder key (minus trailing /) as the resource `type`,
  // and `name` is only the bare filename.  This avoids `/` in `name` which
  // the server's generateResourceId cannot handle correctly.
  const handleCreateFile = useCallback(
    (parentKey?: string) => {
      if (!onChange) return;
      const folderType = parentKey ? parentKey.replace(/\/$/, '') : '';
      const name = 'untitled.txt';
      const newResource: SkillResource = {
        name,
        type: folderType,
        content: '',
        metadata: null,
      };
      const newMapKey = `${Date.now()}_${name}`;
      const newTreeKey = folderType ? `${folderType}/${name}` : name;
      onChange({ ...resources, [newMapKey]: newResource });
      setSelectedKey(newTreeKey);
    },
    [onChange, resources],
  );

  // File tree: create folder
  //
  // Creates a real file (untitled.txt) inside a new subfolder.
  // The subfolder path is encoded in `type` (e.g. "skill/new_folder"),
  // keeping `name` as just the bare filename.
  const handleCreateFolder = useCallback(
    (parentKey?: string) => {
      if (!onChange) return;
      const parentType = parentKey ? parentKey.replace(/\/$/, '') : '';
      const folderName = 'new_folder';
      const fileName = 'untitled.txt';
      const newType = parentType ? `${parentType}/${folderName}` : folderName;
      const newResource: SkillResource = {
        name: fileName,
        type: newType,
        content: '',
        metadata: null,
      };
      const newMapKey = `${Date.now()}_${fileName}`;
      const newTreeKey = `${newType}/${fileName}`;
      onChange({ ...resources, [newMapKey]: newResource });
      setSelectedKey(newTreeKey);
    },
    [onChange, resources],
  );

  // File tree: delete node
  const handleDeleteNode = useCallback(
    (key: string, nodeType: 'file' | 'folder') => {
      if (!onChange) return;
      const newResources = { ...resources };
      if (nodeType === 'file') {
        const entry = resolveResourceByKey(resources, key);
        if (entry) {
          delete newResources[entry.mapKey];
        }
      } else {
        for (const [mk, res] of Object.entries(resources)) {
          const rk = res.type ? `${res.type}/${res.name}` : res.name;
          if (rk.startsWith(key)) {
            delete newResources[mk];
          }
        }
      }
      onChange(newResources);
      if (
        key === selectedKey ||
        (nodeType === 'folder' && selectedKey.startsWith(key))
      ) {
        setSelectedKey('');
      }
    },
    [onChange, resources, selectedKey],
  );

  // File tree: rename file
  const handleRenameFile = useCallback(
    (key: string, newName: string) => {
      if (!onChange) return;
      const entry = resolveResourceByKey(resources, key);
      if (!entry) return;
      const newResources = { ...resources };
      delete newResources[entry.mapKey];
      const parts = entry.resource.name.split('/');
      parts[parts.length - 1] = newName;
      const updatedName = parts.join('/');
      const updatedResource = { ...entry.resource, name: updatedName };
      const newMapKey = `${Date.now()}_${newName}`;
      newResources[newMapKey] = updatedResource;
      onChange(newResources);
      const newTreeKey = updatedResource.type
        ? `${updatedResource.type}/${updatedName}`
        : updatedName;
      setSelectedKey(newTreeKey);
    },
    [onChange, resources],
  );

  // File tree: rename folder
  //
  // Folder keys look like "skill/subfolder/" – the part without the trailing
  // slash is the *type* used by resources inside that folder.  Renaming the
  // folder means rewriting the last segment of every matching `type`.
  const handleRenameFolder = useCallback(
    (folderKey: string, newName: string) => {
      if (!onChange) return;
      // e.g. "skill/old_folder/" → "skill/old_folder"
      const oldType = folderKey.replace(/\/$/, '');
      const segments = oldType.split('/');
      segments[segments.length - 1] = newName;
      const newType = segments.join('/');

      const newResources: typeof resources = {};
      for (const [mk, res] of Object.entries(resources)) {
        if (res.type === oldType || res.type.startsWith(`${oldType}/`)) {
          const updatedType = newType + res.type.slice(oldType.length);
          const newMapKey = `${Date.now()}_${mk}`;
          newResources[newMapKey] = { ...res, type: updatedType };
        } else {
          newResources[mk] = res;
        }
      }
      onChange(newResources);
    },
    [onChange, resources],
  );

  const handleMouseDown = useCallback(
    (e: React.MouseEvent) => {
      dragRef.current = { startX: e.clientX, startWidth: panelWidth };
    },
    [panelWidth],
  );

  const selectedFileName = resolved
    ? resolved.resource.name.split('/').pop() || resolved.resource.name
    : '';

  return (
    <div
      className={cn(
        'flex flex-col overflow-hidden rounded-lg border bg-card',
        className,
      )}
      style={{ height: PANEL_HEIGHT }}
    >
      <div className="flex flex-1 min-h-0">
        {/* File tree */}
        <div style={{ width: panelWidth }} className="shrink-0">
          <FileTreePanel
            nodes={nodes}
            selectedKey={selectedKey || null}
            onSelect={setSelectedKey}
            editable={editable}
            onCreateFile={editable ? handleCreateFile : undefined}
            onCreateFolder={editable ? handleCreateFolder : undefined}
            onDeleteNode={editable ? handleDeleteNode : undefined}
            onRenameFile={editable ? handleRenameFile : undefined}
            onRenameFolder={editable ? handleRenameFolder : undefined}
          />
        </div>

        {/* Resize handle */}
        <div
          className="w-1 cursor-col-resize bg-border/50 transition-colors hover:bg-primary/30 shrink-0"
          onMouseDown={handleMouseDown}
          role="separator"
          aria-orientation="vertical"
        />

        {/* Content area */}
        <div className="flex-1 min-w-0 flex flex-col">
          {!resolved ? (
            <div className="flex-1 flex items-center justify-center text-muted-foreground text-sm">
              {Object.keys(resources).length === 0
                ? t('skill.noResources')
                : t('skill.selectResource')}
            </div>
          ) : fileCategory === 'binary' ? (
            <BinaryFileView fileName={resolved.resource.name} />
          ) : fileCategory === 'image' ? (
            <ImageFileView resource={resolved.resource} />
          ) : (
            <Editor
              language={language}
              value={resolved.resource.content}
              theme="vs"
              options={{
                readOnly: !editable,
                minimap: { enabled: false },
                lineNumbers: 'on',
                scrollBeyondLastLine: false,
                wordWrap: 'on',
                automaticLayout: true,
                fontSize: 13,
                tabSize: 2,
              }}
              onChange={editable ? handleEditorChange : undefined}
              loading={
                <div className="flex items-center justify-center h-full text-muted-foreground text-sm">
                  {t('agentSpec.editorLoading')}
                </div>
              }
            />
          )}
        </div>
      </div>

      {/* Status bar */}
      <div className="flex items-center justify-between border-t bg-muted/20 px-3 py-0.5 text-[11px] leading-none text-muted-foreground">
        <div className="flex items-center gap-3">
          <span>{selectedFileName || '-'}</span>
          {resolved && <span>{language}</span>}
          <span>UTF-8</span>
        </div>
        <span>
          {editable ? t('skill.editMode') : t('agentSpec.readOnly')}
        </span>
      </div>
    </div>
  );
}

// ===== Sub-components =====

function BinaryFileView({ fileName }: { fileName: string }) {
  const { t } = useTranslation();
  return (
    <div className="flex-1 flex flex-col items-center justify-center gap-3 text-muted-foreground">
      <FileWarning className="h-12 w-12 opacity-20" />
      <p className="text-sm font-medium">{fileName}</p>
      <p className="text-xs">{t('skill.binaryFileHint')}</p>
    </div>
  );
}

function ImageFileView({ resource }: { resource: SkillResource }) {
  const { t } = useTranslation();
  const content = resource.content?.trim();

  if (!content) {
    return (
      <div className="flex-1 flex flex-col items-center justify-center gap-3 text-muted-foreground">
        <ImageIcon className="h-12 w-12 opacity-20" />
        <p className="text-sm font-medium">{resource.name}</p>
        <p className="text-xs">{t('skill.imagePreviewUnavailable')}</p>
      </div>
    );
  }

  const isLikelyBase64 =
    /^[A-Za-z0-9+/=\s]+$/.test(content) && content.length > 20;
  const ext = resource.name.split('.').pop()?.toLowerCase() || 'png';
  const mimeMap: Record<string, string> = {
    png: 'image/png',
    jpg: 'image/jpeg',
    jpeg: 'image/jpeg',
    gif: 'image/gif',
    webp: 'image/webp',
    ico: 'image/x-icon',
    bmp: 'image/bmp',
  };
  const mime = mimeMap[ext] || 'image/png';

  if (isLikelyBase64) {
    return (
      <div className="flex-1 flex items-center justify-center p-6 overflow-auto bg-muted/10">
        <img
          src={`data:${mime};base64,${content}`}
          alt={resource.name}
          className="max-w-full max-h-full object-contain rounded-lg shadow-sm border"
          onError={(e) => {
            (e.target as HTMLImageElement).style.display = 'none';
          }}
        />
      </div>
    );
  }

  return (
    <div className="flex-1 flex flex-col items-center justify-center gap-3 text-muted-foreground">
      <ImageIcon className="h-12 w-12 opacity-20" />
      <p className="text-sm font-medium">{resource.name}</p>
      <p className="text-xs">{t('skill.imagePreviewUnavailable')}</p>
    </div>
  );
}

// ===== Utilities =====

function findFirstFile(
  nodes: { type: string; key: string; children?: any[] }[],
): { key: string } | null {
  for (const n of nodes) {
    if (n.type === 'file') return n;
    if (n.children) {
      const found = findFirstFile(n.children);
      if (found) return found;
    }
  }
  return null;
}
