"use client";

import { useEffect, useState } from "react";
import { Minus, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { acService } from "@/services/api";
import { toast } from "sonner";
import {
  describeAcCommand,
  readLastAcCommand,
  writeLastAcCommand,
  type LastAcCommand,
} from "@/lib/ac-command";

const TEMP_MIN = 17;
const TEMP_MAX = 30;
const DRAFT_START = 22;

export default function ArCondicionadoPage() {
  const [draftTemp, setDraftTemp] = useState(DRAFT_START);
  const [draftReady, setDraftReady] = useState(false);
  const [lastCommand, setLastCommand] = useState<LastAcCommand | null>(null);
  const [isSending, setIsSending] = useState(false);
  const [isConnected, setIsConnected] = useState<boolean | null>(null);

  useEffect(() => {
    const stored = readLastAcCommand();
    if (stored) {
      setLastCommand(stored);
      setDraftTemp(stored.temp);
    }
    setDraftReady(true);
  }, []);

  useEffect(() => {
    let cancelled = false;

    const checkConnectionStatus = async () => {
      const status = await acService.getStatus();
      if (!cancelled) setIsConnected(status.connected);
    };

    void checkConnectionStatus();
    const interval = setInterval(() => void checkConnectionStatus(), 30000);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, []);

  const changeTemp = (delta: number) => {
    setDraftTemp((prev) => Math.max(TEMP_MIN, Math.min(TEMP_MAX, prev + delta)));
  };

  const sendAcCommand = async (powerState: "on" | "off") => {
    setIsSending(true);
    try {
      await acService.sendCommand({ power: powerState, temp: draftTemp });
      const accepted = writeLastAcCommand(powerState, draftTemp);
      setLastCommand(accepted);
      toast.success(`O ar aceitou: ${describeAcCommand(powerState, draftTemp)}.`);
      window.setTimeout(() => {
        void acService.getStatus().then((status) => setIsConnected(status.connected));
      }, 1000);
    } catch {
      toast.error("O comando não chegou. Tenta outra vez.");
    } finally {
      setIsSending(false);
    }
  };

  const sendDisabled = isConnected !== true || isSending || !draftReady;
  const stepDisabled = !draftReady || isSending;

  return (
    <div className="flex flex-col gap-8">
      <div>
        <h1 className="text-3xl font-bold leading-tight">Ar</h1>
        <p className="mt-2 max-w-[36ch] text-muted-foreground">
          O número é o que vai ser enviado. O ar não informa a temperatura.
        </p>
      </div>

      {isConnected === null && <p className="text-muted-foreground">A ver se o ar está na rede.</p>}
      {isConnected === false && (
        <p className="max-w-[38ch]">
          O ar não está na rede. As luzes continuam a funcionar.
        </p>
      )}

      <div className="flex flex-col gap-6 border-t border-border pt-8">
        <div className="flex items-center justify-between gap-4">
          <button
            type="button"
            onClick={() => changeTemp(-1)}
            disabled={draftTemp <= TEMP_MIN || stepDisabled}
            aria-label="Baixar um grau"
            className="flex h-14 w-14 items-center justify-center rounded-full border border-border focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-40"
          >
            <Minus className="h-5 w-5" />
          </button>
          <p
            aria-live="polite"
            aria-label={draftReady ? `Próximo comando, ${draftTemp} graus` : "A preparar o comando"}
            className="text-filament text-[clamp(4.75rem,22vw,7.25rem)] font-bold leading-none tabular-nums"
          >
            {draftReady ? draftTemp : "–"}
            <span className="align-top text-[0.45em]">°</span>
          </p>
          <button
            type="button"
            onClick={() => changeTemp(1)}
            disabled={draftTemp >= TEMP_MAX || stepDisabled}
            aria-label="Subir um grau"
            className="flex h-14 w-14 items-center justify-center rounded-full border border-border focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-40"
          >
            <Plus className="h-5 w-5" />
          </button>
        </div>

        <p className="max-w-[36ch]" aria-live="polite">
          {lastCommand
            ? `Último comando aceito: ${describeAcCommand(lastCommand.power, lastCommand.temp)}.`
            : "Ainda não há comando aceito."}
        </p>

        <div className="flex flex-col gap-3">
          <Button
            onClick={() => void sendAcCommand("on")}
            disabled={sendDisabled}
            className="h-14 w-full text-base"
            size="lg"
          >
            Ligar a {draftReady ? draftTemp : "–"}°
          </Button>
          <Button
            variant="outline"
            onClick={() => void sendAcCommand("off")}
            disabled={sendDisabled}
            className="h-14 w-full text-base"
            size="lg"
          >
            Desligar
          </Button>
        </div>
      </div>
    </div>
  );
}
