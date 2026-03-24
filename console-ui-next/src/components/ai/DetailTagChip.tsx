import { X } from 'lucide-react';

import { cn } from '@/lib/utils';

interface DetailTagChipProps {
  label: string;
  onRemove?: () => void;
  className?: string;
}

export function DetailTagChip({ label, onRemove, className }: DetailTagChipProps) {
  return (
    <span
      className={cn(
        'inline-flex h-6 items-center gap-1 rounded-lg border border-border/60 bg-muted/70 px-2.5 text-xs font-medium leading-none text-foreground shadow-sm shadow-black/[0.03]',
        className
      )}
    >
      <span>{label}</span>
      {onRemove && (
        <button
          type="button"
          className="inline-flex h-4 w-4 items-center justify-center rounded-sm text-muted-foreground transition-colors hover:bg-background/80 hover:text-foreground"
          onClick={onRemove}
        >
          <X className="h-3 w-3" />
        </button>
      )}
    </span>
  );
}
