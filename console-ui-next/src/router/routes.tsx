import { lazy, Suspense } from 'react';
import type { RouteObject } from 'react-router-dom';
import { Navigate } from 'react-router-dom';
import { AuthGuard, AdminGuard, GuestGuard } from './guards';

// Loading component
function PageLoading() {
  return (
    <div className="flex items-center justify-center py-32">
      <div className="animate-spin rounded-full h-6 w-6 border-2 border-muted border-t-primary"></div>
    </div>
  );
}

// Lazy load wrapper
function lazyPage(
  importFn: () => Promise<{ default: React.ComponentType }>
) {
  const LazyComponent = lazy(importFn);
  return (
    <Suspense fallback={<PageLoading />}>
      <LazyComponent />
    </Suspense>
  );
}

// Layouts
const AppLayout = lazy(() => import('@/layouts/AppLayout'));

export const routes: RouteObject[] = [
  // Public routes
  {
    element: <GuestGuard />,
    children: [
      {
        path: '/login',
        element: lazyPage(() => import('@/pages/login')),
      },
      {
        path: '/register',
        element: lazyPage(() => import('@/pages/register')),
      },
    ],
  },
  
  // Protected routes with AppLayout
  {
    element: <AuthGuard />,
    children: [
      {
        element: (
          <Suspense fallback={<PageLoading />}>
            <AppLayout />
          </Suspense>
        ),
        children: [
          // Default redirect
          {
            index: true,
            element: <Navigate to="/skill" replace />,
          },
          
          // Welcome page
          {
            path: 'welcome',
            element: lazyPage(() => import('@/pages/welcome')),
          },
          
          // Namespace
          {
            path: 'namespace',
            element: lazyPage(() => import('@/pages/namespace')),
          },
          
          // Configuration Management
          {
            path: 'configurationManagement',
            element: lazyPage(() => import('@/pages/configurationManagement')),
          },
          {
            path: 'newconfig',
            element: lazyPage(() => import('@/pages/newconfig')),
          },
          {
            path: 'configdetail',
            element: lazyPage(() => import('@/pages/configdetail')),
          },
          {
            path: 'configeditor',
            element: lazyPage(() => import('@/pages/configeditor')),
          },
          {
            path: 'configsync',
            element: lazyPage(() => import('@/pages/configsync')),
          },
          {
            path: 'configRollback',
            element: lazyPage(() => import('@/pages/configRollback')),
          },
          {
            path: 'historyDetail',
            element: lazyPage(() => import('@/pages/historyDetail')),
          },
          {
            path: 'historyRollback',
            element: lazyPage(() => import('@/pages/historyRollback')),
          },
          {
            path: 'listeningToQuery',
            element: lazyPage(() => import('@/pages/listeningToQuery')),
          },
          
          // Service Management
          {
            path: 'serviceManagement',
            element: lazyPage(() => import('@/pages/serviceManagement')),
          },
          {
            path: 'serviceDetail',
            element: lazyPage(() => import('@/pages/serviceDetail')),
          },
          {
            path: 'subscriberList',
            element: lazyPage(() => import('@/pages/subscriberList')),
          },
          
          // Cluster Management
          {
            path: 'clusterManagement',
            element: lazyPage(() => import('@/pages/clusterManagement')),
          },
          
          // User Management (Admin only)
          {
            element: <AdminGuard />,
            children: [
              {
                path: 'userManagement',
                element: lazyPage(() => import('@/pages/userManagement')),
              },
              {
                path: 'rolesManagement',
                element: lazyPage(() => import('@/pages/rolesManagement')),
              },
              {
                path: 'permissionsManagement',
                element: lazyPage(() => import('@/pages/permissionsManagement')),
              },
            ],
          },
          
          // MCP Server Management
          {
            path: 'mcpServerManagement',
            element: lazyPage(() => import('@/pages/mcpServerManagement')),
          },
          {
            path: 'mcpServerDetail',
            element: lazyPage(() => import('@/pages/mcpServerDetail')),
          },
          {
            path: 'newMcpServer',
            element: lazyPage(() => import('@/pages/newMcpServer')),
          },
          
          // Agent Management
          {
            path: 'agentManagement',
            element: lazyPage(() => import('@/pages/agentManagement')),
          },
          {
            path: 'newAgent',
            element: lazyPage(() => import('@/pages/newAgent')),
          },
          {
            path: 'agentDetail',
            element: lazyPage(() => import('@/pages/agentDetail')),
          },
          
          // AgentSpec Management
          {
            path: 'agentspec',
            element: lazyPage(() => import('@/pages/agentSpecManagement')),
          },
          {
            path: 'agentspec/new',
            element: lazyPage(() => import('@/pages/newAgentSpec')),
          },
          {
            path: 'agentspec/:name',
            element: lazyPage(() => import('@/pages/agentSpecDetail')),
          },
          
          // Skill Management
          {
            path: 'skill',
            element: lazyPage(() => import('@/pages/skillManagement')),
          },
          {
            path: 'newSkill',
            element: lazyPage(() => import('@/pages/newSkill')),
          },
          {
            path: 'skill/:name',
            element: lazyPage(() => import('@/pages/skillDetail')),
          },
          
          // Prompt Management
          {
            path: 'promptManagement',
            element: lazyPage(() => import('@/pages/promptManagement')),
          },
          {
            path: 'newPrompt',
            element: lazyPage(() => import('@/pages/newPrompt')),
          },
          {
            path: 'promptDetail',
            element: lazyPage(() => import('@/pages/promptDetail')),
          },
          {
            path: 'publishPromptVersion',
            element: lazyPage(() => import('@/pages/publishPromptVersion')),
          },
          
          // Plugin Management
          {
            path: 'pluginManagement',
            element: lazyPage(() => import('@/pages/pluginManagement')),
          },
          
          // Settings
          {
            path: 'settingCenter',
            element: lazyPage(() => import('@/pages/settingCenter')),
          },
        ],
      },
    ],
  },
  
  // Catch-all redirect
  {
    path: '*',
    element: <Navigate to="/" replace />,
  },
];
