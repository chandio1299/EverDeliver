const relativeTime = new Intl.RelativeTimeFormat("en", { numeric: "auto" });

export function formatPercent(rate: number): string {
  return `${(rate * 100).toFixed(1)}%`;
}

export function formatLatency(ms: number | null | undefined): string {
  if (ms == null || Number.isNaN(ms)) {
    return "-";
  }
  if (ms < 1000) {
    return `${Math.round(ms)} ms`;
  }
  return `${(ms / 1000).toFixed(1)} s`;
}

export function formatTimestamp(iso: string | null | undefined): string {
  if (!iso) {
    return "-";
  }
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) {
    return "-";
  }
  return date.toLocaleString(undefined, {
    month: "short",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  });
}

export function formatShortId(id: string): string {
  return id.slice(0, 8);
}

export function timeAgo(from: number, now = Date.now()): string {
  const deltaSec = Math.round((from - now) / 1000);
  const abs = Math.abs(deltaSec);
  if (abs < 5) {
    return "just now";
  }
  if (abs < 60) {
    return relativeTime.format(deltaSec, "second");
  }
  return relativeTime.format(Math.round(deltaSec / 60), "minute");
}
