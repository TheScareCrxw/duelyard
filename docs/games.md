# Games: rules and server architecture

All four games are **server-authoritative**. A client only sends *inputs* (an aim point, an angle
and a power). The server validates the input, runs the physics or scoring itself (including all
randomness), and broadcasts the result. Clients animate what the server decided; they never report
a score, a position or a winner.

## Shared model

| Concept | Rule |
| --- | --- |
| Players | 2 per match, seats 0 and 1. Seat 0 is chosen at random and moves first. |
| Turn timer | Each game has a per-turn limit. On timeout the server applies that game's *timeout move*. |
| Inactivity | 3 consecutive timeouts by the same player forfeit the match. |
| Disconnects | A disconnected player has 30 s to reconnect; their turn timer keeps running. After the grace period they forfeit (ranked and casual). If both are gone, the match is abandoned (no result). |
| Reconnects | On reconnect the server sends the full current state; the game continues. |
| Result | The game module decides WIN/LOSS/DRAW; `MatchCompletionService` records it and applies Elo for ranked games. |

### Engine interface

Each game module (`backend/.../game/<game>/`) provides a `GameSession` per match:

```
applyAction(seat, input) -> events      validate + simulate one move (throws InvalidActionException)
onTurnTimeout(seat)      -> events      the timeout move
view(viewerSeat)         -> state       everything the client renders (no hidden info leaks)
turnSeat()                               whose move it is
outcome()                                empty while playing; per-seat results when over
```

Sessions are plain objects with no I/O, so they are unit-tested directly. All randomness comes from
a per-match `RandomGenerator` the server owns.

---

## Darts: 301, straight out

- Both players start on **301**. A turn is **3 darts**.
- Input per dart: an aim point on the board (mm, board centre = 0,0) and a *release* value 0..1
  from the client's timing meter. The sweet spot is 0.45-0.55.
- The server adds Gaussian scatter: σ = 9 mm, growing up to 30 mm with distance from the sweet
  spot. The landing point is scored on a standard board (bull 50, outer bull 25, triple ring
  99-107 mm, double ring 162-170 mm, outside 170 mm = 0, standard segment order 20-1-18-4-13-6-10-
  15-2-17-3-19-7-16-8-11-14-9-12-5).
- Reaching exactly 0 wins immediately (no double-out, which keeps matches short).
- **Bust:** going below 0 ends the turn and restores the score from the turn's start.
- After **10 rounds** each, the lower remaining score wins; equal is a draw.
- Turn limit: 45 s per turn. Timeout move: remaining darts miss (score 0).

## Mini Golf: 3 holes, fewest strokes

- Three original courses (walls, angled banks and round bumpers). Every match plays all three.
- On each hole, players alternate strokes; a player who has holed out is skipped until the other
  finishes. Seat order alternates per hole.
- Input: angle + power (0..1). The server simulates rolling friction, wall and bumper bounces, and
  the cup. The ball drops if it crosses the cup slowly enough; too fast and it lips out.
- Maximum **6 strokes** per hole. A player who hasn't holed out after 6 is recorded as 7.
- Fewest total strokes after 3 holes wins; equal is a draw.
- Turn limit: 30 s. Timeout move: the stroke counts but the ball doesn't move.

## 8-Ball Pool

- Standard 15-ball rack; seat 0 breaks. Input: angle + power. Simplified physics: no spin.
- **Open table** until a player legally pots a ball after the break. That player takes the group
  (solids 1-7 or stripes 9-15) of the first ball potted.
- A player keeps shooting while they legally pot one of their own group (any non-8 ball on an
  open table).
- **Fouls:** cue ball potted, no ball hit, or the first ball hit is not your group (on an open table:
  the 8 first). After a foul the opponent shoots, with the cue ball re-spotted on the head spot if it
  was potted (simplification: no ball-in-hand placement).
- **The 8:** potting it after clearing your group, without fouling, wins. Potting it earlier, or with
  a foul, loses.
- No called pockets (simplification).
- Turn limit: 45 s. Timeout move: counts as a foul.

## Rocket Launcher: artillery duel

- Generated rolling terrain (seeded), one launcher per side, **100 HP** each. Launchers don't move.
- Input: angle (0-180°) + power. The projectile flies under gravity plus a **wind** value the server
  re-rolls every turn and shows to both players.
- The rocket explodes on terrain or a launcher. Damage falls off with distance (max 40 at the
  centre, 0 beyond a 60 px radius) and it carves a crater. You can damage yourself.
- First to reduce the opponent to 0 HP wins (if both hit 0 together, it's a draw). After **20 shots**
  in total, higher HP wins; equal is a draw.
- Turn limit: 30 s. Timeout move: the turn is skipped.

---

## Real-time protocol (WebSocket, JSON)

Connection: the browser asks the Next.js server for a one-time **ticket**, then connects to
`/ws?ticket=...`. Tickets are random, single-use and expire after 30 s, so the access token never
reaches browser JavaScript.

Client to server: `queue.join {game, mode}`, `queue.leave`, `match.action {matchId, input}`,
`match.sync {matchId}`, `ping`.

Server to client: `welcome {username, activeMatchId}`, `queue.status`, `queue.left`, `match.found
{matchId, game, mode, opponent, startsAt}`, `match.state {matchId, view}`, `match.events {matchId,
events, view}`, `match.ended {matchId, result, ratingChange}`, `opponent.connection {connected,
graceSeconds}`, `error {code, message}`, `pong`.

## Matchmaking

- One queue per game and mode. A player can be in one queue, or one match, at a time.
- **Ranked:** paired with the closest-rated waiting player within ±100 Elo, a window that widens
  by 50 every 5 s waited (up to ±600). **Casual:** first come, first served.
- Pairing runs once a second; a pair is removed from the queue atomically, so nobody is matched twice.
- A paired match shows a 5 s countdown, then starts.
- Leaving the queue or disconnecting removes you from it.

Queues and live rooms are held in memory, so a single backend instance runs all matches. To scale
out: move queues to Redis sorted sets (score = rating) and route a match's players to the instance
that owns the room. The `Matchmaker` interface is the seam for that.
