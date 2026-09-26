#!/bin/bash

# Abort on undefined vars, pipefail, but allow manual exit handling
set -u
set -o pipefail

# ============================================================
# Colors
# ============================================================

RED='\033[31m'
GREEN='\033[32m'
YELLOW='\033[33m'
RESET='\033[0m'


# ============================================================
# Helper functions
# ============================================================

print_usage() {
  echo -e "${YELLOW}Usage:${RESET}"
  echo
  echo "Full pipeline:"
  echo "  $0 <dar-data-dir> <sar-data-dir> <outdir-name> <abstraction-level> <arg1> <arg2> <arg3> <arg4>"
  echo
  echo "MOP only:"
  echo "  $0 --mop-only <dar-data-dir> <sar-data-dir> <outdir-name>"
  echo
  echo "Abstraction only:"
  echo "  $0 --abstraction-only <input-model-dir> <outdir-name> <abstraction-level> <arg1> <arg2> <arg3> <arg4>"
  echo
}


run_abstraction() {
  local INPUT_DIR="$1"
  local OUTPUT_DIR="$2"
  local LEVEL="$3"
  local ARG1="$4"
  local ARG2="$5"
  local ARG3="$6"
  local ARG4="$7"

  echo -e "${GREEN}▶ Abstracting models with MAB...${RESET}"
  echo "  Input:  $INPUT_DIR"
  echo "  Output: $OUTPUT_DIR"
  echo "  Level:  $LEVEL"
  echo

  time java \
    -cp "tools/abstraction/out:tools/oceandsl-tools/lib/*" \
    tools.abstraction.AbstractionMain \
    -i "$INPUT_DIR" \
    -o "$OUTPUT_DIR" \
    -m "$LEVEL" "$ARG1" "$ARG2" "$ARG3" "$ARG4"

  if [ $? -ne 0 ]; then
    echo -e "${RED}Abstraction failed. Exiting.${RESET}"
    exit 1
  fi

  echo -e "${GREEN}✔ Abstraction finished.${RESET}"
}


run_tulip_visualization() {
  local INPUT_FILE="$1"
  local OUTPUT_FILE="$2"
  shift 2

  env \
    PYTHONPATH="$TULIP_ROOT/lib/tulip/python:$HOME/pcarp-tulip-pydeps:${PYTHONPATH:-}" \
    LD_LIBRARY_PATH="$TULIP_ROOT/lib:$TULIP_ROOT/lib/tulip" \
    TULIP_PLUGINS_PATH="$TULIP_ROOT/lib/tulip/plugins:$TULIP_ROOT/lib/tulip/python/tulip/plugins" \
    TULIP_PLUGINS_DIR="$TULIP_ROOT/lib/tulip/plugins:$TULIP_ROOT/lib/tulip/python/tulip/plugins" \
    "$TULIP_ROOT/bin/python3" \
    tools/grouped-graph-visualizer/main.py \
    -i "$INPUT_FILE" \
    -o "$OUTPUT_FILE" \
    -m "tulip" \
    "$@"
}


# ============================================================
# Argument validation
# ============================================================

if [ "$#" -lt 1 ]; then
  echo -e "${RED}Missing arguments.${RESET}"
  echo
  print_usage
  exit 1
fi

if [ "$1" = "--mop-only" ]; then
  if [ "$#" -ne 4 ]; then
    echo -e "${RED}Invalid number of arguments for MOP-only mode.${RESET}"
    echo
    print_usage
    exit 1
  fi

elif [ "$1" = "--abstraction-only" ]; then
  if [ "$#" -ne 8 ]; then
    echo -e "${RED}Invalid number of arguments for abstraction-only mode.${RESET}"
    echo
    print_usage
    exit 1
  fi

elif [ "$#" -ne 8 ]; then
  echo -e "${RED}Invalid number of arguments.${RESET}"
  echo
  print_usage
  exit 1
fi


# ============================================================
# ABSTRACTION-ONLY MODE
# ============================================================

if [ "$1" = "--abstraction-only" ]; then

  INPUT_MODEL_DIR="$2"
  NAME="bin/$3"

  ABSTRACTION_LEVEL="$4"
  FRST_OPTIONAL_ARG="$5"
  SCND_OPTIONAL_ARG="$6"
  THRD_OPTIONAL_ARG="$7"
  FORTH_OPTIONAL_ARG="$8"

  MAB_OUTPUT_DIR="$NAME/mab_model"
  MVIS_COMBINED_DIR="$NAME/mvis_combined"

  echo
  echo -e "${GREEN}============================================================${RESET}"
  echo -e "${GREEN}                ABSTRACTION-ONLY MODE${RESET}"
  echo -e "${GREEN}============================================================${RESET}"
  echo

  if [ ! -d "$INPUT_MODEL_DIR" ]; then
    echo -e "${RED}Input model directory does not exist:${RESET}"
    echo "  $INPUT_MODEL_DIR"
    exit 1
  fi

  # Keep the MOP input untouched.
  rm -rf "$MAB_OUTPUT_DIR" "$MVIS_COMBINED_DIR"

  mkdir -p \
    "$MAB_OUTPUT_DIR" \
    "$MVIS_COMBINED_DIR"

  if [ $? -ne 0 ]; then
    echo -e "${RED}Could not create output directories. Exiting.${RESET}"
    exit 1
  fi


  # ============================================================
  # MAB
  # ============================================================

  run_abstraction \
    "$INPUT_MODEL_DIR" \
    "$MAB_OUTPUT_DIR" \
    "$ABSTRACTION_LEVEL" \
    "$FRST_OPTIONAL_ARG" \
    "$SCND_OPTIONAL_ARG" \
    "$THRD_OPTIONAL_ARG" \
    "$FORTH_OPTIONAL_ARG"


  # ============================================================
  # MVIS
  # ============================================================

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


  # ============================================================
  # GGVIS / Tulip visualization
  # ============================================================

  echo -e "${GREEN}▶ Visualizing graph...${RESET}"

  TULIP_INPUT="$MVIS_COMBINED_DIR/mab_model-component.dot"
  TULIP_OUTPUT="$MVIS_COMBINED_DIR/output.svg"
  TULIP_BUNDLED_OUTPUT="$MVIS_COMBINED_DIR/output-bundled.svg"

  echo "▶ Trying visualization with edge bundling..."

  if time run_tulip_visualization \
      "$TULIP_INPUT" \
      "$TULIP_BUNDLED_OUTPUT"; then

    mv "$TULIP_BUNDLED_OUTPUT" "$TULIP_OUTPUT"
    echo "✔ Visualization with edge bundling finished."

  else
    bundling_exit_code=$?

    echo "⚠ Edge bundling failed with exit code $bundling_exit_code."
    echo "▶ Retrying without edge bundling..."

    if time run_tulip_visualization \
        "$TULIP_INPUT" \
        "$TULIP_OUTPUT" \
        --skip-edge-bundling; then

      echo "✔ Visualization finished without edge bundling."
    else
      echo -e "${RED}Visualization failed even without edge bundling. Exiting.${RESET}"
      exit 1
    fi
  fi


  # ============================================================
  # Done
  # ============================================================

  echo
  echo -e "${GREEN}============================================================${RESET}"
  echo -e "${GREEN}          ABSTRACTION + VISUALIZATION DONE${RESET}"
  echo -e "${GREEN}============================================================${RESET}"
  echo
  echo -e "${GREEN}MAB model:${RESET}      $MAB_OUTPUT_DIR"
  echo -e "${GREEN}DOT graph:${RESET}      $MVIS_COMBINED_DIR/mab_model-component.dot"
  echo -e "${GREEN}Visualization:${RESET} $MVIS_COMBINED_DIR/output.svg"
  echo

  exit 0
fi


# ============================================================
# FULL / MOP-ONLY MODE
# ============================================================

MOP_ONLY=false

if [ "$1" = "--mop-only" ]; then

  MOP_ONLY=true

  DAR_INPUT_DIR="$2"
  SAR_INPUT_DIR="$3"
  NAME="bin/$4"

else

  DAR_INPUT_DIR="$1"
  SAR_INPUT_DIR="$2"
  NAME="bin/$3"

  ABSTRACTION_LEVEL="$4"
  FRST_OPTIONAL_ARG="$5"
  SCND_OPTIONAL_ARG="$6"
  THRD_OPTIONAL_ARG="$7"
  FORTH_OPTIONAL_ARG="$8"
fi


DAR_OUTPUT_DIR="$NAME/dar_model"
SAR_OUTPUT_DIR="$NAME/sar_model"
MOP_OUTPUT_DIR="$NAME/mop_model"
MAB_OUTPUT_DIR="$NAME/mab_model"
MVIS_COMBINED_DIR="$NAME/mvis_combined"


echo
echo -e "${GREEN}============================================================${RESET}"

if [ "$MOP_ONLY" = true ]; then
  echo -e "${GREEN}                    MOP-ONLY MODE${RESET}"
else
  echo -e "${GREEN}                    FULL PIPELINE${RESET}"
fi

echo -e "${GREEN}============================================================${RESET}"
echo


# ============================================================
# Prepare directories
# ============================================================

rm -rf "$NAME"/*

mkdir -p \
  "$DAR_OUTPUT_DIR" \
  "$SAR_OUTPUT_DIR" \
  "$MOP_OUTPUT_DIR"

if [ "$MOP_ONLY" = false ]; then
  mkdir -p \
    "$MAB_OUTPUT_DIR" \
    "$MVIS_COMBINED_DIR"
fi

if [ $? -ne 0 ]; then
  echo -e "${RED}Directory creation failed. Exiting.${RESET}"
  exit 1
fi

echo -e "${GREEN}✔ Directories ready.${RESET}"


# ============================================================
# DAR
# ============================================================

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


# ============================================================
# SAR
# ============================================================

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


# ============================================================
# Convert SAR model
# ============================================================

echo -e "${GREEN}▶ Converting SAR model...${RESET}"

python3 python/convert_sar2dar_model.py \
  "$SAR_OUTPUT_DIR/type-model.xmi"

if [ $? -ne 0 ]; then
  echo -e "${RED}Conversion failed. Exiting.${RESET}"
  exit 1
fi


# ============================================================
# MOP
# ============================================================

echo -e "${GREEN}▶ Merging models with MOP...${RESET}"

# MOP may return a non-zero exit code although the merged
# model has been created. Therefore, do not exit solely based
# on the command's return code.
time tools/oceandsl-tools/bin/mop \
  -i "$DAR_OUTPUT_DIR" "$SAR_OUTPUT_DIR" \
  -o "$MOP_OUTPUT_DIR" \
  -e "" \
  merge


# ============================================================
# Stop here in MOP-only mode
# ============================================================

if [ "$MOP_ONLY" = true ]; then
  echo
  echo -e "${GREEN}============================================================${RESET}"
  echo -e "${GREEN}                   MOP MODEL READY${RESET}"
  echo -e "${GREEN}============================================================${RESET}"
  echo
  echo -e "${GREEN}MOP model:${RESET} $MOP_OUTPUT_DIR"
  echo
  exit 0
fi


# ============================================================
# MAB / Abstraction
# ============================================================

run_abstraction \
  "$MOP_OUTPUT_DIR" \
  "$MAB_OUTPUT_DIR" \
  "$ABSTRACTION_LEVEL" \
  "$FRST_OPTIONAL_ARG" \
  "$SCND_OPTIONAL_ARG" \
  "$THRD_OPTIONAL_ARG" \
  "$FORTH_OPTIONAL_ARG"


# ============================================================
# MVIS
# ============================================================

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


# ============================================================
# GGVIS / Tulip
# ============================================================

echo -e "${GREEN}▶ Visualizing graph...${RESET}"

time run_tulip_visualization \
  "$MVIS_COMBINED_DIR/mab_model-component.dot" \
  "$MVIS_COMBINED_DIR/output.svg"

if [ $? -ne 0 ]; then
  echo -e "${RED}Visualization failed. Exiting.${RESET}"
  exit 1
fi


# ============================================================
# Done
# ============================================================

echo
echo -e "${GREEN}============================================================${RESET}"
echo -e "${GREEN}                       DONE${RESET}"
echo -e "${GREEN}============================================================${RESET}"
echo
echo -e "${GREEN}MOP model:${RESET}      $MOP_OUTPUT_DIR"
echo -e "${GREEN}MAB model:${RESET}      $MAB_OUTPUT_DIR"
echo -e "${GREEN}Visualization:${RESET} $MVIS_COMBINED_DIR/output.svg"
echo