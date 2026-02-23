"use client";

import { useState, useCallback, useEffect } from "react";
import { PowerOff, RefreshCw, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { LightCard, type LightState } from "@/components/light-card";
import { lightingService, deviceService } from "@/services/api";
import type { DeviceResponse } from "@/services/api";
import { toast } from "sonner";
import { ErrorBoundary } from "@/components/ErrorBoundary";

// Default state for lights
const DEFAULT_LIGHT_STATE: LightState = {
  state: false,
  brightness: 100,
  color: "#FFFFFF",
};

// LocalStorage key for persisting linked lights
const LINKED_LIGHTS_STORAGE_KEY = "automacao_residencial_linked_lights";

/**
 * Loads linked lights from localStorage.
 * Returns an empty object if no data exists or if parsing fails.
 */
function loadLinkedLightsFromStorage(): Record<string, string[]> {
  if (typeof window === "undefined") return {};
  
  try {
    const stored = localStorage.getItem(LINKED_LIGHTS_STORAGE_KEY);
    if (!stored) return {};
    
    const parsed = JSON.parse(stored);
    // Validate that it's an object with string keys and string array values
    if (typeof parsed === "object" && parsed !== null && !Array.isArray(parsed)) {
      return parsed;
    }
    return {};
  } catch (error) {
    console.warn("[IluminacaoPage] Failed to load linked lights from storage:", error);
    return {};
  }
}

/**
 * Saves linked lights to localStorage.
 */
function saveLinkedLightsToStorage(linkedLights: Record<string, string[]>): void {
  if (typeof window === "undefined") return;
  
  try {
    localStorage.setItem(LINKED_LIGHTS_STORAGE_KEY, JSON.stringify(linkedLights));
  } catch (error) {
    console.warn("[IluminacaoPage] Failed to save linked lights to storage:", error);
  }
}

/**
 * Iluminação (Lighting) Dashboard Page
 * 
 * Features:
 * - Control multiple smart bulbs
 * - Turn off all lights at once
 * - Refresh state from devices
 * - Responsive grid layout
 * - Persistent light linking across page refreshes
 */
export default function IluminacaoPage() {
  // Device list from backend
  const [devices, setDevices] = useState<DeviceResponse[]>([]);
  const [isLoadingDevices, setIsLoadingDevices] = useState(true);
  
  // Track states for all lights
  const [lightStates, setLightStates] = useState<Record<string, LightState>>({});
  
  // Track linked lights - maps deviceName to array of linked device names
  // Initialize from localStorage on mount
  const [linkedLights, setLinkedLights] = useState<Record<string, string[]>>(() => 
    loadLinkedLightsFromStorage()
  );
  
  // Loading states for bulk actions
  const [isTurningOffAll, setIsTurningOffAll] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);

  /**
   * Fetches devices from the backend.
   * Also validates and cleans up linked lights to remove references to non-existent devices.
   */
  const loadDevices = useCallback(async () => {
    setIsLoadingDevices(true);
    try {
      const deviceList = await deviceService.getAllDevices();
      setDevices(deviceList);
      
      // Get device names for validation
      const deviceNames = new Set(deviceList.map(d => d.deviceName));
      
      // Validate and clean up linked lights - remove links to devices that no longer exist
      setLinkedLights(prev => {
        const cleaned: Record<string, string[]> = {};
        let hasChanges = false;
        
        Object.entries(prev).forEach(([deviceName, linked]) => {
          // Only keep links if both the device and its linked devices exist
          if (deviceNames.has(deviceName)) {
            const validLinks = linked.filter(linkedName => deviceNames.has(linkedName));
            if (validLinks.length > 0) {
              cleaned[deviceName] = validLinks;
            }
            if (validLinks.length !== linked.length) {
              hasChanges = true;
            }
          } else {
            hasChanges = true;
          }
        });
        
        // Save cleaned links if there were changes
        if (hasChanges) {
          saveLinkedLightsToStorage(cleaned);
        }
        
        return cleaned;
      });
      
      // Initialize light states for all devices
      setLightStates(prevStates => {
        const newStates: Record<string, LightState> = { ...prevStates };
        deviceList.forEach(device => {
          if (!newStates[device.deviceName]) {
            newStates[device.deviceName] = { ...DEFAULT_LIGHT_STATE };
          }
        });
        return newStates;
      });
    } catch (error) {
      console.error("[IluminacaoPage] Error loading devices:", error);
      toast.error("Erro ao carregar dispositivos", {
        description: "Não foi possível obter a lista de dispositivos.",
      });
    } finally {
      setIsLoadingDevices(false);
    }
  }, []);

  /**
   * Fetches the current state of all lights from the backend.
   * This syncs the UI with the actual device states.
   */
  const refreshLightStates = useCallback(async () => {
    setIsRefreshing(true);
    
    try {
      const results = await Promise.allSettled(
        devices.map(async (device) => {
          const response = await lightingService.getStatus(device.deviceName);
          return { deviceName: device.deviceName, ...response };
        })
      );
      
      setLightStates(prevStates => {
        const newStates: Record<string, LightState> = {};
        
        results.forEach((result, index) => {
          const deviceName = devices[index]?.deviceName;
          if (!deviceName) return;
          
          if (result.status === 'fulfilled' && result.value) {
            newStates[deviceName] = {
              state: result.value.state ?? false,
              brightness: result.value.brightness ?? 100,
              color: result.value.color ?? "#FFFFFF",
            };
          } else {
            // Keep existing state if fetch failed
            newStates[deviceName] = prevStates[deviceName] || { ...DEFAULT_LIGHT_STATE };
          }
        });
        
        return newStates;
      });
    } catch (error) {
      console.error("[IluminacaoPage] Error refreshing states:", error);
      toast.error("Erro ao atualizar estados", {
        description: "Não foi possível obter o estado atual das lâmpadas.",
      });
    } finally {
      setIsRefreshing(false);
    }
  }, [devices]); // Depend on devices array

  /**
   * Turns off all lights at once.
   */
  const turnOffAllLights = useCallback(async () => {
    // Check if any light is on
    const anyLightOn = Object.values(lightStates).some(state => state.state);
    
    if (!anyLightOn) {
      toast.info("Todas as lâmpadas já estão desligadas");
      return;
    }
    
    setIsTurningOffAll(true);
    
    try {
      // Send off command to all lights that are on
      const results = await Promise.allSettled(
        devices.filter(device => lightStates[device.deviceName]?.state)
          .map(device => lightingService.sendCommand(device.deviceName, { state: false }))
      );
      
      // Check for failures
      const failures = results.filter(r => r.status === 'rejected');
      
      if (failures.length > 0) {
        toast.error("Erro ao desligar algumas lâmpadas", {
          description: `${failures.length} de ${results.length} comandos falharam.`,
        });
      }
      
      // Update local state for successful commands
      setLightStates(prev => {
        const newStates = { ...prev };
        devices.forEach((device, index) => {
          if (prev[device.deviceName]?.state && results[index]?.status === 'fulfilled') {
            newStates[device.deviceName] = { ...prev[device.deviceName], state: false };
          }
        });
        return newStates;
      });
      
    } catch (error) {
      console.error("[IluminacaoPage] Error turning off all lights:", error);
      toast.error("Erro ao desligar lâmpadas", {
        description: "Não foi possível desligar todas as lâmpadas.",
      });
    } finally {
      setIsTurningOffAll(false);
    }
  }, [lightStates, devices]);

  /**
   * Handles state changes from individual LightCard components.
   */
  const handleLightStateChange = useCallback((deviceName: string, newState: LightState) => {
    setLightStates(prev => ({
      ...prev,
      [deviceName]: newState,
    }));
  }, []);

  /**
   * Handles state changes for linked devices when a command is propagated.
   * Merges the new state with the existing state of the linked device.
   */
  const handleLinkedDeviceStateChange = useCallback((deviceName: string, stateUpdate: LightState) => {
    setLightStates(prev => {
      const currentState = prev[deviceName] || { ...DEFAULT_LIGHT_STATE };
      // Only update fields that are actually provided in stateUpdate
      const mergedState: LightState = {
        state: stateUpdate.state !== undefined ? stateUpdate.state : currentState.state,
        brightness: stateUpdate.brightness !== undefined ? stateUpdate.brightness : currentState.brightness,
        color: stateUpdate.color !== undefined ? stateUpdate.color : currentState.color,
      };
      return {
        ...prev,
        [deviceName]: mergedState,
      };
    });
  }, []);

  /**
   * Toggles linking between two lights.
   * When linking, creates a bidirectional link between the two devices.
   * Persists changes to localStorage.
   */
  const handleLinkToggle = useCallback((deviceName1: string, deviceName2: string) => {
    setLinkedLights(prev => {
      const newLinks = { ...prev };
      
      // Get current links for both devices
      const links1 = newLinks[deviceName1] || [];
      const links2 = newLinks[deviceName2] || [];
      
      // Check if they're already linked
      const isLinked = links1.includes(deviceName2) || links2.includes(deviceName1);
      
      if (isLinked) {
        // Unlink: remove from both arrays
        newLinks[deviceName1] = links1.filter(name => name !== deviceName2);
        newLinks[deviceName2] = links2.filter(name => name !== deviceName1);
        
        // Clean up empty arrays
        if (newLinks[deviceName1].length === 0) delete newLinks[deviceName1];
        if (newLinks[deviceName2].length === 0) delete newLinks[deviceName2];
      } else {
        // Link: add to both arrays
        newLinks[deviceName1] = [...links1, deviceName2];
        newLinks[deviceName2] = [...links2, deviceName1];
      }
      
      // Persist to localStorage
      saveLinkedLightsToStorage(newLinks);
      
      return newLinks;
    });
  }, []);

  /**
   * Gets all devices linked to a given device.
   */
  const getLinkedDevices = useCallback((deviceName: string): string[] => {
    return linkedLights[deviceName] || [];
  }, [linkedLights]);

  /**
   * Checks if a device is linked to any other device.
   */
  const isDeviceLinked = useCallback((deviceName: string): boolean => {
    const links = linkedLights[deviceName];
    return links !== undefined && links.length > 0;
  }, [linkedLights]);

  // Load devices on mount
  useEffect(() => {
    loadDevices();
  }, [loadDevices]);

  // Fetch light states after devices are loaded
  useEffect(() => {
    if (devices.length > 0) {
      refreshLightStates();
    }
  }, [devices.length, refreshLightStates]);

  // Calculate summary stats
  const lightsOn = Object.values(lightStates).filter(s => s.state).length;
  const totalLights = devices.length;

  return (
    <ErrorBoundary>
      <div className="flex flex-col gap-6">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Iluminação</h1>
          <p className="text-muted-foreground mt-1">
            Controle das lâmpadas inteligentes • {lightsOn}/{totalLights} ligadas
          </p>
        </div>
        
        {/* Bulk actions */}
        <div className="flex gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={refreshLightStates}
            disabled={isRefreshing}
          >
            {isRefreshing ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <RefreshCw className="h-4 w-4" />
            )}
            <span className="ml-2 hidden sm:inline">Atualizar</span>
          </Button>
          
          <Button
            variant="destructive"
            size="sm"
            onClick={turnOffAllLights}
            disabled={isTurningOffAll || lightsOn === 0}
          >
            {isTurningOffAll ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <PowerOff className="h-4 w-4" />
            )}
            <span className="ml-2 hidden sm:inline">Desligar Todas</span>
          </Button>
        </div>
      </div>

      {/* Light cards grid */}
      {isLoadingDevices ? (
        <div className="flex items-center justify-center py-12">
          <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
        </div>
      ) : devices.length === 0 ? (
        <div className="text-center py-12 text-muted-foreground">
          Nenhum dispositivo encontrado.
        </div>
      ) : (
        <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
          {devices.map((device) => (
            <LightCard
              key={device.deviceName}
              deviceName={device.deviceName}
              displayName={device.displayName}
              location={device.location}
              initialState={lightStates[device.deviceName]}
              onStateChange={handleLightStateChange}
              linkedDevices={getLinkedDevices(device.deviceName)}
              isLinked={isDeviceLinked(device.deviceName)}
              availableDevices={devices.filter(d => d.deviceName !== device.deviceName).map(d => ({
                deviceName: d.deviceName,
                displayName: d.displayName,
              }))}
              onLinkToggle={handleLinkToggle}
              onLinkedDeviceStateChange={handleLinkedDeviceStateChange}
            />
          ))}
        </div>
      )}
      </div>
    </ErrorBoundary>
  );
}
