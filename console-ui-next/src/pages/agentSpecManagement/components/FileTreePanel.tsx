import { useState, useCallback, useRef, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import {
  File,
  Folder,
  FolderOpen,
  FileJson,
  FilePlus,
  FolderPlus,
  Trash2,
  Pencil,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { ScrollArea } from '@/components/ui/scroll-area';
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
  onCreateFile?: (parentKey?: string) => void;
  onCreateFolder?: (parentKey?: string) => void;
  onDeleteNode?: (key: string, nodeType: 'file' | 'folder') => void;
  onRenameFile?: (key: string, newName: string) => void;
  onRenameFolder?: (key: string, newName: string) => void;
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
  onCreateFile?: (parentKey?: string) => void;
  onCreateFolder?: (parentKey?: string) => void;
  onDeleteNode?: (key: string, nodeType: 'file' | 'folder') => void;
  onRenameFile?: (key: string, newName: string) => void;
  onRenameFolder?: (key: string, newName: string) => void;
  depth: number;
}

function isTopLevelFolder(node: FileTreeNode): boolean {
  return node.type === 'folder'
    && node.key.split('/').filter(Boolean).length === 1;
}

function TreeActionButton({
  onClick,
  label,
  children,
  destructive = false,
}: {
  onClick: (event: React.MouseEvent<HTMLButtonElement>) => void;
  label: string;
  children: React.ReactNode;
  destructive?: boolean;
}) {
  return (
    <button
      type="button"
      className={cn(
        'inline-flex h-5 w-5 items-center justify-center rounded-sm text-muted-foreground transition-colors hover:bg-accent hover:text-foreground',
        destructive && 'hover:bg-destructive/10 hover:text-destructive',
      )}
      onClick={onClick}
      aria-label={label}
      title={label}
    >
      {children}
    </button>
  );
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
  onDeleteNode,
  onRenameFile,
  depth,
}: TreeNodeProps) {
  const { t } = useTranslation();
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
        'group/row flex items-center gap-1 px-2 py-1 cursor-pointer rounded-sm text-sm hover:bg-accent/50',
        isSelected && 'text-foreground',
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
        <span className="truncate flex-1 min-w-0">{node.name}</span>
      )}
      {editable && !isManifest && !renaming && (
        <div className="shrink-0 ml-auto flex items-center gap-0.5 opacity-0 group-hover/row:opacity-100 transition-opacity">
          <button
            className="p-0.5 rounded hover:bg-accent"
            onClick={(e) => {
              e.stopPropagation();
              setRenaming(true);
            }}
            aria-label={t('agentSpec.renameNode', { name: node.name })}
          >
            <Pencil className="h-3 w-3 text-muted-foreground" />
          </button>
          <button
            className="p-0.5 rounded hover:bg-destructive/10"
            onClick={(e) => {
              e.stopPropagation();
              onDeleteNode?.(node.key, 'file');
            }}
            aria-label={t('agentSpec.deleteNode', { name: node.name })}
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
  onCreateFile,
  onCreateFolder,
  onDeleteNode,
  onRenameFile,
  onRenameFolder,
  depth,
}: TreeNodeProps) {
  const { t } = useTranslation();
  const [expanded, setExpanded] = useState(true);
  const [renaming, setRenaming] = useState(false);
  const canDelete = !isTopLevelFolder(node);
  const canRename = true;

  const handleRename = useCallback(
    (newName: string) => {
      setRenaming(false);
      onRenameFolder?.(node.key, newName);
    },
    [node.key, onRenameFolder],
  );

  return (
    <div role="group">
      <div
        className="group/row flex items-center gap-1 px-2 py-1 cursor-pointer rounded-sm text-sm hover:bg-accent/50 font-medium"
        style={{ paddingLeft: `${depth * 12 + 8}px` }}
        onClick={() => !renaming && setExpanded(!expanded)}
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
        {renaming ? (
          <RenameInput
            initialName={node.name.replace(/\/$/, '')}
            onConfirm={handleRename}
            onCancel={() => setRenaming(false)}
          />
        ) : (
          <span className="truncate flex-1 min-w-0">{node.name}</span>
        )}
        {editable && !renaming && (
          <div className="shrink-0 ml-auto flex items-center gap-0.5 opacity-0 transition-opacity group-hover/row:opacity-100">
            {canRename && (
              <TreeActionButton
                label={t('agentSpec.renameNode', { name: node.name })}
                onClick={(event) => {
                  event.stopPropagation();
                  setRenaming(true);
                }}
              >
                <Pencil className="h-3.5 w-3.5" />
              </TreeActionButton>
            )}
            <TreeActionButton
              label={t('agentSpec.createFileIn', { name: node.name })}
              onClick={(event) => {
                event.stopPropagation();
                onCreateFile?.(node.key);
              }}
            >
              <FilePlus className="h-3.5 w-3.5" />
            </TreeActionButton>
            <TreeActionButton
              label={t('agentSpec.createFolderIn', { name: node.name })}
              onClick={(event) => {
                event.stopPropagation();
                onCreateFolder?.(node.key);
              }}
            >
              <FolderPlus className="h-3.5 w-3.5" />
            </TreeActionButton>
            {canDelete && (
              <TreeActionButton
                label={t('agentSpec.deleteNode', { name: node.name })}
                destructive
                onClick={(event) => {
                  event.stopPropagation();
                  onDeleteNode?.(node.key, 'folder');
                }}
              >
                <Trash2 className="h-3.5 w-3.5" />
              </TreeActionButton>
            )}
          </div>
        )}
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
              onCreateFile={onCreateFile}
              onCreateFolder={onCreateFolder}
              onDeleteNode={onDeleteNode}
              onRenameFile={onRenameFile}
              onRenameFolder={onRenameFolder}
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
  onCreateFolder,
  onDeleteNode,
  onRenameFile,
  onRenameFolder,
}: FileTreePanelProps) {
  const { t } = useTranslation();

  return (
    <div className="flex flex-col h-full border-r bg-muted/30">
      {editable && (
        <div className="flex items-center justify-between border-b px-2 py-1.5">
          <span className="text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-foreground">
            {t('agentSpec.fileTreeTitle')}
          </span>
          <div className="flex items-center gap-0.5">
            <TreeActionButton label={t('agentSpec.newFile')} onClick={() => onCreateFile?.()}>
              <FilePlus className="h-3.5 w-3.5" />
            </TreeActionButton>
            <TreeActionButton label={t('agentSpec.newFolder')} onClick={() => onCreateFolder?.()}>
              <FolderPlus className="h-3.5 w-3.5" />
            </TreeActionButton>
          </div>
        </div>
      )}
      <ScrollArea className="flex-1 bg-inherit">
        <div className="min-h-full bg-inherit py-2" role="tree" aria-label={t('agentSpec.fileTree')}>
          {nodes.map((node) => (
            <TreeNodeItem
              key={node.key}
              node={node}
              selectedKey={selectedKey}
              onSelect={onSelect}
              editable={editable}
              onCreateFile={onCreateFile}
              onCreateFolder={onCreateFolder}
              onDeleteNode={onDeleteNode}
              onRenameFile={onRenameFile}
              onRenameFolder={onRenameFolder}
              depth={0}
            />
          ))}
        </div>
      </ScrollArea>
    </div>
  );
}
