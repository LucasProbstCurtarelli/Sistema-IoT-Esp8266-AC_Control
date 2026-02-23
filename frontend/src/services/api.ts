import axios from 'axios';
import { API_CONFIG } from '@/config/api';

export const api = axios.create({
  baseURL: API_CONFIG.baseURL,
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
  timeout: API_CONFIG.timeout,
});

// Request interceptor - only log in development
api.interceptors.request.use(
  (config) => {
    if (process.env.NODE_ENV === 'development') {
      console.log(`[API] ${config.method?.toUpperCase()} ${config.url}`, {
        baseURL: config.baseURL,
        // Don't log sensitive data
        hasData: !!config.data,
      });
    }
    return config;
  },
  (error) => {
    // Always log errors
    console.error('[API] Request error:', error);
    return Promise.reject(error);
  }
);

// Response interceptor - only log in development
api.interceptors.response.use(
  (response) => {
    if (process.env.NODE_ENV === 'development') {
      console.log(`[API] Response from ${response.config.url}:`, {
        status: response.status,
        hasData: !!response.data,
      });
    }
    return response;
  },
  (error) => {
    // Always log errors, but sanitize sensitive data
    const requestUrl = error.config?.url || error.request?.url || 'unknown';
    const method = (error.config?.method || error.request?.method || 'unknown').toUpperCase();
    const isNetworkError = !error.response;
    
    // Build error details with guaranteed meaningful values
    const errorDetails: Record<string, any> = {};
    
    // Always include these properties
    errorDetails.url = String(requestUrl);
    errorDetails.method = String(method);
    errorDetails.isNetworkError = Boolean(isNetworkError);
    
    // Add message if available
    if (error.message) {
      errorDetails.message = String(error.message);
    }
    
    // Add response status if available
    if (error.response?.status !== undefined) {
      errorDetails.status = Number(error.response.status);
    }
    
    // Add status text if available
    if (error.response?.statusText) {
      errorDetails.statusText = String(error.response.statusText);
    }
    
    // Don't log full response data in production
    if (process.env.NODE_ENV === 'development') {
      if (error.response?.data !== undefined) {
        errorDetails.responseData = error.response.data;
      }
      if (error.config) {
        errorDetails.config = {
          baseURL: error.config.baseURL,
          url: error.config.url,
          withCredentials: error.config.withCredentials,
        };
      }
    }
    
    // Log with explicit stringification to ensure visibility
    const errorSummary = `[API] Response error from ${requestUrl} (${method})`;
    if (Object.keys(errorDetails).length > 0) {
      console.error(errorSummary, errorDetails);
    } else {
      // Fallback: log raw error if details couldn't be extracted
      console.error(errorSummary, error);
    }

    // Redirect to login on 401/403 (unauthorized/forbidden)
    // Only redirect if we're on the client side and not already on the login page
    // Don't redirect for /api/me calls - those are expected to fail when not authenticated
    if (typeof window !== 'undefined' && 
        (error.response?.status === 401 || error.response?.status === 403)) {
      const currentPath = window.location.pathname;
      const requestUrl = error.config?.url || '';
      
      // Don't redirect if:
      // 1. Already on login page
      // 2. Request is to /api/me (expected to fail when not authenticated)
      if (currentPath !== '/login' && 
          !currentPath.startsWith('/login') &&
          !requestUrl.includes('/api/me')) {
        // Clear any stale auth data
        window.location.href = '/login';
      }
    }

    return Promise.reject(error);
  }
);

export interface AcPayload {
  power: string;
  temp: number;
}

export interface LightCommandRequest {
  state?: boolean;
  brightness?: number; // 0-100
  color?: string; // "#RRGGBB"
}

export interface AcStatusResponse {
  connected: boolean;
  lastMessageTimestamp: number;
  lastMessageSecondsAgo: number;
}

export const acService = {
  sendCommand: async (payload: AcPayload) => {
    const response = await api.post('/api/ac', payload);
    return response.data;
  },
  getStatus: async (): Promise<AcStatusResponse> => {
    try {
      const response = await api.get('/api/ac/status', { timeout: 5000 });
      return response.data;
    } catch (error) {
      // On timeout or error, return disconnected status
      console.error('[AcService] Error getting status:', error);
      return {
        connected: false,
        lastMessageTimestamp: 0,
        lastMessageSecondsAgo: -1,
      };
    }
  },
};

export interface LightStatusResponse {
  success: boolean;
  device: string;
  state: boolean;
  brightness: number;
  color: string;
  lastUpdated?: string;
}

export interface DeviceResponse {
  id: string;
  deviceName: string;
  displayName: string;
  location: string;
  deviceType: string;
  enabled: boolean;
}

export const deviceService = {
  /**
   * Gets all enabled devices.
   * 
   * @returns Promise with list of devices
   */
  getAllDevices: async (): Promise<DeviceResponse[]> => {
    try {
      if (process.env.NODE_ENV === 'development') {
        console.log('[DeviceService] Fetching all devices');
      }
      
      const response = await api.get('/api/devices');
      return response.data;
    } catch (error: unknown) {
      const axiosError = error as { 
        message?: string;
        response?: { data?: unknown; status?: number };
      };
      console.error('[DeviceService] Error fetching devices:', {
        error: axiosError?.message,
        status: axiosError?.response?.status,
      });
      throw error;
    }
  },

  /**
   * Gets a device by device name.
   * 
   * @param deviceName The device name (e.g., "lampada_1")
   * @returns Promise with device information
   */
  getDevice: async (deviceName: string): Promise<DeviceResponse> => {
    try {
      if (process.env.NODE_ENV === 'development') {
        console.log(`[DeviceService] Fetching device: ${deviceName}`);
      }
      
      const response = await api.get(`/api/devices/${deviceName}`);
      return response.data;
    } catch (error: unknown) {
      const axiosError = error as { 
        message?: string;
        response?: { data?: unknown; status?: number };
      };
      console.error(`[DeviceService] Error fetching device ${deviceName}:`, {
        error: axiosError?.message,
        status: axiosError?.response?.status,
      });
      throw error;
    }
  },
};

export const lightingService = {
  /**
   * Sends a light command with optional state, brightness, and color.
   * 
   * @param deviceName The device name (e.g., "lampada_1", "lampada_2")
   * @param command Command object with optional state, brightness (0-100), and color (hex)
   * @returns Promise with the API response
   */
  sendCommand: async (deviceName: string, command: LightCommandRequest) => {
    try {
      if (process.env.NODE_ENV === 'development') {
        console.log(`[LightingService] Sending command to ${deviceName}:`, command);
      }
      
      const response = await api.post(`/api/lights/${deviceName}`, command);
      
      if (process.env.NODE_ENV === 'development') {
        console.log(`[LightingService] Success response:`, response.data);
      }
      return response.data;
    } catch (error: unknown) {
      const axiosError = error as { 
        message?: string;
        response?: { data?: unknown; status?: number };
        config?: unknown;
      };
      // Always log errors
      console.error(`[LightingService] Error in sendCommand:`, {
        deviceName,
        error: axiosError?.message,
        status: axiosError?.response?.status,
        // Only log full response in development
        ...(process.env.NODE_ENV === 'development' && {
          command,
          response: axiosError?.response?.data,
        }),
      });
      throw error;
    }
  },

  /**
   * Gets the current status of a light.
   * 
   * @param deviceName The device name (e.g., "lampada_1", "lampada_2")
   * @returns Promise with the light status
   */
  getStatus: async (deviceName: string): Promise<LightStatusResponse> => {
    try {
      if (process.env.NODE_ENV === 'development') {
        console.log(`[LightingService] Getting status for ${deviceName}`);
      }
      
      const response = await api.get(`/api/lights/${deviceName}/status`);
      
      if (process.env.NODE_ENV === 'development') {
        console.log(`[LightingService] Status response:`, response.data);
      }
      return response.data;
    } catch (error: unknown) {
      const axiosError = error as { 
        message?: string;
        response?: { data?: unknown; status?: number };
      };
      // Always log errors
      console.error(`[LightingService] Error getting status:`, {
        deviceName,
        error: axiosError?.message,
        status: axiosError?.response?.status,
        // Only log full response in development
        ...(process.env.NODE_ENV === 'development' && {
          response: axiosError?.response?.data,
        }),
      });
      throw error;
    }
  },
};
