# EverDeliver Delivery Console — DESIGN.md

Synthesized from [Taste Skill redesign](https://www.tasteskill.dev/) + [awesome-design-md](https://github.com/VoltAgent/awesome-design-md) refs (Linear, PostHog, HashiCorp, Sentry). Locked to SPEC Phase 4 Operate mode ([ADR-0004](../ADR/0004-phase4-dashboard.md)).

## Brief

| Field | Value |
|---|---|
| Surface | Operator delivery console |
| Job | Spot failed deliveries and retry them |
| Audience | Local operators / developers |
| Mode | Operate (not Persuade) |
| DESIGN_VARIANCE | 3 (left-aligned, quiet asymmetry; no artsy chaos) |
| MOTION_INTENSITY | 2 (hover / pressed only; honor prefers-reduced-motion) |
| VISUAL_DENSITY | 8 (cockpit / packed data; table owns the viewport) |

## Design read

Cool-paper ops console. Dense Linear-like craft on a light HashiCorp/PostHog engineering canvas. One blue accent. Hairline structure. No sidebar, no equal metric cards, no purple mesh, no glass.

## Sources (downloaded under `docs/design/refs/`)

- `taste-frontend.SKILL.md` / `taste-redesign.SKILL.md` — anti-slop bans, redesign audit order
- `linear.DESIGN.md` — product density, hairline panels, single accent discipline
- `posthog.DESIGN.md` — IBM Plex discipline for engineering UI
- `hashicorp.DESIGN.md` — technical accent blue, enterprise confidence
- `sentry.DESIGN.md` — developer-tools status language (semantic color only)

## Tokens

```yaml
colors:
  canvas: "#f4f5f7"          # cool paper, not cream/AI beige
  surface: "#ffffff"
  surface-muted: "#eceef2"
  ink: "#111318"
  ink-secondary: "#3d4450"
  muted: "#6b7280"
  hairline: "#d7dbe3"
  hairline-strong: "#c2c7d2"
  accent: "#2563eb"          # HashiCorp-adjacent blue; never purple
  accent-hover: "#1d4ed8"
  accent-soft: "#eff4ff"
  ok: "#15803d"
  warn: "#a16207"
  danger: "#b91c1c"
  focus: "#2563eb"

typography:
  sans: "IBM Plex Sans"      # PostHog engineering voice
  mono: "IBM Plex Mono"      # ids, timestamps, metrics
  brand: 26px / 600 / -0.04em
  metric: 44px / 500 / -0.03em / tabular-nums
  label: 11px / 500 / 0.06em / sentence case (not ALL CAPS soup)
  body: 13px / 400 / 1.45
  mono-sm: 12px / 400 / tabular-nums

shape:
  radius-control: 2px        # Shape Consistency Lock: near-sharp
  radius-none: 0             # table remains sharp
  control-height: 32px
  row-height: 36px

motion:
  duration: 150ms
  easing: ease
  press: scale(0.98)
  reduced-motion: disable transforms
```

## Layout

1. Compact header: brand + “Delivery console” + live “Updated …”
2. Stats strip: success rate is the only large number; latency and failures are secondary; failures-by-channel is a compact bar, not a BI chart
3. Filter toolbar flush above the table
4. Dense notification table (primary surface, ≥60% of viewport intent)
5. No sidebar, no fake nav for unbuilt phases

## Component rules

- Status = text label + 6px semantic dot (never color alone)
- Retry only on FAILED / DEAD
- Cards banned for metrics; metrics sit in plain layout
- Borders: hairlines. No multi-layer shadows. No glass. No gradients.
- Copy: sentence case, no em/en dashes, no “Oops!”, no hype words
- Loading: skeleton geometry matching the table
- Empty: say what to do next (POST or widen filters)
- Error: direct message + Retry action

## Hard bans (Taste §9, adapted for Operate)

- Purple / mesh / AI beige cream
- Equal-weight 3–4 metric cards
- Pill status chip soup
- Glassmorphism / glow / decorative pulses
- Marketing hero, sidebar chrome, locale/weather strips
- Em-dashes in UI copy
