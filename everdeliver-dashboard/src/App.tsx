import { useCallback, useEffect, useState } from "react";
import { fetchNotifications, fetchStats, retryNotification } from "./api";
import { FilterBar } from "./components/FilterBar";
import { NotificationTable } from "./components/NotificationTable";
import { StatsStrip } from "./components/StatsStrip";
import { Alert } from "./components/ui/alert";
import { Button } from "./components/ui/button";
import { timeAgo } from "./lib/format";
import type { Filters, Notification, NotificationStats } from "./types";

const POLL_MS = 2000;

const initialFilters: Filters = {
  channel: "",
  status: "",
  range: "24h",
};

export function App() {
  const [filters, setFilters] = useState<Filters>(initialFilters);
  const [rows, setRows] = useState<Notification[]>([]);
  const [stats, setStats] = useState<NotificationStats | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [updatedAt, setUpdatedAt] = useState<number | null>(null);
  const [now, setNow] = useState(Date.now());
  const [retryingId, setRetryingId] = useState<string | null>(null);
  const [retryMessage, setRetryMessage] = useState<string | null>(null);

  const load = useCallback(async (silent = false) => {
    if (!silent) {
      setLoading(true);
    }
    try {
      const [nextRows, nextStats] = await Promise.all([
        fetchNotifications(filters),
        fetchStats(filters),
      ]);
      setRows(nextRows);
      setStats(nextStats);
      setError(null);
      setUpdatedAt(Date.now());
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Request failed");
    } finally {
      setLoading(false);
    }
  }, [filters]);

  useEffect(() => {
    void load(false);
    const timer = window.setInterval(() => {
      void load(true);
    }, POLL_MS);
    return () => window.clearInterval(timer);
  }, [load]);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  async function onRetry(id: string) {
    setRetryingId(id);
    setRetryMessage(null);
    try {
      await retryNotification(id);
      setRetryMessage("Requeued through Kafka. A retry can double-send.");
      await load(true);
    } catch (cause) {
      setRetryMessage(cause instanceof Error ? cause.message : "Retry failed");
    } finally {
      setRetryingId(null);
    }
  }

  return (
    <div className="mx-auto min-h-[100dvh] max-w-[1280px] px-5 pb-16 pt-7 sm:px-6">
      <a
        href="#notification-queue"
        className="sr-only focus:not-sr-only focus:absolute focus:left-6 focus:top-4 focus:z-50 focus:bg-surface focus:px-3 focus:py-2 focus:text-[13px]"
      >
        Skip to notification queue
      </a>

      <header className="flex flex-wrap items-end justify-between gap-4 border-b border-ink/80 pb-5">
        <div>
          <p className="font-sans text-brand text-ink">EverDeliver</p>
          <h1 className="mt-1 text-[14px] text-secondary">Delivery console</h1>
        </div>
        <div className="text-right">
          <p className="text-label uppercase text-muted">Live feed</p>
          <p className="mt-1 font-mono text-[12px] tabular-nums text-secondary" aria-live="polite">
            {updatedAt ? `Updated ${timeAgo(updatedAt, now)}` : "Connecting"}
          </p>
        </div>
      </header>

      <StatsStrip stats={stats} />
      <FilterBar filters={filters} onChange={setFilters} />

      <div className="pt-4" id="notification-queue">
        {error ? (
          <div className="mb-4">
            <Alert
              title="Could not load notifications"
              action={
                <Button variant="primary" onClick={() => void load(false)}>
                  Retry
                </Button>
              }
            >
              {error}. Confirm the API is on port 8081, or open this page through Compose on port 3000.
            </Alert>
          </div>
        ) : null}

        {retryMessage ? (
          <p className="mb-3 text-[13px] text-secondary" role="status">
            {retryMessage}
          </p>
        ) : null}

        <NotificationTable
          rows={rows}
          loading={loading}
          retryingId={retryingId}
          onRetry={(id) => void onRetry(id)}
        />
      </div>
    </div>
  );
}
