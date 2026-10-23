"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { acService, deviceService, lightingService, type DeviceResponse } from "@/services/api";
import { describeAcCommand, readLastAcCommand, type LastAcCommand } from "@/lib/ac-command";

interface LampView {
  deviceName: string;
  displayName: string;
  location: string;
  on: boolean | null;
  color: string;
}

export default function DashboardPage() {
  const [lamps, setLamps] = useState<LampView[]>([]);
  const [lampsReady, setLampsReady] = useState(false);
  const [lampsFailed, setLampsFailed] = useState(false);
  const [airOnNetwork, setAirOnNetwork] = useState<boolean | null>(null);
  const [lastCommand, setLastCommand] = useState<LastAcCommand | null>(null);

  useEffect(() => {
    setLastCommand(readLastAcCommand());

    let cancelled = false;

    async function load() {
      const [devicesResult, air] = await Promise.all([
        deviceService.getAllDevices().then(
          (devices) => devices,
          () => null
        ),
        acService.getStatus(),
      ]);
      if (cancelled) return;
      setAirOnNetwork(air.connected);

      if (!devicesResult) {
        setLampsFailed(true);
        setLampsReady(true);
        return;
      }

      try {
        const lampsOnly = devicesResult.filter((device) => device.deviceType === "light");
        setLamps(lampsOnly.map(toUnknownLamp));
        setLampsReady(true);

        const statuses = await Promise.allSettled(
          lampsOnly.map((device) => lightingService.getStatus(device.deviceName))
        );
        if (cancelled) return;
        setLamps(
          lampsOnly.map((device, index) => {
            const status = statuses[index];
            if (status.status !== "fulfilled") return toUnknownLamp(device);
            return {
              ...toUnknownLamp(device),
              on: status.value.state,
              color: status.value.color || "#FFFFFF",
            };
          })
        );
      } catch {
        if (!cancelled) {
          setLampsFailed(true);
          setLampsReady(true);
        }
      }
    }

    void load();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="flex flex-col gap-10">
      <div>
        <h1 className="text-3xl font-bold leading-tight">A casa agora</h1>
        <p className="mt-2 max-w-[36ch] text-muted-foreground">
          O que está aceso aparece aqui.
        </p>
      </div>

      <section className="flex flex-col gap-3">
        <h2 className="text-2xl font-bold">Luzes</h2>
        {!lampsReady ? (
          <p className="text-muted-foreground">A ler as lâmpadas.</p>
        ) : lampsFailed ? (
          <p className="text-muted-foreground">Não deu para ler as lâmpadas.</p>
        ) : lamps.length === 0 ? (
          <p className="text-muted-foreground">Nenhuma lâmpada nesta casa.</p>
        ) : (
          <ul className="flex flex-col">
            {lamps.map((lamp) => (
              <li key={lamp.deviceName} className="border-t border-border first:border-t-0">
                <Link
                  href="/dashboard/iluminacao"
                  className="flex min-h-16 items-center gap-4 py-3 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  <span
                    className="h-9 w-9 shrink-0 rounded-full border border-border"
                    style={{
                      background: lamp.on ? lamp.color : "transparent",
                      boxShadow: lamp.on ? `0 0 18px ${lamp.color}` : undefined,
                    }}
                    aria-hidden
                  />
                  <span className="min-w-0">
                    <span className="block text-lg">{lamp.displayName}</span>
                    <span className="block text-sm text-muted-foreground">
                      {lamp.on === null ? "Estado por confirmar" : lamp.on ? "Acesa" : "Apagada"}
                      {lamp.location ? `, ${lamp.location}` : ""}
                    </span>
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="flex flex-col gap-2 border-t border-border pt-8">
        <h2 className="text-2xl font-bold">Ar</h2>
        <p className="text-muted-foreground">
          {airOnNetwork === null && "A ver se o ar está na rede."}
          {airOnNetwork === true && "O ar está na rede."}
          {airOnNetwork === false && "O ar não está na rede. As luzes continuam a funcionar."}
        </p>
        <p>
          {lastCommand
            ? `Último comando aceito: ${describeAcCommand(lastCommand.power, lastCommand.temp)}.`
            : "Ainda não há comando aceito."}
        </p>
        <Link
          href="/dashboard/ar-condicionado"
          className="inline-flex min-h-11 w-fit items-center underline-offset-4 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          Preparar o comando
        </Link>
      </section>

      <p className="border-t border-border pt-8 text-muted-foreground">
        O leitor da entrada ainda não está ligado.
      </p>
    </div>
  );
}

function toUnknownLamp(device: DeviceResponse): LampView {
  return {
    deviceName: device.deviceName,
    displayName: device.displayName || device.deviceName,
    location: device.location || "",
    on: null,
    color: "#FFFFFF",
  };
}
