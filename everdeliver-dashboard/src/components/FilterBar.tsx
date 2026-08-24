import type { Channel, Filters, NotificationStatus, TimeRange } from "../types";
import { Select } from "./ui/select";

const CHANNELS: Array<Channel | ""> = ["", "email", "sms", "whatsapp", "slack", "webhook"];
const STATUSES: Array<NotificationStatus | ""> = [
  "",
  "QUEUED",
  "PROCESSING",
  "SENT",
  "FAILED",
  "DEAD",
];
const RANGES: TimeRange[] = ["1h", "24h", "7d", "all"];

const rangeLabel: Record<TimeRange, string> = {
  "1h": "Last hour",
  "24h": "Last 24 hours",
  "7d": "Last 7 days",
  all: "All time",
};

export function FilterBar({
  filters,
  onChange,
}: {
  filters: Filters;
  onChange: (next: Filters) => void;
}) {
  return (
    <form
      className="flex flex-wrap items-end gap-x-5 gap-y-3 border-b border-line py-4"
      onSubmit={(event) => event.preventDefault()}
      aria-label="Notification filters"
    >
      <label className="flex flex-col gap-1.5 text-label uppercase text-muted">
        Channel
        <Select
          value={filters.channel}
          onChange={(event) => onChange({ ...filters, channel: event.target.value as Channel | "" })}
        >
          {CHANNELS.map((channel) => (
            <option key={channel || "all"} value={channel}>
              {channel || "All channels"}
            </option>
          ))}
        </Select>
      </label>
      <label className="flex flex-col gap-1.5 text-label uppercase text-muted">
        Status
        <Select
          value={filters.status}
          onChange={(event) => onChange({ ...filters, status: event.target.value as NotificationStatus | "" })}
        >
          {STATUSES.map((status) => (
            <option key={status || "all"} value={status}>
              {status || "All statuses"}
            </option>
          ))}
        </Select>
      </label>
      <label className="flex flex-col gap-1.5 text-label uppercase text-muted">
        Time range
        <Select
          value={filters.range}
          onChange={(event) => onChange({ ...filters, range: event.target.value as TimeRange })}
        >
          {RANGES.map((range) => (
            <option key={range} value={range}>
              {rangeLabel[range]}
            </option>
          ))}
        </Select>
      </label>
    </form>
  );
}
