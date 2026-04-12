#!/usr/bin/env bash
# ============================================================================
# Round-trip smoke test for all Barony ASN.1 types
# ============================================================================
# For each type: encode XER → BER → XER, then compare with original.
# This validates that the ASN.1 schema correctly round-trips all message types.
# ============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GEN_DIR="$SCRIPT_DIR/generated"
TEST_DIR="$SCRIPT_DIR/test-data"
PASS=0
FAIL=0
ERRORS=""

# All PDU types to test
TYPES=(
    LoginRequest
    LoginResponse
    Command
    RulerDecision
    RulerStats
    Army
    Tile
    GameState
    ErrorResponse
    BaronyRequest
    BaronyResponse
)

normalize_xml() {
    # Strip leading/trailing whitespace, normalize internal whitespace
    sed 's/^[[:space:]]*//' | sed 's/[[:space:]]*$//' | tr -s '[:space:]' ' ' | sed 's/> </></g'
}

for type in "${TYPES[@]}"; do
    INPUT="$TEST_DIR/${type}.xml"
    CONVERTER="$GEN_DIR/converter-${type}"
    RESULT_DER="$TEST_DIR/${type}.result.der"
    RESULT_XML="$TEST_DIR/${type}.result.xml"

    if [ ! -f "$INPUT" ]; then
        echo "SKIP  $type (no test data at $INPUT)"
        continue
    fi

    if [ ! -f "$CONVERTER" ]; then
        echo "SKIP  $type (no converter at $CONVERTER)"
        continue
    fi

    # Step 1: XER → DER (encode)
    if ! "$CONVERTER" -p "$type" -ixer -oder "$INPUT" > "$RESULT_DER" 2>/dev/null; then
        echo "FAIL  $type (XER → DER encoding failed)"
        FAIL=$((FAIL + 1))
        ERRORS="$ERRORS\n  - $type: XER → DER encoding failed"
        continue
    fi

    # Step 2: DER → XER (decode)
    if ! "$CONVERTER" -p "$type" -iber -oxer "$RESULT_DER" > "$RESULT_XML" 2>/dev/null; then
        echo "FAIL  $type (DER → XER decoding failed)"
        FAIL=$((FAIL + 1))
        ERRORS="$ERRORS\n  - $type: DER → XER decoding failed"
        rm -f "$RESULT_DER"
        continue
    fi

    # Step 3: Compare normalized XER output with normalized input
    ORIGINAL=$(normalize_xml < "$INPUT")
    DECODED=$(normalize_xml < "$RESULT_XML")

    if [ "$ORIGINAL" = "$DECODED" ]; then
        echo "PASS  $type"
        PASS=$((PASS + 1))
    else
        echo "FAIL  $type (round-trip mismatch)"
        echo "  Expected: $ORIGINAL"
        echo "  Got:      $DECODED"
        FAIL=$((FAIL + 1))
        ERRORS="$ERRORS\n  - $type: round-trip content mismatch"
    fi

    # Clean up intermediate files
    rm -f "$RESULT_DER" "$RESULT_XML"
done

echo ""
echo "Results: $PASS passed, $FAIL failed out of ${#TYPES[@]} types"

if [ $FAIL -gt 0 ]; then
    echo -e "\nFailures:$ERRORS"
    exit 1
fi

exit 0
