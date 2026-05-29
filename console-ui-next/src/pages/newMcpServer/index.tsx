import { useEffect, useMemo, useState, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import {
  ArrowLeft,
  Plus,
  Trash2,
  Shield,
  Info,
  Server,
  Terminal,
  Globe,
  Cpu,
  Radio,
} from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Separator } from '@/components/ui/separator';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import { useNamespaceStore } from '@/stores/namespace-store';
import { useMcpStore } from '@/stores/mcp-store';
import { mcpApi } from '@/api/mcp';
import { serviceApi } from '@/api/service';
import type {
  McpProtocol,
  McpSecurityScheme,
  McpServerDetailInfo,
  McpToolSpecification,
  McpVersionDetail,
} from '@/types/mcp';
import { cn } from '@/lib/utils';
import ToolManager from './tool-manager';
import { resolveMcpEndpointUrl, shouldUseExistingService } from './endpoint-utils';

const PROTOCOL_CARD_CONFIG: Record<string, { icon: typeof Terminal; label: string; color: string; bg: string; dot: string; ring: string }> = {
  stdio: {
    icon: Terminal,
    label: 'Stdio',
    color: 'text-purple-600 dark:text-purple-400',
    bg: 'bg-purple-50 dark:bg-purple-950/40',
    dot: 'bg-purple-500',
    ring: 'ring-purple-500/30',
  },
  'mcp-sse': {
    icon: Radio,
    label: 'SSE',
    color: 'text-blue-600 dark:text-blue-400',
    bg: 'bg-blue-50 dark:bg-blue-950/40',
    dot: 'bg-blue-500',
    ring: 'ring-blue-500/30',
  },
  'mcp-streamable': {
    icon: Globe,
    label: 'Streamable',
    color: 'text-cyan-600 dark:text-cyan-400',
    bg: 'bg-cyan-50 dark:bg-cyan-950/40',
    dot: 'bg-cyan-500',
    ring: 'ring-cyan-500/30',
  },
};

type FormMode = 'create' | 'edit' | 'version';

interface SecurityExtensions {
  'server.defaultDownstreamSecurity'?: {
    id?: string;
    passthrough?: boolean;
  };
  'server.defaultUpstreamSecurity'?: {
    id?: string;
    credential?: string;
  };
  [key: string]: unknown;
}

export default function NewMcpServerPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { currentNamespace } = useNamespaceStore();
  const { currentMcp: cachedMcp } = useMcpStore();

  const mode = (searchParams.get('mode') as FormMode) || 'create';
  const editMcpName = searchParams.get('mcpName') || '';
  const namespaceId = searchParams.get('namespaceId') || currentNamespace || 'public';

  const isEdit = mode === 'edit';
  const isVersion = mode === 'version';

  // Check if store already has the data we need (e.g. navigating from detail page)
  const hasCachedData = (isEdit || isVersion) && cachedMcp?.name === editMcpName;

  // Form state
  const [serverName, setServerName] = useState('');
  const [frontProtocol, setFrontProtocol] = useState<McpProtocol>('stdio');
  const [description, setDescription] = useState('');
  const [version, setVersion] = useState('1.0.0');
  const [enabled, setEnabled] = useState(true);

  // Non-stdio config
  const [restToMcpSwitch, setRestToMcpSwitch] = useState(false);
  const [useExistService, setUseExistService] = useState(false);
  const [mcpEndpointUrl, setMcpEndpointUrl] = useState('');
  const [address, setAddress] = useState('');
  const [port, setPort] = useState('');
  const [transportProtocol, setTransportProtocol] = useState('http');
  const [exportPath, setExportPath] = useState('');
  const [selectedService, setSelectedService] = useState(''); // format: groupName@@serviceName
  const [serviceList, setServiceList] = useState<{ label: string; value: string }[]>([]);

  // Stdio config
  const [localServerConfig, setLocalServerConfig] = useState('');

  // Security schemes
  const [securitySchemes, setSecuritySchemes] = useState<McpSecurityScheme[]>([]);
  const [securityExtensions, setSecurityExtensions] = useState<SecurityExtensions>({});

  // Tool specification
  const [toolSpec, setToolSpec] = useState<McpToolSpecification>({});

  // All versions (for strategy availability computation)
  const [allVersions, setAllVersions] = useState<McpVersionDetail[]>([]);

  // Update flags
  const [publishStrategy, setPublishStrategy] = useState<'new-version' | 'set-latest' | 'edit-current'>(
    isVersion ? 'new-version' : 'edit-current'
  );

  // UI state
  const [loading, setLoading] = useState(false);
  const [initLoading, setInitLoading] = useState((isEdit || isVersion) && !hasCachedData);
  const [strategyDialogOpen, setStrategyDialogOpen] = useState(false);

  // Original detail data for edit
  const originalMcp = useRef<McpServerDetailInfo | null>(null);

  const isStdio = frontProtocol === 'stdio';

  // Publish strategy availability based on version number
  const strategyAvailability = useMemo(() => {
    const trimmed = version.trim();
    const existing = allVersions.find((v) => v.version === trimmed);
    const isNew = !existing;
    const isLatest = existing?.is_latest === true;

    return {
      'new-version': {
        available: isNew,
        reason: isNew ? undefined : t('mcp.strategyReasonVersionExists', { defaultValue: '版本已存在' }),
      },
      'set-latest': {
        available: !isNew && !isLatest,
        reason: isNew
          ? t('mcp.strategyReasonVersionNotExists', { defaultValue: '版本不存在' })
          : isLatest
            ? t('mcp.strategyReasonAlreadyLatest', { defaultValue: '已是最新版本' })
            : undefined,
      },
      'edit-current': {
        available: !isNew,
        reason: isNew ? t('mcp.strategyReasonVersionNotExists', { defaultValue: '版本不存在' }) : undefined,
      },
    };
  }, [version, allVersions, t]);

  // Auto-switch strategy when current becomes unavailable
  useEffect(() => {
    if (!isEdit && !isVersion) return;
    if (!strategyAvailability[publishStrategy].available) {
      const order = ['new-version', 'set-latest', 'edit-current'] as const;
      const first = order.find((s) => strategyAvailability[s].available);
      if (first) setPublishStrategy(first);
    }
  }, [strategyAvailability, publishStrategy, isEdit, isVersion]);

  // Load existing data for edit/version modes
  useEffect(() => {
    if ((isEdit || isVersion) && editMcpName) {
      // If store already has matching data (e.g. from detail page), use it directly
      if (cachedMcp && cachedMcp.name === editMcpName) {
        originalMcp.current = cachedMcp;
        populateForm(cachedMcp);
        setInitLoading(false);
        return;
      }
      // Otherwise fetch from API
      setInitLoading(true);
      mcpApi
        .getMcpServer({ mcpName: editMcpName, namespaceId })
        .then((response) => {
          const data = (response as unknown as { data: McpServerDetailInfo }).data;
          originalMcp.current = data;
          populateForm(data);
        })
        .catch(() => {
          toast.error(t('mcp.loadFailed'));
        })
        .finally(() => {
          setInitLoading(false);
        });
    }
  }, [editMcpName, namespaceId, isEdit, isVersion]);

  // Fetch service list for "use existing service" mode
  useEffect(() => {
    serviceApi
      .listServices({ namespaceId, pageNo: 1, pageSize: 100 })
      .then((response) => {
        const result = (response as unknown as { data: { pageItems: Array<{ name: string; groupName: string }> } }).data;
        if (result?.pageItems) {
          setServiceList(
            result.pageItems.map((item) => ({
              label: `${item.groupName} / ${item.name}`,
              value: `${item.groupName}@@${item.name}`,
            }))
          );
        }
      })
      .catch(() => {
        // silently ignore - user can still type manually
      });
  }, [namespaceId]);

  const populateForm = (data: McpServerDetailInfo) => {
    setServerName(data.name);
    setFrontProtocol(data.frontProtocol || 'stdio');
    setDescription(data.description || '');
    setVersion(data.versionDetail?.version || data.version || '1.0.0');
    setAllVersions(data.allVersions || []);
    setEnabled(data.enabled);

    const resolvedFrontProtocol = data.frontProtocol || 'stdio';
    if (resolvedFrontProtocol !== 'stdio') {
      // Determine restToMcpSwitch based on backend protocol field (consistent with original)
      const isRestToMcp = data.protocol === 'http' || data.protocol === 'https';
      setRestToMcpSwitch(isRestToMcp);
      setSelectedService('');
      setMcpEndpointUrl('');
      setAddress('');
      setPort('');
      setExportPath(data.remoteServerConfig?.exportPath || '');

      const useExistingService = isRestToMcp && shouldUseExistingService(data);
      setUseExistService(useExistingService);

      if (useExistingService) {
        const ref = data.remoteServerConfig!.serviceRef!;
        setSelectedService(`${ref.groupName || 'DEFAULT_GROUP'}@@${ref.serviceName}`);
        setTransportProtocol(ref.transportProtocol || 'http');
        setExportPath(data.remoteServerConfig!.exportPath || '');
      } else if (isRestToMcp && (data.backendEndpoints?.length ?? 0) > 0) {
        const ep = data.backendEndpoints![0];
        setAddress(ep.address || '');
        setPort(String(ep.port || ''));
        setTransportProtocol(
          ep.protocol || data.remoteServerConfig?.serviceRef?.transportProtocol || 'http'
        );
      } else {
        // Non-restToMcp: reconstruct endpoint URL from frontend endpoints or generated backend endpoint.
        setMcpEndpointUrl(resolveMcpEndpointUrl(data));
      }
    } else {
      // Stdio: local server config
      if (data.localServerConfig) {
        setLocalServerConfig(JSON.stringify(data.localServerConfig, null, 2));
      }
    }

    // Security schemes
    if (data.toolSpec?.securitySchemes) {
      setSecuritySchemes(data.toolSpec.securitySchemes);
    }
    if (data.toolSpec?.extensions) {
      const ext: SecurityExtensions = {};
      // Preserve all extensions (including non-security ones)
      for (const [key, value] of Object.entries(data.toolSpec.extensions)) {
        if (key === 'server.defaultDownstreamSecurity') {
          ext[key] = value as SecurityExtensions['server.defaultDownstreamSecurity'];
        } else if (key === 'server.defaultUpstreamSecurity') {
          ext[key] = value as SecurityExtensions['server.defaultUpstreamSecurity'];
        } else {
          ext[key] = value;
        }
      }
      setSecurityExtensions(ext);
    }

    // Tool specification (tools, toolsMeta, etc.)
    if (data.toolSpec) {
      setToolSpec(data.toolSpec);
    }

    if (isVersion) {
      // For new version, bump version
      const oldVer = data.versionDetail?.version || data.version || '1.0.0';
      const parts = oldVer.split('.');
      if (parts.length === 3) {
        parts[2] = String(Number(parts[2]) + 1);
        setVersion(parts.join('.'));
      }
    }
  };

  // Validation
  const validate = (): boolean => {
    if (!serverName.trim()) {
      toast.error(t('mcp.serverNameRequired'));
      return false;
    }
    if (!version.trim()) {
      toast.error(t('mcp.versionRequired'));
      return false;
    }
    if (isStdio) {
      if (!localServerConfig.trim()) {
        toast.error(t('mcp.localConfigRequired'));
        return false;
      }
      try {
        JSON.parse(localServerConfig);
      } catch {
        toast.error(t('mcp.invalidJson'));
        return false;
      }
    } else if (!restToMcpSwitch) {
      if (!mcpEndpointUrl.trim()) {
        toast.error(t('mcp.mcpEndpointRequired'));
        return false;
      }
    } else if (!useExistService) {
      if (!address.trim()) {
        toast.error(t('mcp.addressRequired'));
        return false;
      }
      if (!port.trim()) {
        toast.error(t('mcp.portRequired'));
        return false;
      }
    }
    return true;
  };

  // Build submit data
  const buildSubmitData = () => {
    // Build serverSpecification
    const serverSpec: Record<string, unknown> = {
      name: serverName.trim(),
      frontProtocol,
      protocol: isStdio ? 'stdio' : restToMcpSwitch ? transportProtocol : frontProtocol,
      description: description.trim() || undefined,
      enabled,
      versionDetail: { version: version.trim() },
    };

    if (isStdio) {
      serverSpec.localServerConfig = JSON.parse(localServerConfig);
    } else if (restToMcpSwitch && useExistService) {
      const [refGroup, refServiceName] = selectedService.includes('@@')
        ? selectedService.split('@@')
        : ['DEFAULT_GROUP', selectedService];
      serverSpec.remoteServerConfig = {
        serviceRef: {
          serviceName: refServiceName,
          groupName: refGroup,
          namespaceId,
          transportProtocol,
        },
        exportPath: exportPath || undefined,
      };
    } else {
      // Non-stdio: always include remoteServerConfig for backend compatibility
      serverSpec.remoteServerConfig = {
        exportPath: exportPath || undefined,
      };
    }

    // Build endpointSpecification
    let endpointSpec: string | undefined;
    if (!isStdio) {
      if (restToMcpSwitch && !useExistService) {
        endpointSpec = JSON.stringify({
          type: 'DIRECT',
          data: {
            transportProtocol,
            address: address.trim(),
            port: port.trim(),
          },
        });
      } else if (restToMcpSwitch && useExistService) {
        const [refGroup, refServiceName] = selectedService.includes('@@')
          ? selectedService.split('@@')
          : ['DEFAULT_GROUP', selectedService];
        endpointSpec = JSON.stringify({
          type: 'REF',
          data: {
            serviceName: refServiceName,
            groupName: refGroup,
            namespaceId,
            transportProtocol,
          },
        });
      } else {
        // Non-restToMcp: parse the MCP endpoint URL
        try {
          const url = new URL(mcpEndpointUrl.trim());
          const urlTransportProtocol = url.protocol.replace(':', '');
          const urlPath = url.pathname !== '/' ? url.pathname : undefined;

          // Update serverSpec with remoteServerConfig including exportPath
          serverSpec.remoteServerConfig = {
            exportPath: urlPath || '/',
          };

          endpointSpec = JSON.stringify({
            type: 'DIRECT',
            data: {
              address: url.hostname,
              port: url.port || (url.protocol === 'https:' ? '443' : '80'),
              transportProtocol: urlTransportProtocol,
            },
          });
        } catch {
          // fallback: just pass it as-is
          endpointSpec = JSON.stringify({
            type: 'DIRECT',
            data: { address: mcpEndpointUrl.trim() },
          });
        }
      }
    }

    // Build toolSpecification
    let toolSpecStr: string | undefined;
    const mergedToolSpec: Record<string, unknown> = { ...toolSpec };
    if (securitySchemes.length > 0) mergedToolSpec.securitySchemes = securitySchemes;
    if (Object.keys(securityExtensions).length > 0) mergedToolSpec.extensions = securityExtensions;
    if (
      Object.keys(mergedToolSpec).length > 0 &&
      (mergedToolSpec.tools || mergedToolSpec.securitySchemes || mergedToolSpec.extensions)
    ) {
      toolSpecStr = JSON.stringify(mergedToolSpec);
    }

    return {
      mcpName: serverName.trim(),
      namespaceId,
      serverSpecification: JSON.stringify(serverSpec),
      toolSpecification: toolSpecStr,
      endpointSpecification: endpointSpec,
    };
  };

  const handleSubmit = async () => {
    if (!validate()) return;
    // For edit/version mode, open strategy dialog first
    if (isEdit || isVersion) {
      setStrategyDialogOpen(true);
      return;
    }
    // Create mode: submit directly
    await doSubmit();
  };

  const doSubmit = async () => {
    setLoading(true);
    try {
      const data = buildSubmitData();
      if (isEdit || isVersion) {
        let latest: boolean;
        let overrideExisting: boolean;
        if (publishStrategy === 'new-version') {
          latest = false;
          overrideExisting = false;
        } else if (publishStrategy === 'set-latest') {
          latest = true;
          overrideExisting = true;
        } else {
          // edit-current: maintain latest flag if already latest
          overrideExisting = true;
          const currentVersionInfo = allVersions.find(
            (v) => v.version === version.trim()
          );
          latest = currentVersionInfo?.is_latest ?? false;
        }
        await mcpApi.updateMcpServer({
          ...data,
          latest,
          overrideExisting,
        });
        toast.success(t('mcp.updateSuccess'));
      } else {
        await mcpApi.createMcpServer(data);
        toast.success(t('mcp.createSuccess'));
      }
      setStrategyDialogOpen(false);
      navigate('/mcpServerManagement');
    } catch {
      // handled by interceptor
    } finally {
      setLoading(false);
    }
  };

  // Security scheme helpers
  const addSecurityScheme = () => {
    setSecuritySchemes((prev) => [
      ...prev,
      { id: '', type: 'http', scheme: 'bearer' },
    ]);
  };

  const removeSecurityScheme = (index: number) => {
    const removedId = securitySchemes[index]?.id;
    setSecuritySchemes((prev) => prev.filter((_, i) => i !== index));
    // Clear downstream/upstream references to the removed scheme
    if (removedId) {
      setSecurityExtensions((prev) => {
        const next = { ...prev };
        if (next['server.defaultDownstreamSecurity']?.id === removedId) {
          next['server.defaultDownstreamSecurity'] = { id: undefined, passthrough: false };
        }
        if (next['server.defaultUpstreamSecurity']?.id === removedId) {
          next['server.defaultUpstreamSecurity'] = { id: undefined, credential: '' };
        }
        return next;
      });
    }
  };

  const updateSecurityScheme = (index: number, field: keyof McpSecurityScheme, value: string) => {
    const oldId = securitySchemes[index]?.id;
    setSecuritySchemes((prev) =>
      prev.map((s, i) => {
        if (i !== index) return s;
        const updated = { ...s, [field]: value };
        // Reset conditional fields when type changes
        if (field === 'type') {
          if (value === 'http') {
            updated.scheme = 'bearer';
            delete updated.in;
            delete updated.name;
          } else {
            delete updated.scheme;
            updated.in = 'header';
            updated.name = '';
          }
        }
        return updated;
      })
    );
    // When scheme ID changes, update downstream/upstream references
    if (field === 'id' && oldId && oldId !== value) {
      setSecurityExtensions((prev) => {
        const next = { ...prev };
        if (next['server.defaultDownstreamSecurity']?.id === oldId) {
          next['server.defaultDownstreamSecurity'] = {
            ...next['server.defaultDownstreamSecurity'],
            id: value || undefined,
          };
        }
        if (next['server.defaultUpstreamSecurity']?.id === oldId) {
          next['server.defaultUpstreamSecurity'] = {
            ...next['server.defaultUpstreamSecurity'],
            id: value || undefined,
          };
        }
        return next;
      });
    }
  };

  // Parse MCP endpoint URL
  const handleEndpointUrlParse = (url: string) => {
    setMcpEndpointUrl(url);
  };

  if (initLoading) {
    return (
      <div className="space-y-6">
        <div className="flex items-center gap-4">
          <Skeleton className="h-9 w-9 rounded-md" />
          <Skeleton className="h-8 w-48" />
        </div>
        <Skeleton className="h-[400px] w-full" />
      </div>
    );
  }

  const pageTitle = isEdit
    ? t('mcp.editServer')
    : isVersion
      ? t('mcp.newVersion')
      : t('mcp.createServer');

  const protoConfig = PROTOCOL_CARD_CONFIG[frontProtocol];

  return (
    <div className="flex flex-col min-h-[calc(100vh-120px)]">
      <div className="space-y-5 grow">
      {/* ===== Hero Header ===== */}
      <div className="relative rounded-xl border bg-card overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-br from-primary/[0.04] via-transparent to-blue-500/[0.03]" />
        <div className="absolute top-0 right-0 w-64 h-64 bg-gradient-to-bl from-primary/[0.06] to-transparent rounded-full -translate-y-1/2 translate-x-1/3" />

        <div className="relative px-5 py-4">
          {/* Top bar: back */}
          <div className="flex items-center justify-between mb-4">
            <Button
              variant="ghost"
              size="sm"
              className="h-7 gap-1.5 text-muted-foreground hover:text-foreground -ml-2"
              onClick={() => navigate(-1)}
            >
              <ArrowLeft className="h-3.5 w-3.5" />
              {t('mcp.backToList')}
            </Button>
          </div>

          {/* Server identity */}
          <div className="flex items-start gap-4">
            <div className="flex h-14 w-14 shrink-0 items-center justify-center rounded-xl bg-gradient-to-br from-blue-500 to-cyan-400 shadow-lg shadow-blue-500/20">
              <Cpu className="h-7 w-7 text-white" />
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2.5 mb-1">
                <h1 className="text-xl font-bold tracking-tight">{pageTitle}</h1>
                {protoConfig && (
                  <span className={cn(
                    'inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-medium',
                    protoConfig.bg, protoConfig.color
                  )}>
                    <span className={cn('h-1.5 w-1.5 rounded-full', protoConfig.dot)} />
                    {protoConfig.label}
                  </span>
                )}
                {version && (
                  <span className="text-xs text-muted-foreground font-mono bg-muted/60 px-1.5 py-0.5 rounded">
                    v{version}
                  </span>
                )}
              </div>
              <p className="text-sm text-muted-foreground leading-relaxed max-w-2xl">
                {isEdit
                  ? t('mcp.editServerDesc', { defaultValue: '修改 MCP Server 的配置信息' })
                  : isVersion
                    ? t('mcp.newVersionDesc', { defaultValue: '基于当前版本创建新版本' })
                    : t('mcp.createServerDesc', { defaultValue: '配置并发布一个新的 MCP Server' })}
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* Basic Info */}
      <Card className="overflow-hidden py-0 gap-0">
        <div className="px-5 py-3.5 border-b bg-muted/30">
          <h2 className="text-sm font-semibold flex items-center gap-2">
            <Server className="h-4 w-4 text-muted-foreground" />
            {t('mcp.basicInfo')}
          </h2>
        </div>
        <CardContent className="p-5 space-y-5">
          {/* Row 1: Server Name (2/3) + Version (1/3) */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
            <div className="md:col-span-2 space-y-2.5">
              <Label htmlFor="serverName">
                {t('mcp.serverName')} <span className="text-destructive ml-1">*</span>
              </Label>
              <Input
                id="serverName"
                value={serverName}
                onChange={(e) => setServerName(e.target.value)}
                placeholder={t('mcp.serverName')}
                disabled={isEdit || isVersion}
                maxLength={255}
              />
            </div>
            <div className="space-y-2.5">
              <Label htmlFor="version">
                {t('mcp.version')} <span className="text-destructive ml-1">*</span>
              </Label>
              <Input
                id="version"
                value={version}
                onChange={(e) => setVersion(e.target.value)}
                placeholder={t('mcp.versionPlaceholder')}
              />
            </div>
          </div>

          {/* Row 2: Protocol selector (card-style) + Enable */}
          <div className="grid grid-cols-1 md:grid-cols-[1fr_auto] gap-5 items-end">
            <div className="space-y-2.5">
              <Label>
                {t('mcp.protocol')} <span className="text-destructive ml-1">*</span>
              </Label>
              <RadioGroup
                value={frontProtocol}
                onValueChange={(v) => setFrontProtocol(v as McpProtocol)}
                className="grid grid-cols-3 gap-3"
                disabled={isEdit || isVersion}
              >
                {(['stdio', 'mcp-sse', 'mcp-streamable'] as const).map((proto) => {
                  const cfg = PROTOCOL_CARD_CONFIG[proto];
                  const Icon = cfg.icon;
                  const isSelected = frontProtocol === proto;
                  return (
                    <Label
                      key={proto}
                      htmlFor={`proto-${proto}`}
                      className={cn(
                        'flex items-center gap-3 rounded-lg border px-4 py-3 cursor-pointer transition-all',
                        isSelected
                          ? cn('bg-primary/[0.06] shadow-sm ring-1', cfg.ring, 'border-transparent')
                          : 'hover:bg-muted/50',
                        (isEdit || isVersion) && 'opacity-60 cursor-not-allowed'
                      )}
                    >
                      <RadioGroupItem value={proto} id={`proto-${proto}`} className="sr-only" />
                      <div className={cn(
                        'flex h-9 w-9 shrink-0 items-center justify-center rounded-lg',
                        isSelected ? cfg.bg : 'bg-muted/50'
                      )}>
                        <Icon className={cn('h-4 w-4', isSelected ? cfg.color : 'text-muted-foreground')} />
                      </div>
                      <div>
                        <div className={cn('text-sm font-medium', isSelected && 'text-foreground')}>
                          {cfg.label}
                        </div>
                        <div className="text-xs text-muted-foreground">
                          {t(`mcp.protocol${proto === 'stdio' ? 'Stdio' : proto === 'mcp-sse' ? 'Sse' : 'Streamable'}`)}
                        </div>
                      </div>
                    </Label>
                  );
                })}
              </RadioGroup>
            </div>
            <div className="flex items-center gap-3 h-9">
              <Switch checked={enabled} onCheckedChange={setEnabled} />
              <Label>{enabled ? t('mcp.enabled') : t('mcp.disabled')}</Label>
            </div>
          </div>

          {/* Row 3: Description full width */}
          <div className="space-y-2.5">
            <Label htmlFor="description">{t('mcp.description')}</Label>
            <Textarea
              id="description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder={t('mcp.descriptionPlaceholder')}
              rows={3}
            />
          </div>
        </CardContent>
      </Card>

      {/* Protocol-specific config */}
      {isStdio ? (
        /* Stdio: Local Server Config */
        <Card className="overflow-hidden py-0 gap-0">
          <div className="px-5 py-3.5 border-b bg-muted/30">
            <h2 className="text-sm font-semibold flex items-center gap-2">
              <Terminal className="h-4 w-4 text-purple-500" />
              {t('mcp.localServerConfig')}
            </h2>
          </div>
          <CardContent className="p-5 space-y-4">
            <div className="flex items-center gap-2 text-xs text-muted-foreground">
              <Info className="h-3.5 w-3.5" />
              {t('mcp.localServerConfigTip')}
            </div>
            <Textarea
              value={localServerConfig}
              onChange={(e) => setLocalServerConfig(e.target.value)}
              placeholder='{\n  "command": "npx",\n  "args": ["-y", "@modelcontextprotocol/server-demo"]\n}'
              rows={12}
              className="font-mono text-sm"
            />
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                try {
                  setLocalServerConfig(JSON.stringify(JSON.parse(localServerConfig), null, 2));
                } catch {
                  toast.error(t('mcp.invalidJson'));
                }
              }}
            >
              {t('mcp.formatJson')}
            </Button>
          </CardContent>
        </Card>
      ) : (
        /* Non-stdio: Server connection config */
        <Card className="overflow-hidden py-0 gap-0">
          <div className="px-5 py-3.5 border-b bg-muted/30">
            <h2 className="text-sm font-semibold flex items-center gap-2">
              <Globe className="h-4 w-4 text-blue-500" />
              {t('mcp.remoteServerConfig')}
            </h2>
          </div>
          <CardContent className="p-5 space-y-5">
            {/* REST to MCP switch */}
            <div className="flex items-center justify-between p-3 rounded-lg bg-muted/30">
              <div className="flex items-center gap-2">
                <Label>{t('mcp.restToMcpSwitch')}</Label>
                <Tooltip>
                  <TooltipTrigger>
                    <Info className="h-3.5 w-3.5 text-muted-foreground" />
                  </TooltipTrigger>
                  <TooltipContent>{t('mcp.restToMcpSwitchTip')}</TooltipContent>
                </Tooltip>
              </div>
              <Switch checked={restToMcpSwitch} onCheckedChange={setRestToMcpSwitch} disabled={isEdit || isVersion} />
            </div>

            <Separator />

            {restToMcpSwitch ? (
              <>
                {/* Use existing service or direct connect */}
                <RadioGroup
                  value={useExistService ? 'existing' : 'direct'}
                  onValueChange={(v) => setUseExistService(v === 'existing')}
                  className="flex gap-6"
                >
                  <div className="flex items-center gap-2">
                    <RadioGroupItem value="existing" id="use-existing" />
                    <Label htmlFor="use-existing" className="cursor-pointer font-normal">
                      {t('mcp.useExistServiceOption')}
                    </Label>
                  </div>
                  <div className="flex items-center gap-2">
                    <RadioGroupItem value="direct" id="use-direct" />
                    <Label htmlFor="use-direct" className="cursor-pointer font-normal">
                      {t('mcp.directConnectOption')}
                    </Label>
                  </div>
                </RadioGroup>

                {useExistService ? (
                  /* Existing service mode */
                  <div className="space-y-5">
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
                      <div className="space-y-2.5">
                        <Label>{t('mcp.selectService')}</Label>
                        {serviceList.length > 0 ? (
                          <Select value={selectedService} onValueChange={setSelectedService}>
                            <SelectTrigger>
                              <SelectValue placeholder={t('mcp.selectService')} />
                            </SelectTrigger>
                            <SelectContent>
                              {serviceList.map((svc) => (
                                <SelectItem key={svc.value} value={svc.value}>
                                  {svc.label}
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                        ) : (
                          <Input
                            value={selectedService}
                            onChange={(e) => setSelectedService(e.target.value)}
                            placeholder="groupName@@serviceName"
                          />
                        )}
                      </div>
                      <div className="space-y-2.5">
                        <Label>{t('mcp.transportProtocol')}</Label>
                        <Select value={transportProtocol} onValueChange={setTransportProtocol}>
                          <SelectTrigger>
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="http">HTTP</SelectItem>
                            <SelectItem value="https">HTTPS</SelectItem>
                          </SelectContent>
                        </Select>
                      </div>
                      <div className="space-y-2.5">
                        <Label>{t('mcp.exportPath')}</Label>
                        <Input
                          value={exportPath}
                          onChange={(e) => setExportPath(e.target.value)}
                          placeholder="/mcp"
                        />
                      </div>
                    </div>
                  </div>
                ) : (
                  /* Direct connect mode */
                  <div className="space-y-5">
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
                      <div className="space-y-2.5">
                        <Label>
                          {t('mcp.address')} <span className="text-destructive ml-1">*</span>
                        </Label>
                        <Input
                          value={address}
                          onChange={(e) => setAddress(e.target.value)}
                          placeholder={t('mcp.addressPlaceholder')}
                        />
                      </div>
                      <div className="space-y-2.5">
                        <Label>
                          {t('mcp.port')} <span className="text-destructive ml-1">*</span>
                        </Label>
                        <Input
                          value={port}
                          onChange={(e) => setPort(e.target.value.replace(/\D/g, ''))}
                          placeholder={t('mcp.portPlaceholder')}
                          type="number"
                          min={1}
                          max={65535}
                        />
                      </div>
                      <div className="space-y-2.5">
                        <Label>{t('mcp.transportProtocol')}</Label>
                        <Select value={transportProtocol} onValueChange={setTransportProtocol}>
                          <SelectTrigger>
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="http">HTTP</SelectItem>
                            <SelectItem value="https">HTTPS</SelectItem>
                          </SelectContent>
                        </Select>
                      </div>
                    </div>
                  </div>
                )}
              </>
            ) : (
              /* MCP endpoint URL direct input */
              <div className="space-y-2.5">
                <Label>
                  {t('mcp.mcpEndpointUrl')} <span className="text-destructive ml-1">*</span>
                </Label>
                <Input
                  value={mcpEndpointUrl}
                  onChange={(e) => handleEndpointUrlParse(e.target.value)}
                  placeholder={t('mcp.mcpEndpointUrlPlaceholder')}
                />
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {/* Security Schemes - non-stdio with restToMcp */}
      {!isStdio && restToMcpSwitch && (
        <Card className="overflow-hidden py-0 gap-0">
          <div className="px-5 py-3.5 border-b bg-muted/30">
            <h2 className="text-sm font-semibold flex items-center gap-2">
              <Shield className="h-4 w-4 text-rose-500" />
              {t('mcp.securitySchemes')}
              {securitySchemes.length > 0 && (
                <Badge variant="secondary" className="h-5 min-w-5 rounded-full text-[11px] font-semibold px-1.5 bg-rose-100 text-rose-700 dark:bg-rose-950/50 dark:text-rose-300">
                  {securitySchemes.length}
                </Badge>
              )}
            </h2>
          </div>
          <CardContent className="p-5 space-y-5">
            {/* Default downstream security */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 p-5 rounded-xl bg-muted/40 border border-border/50">
              <div className="flex flex-col gap-2">
                <Label>{t('mcp.defaultDownstreamSecurity')}</Label>
                <p className="text-xs text-muted-foreground -mt-1">{t('mcp.defaultDownstreamSecurityDesc')}</p>
                <Select
                  value={securityExtensions['server.defaultDownstreamSecurity']?.id || ''}
                  onValueChange={(v) =>
                    setSecurityExtensions((prev) => ({
                      ...prev,
                      'server.defaultDownstreamSecurity': {
                        ...prev['server.defaultDownstreamSecurity'],
                        id: v,
                      },
                    }))
                  }
                >
                  <SelectTrigger>
                    <SelectValue placeholder="-" />
                  </SelectTrigger>
                  <SelectContent>
                    {securitySchemes.filter((s) => s.id).map((s) => (
                      <SelectItem key={s.id} value={s.id}>
                        {s.id}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <div className="flex items-center gap-2">
                  <Switch
                    checked={securityExtensions['server.defaultDownstreamSecurity']?.passthrough || false}
                    disabled={!securityExtensions['server.defaultDownstreamSecurity']?.id}
                    onCheckedChange={(v) =>
                      setSecurityExtensions((prev) => ({
                        ...prev,
                        'server.defaultDownstreamSecurity': {
                          ...prev['server.defaultDownstreamSecurity'],
                          passthrough: v,
                        },
                      }))
                    }
                  />
                  <span className="text-sm text-muted-foreground">{t('mcp.passthroughAuth')}</span>
                </div>
              </div>

              <div className="flex flex-col gap-2">
                <Label>{t('mcp.defaultUpstreamSecurity')}</Label>
                <p className="text-xs text-muted-foreground -mt-1">{t('mcp.defaultUpstreamSecurityDesc')}</p>
                <Select
                  value={securityExtensions['server.defaultUpstreamSecurity']?.id || ''}
                  onValueChange={(v) =>
                    setSecurityExtensions((prev) => ({
                      ...prev,
                      'server.defaultUpstreamSecurity': {
                        ...prev['server.defaultUpstreamSecurity'],
                        id: v,
                      },
                    }))
                  }
                >
                  <SelectTrigger>
                    <SelectValue placeholder="-" />
                  </SelectTrigger>
                  <SelectContent>
                    {securitySchemes.filter((s) => s.id).map((s) => (
                      <SelectItem key={s.id} value={s.id}>
                        {s.id}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <div className="space-y-1">
                  <Label className="text-xs text-muted-foreground">{t('mcp.credentialOverride')}</Label>
                  <Input
                    value={securityExtensions['server.defaultUpstreamSecurity']?.credential || ''}
                    disabled={!securityExtensions['server.defaultUpstreamSecurity']?.id}
                    onChange={(e) =>
                      setSecurityExtensions((prev) => ({
                        ...prev,
                        'server.defaultUpstreamSecurity': {
                          ...prev['server.defaultUpstreamSecurity'],
                          credential: e.target.value,
                        },
                      }))
                    }
                    placeholder={t('mcp.credentialOverride')}
                  />
                </div>
              </div>
            </div>

            <Separator />

            {/* Scheme rows */}
            {securitySchemes.length > 0 && (
              <div className="flex flex-col gap-2">
                {securitySchemes.map((scheme, index) => (
                  <div
                    key={index}
                    className="grid grid-cols-1 sm:grid-cols-[1fr_1fr_1fr_1fr_auto] gap-4 items-end p-4 rounded-lg border border-border/40"
                  >
                    <div className="space-y-1">
                      <Label>{t('mcp.securitySchemeId')}</Label>
                      <Input
                        value={scheme.id}
                        onChange={(e) => updateSecurityScheme(index, 'id', e.target.value)}
                        placeholder="ID"
                      />
                    </div>
                    <div className="space-y-1">
                      <Label>{t('mcp.securitySchemeType')}</Label>
                      <Select
                        value={scheme.type}
                        onValueChange={(v) => updateSecurityScheme(index, 'type', v)}
                      >
                        <SelectTrigger>
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="http">HTTP</SelectItem>
                          <SelectItem value="apiKey">API Key</SelectItem>
                        </SelectContent>
                      </Select>
                    </div>
                    <div className="space-y-1">
                      {scheme.type === 'http' ? (
                        <>
                          <Label>{t('mcp.securitySchemeField')}</Label>
                          <Select
                            value={scheme.scheme || 'bearer'}
                            onValueChange={(v) => updateSecurityScheme(index, 'scheme', v)}
                          >
                            <SelectTrigger>
                              <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                              <SelectItem value="basic">{t('mcp.schemeBasic')}</SelectItem>
                              <SelectItem value="bearer">{t('mcp.schemeBearer')}</SelectItem>
                            </SelectContent>
                          </Select>
                        </>
                      ) : (
                        <>
                          <Label>{t('mcp.securityIn')}</Label>
                          <Select
                            value={scheme.in || 'header'}
                            onValueChange={(v) => updateSecurityScheme(index, 'in', v)}
                          >
                            <SelectTrigger>
                              <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                              <SelectItem value="header">{t('mcp.inHeader')}</SelectItem>
                              <SelectItem value="query">{t('mcp.inQuery')}</SelectItem>
                            </SelectContent>
                          </Select>
                        </>
                      )}
                    </div>
                    <div className="space-y-1">
                      {scheme.type === 'apiKey' ? (
                        <>
                          <Label>Key Name</Label>
                          <Input
                            value={scheme.name || ''}
                            onChange={(e) => updateSecurityScheme(index, 'name', e.target.value)}
                            placeholder="X-API-Key"
                          />
                        </>
                      ) : (
                        <>
                          <Label>{t('mcp.securityDefaultCredential')}</Label>
                          <Input
                            value={scheme.defaultCredential || ''}
                            onChange={(e) => updateSecurityScheme(index, 'defaultCredential', e.target.value)}
                            placeholder="token..."
                          />
                        </>
                      )}
                    </div>
                    <Button
                      variant="ghost"
                      size="icon"
                      className="h-9 w-9 text-destructive hover:text-destructive hover:bg-destructive/10 shrink-0"
                      onClick={() => removeSecurityScheme(index)}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                ))}
              </div>
            )}

            <Button variant="outline" size="sm" onClick={addSecurityScheme}>
              <Plus className="mr-1.5 h-3.5 w-3.5" />
              {t('mcp.addSecurityScheme')}
            </Button>
          </CardContent>
        </Card>
      )}

      {/* Tool Management */}
      <ToolManager
        toolSpec={toolSpec}
        importMode={isStdio ? 'none' : restToMcpSwitch ? 'openapi' : 'mcp'}
        onChange={(newSpec) => {
          setToolSpec(newSpec);
          // Sync security schemes from tool imports (e.g. OpenAPI)
          if (newSpec.securitySchemes && newSpec.securitySchemes.length > 0) {
            setSecuritySchemes((prev) => {
              const existingIds = new Set(prev.map((s) => s.id));
              const newSchemes = newSpec.securitySchemes!.filter((s) => !existingIds.has(s.id));
              return newSchemes.length > 0 ? [...prev, ...newSchemes] : prev;
            });
          }
        }}
      />

      </div>

      {/* Submit — full-width sticky bottom bar */}
      <div className="sticky bottom-0 z-10 -mx-6 -mb-6 mt-2">
        <div className="border-t bg-background/95 backdrop-blur-sm py-3 px-6">
          <div className="flex justify-end gap-3">
            <Button variant="outline" onClick={() => navigate(-1)} disabled={loading}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleSubmit} disabled={loading}>
              {loading ? t('mcp.saving') : t('mcp.publish', { defaultValue: '发布' })}
            </Button>
          </div>
        </div>
      </div>

      {/* Publishing Strategy Dialog (edit/version mode) */}
      <Dialog open={strategyDialogOpen} onOpenChange={setStrategyDialogOpen}>
        <DialogContent className="sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>{t('mcp.publishStrategy', { defaultValue: '发布策略' })}</DialogTitle>
            <DialogDescription>
              {t('mcp.publishStrategyDesc', { defaultValue: '选择如何处理此次修改。版本号' })}: <span className="font-mono font-semibold text-foreground">{version}</span>
            </DialogDescription>
          </DialogHeader>

          <RadioGroup
            value={publishStrategy}
            onValueChange={(val) => setPublishStrategy(val as typeof publishStrategy)}
            className="grid grid-cols-1 gap-2.5 py-1"
          >
            {([
              {
                key: 'new-version' as const,
                label: t('mcp.strategyNewVersion', { defaultValue: '发布新版本' }),
                desc: t('mcp.strategyNewVersionDesc', { defaultValue: '以新版本号发布，但不设为默认版本' }),
                accent: 'border-emerald-500/70 bg-emerald-50/60 dark:bg-emerald-950/25',
                dot: 'text-emerald-600 dark:text-emerald-400',
                ring: 'ring-emerald-500/20',
              },
              {
                key: 'set-latest' as const,
                label: t('mcp.strategySetLatest', { defaultValue: '设为最新版本' }),
                desc: t('mcp.strategySetLatestDesc', { defaultValue: '设为默认版本，客户端未指定版本时自动使用' }),
                accent: 'border-blue-500/70 bg-blue-50/60 dark:bg-blue-950/25',
                dot: 'text-blue-600 dark:text-blue-400',
                ring: 'ring-blue-500/20',
              },
              {
                key: 'edit-current' as const,
                label: t('mcp.strategyEditCurrent', { defaultValue: '更新当前版本' }),
                desc: t('mcp.strategyEditCurrentDesc', { defaultValue: '覆盖当前版本配置，版本号和最新标记不变' }),
                accent: 'border-orange-500/70 bg-orange-50/60 dark:bg-orange-950/25',
                dot: 'text-orange-600 dark:text-orange-400',
                ring: 'ring-orange-500/20',
              },
            ] as const).map(({ key, label, desc, accent, dot, ring }) => {
              const { available, reason } = strategyAvailability[key];
              const isSelected = publishStrategy === key && available;
              return (
                <label
                  key={key}
                  className={cn(
                    'relative flex items-start gap-3 rounded-lg border px-4 py-3 transition-all',
                    available
                      ? 'cursor-pointer hover:bg-muted/40'
                      : 'opacity-40 cursor-not-allowed',
                    isSelected ? cn(accent, 'ring-2', ring) : 'border-border'
                  )}
                >
                  <RadioGroupItem value={key} disabled={!available} className="mt-0.5" />
                  <div className="flex-1 space-y-0.5">
                    <span className={cn('text-sm font-medium', isSelected && dot)}>
                      {label}
                    </span>
                    <p className="text-xs text-muted-foreground leading-relaxed">
                      {!available && reason ? reason : desc}
                    </p>
                  </div>
                </label>
              );
            })}
          </RadioGroup>

          <DialogFooter className="gap-2 sm:gap-0">
            <Button variant="outline" onClick={() => setStrategyDialogOpen(false)} disabled={loading}>
              {t('common.cancel')}
            </Button>
            <Button onClick={doSubmit} disabled={loading || !strategyAvailability[publishStrategy].available}>
              {loading ? t('mcp.saving') : t('mcp.confirmPublish', { defaultValue: '确认发布' })}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
