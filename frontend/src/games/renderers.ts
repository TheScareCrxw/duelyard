import { dartsRenderer } from "./darts/DartsGame";
import { miniGolfRenderer } from "./minigolf/MiniGolfGame";
import { poolRenderer } from "./pool/PoolGame";
import { rocketRenderer } from "./rocket/RocketGame";
import type { GameRenderer } from "./types";

/** Client renderer per game slug. A new game adds one entry here and one module on the backend. */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
export const GAME_RENDERERS: Record<string, GameRenderer<any>> = {
  darts: dartsRenderer,
  "mini-golf": miniGolfRenderer,
  "eight-ball": poolRenderer,
  "rocket-launcher": rocketRenderer,
};
