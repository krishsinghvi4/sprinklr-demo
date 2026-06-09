import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

export default function PrivateRoute() {
  const { isAuthenticated, isLoading } = useAuth()

  if (isLoading) {
    return (
      <div className="private-route-loading">
        <div className="private-route-spinner" />
        <style>{`
          .private-route-loading {
            display: flex;
            align-items: center;
            justify-content: center;
            min-height: 100vh;
            background-color: #f9fafb;
          }
          .private-route-spinner {
            width: 40px;
            height: 40px;
            border: 4px solid #e5e7eb;
            border-top-color: #2563eb;
            border-radius: 50%;
            animation: private-route-spin 0.8s linear infinite;
          }
          @keyframes private-route-spin {
            to { transform: rotate(360deg); }
          }
        `}</style>
      </div>
    )
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }

  return <Outlet />
}
