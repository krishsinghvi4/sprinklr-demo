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
    if (error.response?.status === 401) {
      localStorage.removeItem('chat_token')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)

export default axiosInstance
