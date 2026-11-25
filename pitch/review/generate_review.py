#!/usr/bin/env python3
"""
Deterministic Automated Walkthrough, Invariant Verification & Visual Review Generator
Focus Mode+ Five-Minute Pitch Experience (Independent Argon Build)

Verifies:
  - 0 console errors / 0 page errors
  - 0 failed asset loads / 0 external network requests
  - 0 raw [PLACEHOLDER] tokens visible in ?mode=recording
  - 0 horizontal or vertical viewport overflow across 1920x1080, 1600x900, 1440x900, 1366x768
  - Top-right 320x220 camera-safe zone clearance in recording mode
  - Captures opening, strongest, and ending states for all 7 scenes + rehearsal mode + 1366x768 + reduced motion
  - Builds pitch/review/contact-sheet.png (7 scenes x 3 states = 21 frames)
"""

import http.server
import json
import os
import socketserver
import threading
import time
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
from playwright.sync_api import sync_playwright

ROOT_DIR = Path(__file__).resolve().parents[2]
REVIEW_DIR = Path(__file__).resolve().parent
PORT = 8794


class QuietHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=str(ROOT_DIR), **kwargs)

    def log_message(self, format, *args):
        pass


def start_static_server():
    socketserver.TCPServer.allow_reuse_address = True
    httpd = socketserver.TCPServer(("127.0.0.1", PORT), QuietHandler)
    thread = threading.Thread(target=httpd.serve_forever, daemon=True)
    thread.start()
    return httpd


def build_contact_sheet(rows_meta, output_path):
    """Builds a 3-column x 7-row contact sheet from the 21 scene state screenshots."""
    thumb_w, thumb_h = 560, 315
    pad_x, pad_y = 24, 24
    header_h = 84
    label_h = 34
    cols = 3
    rows = len(rows_meta)

    sheet_w = pad_x + cols * (thumb_w + pad_x)
    sheet_h = header_h + rows * (thumb_h + label_h + pad_y) + pad_y

    sheet = Image.new("RGB", (sheet_w, sheet_h), (20, 15, 31))
    draw = ImageDraw.Draw(sheet)

    try:
        title_font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 26)
        label_font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 14)
        sub_font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 14)
    except Exception:
        title_font = ImageFont.load_default()
        label_font = ImageFont.load_default()
        sub_font = ImageFont.load_default()

    draw.text(
        (pad_x, 20),
        "Focus Mode+ — Independent Argon Pitch Experience · Visual Contact Sheet (7 Scenes × 3 States)",
        fill=(246, 241, 231),
        font=title_font,
    )
    draw.text(
        (pad_x, 52),
        "Columns: [1] Opening State   ·   [2] Strongest Visual Transformation State   ·   [3] Ending State   (1920 × 1080 · ?mode=recording)",
        fill=(196, 181, 253),
        font=sub_font,
    )

    for r_idx, row in enumerate(rows_meta):
        y_top = header_h + r_idx * (thumb_h + label_h + pad_y)
        for c_idx, item in enumerate(row["states"]):
            x_left = pad_x + c_idx * (thumb_w + pad_x)
            draw.text(
                (x_left, y_top),
                f"{row['scene_label']} — {item['state_label']}",
                fill=(237, 233, 254),
                font=label_font,
            )
            img = Image.open(item["path"]).convert("RGB")
            img_resized = img.resize((thumb_w, thumb_h), Image.Resampling.LANCZOS)
            sheet.paste(img_resized, (x_left, y_top + label_h - 4))
            draw.rectangle(
                [x_left - 1, y_top + label_h - 5, x_left + thumb_w, y_top + label_h - 4 + thumb_h],
                outline=(109, 40, 217),
                width=2,
            )

    sheet.save(output_path, "PNG", optimize=True)


def run_review():
    httpd = start_static_server()
    base_url = f"http://127.0.0.1:{PORT}/pitch/index.html"

    console_errors = []
    page_errors = []
    failed_requests = []
    external_requests = []
    layout_checks = []

    scene_capture_plan = [
        {
            "scene_index": 0,
            "scene_id": "scene-01-hook",
            "scene_label": "Scene 1 (Hook)",
            "states": [
                {"state_label": "Opening (Beat 1: 18-Step AI Plan)", "beat": 0, "wait_ms": 450, "file": "s01-01-opening.png"},
                {"state_label": "Strongest (Beat 2: Fracture & Distraction)", "beat": 1, "wait_ms": 650, "file": "s01-02-strongest.png"},
                {"state_label": "Ending (Beat 3: PLANUNG | UMSETZUNG)", "beat": 2, "wait_ms": 650, "file": "s01-03-ending.png"},
            ],
        },
        {
            "scene_index": 1,
            "scene_id": "scene-02-problem",
            "scene_label": "Scene 2 (Problem)",
            "states": [
                {"state_label": "Opening (Beat 1: 44% JIM-Studie 2025)", "beat": 0, "wait_ms": 450, "file": "s02-01-opening.png"},
                {"state_label": "Strongest (Beat 2: 4 Disconnected Islands)", "beat": 1, "wait_ms": 600, "file": "s02-02-strongest.png"},
                {"state_label": "Ending (Complete Problem & Source)", "beat": 1, "wait_ms": 750, "file": "s02-03-ending.png"},
            ],
        },
        {
            "scene_index": 2,
            "scene_id": "scene-03-thesis",
            "scene_label": "Scene 3 (Why Now & Thesis)",
            "states": [
                {"state_label": "Opening (Beat 1: 46,5% / 81% CHE 9/2026)", "beat": 0, "wait_ms": 450, "file": "s03-01-opening.png"},
                {"state_label": "Strongest (Beat 2: Gravitational Orbit Convergence)", "beat": 1, "wait_ms": 550, "file": "s03-02-strongest.png"},
                {"state_label": "Ending (Connected 5-Node Workflow Loop)", "beat": 1, "wait_ms": 800, "file": "s03-03-ending.png"},
            ],
        },
        {
            "scene_index": 3,
            "scene_id": "scene-04-demo",
            "scene_label": "Scene 4 (Product Demo)",
            "states": [
                {"state_label": "Opening (Beat 1: Mission Creation Layers)", "beat": 0, "wait_ms": 500, "file": "s04-01-opening.png"},
                {"state_label": "Strongest (Beat 6/7: Quick Focus → Cosmic Core)", "beat": 5, "wait_ms": 1350, "file": "s04-02-strongest.png"},
                {"state_label": "Ending (Beat 8: Result & Route Adaptation)", "beat": 7, "wait_ms": 650, "file": "s04-03-ending.png"},
            ],
        },
        {
            "scene_index": 4,
            "scene_id": "scene-05-progress",
            "scene_label": "Scene 5 (Progress & Validation)",
            "states": [
                {"state_label": "Opening (Beat 1: 5-Stage Route & GEBAUT)", "beat": 0, "wait_ms": 450, "file": "s05-01-opening.png"},
                {"state_label": "Strongest (Beat 2: GEBAUT vs. NOCH ZU BEWEISEN)", "beat": 1, "wait_ms": 600, "file": "s05-02-strongest.png"},
                {"state_label": "Ending (Clean Validation Fallback State)", "beat": 1, "wait_ms": 750, "file": "s05-03-ending.png"},
            ],
        },
        {
            "scene_index": 5,
            "scene_id": "scene-06-market",
            "scene_label": "Scene 6 (Market & Business Model)",
            "states": [
                {"state_label": "Opening (Beat 1: 2,9 Mio. Context & Domains)", "beat": 0, "wait_ms": 450, "file": "s06-01-opening.png"},
                {"state_label": "Strongest (Beat 2: FREE / PRO & Preishypothesen)", "beat": 1, "wait_ms": 600, "file": "s06-02-strongest.png"},
                {"state_label": "Ending (Complete Market & Model View)", "beat": 1, "wait_ms": 750, "file": "s06-03-ending.png"},
            ],
        },
        {
            "scene_index": 6,
            "scene_id": "scene-07-pilot",
            "scene_label": "Scene 7 (Pilot & Final CTA)",
            "states": [
                {"state_label": "Opening (Beat 1: 20 Android Users · 14 Days Route)", "beat": 0, "wait_ms": 550, "file": "s07-01-opening.png"},
                {"state_label": "Strongest (Beat 2: Cosmic Portal & QR Placeholder)", "beat": 1, "wait_ms": 700, "file": "s07-02-strongest.png"},
                {"state_label": "Ending (Die ersten echten Nutzer.)", "beat": 1, "wait_ms": 900, "file": "s07-03-ending.png"},
            ],
        },
    ]

    try:
        with sync_playwright() as p:
            browser = p.chromium.launch(headless=True)

            # 1. Primary 1920x1080 Recording Mode Walkthrough & Screenshot Capture
            context = browser.new_context(viewport={"width": 1920, "height": 1080}, device_scale_factor=1)
            page = context.new_page()

            page.on("console", lambda msg: console_errors.append(msg.text) if msg.type == "error" else None)
            page.on("pageerror", lambda err: page_errors.append(str(err)))
            page.on("requestfailed", lambda req: failed_requests.append(req.url))
            page.on(
                "request",
                lambda req: external_requests.append(req.url)
                if not req.url.startswith(f"http://127.0.0.1:{PORT}") and not req.url.startswith("data:")
                else None,
            )

            page.goto(f"{base_url}?mode=recording", wait_until="networkidle")
            page.wait_for_timeout(400)

            for row in scene_capture_plan:
                s_idx = row["scene_index"]
                for state in row["states"]:
                    b_idx = state["beat"]
                    page.evaluate(f"window.FocusPitchController.goTo({s_idx}, {b_idx}, {{ skipLock: true }})")
                    page.wait_for_timeout(state["wait_ms"])
                    out_file = REVIEW_DIR / state["file"]
                    page.screenshot(path=str(out_file))
                    state["path"] = str(out_file)

                    # Verify invariants on every captured state
                    check = page.evaluate(
                        """() => {
                        const doc = document.documentElement;
                        const activeScene = document.querySelector('.pitch-scene.is-active-scene');
                        const visibleText = activeScene ? activeScene.innerText : '';
                        const hasRawBracket = /\\[[A-Z0-9_]+\\]/.test(visibleText);
                        const hudVisible = !document.getElementById('pitchHudRoot').hidden;

                        // Check camera safe zone (top-right 320x220 at 1920x1080 -> x: 1600..1920, y: 0..220)
                        const safeX = window.innerWidth - 320;
                        const safeY = 220;
                        const criticalSelectors = ['h1', 'h2', '.s2-stat-number', '.s3-big-num', '.fm-source-bar', '#s7QrPortalFrame', '#s4AnchoredTimer'];
                        const collisions = [];
                        if (activeScene) {
                          criticalSelectors.forEach(sel => {
                            activeScene.querySelectorAll(sel).forEach(el => {
                              const r = el.getBoundingClientRect();
                              if (r.width > 0 && r.height > 0 && r.right > safeX + 8 && r.top < safeY - 8) {
                                collisions.push(sel);
                              }
                            });
                          });
                        }
                        return {
                          scrollW: doc.scrollWidth,
                          scrollH: doc.scrollHeight,
                          innerW: window.innerWidth,
                          innerH: window.innerHeight,
                          hasRawBracket,
                          hudVisible,
                          collisions
                        };
                    }"""
                    )
                    layout_checks.append({"scene": row["scene_id"], "beat": b_idx, "viewport": "1920x1080", **check})

            # Also capture extra intermediate Demo Beats in Scene 4 (Beat 1 Operator, Beat 2 Route, Beat 3 Next Step, Beat 4 Fokus-Schutz, Beat 5 Quick Focus Dial)
            extra_demo_beats = [
                (1, 900, "s04-demo-beat2-operator.png"),
                (2, 500, "s04-demo-beat3-route.png"),
                (3, 500, "s04-demo-beat4-next-step.png"),
                (4, 500, "s04-demo-beat5-protection.png"),
                (5, 120, "s04-demo-beat6-quickfocus-dial.png"),
            ]
            for b_idx, wait_ms, fname in extra_demo_beats:
                page.evaluate(f"window.FocusPitchController.goTo(3, {b_idx}, {{ skipLock: true }})")
                page.wait_for_timeout(wait_ms)
                page.screenshot(path=str(REVIEW_DIR / fname))

            # 2. Capture Rehearsal Mode at 1920x1080
            page.goto(f"{base_url}?mode=rehearsal&scene=3&beat=1", wait_until="networkidle")
            page.wait_for_timeout(550)
            page.screenshot(path=str(REVIEW_DIR / "mode-rehearsal-1080p.png"))

            # 3. Capture & Verify Responsive Viewports: 1600x900, 1440x900, 1366x768
            for vw, vh in [(1600, 900), (1440, 900), (1366, 768)]:
                page.set_viewport_size({"width": vw, "height": vh})
                page.goto(f"{base_url}?mode=recording", wait_until="networkidle")
                for s_idx in range(7):
                    page.evaluate(f"window.FocusPitchController.goTo({s_idx}, 1, {{ skipLock: true }})")
                    page.wait_for_timeout(300)
                    chk = page.evaluate(
                        """() => ({
                            scrollW: document.documentElement.scrollWidth,
                            scrollH: document.documentElement.scrollHeight,
                            innerW: window.innerWidth,
                            innerH: window.innerHeight
                        })"""
                    )
                    layout_checks.append({"scene": f"scene-{s_idx+1}", "beat": 1, "viewport": f"{vw}x{vh}", **chk})
                if (vw, vh) == (1366, 768):
                    page.evaluate("window.FocusPitchController.goTo(3, 6, { skipLock: true })")
                    page.wait_for_timeout(500)
                    page.screenshot(path=str(REVIEW_DIR / "viewport-1366x768-s04-core.png"))
                    page.evaluate("window.FocusPitchController.goTo(6, 1, { skipLock: true })")
                    page.wait_for_timeout(500)
                    page.screenshot(path=str(REVIEW_DIR / "viewport-1366x768-s07-pilot.png"))

            # 4. Capture & Verify Reduced Motion Mode (prefers-reduced-motion: reduce)
            rm_context = browser.new_context(
                viewport={"width": 1920, "height": 1080},
                reduced_motion="reduce",
            )
            rm_page = rm_context.new_page()
            rm_page.goto(f"{base_url}?mode=recording&scene=4&beat=5", wait_until="networkidle")
            rm_page.wait_for_timeout(350)
            rm_page.screenshot(path=str(REVIEW_DIR / "reduced-motion-s04-core.png"))
            rm_context.close()

            context.close()
            browser.close()
    finally:
        httpd.shutdown()

    # Build the 7x3 Contact Sheet
    contact_sheet_path = REVIEW_DIR / "contact-sheet.png"
    build_contact_sheet(scene_capture_plan, contact_sheet_path)

    # Summarize Verification Report
    overflows = [
        c for c in layout_checks if c["scrollW"] > c["innerW"] + 2 or c["scrollH"] > c["innerH"] + 2
    ]
    raw_brackets = [c for c in layout_checks if c.get("hasRawBracket")]
    camera_collisions = [c for c in layout_checks if c.get("collisions")]

    summary = {
        "console_errors": console_errors,
        "page_errors": page_errors,
        "failed_requests": failed_requests,
        "external_requests": external_requests,
        "overflow_count": len(overflows),
        "overflows": overflows,
        "raw_bracket_leaks": raw_brackets,
        "camera_safe_collisions": camera_collisions,
        "contact_sheet": str(contact_sheet_path),
        "total_checks": len(layout_checks),
    }
    print(json.dumps(summary, indent=2))

    if console_errors or page_errors or failed_requests or external_requests or overflows or raw_brackets or camera_collisions:
        raise SystemExit(1)


if __name__ == "__main__":
    run_review()
