#!/usr/bin/env python3
"""Host-side evaluation of the local Gemma scale recognition with the real LiteRT-LM runtime.

Runs the pinned .litertlm model against a folder of photos whose file names carry the expected
weight (for example `dual-117g.png`). Sampler, thinking, context, and output limits match
`LiteRtScaleEngine`. CPU and host GPU (WebGPU) are available; neither is identical to the
Adreno GPU path on a phone, so this separates prompt and image questions from device runtime
questions but does not replace a device run.

Experiments:
  A  SCALE_PROMPT on the full photo (production; `--prompt-rev` selects an older revision)
  B  transcription prompt on the full photo, selection in code
  C  transcription prompt on a manual display crop (`--manual-boxes`)
  D  Gemma box_2d localization, crop, transcription prompt, selection in code

Setup (outside the repository, see docs/development/ai-scale-recognition.md):
  python3 -m venv ~/.cache/foodyou-ai-eval/venv
  ~/.cache/foodyou-ai-eval/venv/bin/pip install litert-lm-api==0.16.1 pillow
"""

import argparse
import io
import json
import re
import subprocess
import sys
import time
from pathlib import Path

import litert_lm
from litert_lm import _messages
from PIL import Image, ImageOps

REPO = Path(__file__).resolve().parent.parent
KOTLIN = REPO / "app/src/commonMain/kotlin/com/maksimowiczm/foodyou/ai/ScaleRecognition.kt"

# Patch budget of the pinned Gemma 4 export: max_num_patches 2520 at 16x16 px.
PATCH_PIXEL_BUDGET = 2520 * 16 * 16
CROP_TARGET_PIXELS = 600_000

FALLBACK_LOCATE_PROMPT = (
    "What's the bounding box of the kitchen scale's digital weight display in the image, "
    "in JSON format?"
)
FALLBACK_READINGS_PROMPT = """Transcribe every weight reading shown on the kitchen scale's digital display, from top to bottom, exactly as displayed.
Keep decimal points exactly as shown; do not round, add, or estimate. Ignore clocks, timers, buttons, and printed labels.
Return only JSON: {"readings":[{"value":"<number as shown>","unit":"g"}]}. Use "kg" only if the display shows kg; omit unit if none is shown.
Return {"readings":[]} if no digits are readable."""


def kotlin_prompt(name, fallback=None, revision=None):
    """Reads a raw-string prompt constant so the harness evaluates the shipped wording."""
    if revision:
        relative = KOTLIN.relative_to(REPO).as_posix()
        text = subprocess.run(["git", "-C", str(REPO), "show", f"{revision}:{relative}"],
                              check=True, capture_output=True, text=True).stdout
    else:
        text = KOTLIN.read_text()
    match = re.search(rf'const val {name} = """(.*?)"""', text, re.S)
    if match:
        return match.group(1)
    if fallback is None:
        raise SystemExit(f"{name} not found in {KOTLIN}")
    return fallback


# --- Response parsing (A mirrors parseScaleReading; B-D are experimental) ---------------------

def extract_json(text):
    """First JSON object or array in the response; ignores fences and trailing prose."""
    clean = text.strip()
    clean = re.sub(r"^```(?:json)?", "", clean).strip()
    decoder = json.JSONDecoder()
    for index, char in enumerate(clean):
        if char in "{[":
            try:
                return decoder.raw_decode(clean[index:])[0]
            except json.JSONDecodeError:
                continue
    return None


def exact_grams(raw, unit):
    """Returns (grams, error) using the decimal shift of parseScaleReading."""
    raw = str(raw).strip()
    if not re.fullmatch(r"[0-9]+([.,][0-9]+)?", raw):
        return None, "format"
    places = {"g": 0, "kg": 3}.get(unit)
    if places is None:
        return None, "format"
    whole, _, fraction = raw.replace(",", ".").partition(".")
    fraction = fraction.rstrip("0")
    if len(fraction) > places:
        return None, "non_whole_grams"
    digits = (whole + fraction.ljust(places, "0")).lstrip("0")
    return int(digits or "0"), None


def parse_legacy(text):
    """parseScaleReading for experiment A (strict format, as in production)."""
    clean = text.strip().removeprefix("```json").removeprefix("```").removesuffix("```").strip()
    try:
        obj = json.loads(clean)
    except json.JSONDecodeError:
        return "error_format"
    if not isinstance(obj, dict):
        return "error_format"
    if obj.get("value", 0) is None and set(obj) == {"value"}:
        return "unreadable"
    if set(obj) not in ({"value", "unit"}, {"value"}) or obj["value"] is None:
        return "error_format"
    grams, error = exact_grams(obj["value"], obj.get("unit", "g"))
    if error:
        return f"error_{error}"
    return grams if grams and grams > 0 else "error_format"


def parse_readings(text):
    obj = extract_json(text)
    if isinstance(obj, dict) and isinstance(obj.get("readings"), list):
        return obj["readings"]
    return None


def select_reading(readings):
    """selectScaleReading: drop sub-gram idle values, prefer the unique whole-gram reading."""
    if readings is None:
        return "error_format"
    candidates = []
    for reading in readings:
        if not isinstance(reading, dict) or reading.get("value") is None:
            continue
        unit = reading.get("unit") or "g"
        raw = str(reading["value"]).strip()
        if not re.fullmatch(r"[0-9]+([.,][0-9]+)?", raw) or unit not in ("g", "kg"):
            continue
        value = float(raw.replace(",", ".")) * (1000 if unit == "kg" else 1)
        if value < 1:
            continue
        candidates.append(exact_grams(raw, unit))
    if not candidates:
        return "unreadable"
    whole = [grams for grams, error in candidates if error is None]
    if len(candidates) == 1:
        grams, error = candidates[0]
        return grams if error is None else f"error_{error}"
    return whole[0] if len(whole) == 1 else "unreadable"


def parse_box(text):
    obj = extract_json(text)
    if isinstance(obj, list):
        obj = next((item for item in obj if isinstance(item, dict) and "box_2d" in item), None)
    if not isinstance(obj, dict):
        return None
    box = obj.get("box_2d")
    if not (isinstance(box, list) and len(box) == 4 and all(isinstance(v, (int, float)) for v in box)):
        return None
    ymin, xmin, ymax, xmax = (int(v) for v in box)
    if not (0 <= ymin < ymax <= 1000 and 0 <= xmin < xmax <= 1000):
        return None
    area = (ymax - ymin) * (xmax - xmin) / 1_000_000
    if area < 0.0005 or area > 0.6:
        return None
    return [ymin, xmin, ymax, xmax]


# --- Image preparation (mirrors ScalePhotoDecoder.kt) ------------------------------------------

def jpeg(image):
    out = io.BytesIO()
    image.convert("RGB").save(out, "JPEG", quality=95)
    return out.getvalue()


def full_photo(path, source_long_side=None, rotate=0):
    image = ImageOps.exif_transpose(Image.open(path)).convert("RGB")
    if rotate:
        # Simulates a photo whose orientation was not applied before inference.
        image = image.rotate(-rotate, expand=True)
    if source_long_side:
        # Emulates a larger camera file (the screenshots are smaller than real photos).
        scale = source_long_side / max(image.size)
        image = image.resize((round(image.width * scale), round(image.height * scale)), Image.LANCZOS)
    sample = 1
    while max(image.size) / sample > 2048:
        sample *= 2
    if sample > 1:
        image = image.resize((image.width // sample, image.height // sample), Image.BILINEAR)
    return image


def crop_photo(image, box, padding):
    ymin, xmin, ymax, xmax = box
    width, height = image.size
    left, right = xmin / 1000 * width, xmax / 1000 * width
    top, bottom = ymin / 1000 * height, ymax / 1000 * height
    pad_x, pad_y = (right - left) * padding, (bottom - top) * padding
    region = (
        max(0, int(left - pad_x)), max(0, int(top - pad_y)),
        min(width, int(right + pad_x + 0.5)), min(height, int(bottom + pad_y + 0.5)),
    )
    crop = image.crop(region)
    scale = (CROP_TARGET_PIXELS / (crop.width * crop.height)) ** 0.5
    size = (max(1, int(crop.width * scale)), max(1, int(crop.height * scale)))
    return crop.resize(size, Image.LANCZOS), region


# --- Inference ---------------------------------------------------------------------------------

def fit_patch_budget(image):
    """Downscales with a proper filter so the native preprocessor does not resize again."""
    scale = min(1.0, (PATCH_PIXEL_BUDGET * 0.98 / (image.width * image.height)) ** 0.5)
    if scale >= 1.0:
        return image
    return image.resize((int(image.width * scale), int(image.height * scale)), Image.LANCZOS)


def backend(name, threads):
    return litert_lm.Backend.GPU() if name == "gpu" else litert_lm.Backend.CPU(thread_count=threads)


class Gemma:
    def __init__(self, model, threads, cache, main_backend="cpu", vision_backend="cpu", activation=None):
        options = {}
        if activation:
            options["activation_data_type"] = litert_lm.ActivationDataType[activation.upper()]
        self.engine = litert_lm.Engine(
            model, backend=backend(main_backend, threads), vision_backend=backend(vision_backend, threads),
            max_num_tokens=4096, max_num_images=1, cache_dir=cache, **options,
        )

    def generate(self, image_bytes, prompt):
        conversation = self.engine.create_conversation(
            sampler_config=litert_lm.SamplerConfig(top_k=1, top_p=1.0, temperature=0.0),
            thinking_config=litert_lm.ThinkingConfig(enable_thinking=False),
            max_output_tokens=128,
        )
        try:
            message = litert_lm.Message.user(
                litert_lm.Contents.of(_messages.ImageBytes(image_bytes), prompt)
            )
            started = time.monotonic()
            response = conversation.send_message(message)
            elapsed = time.monotonic() - started
        finally:
            conversation.close()
        content = response.get("content", [])
        text = "".join(item.get("text", "") for item in content if isinstance(item, dict))
        return text, elapsed


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--model", required=True)
    parser.add_argument("--images", required=True, type=Path)
    parser.add_argument("--experiments", default="A,B,C,D")
    parser.add_argument("--manual-boxes", type=Path, help="JSON: {file name: [ymin,xmin,ymax,xmax]}")
    parser.add_argument("--padding", type=float, default=0.35, help="crop margin per side, relative to box")
    parser.add_argument("--threads", type=int, default=8)
    parser.add_argument("--cache", default=str(Path.home() / ".cache/foodyou-ai-eval/cache"))
    parser.add_argument("--save-crops", type=Path, help="write crops sent to the model here")
    parser.add_argument("--backend", choices=("cpu", "gpu"), default="cpu")
    parser.add_argument("--vision-backend", choices=("cpu", "gpu"), default="cpu")
    parser.add_argument("--prompt-rev", help="git revision whose SCALE_PROMPT experiment A uses")
    parser.add_argument("--activation", choices=("float32", "float16", "int16", "int8"))
    parser.add_argument("--source-long-side", type=int, help="upscale inputs to emulate camera files")
    parser.add_argument("--rotate", type=int, choices=(0, 90, 180, 270), default=0,
                        help="rotate inputs clockwise to simulate missing EXIF handling")
    parser.add_argument("--presize", action="store_true", help="fit full photos to the patch budget before sending")
    args = parser.parse_args()

    experiments = [e.strip().upper() for e in args.experiments.split(",") if e.strip()]
    manual = json.loads(args.manual_boxes.read_text()) if args.manual_boxes else {}
    legacy_prompt = kotlin_prompt("SCALE_PROMPT", revision=args.prompt_rev)
    locate_prompt = kotlin_prompt("SCALE_LOCATE_PROMPT", FALLBACK_LOCATE_PROMPT)
    readings_prompt = kotlin_prompt("SCALE_READINGS_PROMPT", FALLBACK_READINGS_PROMPT)
    photos = sorted(p for p in args.images.iterdir() if re.search(r"-(\d+)g\.\w+$", p.name))
    if not photos:
        sys.exit("No photos named like '*-123g.png' found.")
    Path(args.cache).mkdir(parents=True, exist_ok=True)
    if args.save_crops:
        args.save_crops.mkdir(parents=True, exist_ok=True)

    print(f"Loading {args.model} ...", flush=True)
    gemma = Gemma(args.model, args.threads, args.cache, args.backend, args.vision_backend, args.activation)
    rows = []
    for photo in photos:
        expected = int(re.search(r"-(\d+)g\.\w+$", photo.name).group(1))
        image = full_photo(photo, args.source_long_side, args.rotate)
        full = jpeg(fit_patch_budget(image) if args.presize else image)
        for experiment in experiments:
            row = {"photo": photo.name, "expected": expected, "experiment": experiment,
                   "image": f"{image.width}x{image.height}"}
            if experiment == "A":
                text, elapsed = gemma.generate(full, legacy_prompt)
                row.update(raw=text, result=parse_legacy(text), seconds=round(elapsed, 1))
            elif experiment == "B":
                text, elapsed = gemma.generate(full, readings_prompt)
                row.update(raw=text, result=select_reading(parse_readings(text)), seconds=round(elapsed, 1))
            elif experiment in ("C", "D"):
                elapsed_total = 0.0
                if experiment == "C":
                    box = manual.get(photo.name)
                    if box is None:
                        continue
                else:
                    locate_text, elapsed = gemma.generate(full, locate_prompt)
                    elapsed_total += elapsed
                    box = parse_box(locate_text)
                    row["locate_raw"] = locate_text
                row["box"] = box
                if box is None:
                    text, elapsed = gemma.generate(full, readings_prompt)
                    row["fallback"] = "full_image"
                else:
                    crop, region = crop_photo(image, box, args.padding)
                    row["crop"] = f"{crop.width}x{crop.height} from {region}"
                    if args.save_crops:
                        crop.save(args.save_crops / f"{experiment}-{photo.stem}.jpg", quality=95)
                    text, elapsed = gemma.generate(jpeg(crop), readings_prompt)
                elapsed_total += elapsed
                row.update(raw=text, result=select_reading(parse_readings(text)), seconds=round(elapsed_total, 1))
            row["ok"] = row["result"] == expected
            rows.append(row)
            print(json.dumps(row, ensure_ascii=False), flush=True)

    print("\nSummary")
    for experiment in experiments:
        selected = [r for r in rows if r["experiment"] == experiment]
        if selected:
            hits = sum(r["ok"] for r in selected)
            cells = ", ".join(f"{r['photo']}={r['result']}" for r in selected)
            print(f"  {experiment}: {hits}/{len(selected)} correct  [{cells}]")


if __name__ == "__main__":
    main()
