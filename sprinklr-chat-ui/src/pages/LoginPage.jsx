import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { login as loginApi } from '../api/authApi'
import { useAuth } from '../context/AuthContext'
import '../styles/auth.css'

export default function LoginPage() {
  const navigate = useNavigate()
  const { login } = useAuth()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [submitted, setSubmitted] = useState(false)
  const [loading, setLoading] = useState(false)
  const [formError, setFormError] = useState('')

  const emailError = submitted && !email.trim() ? 'Email is required' : ''
  const passwordError = submitted && !password ? 'Password is required' : ''

  const handleSubmit = async (e) => {
    e.preventDefault()
    setSubmitted(true)
    setFormError('')

    if (!email.trim() || !password) {
      return
    }

    setLoading(true)
    try {
      const data = await loginApi({ email: email.trim(), password })
      login(data.token)
      navigate('/')
    } catch (error) {
      const status = error.response?.status

      // #region agent log
      fetch('http://127.0.0.1:7692/ingest/d71e150a-d060-44f4-ba2f-f84045f9e3f2',{method:'POST',headers:{'Content-Type':'application/json','X-Debug-Session-Id':'c28fed'},body:JSON.stringify({sessionId:'c28fed',location:'LoginPage.jsx:handleSubmit',message:'login failed',data:{status,apiMessage:error.response?.data?.message||null},hypothesisId:'H5',timestamp:Date.now()})}).catch(()=>{});
      // #endregion

      if (status === 401) {
        setFormError('Incorrect email or password')
      } else if (status === 403) {
        setFormError('Please verify your email. Check your inbox.')
      } else {
        setFormError('Something went wrong. Please try again.')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1 className="auth-title">Sign in</h1>
        <p className="auth-subtitle">Welcome back to Sprinklr Chat</p>

        <form className="auth-form" onSubmit={handleSubmit} noValidate>
          {formError && <p className="auth-form-error">{formError}</p>}

          <div className="auth-field">
            <label className="auth-label" htmlFor="login-email">
              Email
            </label>
            <input
              id="login-email"
              type="email"
              className={`auth-input ${emailError ? 'auth-input-error' : ''}`}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoComplete="email"
            />
            {emailError && <p className="auth-field-error">{emailError}</p>}
          </div>

          <div className="auth-field">
            <label className="auth-label" htmlFor="login-password">
              Password
            </label>
            <div className="auth-password-wrapper">
              <input
                id="login-password"
                type={showPassword ? 'text' : 'password'}
                className={`auth-input ${passwordError ? 'auth-input-error' : ''}`}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
              />
              <button
                type="button"
                className="auth-toggle-password"
                onClick={() => setShowPassword((prev) => !prev)}
              >
                {showPassword ? 'Hide' : 'Show'}
              </button>
            </div>
            {passwordError && <p className="auth-field-error">{passwordError}</p>}
          </div>

          <button type="submit" className="auth-button" disabled={loading}>
            {loading ? 'Signing in...' : 'Sign in'}
          </button>
        </form>

        <p className="auth-link-row">
          <Link to="/forgot-password" className="auth-link">
            Forgot password?
          </Link>
          <span className="auth-link-separator">|</span>
          <Link to="/signup" className="auth-link">
            Don&apos;t have an account? Sign up
          </Link>
        </p>

        <p className="auth-link-row">
          <Link to="/login" className="auth-link">
            Back to login
          </Link>
        </p>
      </div>
    </div>
  )
}
