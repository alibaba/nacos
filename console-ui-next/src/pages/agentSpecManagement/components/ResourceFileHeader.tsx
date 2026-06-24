import { useEffect, useRef, useState } from 'react';
import { Copy, Download, Pencil, Trash2 } from 'lucide-react';
import { cn } from '@/lib/utils';
import { Input } from '@/components/ui/input';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';

export interface ResourceFileHeaderLabels {
  rename: string;
  copy: string;
  download: string;
  delete: string;
}

export interface ResourceFileHeaderProps {
  filePath: string;
  fileName: string;
  editable: boolean;
  canRename: boolean;
  canCopy: boolean;
  renaming: boolean;
  labels: ResourceFileHeaderLabels;
  onStartRename: () => void;
  onCancelRename: () => void;
  onRename: (newName: string) => void;
  onCopy: () => void;
  onDownload: () => void;
  onDelete: () => void;
}

export function ResourceFileHeader({
  filePath,
  fileName,
  editable,
  canRename,
  canCopy,
  renaming,
  labels,
  onStartRename,
  onCancelRename,
  onRename,
  onCopy,
  onDownload,
  onDelete,
}: ResourceFileHeaderProps) {
  return (
    <div className="flex h-11 shrink-0 items-center justify-between gap-3 border-b bg-muted/20 px-3">
      <div className="flex min-w-0 items-center gap-2">
        {renaming ? (
          <HeaderRenameInput
            initialName={fileName}
            onConfirm={(nextName) => {
              onRename(nextName);
              onCancelRename();
            }}
            onCancel={onCancelRename}
          />
        ) : (
          <>
            <span
              className="min-w-0 truncate font-mono text-sm font-medium text-foreground"
              title={filePath}
            >
              {filePath}
            </span>
            {editable && canRename && (
              <HeaderActionButton label={labels.rename} onClick={onStartRename}>
                <Pencil className="h-3.5 w-3.5" />
              </HeaderActionButton>
            )}
          </>
        )}
      </div>

      <div className="flex shrink-0 items-center gap-1">
        {canCopy && (
          <HeaderActionButton label={labels.copy} onClick={onCopy}>
            <Copy className="h-3.5 w-3.5" />
          </HeaderActionButton>
        )}
        <HeaderActionButton label={labels.download} onClick={onDownload}>
          <Download className="h-3.5 w-3.5" />
        </HeaderActionButton>
        {editable && canRename && (
          <HeaderActionButton label={labels.delete} onClick={onDelete} destructive>
            <Trash2 className="h-3.5 w-3.5" />
          </HeaderActionButton>
        )}
      </div>
    </div>
  );
}

function HeaderRenameInput({
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
    const nextValue = value.trim();
    if (nextValue && nextValue !== initialName) {
      onConfirm(nextValue);
      return;
    }
    onCancel();
  };

  return (
    <Input
      ref={inputRef}
      value={value}
      onChange={(event) => setValue(event.target.value)}
      onBlur={handleSubmit}
      onKeyDown={(event) => {
        if (event.key === 'Enter') handleSubmit();
        if (event.key === 'Escape') onCancel();
      }}
      className="h-7 w-[260px] max-w-[45vw] font-mono text-sm"
    />
  );
}

function HeaderActionButton({
  label,
  onClick,
  children,
  destructive = false,
}: {
  label: string;
  onClick: () => void;
  children: React.ReactNode;
  destructive?: boolean;
}) {
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <button
          type="button"
          className={cn(
            'inline-flex h-7 w-7 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-muted hover:text-foreground',
            destructive && 'hover:bg-destructive/10 hover:text-destructive',
          )}
          onClick={onClick}
          aria-label={label}
        >
          {children}
        </button>
      </TooltipTrigger>
      <TooltipContent>{label}</TooltipContent>
    </Tooltip>
  );
}
