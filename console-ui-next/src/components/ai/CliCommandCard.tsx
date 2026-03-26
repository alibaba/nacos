import { useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { Terminal, Copy, Check } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { toast } from 'sonner';
import { cn } from '@/lib/utils';

interface CliCommand {
  /** Short label shown above the command, e.g. "By version" */
  label: string;
  command: string;
}

interface CliCommandCardProps {
  commands: CliCommand[];
  className?: string;
}

export function CliCommandCard({ commands, className }: CliCommandCardProps) {
  const { t } = useTranslation();

  if (commands.length === 0) return null;

  return (
    <Card className={cn('overflow-hidden py-0 gap-0', className)}>
      <div className="px-4 py-3 border-b bg-muted/30">
        <h2 className="text-sm font-semibold flex items-center gap-2">
          <Terminal className="h-4 w-4 text-muted-foreground" />
          {t('common.cliUsage.title')}
        </h2>
      </div>
      <CardContent className="p-3.5 space-y-3">
        <p className="text-[11px] text-muted-foreground leading-relaxed">
          {t('common.cliUsage.description')}
        </p>
        {commands.map((cmd, idx) => (
          <CommandBlock key={idx} label={cmd.label} command={cmd.command} />
        ))}
      </CardContent>
    </Card>
  );
}

function CommandBlock({ label, command }: { label: string; command: string }) {
  const { t } = useTranslation();
  const [copied, setCopied] = useState(false);

  const handleCopy = useCallback(async () => {
    try {
      await navigator.clipboard.writeText(command);
      setCopied(true);
      toast.success(t('common.cliUsage.copied'));
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Fallback
      const textarea = document.createElement('textarea');
      textarea.value = command;
      document.body.appendChild(textarea);
      textarea.select();
      document.execCommand('copy');
      document.body.removeChild(textarea);
      setCopied(true);
      toast.success(t('common.cliUsage.copied'));
      setTimeout(() => setCopied(false), 2000);
    }
  }, [command, t]);

  return (
    <div>
      <p className="text-[11px] font-medium text-muted-foreground mb-1.5">{label}</p>
      <div className="group relative rounded-md bg-zinc-950 dark:bg-zinc-900 border border-zinc-800 overflow-hidden">
        <pre className="px-3 py-2.5 pr-10 text-[11px] leading-relaxed text-zinc-300 font-mono overflow-x-auto whitespace-pre-wrap break-all">
          <span className="text-emerald-400 select-none">$ </span>
          {command}
        </pre>
        <Button
          variant="ghost"
          size="icon"
          className="absolute top-1.5 right-1.5 h-6 w-6 text-zinc-500 hover:text-zinc-200 hover:bg-zinc-800 opacity-0 group-hover:opacity-100 transition-opacity"
          onClick={handleCopy}
        >
          {copied ? (
            <Check className="h-3 w-3 text-emerald-400" />
          ) : (
            <Copy className="h-3 w-3" />
          )}
        </Button>
      </div>
    </div>
  );
}
