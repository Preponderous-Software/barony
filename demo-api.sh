#!/bin/bash
#
# Plays a short game through the backend's single-game REST API: player 1's army is marched to the
# nearest village, which it captures and then recruits from. Needs curl and python3, and a running
# backend (cd backend && ./mvnw spring-boot:run). Set BASE_URL to reach one somewhere else.
#
# This resets the backend's single game. Per-player games under /api/session/* are not touched.

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"

# Prints fields of the game state on stdin. $1 is a Python expression over `state`.
state_field() {
  python3 -c "import json, sys; state = json.load(sys.stdin); print($1)"
}

# Prints "x y soldiers" for army $1 from the game state on stdin, or "lost" if it no longer exists.
army_status() {
  python3 -c '
import json, sys
state = json.load(sys.stdin)
armies = [a for a in state["armies"] if a["id"] == int(sys.argv[1])]
print("{x} {y} {soldiers}".format(**armies[0]) if armies else "lost")
' "$1"
}

tick() {
  curl -sf -X POST "${BASE_URL}/tick"
}

echo "================================================"
echo "Barony API Demo"
echo "================================================"
echo ""

echo "1. Starting a new game (POST /api/reset)..."
if ! STATE=$(curl -sf -X POST "${BASE_URL}/api/reset"); then
  echo "   No backend answered at ${BASE_URL}. Start one with: cd backend && ./mvnw spring-boot:run" >&2
  exit 1
fi
echo "${STATE}" | state_field '"   Map: {} x {}, villages: {}".format(len(state["grid"]), len(state["grid"][0]), sum(t["type"] == "VILLAGE" for column in state["grid"] for t in column))'
echo "${STATE}" | python3 -c '
import json, sys
for army in json.load(sys.stdin)["armies"]:
    print("   Army {id}: player {playerId}, {soldiers} soldiers at ({x},{y})".format(**army))
'
echo ""

# Army ids keep counting across resets and villages are placed at random, so both are read from
# the state rather than assumed.
read -r ARMY_ID VILLAGE_X VILLAGE_Y < <(echo "${STATE}" | python3 -c '
import json, sys
state = json.load(sys.stdin)
army = next(a for a in state["armies"] if a["playerId"] == 1)
villages = [(x, y) for x, column in enumerate(state["grid"]) for y, tile in enumerate(column)
            if tile["type"] == "VILLAGE"]
x, y = min(villages, key=lambda v: abs(v[0] - army["x"]) + abs(v[1] - army["y"]))
print(army["id"], x, y)
')

echo "2. Ordering army ${ARMY_ID} to the nearest village, at (${VILLAGE_X},${VILLAGE_Y}) (POST /command)..."
curl -sf -X POST "${BASE_URL}/command" \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d "{\"type\":\"MOVE\",\"armyId\":${ARMY_ID},\"targetX\":${VILLAGE_X},\"targetY\":${VILLAGE_Y}}" \
  > /dev/null
echo ""

echo "3. Advancing turns until it arrives; armies move 1 tile per turn (POST /tick)..."
while true; do
  STATE=$(tick)
  TICK_COUNT=$(echo "${STATE}" | state_field 'state["tickCount"]')
  STATUS=$(echo "${STATE}" | army_status "${ARMY_ID}")
  GAME_OVER=$(echo "${STATE}" | state_field 'state["gameOver"]')
  if [ "${STATUS}" = "lost" ] || [ "${GAME_OVER}" = "True" ]; then
    echo "   Tick ${TICK_COUNT}: army ${ARMY_ID} did not reach the village. Run the demo again."
    exit 0
  fi
  read -r ARMY_X ARMY_Y SOLDIERS <<< "${STATUS}"
  echo "   Tick ${TICK_COUNT}: army ${ARMY_ID} at (${ARMY_X},${ARMY_Y}), ${SOLDIERS} soldiers"
  if [ "${ARMY_X}" = "${VILLAGE_X}" ] && [ "${ARMY_Y}" = "${VILLAGE_Y}" ]; then
    break
  fi
done
echo ""

echo "4. Advancing one more turn; a village is captured at the end of the turn an army arrives,"
echo "   and it recruits for its owner from the next turn on..."
STATE=$(tick)
TICK_COUNT=$(echo "${STATE}" | state_field 'state["tickCount"]')
OWNER=$(echo "${STATE}" | state_field "state[\"grid\"][${VILLAGE_X}][${VILLAGE_Y}][\"ownerId\"]")
STATUS=$(echo "${STATE}" | army_status "${ARMY_ID}")
if [ "${STATUS}" = "lost" ]; then
  echo "   Tick ${TICK_COUNT}: army ${ARMY_ID} was destroyed by the AI at the village. Run the demo again."
  exit 0
fi
read -r _ _ NOW_SOLDIERS <<< "${STATUS}"
echo "   Tick ${TICK_COUNT}: army ${ARMY_ID} has ${NOW_SOLDIERS} soldiers (was ${SOLDIERS}), village owner is player ${OWNER}"
echo ""

echo "================================================"
echo "Demo complete!"
echo "================================================"
