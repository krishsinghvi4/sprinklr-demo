import axiosInstance from './axiosInstance'

export async function register({ username, email, password }) {
  const response = await axiosInstance.post('/api/auth/register', {
    username,
    email,
    password,
  })
  return response.data
}

export async function verifySignupOtp({ email, otp }) {
  const response = await axiosInstance.post('/api/auth/verify-signup-otp', {
    email,
    otp,
  })
  return response.data
}

export async function login({ email, password }) {
  const response = await axiosInstance.post('/api/auth/login', {
    email,
    password,
  })
  return response.data
}

export async function forgotPassword({ email }) {
  const response = await axiosInstance.post('/api/auth/forgot-password', {
    email,
  })
  return response.data
}

export async function verifyForgotOtp({ email, otp }) {
  const response = await axiosInstance.post('/api/auth/verify-forgot-otp', {
    email,
    otp,
  })
  return response.data
}

export async function resetPassword({ email, newPassword }) {
  const response = await axiosInstance.post('/api/auth/reset-password', {
    email,
    newPassword,
  })
  return response.data
}
