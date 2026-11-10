/** "Nice" axis ticks covering [min, max]: steps of 1/2/2.5/5 × 10^n, about `target` ticks. */
export function niceTicks(min: number, max: number, target = 4): number[] {
  if (min === max) {
    min -= 10;
    max += 10;
  }
  const rough = (max - min) / target;
  const magnitude = 10 ** Math.floor(Math.log10(rough));
  const step = [1, 2, 2.5, 5, 10].map((m) => m * magnitude).find((s) => s >= rough) ?? 10 * magnitude;
  const start = Math.floor(min / step) * step;
  const end = Math.ceil(max / step) * step;
  const ticks: number[] = [];
  for (let v = start; v <= end + step / 2; v += step) ticks.push(Math.round(v * 100) / 100);
  return ticks;
}

/** Linear map from [d0, d1] to [r0, r1]. */
export function linearScale(d0: number, d1: number, r0: number, r1: number) {
  const span = d1 - d0 || 1;
  return (v: number) => r0 + ((v - d0) / span) * (r1 - r0);
}
