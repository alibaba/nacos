import { useState, useCallback } from 'react';
import { useTranslation } from 'react-i18next';
import { Copy, Check, Download, ExternalLink } from 'lucide-react';
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
  /** When provided, renders a download ZIP button section */
  onDownload?: () => void;
  /** File name hint shown on the download button, e.g. "skill-name-1.0.0.zip" */
  downloadFileName?: string;
  /** Whether download is available (e.g. non-draft version selected) */
  downloadDisabled?: boolean;
}

export function CliCommandCard({ commands, className, onDownload, downloadFileName, downloadDisabled }: CliCommandCardProps) {
  const { t } = useTranslation();
  const downloadLabel = downloadFileName || t('common.cliUsage.downloadZip');

  if (commands.length === 0 && !onDownload) return null;

  return (
    <Card className={cn('overflow-hidden py-0 gap-0', className)}>
      <div className="px-4 py-3 border-b bg-muted/30">
        <h2 className="text-sm font-semibold flex items-center gap-2">
          <Download className="h-4 w-4 text-muted-foreground" />
          {t('common.cliUsage.title')}
        </h2>
      </div>
      <CardContent className="p-3.5 space-y-3">
        {/* Download ZIP section */}
        {onDownload && (
          <>
            <p className="text-xs font-medium text-foreground">{t('common.cliUsage.manualDownload')}</p>
            <Button
              variant="outline"
              size="sm"
              className="w-full h-8 min-w-0 overflow-hidden text-xs gap-1.5"
              disabled={downloadDisabled}
              onClick={onDownload}
            >
              <Download className="h-3.5 w-3.5" />
              <span className="min-w-0 truncate" title={downloadLabel}>
                {downloadLabel}
              </span>
            </Button>
          </>
        )}

        {/* CLI section */}
        {commands.length > 0 && (
          <>
            {onDownload && <div className="border-t" />}
            <p className="text-xs font-medium text-foreground flex items-center gap-1.5">
              {t('common.cliUsage.cliInstall')}
              <a
                href="https://github.com/nacos-group/nacos-cli"
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-0.5 text-[11px] text-muted-foreground hover:text-foreground transition-colors"
              >
                {t('common.cliUsage.cliDoc')}
                <ExternalLink className="h-3 w-3" />
              </a>
            </p>
            {commands.map((cmd, idx) => (
              <CommandBlock key={idx} command={cmd.command} />
            ))}
          </>
        )}
      </CardContent>
    </Card>
  );
}

function CommandBlock({ command }: { command: string }) {
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
