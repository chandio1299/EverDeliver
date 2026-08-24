import { Bar, BarChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { NotificationStats } from "../types";

export function FailureChart({ stats }: { stats: NotificationStats | null }) {
  const data = Object.entries(stats?.failuresByChannel ?? {})
    .map(([channel, count]) => ({ channel, count }))
    .filter((row) => row.count > 0)
    .sort((a, b) => b.count - a.count);

  return (
    <div className="min-w-0" aria-label="Failures by channel">
      <p className="mb-3 text-label uppercase text-muted">Failures by channel</p>
      {!stats || data.length === 0 ? (
        <div className="flex h-[104px] items-center border border-dashed border-line bg-surface px-4 text-[13px] text-muted">
          No channel failures in this window.
        </div>
      ) : (
        <div className="h-[104px] w-full border border-line bg-surface px-2 py-2">
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={data} layout="vertical" margin={{ top: 4, right: 12, left: 0, bottom: 4 }}>
              <XAxis type="number" hide />
              <YAxis
                type="category"
                dataKey="channel"
                width={76}
                tick={{ fill: "#6b7280", fontSize: 12, fontFamily: "IBM Plex Sans" }}
                axisLine={false}
                tickLine={false}
              />
              <Tooltip
                cursor={{ fill: "#eceef2" }}
                contentStyle={{
                  border: "1px solid #d7dbe3",
                  borderRadius: 2,
                  background: "#ffffff",
                  fontSize: 12,
                  fontFamily: "IBM Plex Sans",
                  color: "#111318",
                  boxShadow: "none",
                }}
              />
              <Bar dataKey="count" fill="#b91c1c" barSize={8} name="Failures" isAnimationActive={false} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </div>
  );
}
