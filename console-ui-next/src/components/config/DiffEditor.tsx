import { DiffEditor as MonacoDiffEditor } from '@monaco-editor/react';
import { type ConfigType, MONACO_LANGUAGE_MAP } from '@/types/config';

interface DiffEditorProps {
  original: string;
  modified: string;
  language: ConfigType;
  height?: string;
  className?: string;
}

export function DiffEditor({
  original,
  modified,
  language,
  height = '400px',
  className,
}: DiffEditorProps) {
  const monacoLanguage = MONACO_LANGUAGE_MAP[language] || 'plaintext';

  return (
    <div className={`border rounded-md overflow-hidden ${className || ''}`}>
      <MonacoDiffEditor
        height={height}
        language={monacoLanguage}
        original={original}
        modified={modified}
        theme="vs"
        options={{
          readOnly: true,
          minimap: { enabled: false },
          scrollBeyondLastLine: false,
          wordWrap: 'on',
          automaticLayout: true,
          fontSize: 13,
          renderSideBySide: true,
        }}
        loading={
          <div className="flex items-center justify-center h-full text-gray-500">
            Loading diff editor...
          </div>
        }
      />
    </div>
  );
}
