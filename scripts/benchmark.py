#!/usr/bin/env python3
"""Repeat existing PCARP commands and extract phase timings/counts (Linux/WSL, Python 3.9+).

Run from the PCARP root:
  python3 scripts/benchmark.py --case 'Identity=YOUR_WORKING_COMMAND' --runs 10
  python3 scripts/benchmark.py --cases benchmark-cases.json --runs 10 --warmups 2
  python3 scripts/benchmark.py --cases scripts/benchmark-survey.json --dry-run
Parse existing logs without executing anything:
  python3 scripts/benchmark.py --parse run1.log run2.log

The cases JSON maps names to shell commands, or to {"command": ..., "input_dir": ..., "model_dir": ...}.
Commands should run MAB -> MVIS -> Tulip, using the same existing MOP input.
They run sequentially in bash, exactly as supplied. No compilation or cleanup is added.
Optional input_dir/model_dir provide XMI counts outside the measured interval.
"""

import argparse
import csv
import datetime as dt
import json
import os
from pathlib import Path
import platform
import random
import re
import resource
import signal
import statistics
import subprocess
import sys
import tempfile
import threading
import time
import xml.etree.ElementTree as ET

PHASES = ("mab", "mvis", "tulip")
MODEL_NAMES = ("type", "assembly", "deployment", "execution", "statistics", "source")
ANSI = re.compile(r"\x1b\[[0-?]*[ -/]*[@-~]")
COUNT_LABELS = {
    "ComponentTypes": "component_types", "AssemblyComponents": "assembly_components",
    "DeployedComponents": "deployed_components", "OperationTypes": "operation_types",
    "AssemblyOperations": "assembly_operations", "DeployedOperations": "deployed_operations",
    "Invocations": "invocations", "OperationDataflows": "operation_dataflows",
    "StorageDataflows": "storage_dataflows", "Statistics entries": "statistics_entries",
    "Source entries": "source_entries", "Invocation call sum": "invocation_calls",
}
FIELDS = ["case", "run", "warmup", "status", "exit_code", "tulip_mode", "workflow_real_s",
          "workflow_user_s", "workflow_sys_s", "phases_real_s"]
FIELDS += [f"{phase}_{metric}_s" for phase in PHASES for metric in ("real", "user", "sys")]
FIELDS += [f"{phase}_attempts" for phase in PHASES]
FIELDS += [f"{prefix}_{name}" for prefix in ("original", "final") for name in COUNT_LABELS.values()]
FIELDS += ["original_model_bytes", "model_bytes", "reported_displayed_edges", "consistency_errors", "log", "warnings"]


def seconds(value):
    """Accept Bash time's 0m1.234s and TIMEFORMAT=%R; tolerate decimal commas."""
    value = value.strip().replace(",", ".")
    if re.fullmatch(r"\d+(?:\.\d+)?", value):
        return float(value)
    match = re.fullmatch(r"(?:(\d+)h)?(?:(\d+)m)?(\d+(?:\.\d+)?)s", value)
    if not match:
        raise ValueError(f"Unsupported time value: {value}")
    hours, minutes, secs = match.groups()
    return int(hours or 0) * 3600 + int(minutes or 0) * 60 + float(secs)


def parse_log(text):
    """Do not infer durations from Java timestamps or confuse input/output counts."""
    row, warnings, blocks = {}, [], {phase: [] for phase in PHASES}
    phase, block, section = None, None, None
    originals, finals, saved_result = {}, {}, {}
    saw_original = False
    fallback, skipped, requested_bundling = False, False, False
    errors = []
    for line in ANSI.sub("", text).splitlines():
        line, low = line.strip(), line.strip().lower()
        explicit = re.fullmatch(r"BENCH_PHASE=(MAB|MVIS|TULIP|OTHER|END)", line)
        next_phase = None
        if explicit:
            next_phase = explicit[1].lower()
        elif "abstracting models with mab" in low or "running mab" in low:
            next_phase = "mab"
        elif "running mvis" in low:
            next_phase = "mvis"
        elif "visualizing graph" in low or "trying visualization" in low or "retrying without edge bundling" in low:
            next_phase = "tulip"
        elif any(value in low for value in ("running dar", "running sar", "merging models with mop")):
            next_phase = "other"
            warnings.append("DAR/SAR/MOP present: workflow time is not abstraction-only")
        if next_phase:
            phase, block = next_phase, None

        fallback |= "retrying without edge bundling" in low
        skipped |= "skipping edge bundling" in low or "edge bundling disabled" in low
        requested_bundling |= "trying visualization with edge bundling" in low
        if "ORIGINAL MODEL STATE" in line:
            section = "original_ignored" if saw_original else "original"
            saw_original = True
        elif "COMPLETE MODEL VALIDATION" in line:
            section, finals = "final", {}  # Composition: use the last completed model state.
        elif line.startswith("====="):
            section = None
        count = re.fullmatch(r"([^:]+):\s*(\d+)", line)
        if count:
            label, value = count[1].strip(), int(count[2])
            if label in COUNT_LABELS and section in ("original", "final"):
                (originals if section == "original" else finals)[COUNT_LABELS[label]] = value
            elif label == "Consistency errors":
                errors.append(value)
            elif label == "Displayed edges":
                row["reported_displayed_edges"] = value

        timing = re.fullmatch(r"(real|user|sys)\s+(\S+)", line)
        if timing:
            if phase not in blocks:
                warnings.append("Unassigned timing block; use BENCH_PHASE=MAB/MVIS/TULIP markers")
                continue
            metric = timing[1]
            if metric == "real":
                block = {}
                blocks[phase].append(block)
            if block is not None:
                try:
                    block[metric] = seconds(timing[2])
                except ValueError as error:
                    warnings.append(str(error))

        footer = re.fullmatch(r"BENCH_EXIT_CODE=(-?\d+)", line)
        if footer:
            row["exit_code"] = int(footer[1])
        if line.startswith("BENCH_RESULT_JSON="):
            saved_result = json.loads(line.partition("=")[2])

    row.update({f"original_{key}": value for key, value in originals.items()})
    row.update({f"final_{key}": value for key, value in finals.items()})
    row["tulip_mode"] = "fallback" if fallback else "skipped" if skipped else "requested" if requested_bundling else "as_configured"
    if errors:
        row["consistency_errors"] = max(errors)
    for phase, attempts in blocks.items():
        row[f"{phase}_attempts"] = len(attempts)
        expected = 2 if phase == "tulip" and fallback else 1
        if len(attempts) != expected:
            warnings.append(f"{phase}: expected {expected} time block(s), found {len(attempts)}; phase time left blank")
            continue
        for metric in ("real", "user", "sys"):
            if all(metric in attempt for attempt in attempts):
                row[f"{phase}_{metric}_s"] = round(sum(attempt[metric] for attempt in attempts), 6)
    if all(f"{phase}_real_s" in row for phase in PHASES):
        row["phases_real_s"] = round(sum(row[f"{phase}_real_s"] for phase in PHASES), 6)
    if not finals:
        warnings.append("No final counts printed (normal for Identity); optionally supply model_dir")
    row["warnings"] = "; ".join(dict.fromkeys(warnings))
    # Preserve measured process times and warm-up labels when re-importing our own logs.
    row.update({key: value for key, value in saved_result.items() if key in FIELDS})
    return row


def model_counts(directory):
    """Count the known Kieker XMI containment paths, outside the measured command."""
    paths = {
        "type": {"componentTypes": "component_types", "componentTypes/value/providedOperations": "operation_types"},
        "assembly": {"components": "assembly_components", "components/value/operations": "assembly_operations"},
        "deployment": {"contexts/value/components": "deployed_components",
                       "contexts/value/components/value/operations": "deployed_operations"},
        "execution": {"invocations": "invocations", "operationDataflows": "operation_dataflows", "storageDataflows": "storage_dataflows"},
        "statistics": {"statistics": "statistics_entries"}, "source": {"sources": "source_entries"},
    }
    result = {"model_bytes": 0}
    for name, mapping in paths.items():
        path, stack = directory / f"{name}-model.xmi", []
        result["model_bytes"] += path.stat().st_size
        result.update({f"final_{value}": 0 for value in mapping.values()})
        for event, element in ET.iterparse(path, events=("start", "end")):
            if event == "start":
                stack.append(element.tag.rsplit("}", 1)[-1])
                key = mapping.get("/".join(stack[1:]))
                if key:
                    result[f"final_{key}"] += 1
            else:
                stack.pop()
                element.clear()
    return result


def check_inputs(cases, cwd):
    """Check all MOP inputs before starting any command; count each input only once."""
    errors, inputs, cache = [], {}, {}
    for name, case in cases.items():
        for key in ("input_dir", "model_dir"):
            if key in case and (not isinstance(case[key], str) or not case[key].strip()):
                errors.append(f"{name}: {key} must be a non-empty path")
        if case.get("input_dir") and isinstance(case["input_dir"], str):
            folder = (cwd / case["input_dir"]).resolve()
            inputs[name] = folder
            missing = [f"{kind}-model.xmi" for kind in MODEL_NAMES if not (folder / f"{kind}-model.xmi").is_file()]
            if missing:
                errors.append(f"{name}: missing files in {folder}: {', '.join(missing)}")
    for name, case in cases.items():
        if case.get("model_dir") and isinstance(case["model_dir"], str):
            output = (cwd / case["model_dir"]).resolve()
            if any(folder == output or output in folder.parents for folder in inputs.values()):
                errors.append(f"{name}: output directory contains a configured MOP input")
    if errors:
        raise ValueError("\n".join(errors))
    for folder in dict.fromkeys(inputs.values()):
        try:
            counts = model_counts(folder)
            cache[folder] = {key.replace("final_", "original_", 1): value for key, value in counts.items() if key != "model_bytes"}
            cache[folder]["original_model_bytes"] = counts["model_bytes"]
        except (OSError, ValueError, ET.ParseError) as error:
            raise ValueError(f"Cannot read MOP input {folder}: {error}") from error
    return {name: cache[folder] for name, folder in inputs.items()}


def stop(process):
    """Stop the whole benchmark command group, including Java/Python children."""
    try:
        os.killpg(process.pid, signal.SIGTERM)
        process.wait(timeout=3)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGKILL)
        process.wait()
    except ProcessLookupError:
        process.wait()


def execute(case, command, log, cwd, timeout, run, warmup):
    # Capture both streams in one file. Formatting/logging work still belongs to the timing.
    environment = dict(os.environ, LC_ALL="C", TIMEFORMAT="real %3R\nuser %3U\nsys %3S")
    before = resource.getrusage(resource.RUSAGE_CHILDREN)
    interrupted, status = False, "ok"
    with log.open("w", encoding="utf-8") as output:
        start = time.perf_counter()
        process = subprocess.Popen(["bash", "-c", command], cwd=cwd, env=environment,
                                   stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
        expired = threading.Event()
        def expire():
            if process.poll() is None:
                expired.set()
                try:
                    os.killpg(process.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
        timer = threading.Timer(timeout, expire)
        timer.daemon = True
        timer.start()
        try:
            # Blocking wait avoids timeout polling adding up to 50 ms to short runs.
            code = process.wait()
            status = "timeout" if expired.is_set() else "failed" if code != 0 else "ok"
        except KeyboardInterrupt:
            stop(process)
            code, status, interrupted = process.returncode, "interrupted", True
        finally:
            timer.cancel()
        elapsed = time.perf_counter() - start
        output.write(f"\nBENCH_EXIT_CODE={code}\n")
    after = resource.getrusage(resource.RUSAGE_CHILDREN)
    row = parse_log(log.read_text(encoding="utf-8", errors="replace"))
    if row.get("consistency_errors", 0) > 0 and status == "ok":
        status = "invalid_model"
    row.update(case=case, run=run, warmup=int(warmup), status=status, exit_code=code, log=str(log),
               workflow_real_s=round(elapsed, 6), workflow_user_s=round(after.ru_utime - before.ru_utime, 6),
               workflow_sys_s=round(after.ru_stime - before.ru_stime, 6))
    return row, interrupted


def write_csv(path, fields, rows):
    with path.open("w", encoding="utf-8-sig", newline="") as output:
        writer = csv.DictWriter(output, fieldnames=fields, delimiter=";", extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)


def summarize(rows):
    """Use complete successful runs; never pool successful bundling and fallback runs."""
    groups, result = {}, []
    for row in rows:
        if not row["warmup"] and row["status"] == "ok":
            groups.setdefault((row["case"], row["tulip_mode"]), []).append(row)
    metrics = [key for key in FIELDS if key.endswith("_s") or key.startswith(("final_", "original_"))]
    metrics += ["model_bytes", "reported_displayed_edges"]
    for (case, mode), samples in groups.items():
        for metric in metrics:
            values = [row[metric] for row in samples if metric in row]
            if not values:
                continue
            quartiles = statistics.quantiles(values, n=4, method="inclusive") if len(values) > 1 else [values[0]] * 3
            result.append(dict(case=case, tulip_mode=mode, metric=metric, n=len(values),
                               median=statistics.median(values), q1=quartiles[0], q3=quartiles[2],
                               minimum=min(values), maximum=max(values)))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--case", action="append", default=[], metavar="NAME=COMMAND")
    parser.add_argument("--cases", type=Path, help="JSON object mapping variant names to commands")
    parser.add_argument("--dry-run", action="store_true", help="Check configured inputs and print commands without running them")
    parser.add_argument("--parse", nargs="+", type=Path, metavar="LOG", help="Only parse existing logs")
    parser.add_argument("--assume-success", action="store_true", help="Include imported logs without an exit code in summaries")
    parser.add_argument("--import-case", help="Group imported external logs as repetitions of one variant")
    parser.add_argument("--cwd", type=Path, default=Path.cwd(), help="PCARP root (default: current directory)")
    parser.add_argument("--out", type=Path, default=Path("benchmark-results"), help="Parent for a new results directory")
    parser.add_argument("--runs", type=int, default=10)
    parser.add_argument("--warmups", type=int, default=2)
    parser.add_argument("--timeout", type=float, default=1800, help="Seconds per whole command")
    parser.add_argument("--seed", type=int, default=42, help="Shuffle order only, not Tulip/Leiden randomness")
    args = parser.parse_args()
    if args.runs < 1 or args.warmups < 0 or args.timeout <= 0:
        parser.error("runs >= 1, warmups >= 0 and timeout > 0 required")
    cases = {}
    if args.cases:
        loaded = json.loads(args.cases.read_text(encoding="utf-8-sig"))
        if not isinstance(loaded, dict):
            parser.error("The cases file must be a JSON object")
        cases.update(loaded)
    for item in args.case:
        name, separator, command = item.partition("=")
        if not separator or not name.strip() or not command.strip() or name in cases:
            parser.error("Use distinct --case 'NAME=COMMAND' entries")
        cases[name] = command
    if bool(args.parse) == bool(cases):
        parser.error("Supply --case/--cases OR --parse logs")
    if args.parse and args.dry_run:
        parser.error("--dry-run applies to --case/--cases, not --parse")
    for name, case in list(cases.items()):
        cases[name] = {"command": case} if isinstance(case, str) else case
        if not isinstance(cases[name], dict) or not isinstance(cases[name].get("command"), str) or not cases[name]["command"].strip():
            parser.error(f"Invalid command for {name}")
    cwd = args.cwd.resolve()
    if not cwd.is_dir():
        parser.error(f"Working directory does not exist: {cwd}")
    try:
        input_counts = check_inputs(cases, cwd)
    except ValueError as error:
        parser.error(str(error))
    if cases:
        print(f"{len(cases)} variants: {len(cases) * args.warmups} warm-ups + {len(cases) * args.runs} measured runs", flush=True)
    if args.dry_run:
        for name, case in cases.items():
            print(f"\n{name}\n  {case['command']}")
            if case.get("model_dir"):
                print(f"  Output counts: {cwd / case['model_dir']}")
        print("Inputs checked; no commands executed and no results written.")
        return 0
    args.out.mkdir(parents=True, exist_ok=True)
    destination = Path(tempfile.mkdtemp(prefix=dt.datetime.now().strftime("%Y%m%d-%H%M%S-"), dir=args.out)).resolve()
    (destination / "metadata.json").write_text(json.dumps(dict(created=dt.datetime.now(dt.timezone.utc).isoformat(),
        platform=platform.platform(), python=sys.version, cwd=str(cwd), cases=cases, runs=args.runs, warmups=args.warmups,
        timeout=args.timeout, order_seed=args.seed, imported_logs=bool(args.parse), assume_success=args.assume_success,
        measurement="Fresh processes, CLI wall/CPU time; logs included; analysis outside timed interval"), indent=2), encoding="utf-8")
    rows, rng, interrupted = [], random.Random(args.seed), False
    print(f"Results: {destination}", flush=True)
    if args.parse:
        for index, path in enumerate(args.parse, 1):
            row = parse_log(path.read_text(encoding="utf-8", errors="replace"))
            code = row.get("exit_code")
            status = row.get("status", "ok" if code == 0 or code is None and args.assume_success else "unknown_exit" if code is None else "failed")
            if row.get("consistency_errors", 0) > 0:
                status = "invalid_model"
            row.update(case=args.import_case or row.get("case", path.stem), run=row.get("run", index),
                       warmup=row.get("warmup", 0), status=status, log=str(path.resolve()))
            rows.append(row)
    else:
        for warmup, repetitions in ((True, args.warmups), (False, args.runs)):
            for run in range(1, repetitions + 1):
                order = list(cases)
                rng.shuffle(order)
                for name in order:
                    print(f"{'Warm-up' if warmup else 'Run'} {run}/{repetitions}: {name}", flush=True)
                    log = destination / f"{len(rows) + 1:04d}.log"
                    started = time.time()
                    row, interrupted = execute(name, cases[name]["command"], log, cwd, args.timeout, run, warmup)
                    for key, value in input_counts.get(name, {}).items():
                        if key in row and row[key] != value:
                            row["warnings"] += f"; {key}: log={row[key]}, input XMI={value}"
                            if row["status"] == "ok":
                                row["status"] = "counts_error"
                        row[key] = value
                    if row["status"] == "ok" and cases[name].get("model_dir"):
                        try:
                            folder = cwd / cases[name]["model_dir"]
                            files = [folder / f"{kind}-model.xmi" for kind in MODEL_NAMES]
                            if any(file.stat().st_mtime < started - 2 for file in files):
                                raise ValueError("model_dir contains old files; confirm the output path")
                            counts = model_counts(folder)
                            if any(key in row and row[key] != value for key, value in counts.items()):
                                raise ValueError("Log counts disagree with XMI counts")
                            row.update(counts)
                            row["warnings"] = row["warnings"].replace("No final counts printed (normal for Identity); optionally supply model_dir", "").strip("; ")
                        except (OSError, ValueError, ET.ParseError) as error:
                            row["status"] = "counts_error"
                            row["warnings"] += f"; {error}"
                    rows.append(row)
                    with log.open("a", encoding="utf-8") as output:
                        output.write("\nBENCH_RESULT_JSON=" + json.dumps(row) + "\n")
                    write_csv(destination / "runs.csv", FIELDS, rows)  # Save progress after every run.
                    print(f"  {row['status']}: {row['workflow_real_s']:.3f}s ({row['tulip_mode']})", flush=True)
                    if row["warnings"]:
                        print(f"  Note: {row['warnings']}", flush=True)
                    if interrupted:
                        break
                if interrupted:
                    break
            if interrupted:
                break
    write_csv(destination / "runs.csv", FIELDS, rows)
    summary = summarize(rows)
    write_csv(destination / "summary.csv", ["case", "tulip_mode", "metric", "n", "median", "q1", "q3", "minimum", "maximum"], summary)
    for row in summary:
        if row["metric"] == "workflow_real_s":
            print(f"{row['case']} ({row['tulip_mode']}): median {row['median']:.3f}s; n={row['n']}")
    rejected = sum(row["status"] != "ok" for row in rows)
    print(f"Saved runs.csv, summary.csv, metadata.json and logs. Excluded unsuccessful/unverified rows: {rejected}")
    return 130 if interrupted else 1 if rejected else 0


if __name__ == "__main__":
    raise SystemExit(main())
