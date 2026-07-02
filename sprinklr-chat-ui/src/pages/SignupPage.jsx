import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { register, verifySignupOtp, resendSignupOtp } from '../api/authApi'
import '../styles/auth.css'

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export default function SignupPage() {
  const navigate = useNavigate()

  const [step, setStep] = useState(1)
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [otp, setOtp] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [loading, setLoading] = useState(false)
  const [formError, setFormError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [successMessage, setSuccessMessage] = useState('')
  const [resendCountdown, setResendCountdown] = useState(0)

  useEffect(() => {
    if (resendCountdown <= 0) return
    const timer = setInterval(() => {
      setResendCountdown((prev) => prev - 1)
    }, 1000)
    return () => clearInterval(timer)
  }, [resendCountdown])

  const validateStep1 = () => {
    const errors = {}

    if (!username.trim()) {
      errors.username = 'Username is required'
    }
    if (!email.trim()) {
      errors.email = 'Email is required'
    } else if (!EMAIL_REGEX.test(email.trim())) {
      errors.email = 'Enter a valid email address'
    }
    if (!password) {
      errors.password = 'Password is required'
    } else if (password.length < 8) {
      errors.password = 'Password must be at least 8 characters'
    }
    if (!confirmPassword) {
      errors.confirmPassword = 'Please confirm your password'
    } else if (password !== confirmPassword) {
      errors.confirmPassword = 'Passwords do not match'
    }

    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  const handleRegister = async (e) => {
    e.preventDefault()
    setSubmitted(true)
    setFormError('')
    setFieldErrors({})

    if (!validateStep1()) {
      return
    }

    setLoading(true)
    try {
      await register({
        username: username.trim(),
        email: email.trim(),
        password,
      })
      setStep(2)
      setSubmitted(false)
      setOtp('')
    } catch (error) {
      const status = error.response?.status
      const message = (error.response?.data?.message || '').toLowerCase()

      if (status === 409 && message.includes('email')) {
        setFieldErrors({ email: 'Email already registered' })
      } else if (status === 409 && message.includes('username')) {
        setFieldErrors({ username: 'Username already taken' })
      } else {
        setFormError('Could not send OTP. Please check your email address.')
      }
    } finally {
      setLoading(false)
    }
  }

  const handleVerifyOtp = async (e) => {
    e.preventDefault()
    setFormError('')
    setSuccessMessage('')

    if (!otp || otp.length !== 6) {
      setFormError('Invalid or expired OTP')
      return
    }

    setLoading(true)
    try {
      await verifySignupOtp({ email: email.trim(), otp })
      setSuccessMessage('Account verified! Redirecting...')
      setTimeout(() => navigate('/login'), 2000)
    } catch {
      setFormError('Invalid or expired OTP')
    } finally {
      setLoading(false)
    }
  }

  const handleResend = async () => {
    if (resendCountdown > 0) return

    setFormError('')
    setLoading(true)
    try {
      await resendSignupOtp({ email: email.trim() })
      setResendCountdown(30)
    } catch {
      setFormError('Could not send OTP. Please check your email address.')
    } finally {
      setLoading(false)
    }
  }

  if (step === 2) {
    return (
      <div className="auth-page">
        <div className="auth-card">
          <h1 className="auth-title">Verify your email</h1>
          <p className="auth-subtitle">Code sent to {email}</p>

          <form className="auth-form" onSubmit={handleVerifyOtp} noValidate>
            {formError && <p className="auth-form-error">{formError}</p>}
            {successMessage && <p className="auth-success">{successMessage}</p>}

            <div className="auth-field">
              <label className="auth-label" htmlFor="signup-otp">
                6-digit code
              </label>
              <input
                id="signup-otp"
                type="text"
                inputMode="numeric"
                pattern="[0-9]*"
                maxLength={6}
                className="auth-input auth-input-otp"
                value={otp}
                onChange={(e) => setOtp(e.target.value.replace(/\D/g, ''))}
                autoComplete="one-time-code"
              />
            </div>

            <button type="submit" className="auth-button" disabled={loading || !!successMessage}>
              {loading ? 'Verifying...' : 'Verify'}
            </button>

            <div className="auth-resend-row">
              <button
                type="button"
                className="auth-button-secondary"
                onClick={handleResend}
                disabled={loading || resendCountdown > 0 || !!successMessage}
              >
                {resendCountdown > 0 ? `Resend in ${resendCountdown}s` : 'Resend code'}
              </button>
            </div>
          </form>

          <p className="auth-link-row">
            <Link to="/login" className="auth-link">
              Back to login
            </Link>
          </p>
        </div>
      </div>
    )
  }

  return (
    <div className="auth-page">
      <div className="auth-card">
        <h1 className="auth-title">Create account</h1>
        <p className="auth-subtitle">Sign up for Sprinklr Chat</p>

        <form className="auth-form" onSubmit={handleRegister} noValidate>
          {formError && <p className="auth-form-error">{formError}</p>}

          <div className="auth-field">
            <label className="auth-label" htmlFor="signup-username">
              Username
            </label>
            <input
              id="signup-username"
              type="text"
              className={`auth-input ${submitted && fieldErrors.username ? 'auth-input-error' : ''}`}
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoComplete="username"
            />
            {submitted && fieldErrors.username && (
              <p className="auth-field-error">{fieldErrors.username}</p>
            )}
          </div>

          <div className="auth-field">
            <label className="auth-label" htmlFor="signup-email">
              Email
            </label>
            <input
              id="signup-email"
              type="email"
              className={`auth-input ${submitted && fieldErrors.email ? 'auth-input-error' : ''}`}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoComplete="email"
            />
            {submitted && fieldErrors.email && (
              <p className="auth-field-error">{fieldErrors.email}</p>
            )}
          </div>

          <div className="auth-field">
            <label className="auth-label" htmlFor="signup-password">
              Password
            </label>
            <input
              id="signup-password"
              type="password"
              className={`auth-input ${submitted && fieldErrors.password ? 'auth-input-error' : ''}`}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="new-password"
            />
            {submitted && fieldErrors.password && (
              <p className="auth-field-error">{fieldErrors.password}</p>
            )}
          </div>

          <div className="auth-field">
            <label className="auth-label" htmlFor="signup-confirm-password">
              Confirm password
            </label>
            <input
              id="signup-confirm-password"
              type="password"
              className={`auth-input ${submitted && fieldErrors.confirmPassword ? 'auth-input-error' : ''}`}
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              autoComplete="new-password"
            />
            {submitted && fieldErrors.confirmPassword && (
              <p className="auth-field-error">{fieldErrors.confirmPassword}</p>
            )}
          </div>

          <button type="submit" className="auth-button" disabled={loading}>
            {loading ? 'Sending code...' : 'Continue'}
          </button>
        </form>

        <p className="auth-link-row">
          Already have an account?{' '}
          <Link to="/login" className="auth-link">
            Sign in
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
