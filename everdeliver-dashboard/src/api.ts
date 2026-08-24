import type { Filters, Notification, NotificationStats } from "./types";

function sinceParam(range: Filters["range"]): string | undefined {
  const now = Date.now();
  if (range === "1h") {
    return new Date(now - 60 * 60 * 1000).toISOString();
  }
  if (range === "24h") {
    return new Date(now - 24 * 60 * 60 * 1000).toISOString();
  }
  if (range === "7d") {
    return new Date(now - 7 * 24 * 60 * 60 * 1000).toISOString();
  }
  return undefined;
}

async function parseError(response: Response): Promise<string> {
  try {
    const text = await response.text();
    if (!text) {
      return `${response.status} ${response.statusText}`;
    }
    try {
      const json = JSON.parse(text) as { message?: string };
      return json.message ?? text;
    } catch {
      return text;
    }
  } catch {
    return `${response.status} ${response.statusText}`;
  }
}

export function listQuery(filters: Filters): string {
  const params = new URLSearchParams();
  if (filters.status) {
    params.set("status", filters.status);
  }
  if (filters.channel) {
    params.set("channel", filters.channel);
  }
  const since = sinceParam(filters.range);
  if (since) {
    params.set("since", since);
  }
  params.set("limit", "100");
  return params.toString();
}

export async function fetchNotifications(filters: Filters): Promise<Notification[]> {
  const response = await fetch(`/api/v1/notifications?${listQuery(filters)}`);
  if (!response.ok) {
    throw new Error(await parseError(response));
  }
  return (await response.json()) as Notification[];
}

export async function fetchStats(filters: Filters): Promise<NotificationStats> {
  const params = new URLSearchParams();
  const since = sinceParam(filters.range);
  if (since) {
    params.set("since", since);
  }
  const query = params.toString();
  const response = await fetch(`/api/v1/notifications/stats${query ? `?${query}` : ""}`);
  if (!response.ok) {
    throw new Error(await parseError(response));
  }
  return (await response.json()) as NotificationStats;
}

export async function retryNotification(id: string): Promise<Notification> {
  const response = await fetch(`/api/v1/notifications/${id}/retry`, { method: "POST" });
  if (!response.ok) {
    throw new Error(await parseError(response));
  }
  return (await response.json()) as Notification;
}
