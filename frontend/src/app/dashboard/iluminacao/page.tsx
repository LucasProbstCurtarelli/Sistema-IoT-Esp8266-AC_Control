"use client";

import { useState, useCallback, useEffect, useRef } from "react";
import { Button } from "@/components/ui/button";
import { LightCard, type LightState } from "@/components/light-card";
import { lightingService, deviceService } from "@/services/api";
import type { DeviceResponse } from "@/services/api";
import { toast } from "sonner";
import { ErrorBoundary } from "@/components/ErrorBoundary";

const DEFAULT_LIGHT_STATE: LightState = {
  state: false,
  brightness: 100,
  color: "#FFFFFF",
};

function toggleLinkMap(
  links: Record<string, string[]>,
  deviceName1: string,
  deviceName2: string,
): Record<string, string[]> {
  const next: Record<string, string[]> = {
    ...links,
    [deviceName1]: [...(links[deviceName1] ?? [])],
    [deviceName2]: [...(links[deviceName2] ?? [])],
  };
  const isLinked = next[deviceName1].includes(deviceName2) || next[deviceName2].includes(deviceName1);

  if (isLinked) {
    next[deviceName1] = next[deviceName1].filter((name) => name !== deviceName2);
    next[deviceName2] = next[deviceName2].filter((name) => name !== deviceName1);
  } else {
    next[deviceName1] = [...next[deviceName1], deviceName2];
    next[deviceName2] = [...next[deviceName2], deviceName1];
  }

  if (next[deviceName1].length === 0) delete next[deviceName1];
  if (next[deviceName2].length === 0) delete next[deviceName2];
  return next;
}

export default function IluminacaoPage() {
  // Device list from backend
  const [devices, setDevices] = useState<DeviceResponse[]>([]);
  const [isLoadingDevices, setIsLoadingDevices] = useState(true);
  const [devicesFailed, setDevicesFailed] = useState(false);
  
  // Track states for all lights
  const [lightStates, setLightStates] = useState<Record<string, LightState>>({});
  
  const [linkedLights, setLinkedLights] = useState<Record<string, string[]>>({});
  const [linksAttempt, setLinksAttempt] = useState(0);
  const [linksLoading, setLinksLoading] = useState(true);
  const [linksFailed, setLinksFailed] = useState(false);
  const linksRef = useRef<Record<string, string[]>>({});
  
  // Loading states for bulk actions
  const [isTurningOffAll, setIsTurningOffAll] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);

  /**
   * Fetches devices from the backend.
   * Also validates and cleans up linked lights to remove references to non-existent devices.
   */
  const loadDevices = useCallback(async () => {
    setIsLoadingDevices(true);
    setDevicesFailed(false);
    try {
      const deviceList = await deviceService.getAllDevices();
      setDevices(deviceList);

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
      setDevicesFailed(true);
      toast.error("Não deu para ler as lâmpadas.");
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
      toast.error("Não deu para atualizar as lâmpadas.");
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
      toast.info("Todas já estão apagadas.");
      return;
    }
    
    setIsTurningOffAll(true);
    
    try {
      // Send off command to all lights that are on
      const targets = devices.filter(device => lightStates[device.deviceName]?.state);
      const results = await Promise.allSettled(
        targets.map(device => lightingService.sendCommand(device.deviceName, { state: false }))
      );
      
      const failures = results.filter(r => r.status === 'rejected');
      
      if (failures.length > 0) {
        toast.error("Não deu para apagar todas. Algumas continuam acesas.");
      }
      
      setLightStates(prev => {
        const newStates = { ...prev };
        targets.forEach((device, index) => {
          if (results[index]?.status === 'fulfilled') {
            newStates[device.deviceName] = { ...prev[device.deviceName], state: false };
          }
        });
        return newStates;
      });
      
    } catch (error) {
      console.error("[IluminacaoPage] Error turning off all lights:", error);
      toast.error("Não deu para apagar as lâmpadas.");
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

  const handleLinkToggle = useCallback((deviceName1: string, deviceName2: string) => {
    const previous = linksRef.current;
    const next = toggleLinkMap(previous, deviceName1, deviceName2);
    linksRef.current = next;
    setLinkedLights(next);

    void lightingService.saveLinks(next).catch(() => {
      if (linksRef.current === next) {
        linksRef.current = previous;
        setLinkedLights(previous);
      }
      toast.error("Não deu para guardar o vínculo.");
    });
  }, []);

  /**
   * Gets all devices linked to a given device.
   */
  const getLinkedDevices = useCallback((deviceName: string): string[] => {
    return linkedLights[deviceName] || [];
  }, [linkedLights]);

  // Load devices on mount
  useEffect(() => {
    loadDevices();
  }, [loadDevices]);

  useEffect(() => {
    let cancelled = false;
    setLinksLoading(true);

    lightingService.getLinks().then(
      (links) => {
        if (cancelled) return;
        linksRef.current = links;
        setLinkedLights(links);
        setLinksFailed(false);
      },
      () => {
        if (cancelled) return;
        setLinksFailed(true);
      }
    ).finally(() => {
      if (!cancelled) setLinksLoading(false);
    });

    return () => {
      cancelled = true;
    };
  }, [linksAttempt]);

  // Fetch light states after devices are loaded
  useEffect(() => {
    if (devices.length > 0) {
      refreshLightStates();
    }
  }, [devices.length, refreshLightStates]);

  // Calculate summary stats
  const lightsOn = Object.values(lightStates).filter(s => s.state).length;
  const totalLights = devices.length;

  const summary = isLoadingDevices
    ? "A ler as lâmpadas."
    : devicesFailed
      ? "Não deu para ler as lâmpadas."
      : totalLights === 0
        ? "Nenhuma lâmpada nesta casa."
        : lightsOn === 0
        ? "Todas apagadas."
        : lightsOn === totalLights
          ? "Todas acesas."
          : `${lightsOn} de ${totalLights} acesas.`;

  return (
    <ErrorBoundary>
      <div className="flex flex-col gap-8">
        <div>
          <h1 className="text-3xl font-bold leading-tight">Luzes</h1>
          <p className="mt-2 text-muted-foreground">{summary}</p>
        </div>

        {linksFailed && (
          <div className="text-muted-foreground">
            <p>Não deu para ler quais lâmpadas acendem juntas.</p>
            <button
              type="button"
              onClick={() => setLinksAttempt((attempt) => attempt + 1)}
              disabled={linksLoading}
              className="mt-2 min-h-11 underline underline-offset-4 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50"
            >
              {linksLoading ? "A tentar" : "Tentar de novo"}
            </button>
          </div>
        )}

        {!isLoadingDevices && devices.length > 0 && (
          <div className="flex flex-col gap-3">
            <Button
              variant="outline"
              onClick={refreshLightStates}
              disabled={isRefreshing}
              className="h-12"
            >
              {isRefreshing ? "A atualizar" : "Atualizar"}
            </Button>
            <Button
              variant="secondary"
              onClick={turnOffAllLights}
              disabled={isTurningOffAll || lightsOn === 0}
              className="h-12"
            >
              {isTurningOffAll ? "A apagar" : "Apagar as acesas"}
            </Button>
          </div>
        )}

        {!isLoadingDevices && devices.length > 0 && (
          <div className="flex flex-col">
            {devices.map((device) => (
              <LightCard
                key={device.deviceName}
                deviceName={device.deviceName}
                displayName={device.displayName}
                location={device.location}
                initialState={lightStates[device.deviceName]}
                onStateChange={handleLightStateChange}
                linkedDevices={getLinkedDevices(device.deviceName)}
                availableDevices={devices.filter(d => d.deviceName !== device.deviceName).map(d => ({
                  deviceName: d.deviceName,
                  displayName: d.displayName,
                }))}
                onLinkToggle={linksLoading ? undefined : handleLinkToggle}
                onLinkedDeviceStateChange={handleLinkedDeviceStateChange}
              />
            ))}
          </div>
        )}
      </div>
    </ErrorBoundary>
  );
}
