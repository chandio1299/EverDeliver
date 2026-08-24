import type { NotificationStatus } from "../../types";

const tones: Record<NotificationStatus, { dot: string; label: string }> = {
  QUEUED: { dot: "bg-muted", label: "Queued" },
  PROCESSING: { dot: "bg-accent", label: "Processing" },
  SENT: { dot: "bg-ok", label: "Sent" },
  FAILED: { dot: "bg-warn", label: "Failed" },
  DEAD: { dot: "bg-danger", label: "Dead" },
};

export function StatusBadge({ status }: { status: NotificationStatus | string }) {
  const tone = tones[status as NotificationStatus] ?? { dot: "bg-muted", label: status };
  return (
    <span className="inline-flex items-center gap-2 text-[13px] text-ink">
      <span className={`h-1.5 w-1.5 shrink-0 rounded-full ${tone.dot}`} aria-hidden />
      <span>{tone.label}</span>
    </span>
  );
}
