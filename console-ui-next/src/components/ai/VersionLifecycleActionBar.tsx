import type { ReactNode } from 'react';

import { cn } from '@/lib/utils';

interface VersionLifecycleActionBarProps {
  children: ReactNode;
  className?: string;
  warning?: ReactNode;
}

export function VersionLifecycleActionBar({
  children,
  className,
  warning,
}: VersionLifecycleActionBarProps) {
  return (
    <div className={cn('mt-3 border-t border-border/40 pt-3', className)}>
      {warning}
      <div className="flex flex-wrap items-center gap-2">{children}</div>
    </div>
  );
}

export function VersionLifecycleActionDivider() {
  return <div aria-hidden="true" className="mx-0.5 h-4 w-px bg-border" />;
}
