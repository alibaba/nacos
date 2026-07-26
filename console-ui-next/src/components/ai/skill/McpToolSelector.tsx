import { useState, useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { ChevronDown, ChevronRight, Search, Server, Loader2 } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Checkbox } from '@/components/ui/checkbox';
import { Input } from '@/components/ui/input';
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@/components/ui/collapsible';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { mcpApi } from '@/api/mcp';
import type { McpServerBasicInfo, McpTool } from '@/types/mcp';
import type { SelectedMcpTool } from '@/types/skill-ai';

interface McpToolSelectorProps {
  namespaceId: string;
  selectedTools: SelectedMcpTool[];
  onSelectionChange: (tools: SelectedMcpTool[]) => void;
  disabled?: boolean;
}

export function McpToolSelector({
  namespaceId,
  selectedTools,
  onSelectionChange,
  disabled = false,
}: McpToolSelectorProps) {
  const { t } = useTranslation();
  const [isOpen, setIsOpen] = useState(false);
  const [servers, setServers] = useState<McpServerBasicInfo[]>([]);
  const [serversLoading, setServersLoading] = useState(false);
  const [selectedServer, setSelectedServer] = useState<string>('');
  const [tools, setTools] = useState<McpTool[]>([]);
  const [toolsLoading, setToolsLoading] = useState(false);
  const [searchKeyword, setSearchKeyword] = useState('');

  // Load MCP servers on open
  useEffect(() => {
    if (!isOpen || servers.length > 0) return;
    setServersLoading(true);
    mcpApi
      .listMcpServers({ namespaceId, pageNo: 1, pageSize: 200 })
      .then((res) => {
        const data = (res as unknown as { data: { pageItems: McpServerBasicInfo[] } }).data;
        setServers(data?.pageItems || []);
      })
      .catch(() => {
        setServers([]);
      })
      .finally(() => setServersLoading(false));
  }, [isOpen, namespaceId, servers.length]);

  // Load tools when server changes
  useEffect(() => {
    if (!selectedServer) {
      setTools([]);
      return;
    }
    setToolsLoading(true);
    mcpApi
      .getMcpServer({ mcpName: selectedServer, namespaceId })
      .then((res) => {
        const data = (res as unknown as { data: { toolSpec?: { tools?: McpTool[] } } }).data;
        setTools(data?.toolSpec?.tools || []);
      })
      .catch(() => {
        setTools([]);
      })
      .finally(() => setToolsLoading(false));
  }, [selectedServer, namespaceId]);

  const filteredTools = useMemo(() => {
    if (!searchKeyword.trim()) return tools;
    const kw = searchKeyword.toLowerCase();
    return tools.filter(
      (tool) =>
        tool.name?.toLowerCase().includes(kw) ||
        tool.description?.toLowerCase().includes(kw),
    );
  }, [tools, searchKeyword]);

  const handleToolToggle = (tool: McpTool, checked: boolean) => {
    if (checked) {
      onSelectionChange([
        ...selectedTools,
        {
          name: tool.name,
          description: tool.description,
          inputSchema: tool.inputSchema,
        },
      ]);
    } else {
      onSelectionChange(selectedTools.filter((t) => t.name !== tool.name));
    }
  };

  const handleServerChange = (value: string) => {
    setSelectedServer(value);
    setSearchKeyword('');
    // Clear tools from the old server
    onSelectionChange(
      selectedTools.filter((t) =>
        tools.every((serverTool) => serverTool.name !== t.name),
      ),
    );
  };

  return (
    <Collapsible open={isOpen} onOpenChange={setIsOpen}>
      <CollapsibleTrigger
        className="flex w-full items-center gap-2 text-sm font-medium text-muted-foreground hover:text-foreground transition-colors py-1"
        disabled={disabled}
      >
        {isOpen ? (
          <ChevronDown className="h-3.5 w-3.5" />
        ) : (
          <ChevronRight className="h-3.5 w-3.5" />
        )}
        <Server className="h-3.5 w-3.5" />
        {t('skill.mcpToolsOptional')}
        {selectedTools.length > 0 && (
          <Badge variant="secondary" className="text-[10px] px-1.5 py-0 h-4 ml-1">
            {t('skill.selectedToolsCount', { count: selectedTools.length })}
          </Badge>
        )}
      </CollapsibleTrigger>

      <CollapsibleContent className="mt-2 space-y-3 pl-5">
        {/* Server selector */}
        <Select
          value={selectedServer}
          onValueChange={handleServerChange}
          disabled={disabled || serversLoading}
        >
          <SelectTrigger className="h-8 text-xs">
            <SelectValue
              placeholder={
                serversLoading
                  ? t('common.loading')
                  : t('skill.selectMcpServer')
              }
            />
          </SelectTrigger>
          <SelectContent>
            {servers.map((server) => (
              <SelectItem key={server.name} value={server.name}>
                <span className="flex items-center gap-2">
                  <span>{server.name}</span>
                  {server.description && (
                    <span className="text-muted-foreground text-[10px] truncate max-w-[200px]">
                      {server.description}
                    </span>
                  )}
                </span>
              </SelectItem>
            ))}
          </SelectContent>
        </Select>

        {/* Tools list */}
        {selectedServer && (
          <>
            {/* Search */}
            <div className="relative">
              <Search className="absolute left-2 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground" />
              <Input
                value={searchKeyword}
                onChange={(e) => setSearchKeyword(e.target.value)}
                placeholder={t('skill.searchTools')}
                className="h-7 pl-7 text-xs"
                disabled={disabled || toolsLoading}
              />
            </div>

            {toolsLoading ? (
              <div className="flex items-center justify-center py-4 text-xs text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin mr-2" />
                {t('skill.loadingTools')}
              </div>
            ) : filteredTools.length === 0 ? (
              <p className="text-xs text-muted-foreground text-center py-3">
                {t('skill.noToolsAvailable')}
              </p>
            ) : (
              <div className="max-h-[200px] overflow-y-auto space-y-1.5 pr-1">
                {filteredTools.map((tool) => {
                  const isChecked = selectedTools.some(
                    (t) => t.name === tool.name,
                  );
                  return (
                    <label
                      key={tool.name}
                      className="flex items-start gap-2 cursor-pointer rounded-md px-2 py-1.5 hover:bg-muted/50 transition-colors"
                    >
                      <Checkbox
                        checked={isChecked}
                        onCheckedChange={(checked) =>
                          handleToolToggle(tool, !!checked)
                        }
                        disabled={disabled}
                        className="mt-0.5"
                      />
                      <div className="min-w-0 flex-1">
                        <p className="text-xs font-medium leading-tight">
                          {tool.name}
                        </p>
                        {tool.description && (
                          <p className="text-[10px] text-muted-foreground leading-snug mt-0.5 line-clamp-2">
                            {tool.description}
                          </p>
                        )}
                      </div>
                    </label>
                  );
                })}
              </div>
            )}
          </>
        )}
      </CollapsibleContent>
    </Collapsible>
  );
}
