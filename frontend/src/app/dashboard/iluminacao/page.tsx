"use client";

import { useState, useCallback, useEffect } from "react";
import { PowerOff, RefreshCw, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { LightCard, type LightState } from "@/components/light-card";
import { lightingService } from "@/services/api";
import { toast } from "sonner";

// Device configuration - could be fetched from API in the future
const DEVICES = [
  { deviceName: "lampada_1", displayName: "Lâmpada 1", location: "Sala de estar" },
  { deviceName: "lampada_2", displayName: "Lâmpada 2", location: "Quarto" },
] as const;

// Default state for lights
const DEFAULT_LIGHT_STATE: LightState = {
  state: false,
  brightness: 100,
  color: "#FFFFFF",
};

/**
 * Iluminação (Lighting) Dashboard Page
 * 
 * Features:
 * - Control multiple smart bulbs
 * - Turn off all lights at once
 * - Refresh state from devices
 * - Responsive grid layout
 */
export default function IluminacaoPage() {
  // Track states for all lights
  const [lightStates, setLightStates] = useState<Record<string, LightState>>(
    Object.fromEntries(
      DEVICES.map(d => [d.deviceName, { ...DEFAULT_LIGHT_STATE }])
    )
  );
  
  // Loading states for bulk actions
  const [isTurningOffAll, setIsTurningOffAll] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);

  /**
   * Fetches the current state of all lights from the backend.
   * This syncs the UI with the actual device states.
   */
  const refreshLightStates = useCallback(async () => {
    setIsRefreshing(true);
    
    try {
      const results = await Promise.allSettled(
        DEVICES.map(async (device) => {
          const response = await lightingService.getStatus(device.deviceName);
          return { deviceName: device.deviceName, ...response };
        })
      );
      
      setLightStates(prevStates => {
        const newStates: Record<string, LightState> = {};
        
        results.forEach((result, index) => {
          const deviceName = DEVICES[index].deviceName;
          
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
  }, []); // No dependencies - uses functional setState to access current state

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
        DEVICES.filter(device => lightStates[device.deviceName]?.state)
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
        DEVICES.forEach((device, index) => {
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
  }, [lightStates]);

  /**
   * Handles state changes from individual LightCard components.
   */
  const handleLightStateChange = useCallback((deviceName: string, newState: LightState) => {
    setLightStates(prev => ({
      ...prev,
      [deviceName]: newState,
    }));
  }, []);

  // Fetch light states automatically on page load
  useEffect(() => {
    refreshLightStates();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []); // Empty dependency array - only run on mount

  // Calculate summary stats
  const lightsOn = Object.values(lightStates).filter(s => s.state).length;
  const totalLights = DEVICES.length;

  return (
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
      <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
        {DEVICES.map((device) => (
          <LightCard
            key={device.deviceName}
            deviceName={device.deviceName}
            displayName={device.displayName}
            location={device.location}
            initialState={lightStates[device.deviceName]}
            onStateChange={handleLightStateChange}
          />
        ))}
      </div>
    </div>
  );
}
