"use client";

import { useState, useEffect, useCallback, useRef } from "react";
import { Lightbulb, Power, Circle } from "lucide-react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Slider } from "@/components/ui/slider";
import { Label } from "@/components/ui/label";
import { lightingService, type LightCommandRequest } from "@/services/api";
import { toast } from "sonner";
import { useDebounce } from "@/hooks/use-debounce";

interface LightState {
  state: boolean;
  brightness: number; // 0-100
  color: string; // "#RRGGBB"
}

export default function IluminacaoPage() {
  const [lightStates, setLightStates] = useState<Record<string, LightState>>({
    lampada_1: { state: false, brightness: 100, color: "#FFFFFF" },
    lampada_2: { state: false, brightness: 100, color: "#FFFFFF" },
  });
  
  const [localBrightness, setLocalBrightness] = useState<Record<string, number>>({
    lampada_1: 100,
    lampada_2: 100,
  });
  
  const [localColor, setLocalColor] = useState<Record<string, string>>({
    lampada_1: "#FFFFFF",
    lampada_2: "#FFFFFF",
  });
  
  const [isLoadingLights, setIsLoadingLights] = useState<Record<string, boolean>>({
    lampada_1: false,
    lampada_2: false,
  });

  // Refs to track previous debounced values to avoid unnecessary API calls
  const prevDebouncedBrightness1 = useRef<number | null>(null);
  const prevDebouncedBrightness2 = useRef<number | null>(null);
  const prevDebouncedColor1 = useRef<string | null>(null);
  const prevDebouncedColor2 = useRef<string | null>(null);

  // Debounced values for brightness and color
  const debouncedBrightness1 = useDebounce(localBrightness.lampada_1, 300);
  const debouncedBrightness2 = useDebounce(localBrightness.lampada_2, 300);
  const debouncedColor1 = useDebounce(localColor.lampada_1, 300);
  const debouncedColor2 = useDebounce(localColor.lampada_2, 300);

  // Send command with optimistic update
  const sendCommand = useCallback(async (
    deviceName: string,
    command: LightCommandRequest,
    optimisticUpdate?: (prev: LightState) => LightState
  ) => {
    let previousState: LightState | null = null;
    
    // Optimistic update and capture previous state
    setLightStates((prev) => {
      previousState = prev[deviceName];
      
      if (optimisticUpdate) {
        return {
          ...prev,
          [deviceName]: optimisticUpdate(previousState),
        };
      }
      return prev;
    });
    
    setIsLoadingLights((prev) => ({ ...prev, [deviceName]: true }));
    
    try {
      console.log(`[LightControl] Sending command to ${deviceName}:`, command);
      const response = await lightingService.sendCommand(deviceName, command);
      console.log(`[LightControl] Command successful:`, response);
      // Success - state already updated optimistically
    } catch (error: any) {
      console.error(`[LightControl] Error caught for ${deviceName}:`, error);
      console.error(`[LightControl] Error type:`, error?.constructor?.name);
      console.error(`[LightControl] Error details:`, {
        message: error?.message,
        response: error?.response,
        request: error?.request,
        stack: error?.stack,
      });
      
      // Revert optimistic update on error
      if (previousState) {
        setLightStates((prev) => ({
          ...prev,
          [deviceName]: previousState!,
        }));
        
        // Revert local values if they were changed
        if (command.brightness !== undefined) {
          setLocalBrightness((prev) => ({
            ...prev,
            [deviceName]: previousState!.brightness,
          }));
        }
        if (command.color !== undefined) {
          setLocalColor((prev) => ({
            ...prev,
            [deviceName]: previousState!.color,
          }));
        }
      }
      
      // Extract error message with better error handling
      let errorMessage = "Não foi possível comunicar com o dispositivo";
      
      if (error?.response) {
        // Server responded with error status
        const responseData = error.response.data;
        errorMessage = responseData?.message || 
                      (typeof responseData === 'string' ? responseData : errorMessage);
        console.error(`[LightControl] Server error response:`, {
          status: error.response.status,
          statusText: error.response.statusText,
          data: responseData,
        });
      } else if (error?.request) {
        // Request was made but no response received
        errorMessage = "Servidor não está respondendo. Verifique se o backend está rodando na porta 8080.";
        console.error(`[LightControl] No response from server:`, error.request);
      } else if (error?.message) {
        // Error setting up the request
        errorMessage = error.message;
        console.error(`[LightControl] Request setup error:`, error.message);
      }
      
      toast.error("Erro ao controlar lâmpada", {
        description: errorMessage,
        duration: 5000,
      });
    } finally {
      setIsLoadingLights((prev) => ({ ...prev, [deviceName]: false }));
    }
  }, []);

  // Handle brightness changes with debounce for lampada_1
  useEffect(() => {
    if (prevDebouncedBrightness1.current === debouncedBrightness1) return;
    prevDebouncedBrightness1.current = debouncedBrightness1;
    
    setLightStates((prev) => {
      if (!prev.lampada_1.state || prev.lampada_1.brightness === debouncedBrightness1) {
        return prev;
      }
      return {
        ...prev,
        lampada_1: { ...prev.lampada_1, brightness: debouncedBrightness1 },
      };
    });
    
    // Send command separately - include current color to maintain colour mode
    // Use localColor which is the actual current value from the color picker
    setLightStates((prev) => {
      if (!prev.lampada_1.state) return prev;
      
      const command: LightCommandRequest = { brightness: debouncedBrightness1 };
      // Use localColor (from color picker) instead of lightStates to get the most current value
      // Always include color if it's not pure black - this maintains colour mode
      const currentColor = localColor.lampada_1;
      // Check if color is different from initial white - if user selected a color, preserve it
      const isUserSelectedColor = currentColor && currentColor !== '#FFFFFF' && currentColor !== '#ffffff';
      if (isUserSelectedColor) {
        command.color = currentColor;
        console.log(`[Brightness] Including user-selected color ${currentColor} to maintain colour mode`);
      } else {
        console.log(`[Brightness] Using default white, not including color (current: ${currentColor})`);
      }
      
      sendCommand("lampada_1", command, (p) => ({
        ...p,
        brightness: debouncedBrightness1,
      }));
      
      return prev;
    });
  }, [debouncedBrightness1, sendCommand, localColor.lampada_1]);

  // Handle brightness changes with debounce for lampada_2
  useEffect(() => {
    if (prevDebouncedBrightness2.current === debouncedBrightness2) return;
    prevDebouncedBrightness2.current = debouncedBrightness2;
    
    setLightStates((prev) => {
      if (!prev.lampada_2.state || prev.lampada_2.brightness === debouncedBrightness2) {
        return prev;
      }
      return {
        ...prev,
        lampada_2: { ...prev.lampada_2, brightness: debouncedBrightness2 },
      };
    });
    
    // Send command separately - include current color to maintain colour mode
    // Use localColor which is the actual current value from the color picker
    setLightStates((prev) => {
      if (!prev.lampada_2.state) return prev;
      
      const command: LightCommandRequest = { brightness: debouncedBrightness2 };
      // Use localColor (from color picker) instead of lightStates to get the most current value
      // Always include color if it's not pure black - this maintains colour mode
      const currentColor = localColor.lampada_2;
      // Check if color is different from initial white - if user selected a color, preserve it
      const isUserSelectedColor = currentColor && currentColor !== '#FFFFFF' && currentColor !== '#ffffff';
      if (isUserSelectedColor) {
        command.color = currentColor;
        console.log(`[Brightness] Including user-selected color ${currentColor} to maintain colour mode`);
      } else {
        console.log(`[Brightness] Using default white, not including color (current: ${currentColor})`);
      }
      
      sendCommand("lampada_2", command, (p) => ({
        ...p,
        brightness: debouncedBrightness2,
      }));
      
      return prev;
    });
  }, [debouncedBrightness2, sendCommand, localColor.lampada_2]);

  // Handle color changes with debounce for lampada_1
  useEffect(() => {
    if (prevDebouncedColor1.current === debouncedColor1) return;
    prevDebouncedColor1.current = debouncedColor1;
    
    setLightStates((prev) => {
      if (!prev.lampada_1.state || prev.lampada_1.color === debouncedColor1) {
        return prev;
      }
      return {
        ...prev,
        lampada_1: { ...prev.lampada_1, color: debouncedColor1 },
      };
    });
    
    // Send command separately
    if (lightStates.lampada_1.state) {
      sendCommand("lampada_1", { color: debouncedColor1 }, (p) => ({
        ...p,
        color: debouncedColor1,
      }));
    }
  }, [debouncedColor1, sendCommand, lightStates.lampada_1.state]);

  // Handle color changes with debounce for lampada_2
  useEffect(() => {
    if (prevDebouncedColor2.current === debouncedColor2) return;
    prevDebouncedColor2.current = debouncedColor2;
    
    setLightStates((prev) => {
      if (!prev.lampada_2.state || prev.lampada_2.color === debouncedColor2) {
        return prev;
      }
      return {
        ...prev,
        lampada_2: { ...prev.lampada_2, color: debouncedColor2 },
      };
    });
    
    // Send command separately
    if (lightStates.lampada_2.state) {
      sendCommand("lampada_2", { color: debouncedColor2 }, (p) => ({
        ...p,
        color: debouncedColor2,
      }));
    }
  }, [debouncedColor2, sendCommand, lightStates.lampada_2.state]);

  const toggleLight = async (deviceName: string) => {
    console.log(`[ToggleLight] === INÍCIO === Toggling ${deviceName}`);
    console.log(`[ToggleLight] Current state:`, lightStates[deviceName]);
    
    const newState = !lightStates[deviceName].state;
    console.log(`[ToggleLight] New state will be:`, newState);
    
    // Sync local values when turning on
    if (newState) {
      setLocalBrightness((prev) => ({
        ...prev,
        [deviceName]: lightStates[deviceName].brightness,
      }));
      setLocalColor((prev) => ({
        ...prev,
        [deviceName]: lightStates[deviceName].color,
      }));
    }
    
    console.log(`[ToggleLight] Calling sendCommand with:`, { deviceName, state: newState });
    
    await sendCommand(
      deviceName,
      { state: newState },
      (prev) => {
        console.log(`[ToggleLight] Optimistic update, prev:`, prev);
        return { ...prev, state: newState };
      }
    );
    
    console.log(`[ToggleLight] Command completed successfully`);
    
    // Show success toast
    if (newState) {
      toast.success("Lâmpada ligada com sucesso!", {
        description: `Lâmpada ${deviceName} ligada`,
      });
    } else {
      toast.success("Lâmpada desligada com sucesso!", {
        description: `Lâmpada ${deviceName} desligada`,
      });
    }
  };

  const handleBrightnessChange = (deviceName: string, value: number[]) => {
    const newBrightness = value[0];
    setLocalBrightness((prev) => ({ ...prev, [deviceName]: newBrightness }));
    // Optimistic update - only if light is on
    setLightStates((prev) => {
      if (!prev[deviceName].state) return prev;
      return {
        ...prev,
        [deviceName]: { ...prev[deviceName], brightness: newBrightness },
      };
    });
  };

  const handleColorChange = (deviceName: string, color: string) => {
    setLocalColor((prev) => ({ ...prev, [deviceName]: color }));
    // Optimistic update - only if light is on
    setLightStates((prev) => {
      if (!prev[deviceName].state) return prev;
      return {
        ...prev,
        [deviceName]: { ...prev[deviceName], color },
      };
    });
  };

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Iluminação</h1>
        <p className="text-muted-foreground mt-1">
          Controle das lâmpadas inteligentes
        </p>
      </div>

      <div className="grid gap-6 md:grid-cols-2">
        {/* Lighting Control - Lampada 1 */}
        <Card>
          <CardHeader>
            <div className="flex items-center gap-2">
              <Lightbulb className="h-5 w-5" />
              <CardTitle>Lâmpada 1</CardTitle>
            </div>
            <CardDescription>Sala de estar</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="text-center">
              <div className="flex justify-center">
                <Circle 
                  className={`h-12 w-12 transition-colors ${
                    lightStates.lampada_1.state 
                      ? "fill-current text-current" 
                      : "text-muted-foreground"
                  }`}
                  style={{
                    color: lightStates.lampada_1.state 
                      ? lightStates.lampada_1.color 
                      : undefined,
                  }}
                />
              </div>
              <div className="text-sm text-muted-foreground mt-2">
                {lightStates.lampada_1.state ? "Ligada" : "Desligada"}
              </div>
            </div>

            <Button
              onClick={(e) => {
                e.preventDefault();
                e.stopPropagation();
                console.log('[Button] Clicked for lampada_1, current state:', lightStates.lampada_1.state);
                toggleLight("lampada_1").catch((err) => {
                  console.error('[Button] Error in toggleLight:', err);
                });
              }}
              disabled={isLoadingLights.lampada_1}
              className="w-full"
              size="lg"
              variant={lightStates.lampada_1.state ? "secondary" : "default"}
              type="button"
            >
              <Power className="mr-2 h-4 w-4" />
              {isLoadingLights.lampada_1 ? "Processando..." : (lightStates.lampada_1.state ? "Desligar" : "Ligar")}
            </Button>

            {lightStates.lampada_1.state && (
              <>
                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <Label htmlFor="brightness-1">Brilho</Label>
                    <span className="text-sm text-muted-foreground">
                      {lightStates.lampada_1.brightness}%
                    </span>
                  </div>
                  <Slider
                    id="brightness-1"
                    min={0}
                    max={100}
                    step={1}
                    value={[lightStates.lampada_1.brightness]}
                    onValueChange={(value) => handleBrightnessChange("lampada_1", value)}
                    disabled={isLoadingLights.lampada_1}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="color-1">Cor</Label>
                  <div className="flex items-center gap-3">
                    <input
                      id="color-1"
                      type="color"
                      value={lightStates.lampada_1.color}
                      onChange={(e) => handleColorChange("lampada_1", e.target.value)}
                      disabled={isLoadingLights.lampada_1}
                      className="h-10 w-20 cursor-pointer rounded-md border border-input bg-background disabled:cursor-not-allowed disabled:opacity-50"
                    />
                    <span className="text-sm text-muted-foreground font-mono">
                      {lightStates.lampada_1.color.toUpperCase()}
                    </span>
                  </div>
                </div>
              </>
            )}
          </CardContent>
        </Card>

        {/* Lighting Control - Lampada 2 */}
        <Card>
          <CardHeader>
            <div className="flex items-center gap-2">
              <Lightbulb className="h-5 w-5" />
              <CardTitle>Lâmpada 2</CardTitle>
            </div>
            <CardDescription>Quarto</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="text-center">
              <div className="flex justify-center">
                <Circle 
                  className={`h-12 w-12 transition-colors ${
                    lightStates.lampada_2.state 
                      ? "fill-current text-current" 
                      : "text-muted-foreground"
                  }`}
                  style={{
                    color: lightStates.lampada_2.state 
                      ? lightStates.lampada_2.color 
                      : undefined,
                  }}
                />
              </div>
              <div className="text-sm text-muted-foreground mt-2">
                {lightStates.lampada_2.state ? "Ligada" : "Desligada"}
              </div>
            </div>

            <Button
              onClick={(e) => {
                e.preventDefault();
                e.stopPropagation();
                console.log('[Button] Clicked for lampada_2, current state:', lightStates.lampada_2.state);
                toggleLight("lampada_2").catch((err) => {
                  console.error('[Button] Error in toggleLight:', err);
                });
              }}
              disabled={isLoadingLights.lampada_2}
              className="w-full"
              size="lg"
              variant={lightStates.lampada_2.state ? "secondary" : "default"}
              type="button"
            >
              <Power className="mr-2 h-4 w-4" />
              {isLoadingLights.lampada_2 ? "Processando..." : (lightStates.lampada_2.state ? "Desligar" : "Ligar")}
            </Button>

            {lightStates.lampada_2.state && (
              <>
                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <Label htmlFor="brightness-2">Brilho</Label>
                    <span className="text-sm text-muted-foreground">
                      {lightStates.lampada_2.brightness}%
                    </span>
                  </div>
                  <Slider
                    id="brightness-2"
                    min={0}
                    max={100}
                    step={1}
                    value={[lightStates.lampada_2.brightness]}
                    onValueChange={(value) => handleBrightnessChange("lampada_2", value)}
                    disabled={isLoadingLights.lampada_2}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="color-2">Cor</Label>
                  <div className="flex items-center gap-3">
                    <input
                      id="color-2"
                      type="color"
                      value={lightStates.lampada_2.color}
                      onChange={(e) => handleColorChange("lampada_2", e.target.value)}
                      disabled={isLoadingLights.lampada_2}
                      className="h-10 w-20 cursor-pointer rounded-md border border-input bg-background disabled:cursor-not-allowed disabled:opacity-50"
                    />
                    <span className="text-sm text-muted-foreground font-mono">
                      {lightStates.lampada_2.color.toUpperCase()}
                    </span>
                  </div>
                </div>
              </>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
