# Bubble Zoom — Seeneva Fork Handoff

## What this project is
Google Play Books-style "Bubble Zoom" for western comics. Tap a speech balloon, it
expands shape-aware (following the balloon outline), navigate bubble-by-bubble in
reading order. Two pieces:

1. **Processor tool** (DONE) — a browser HTML page that runs YOLO11 ONNX models,
   detects speech balloons and panels on every comic page, and outputs a
   `.seeneva.json` sidecar file next to the comic.

2. **Seeneva fork** (YOUR JOB) — modify the open-source Seeneva Android reader to
   load our sidecar JSON instead of running its own internal TFLite detection.

---

## Repos

- Android app: https://github.com/Seeneva/seeneva-reader-android
- Native Rust lib: https://github.com/Seeneva/seeneva-lib
- The lib is a git submodule of the reader repo. All detection logic is in the lib (Rust).
- Stock app runs a TFLite model internally per-page. We replace that with a sidecar lookup.

---

## The sidecar JSON format (schema v1.0)

File is named `<comic-filename>.seeneva.json` and lives next to the comic file.
e.g. `Justice-League-Dream-Girls.cbz` → `Justice-League-Dream-Girls.cbz.seeneva.json`

All coordinates are **normalized [0.0, 1.0]** (top-left origin).
`class_id 0` = speech_balloon, `class_id 1` = panel.
Reading direction: `ltr` for western comics (left→right, top→bottom).

```json
{
  "schema_version": "1.0",
  "format": "seeneva-bubble-zoom",
  "generator": {
    "tool": "bubble-zoom-processor",
    "tool_version": "0.6.1",
    "model": "balloon: specialist yolo11m-seg; panel: manga-segment yolo11s-seg",
    "model_input_size": 1024,
    "confidence_threshold": 0.1,
    "iou_threshold": 0.45,
    "created_at": "2026-06-22T00:00:00.000Z"
  },
  "source": {
    "file_name": "Justice-League-Dream-Girls.cbz",
    "page_count": 22
  },
  "reading_direction": "ltr",
  "coordinate_space": "normalized",
  "class_map": { "0": "speech_balloon", "1": "panel" },
  "pages": [
    {
      "index": 0,
      "image_name": "page001.jpg",
      "width": 1988,
      "height": 3056,
      "objects": [
        {
          "id": 0,
          "class_id": 1,
          "class_name": "panel",
          "confidence": 0.87,
          "bbox": { "x": 0.02, "y": 0.01, "width": 0.96, "height": 0.45 },
          "polygon": [[0.02,0.01],[0.98,0.01],[0.98,0.46],[0.02,0.46]],
          "panel_id": null,
          "order_index": 0
        },
        {
          "id": 2,
          "class_id": 0,
          "class_name": "speech_balloon",
          "confidence": 0.76,
          "bbox": { "x": 0.1, "y": 0.05, "width": 0.18, "height": 0.09 },
          "polygon": [[0.1,0.05],[0.14,0.04],[0.28,0.05],[0.28,0.14],[0.1,0.14]],
          "panel_id": 0,
          "order_index": 0
        }
      ]
    }
  ]
}
```

Key fields per object:
- `bbox` — bounding box, top-left x/y + width/height, normalized
- `polygon` — outline of the balloon/panel as [[x,y],...] normalized points
- `panel_id` — id of the containing panel (null if orphan or if object IS a panel)
- `order_index` — reading order rank among speech_balloons (0 = first to read)

---

## What to build in the Seeneva fork

### Goal
When Seeneva opens a comic, before running its internal TFLite detection, check for a
`.seeneva.json` sidecar. If found and valid, use our pre-computed detections for ALL
pages. If not found, fall back to the existing TFLite pipeline unchanged.

### Where the detection pipeline lives (Rust, seeneva-lib)
The native lib is called per-page from the Android app. It returns speech balloon
coordinates and panel coordinates that the app uses for zoom and reading-order
navigation. That return type / interface is what we need to satisfy using sidecar data
instead of TFLite inference.

Start by reading seeneva-lib to find:
- The function(s) called by the Android app to trigger detection on a page
- The Rust struct(s) that hold detected object coordinates (bounding box + polygon)
- Where TFLite inference actually runs

Then add a parallel code path:
1. Accept the comic file path as context (the lib already has it)
2. Construct the sidecar path: `<comic_path>.seeneva.json`
3. If the file exists, parse it (serde_json)
4. For the requested page index, look up `pages[index].objects`
5. Convert normalized coords back to pixel coords using the page's `width`/`height`
6. Return those detections in the same struct the TFLite path returns
7. If sidecar missing or parse fails, fall through to TFLite as normal

### Coordinate conversion (normalized → pixels)
```
pixel_x      = bbox.x      * page_width
pixel_y      = bbox.y      * page_height
pixel_width  = bbox.width  * page_width
pixel_height = bbox.height * page_height
polygon_px   = [[x * page_width, y * page_height] for [x, y] in polygon]
```

### Android app side
Ideally no changes needed — the app just receives coordinates the same way.
If the detection result struct needs a new "source" field (sidecar vs tflite),
add it but make it optional/ignored by existing UI code.

### Reading order
`objects` where `class_id == 0` (speech_balloon) are already sorted by `order_index`
ascending. That is the correct tap-to-navigate order. Panels (`class_id == 1`) are
sorted by `order_index` for panel-level navigation if Seeneva supports it.

---

## Class mapping
| class_id | class_name     | Seeneva equivalent     |
|----------|---------------|------------------------|
| 0        | speech_balloon | speech balloon (zoom target) |
| 1        | panel         | panel frame            |

The processor currently uses 0.1 confidence threshold (found empirically to give
best western comic recall). The sidecar already has filtered detections baked in —
no threshold logic needed in Seeneva.

---

## What the processor tool produces (for testing)

`bubble-zoom-processor.html` — open in any browser, load two ONNX model files,
drop a CBZ/CBR, downloads the `.seeneva.json`. Models not included (trained
separately with Ultralytics YOLO11-seg). The tool is standalone, no server needed.

---

## Things NOT done yet (out of scope for this handoff)
- Caption/narration box detection (rectangular boxes, not speech balloons) — future
- Double-page spread tiling for very wide pages — future
- WebGPU acceleration in the processor — future
- Panel specialist model (panels currently detected by a manga-trained model,
  western panel recall is acceptable but not perfect) — future training round

---

## Suggested first steps for Claude Code
1. Clone seeneva-reader-android (with --recurse-submodules for seeneva-lib)
2. Read seeneva-lib's detection entry point and return structs
3. Add serde_json as a dependency if not already present
4. Implement the sidecar loader as a separate module: `src/sidecar.rs` or similar
5. Wire it into the detection entry point with the fallback logic
6. Build and run on an emulator or device with a test CBZ + its .seeneva.json sidecar
