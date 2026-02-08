#!/bin/bash

# Demo script showing API usage
BASE_URL="http://localhost:8080"

echo "================================================"
echo "Barony Prototype API Demo"
echo "================================================"
echo ""

echo "1. Getting initial game state..."
curl -s ${BASE_URL}/state | python3 -m json.tool | head -20
echo ""

echo "2. Moving player 1's army to village at (3,3)..."
curl -X POST ${BASE_URL}/command \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{"type":"MOVE","armyId":1,"targetX":3,"targetY":3}' \
  -s > /dev/null
echo "   Done!"
echo ""

echo "3. Advancing game by 1 tick (army on village gains 1 soldier)..."
curl -X POST ${BASE_URL}/tick -s | python3 -c "import sys, json; data=json.load(sys.stdin); army1=[a for a in data['armies'] if a['id']==1][0]; print(f\"   Tick: {data['tickCount']}, Army 1 soldiers: {army1['soldiers']}\")"
echo ""

echo "4. Moving player 2's army to same location for combat..."
curl -X POST ${BASE_URL}/command \
  -H "Content-Type: application/json; charset=UTF-8" \
  -d '{"type":"MOVE","armyId":2,"targetX":3,"targetY":3}' \
  -s > /dev/null
echo "   Done! Both armies at (3,3)"
echo ""

echo "5. Advancing game by 1 tick (combat occurs)..."
RESULT=$(curl -X POST ${BASE_URL}/tick -s)
echo "${RESULT}" | python3 -c "import sys, json; data=json.load(sys.stdin); print(f\"   Tick: {data['tickCount']}, Remaining armies: {len(data['armies'])}\")"
echo "${RESULT}" | python3 -c "import sys, json; data=json.load(sys.stdin); [print(f\"   Army {i}: Player {a['playerId']}, {a['soldiers']} soldiers at ({a['x']},{a['y']})\") for i, a in enumerate(data['armies'])]"
echo ""

echo "================================================"
echo "Demo complete!"
echo "================================================"
