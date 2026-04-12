# ============================================================================
# Barony ASN.1 Build
# ============================================================================
# Compiles protocol.asn using asn1c and builds converter binaries for each
# PDU type to enable round-trip XER encoding/decoding smoke tests.
#
# Prerequisites:
#   - gcc (Linux) or clang (macOS)
#   - asn1c submodule initialized and built:
#       cd ../asn1c && test -f configure || autoreconf -iv && ./configure && make
#
# Usage:
#   make          - compile protocol.asn and build converter binaries
#   make test     - run round-trip smoke tests for all types
#   make clean    - remove generated files and binaries
# ============================================================================

ASN1C       := ../asn1c/install/bin/asn1c
ASN1C_FLAGS := -fcompound-names -fincludes-quoted
PROTOCOL    := ../protocol.asn
GEN_DIR     := generated
LIB_DIR     := lib
TEST_DIR    := test-data

CC      := gcc
CFLAGS  := -I$(GEN_DIR) -Wall -Wno-unused-variable -Wno-unused-parameter

# Detect OS
UNAME_S := $(shell uname -s)
ifeq ($(UNAME_S),Darwin)
    CC := clang
endif

# All PDU types we want to build converters for
PDU_TYPES := LoginRequest LoginResponse Command RulerDecision RulerStats \
             Army Tile GameState ErrorResponse BaronyRequest BaronyResponse

CONVERTERS := $(addprefix $(GEN_DIR)/converter-,$(PDU_TYPES))

# Non-converter source files (everything except converter-example.c)
GEN_LIB_SRCS = $(filter-out $(GEN_DIR)/converter-example.c,$(wildcard $(GEN_DIR)/*.c))

.PHONY: all clean test generate

all: generate $(CONVERTERS)

# Step 1: Generate C code from protocol.asn
generate: $(GEN_DIR)/.generated

$(GEN_DIR)/.generated: $(PROTOCOL)
	@echo "=== Generating C code from protocol.asn ==="
	@mkdir -p $(GEN_DIR)
	cd $(GEN_DIR) && $(abspath $(ASN1C)) $(ASN1C_FLAGS) $(abspath $(PROTOCOL))
	@touch $(GEN_DIR)/.generated

# Step 2: Build one converter binary per PDU type
$(GEN_DIR)/converter-%: $(GEN_DIR)/.generated
	@echo "=== Building converter for $* ==="
	cd $(GEN_DIR) && $(CC) $(CFLAGS) -I. -DPDU=$* \
		-o converter-$* converter-example.c \
		$(notdir $(filter-out $(GEN_DIR)/converter-example.c,$(wildcard $(GEN_DIR)/*.c))) \
		-lm

# Step 3: Run round-trip smoke tests
test: $(CONVERTERS) $(TEST_DIR)
	@echo "=== Running round-trip smoke tests ==="
	@./run-tests.sh
	@echo "=== All round-trip tests passed ==="

$(TEST_DIR):
	@mkdir -p $(TEST_DIR)

clean:
	rm -rf $(GEN_DIR) $(LIB_DIR) $(TEST_DIR)/*.result.xml
