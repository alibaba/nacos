import { useState, useCallback, useRef, useEffect } from 'react';
import {
  File,
  Folder,
  FolderOpen,
  FileJson,
  Plus,
  Trash2,
  Pencil,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';

// ===== Types =====

export interface FileTreeNode {
  key: string;
  name: string;
  type: 'file' | 'folder';
  children?: FileTreeNode[];
  resourceType?: string;
}

export interface FileTreePanelProps {
  nodes: FileTreeNode[];
  selectedKey: string | null;
  onSelect: (key: string) => void;
  editable: boolean;
  onCreateFile?: () => void;
  onDeleteFile?: (key: string) => void;
  onRenameFile?: (key: string, newName: string) => void;
}

// ===== Constants =====

const MANIFEST_KEY = 'manifest.json';

// ===== Sub-components =====

function FileIcon({ node }: { node: FileTreeNode }) {
  if (node.key === MANIFEST_KEY) {
    return <FileJson className="h-4 w-4 shrink-0 text-amber-500" />;
  }
  if (node.type === 'folder') {
    return null; // handled by FolderNode
  }
  return <File className="h-4 w-4 shrink-0 text-muted-foreground" />;
}

interface TreeNodeProps {
  node: FileTreeNode;
  selectedKey: string | null;
  onSelect: (key: string) => void;
  editable: boolean;
  onDeleteFile?: (key: string) => void;
  onRenameFile?: (key: string, newName: string) => void;
  depth: number;
}

function RenameInput({
  initialName,
  onConfirm,
  onCancel,
}: {
  initialName: string;
  onConfirm: (newName: string) => void;
  onCancel: () => void;
}) {
  const [value, setValue] = useState(initialName);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    inputRef.current?.focus();
    inputRef.current?.select();
  }, []);

  const handleSubmit = () => {
    const trimmed = value.trim();
    if (trimmed && trimmed !== initialName) {
      onConfirm(trimmed);
    } else {
      onCancel();
    }
  };

  return (
    <Input
      ref={inputRef}
      value={value}
      onChange={(e) => setValue(e.target.value)}
      onBlur={handleSubmit}
      onKeyDown={(e) => {
        if (e.key === 'Enter') handleSubmit();
        if (e.key === 'Escape') onCancel();
      }}
      className="h-6 px-1 py-0 text-sm"
    />
  );
}

function FileNode({
  node,
  selectedKey,
  onSelect,
  editable,
  onDeleteFile,
  onRenameFile,
  depth,
}: TreeNodeProps) {
  const [renaming, setRenaming] = useState(false);
  const isManifest = node.key === MANIFEST_KEY;
  const isSelected = selectedKey === node.key;

  const handleRename = useCallback(
    (newName: string) => {
      setRenaming(false);
      onRenameFile?.(node.key, newName);
    },
    [node.key, onRenameFile],
  );

  return (
    <div
      className={cn(
        'group flex items-center gap-1 px-2 py-1 cursor-pointer rounded-sm text-sm hover:bg-accent/50',
        isSelected && 'bg-accent text-accent-foreground',
      )}
      style={{ paddingLeft: `${depth * 12 + 8}px` }}
      onClick={() => onSelect(node.key)}
      role="treeitem"
      aria-selected={isSelected}
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          onSelect(node.key);
        }
      }}
    >
      <FileIcon node={node} />
      {renaming ? (
        <RenameInput
          initialName={node.name}
          onConfirm={handleRename}
          onCancel={() => setRenaming(false)}
        />
      ) : (
        <span className="truncate flex-1">{node.name}</span>
      )}
      {editable && !isManifest && !renaming && (
        <div className="ml-auto flex items-center gap-0.5 opacity-0 group-hover:opacity-100 transition-opacity">
          <button
            className="p-0.5 rounded hover:bg-accent"
            onClick={(e) => {
              e.stopPropagation();
              setRenaming(true);
            }}
            aria-label={`Rename ${node.name}`}
          >
            <Pencil className="h-3 w-3 text-muted-foreground" />
          </button>
          <button
            className="p-0.5 rounded hover:bg-destructive/10"
            onClick={(e) => {
              e.stopPropagation();
              onDeleteFile?.(node.key);
            }}
            aria-label={`Delete ${node.name}`}
          >
            <Trash2 className="h-3 w-3 text-destructive" />
          </button>
        </div>
      )}
    </div>
  );
}

function FolderNode({
  node,
  selectedKey,
  onSelect,
  editable,
  onDeleteFile,
  onRenameFile,
  depth,
}: TreeNodeProps) {
  const [expanded, setExpanded] = useState(true);

  return (
    <div role="group">
      <div
        className="flex items-center gap-1 px-2 py-1 cursor-pointer rounded-sm text-sm hover:bg-accent/50 font-medium"
        style={{ paddingLeft: `${depth * 12 + 8}px` }}
        onClick={() => setExpanded(!expanded)}
        role="treeitem"
        aria-expanded={expanded}
        tabIndex={0}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            setExpanded(!expanded);
          }
        }}
      >
        {expanded ? (
          <FolderOpen className="h-4 w-4 shrink-0 text-blue-500" />
        ) : (
          <Folder className="h-4 w-4 shrink-0 text-blue-500" />
        )}
        <span className="truncate flex-1">{node.name}</span>
      </div>
      {expanded && node.children && (
        <div>
          {node.children.map((child) => (
            <TreeNodeItem
              key={child.key}
              node={child}
              selectedKey={selectedKey}
              onSelect={onSelect}
              editable={editable}
              onDeleteFile={onDeleteFile}
              onRenameFile={onRenameFile}
              depth={depth + 1}
            />
          ))}
        </div>
      )}
    </div>
  );
}

function TreeNodeItem(props: TreeNodeProps) {
  if (props.node.type === 'folder') {
    return <FolderNode {...props} />;
  }
  return <FileNode {...props} />;
}

// ===== Main Component =====

export function FileTreePanel({
  nodes,
  selectedKey,
  onSelect,
  editable,
  onCreateFile,
  onDeleteFile,
  onRenameFile,
}: FileTreePanelProps) {
  return (
    <div className="flex flex-col h-full border-r bg-muted/30">
      <ScrollArea className="flex-1">
        <div className="py-2" role="tree" aria-label="File tree">
          {nodes.map((node) => (
            <TreeNodeItem
              key={node.key}
              node={node}
              selectedKey={selectedKey}
              onSelect={onSelect}
              editable={editable}
              onDeleteFile={onDeleteFile}
              onRenameFile={onRenameFile}
              depth={0}
            />
          ))}
        </div>
      </ScrollArea>
      {editable && (
        <div className="border-t p-2">
          <Button
            variant="ghost"
            size="sm"
            className="w-full justify-start gap-2 text-muted-foreground"
            onClick={onCreateFile}
          >
            <Plus className="h-4 w-4" />
            New File
          </Button>
        </div>
      )}
    </div>
  );
}
