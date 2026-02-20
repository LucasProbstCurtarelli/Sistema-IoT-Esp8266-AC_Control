import axios from 'axios';

const API_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080';

export const api = axios.create({
  baseURL: API_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true,
  timeout: 10000, // 10 seconds timeout
});

// Request interceptor for debugging
api.interceptors.request.use(
  (config) => {
    console.log(`[API] ${config.method?.toUpperCase()} ${config.url}`, {
      baseURL: config.baseURL,
      data: config.data,
      headers: config.headers,
    });
    return config;
  },
  (error) => {
    console.error('[API] Request error:', error);
    return Promise.reject(error);
  }
);

// Response interceptor for debugging
api.interceptors.response.use(
  (response) => {
    console.log(`[API] Response from ${response.config.url}:`, {
      status: response.status,
      data: response.data,
    });
    return response;
  },
  (error) => {
    console.error(`[API] Response error from ${error.config?.url}:`, {
      message: error.message,
      response: error.response?.data,
      status: error.response?.status,
      statusText: error.response?.statusText,
    });
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

export const acService = {
  sendCommand: async (payload: AcPayload) => {
    const response = await api.post('/api/ac', payload);
    return response.data;
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
      console.log(`[LightingService] Sending command to ${deviceName}:`, command);
      
      const response = await api.post(`/api/lights/${deviceName}`, command);
      
      console.log(`[LightingService] Success response:`, response.data);
      return response.data;
    } catch (error: unknown) {
      const axiosError = error as { 
        message?: string;
        response?: { data?: unknown; status?: number };
        config?: unknown;
      };
      console.error(`[LightingService] Error in sendCommand:`, {
        deviceName,
        command,
        error: axiosError?.message,
        response: axiosError?.response?.data,
        status: axiosError?.response?.status,
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
      console.log(`[LightingService] Getting status for ${deviceName}`);
      
      const response = await api.get(`/api/lights/${deviceName}/status`);
      
      console.log(`[LightingService] Status response:`, response.data);
      return response.data;
    } catch (error: unknown) {
      const axiosError = error as { 
        message?: string;
        response?: { data?: unknown; status?: number };
      };
      console.error(`[LightingService] Error getting status:`, {
        deviceName,
        error: axiosError?.message,
        response: axiosError?.response?.data,
        status: axiosError?.response?.status,
      });
      throw error;
    }
  },
  
  /**
   * Sets the light state (on/off) - maintained for backward compatibility.
   * 
   * @param deviceName The device name
   * @param isOn True to turn on, false to turn off
   * @returns Promise with the API response
   */
  setLightState: async (deviceName: string, isOn: boolean) => {
    return lightingService.sendCommand(deviceName, { state: isOn });
  },
};
