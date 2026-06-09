import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { Spin } from 'antd';
import { lazy, Suspense } from 'react';
import { useAuthStore } from '../stores/authStore';
import AppLayout from '../components/layout/AppLayout';

const LoginPage = lazy(() => import('../pages/LoginPage'));
const DashboardPage = lazy(() => import('../pages/DashboardPage'));
const ProjectBoardPage = lazy(() => import('../pages/ProjectBoardPage'));
const ProjectSettingsPage = lazy(() => import('../pages/ProjectSettingsPage'));
const SprintPage = lazy(() => import('../pages/SprintPage'));
const SprintDetailPage = lazy(() => import('../pages/SprintDetailPage'));
const ReportPage = lazy(() => import('../pages/ReportPage'));
const GanttPage = lazy(() => import('../pages/GanttPage'));
const ProfilePage = lazy(() => import('../pages/ProfilePage'));
const ChangePasswordPage = lazy(() => import('../pages/ChangePasswordPage'));
const AdminUsersPage = lazy(() => import('../pages/AdminUsersPage'));
const KnowledgeBasePage = lazy(() => import('../pages/KnowledgeBasePage'));

const PageLoader = () => (
  <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
    <Spin size="large" />
  </div>
);

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const isInitialized = useAuthStore((s) => s.isInitialized);
  const isLoggedIn = useAuthStore((s) => s.isLoggedIn);
  const mustChangePassword = useAuthStore((s) => s.mustChangePassword);
  const location = useLocation();

  if (!isInitialized) {
    return <PageLoader />;
  }

  if (!isLoggedIn) return <Navigate to="/login" replace />;
  if (mustChangePassword && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />;
  }
  return <>{children}</>;
}

export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<Suspense fallback={<PageLoader />}><LoginPage /></Suspense>} />
      <Route path="/change-password" element={
        <ProtectedRoute>
          <Suspense fallback={<PageLoader />}><ChangePasswordPage /></Suspense>
        </ProtectedRoute>
      } />
      <Route
        path="/"
        element={
          <ProtectedRoute>
            <AppLayout />
          </ProtectedRoute>
        }
      >
        <Route index element={<Suspense fallback={<PageLoader />}><DashboardPage /></Suspense>} />
        <Route path="projects/:id" element={<Suspense fallback={<PageLoader />}><ProjectBoardPage /></Suspense>} />
        <Route path="projects/:id/settings" element={<Suspense fallback={<PageLoader />}><ProjectSettingsPage /></Suspense>} />
        <Route path="projects/:id/sprints/:sprintId" element={<Suspense fallback={<PageLoader />}><SprintDetailPage /></Suspense>} />
        <Route path="projects/:id/sprints" element={<Suspense fallback={<PageLoader />}><SprintPage /></Suspense>} />
        <Route path="projects/:id/reports" element={<Suspense fallback={<PageLoader />}><ReportPage /></Suspense>} />
        <Route path="projects/:id/gantt" element={<Suspense fallback={<PageLoader />}><GanttPage /></Suspense>} />
        <Route path="profile" element={<Suspense fallback={<PageLoader />}><ProfilePage /></Suspense>} />
        <Route path="knowledge" element={<Suspense fallback={<PageLoader />}><KnowledgeBasePage /></Suspense>} />
        <Route path="admin/users" element={<Suspense fallback={<PageLoader />}><AdminUsersPage /></Suspense>} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
