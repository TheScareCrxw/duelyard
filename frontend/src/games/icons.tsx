import type { SVGProps } from "react";

type IconProps = SVGProps<SVGSVGElement>;

const base = {
  viewBox: "0 0 48 48",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 2.5,
  strokeLinecap: "round",
  strokeLinejoin: "round",
  "aria-hidden": true,
} as const;

export function DartsIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <circle cx="22" cy="26" r="16" />
      <circle cx="22" cy="26" r="9" />
      <circle cx="22" cy="26" r="2.5" fill="currentColor" />
      <path d="M22 26 40 8" />
      <path d="M36 6l4 2 2 4" />
    </svg>
  );
}

export function MiniGolfIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M26 38V8l12 6-12 6" />
      <ellipse cx="26" cy="38" rx="12" ry="3.5" />
      <circle cx="11" cy="31" r="3.5" fill="currentColor" />
    </svg>
  );
}

export function PoolIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <circle cx="24" cy="24" r="16" />
      <circle cx="24" cy="24" r="7.5" />
      <text
        x="24"
        y="28.5"
        textAnchor="middle"
        fontSize="12"
        fontWeight="700"
        fill="currentColor"
        stroke="none"
        fontFamily="ui-sans-serif, system-ui"
      >
        8
      </text>
    </svg>
  );
}

export function RocketIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M6 40c6-16 18-26 34-30" strokeDasharray="3 5" />
      <path d="M33 9l9-2-2 9-6 6-7-7z" fill="currentColor" fillOpacity="0.2" />
      <path d="M29 15l-5 1-3 4 5 1" />
      <path d="M36 22l-1 5-4 3-1-5" />
    </svg>
  );
}

export function GenericGameIcon(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <rect x="8" y="14" width="32" height="20" rx="8" />
      <path d="M16 21v6M13 24h6" />
      <circle cx="31" cy="22" r="1.5" fill="currentColor" />
      <circle cx="34" cy="26" r="1.5" fill="currentColor" />
    </svg>
  );
}
