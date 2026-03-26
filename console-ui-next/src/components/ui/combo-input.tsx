import * as React from 'react';
import { useState, useRef, useEffect } from 'react';
import { cn } from '@/lib/utils';
import { Input } from './input';
import { ChevronDown } from 'lucide-react';

interface ComboInputProps {
  value: string;
  onChange: (value: string) => void;
  options: { value: string; label: string }[];
  placeholder?: string;
  loading?: boolean;
  loadingText?: string;
  className?: string;
}

/**
 * ComboInput - Input with dropdown suggestions.
 * Supports both free-text input and selecting from a list.
 * Uses a plain positioned dropdown to avoid Radix Popover toggle-flicker.
 */
export function ComboInput({
  value,
  onChange,
  options,
  placeholder,
  loading,
  loadingText = 'Loading...',
  className,
}: ComboInputProps) {
  const [open, setOpen] = useState(false);
  const [filter, setFilter] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  // Sync external value to internal filter when dropdown closes
  useEffect(() => {
    if (!open) {
      setFilter(value);
    }
  }, [open, value]);

  // Close on click outside
  useEffect(() => {
    if (!open) return;
    const handlePointerDown = (e: PointerEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('pointerdown', handlePointerDown);
    return () => document.removeEventListener('pointerdown', handlePointerDown);
  }, [open]);

  const filtered = filter
    ? options.filter((o) =>
        o.label.toLowerCase().includes(filter.toLowerCase()) ||
        o.value.toLowerCase().includes(filter.toLowerCase())
      )
    : options;

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const v = e.target.value;
    setFilter(v);
    onChange(v);
    if (!open) setOpen(true);
  };

  const handleSelect = (val: string) => {
    onChange(val);
    setFilter(val);
    setOpen(false);
  };

  return (
    <div ref={containerRef} className={cn('relative', className)}>
      <Input
        ref={inputRef}
        value={open ? filter : value}
        onChange={handleInputChange}
        placeholder={placeholder}
        onFocus={() => setOpen(true)}
        onKeyDown={(e) => {
          if (e.key === 'Escape') {
            setOpen(false);
            inputRef.current?.blur();
          }
        }}
        className="pr-8"
      />
      <ChevronDown
        className="absolute right-2.5 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground cursor-pointer"
        onClick={() => {
          setOpen((prev) => !prev);
          inputRef.current?.focus();
        }}
      />
      {open && (
        <div className="absolute z-50 mt-1 w-full rounded-md border bg-popover p-1 text-popover-foreground shadow-md animate-in fade-in-0 zoom-in-95 slide-in-from-top-2">
          <div className="max-h-[200px] overflow-y-auto">
            {loading ? (
              <div className="px-3 py-2 text-sm text-muted-foreground">{loadingText}</div>
            ) : filtered.length === 0 ? (
              <div className="px-3 py-2 text-sm text-muted-foreground">—</div>
            ) : (
              filtered.map((opt) => (
                <div
                  key={opt.value}
                  className={cn(
                    'flex items-center rounded-sm px-3 py-1.5 text-sm cursor-pointer hover:bg-accent hover:text-accent-foreground',
                    opt.value === value && 'bg-accent text-accent-foreground'
                  )}
                  onMouseDown={(e) => {
                    e.preventDefault();
                    handleSelect(opt.value);
                  }}
                >
                  {opt.label}
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
}
