import { useState } from 'react';
import { Braces, Check, Copy } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import type { McpClientConfiguration } from './mcp-client-config';

interface McpClientConfigCardProps {
  configurations: McpClientConfiguration[];
}

async function copyTextToClipboard(text: string) {
  if (navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(text);
      return;
    } catch {
      // Fall through for browsers that expose but restrict the Clipboard API.
    }
  }

  const textarea = document.createElement('textarea');
  textarea.value = text;
  textarea.style.position = 'absolute';
  textarea.style.left = '-999999px';
  document.body.appendChild(textarea);
  textarea.select();
  try {
    document.execCommand('copy');
  } finally {
    document.body.removeChild(textarea);
  }
}

export function McpClientConfigCard({ configurations }: McpClientConfigCardProps) {
  const { t } = useTranslation();
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  const copyConfiguration = async (configuration: McpClientConfiguration) => {
    try {
      await copyTextToClipboard(JSON.stringify(configuration.config, null, 2));
      setCopiedKey(configuration.key);
      toast.success(t('mcp.copySuccess'));
      window.setTimeout(() => setCopiedKey(null), 2000);
    } catch {
      toast.error(t('mcp.copyFailed'));
    }
  };

  return (
    <Card className="overflow-hidden py-0 gap-0">
      <div className="px-5 py-3.5 border-b bg-muted/30">
        <h2 className="text-sm font-semibold flex items-center gap-2">
          <Braces className="h-4 w-4 text-muted-foreground" />
          {t('mcp.clientConfiguration')}
        </h2>
      </div>
      <CardContent className="p-4 space-y-3">
        {configurations.length === 0 ? (
          <p className="text-sm text-muted-foreground text-center py-4">
            {t('mcp.noClientConfiguration')}
          </p>
        ) : configurations.map((configuration) => {
          const content = JSON.stringify(configuration.config, null, 2);
          return (
            <div key={configuration.key} className="space-y-1.5">
              {configurations.length > 1 && (
                <p className="truncate text-[11px] text-muted-foreground" title={configuration.label}>
                  {configuration.label}
                </p>
              )}
              <div className="group relative overflow-hidden rounded-md border border-zinc-800 bg-zinc-950 dark:bg-zinc-900">
                <pre className="max-h-64 overflow-auto whitespace-pre-wrap break-all px-3 py-3 pr-10 font-mono text-[11px] leading-relaxed text-zinc-300">
                  {content}
                </pre>
                <Button
                  variant="ghost"
                  size="icon"
                  className="absolute right-1.5 top-1.5 h-7 w-7 text-zinc-400 hover:bg-zinc-800 hover:text-zinc-100"
                  onClick={() => copyConfiguration(configuration)}
                  aria-label={t('mcp.copyClientConfiguration')}
                  title={t('mcp.copyClientConfiguration')}
                >
                  {copiedKey === configuration.key ? (
                    <Check className="h-3.5 w-3.5 text-emerald-400" />
                  ) : (
                    <Copy className="h-3.5 w-3.5" />
                  )}
                </Button>
              </div>
            </div>
          );
        })}
      </CardContent>
    </Card>
  );
}
