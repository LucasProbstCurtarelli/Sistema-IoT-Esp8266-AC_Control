"use client";

import { memo, useCallback, useEffect, useRef, useState } from "react";
import { Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Slider } from "@/components/ui/slider";
import { Label } from "@/components/ui/label";
import { lightingService, type LightCommandRequest } from "@/services/api";
import { toast } from "sonner";
import { cn } from "@/lib/utils";
import { adjustBrightness, extractBrightness } from "@/lib/color-utils";

export interface LightState {
  state: boolean;
  brightness: number; // 0-100
  color: string; // "#RRGGBB"
}

interface LightCardProps {
  deviceName: string;
  displayName: string;
  location: string;
  initialState?: LightState;
  onStateChange?: (deviceName: string, state: LightState) => void;
  linkedDevices?: string[];
  availableDevices?: Array<{ deviceName: string; displayName: string }>;
  onLinkToggle?: (deviceName1: string, deviceName2: string) => void;
  onLinkedDeviceStateChange?: (deviceName: string, state: LightState) => void;
}

/**
 * Reusable Light Card component for controlling smart bulbs.
 * 
 * Features:
 * - On/Off toggle with optimistic updates
 * - Brightness slider - commands sent on release (onValueCommit)
 * - Color picker - commands sent on blur/release
 * - Light linking - sync commands across linked lights
 * - Visual loading states
 * - Error handling with toast notifications
 */
function LightCardComponent({
  deviceName,
  displayName,
  location,
  initialState = { state: false, brightness: 100, color: "#FFFFFF" },
  onStateChange,
  linkedDevices = [],
  availableDevices = [],
  onLinkToggle,
  onLinkedDeviceStateChange,
}: LightCardProps) {
  // Main state
  const [lightState, setLightState] = useState<LightState>(initialState);
  
  // Loading states
  const [isToggling, setIsToggling] = useState(false);
  const [isSendingBrightness, setIsSendingBrightness] = useState(false);
  const [isSendingColor, setIsSendingColor] = useState(false);
  
  // Refs to track external state changes
  const prevInitialState = useRef<LightState>(initialState);
  const userInitiatedChange = useRef(false);
  const colorCommitInProgress = useRef(false);
  const colorCommitTimeoutRef = useRef<NodeJS.Timeout | null>(null);

  // Sync with external state changes (e.g., from refreshLightStates)
  // This should NOT trigger command sends unless user initiated the change
  useEffect(() => {
    const prev = prevInitialState.current;
    
    // Check if any state changed externally
    const stateChanged = 
      prev.state !== initialState.state ||
      prev.brightness !== initialState.brightness ||
      prev.color !== initialState.color;
    
    if (stateChanged) {
      // Only sync if this is NOT a user-initiated change
      // If user is changing something, don't override their changes
      if (!userInitiatedChange.current) {
        setLightState(initialState);
      }
      // If user initiated change, don't sync - let user's change complete first
      // The flag will be reset after command is sent
    }
    
    prevInitialState.current = initialState;
  }, [initialState]);

  // Cleanup timeout on unmount
  useEffect(() => {
    return () => {
      if (colorCommitTimeoutRef.current) {
        clearTimeout(colorCommitTimeoutRef.current);
      }
    };
  }, []);

  /**
   * Sends a command to the light with error handling.
   * Also propagates the command to linked lights if any.
   * Returns true if successful, false otherwise.
   */
  const sendCommand = useCallback(async (
    command: LightCommandRequest,
    setLoading: (loading: boolean) => void,
    propagateToLinked = true,
  ): Promise<boolean> => {
    setLoading(true);
    
    try {
      // Send to this device
      await lightingService.sendCommand(deviceName, command);
      
      // Propagate to linked devices if enabled and there are linked devices
      if (propagateToLinked && linkedDevices.length > 0) {
        // Calculate the new state values from the command
        const stateUpdate: Partial<LightState> = {};
        if (command.state !== undefined) {
          stateUpdate.state = command.state;
        }
        if (command.brightness !== undefined) {
          stateUpdate.brightness = command.brightness;
        }
        if (command.color !== undefined) {
          stateUpdate.color = command.color;
          if (command.brightness === undefined) {
            // Extract brightness from color if not explicitly provided
            stateUpdate.brightness = extractBrightness(command.color);
          }
        }
        
        // Update parent state for linked devices optimistically BEFORE sending commands
        if (onLinkedDeviceStateChange && Object.keys(stateUpdate).length > 0) {
          linkedDevices.forEach(linkedDeviceName => {
            onLinkedDeviceStateChange(linkedDeviceName, stateUpdate as LightState);
          });
        }
        
        // Send to all linked devices in parallel (don't wait for all to complete)
        Promise.allSettled(
          linkedDevices.map(linkedDeviceName =>
            lightingService.sendCommand(linkedDeviceName, command)
          )
        ).then(results => {
          const failures = results.filter(r => r.status === 'rejected');
          if (failures.length > 0) {
            console.warn(`[LightCard] Some linked devices failed to update:`, failures);
          }
        });
      }
      
      return true;
    } catch (error: unknown) {
      // Extract error message
      let errorMessage = "Não foi possível comunicar com o dispositivo";
      
      if (error && typeof error === 'object') {
        const axiosError = error as { 
          response?: { data?: { message?: string }, status?: number };
          request?: unknown;
          message?: string;
        };
        
        if (axiosError.response?.data?.message) {
          errorMessage = axiosError.response.data.message;
        } else if (axiosError.request) {
          errorMessage = "A casa não respondeu. Tenta outra vez.";
        } else if (axiosError.message) {
          errorMessage = axiosError.message;
        }
      }
      
      toast.error("Não deu para mudar a lâmpada.", {
        description: errorMessage,
        duration: 5000,
      });
      
      return false;
    } finally {
      setLoading(false);
    }
  }, [deviceName, linkedDevices, onLinkedDeviceStateChange, lightState]);

  /**
   * Toggles the light on/off.
   */
  const toggleLight = useCallback(async () => {
    const newState = !lightState.state;
    const previousState = { ...lightState };
    
    // Optimistic update
    const updatedState = { ...lightState, state: newState };
    setLightState(updatedState);
    
    // Notify parent of state change
    onStateChange?.(deviceName, updatedState);
    
    const success = await sendCommand({ state: newState }, setIsToggling);
    
    if (!success) {
      // Revert on error
      setLightState(previousState);
      onStateChange?.(deviceName, previousState);
    }
    // No success toast - reduces visual noise
  }, [lightState, sendCommand, deviceName, onStateChange]);

  /**
   * Handles brightness slider changes (while dragging - optimistic update only).
   * Also updates linked devices in real-time.
   */
  const handleBrightnessChange = useCallback((value: number[]) => {
    const newBrightness = value[0];
    
    // Calculate adjusted hex color with new brightness (maintains hue and saturation)
    const adjustedColor = adjustBrightness(lightState.color, newBrightness);
    
    // Optimistic update only if light is on
    if (lightState.state) {
      const updatedState = { ...lightState, brightness: newBrightness, color: adjustedColor };
      setLightState(updatedState);
      onStateChange?.(deviceName, updatedState);
      
      // Update linked devices in real-time (UI only, no commands sent yet)
      if (linkedDevices.length > 0 && onLinkedDeviceStateChange) {
        const stateUpdate: LightState = {
          state: lightState.state,
          brightness: newBrightness,
          color: adjustedColor,
        };
        linkedDevices.forEach(linkedDeviceName => {
          onLinkedDeviceStateChange(linkedDeviceName, stateUpdate);
        });
      }
    }
  }, [lightState, deviceName, onStateChange, linkedDevices, onLinkedDeviceStateChange]);

  /**
   * Handles brightness slider commit (when user releases - send command).
   */
  const handleBrightnessCommit = useCallback(async (value: number[]) => {
    const newBrightness = value[0];
    
    // Skip if light is off
    if (!lightState.state) return;
    
    // Mark as user-initiated change
    userInitiatedChange.current = true;
    
    // Calculate adjusted hex color with new brightness
    const adjustedColor = adjustBrightness(lightState.color, newBrightness);
    
    // Update state optimistically
    const updatedState = { ...lightState, brightness: newBrightness, color: adjustedColor };
    setLightState(updatedState);
    onStateChange?.(deviceName, updatedState);
    
    // Send command
    await sendCommand({ 
      color: adjustedColor 
    }, setIsSendingBrightness);
    
    // Reset flag after command completes
    setTimeout(() => {
      userInitiatedChange.current = false;
    }, 100);
  }, [lightState, deviceName, onStateChange, sendCommand]);

  /**
   * Handles color picker changes (while selecting - optimistic update only).
   */
  const handleColorChange = useCallback((color: string) => {
    // Extract brightness from the new color and sync it
    const extractedBrightness = extractBrightness(color);
    
    // Optimistic update only if light is on
    if (lightState.state) {
      const updatedState = { ...lightState, color, brightness: extractedBrightness };
      setLightState(updatedState);
      onStateChange?.(deviceName, updatedState);
    }
  }, [lightState, deviceName, onStateChange]);

  /**
   * Handles color picker commit (when user releases/blurs - send command).
   */
  const handleColorCommit = useCallback(async (color: string) => {
    // Skip if light is off
    if (!lightState.state) return;
    
    // Prevent duplicate sends if already processing
    if (colorCommitInProgress.current) return;
    
    // Mark as user-initiated change and set commit flag
    userInitiatedChange.current = true;
    colorCommitInProgress.current = true;
    
    // Extract brightness from the new color
    const extractedBrightness = extractBrightness(color);
    
    // Update state optimistically
    const updatedState = { ...lightState, color, brightness: extractedBrightness };
    setLightState(updatedState);
    onStateChange?.(deviceName, updatedState);
    
    try {
      // Send command
      await sendCommand({ color }, setIsSendingColor);
    } finally {
      // Reset flags after command completes
      setTimeout(() => {
        userInitiatedChange.current = false;
        colorCommitInProgress.current = false;
      }, 100);
    }
  }, [lightState, deviceName, onStateChange, sendCommand]);

  const isLoading = isToggling || isSendingBrightness || isSendingColor;
  const showControls = lightState.state;

  return (
    <section className="flex flex-col gap-5 border-t border-border py-8 first:border-t-0 first:pt-0">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold leading-tight">{displayName}</h2>
          {location ? <p className="mt-1 text-muted-foreground">{location}</p> : null}
        </div>
        {isLoading && (
          <Loader2 className="mt-2 h-4 w-4 animate-spin text-muted-foreground" aria-hidden />
        )}
      </div>

      <div className="flex items-center gap-4">
        <span
          className="h-16 w-16 shrink-0 rounded-full border border-border"
          style={{
            background: lightState.state ? lightState.color : "transparent",
            boxShadow: lightState.state ? `0 0 22px ${lightState.color}` : undefined,
          }}
          aria-hidden
        />
        <p className="text-muted-foreground">
          {lightState.state ? `Acesa, brilho ${lightState.brightness}%` : "Apagada"}
        </p>
      </div>

      <Button
        onClick={toggleLight}
        disabled={isToggling}
        className="h-12 w-full text-base"
        size="lg"
        variant={lightState.state ? "secondary" : "default"}
        type="button"
      >
        {isToggling ? "A enviar" : (lightState.state ? "Apagar" : "Acender")}
      </Button>

      <div className={cn("space-y-3", !showControls && "opacity-50")}>
        <div className="flex items-center justify-between">
          <Label htmlFor={`brightness-${deviceName}`} className="flex items-center gap-2">
            Brilho
            {isSendingBrightness && (
              <Loader2 className="h-3 w-3 animate-spin text-muted-foreground" aria-hidden />
            )}
          </Label>
          <span className="text-sm text-muted-foreground">
            {lightState.brightness}%
          </span>
        </div>
        <Slider
          id={`brightness-${deviceName}`}
          min={0}
          max={100}
          step={1}
          value={[lightState.brightness]}
          onValueChange={handleBrightnessChange}
          onValueCommit={handleBrightnessCommit}
          disabled={!showControls || isToggling}
        />
      </div>

      <div className={cn("space-y-3", !showControls && "opacity-50")}>
        <Label htmlFor={`color-${deviceName}`} className="flex items-center gap-2">
          Cor
          {isSendingColor && (
            <Loader2 className="h-3 w-3 animate-spin text-muted-foreground" aria-hidden />
          )}
        </Label>
        <div className="flex items-center gap-3">
          <input
            id={`color-${deviceName}`}
            type="color"
            value={lightState.color}
            onChange={(e) => {
              handleColorChange(e.target.value);

              if (colorCommitTimeoutRef.current) {
                clearTimeout(colorCommitTimeoutRef.current);
              }

              colorCommitTimeoutRef.current = setTimeout(() => {
                if (!colorCommitInProgress.current) {
                  handleColorCommit(e.target.value);
                }
              }, 150);
            }}
            onMouseUp={(e) => {
              if (colorCommitTimeoutRef.current) {
                clearTimeout(colorCommitTimeoutRef.current);
                colorCommitTimeoutRef.current = null;
              }
              const target = e.target as HTMLInputElement;
              if (target.value && !colorCommitInProgress.current) {
                handleColorCommit(target.value);
              }
            }}
            onPointerUp={(e) => {
              if (colorCommitTimeoutRef.current) {
                clearTimeout(colorCommitTimeoutRef.current);
                colorCommitTimeoutRef.current = null;
              }
              const target = e.target as HTMLInputElement;
              if (target.value && !colorCommitInProgress.current) {
                handleColorCommit(target.value);
              }
            }}
            onTouchEnd={(e) => {
              if (colorCommitTimeoutRef.current) {
                clearTimeout(colorCommitTimeoutRef.current);
                colorCommitTimeoutRef.current = null;
              }
              const target = e.target as HTMLInputElement;
              if (target.value && !colorCommitInProgress.current) {
                handleColorCommit(target.value);
              }
            }}
            disabled={!showControls || isToggling}
            aria-label={`Cor de ${displayName}`}
            className={cn(
              "h-12 w-16 cursor-pointer rounded-md border border-input bg-background",
              "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
              (!showControls || isToggling) && "cursor-not-allowed opacity-50"
            )}
          />
          <span className="text-sm text-muted-foreground">
            {lightState.state ? "Cor desta lâmpada" : "A cor vale quando acender"}
          </span>
        </div>
      </div>

      {onLinkToggle && availableDevices.length > 0 && (
        <div className="flex flex-col gap-2 border-t border-border pt-4">
          {availableDevices.map((device) => {
            const isCurrentlyLinked = linkedDevices.includes(device.deviceName);
            return (
              <button
                key={device.deviceName}
                type="button"
                onClick={() => onLinkToggle(deviceName, device.deviceName)}
                className="min-h-11 w-fit text-left underline-offset-4 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                {isCurrentlyLinked
                  ? `Separar de ${device.displayName}`
                  : `Acender junto com ${device.displayName}`}
              </button>
            );
          })}
        </div>
      )}
    </section>
  );
}

// Memoize to prevent unnecessary re-renders
export const LightCard = memo(LightCardComponent);
