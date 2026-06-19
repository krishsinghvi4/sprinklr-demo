import axios from 'axios'

const axiosInstance = axios.create({
  baseURL: '/',
})

axiosInstance.interceptors.request.use((config) => {
  const token = localStorage.getItem('chat_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

axiosInstance.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status
    const url = error.config?.url || ''
    const isAuthEndpoint = url.includes('/api/auth/')
    const isMcpEndpoint = url.includes('/api/v1/mcp/')

    if (status === 401 && !isAuthEndpoint && !isMcpEndpoint) {
      localStorage.removeItem('chat_token')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)

export default axiosInstance
