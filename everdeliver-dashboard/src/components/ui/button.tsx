import type { ButtonHTMLAttributes, ReactNode } from "react";

type Variant = "primary" | "ghost" | "danger";

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  children: ReactNode;
}

const variants: Record<Variant, string> = {
  primary:
    "bg-accent text-white hover:bg-accent-hover active:scale-[0.98] disabled:bg-soft disabled:text-muted disabled:active:scale-100",
  ghost:
    "bg-surface text-ink border border-line hover:border-line-strong hover:bg-soft active:scale-[0.98] disabled:text-muted disabled:active:scale-100",
  danger:
    "bg-surface text-danger border border-danger/30 hover:bg-danger-soft active:scale-[0.98] disabled:opacity-50 disabled:active:scale-100",
};

export function Button({ variant = "ghost", className = "", children, ...props }: ButtonProps) {
  return (
    <button
      className={`inline-flex h-8 items-center justify-center rounded-control px-3 text-[13px] font-medium tracking-tight transition-all duration-ui ${variants[variant]} ${className}`}
      {...props}
    >
      {children}
    </button>
  );
}
