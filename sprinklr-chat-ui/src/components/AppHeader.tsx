import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

interface AppHeaderProps {
  title?: string
  backLink?: { to: string; label: string }
  actions?: React.ReactNode
}

export default function AppHeader({ title = 'Sprinklr Chat', backLink, actions }: AppHeaderProps) {
  const { user, logout } = useAuth()

  return (
    <header className="bg-white shadow-sm border-b border-gray-200">
      <div className="max-w-3xl mx-auto px-4 py-4 flex justify-between items-center">
        <div className="flex items-center gap-4">
          {backLink && (
            <Link to={backLink.to} className="text-sm text-gray-600 hover:text-gray-900">
              {backLink.label}
            </Link>
          )}
          <div>
            <h1 className="text-2xl font-bold text-gray-900">{title}</h1>
            {user?.email && (
              <p className="text-sm text-gray-500 mt-1">{user.email}</p>
            )}
          </div>
        </div>
        <div className="flex items-center gap-3">
          <Link
            to="/insights"
            className="text-sm text-gray-600 hover:text-gray-900"
          >
            Insights
          </Link>
          <Link
            to="/"
            className="text-sm text-gray-600 hover:text-gray-900"
          >
            Chats
          </Link>
          <Link
            to="/profile"
            className="text-sm text-gray-600 hover:text-gray-900"
          >
            Profile
          </Link>
          {actions}
          <button
            onClick={logout}
            className="px-3 py-2 text-sm text-gray-600 hover:text-gray-900"
          >
            Log out
          </button>
        </div>
      </div>
    </header>
  )
}
