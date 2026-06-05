import { useEffect } from 'react';
import { BrowserRouter } from 'react-router-dom';
import { ConfigProvider, App as AntApp } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import AppRoutes from './routes';
import { useAuthStore } from './stores/authStore';

export default function App() {
  const initialize = useAuthStore((s) => s.initialize);

  useEffect(() => {
    initialize();
  }, [initialize]);

  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#3b82f6',
          colorInfo: '#3b82f6',
          colorSuccess: '#10b981',
          colorWarning: '#f59e0b',
          colorError: '#ef4444',
          borderRadiusLG: 12,
          borderRadius: 8,
          colorBorderSecondary: 'rgba(59,130,246,0.1)',
          colorBgContainer: '#ffffff',
          boxShadow: '0 2px 12px rgba(59,130,246,0.06)',
          boxShadowSecondary: '0 4px 16px rgba(59,130,246,0.1)',
        },
      }}
    >
      <AntApp>
        <BrowserRouter>
          <AppRoutes />
        </BrowserRouter>
      </AntApp>
    </ConfigProvider>
  );
}
