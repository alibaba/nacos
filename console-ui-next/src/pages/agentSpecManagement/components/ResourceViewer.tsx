import { useState, useCallback, useMemo, useRef, useEffect } from 'react';
import { Editor } from '@monaco-editor/react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import type { AgentSpecResource } from '@/types/agentspec';
import { FileTreePanel } from './FileTreePanel';
import { ResourceFileHeader } from './ResourceFileHeader';
import { buildFileTree } from './file-tree-utils';
import { getLanguageFromFileName } from './resource-viewer-utils';

// ===== Constants =====

const MANIFEST_KEY = 'manifest.json';
const DEFAULT_PANEL_WIDTH = 220;
const MIN_PANEL_WIDTH = 180;
const MAX_PANEL_WIDTH = 360;

// ===== Props =====

export interface ResourceViewerProps {
  resources: Record<string, AgentSpecResource>;
  content: string; // manifest.json content
  editable: boolean;
  onChange?: (resources: Record<string, AgentSpecResource>, content: string) => void;
  onCreateFile?: (parentKey?: string) => void;
  onCreateFolder?: (parentKey?: string) => void;
  onDeleteNode?: (key: string, nodeType: 'file' | 'folder') => void;
  onRenameFile?: (key: string, newName: string) => void;
  onRenameFolder?: (key: string, newName: string) => void;
  virtualFolders?: string[];
  className?: string;
}

// ===== Component =====

export function ResourceViewer({
  resources,
  content,
  editable,
  onChange,
  onCreateFile,
  onCreateFolder,
  onDeleteNode,
  onRenameFile,
  onRenameFolder,
  virtualFolders,
  className,
}: ResourceViewerProps) {
  const { t } = useTranslation();
  const [selectedKey, setSelectedKey] = useState<string>(MANIFEST_KEY);
  const [panelWidth, setPanelWidth] = useState(DEFAULT_PANEL_WIDTH);
  const [renamingSelectedFile, setRenamingSelectedFile] = useState(false);
  const dragRef = useRef<{ startX: number; startWidth: number } | null>(null);

  const nodes = useMemo(() => buildFileTree(resources, content, virtualFolders), [resources, content, virtualFolders]);

  useEffect(() => {
    const handleMouseMove = (event: MouseEvent) => {
      if (!dragRef.current) return;
      const deltaX = event.clientX - dragRef.current.startX;
      const nextWidth = Math.min(
        MAX_PANEL_WIDTH,
        Math.max(MIN_PANEL_WIDTH, dragRef.current.startWidth + deltaX),
      );
      setPanelWidth(nextWidth);
    };

    const handleMouseUp = () => {
      dragRef.current = null;
    };

    window.addEventListener('mousemove', handleMouseMove);
    window.addEventListener('mouseup', handleMouseUp);
    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
    };
  }, []);

  const selectedResourceEntry = useMemo(() => {
    if (selectedKey === MANIFEST_KEY) return null;
    const entry = Object.entries(resources).find(
      ([, resource]) => `${resource.type}/${resource.name}` === selectedKey,
    );
    return entry ? { resource: entry[1] } : null;
  }, [resources, selectedKey]);

  // Resolve the content and language for the currently selected file
  const { fileContent, language } = useMemo(() => {
    if (selectedKey === MANIFEST_KEY) {
      return { fileContent: content, language: getLanguageFromFileName(MANIFEST_KEY) };
    }
    if (selectedResourceEntry) {
      return {
        fileContent: selectedResourceEntry.resource.content,
        language: getLanguageFromFileName(selectedResourceEntry.resource.name),
      };
    }
    return { fileContent: '', language: 'plaintext' };
  }, [selectedKey, content, selectedResourceEntry]);

  const handleEditorChange = useCallback(
    (value: string | undefined) => {
      if (!onChange) return;
      const newValue = value ?? '';
      if (selectedKey === MANIFEST_KEY) {
        onChange(resources, newValue);
      } else {
        const entry = Object.entries(resources).find(
          ([, r]) => `${r.type}/${r.name}` === selectedKey,
        );
        if (entry) {
          const [key, res] = entry;
          onChange(
            { ...resources, [key]: { ...res, content: newValue } },
            content,
          );
        }
      }
    },
    [selectedKey, resources, content, onChange],
  );

  const selectedFilePath = selectedResourceEntry
    ? `${selectedResourceEntry.resource.type}/${selectedResourceEntry.resource.name}`
    : MANIFEST_KEY;
  const selectedFileName = selectedResourceEntry
    ? selectedResourceEntry.resource.name.split('/').pop() || selectedResourceEntry.resource.name
    : MANIFEST_KEY;
  const canManageSelectedFile = Boolean(selectedResourceEntry);

  const handleRenameSelectedFile = useCallback(
    (newName: string) => {
      if (!selectedResourceEntry || !onRenameFile) return;
      const parentPath = selectedResourceEntry.resource.name.includes('/')
        ? `${selectedResourceEntry.resource.name.split('/').slice(0, -1).join('/')}/`
        : '';
      const nextResourceName = `${parentPath}${newName}`;
      const exists = Object.values(resources).some(
        (resource) =>
          resource.type === selectedResourceEntry.resource.type
          && resource.name === nextResourceName,
      );
      if (exists) {
        toast.error(t('agentSpec.fileExists'));
        return;
      }
      onRenameFile(selectedKey, newName);
      setSelectedKey(`${selectedResourceEntry.resource.type}/${nextResourceName}`);
    },
    [onRenameFile, resources, selectedKey, selectedResourceEntry, t],
  );

  const handleCopySelectedFile = useCallback(async () => {
    try {
      await navigator.clipboard.writeText(fileContent);
      toast.success(t('agentSpec.resourceCopySuccess'));
    } catch {
      toast.error(t('agentSpec.resourceCopyFailed'));
    }
  }, [fileContent, t]);

  const handleDownloadSelectedFile = useCallback(() => {
    const blob = new Blob([fileContent], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = selectedFileName;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }, [fileContent, selectedFileName]);

  const handleDeleteSelectedFile = useCallback(() => {
    if (!selectedResourceEntry || !onDeleteNode) return;
    onDeleteNode(selectedKey, 'file');
    setSelectedKey(MANIFEST_KEY);
  }, [onDeleteNode, selectedKey, selectedResourceEntry]);

  const handleMouseDown = useCallback((event: React.MouseEvent<HTMLDivElement>) => {
    dragRef.current = {
      startX: event.clientX,
      startWidth: panelWidth,
    };
  }, [panelWidth]);

  return (
    <div className={['flex h-full min-h-0 flex-col overflow-hidden', className].filter(Boolean).join(' ')}>
      <div className="flex flex-1 min-h-0">
        <div style={{ width: panelWidth }} className="shrink-0">
          <FileTreePanel
            nodes={nodes}
            selectedKey={selectedKey}
            onSelect={setSelectedKey}
            editable={editable}
            onCreateFile={onCreateFile}
            onCreateFolder={onCreateFolder}
            onDeleteNode={onDeleteNode}
            onRenameFile={onRenameFile}
            onRenameFolder={onRenameFolder}
          />
        </div>

        <div
          className="relative -ml-px w-px shrink-0 cursor-col-resize bg-border transition-colors hover:bg-primary/40 before:absolute before:-left-1 before:top-0 before:h-full before:w-3 before:content-['']"
          onMouseDown={handleMouseDown}
          role="separator"
          aria-orientation="vertical"
          aria-label={t('agentSpec.resizeFileTreePanel')}
          tabIndex={0}
        />

        <div className="flex min-w-0 flex-1 flex-col">
          <ResourceFileHeader
            filePath={selectedFilePath}
            fileName={selectedFileName}
            editable={editable}
            canRename={canManageSelectedFile}
            canCopy
            renaming={renamingSelectedFile}
            labels={{
              rename: t('agentSpec.resourceRenameFile'),
              copy: t('agentSpec.resourceCopyFile'),
              download: t('agentSpec.resourceDownloadFile'),
              delete: t('agentSpec.resourceDeleteFile'),
            }}
            onStartRename={() => setRenamingSelectedFile(true)}
            onCancelRename={() => setRenamingSelectedFile(false)}
            onRename={handleRenameSelectedFile}
            onCopy={handleCopySelectedFile}
            onDownload={handleDownloadSelectedFile}
            onDelete={handleDeleteSelectedFile}
          />
          <div className="min-h-0 flex-1">
            <Editor
              height="100%"
              language={language}
              value={fileContent}
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
          </div>
        </div>
      </div>

      <div className="flex items-center justify-between border-t bg-muted/20 px-3 py-0.5 text-[11px] leading-none text-muted-foreground">
        <div className="flex items-center gap-2">
          <span>{selectedFileName}</span>
          <span>{language}</span>
          <span>UTF-8</span>
        </div>
        <span className="leading-none">{editable ? t('agentSpec.modified') : t('agentSpec.readOnly')}</span>
      </div>
    </div>
  );
}
