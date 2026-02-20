/**
 * Converte cor hex para HSV
 */
export function hexToHsv(hex: string): { h: number; s: number; v: number } {
  // Remove #
  const r = parseInt(hex.slice(1, 3), 16) / 255;
  const g = parseInt(hex.slice(3, 5), 16) / 255;
  const b = parseInt(hex.slice(5, 7), 16) / 255;

  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const delta = max - min;

  let h = 0;
  const s = max === 0 ? 0 : delta / max;
  const v = max;

  if (delta !== 0) {
    if (max === r) {
      h = ((g - b) / delta) % 6;
    } else if (max === g) {
      h = (b - r) / delta + 2;
    } else {
      h = (r - g) / delta + 4;
    }
    h = h * 60;
    if (h < 0) {
      h += 360;
    }
  }

  return { h, s: s * 100, v: v * 100 };
}

/**
 * Converte HSV para hex
 */
export function hsvToHex(h: number, s: number, v: number): string {
  h = h % 360;
  if (h < 0) h += 360;
  s = Math.max(0, Math.min(100, s)) / 100;
  v = Math.max(0, Math.min(100, v)) / 100;

  const c = v * s;
  const x = c * (1 - Math.abs(((h / 60) % 2) - 1));
  const m = v - c;

  let r = 0, g = 0, b = 0;

  if (h >= 0 && h < 60) {
    r = c; g = x; b = 0;
  } else if (h >= 60 && h < 120) {
    r = x; g = c; b = 0;
  } else if (h >= 120 && h < 180) {
    r = 0; g = c; b = x;
  } else if (h >= 180 && h < 240) {
    r = 0; g = x; b = c;
  } else if (h >= 240 && h < 300) {
    r = x; g = 0; b = c;
  } else if (h >= 300 && h < 360) {
    r = c; g = 0; b = x;
  }

  const toHex = (x: number) => {
    const hex = Math.round((x + m) * 255).toString(16);
    return hex.length === 1 ? '0' + hex : hex;
  };

  return `#${toHex(r)}${toHex(g)}${toHex(b)}`.toUpperCase();
}

/**
 * Ajusta o brilho de uma cor hex mantendo matiz e saturação
 * @param hex Cor hex original (#RRGGBB)
 * @param brightness Brilho desejado (0-100)
 * @returns Nova cor hex com brilho ajustado
 */
export function adjustBrightness(hex: string, brightness: number): string {
  const hsv = hexToHsv(hex);
  // Ajusta apenas o Value (V) baseado no brilho
  // brightness 0-100 mapeia para Value 0-100
  // Mantém H (matiz) e S (saturação) inalterados
  return hsvToHex(hsv.h, hsv.s, brightness);
}

/**
 * Extrai o brilho (Value) de uma cor hex
 * @param hex Cor hex (#RRGGBB)
 * @returns Brilho (0-100)
 */
export function extractBrightness(hex: string): number {
  const hsv = hexToHsv(hex);
  return Math.round(hsv.v);
}
