import { describe, expect, it } from "vitest";
import { linearScale, niceTicks } from "./chartScale";

describe("niceTicks", () => {
  it("covers the range with round steps", () => {
    expect(niceTicks(984, 1016)).toEqual([980, 990, 1000, 1010, 1020]);
    expect(niceTicks(900, 1300)).toEqual([900, 1000, 1100, 1200, 1300]);
  });

  it("handles a flat series", () => {
    const ticks = niceTicks(1000, 1000);
    expect(ticks[0]).toBeLessThan(1000);
    expect(ticks.at(-1)).toBeGreaterThan(1000);
    expect(ticks).toContain(1000);
  });
});

describe("linearScale", () => {
  it("maps and inverts direction", () => {
    const y = linearScale(900, 1100, 200, 0);
    expect(y(900)).toBe(200);
    expect(y(1000)).toBe(100);
    expect(y(1100)).toBe(0);
  });
});
