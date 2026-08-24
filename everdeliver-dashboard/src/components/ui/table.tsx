import type { ReactNode, TdHTMLAttributes, ThHTMLAttributes } from "react";

export function Table({ children }: { children: ReactNode }) {
  return (
    <div className="overflow-x-auto border border-line bg-surface">
      <table className="w-full min-w-[980px] border-collapse text-left">{children}</table>
    </div>
  );
}

export function Th({ children, className = "", ...props }: ThHTMLAttributes<HTMLTableCellElement>) {
  return (
    <th
      className={`sticky top-0 border-b border-line bg-soft px-3 py-2 text-left text-label uppercase text-muted ${className}`}
      {...props}
    >
      {children}
    </th>
  );
}

export function Td({ children, className = "", ...props }: TdHTMLAttributes<HTMLTableCellElement>) {
  return (
    <td className={`h-9 border-b border-line px-3 text-[13px] align-middle ${className}`} {...props}>
      {children}
    </td>
  );
}
