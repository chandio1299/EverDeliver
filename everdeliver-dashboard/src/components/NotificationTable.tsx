import type { Notification } from "../types";
import { formatShortId, formatTimestamp } from "../lib/format";
import { StatusBadge } from "./ui/badge";
import { Button } from "./ui/button";
import { Table, Td, Th } from "./ui/table";

function canRetry(status: string): boolean {
  return status === "FAILED" || status === "DEAD";
}

export function NotificationTable({
  rows,
  loading,
  retryingId,
  onRetry,
}: {
  rows: Notification[];
  loading: boolean;
  retryingId: string | null;
  onRetry: (id: string) => void;
}) {
  if (loading && rows.length === 0) {
    return (
      <Table>
        <thead>
          <tr>
            <Th>Id</Th>
            <Th>Channel</Th>
            <Th>Recipient</Th>
            <Th>Status</Th>
            <Th className="text-right">Retries</Th>
            <Th>Last error</Th>
            <Th>Created</Th>
            <Th>Updated</Th>
            <Th>Sent</Th>
            <Th>Action</Th>
          </tr>
        </thead>
        <tbody>
          {Array.from({ length: 8 }).map((_, index) => (
            <tr key={index}>
              <Td colSpan={10} className="!h-9 py-2">
                <span className="skeleton block h-3 w-[72%] max-w-2xl" aria-hidden />
                <span className="sr-only">Loading notifications</span>
              </Td>
            </tr>
          ))}
        </tbody>
      </Table>
    );
  }

  if (!loading && rows.length === 0) {
    return (
      <div className="border border-line bg-surface px-6 py-14">
        <p className="text-[15px] font-medium tracking-tight text-ink">No notifications in this window</p>
        <p className="mt-2 max-w-xl text-[13px] leading-relaxed text-secondary">
          POST to <span className="font-mono text-ink">/api/v1/notifications</span> or widen the filters
          above. The table refreshes every two seconds.
        </p>
      </div>
    );
  }

  return (
    <Table>
      <thead>
        <tr>
          <Th>Id</Th>
          <Th>Channel</Th>
          <Th>Recipient</Th>
          <Th>Status</Th>
          <Th className="text-right">Retries</Th>
          <Th>Last error</Th>
          <Th>Created</Th>
          <Th>Updated</Th>
          <Th>Sent</Th>
          <Th>Action</Th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row.id} className="transition-colors duration-ui hover:bg-accent-soft/40">
            <Td className="font-mono text-[12px] text-secondary" title={row.id}>
              {formatShortId(row.id)}
            </Td>
            <Td className="text-secondary">{row.channel}</Td>
            <Td className="max-w-[180px] truncate font-mono text-[12px]" title={row.recipient}>
              {row.recipient}
            </Td>
            <Td>
              <StatusBadge status={row.status} />
            </Td>
            <Td className="text-right font-mono tabular-nums text-secondary">{row.retryCount}</Td>
            <Td className="max-w-[220px] truncate text-muted" title={row.lastError ?? ""}>
              {row.lastError ?? "-"}
            </Td>
            <Td className="whitespace-nowrap font-mono text-[12px] tabular-nums text-secondary">
              {formatTimestamp(row.createdAt)}
            </Td>
            <Td className="whitespace-nowrap font-mono text-[12px] tabular-nums text-secondary">
              {formatTimestamp(row.updatedAt)}
            </Td>
            <Td className="whitespace-nowrap font-mono text-[12px] tabular-nums text-secondary">
              {formatTimestamp(row.sentAt)}
            </Td>
            <Td>
              {canRetry(row.status) ? (
                <Button
                  variant="danger"
                  disabled={retryingId === row.id}
                  onClick={() => onRetry(row.id)}
                >
                  {retryingId === row.id ? "Retrying" : "Retry"}
                </Button>
              ) : (
                <span className="text-muted">-</span>
              )}
            </Td>
          </tr>
        ))}
      </tbody>
    </Table>
  );
}
