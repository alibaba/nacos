import { useState, useCallback, useMemo } from 'react';
import { Editor } from '@monaco-editor/react';
import type { AgentSpecResource } from '@/types/agentspec';
import { FileTreePanel } from './FileTreePanel';
import { buildFileTree } from './file-tree-utils';
import { getLanguageFromFileName } from './resource-viewer-utils';

// ===== Constants =====

const MANIFEST_KEY = 'manifest.json';

// ===== Props =====

export interface ResourceViewerProps {
  resources: Record<string, AgentSpecResource>;
  content: string; // manifest.json content
  editable: boolean;
  onChange?: (resources: Record<string, AgentSpecResource>, content: string) => void;
}

// ===== Component =====

export function ResourceViewer({
  resources,
  content,
  editable,
  onChange,
}: ResourceViewerProps) {
  const [selectedKey, setSelectedKey] = useState<string>(MANIFEST_KEY);

  const nodes = useMemo(() => buildFileTree(resources, content), [resources, content]);

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

  return (
    <div className="flex h-full min-h-[300px] border rounded-md overflow-hidden">
      {/* Left: File Tree */}
      <div className="w-56 shrink-0">
        <FileTreePanel
          nodes={nodes}
          selectedKey={selectedKey}
          onSelect={setSelectedKey}
          editable={editable}
        />
      </div>

      {/* Right: Monaco Editor */}
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
            <div className="flex items-center justify-center h-full text-muted-foreground">
              Loading editor...
            </div>
          }
        />
      </div>
    </div>
  );
}
