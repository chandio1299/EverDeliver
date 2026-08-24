import type { SelectHTMLAttributes } from "react";

export function Select({ className = "", children, ...props }: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select
      className={`h-8 min-w-[9.5rem] rounded-control border border-line bg-surface px-2 text-[13px] text-ink transition-colors duration-ui hover:border-line-strong ${className}`}
      {...props}
    >
      {children}
    </select>
  );
}
