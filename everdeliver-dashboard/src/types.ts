export type NotificationStatus = "QUEUED" | "PROCESSING" | "SENT" | "FAILED" | "DEAD";

export type Channel = "email" | "sms" | "whatsapp" | "slack" | "webhook";

export interface Notification {
  id: string;
  status: NotificationStatus;
  channel: Channel | string;
  recipient: string;
  subject: string | null;
  body: string;
  providerMessageId: string | null;
  retryCount: number;
  lastError: string | null;
  createdAt: string;
  updatedAt: string;
  sentAt: string | null;
}

export interface NotificationStats {
  since: string | null;
  total: number;
  byStatus: Record<string, number>;
  byChannel: Record<string, number>;
  successRate: number;
  avgLatencyMs: number | null;
  failuresByChannel: Record<string, number>;
}

export type TimeRange = "1h" | "24h" | "7d" | "all";

export interface Filters {
  channel: Channel | "";
  status: NotificationStatus | "";
  range: TimeRange;
}
