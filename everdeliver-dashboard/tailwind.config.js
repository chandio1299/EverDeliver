/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        canvas: "#f4f5f7",
        surface: "#ffffff",
        soft: "#eceef2",
        ink: "#111318",
        secondary: "#3d4450",
        muted: "#6b7280",
        line: "#d7dbe3",
        "line-strong": "#c2c7d2",
        accent: "#2563eb",
        "accent-hover": "#1d4ed8",
        "accent-soft": "#eff4ff",
        danger: "#b91c1c",
        "danger-soft": "#fef2f2",
        warn: "#a16207",
        ok: "#15803d",
      },
      fontFamily: {
        sans: ['"IBM Plex Sans"', "ui-sans-serif", "system-ui", "sans-serif"],
        mono: ['"IBM Plex Mono"', "ui-monospace", "SFMono-Regular", "monospace"],
      },
      fontSize: {
        brand: ["1.625rem", { lineHeight: "1.15", letterSpacing: "-0.04em", fontWeight: "600" }],
        metric: ["2.75rem", { lineHeight: "1", letterSpacing: "-0.03em", fontWeight: "500" }],
        label: ["0.6875rem", { lineHeight: "1.2", letterSpacing: "0.04em", fontWeight: "500" }],
      },
      borderRadius: {
        control: "2px",
      },
      transitionDuration: {
        ui: "150ms",
      },
      boxShadow: {
        focus: "0 0 0 2px #ffffff, 0 0 0 4px #2563eb",
      },
    },
  },
  plugins: [],
};
