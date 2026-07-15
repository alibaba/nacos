import { useState, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Search, Wrench } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { cn } from '@/lib/utils';
import type { McpTool, McpToolMeta } from '@/types/mcp';

interface ToolListProps {
  tools: McpTool[];
  toolsMeta: Record<string, McpToolMeta>;
  selectedTool: string | null;
  onSelect: (name: string) => void;
}

export default function ToolList({ tools, toolsMeta, selectedTool, onSelect }: ToolListProps) {
  const { t } = useTranslation();
  const [search, setSearch] = useState('');

  const filtered = useMemo(() => {
    if (!search.trim()) return tools;
    const q = search.toLowerCase();
    return tools.filter(
      (tool) =>
        tool.name.toLowerCase().includes(q) ||
        (tool.description || '').toLowerCase().includes(q)
    );
  }, [tools, search]);

  return (
    <div className="flex flex-col h-full">
      {/* Search */}
      <div className="p-3 border-b bg-muted/20">
        <div className="relative">
          <Search className="absolute left-2 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            className="h-9 pl-8 text-sm"
            placeholder={t('mcp.searchTools')}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
      </div>

      {/* List */}
      <div className="flex-1 overflow-y-auto overflow-x-hidden">
        {filtered.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-8 text-muted-foreground">
            <Wrench className="h-8 w-8 mb-2 opacity-30" />
            <span className="text-sm">{t('mcp.noTools')}</span>
          </div>
        ) : (
          <div className="p-2 overflow-hidden">
            {filtered.map((tool) => {
              const meta = toolsMeta[tool.name];
              const isEnabled = meta?.enabled !== false;
              const isSelected = selectedTool === tool.name;
              const paramCount = Object.keys(
                (tool.inputSchema as Record<string, unknown>)?.properties || {}
              ).length;

              return (
                <button
                  key={tool.name}
                  className={cn(
                    'w-full min-w-0 text-left rounded-md px-2.5 py-2 mb-0.5 transition-all overflow-hidden',
                    'hover:bg-muted/50',
                    isSelected && 'bg-primary/10 shadow-sm ring-1 ring-primary/20'
                  )}
                  onClick={() => onSelect(tool.name)}
                >
                  <div className="flex items-center gap-1.5 min-w-0 overflow-hidden">
                    <Wrench className={cn(
                      'h-3.5 w-3.5 shrink-0',
                      isSelected ? 'text-primary' : 'text-muted-foreground/50'
                    )} />
                    <span className={cn(
                      'text-sm font-medium truncate',
                      isSelected && 'text-primary'
                    )}>{tool.name}</span>
                    {!isEnabled && (
                      <Badge variant="secondary" className="text-[10px] h-4 px-1 shrink-0">
                        OFF
                      </Badge>
                    )}
                  </div>
                  {tool.description && (
                    <p className="text-xs text-muted-foreground mt-0.5 ml-5 overflow-hidden text-ellipsis whitespace-nowrap">
                      {tool.description}
                    </p>
                  )}
                  {paramCount > 0 && (
                    <span className="text-xs text-muted-foreground/70 ml-5 block overflow-hidden text-ellipsis whitespace-nowrap">
                      {t('mcp.toolParams', { count: paramCount })}
                    </span>
                  )}
                </button>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
