"use client";

import { memo, useCallback, useEffect, useRef, useState } from "react";
import { Lightbulb, Power, Circle, Loader2 } from "lucide-react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Slider } from "@/components/ui/slider";
import { Label } from "@/components/ui/label";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { lightingService, type LightCommandRequest } from "@/services/api";
import { toast } from "sonner";
import { useDebounce } from "@/hooks/use-debounce";
import { cn } from "@/lib/utils";
import { adjustBrightness, extractBrightness } from "@/lib/color-utils";

// Color presets for quick selection
const COLOR_PRESETS = [
  { name: "Branco Quente", color: "#FFE4B5", icon: "☀️" },
  { name: "Branco Frio", color: "#F0F8FF", icon: "❄️" },
  { name: "Relaxar", color: "#FFB347", icon: "🧘" },
  { name: "Concentrar", color: "#87CEEB", icon: "💡" },
  { name: "Vermelho", color: "#FF4444", icon: "🔴" },
  { name: "Verde", color: "#44FF44", icon: "🟢" },
  { name: "Azul", color: "#4444FF", icon: "🔵" },
  { name: "Roxo", color: "#9944FF", icon: "🟣" },
] as const;

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
}

/**
 * Reusable Light Card component for controlling smart bulbs.
 * 
 * Features:
 * - On/Off toggle with optimistic updates
 * - Brightness slider with debounce (300ms)
 * - Color picker with debounce (150ms for better UX)
 * - Color presets for quick selection
 * - Visual loading states
 * - Error handling with toast notifications
 */
function LightCardComponent({
  deviceName,
  displayName,
  location,
  initialState = { state: false, brightness: 100, color: "#FFFFFF" },
  onStateChange,
}: LightCardProps) {
  // Main state
  const [lightState, setLightState] = useState<LightState>(initialState);
  
  // Local values for smooth UI (before debounce)
  const [localBrightness, setLocalBrightness] = useState(initialState.brightness);
  const [localColor, setLocalColor] = useState(initialState.color);
  
  // Loading states
  const [isToggling, setIsToggling] = useState(false);
  const [isSendingBrightness, setIsSendingBrightness] = useState(false);
  const [isSendingColor, setIsSendingColor] = useState(false);
  
  // Refs to track previous debounced values and external state changes
  const prevDebouncedBrightness = useRef<number | null>(null);
  const prevDebouncedColor = useRef<string | null>(null);
  const prevInitialState = useRef<LightState>(initialState);
  const isInitialMount = useRef(true);
  
  // Debounced values - color has shorter debounce for better UX
  const debouncedBrightness = useDebounce(localBrightness, 300);
  const debouncedColor = useDebounce(localColor, 150);

  // Sync with external state changes (e.g., "Turn Off All" button)
  // Only syncs when the power state changes externally
  useEffect(() => {
    const prev = prevInitialState.current;
    
    // Detect if power state changed externally
    if (prev.state !== initialState.state) {
      setLightState(initialState);
      setLocalBrightness(initialState.brightness);
      setLocalColor(initialState.color);
    }
    
    prevInitialState.current = initialState;
  }, [initialState]);

  /**
   * Sends a command to the light with error handling.
   * Returns true if successful, false otherwise.
   */
  const sendCommand = useCallback(async (
    command: LightCommandRequest,
    setLoading: (loading: boolean) => void,
  ): Promise<boolean> => {
    setLoading(true);
    
    try {
      await lightingService.sendCommand(deviceName, command);
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
          errorMessage = "Servidor não está respondendo. Verifique se o backend está rodando.";
        } else if (axiosError.message) {
          errorMessage = axiosError.message;
        }
      }
      
      toast.error("Erro ao controlar lâmpada", {
        description: errorMessage,
        duration: 5000,
      });
      
      return false;
    } finally {
      setLoading(false);
    }
  }, [deviceName]);

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
    
    // Sync local values when turning on
    if (newState) {
      setLocalBrightness(lightState.brightness);
      setLocalColor(lightState.color);
    }
    
    const success = await sendCommand({ state: newState }, setIsToggling);
    
    if (!success) {
      // Revert on error
      setLightState(previousState);
      onStateChange?.(deviceName, previousState);
    }
    // No success toast - reduces visual noise
  }, [lightState, sendCommand, deviceName, onStateChange]);

  /**
   * Handles brightness slider changes.
   */
  const handleBrightnessChange = useCallback((value: number[]) => {
    const newBrightness = value[0];
    setLocalBrightness(newBrightness);
    
    // Calculate adjusted hex color with new brightness (maintains hue and saturation)
    const adjustedColor = adjustBrightness(lightState.color, newBrightness);
    setLocalColor(adjustedColor);
    
    // Optimistic update only if light is on
    if (lightState.state) {
      const updatedState = { ...lightState, brightness: newBrightness, color: adjustedColor };
      setLightState(updatedState);
      onStateChange?.(deviceName, updatedState);
    }
  }, [lightState, deviceName, onStateChange]);

  /**
   * Handles color picker changes.
   */
  const handleColorChange = useCallback((color: string) => {
    setLocalColor(color);
    
    // Extract brightness from the new color and sync it
    const extractedBrightness = extractBrightness(color);
    setLocalBrightness(extractedBrightness);
    
    // Optimistic update only if light is on
    if (lightState.state) {
      const updatedState = { ...lightState, color, brightness: extractedBrightness };
      setLightState(updatedState);
      onStateChange?.(deviceName, updatedState);
    }
  }, [lightState, deviceName, onStateChange]);

  /**
   * Applies a color preset.
   */
  const applyPreset = useCallback((presetColor: string) => {
    handleColorChange(presetColor);
  }, [handleColorChange]);

  // Send brightness command after debounce
  useEffect(() => {
    // Skip initial mount
    if (isInitialMount.current) {
      isInitialMount.current = false;
      prevDebouncedBrightness.current = debouncedBrightness;
      prevDebouncedColor.current = debouncedColor;
      return;
    }
    
    // Skip if value hasn't changed
    if (prevDebouncedBrightness.current === debouncedBrightness) return;
    prevDebouncedBrightness.current = debouncedBrightness;
    
    // Skip if light is off
    if (!lightState.state) return;
    
    // Calculate adjusted hex color with debounced brightness
    // Use the current color from state to maintain hue and saturation
    const adjustedColor = adjustBrightness(lightState.color, debouncedBrightness);
    
    // Send ONLY color with adjusted hex (no brightness field)
    sendCommand({ 
      color: adjustedColor 
    }, setIsSendingBrightness);
  }, [debouncedBrightness, lightState.state, lightState.color, sendCommand]);

  // Send color command after debounce
  useEffect(() => {
    // Skip if value hasn't changed
    if (prevDebouncedColor.current === debouncedColor) return;
    prevDebouncedColor.current = debouncedColor;
    
    // Skip if light is off
    if (!lightState.state) return;
    
    sendCommand({ color: debouncedColor }, setIsSendingColor);
  }, [debouncedColor, lightState.state, sendCommand]);

  const isLoading = isToggling || isSendingBrightness || isSendingColor;
  const showControls = lightState.state;

  return (
    <Card className={cn(
      "transition-all duration-300",
      lightState.state && "ring-2 ring-primary/20"
    )}>
      <CardHeader>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Lightbulb className={cn(
              "h-5 w-5 transition-colors",
              lightState.state ? "text-yellow-500" : "text-muted-foreground"
            )} />
            <CardTitle>{displayName}</CardTitle>
          </div>
          {isLoading && (
            <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
          )}
        </div>
        <CardDescription>{location}</CardDescription>
      </CardHeader>
      
      <CardContent className="space-y-4">
        {/* Light indicator */}
        <div className="text-center">
          <div className="flex justify-center">
            <Circle 
              className={cn(
                "h-12 w-12 transition-all duration-300",
                lightState.state 
                  ? "fill-current" 
                  : "text-muted-foreground"
              )}
              style={{
                color: lightState.state ? lightState.color : undefined,
                filter: lightState.state 
                  ? `drop-shadow(0 0 ${lightState.brightness / 10}px ${lightState.color})`
                  : undefined,
              }}
            />
          </div>
          <div className="text-sm text-muted-foreground mt-2">
            {lightState.state ? "Ligada" : "Desligada"}
            {lightState.state && ` • ${lightState.brightness}%`}
          </div>
        </div>

        {/* Power button */}
        <Button
          onClick={toggleLight}
          disabled={isToggling}
          className="w-full"
          size="lg"
          variant={lightState.state ? "secondary" : "default"}
          type="button"
        >
          {isToggling ? (
            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
          ) : (
            <Power className="mr-2 h-4 w-4" />
          )}
          {isToggling ? "Processando..." : (lightState.state ? "Desligar" : "Ligar")}
        </Button>

        {/* Brightness control - always visible, disabled when off */}
        <div className={cn("space-y-2 transition-opacity", !showControls && "opacity-50")}>
          <div className="flex items-center justify-between">
            <Label htmlFor={`brightness-${deviceName}`} className="flex items-center gap-2">
              Brilho
              {isSendingBrightness && (
                <Loader2 className="h-3 w-3 animate-spin text-muted-foreground" />
              )}
            </Label>
            <span className="text-sm text-muted-foreground">
              {lightState.brightness}%
            </span>
          </div>
          <Slider
            id={`brightness-${deviceName}`}
            min={1}
            max={100}
            step={1}
            value={[lightState.brightness]}
            onValueChange={handleBrightnessChange}
            disabled={!showControls || isToggling}
          />
        </div>

        {/* Color control - always visible, disabled when off */}
        <div className={cn("space-y-3 transition-opacity", !showControls && "opacity-50")}>
          <Label className="flex items-center gap-2">
            Cor
            {isSendingColor && (
              <Loader2 className="h-3 w-3 animate-spin text-muted-foreground" />
            )}
          </Label>
          
          {/* Color presets */}
          <div className="flex flex-wrap gap-2">
            <TooltipProvider delayDuration={200}>
              {COLOR_PRESETS.map((preset) => (
                <Tooltip key={preset.color}>
                  <TooltipTrigger asChild>
                    <button
                      type="button"
                      onClick={() => applyPreset(preset.color)}
                      disabled={!showControls || isToggling}
                      className={cn(
                        "h-8 w-8 rounded-full border-2 transition-all hover:scale-110 focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2",
                        lightState.color.toLowerCase() === preset.color.toLowerCase()
                          ? "border-primary ring-2 ring-primary/50"
                          : "border-border hover:border-primary/50",
                        (!showControls || isToggling) && "cursor-not-allowed opacity-50"
                      )}
                      style={{ backgroundColor: preset.color }}
                      aria-label={preset.name}
                    />
                  </TooltipTrigger>
                  <TooltipContent>
                    <p>{preset.icon} {preset.name}</p>
                  </TooltipContent>
                </Tooltip>
              ))}
            </TooltipProvider>
          </div>
          
          {/* Custom color picker */}
          <div className="flex items-center gap-3">
            <input
              id={`color-${deviceName}`}
              type="color"
              value={lightState.color}
              onChange={(e) => handleColorChange(e.target.value)}
              disabled={!showControls || isToggling}
              className={cn(
                "h-10 w-20 cursor-pointer rounded-md border border-input bg-background transition-all",
                "hover:border-primary focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2",
                (!showControls || isToggling) && "cursor-not-allowed opacity-50"
              )}
            />
            <span className="text-sm text-muted-foreground font-mono">
              {lightState.color.toUpperCase()}
            </span>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

// Memoize to prevent unnecessary re-renders
export const LightCard = memo(LightCardComponent);
