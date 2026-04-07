import { useState, useCallback, useMemo, useRef, useEffect } from 'react';
import { Editor } from '@monaco-editor/react';
import { useTranslation } from 'react-i18next';
import type { AgentSpecResource } from '@/types/agentspec';
import { FileTreePanel } from './FileTreePanel';
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

  // Resolve the content and language for the currently selected file
  const { fileContent, language } = useMemo(() => {
    if (selectedKey === MANIFEST_KEY) {
      return { fileContent: content, language: getLanguageFromFileName(MANIFEST_KEY) };
    }
    // selectedKey format: "type/resourceName"
    const resource = Object.values(resources).find(
      (r) => `${r.type}/${r.name}` === selectedKey,
    );
    if (resource) {
      return {
        fileContent: resource.content,
        language: getLanguageFromFileName(resource.name),
      };
    }
    return { fileContent: '', language: 'plaintext' };
  }, [selectedKey, content, resources]);

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

  const selectedFileName = selectedKey === MANIFEST_KEY
    ? MANIFEST_KEY
    : selectedKey.includes('/')
      ? selectedKey.split('/').pop() || selectedKey
      : selectedKey;

  const handleMouseDown = useCallback((event: React.MouseEvent<HTMLDivElement>) => {
    dragRef.current = {
      startX: event.clientX,
      startWidth: panelWidth,
    };
  }, [panelWidth]);

  return (
    <div className={['flex h-full min-h-0 flex-col overflow-hidden', className].filter(Boolean).join(' ')}>
      <div className="flex flex-1 min-h-0">
        <div style={{ width: panelWidth }} className="shrink-0 border-r">
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
          className="w-1 cursor-col-resize bg-border transition-colors hover:bg-primary/30 shrink-0"
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
