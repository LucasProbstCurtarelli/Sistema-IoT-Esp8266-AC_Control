const STORAGE_KEY = "casa_ultimo_comando_ar";
const TEMP_MIN = 17;
const TEMP_MAX = 30;

export interface LastAcCommand {
    power: "on" | "off";
    temp: number;
}

export function describeAcCommand(power: "on" | "off", temp: number): string {
    if (power === "on") return `ligado a ${temp}°`;
    return `desligado, a ${temp}°`;
}

export function readLastAcCommand(): LastAcCommand | null {
    if (typeof window === "undefined") return null;

    try {
        const stored = window.localStorage.getItem(STORAGE_KEY);
        if (!stored) return null;
        return parseLastAcCommand(JSON.parse(stored));
    } catch {
        return null;
    }
}

export function writeLastAcCommand(power: "on" | "off", temp: number): LastAcCommand {
    const accepted: LastAcCommand = { power, temp };
    try {
        window.localStorage.setItem(STORAGE_KEY, JSON.stringify(accepted));
    } catch {
        // The screen still shows the command this send accepted.
    }
    return accepted;
}

function parseLastAcCommand(value: unknown): LastAcCommand | null {
    if (!value || typeof value !== "object") return null;
    const power = (value as { power?: unknown }).power;
    const temp = (value as { temp?: unknown }).temp;
    if (power !== "on" && power !== "off") return null;
    if (typeof temp !== "number" || !Number.isInteger(temp) || temp < TEMP_MIN || temp > TEMP_MAX) {
        return null;
    }
    return { power, temp };
}
