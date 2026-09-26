#!/bin/bash

# Abort on undefined vars, pipefail, but allow manual exit handling
set -u
set -o pipefail

# Colors
RED='\033[31m'
GREEN='\033[32m'
RESET='\033[0m'

# Usage check
if [ "$#" -ne 7 ]; then
  echo -e "${RED}Usage: $0 <dar-data-dir> <sar-data-dir> <outdir-name> <abstraction-level> <rename from> <rename to> <optional argument>${RESET}"
  exit 1
fi

DAR_INPUT_DIR="$1"
SAR_INPUT_DIR="$2"
NAME="bin/$3"
ABSTRACTION_LEVEL="$4"
FRST_OPTIONAL_ARG="$5"
SCND_OPTIONAL_ARG="$6"
THRD_OPTIONAL_ARG="$7"

DAR_OUTPUT_DIR="$NAME/dar_model"
SAR_OUTPUT_DIR="$NAME/sar_model"
MOP_OUTPUT_DIR="$NAME/mop_model"
MAB_OUTPUT_DIR="$NAME/mab_model"
MVIS_COMBINED_DIR="$NAME/mvis_combined"

# Clean and prepare directories
rm -rf "$NAME"/*
mkdir -p "$DAR_OUTPUT_DIR" "$SAR_OUTPUT_DIR" "$MOP_OUTPUT_DIR" "$MAB_OUTPUT_DIR" "$MVIS_COMBINED_DIR"
if [ $? -ne 0 ]; then
  echo -e "${RED}Directory creation failed. Exiting.${RESET}"
  exit 1
fi

echo -e "${GREEN}✔ Directories ready.${RESET}"

# Run DAR
echo -e "${GREEN}▶ Running DAR...${RESET}"
time tools/oceandsl-tools/bin/dar \
  -l dynamic \
  -c \
  -o "$DAR_OUTPUT_DIR" \
  -s java \
  -m java-class-mode \
  -E "" \
  -i "$DAR_INPUT_DIR"
if [ $? -ne 0 ]; then
  echo -e "${RED}DAR command failed. Exiting.${RESET}"
  exit 1
fi

# Run SAR
echo -e "${GREEN}▶ Running SAR...${RESET}"
time tools/oceandsl-tools/bin/sar \
  -l static \
  -o "$SAR_OUTPUT_DIR" \
  -m module-mode \
  -g both \
  -E "" \
  -i "$SAR_INPUT_DIR"
if [ $? -ne 0 ]; then
  echo -e "${RED}SAR command failed. Exiting.${RESET}"
  exit 1
fi

# Convert model
echo -e "${GREEN}▶ Converting model...${RESET}"
python3 python/convert_sar2dar_model.py "$SAR_OUTPUT_DIR/type-model.xmi"
if [ $? -ne 0 ]; then
  echo -e "${RED}Conversion failed. Exiting.${RESET}"
  exit 1
fi

# Merge models
# Somehow, mop always fails but still finishes
echo -e "${GREEN}▶ Merging models with MOP...${RESET}"
time tools/oceandsl-tools/bin/mop \
  -i "$DAR_OUTPUT_DIR" "$SAR_OUTPUT_DIR" \
  -o "$MOP_OUTPUT_DIR" \
  -e "" \
  merge

# Abstract MOP model based on abstraction level
echo -e "${GREEN}▶ Abstracting models with MAB...${RESET}"
time java \
  -cp "tools/abstraction/out:tools/oceandsl-tools/lib/*" \
  tools.abstraction.AbstractionMain \
  -i "$MOP_OUTPUT_DIR" \
  -o "$MAB_OUTPUT_DIR" \
  -m "$ABSTRACTION_LEVEL" "$FRST_OPTIONAL_ARG" "$SCND_OPTIONAL_ARG" "$THRD_OPTIONAL_ARG"
  if [ $? -ne 0 ]; then
    echo -e "${RED}Conversion failed. Exiting.${RESET}"
    exit 1
  fi


# Convert model to graph description
echo -e "${GREEN}▶ Running MVIS...${RESET}"
time tools/oceandsl-tools/bin/mvis \
  -i "$MAB_OUTPUT_DIR" \
  -m add-nodes \
  -o "$MVIS_COMBINED_DIR" \
  -s all \
  -g dot-component
if [ $? -ne 0 ]; then
  echo -e "${RED}MVIS command failed. Exiting.${RESET}"
  exit 1
fi

# Visualize graph
echo -e "${GREEN}▶ Visualizing graph...${RESET}"
time env PYTHONPATH="$TULIP_ROOT/lib/tulip/python:$HOME/pcarp-tulip-pydeps:${PYTHONPATH:-}" LD_LIBRARY_PATH="$TULIP_ROOT/lib:$TULIP_ROOT/lib/tulip" TULIP_PLUGINS_PATH="$TULIP_ROOT/lib/tulip/plugins:$TULIP_ROOT/lib/tulip/python/tulip/plugins" TULIP_PLUGINS_DIR="$TULIP_ROOT/lib/tulip/plugins:$TULIP_ROOT/lib/tulip/python/tulip/plugins" "$TULIP_ROOT/bin/python3" tools/grouped-graph-visualizer/main.py -i "$MVIS_COMBINED_DIR/mab_model-component.dot" -o "$MVIS_COMBINED_DIR/output.svg" -m "tulip"
if [ $? -ne 0 ]; then
  echo -e "${RED}Visualization failed. Exiting.${RESET}"
  exit 1
fi

cd ../..

echo -e "${GREEN}Done! Output PDF ready at: $MVIS_COMBINED_DIR/output.pdf${RESET}"
