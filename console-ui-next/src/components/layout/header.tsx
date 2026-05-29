import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  Sun,
  Moon,
  Languages,
  LogOut,
  KeyRound,
  Layers,
  ArrowLeftRight,
} from 'lucide-react';
import { useAppStore } from '@/stores/app-store';
import { useAuthStore } from '@/stores/auth-store';
import { getNamespaceSearchAfterSwitch, useNamespaceStore } from '@/stores/namespace-store';
import { useServerStore } from '@/stores/server-store';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Button } from '@/components/ui/button';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import { Separator } from '@/components/ui/separator';
import { Badge } from '@/components/ui/badge';
import { ChangePasswordDialog } from '@/components/layout/change-password-dialog';

function getBaseUrl(language: string) {
  return language.toLowerCase() === 'en-us' ? 'https://nacos.io/en/' : 'https://nacos.io/';
}

interface NavLink {
  key: string;
  href: string | ((base: string) => string);
  hot?: boolean;
}

const NAV_LINKS: NavLink[] = [
  { key: 'home', href: (base) => base },
  {
    key: 'enterprise',
    href: 'https://cn.aliyun.com/product/aliware/mse?spm=nacos-website.topbar.0.0.0',
    hot: true,
  },
  {
    key: 'mcp',
    href: 'https://mcp.nacos.io?spm=nacos-website.topbar.0.0.0',
    hot: true,
  },
  { key: 'docs', href: (base) => `${base}docs/latest/what-is-nacos/` },
  { key: 'blog', href: (base) => `${base}blog/` },
  { key: 'community', href: (base) => `${base}news/` },
];

export function Header() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const { theme, setTheme, language, setLanguage } = useAppStore();
  const { username, logout, isOidcUser } = useAuthStore();
  const { currentNamespace, namespaces, setNamespace, getNamespaceChangeGuard } = useNamespaceStore();
  const { authEnabled } = useServerStore();
  const [changePasswordOpen, setChangePasswordOpen] = useState(false);

  const baseUrl = getBaseUrl(language);

  const toggleTheme = () => setTheme(theme === 'light' ? 'dark' : 'light');
  const toggleLanguage = () => {
    const newLang = language === 'zh-CN' ? 'en-US' : 'zh-CN';
    setLanguage(newLang);
    window.location.reload();
  };

  const handleNamespaceChange = (value: string) => {
    const ns = namespaces.find(n => n.namespace === value);
    const nextShowName = ns?.namespaceShowName || value;
    const guard = getNamespaceChangeGuard();
    if (guard && !guard(value, nextShowName)) {
      return;
    }

    setNamespace(value, nextShowName);

    const nextSearch = getNamespaceSearchAfterSwitch(location.search, value, nextShowName);
    if (nextSearch !== null && nextSearch !== location.search) {
      navigate(`${location.pathname}${nextSearch}${location.hash}`, { replace: true });
    }
  };

  return (
    <header className="sticky top-0 z-30 flex h-14 items-center justify-between border-b border-border bg-background/80 backdrop-blur-md px-4">
      {/* Left - Namespace selector */}
      <div className="flex items-center gap-2 shrink-0">
        <Layers size={16} className="text-primary shrink-0" />
        <span className="text-xs font-medium text-foreground/70 shrink-0 hidden lg:inline">{t('common.selectNamespace')}</span>
        <Select value={currentNamespace} onValueChange={handleNamespaceChange}>
          <SelectTrigger className="h-8 w-[180px] text-xs">
            <SelectValue placeholder={t('common.selectNamespace')} />
          </SelectTrigger>
          <SelectContent>
            {namespaces.map((ns) => (
              <SelectItem key={ns.namespace} value={ns.namespace} className="text-xs">
                {ns.namespaceShowName}
                {ns.namespaceShowName !== ns.namespace && (
                  <span className="ml-1 text-muted-foreground">({ns.namespace})</span>
                )}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* Center - Navigation links */}
      <nav className="hidden md:flex items-center gap-0.5">
        {NAV_LINKS.map((link) => {
          const href = typeof link.href === 'function' ? link.href(baseUrl) : link.href;
          return (
            <a
              key={link.key}
              href={href}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1 px-3 py-1.5 text-sm text-foreground/70 hover:text-primary font-medium transition-colors rounded-md hover:bg-accent"
            >
              {t(`header.${link.key}`)}
              {link.hot && (
                <Badge variant="destructive" className="h-4 px-1 text-[10px] leading-none font-medium">
                  HOT
                </Badge>
              )}
            </a>
          );
        })}
      </nav>

      {/* Right actions */}
      <div className="flex items-center gap-1 shrink-0">
        {/* Switch to legacy console */}
        <Tooltip>
          <TooltipTrigger asChild>
            <Button variant="outline" size="sm" className="h-7 gap-1.5 text-xs" asChild>
              <a href="../legacy/">
                <ArrowLeftRight size={14} />
                {t('common.legacyConsole')}
              </a>
            </Button>
          </TooltipTrigger>
          <TooltipContent>{t('common.switchToLegacy')}</TooltipContent>
        </Tooltip>

        <Separator orientation="vertical" className="mx-1 h-5" />

        {/* Language switch */}
        <Tooltip>
          <TooltipTrigger asChild>
            <Button variant="ghost" size="icon" className="h-8 w-8 text-muted-foreground" onClick={toggleLanguage}>
              <Languages size={16} />
            </Button>
          </TooltipTrigger>
          <TooltipContent>{t('header.languageSwitch')}</TooltipContent>
        </Tooltip>

        {/* Theme toggle */}
        <Tooltip>
          <TooltipTrigger asChild>
            <Button variant="ghost" size="icon" className="h-8 w-8 text-muted-foreground" onClick={toggleTheme}>
              {theme === 'light' ? <Moon size={16} /> : <Sun size={16} />}
            </Button>
          </TooltipTrigger>
          <TooltipContent>{theme === 'light' ? 'Dark mode' : 'Light mode'}</TooltipContent>
        </Tooltip>

        {/* User menu */}
        {authEnabled && !isOidcUser() && (
          <ChangePasswordDialog open={changePasswordOpen} onOpenChange={setChangePasswordOpen} />
        )}
        {authEnabled && (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" className="h-8 gap-2 px-2 text-muted-foreground">
              <Avatar className="h-6 w-6">
                <AvatarFallback className="bg-gradient-to-br from-blue-500 to-blue-600 text-white text-[10px]">
                  {(username || 'U')[0].toUpperCase()}
                </AvatarFallback>
              </Avatar>
              <span className="text-xs max-w-[80px] truncate hidden sm:inline">{username}</span>
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-48">
            <div className="px-2 py-1.5 text-xs text-muted-foreground">{username}</div>
            <DropdownMenuSeparator />
            {!isOidcUser() && (
              <>
                <DropdownMenuItem
                  className="gap-2 text-xs"
                  onSelect={(event) => {
                    event.preventDefault();
                    setChangePasswordOpen(true);
                  }}
                >
                  <KeyRound size={14} />
                  {t('header.changePassword')}
                </DropdownMenuItem>
                <DropdownMenuSeparator />
              </>
            )}
            <DropdownMenuItem className="gap-2 text-xs text-destructive" onClick={logout}>
              <LogOut size={14} />
              {t('header.logout')}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
        )}
      </div>
    </header>
  );
}
