import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  forgotPassword,
  verifyForgotOtp,
  resetPassword,
} from '../api/authApi'
import '../styles/auth.css'

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export default function ForgotPasswordPage() {
  const navigate = useNavigate()

  const [step, setStep] = useState(1)
  const [email, setEmail] = useState('')
  const [otp, setOtp] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showNewPassword, setShowNewPassword] = useState(false)
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)
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

  const handleStartOver = () => {
    setStep(1)
    setEmail('')
    setOtp('')
    setNewPassword('')
    setConfirmPassword('')
    setSubmitted(false)
    setFormError('')
    setFieldErrors({})
    setSuccessMessage('')
    setResendCountdown(0)
  }

  const handleForgotPassword = async (e) => {
    e.preventDefault()
    setSubmitted(true)
    setFormError('')
    setFieldErrors({})

    if (!email.trim()) {
      setFieldErrors({ email: 'Email is required' })
      return
    }
    if (!EMAIL_REGEX.test(email.trim())) {
      setFieldErrors({ email: 'Enter a valid email address' })
      return
    }

    setLoading(true)
    try {
      await forgotPassword({ email: email.trim() })
      setStep(2)
      setSubmitted(false)
      setOtp('')
    } catch {
      setFormError('Something went wrong. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  const handleVerifyOtp = async (e) => {
    e.preventDefault()
    setFormError('')

    if (!otp || otp.length !== 6) {
      setFormError('Invalid or expired code')
      return
    }

    setLoading(true)
    try {
      await verifyForgotOtp({ email: email.trim(), otp })
      setStep(3)
      setSubmitted(false)
      setNewPassword('')
      setConfirmPassword('')
      setFieldErrors({})
    } catch {
      setFormError('Invalid or expired code')
    } finally {
      setLoading(false)
    }
  }

  const handleResend = async () => {
    if (resendCountdown > 0) return

    setFormError('')
    setLoading(true)
    try {
      await forgotPassword({ email: email.trim() })
      setResendCountdown(30)
    } catch {
      setFormError('Something went wrong. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  const validateStep3 = () => {
    const errors = {}

    if (!newPassword) {
      errors.newPassword = 'Password is required'
    } else if (newPassword.length < 8) {
      errors.newPassword = 'Password must be at least 8 characters'
    }
    if (!confirmPassword) {
      errors.confirmPassword = 'Please confirm your password'
    } else if (newPassword !== confirmPassword) {
      errors.confirmPassword = 'Passwords do not match'
    }

    setFieldErrors(errors)
    return Object.keys(errors).length === 0
  }

  const handleResetPassword = async (e) => {
    e.preventDefault()
    setSubmitted(true)
    setFormError('')
    setSuccessMessage('')

    if (!validateStep3()) {
      return
    }

    setLoading(true)
    try {
      await resetPassword({ email: email.trim(), newPassword })
      setSuccessMessage('Password reset! Redirecting...')
      setTimeout(() => navigate('/login'), 2000)
    } catch (error) {
      if (error.response?.status === 400) {
        setFormError('Session expired.')
      } else {
        setFormError('Something went wrong. Please try again.')
      }
    } finally {
      setLoading(false)
    }
  }

  if (step === 2) {
    return (
      <div className="auth-page">
        <div className="auth-card">
          <h1 className="auth-title">Enter code</h1>
          <p className="auth-subtitle">
            If {email} is registered, a code was sent
          </p>

          <form className="auth-form" onSubmit={handleVerifyOtp} noValidate>
            {formError && <p className="auth-form-error">{formError}</p>}

            <div className="auth-field">
              <label className="auth-label" htmlFor="forgot-otp">
                6-digit code
              </label>
              <input
                id="forgot-otp"
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

            <button type="submit" className="auth-button" disabled={loading}>
              {loading ? 'Verifying...' : 'Verify code'}
            </button>

            <div className="auth-resend-row">
              <button
                type="button"
                className="auth-button-secondary"
                onClick={handleResend}
                disabled={loading || resendCountdown > 0}
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

  if (step === 3) {
    return (
      <div className="auth-page">
        <div className="auth-card">
          <h1 className="auth-title">New password</h1>
          <p className="auth-subtitle">Choose a new password for your account</p>

          <form className="auth-form" onSubmit={handleResetPassword} noValidate>
            {formError && (
              <div>
                <p className="auth-form-error">{formError}</p>
                {formError === 'Session expired.' && (
                  <button
                    type="button"
                    className="auth-button-secondary"
                    style={{ marginTop: '8px', width: '100%' }}
                    onClick={handleStartOver}
                  >
                    Start Over
                  </button>
                )}
              </div>
            )}
            {successMessage && <p className="auth-success">{successMessage}</p>}

            <div className="auth-field">
              <label className="auth-label" htmlFor="reset-password">
                New password
              </label>
              <div className="auth-password-wrapper">
                <input
                  id="reset-password"
                  type={showNewPassword ? 'text' : 'password'}
                  className={`auth-input ${submitted && fieldErrors.newPassword ? 'auth-input-error' : ''}`}
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  autoComplete="new-password"
                />
                <button
                  type="button"
                  className="auth-toggle-password"
                  onClick={() => setShowNewPassword((prev) => !prev)}
                >
                  {showNewPassword ? 'Hide' : 'Show'}
                </button>
              </div>
              {submitted && fieldErrors.newPassword && (
                <p className="auth-field-error">{fieldErrors.newPassword}</p>
              )}
            </div>

            <div className="auth-field">
              <label className="auth-label" htmlFor="reset-confirm-password">
                Confirm password
              </label>
              <div className="auth-password-wrapper">
                <input
                  id="reset-confirm-password"
                  type={showConfirmPassword ? 'text' : 'password'}
                  className={`auth-input ${submitted && fieldErrors.confirmPassword ? 'auth-input-error' : ''}`}
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  autoComplete="new-password"
                />
                <button
                  type="button"
                  className="auth-toggle-password"
                  onClick={() => setShowConfirmPassword((prev) => !prev)}
                >
                  {showConfirmPassword ? 'Hide' : 'Show'}
                </button>
              </div>
              {submitted && fieldErrors.confirmPassword && (
                <p className="auth-field-error">{fieldErrors.confirmPassword}</p>
              )}
            </div>

            <button
              type="submit"
              className="auth-button"
              disabled={loading || !!successMessage}
            >
              {loading ? 'Resetting...' : 'Reset password'}
            </button>
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
        <h1 className="auth-title">Forgot password</h1>
        <p className="auth-subtitle">Enter your email to receive a reset code</p>

        <form className="auth-form" onSubmit={handleForgotPassword} noValidate>
          {formError && <p className="auth-form-error">{formError}</p>}

          <div className="auth-field">
            <label className="auth-label" htmlFor="forgot-email">
              Email
            </label>
            <input
              id="forgot-email"
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

          <button type="submit" className="auth-button" disabled={loading}>
            {loading ? 'Sending...' : 'Send code'}
          </button>
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
