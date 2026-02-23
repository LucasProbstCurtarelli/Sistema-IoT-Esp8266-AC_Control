"use client";

import { useState, useEffect } from "react";
import { Snowflake, Power, Minus, Plus } from "lucide-react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { acService } from "@/services/api";
import { toast } from "sonner";

export default function ArCondicionadoPage() {
  const [currentTemp, setCurrentTemp] = useState(22);
  const [isLoadingAc, setIsLoadingAc] = useState(false);
  const [isConnected, setIsConnected] = useState<boolean | null>(null);

  const checkConnectionStatus = async () => {
    try {
      const status = await acService.getStatus();
      setIsConnected((prev) => {
        // Only update if status actually changed to prevent unnecessary re-renders
        if (prev !== status.connected) {
          return status.connected;
        }
        return prev;
      });
    } catch (error) {
      console.error("Error checking connection status:", error);
      setIsConnected(false);
    }
  };

  useEffect(() => {
    // Check connection status on mount
    checkConnectionStatus();
    
    // Check connection status every 30 seconds
    const interval = setInterval(checkConnectionStatus, 30000);
    
    return () => clearInterval(interval);
  }, []);

  const changeTemp = (delta: number) => {
    setCurrentTemp((prev) => {
      const newTemp = prev + delta;
      return Math.max(17, Math.min(30, newTemp));
    });
  };

  const sendAcCommand = async (powerState: "on" | "off") => {
    setIsLoadingAc(true);
    try {
      await acService.sendCommand({
        power: powerState,
        temp: currentTemp,
      });
      toast.success("Comando enviado com sucesso!", {
        description: `Ar condicionado ${powerState === "on" ? "ligado" : "desligado"} a ${currentTemp}°C`,
      });
      
      // Check connection status after sending command (with delay to allow ESP to process)
      setTimeout(() => {
        checkConnectionStatus();
      }, 1000);
    } catch (error) {
      toast.error("Erro ao enviar comando", {
        description: "Não foi possível comunicar com o dispositivo",
      });
    } finally {
      setIsLoadingAc(false);
    }
  };

  const isDisabled = isConnected === false || isLoadingAc;

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">Ar Condicionado</h1>
        <p className="text-muted-foreground mt-1">
          Controle de temperatura e energia
        </p>
      </div>

      {isConnected === null && (
        <Alert>
          <AlertDescription>Verificando conexão...</AlertDescription>
        </Alert>
      )}

      {isConnected === false && (
        <Alert variant="destructive">
          <AlertDescription>
            O dispositivo ESP8266 está desconectado. As interações estão bloqueadas até que a conexão seja restaurada.
          </AlertDescription>
        </Alert>
      )}

      <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
        <Card>
          <CardHeader>
            <div className="flex items-center gap-2">
              <Snowflake className="h-5 w-5" />
              <CardTitle>Ar Condicionado</CardTitle>
            </div>
            <CardDescription>Controle de temperatura e energia</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="text-center">
              <div className="text-4xl font-bold">{currentTemp}°C</div>
              <div className="text-sm text-muted-foreground">Temperatura</div>
            </div>

            <div className="flex items-center justify-center gap-2">
              <Button
                variant="outline"
                size="icon"
                onClick={() => changeTemp(-1)}
                disabled={currentTemp <= 17 || isDisabled}
              >
                <Minus className="h-4 w-4" />
              </Button>
              <Button
                variant="outline"
                size="icon"
                onClick={() => changeTemp(1)}
                disabled={currentTemp >= 30 || isDisabled}
              >
                <Plus className="h-4 w-4" />
              </Button>
            </div>

            <div className="flex flex-col gap-2">
              <Button
                onClick={() => sendAcCommand("on")}
                disabled={isDisabled}
                className="w-full"
                size="lg"
              >
                <Power className="mr-2 h-4 w-4" />
                Ligar
              </Button>
              <Button
                variant="destructive"
                onClick={() => sendAcCommand("off")}
                disabled={isDisabled}
                className="w-full"
                size="lg"
              >
                Desligar
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
