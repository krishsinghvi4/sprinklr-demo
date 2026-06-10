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
  const [fieldErrors, setFieldErrors] = useState({})
  const [highlightCredentials, setHighlightCredentials] = useState(false)

  const emailError = fieldErrors.email || (submitted && !email.trim() ? 'Email is required' : '')
  const passwordError = fieldErrors.password || (submitted && !password ? 'Password is required' : '')

  const handleSubmit = async (e) => {
    e.preventDefault()
    setSubmitted(true)
    setFormError('')
    setFieldErrors({})
    setHighlightCredentials(false)

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
      const apiMessage = error.response?.data?.message

      if (status === 401) {
        setFormError(apiMessage || 'Incorrect email or password')
        setHighlightCredentials(true)
      } else if (status === 403) {
        setFormError(apiMessage || 'Please verify your email. Check your inbox.')
      } else if (status === 400 && apiMessage?.toLowerCase().includes('email')) {
        setFieldErrors({ email: apiMessage })
      } else {
        setFormError(apiMessage || 'Something went wrong. Please try again.')
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
              className={`auth-input ${emailError || highlightCredentials ? 'auth-input-error' : ''}`}
              value={email}
              onChange={(e) => {
                setEmail(e.target.value)
                if (fieldErrors.email) setFieldErrors((prev) => ({ ...prev, email: undefined }))
                if (highlightCredentials) setHighlightCredentials(false)
              }}
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
                className={`auth-input ${passwordError || highlightCredentials ? 'auth-input-error' : ''}`}
                value={password}
                onChange={(e) => {
                  setPassword(e.target.value)
                  if (fieldErrors.password) setFieldErrors((prev) => ({ ...prev, password: undefined }))
                  if (highlightCredentials) setHighlightCredentials(false)
                }}
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
