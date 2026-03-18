import { createHashRouter, RouterProvider } from 'react-router-dom';
import { routes } from './routes';

export const router = createHashRouter(routes);

export function AppRouter() {
  return <RouterProvider router={router} />;
}

export default router;
