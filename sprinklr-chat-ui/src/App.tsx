import { BrowserRouter, Routes, Route } from 'react-router-dom'
import PrivateRoute from './components/PrivateRoute'
import ChatDashboardPage from './pages/ChatDashboardPage'
import ChatPage from './pages/ChatPage'
import InsightsDashboardPage from './pages/InsightsDashboardPage'
import InsightsConversationPage from './pages/InsightsConversationPage'
import LoginPage from './pages/LoginPage'
import SignupPage from './pages/SignupPage'
import ForgotPasswordPage from './pages/ForgotPasswordPage'
import ProfilePage from './pages/ProfilePage'
import OAuthCallbackPage from './pages/OAuthCallbackPage'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/oauth/callback" element={<OAuthCallbackPage />} />
        <Route element={<PrivateRoute />}>
          <Route path="/" element={<ChatDashboardPage />} />
          <Route path="/chat/:conversationId" element={<ChatPage />} />
          <Route path="/insights" element={<InsightsDashboardPage />} />
          <Route path="/insights/:dashboardConversationId" element={<InsightsConversationPage />} />
          <Route path="/profile" element={<ProfilePage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default App
