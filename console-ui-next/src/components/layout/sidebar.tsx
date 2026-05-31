import { useCallback } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import {
  FileText,
  Server,
  Bot,
  Globe,
  Network,
  Shield,
  Users,
  Key,
  Lock,
  Settings,
  ChevronDown,
  ChevronRight,
  PanelLeftClose,
  PanelLeft,
  Puzzle,
  History,
  Radio,
  Eye,
  Cpu,
  Sparkles,
  MessageSquare,
  Wrench,
  Package,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { useAppStore } from '@/stores/app-store';
import { useAuthStore } from '@/stores/auth-store';
import { useServerStore } from '@/stores/server-store';
import { useNamespaceStore } from '@/stores/namespace-store';
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@/components/ui/collapsible';
import { Badge } from '@/components/ui/badge';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { Separator } from '@/components/ui/separator';
import { Popover, PopoverTrigger, PopoverContent } from '@/components/ui/popover';
import { ScrollArea } from '@/components/ui/scroll-area';
import { useState, useEffect, useRef } from 'react';

interface NavItem {
  key: string;
  label: string;
  icon: React.ReactNode;
  path?: string;
  badge?: string;
  children?: { key: string; label: string; path: string; badge?: string }[];
  adminOnly?: boolean;
  defaultOpen?: boolean;
}

export function Sidebar() {
  const { t } = useTranslation();
  const location = useLocation();
  const navigate = useNavigate();
  const { sidebarCollapsed, toggleSidebar } = useAppStore();
  const { globalAdmin } = useAuthStore();
  const { version, startupMode, functionMode, copilotEnabled, aiEnabled } = useServerStore();
  const { currentNamespace, namespaceShowName } = useNamespaceStore();
  const [platformOpen, setPlatformOpen] = useState(false);

  // Auto-expand platform section if current path is within it
  useEffect(() => {
    const platformPaths = ['/namespace', '/clusterManagement', '/pluginManagement', '/userManagement', '/rolesManagement', '/permissionsManagement'];
    if (platformPaths.some((p) => location.pathname === p)) {
      setPlatformOpen(true);
    }
  }, [location.pathname]);

  const coreItems: NavItem[] = [];

  // AI Registry - show when AI is enabled and not in naming/config/microservice mode
  if (aiEnabled && functionMode !== 'naming' && functionMode !== 'config' && functionMode !== 'microservice') {
    coreItems.push({
      key: 'ai',
      label: t('menu.aiRegistry'),
      icon: <Bot size={18} />,
      badge: 'new',
      defaultOpen: true,
      children: [
        { key: 'skillRegistry', label: t('menu.skillRegistry'), path: '/skill', badge: 'new' },
        { key: 'promptRegistry', label: t('menu.promptRegistry'), path: '/promptManagement', badge: 'new' },
        { key: 'agentRegistry', label: t('menu.agentRegistry'), path: '/agentManagement' },
        { key: 'agentSpecRegistry', label: t('menu.agentSpecRegistry'), path: '/agentspec', badge: 'Beta' },
        { key: 'mcpRegistry', label: t('menu.mcpRegistry'), path: '/mcpServerManagement' },
      ],
    });
  }

  // Config Management - show unless mode is 'naming' or 'ai'
  if (functionMode !== 'naming' && functionMode !== 'ai') {
    coreItems.push({
      key: 'config',
      label: t('menu.configManagement'),
      icon: <FileText size={18} />,
      children: [
        { key: 'configList', label: t('menu.configList'), path: '/configurationManagement' },
        { key: 'historyRollback', label: t('menu.historyRollback'), path: '/historyRollback' },
        { key: 'listeningQuery', label: t('menu.listeningQuery'), path: '/listeningToQuery' },
      ],
    });
  }

  // Service Management - show unless mode is 'config' or 'ai'
  if (functionMode !== 'config' && functionMode !== 'ai') {
    coreItems.push({
      key: 'service',
      label: t('menu.serviceManagement'),
      icon: <Server size={18} />,
      children: [
        { key: 'serviceList', label: t('menu.serviceList'), path: '/serviceManagement' },
        { key: 'subscriberList', label: t('menu.subscriberList'), path: '/subscriberList' },
      ],
    });
  }

  const platformItems: NavItem[] = [
    { key: 'namespace', label: t('menu.namespace'), icon: <Globe size={18} />, path: '/namespace' },
  ];

  if (globalAdmin) {
    platformItems.push(
      { key: 'cluster', label: t('menu.clusterManagement'), icon: <Network size={18} />, path: '/clusterManagement' },
    );
  }

  if (globalAdmin && functionMode !== 'naming' && functionMode !== 'config' && functionMode !== 'ai') {
    platformItems.push({
      key: 'plugin',
      label: t('menu.pluginManagement'),
      icon: <Puzzle size={18} />,
      path: '/pluginManagement',
    });
  }

  if (globalAdmin) {
    platformItems.push({
      key: 'authority',
      label: t('menu.authorityControl'),
      icon: <Shield size={18} />,
      children: [
        { key: 'userList', label: t('menu.userList'), path: '/userManagement' },
        { key: 'roleManagement', label: t('menu.roleManagement'), path: '/rolesManagement' },
        { key: 'privilegeManagement', label: t('menu.privilegeManagement'), path: '/permissionsManagement' },
      ],
    });
  }

  const navTo = useCallback(
    (url: string) => {
      const params = new URLSearchParams();
      if (currentNamespace !== undefined) params.set('namespace', currentNamespace);
      if (namespaceShowName) params.set('namespaceShowName', namespaceShowName);
      const qs = params.toString();
      navigate(qs ? `${url}?${qs}` : url);
    },
    [navigate, currentNamespace, namespaceShowName]
  );

  const isActive = (path: string) => location.pathname === path;
  const isGroupActive = (children?: { path: string }[]) =>
    children?.some((c) => location.pathname === c.path) ?? false;

  const iconForChild = (key: string) => {
    const map: Record<string, React.ReactNode> = {
      configList: <FileText size={16} />,
      historyRollback: <History size={16} />,
      listeningQuery: <Radio size={16} />,
      serviceList: <Server size={16} />,
      subscriberList: <Eye size={16} />,
      mcpRegistry: <Cpu size={16} />,
      agentRegistry: <Bot size={16} />,
      agentSpecRegistry: <Package size={16} />,
      skillRegistry: <Wrench size={16} />,
      promptRegistry: <MessageSquare size={16} />,
      userList: <Users size={16} />,
      roleManagement: <Key size={16} />,
      privilegeManagement: <Lock size={16} />,
    };
    return map[key] || <Sparkles size={16} />;
  };

  return (
    <aside
      className={cn(
        'fixed left-0 top-0 z-40 h-screen border-r border-sidebar-border bg-sidebar-background/80 backdrop-blur-xl transition-all duration-300 flex flex-col',
        sidebarCollapsed ? 'w-16' : 'w-64'
      )}
    >
      {/* Header - Logo */}
      <div className="flex h-14 items-center justify-center border-b border-sidebar-border">
        {!sidebarCollapsed ? (
          <img
            src={`${import.meta.env.BASE_URL}img/nacos-logo-dark.svg`}
            alt="Nacos"
            className="h-6 w-auto max-w-[140px] object-contain"
          />
        ) : (
          <Tooltip>
            <TooltipTrigger asChild>
              <img
                src={`${import.meta.env.BASE_URL}img/nacos-icon.png`}
                alt="Nacos"
                className="h-7 w-7 object-contain cursor-default"
              />
            </TooltipTrigger>
            <TooltipContent side="right" sideOffset={8} className="rounded-lg border border-border/50 bg-popover text-popover-foreground shadow-md px-3 py-2 text-xs font-medium">
              NACOS {version && `v${version}`}
            </TooltipContent>
          </Tooltip>
        )}
      </div>

      {/* Navigation */}
      <ScrollArea className="flex-1 py-2">
        <div className="px-2 space-y-1">
          {/* Core Section Label */}
          {!sidebarCollapsed && (
            <div className="px-2 py-2 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
              {t('menu.configManagement').split('').length > 0 ? '核心功能' : 'Core'}
            </div>
          )}

          {/* Core Nav Items */}
          {coreItems.map((item) =>
            item.children ? (
              <NavGroup
                key={item.key}
                item={item}
                collapsed={sidebarCollapsed}
                isGroupActive={isGroupActive(item.children)}
                isActive={isActive}
                onNavigate={navTo}
                iconForChild={iconForChild}
              />
            ) : (
              <NavLink
                key={item.key}
                item={item}
                collapsed={sidebarCollapsed}
                active={isActive(item.path!)}
                onClick={() => navTo(item.path!)}
              />
            )
          )}

          {/* Separator */}
          <Separator className="my-2" />

          {/* Platform Section */}
          {sidebarCollapsed ? (
            platformItems.map((item) =>
              item.children ? (
                <NavGroup
                  key={item.key}
                  item={item}
                  collapsed={sidebarCollapsed}
                  isGroupActive={isGroupActive(item.children)}
                  isActive={isActive}
                  onNavigate={navTo}
                  iconForChild={iconForChild}
                />
              ) : (
                <NavLink
                  key={item.key}
                  item={item}
                  collapsed={sidebarCollapsed}
                  active={isActive(item.path!)}
                  onClick={() => navTo(item.path!)}
                />
              )
            )
          ) : (
            <Collapsible open={platformOpen} onOpenChange={setPlatformOpen}>
              <CollapsibleTrigger className="flex w-full items-center gap-2 rounded-md px-2 py-2 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground hover:text-foreground transition-colors">
                {platformOpen ? <ChevronDown size={12} /> : <ChevronRight size={12} />}
                {t('menu.platformManagement')}
              </CollapsibleTrigger>
              <CollapsibleContent className="space-y-1">
                {platformItems.map((item) =>
                  item.children ? (
                    <NavGroup
                      key={item.key}
                      item={item}
                      collapsed={sidebarCollapsed}
                      isGroupActive={isGroupActive(item.children)}
                      isActive={isActive}
                      onNavigate={navTo}
                      iconForChild={iconForChild}
                    />
                  ) : (
                    <NavLink
                      key={item.key}
                      item={item}
                      collapsed={sidebarCollapsed}
                      active={isActive(item.path!)}
                      onClick={() => navTo(item.path!)}
                    />
                  )
                )}
              </CollapsibleContent>
            </Collapsible>
          )}
        </div>
      </ScrollArea>

      {/* Bottom Section */}
      <div className="border-t border-sidebar-border p-2 space-y-1">
        {!sidebarCollapsed && (
          <div className="px-3 py-1.5 text-xs text-muted-foreground text-center">
            {version && `v${version}`}
            {startupMode && ` · ${startupMode}`}
          </div>
        )}
        {copilotEnabled && (
          <NavLink
            item={{
              key: 'settings',
              label: t('menu.settingCenter'),
              icon: <Settings size={18} />,
              path: '/settingCenter',
            }}
            collapsed={sidebarCollapsed}
            active={isActive('/settingCenter')}
            onClick={() => navTo('/settingCenter')}
          />
        )}
        <button
          onClick={toggleSidebar}
          className="flex w-full items-center justify-center gap-2 rounded-md px-2 py-2 text-muted-foreground hover:bg-sidebar-accent hover:text-sidebar-accent-foreground transition-colors"
        >
          {sidebarCollapsed ? <PanelLeft size={18} /> : <PanelLeftClose size={18} />}
          {!sidebarCollapsed && <span className="text-xs">{t('common.collapse') || 'Collapse'}</span>}
        </button>
      </div>
    </aside>
  );
}

/* --- Sub-components --- */

function NavLink({
  item,
  collapsed,
  active,
  onClick,
}: {
  item: NavItem;
  collapsed: boolean;
  active: boolean;
  onClick: () => void;
}) {
  const btn = (
    <button
      onClick={onClick}
      className={cn(
        'group relative flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-all duration-200',
        active
          ? 'bg-sidebar-accent text-sidebar-primary'
          : 'text-sidebar-foreground hover:bg-sidebar-accent/60 hover:text-sidebar-accent-foreground'
      )}
    >
      {active && (
        <div className="absolute left-0 top-1/2 -translate-y-1/2 h-5 w-[3px] rounded-r-full bg-gradient-to-b from-blue-500 to-blue-600" />
      )}
      <span className="shrink-0">{item.icon}</span>
      {!collapsed && <span className="truncate">{item.label}</span>}
      {!collapsed && item.badge && (
        <Badge variant="destructive" className="ml-auto text-[10px] px-1.5 py-0">
          {item.badge}
        </Badge>
      )}
    </button>
  );

  if (collapsed) {
    return (
      <Tooltip>
        <TooltipTrigger asChild>{btn}</TooltipTrigger>
        <TooltipContent side="right" sideOffset={8} className="rounded-lg border border-border/50 bg-popover text-popover-foreground shadow-md px-3 py-2 text-xs font-medium">
          {item.label}
        </TooltipContent>
      </Tooltip>
    );
  }
  return btn;
}

function NavGroup({
  item,
  collapsed,
  isGroupActive,
  isActive,
  onNavigate,
  iconForChild,
}: {
  item: NavItem;
  collapsed: boolean;
  isGroupActive: boolean;
  isActive: (path: string) => boolean;
  onNavigate: (path: string) => void;
  iconForChild: (key: string) => React.ReactNode;
}) {
  const [open, setOpen] = useState(isGroupActive || !!item.defaultOpen);
  const [flyoutOpen, setFlyoutOpen] = useState(false);
  const closeTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (isGroupActive) setOpen(true);
  }, [isGroupActive]);

  useEffect(() => {
    return () => { if (closeTimer.current) clearTimeout(closeTimer.current); };
  }, []);

  const handleFlyoutEnter = () => {
    if (closeTimer.current) clearTimeout(closeTimer.current);
    setFlyoutOpen(true);
  };

  const handleFlyoutLeave = () => {
    closeTimer.current = setTimeout(() => setFlyoutOpen(false), 120);
  };

  if (collapsed) {
    return (
      <Popover open={flyoutOpen}>
        <PopoverTrigger asChild>
          <button
            onMouseEnter={handleFlyoutEnter}
            onMouseLeave={handleFlyoutLeave}
            className={cn(
              'group relative flex w-full items-center justify-center rounded-lg px-3 py-2 transition-all duration-200',
              isGroupActive
                ? 'bg-sidebar-accent text-sidebar-primary'
                : 'text-sidebar-foreground hover:bg-sidebar-accent/60'
            )}
          >
            {isGroupActive && (
              <div className="absolute left-0 top-1/2 -translate-y-1/2 h-5 w-[3px] rounded-r-full bg-gradient-to-b from-blue-500 to-blue-600" />
            )}
            {item.icon}
          </button>
        </PopoverTrigger>
        <PopoverContent
          side="right"
          align="start"
          sideOffset={8}
          onMouseEnter={handleFlyoutEnter}
          onMouseLeave={handleFlyoutLeave}
          onOpenAutoFocus={(e) => e.preventDefault()}
          onCloseAutoFocus={(e) => e.preventDefault()}
          onPointerDownOutside={() => setFlyoutOpen(false)}
          className="w-48 p-1.5 rounded-xl shadow-lg border border-border/60 bg-popover/95 backdrop-blur-xl"
        >
          <div className="flex items-center gap-2 px-2.5 py-1.5">
            <span className="text-muted-foreground shrink-0">{item.icon}</span>
            <span className="text-xs font-semibold text-foreground truncate">{item.label}</span>
            {item.badge && (
              <Badge variant="destructive" className="ml-auto text-[10px] px-1.5 py-0">
                {item.badge}
              </Badge>
            )}
          </div>
          <Separator className="my-1" />
          <div className="space-y-0.5">
            {item.children?.map((child) => (
              <button
                key={child.key}
                onClick={() => { onNavigate(child.path); setFlyoutOpen(false); }}
                className={cn(
                  'flex w-full items-center gap-2.5 rounded-lg px-2.5 py-1.5 text-[13px] transition-colors',
                  isActive(child.path)
                    ? 'bg-sidebar-accent text-sidebar-primary font-medium'
                    : 'text-muted-foreground hover:bg-sidebar-accent/60 hover:text-foreground'
                )}
              >
                <span className="shrink-0 opacity-70">{iconForChild(child.key)}</span>
                <span className="truncate">{child.label}</span>
                {child.badge && (
                  <Badge className={cn(
                    'text-[9px] px-1 py-0 h-3.5 ml-auto font-medium text-white border-0',
                    child.badge === 'Beta' ? 'bg-amber-500 hover:bg-amber-500' : 'bg-destructive hover:bg-destructive',
                  )}>
                    {child.badge}
                  </Badge>
                )}
              </button>
            ))}
          </div>
        </PopoverContent>
      </Popover>
    );
  }

  return (
    <Collapsible open={open} onOpenChange={setOpen}>
      <CollapsibleTrigger className="flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium text-sidebar-foreground hover:bg-sidebar-accent/60 transition-all duration-200">
        <span className="shrink-0">{item.icon}</span>
        <span className="truncate flex-1 text-left">{item.label}</span>
        {item.badge && (
          <Badge variant="destructive" className="text-[10px] px-1.5 py-0 mr-1">
            {item.badge}
          </Badge>
        )}
        {open ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
      </CollapsibleTrigger>
      <CollapsibleContent className="ml-4 space-y-0.5 border-l border-sidebar-border pl-3 mt-0.5">
        {item.children?.map((child) => (
          <button
            key={child.key}
            onClick={() => onNavigate(child.path)}
            className={cn(
              'flex w-full items-center gap-2 rounded-md px-2 py-1.5 text-sm transition-all duration-200',
              isActive(child.path)
                ? 'text-sidebar-primary font-medium bg-sidebar-accent'
                : 'text-muted-foreground hover:text-sidebar-foreground hover:bg-sidebar-accent/40'
            )}
          >
            <span className="shrink-0 opacity-70">{iconForChild(child.key)}</span>
            <span className="truncate">{child.label}</span>
            {child.badge && (
              <Badge className={cn(
                'text-[9px] px-1 py-0 h-3.5 ml-auto font-medium text-white border-0',
                child.badge === 'Beta' ? 'bg-amber-500 hover:bg-amber-500' : 'bg-destructive hover:bg-destructive',
              )}>
                {child.badge}
              </Badge>
            )}
          </button>
        ))}
      </CollapsibleContent>
    </Collapsible>
  );
}
