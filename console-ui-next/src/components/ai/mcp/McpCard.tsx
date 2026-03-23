import { useTranslation } from 'react-i18next';
import { Cpu, Pencil, Trash2, ExternalLink, Wrench, MessageSquare, Database, Zap, RefreshCw } from 'lucide-react';
import { Card, CardFooter } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Checkbox } from '@/components/ui/checkbox';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { cn } from '@/lib/utils';
import type { McpServerBasicInfo } from '@/types/mcp';

interface McpCardProps {
  server: McpServerBasicInfo;
  selected?: boolean;
  onSelect?: (name: string) => void;
  onDetail?: (name: string) => void;
  onEdit?: (name: string) => void;
  onDelete?: (name: string) => void;
}

const PROTOCOL_STYLES: Record<string, { bg: string; text: string; dot: string }> = {
  stdio: { bg: 'bg-purple-50 dark:bg-purple-950/40', text: 'text-purple-700 dark:text-purple-300', dot: 'bg-purple-500' },
  'mcp-sse': { bg: 'bg-blue-50 dark:bg-blue-950/40', text: 'text-blue-700 dark:text-blue-300', dot: 'bg-blue-500' },
  'mcp-streamable': { bg: 'bg-cyan-50 dark:bg-cyan-950/40', text: 'text-cyan-700 dark:text-cyan-300', dot: 'bg-cyan-500' },
  http: { bg: 'bg-orange-50 dark:bg-orange-950/40', text: 'text-orange-700 dark:text-orange-300', dot: 'bg-orange-500' },
  dubbo: { bg: 'bg-green-50 dark:bg-green-950/40', text: 'text-green-700 dark:text-green-300', dot: 'bg-green-500' },
};

const CAPABILITY_ICON: Record<string, { icon: typeof Wrench; color: string }> = {
  TOOL: { icon: Wrench, color: 'text-amber-500' },
  PROMPT: { icon: MessageSquare, color: 'text-blue-500' },
  RESOURCE: { icon: Database, color: 'text-emerald-500' },
};

export function McpCard({
  server,
  selected,
  onSelect,
  onDetail,
  onEdit,
  onDelete,
}: McpCardProps) {
  const { t } = useTranslation();
  const version = server.versionDetail?.version || server.version || '';
  const protocolLabel = server.frontProtocol || server.protocol || 'unknown';
  const protocolStyle = PROTOCOL_STYLES[protocolLabel];
  const isRestToMcp = server.protocol === 'http' || server.protocol === 'https';
  const tools = (server as { toolSpec?: { tools?: { name: string }[] } }).toolSpec?.tools || [];
  const capabilities = server.capabilities || [];

  return (
    <Card
      className={cn(
        'group relative flex flex-col py-0 gap-0 transition-all duration-200 hover:shadow-sm hover:border-primary/20 cursor-pointer overflow-hidden',
        selected && 'ring-2 ring-primary border-primary/40'
      )}
      onClick={() => onDetail?.(server.name)}
    >
      {/* Header */}
      <div className="flex items-start gap-3 px-4 pt-3.5 pb-2 relative">
        {/* Checkbox - top right */}
        {onSelect && (
          <div
            className="absolute top-2.5 right-2.5 opacity-0 group-hover:opacity-100 transition-opacity data-[checked=true]:opacity-100"
            data-checked={selected || undefined}
            onClick={(e) => e.stopPropagation()}
          >
            <Checkbox
              checked={selected}
              onCheckedChange={() => onSelect(server.name)}
            />
          </div>
        )}

        {/* Icon */}
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-blue-500 to-cyan-400 shadow-sm shadow-blue-500/15">
          {server.icons?.[0]?.url ? (
            <img
              src={server.icons[0].url}
              alt={server.name}
              className="h-5.5 w-5.5 object-contain"
              onError={(e) => {
                (e.target as HTMLImageElement).style.display = 'none';
                (e.target as HTMLImageElement).nextElementSibling?.classList.remove('hidden');
              }}
            />
          ) : null}
          <Cpu className={cn('h-5 w-5 text-white', server.icons?.[0]?.url && 'hidden')} />
        </div>

        {/* Title + meta */}
        <div className="flex-1 min-w-0">
          <h3 className="font-semibold text-sm truncate leading-tight">{server.name}</h3>
          <div className="flex items-center gap-1.5 mt-1">
            {/* Protocol pill */}
            <span className={cn(
              'inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium',
              protocolStyle?.bg || 'bg-gray-100 dark:bg-gray-800',
              protocolStyle?.text || 'text-gray-600 dark:text-gray-400'
            )}>
              <span className={cn('h-1.5 w-1.5 rounded-full', protocolStyle?.dot || 'bg-gray-400')} />
              {protocolLabel}
            </span>
            {isRestToMcp && (
              <span className="inline-flex items-center gap-0.5 rounded-full bg-amber-50 dark:bg-amber-950/40 text-amber-700 dark:text-amber-300 px-1.5 py-0.5 text-[10px] font-medium">
                <RefreshCw className="h-2.5 w-2.5" />
                HTTP {t('mcp.restToMcp', { defaultValue: '转换' })}
              </span>
            )}
            {version && (
              <span className="text-[10px] text-muted-foreground font-mono bg-muted/60 px-1 py-0.5 rounded">
                {version}
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Content */}
      <div className="px-4 pb-2 flex-1">
        <p className="text-xs text-muted-foreground line-clamp-2 leading-relaxed">
          {server.description || t('mcp.noDescription')}
        </p>

        {/* Capabilities + tools row */}
        {(capabilities.length > 0 || tools.length > 0) && (
          <div className="flex items-center gap-2 mt-2 flex-wrap">
            {/* Capability icons */}
            {capabilities.map((cap) => {
              const config = CAPABILITY_ICON[cap] || { icon: Zap, color: 'text-gray-400' };
              const Icon = config.icon;
              return (
                <Tooltip key={cap}>
                  <TooltipTrigger asChild>
                    <span className="inline-flex items-center gap-1 rounded-md bg-muted/60 px-1.5 py-0.5 text-[10px] font-medium text-muted-foreground">
                      <Icon className={cn('h-3 w-3', config.color)} />
                      {cap}
                    </span>
                  </TooltipTrigger>
                  <TooltipContent>{cap}</TooltipContent>
                </Tooltip>
              );
            })}

            {/* Tools count */}
            {tools.length > 0 && (
              <span className="inline-flex items-center gap-1 text-[10px] text-muted-foreground">
                <Wrench className="h-3 w-3" />
                {tools.length} {tools.length === 1 ? 'tool' : 'tools'}
              </span>
            )}
          </div>
        )}
      </div>

      {/* Footer */}
      <CardFooter className="px-4 py-1.5 border-t bg-muted/20 flex items-center justify-between [.border-t]:pt-1.5">
        <Badge
          className={cn(
            'text-[10px] px-1.5 py-0 h-4 font-medium border-0',
            server.enabled
              ? 'bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300'
              : 'bg-gray-100 text-gray-500 dark:bg-gray-800 dark:text-gray-400',
          )}
        >
          {server.enabled ? t('mcp.enabled') : t('mcp.disabled')}
        </Badge>

        <div className="flex items-center -mr-1" onClick={(e) => e.stopPropagation()}>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button variant="ghost" size="icon" className="h-6 w-6" onClick={() => onDetail?.(server.name)}>
                <ExternalLink className="h-3 w-3" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>{t('common.detail')}</TooltipContent>
          </Tooltip>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button variant="ghost" size="icon" className="h-6 w-6" onClick={() => onEdit?.(server.name)}>
                <Pencil className="h-3 w-3" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>{t('common.edit')}</TooltipContent>
          </Tooltip>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button variant="ghost" size="icon" className="h-6 w-6 text-destructive hover:text-destructive" onClick={() => onDelete?.(server.name)}>
                <Trash2 className="h-3 w-3" />
              </Button>
            </TooltipTrigger>
            <TooltipContent>{t('common.delete')}</TooltipContent>
          </Tooltip>
        </div>
      </CardFooter>
    </Card>
  );
}
