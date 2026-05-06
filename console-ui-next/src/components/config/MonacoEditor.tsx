import { Editor } from '@monaco-editor/react';
import { type ConfigType, MONACO_LANGUAGE_MAP } from '@/types/config';

interface MonacoEditorProps {
  value: string;
  onChange?: (value: string) => void;
  language: ConfigType;
  readOnly?: boolean;
  height?: string;
  className?: string;
}

export function MonacoEditor({
  value,
  onChange,
  language,
  readOnly = false,
  height = '400px',
  className,
}: MonacoEditorProps) {
  const monacoLanguage = MONACO_LANGUAGE_MAP[language];

  return (
    <div className={`border rounded-md overflow-hidden ${className || ''}`}>
      <Editor
        height={height}
        language={monacoLanguage}
        value={value}
        theme="vs"
        options={{
          readOnly,
          minimap: { enabled: false },
          lineNumbers: 'on',
          scrollBeyondLastLine: false,
          wordWrap: 'on',
          automaticLayout: true,
          fontSize: 13,
          tabSize: 2,
        }}
        onChange={(newValue) => onChange?.(newValue || '')}
        loading={
          <div className="flex items-center justify-center h-full text-gray-500">
            Loading editor...
          </div>
        }
      />
    </div>
  );
}
