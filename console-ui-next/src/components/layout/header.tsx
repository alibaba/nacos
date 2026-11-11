import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  Sun,
  Moon,
  LogOut,
  KeyRound,
  Layers,
  ArrowLeftRight,
  PanelLeft,
  PanelLeftClose,
  UserRound,
} from 'lucide-react';
import { useAppStore } from '@/stores/app-store';
import { useAuthStore } from '@/stores/auth-store';
import { getNamespaceSearchAfterSwitch, useNamespaceStore } from '@/stores/namespace-store';
import { useServerStore } from '@/stores/server-store';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuRadioGroup,
  DropdownMenuRadioItem,
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
import { SUPPORTED_LANGUAGES, getLanguageMeta, isSupportedLanguage } from '@/locales/languages';

// Not translated on purpose: users who switched to a language they cannot read
// must still recognize the language selector.
const LANGUAGE_SELECTOR_LABEL = 'Lang.';

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
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const { theme, setTheme, language, setLanguage, sidebarCollapsed, toggleSidebar } = useAppStore();
  const { username, logout, isOidcUser } = useAuthStore();
  const { currentNamespace, namespaces, setNamespace, getNamespaceChangeGuard } = useNamespaceStore();
  const { authEnabled } = useServerStore();
  const [changePasswordOpen, setChangePasswordOpen] = useState(false);

  const currentLanguage = getLanguageMeta(language);
  const baseUrl = currentLanguage.docsBase;
  const sidebarToggleLabel = sidebarCollapsed ? t('common.expand') : t('common.collapse');

  const toggleTheme = () => setTheme(theme === 'light' ? 'dark' : 'light');
  const handleLanguageChange = (value: string) => {
    if (!isSupportedLanguage(value) || value === language) {
      return;
    }
    setLanguage(value);
    i18n.changeLanguage(value);
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
      {/* Left - Sidebar toggle and namespace selector */}
      <div className="flex items-center gap-2 shrink-0">
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8 text-muted-foreground"
              onClick={toggleSidebar}
              aria-label={sidebarToggleLabel}
            >
              {sidebarCollapsed ? <PanelLeft size={16} /> : <PanelLeftClose size={16} />}
            </Button>
          </TooltipTrigger>
          <TooltipContent>{sidebarToggleLabel}</TooltipContent>
        </Tooltip>
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

        {/* Language selector */}
        <DropdownMenu>
          <Tooltip>
            <TooltipTrigger asChild>
              <DropdownMenuTrigger asChild>
                <Button
                  variant="ghost"
                  className="h-8 gap-1 px-2 text-xs text-muted-foreground"
                  aria-label={t('header.language')}
                >
                  {LANGUAGE_SELECTOR_LABEL} {currentLanguage.shortLabel}
                </Button>
              </DropdownMenuTrigger>
            </TooltipTrigger>
            <TooltipContent>{t('header.language')}</TooltipContent>
          </Tooltip>
          <DropdownMenuContent align="end" className="w-36">
            <DropdownMenuRadioGroup value={language} onValueChange={handleLanguageChange}>
              {Object.entries(SUPPORTED_LANGUAGES).map(([code, { label }]) => (
                <DropdownMenuRadioItem key={code} value={code} className="text-xs">
                  {label}
                </DropdownMenuRadioItem>
              ))}
            </DropdownMenuRadioGroup>
          </DropdownMenuContent>
        </DropdownMenu>

        {/* Theme toggle */}
        <Tooltip>
          <TooltipTrigger asChild>
            <Button variant="ghost" size="icon" className="h-8 w-8 text-muted-foreground" onClick={toggleTheme}>
              {theme === 'light' ? <Moon size={16} /> : <Sun size={16} />}
            </Button>
          </TooltipTrigger>
          <TooltipContent>{theme === 'light' ? t('header.darkMode') : t('header.lightMode')}</TooltipContent>
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
            <div className="flex items-center gap-2 px-2 py-1.5 text-xs text-muted-foreground">
              <UserRound size={14} className="shrink-0" />
              <span className="truncate">{username}</span>
            </div>
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
