import type { ReactNode } from "react";

export function Alert({
  title,
  children,
  action,
}: {
  title: string;
  children: ReactNode;
  action?: ReactNode;
}) {
  return (
    <div
      role="alert"
      className="flex flex-wrap items-start justify-between gap-4 border border-danger/25 bg-danger-soft px-4 py-3"
    >
      <div className="min-w-0">
        <p className="text-[13px] font-medium text-ink">{title}</p>
        <p className="mt-1 text-[13px] leading-relaxed text-secondary">{children}</p>
      </div>
      {action ? <div className="shrink-0">{action}</div> : null}
    </div>
  );
}
