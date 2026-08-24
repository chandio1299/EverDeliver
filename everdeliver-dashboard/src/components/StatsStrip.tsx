import type { NotificationStats } from "../types";
import { formatLatency, formatPercent } from "../lib/format";
import { FailureChart } from "./FailureChart";

export function StatsStrip({ stats }: { stats: NotificationStats | null }) {
  const failed = (stats?.byStatus.FAILED ?? 0) + (stats?.byStatus.DEAD ?? 0);
  const sent = stats?.byStatus.SENT ?? 0;

  return (
    <section
      aria-label="Delivery stats"
      className="grid gap-8 border-b border-line py-7 md:grid-cols-[minmax(0,1.15fr)_minmax(0,0.85fr)]"
    >
      <div className="flex flex-wrap items-end gap-x-12 gap-y-6">
        <div>
          <p className="text-label uppercase text-muted">Success rate</p>
          <p className="mt-2 font-sans text-metric tabular-nums text-ink">
            {stats ? formatPercent(stats.successRate) : "-"}
          </p>
          <p className="mt-2 max-w-[18rem] text-[13px] leading-snug text-secondary">
            {stats ? `${sent} sent of ${stats.total} in this window` : "Waiting for the first poll"}
          </p>
        </div>
        <div className="pb-1">
          <p className="text-label uppercase text-muted">Avg latency</p>
          <p className="mt-2 font-mono text-[1.375rem] tabular-nums tracking-tight text-ink">
            {formatLatency(stats?.avgLatencyMs)}
          </p>
          <p className="mt-2 text-[13px] text-muted">sent_at minus created_at</p>
        </div>
        <div className="pb-1">
          <p className="text-label uppercase text-muted">Failures</p>
          <p className="mt-2 font-mono text-[1.375rem] tabular-nums tracking-tight text-ink">
            {stats ? failed : "-"}
          </p>
          <p className="mt-2 text-[13px] text-muted">FAILED + DEAD</p>
        </div>
      </div>
      <FailureChart stats={stats} />
    </section>
  );
}
