import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { Loader2 } from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
  DialogDescription,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { mcpApi } from '@/api/mcp';
import type { McpTool, McpToolMeta } from '@/types/mcp';

interface ImportMcpToolsDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onImport: (tools: McpTool[], toolsMeta: Record<string, McpToolMeta>) => void;
}

export default function ImportMcpToolsDialog({
  open,
  onOpenChange,
  onImport,
}: ImportMcpToolsDialogProps) {
  const { t } = useTranslation();
  const [transportType, setTransportType] = useState('mcp-sse');
  const [baseUrl, setBaseUrl] = useState('');
  const [endpoint, setEndpoint] = useState('');
  const [authToken, setAuthToken] = useState('');
  const [loading, setLoading] = useState(false);

  const handleImport = async () => {
    if (!baseUrl.trim()) {
      toast.error(t('mcp.addressRequired'));
      return;
    }

    setLoading(true);
    try {
      const response = await mcpApi.importToolsFromMcp({
        transportType,
        baseUrl: baseUrl.trim(),
        endpoint: endpoint.trim() || undefined,
        authToken: authToken.trim() || undefined,
      });

      const result = response as unknown as { data: McpTool[] };
      const tools: McpTool[] = result.data || [];

      if (tools.length === 0) {
        toast.error(t('mcp.noHealthyInstance'));
        return;
      }

      // Build default meta for imported tools
      const toolsMeta: Record<string, McpToolMeta> = {};
      for (const tool of tools) {
        toolsMeta[tool.name] = { enabled: true };
      }

      onImport(tools, toolsMeta);
      toast.success(t('mcp.importToolsSuccess', { count: tools.length }));
      onOpenChange(false);

      // Reset form
      setBaseUrl('');
      setEndpoint('');
      setAuthToken('');
    } catch {
      toast.error(t('mcp.importToolsFailed'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>{t('mcp.importToolsFromMCPTitle')}</DialogTitle>
          <DialogDescription />
        </DialogHeader>

        <div className="space-y-4">
          {/* Transport type */}
          <div className="space-y-2">
            <Label>{t('mcp.protocol')}</Label>
            <Select value={transportType} onValueChange={setTransportType}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="mcp-sse">SSE</SelectItem>
                <SelectItem value="mcp-streamable">Streamable HTTP</SelectItem>
              </SelectContent>
            </Select>
          </div>

          {/* Base URL */}
          <div className="space-y-2">
            <Label>
              {t('mcp.serverAddress')} <span className="text-destructive">*</span>
            </Label>
            <Input
              value={baseUrl}
              onChange={(e) => setBaseUrl(e.target.value)}
              placeholder="http://127.0.0.1:8080"
            />
          </div>

          {/* Endpoint */}
          <div className="space-y-2">
            <Label>{t('mcp.exportPath')}</Label>
            <Input
              value={endpoint}
              onChange={(e) => setEndpoint(e.target.value)}
              placeholder="/sse"
            />
          </div>

          {/* Auth token */}
          <div className="space-y-2">
            <Label>{t('mcp.authToken')}</Label>
            <Input
              value={authToken}
              onChange={(e) => setAuthToken(e.target.value)}
              placeholder={t('mcp.authTokenPlaceholder')}
              type="password"
            />
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={loading}>
            {t('common.cancel')}
          </Button>
          <Button onClick={handleImport} disabled={loading}>
            {loading && <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />}
            {loading ? t('mcp.importingTools') : t('mcp.importTools')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
