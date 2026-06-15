#!/usr/bin/env python3
"""
Live ticket-theming playground.

A tiny Flask UI on top of the same POC pipeline as generate_ticket.py: type a
prompt, Claude Haiku writes the stylesheet, and the themed ticket renders inline.
Because browsers resolve CSS var() natively, we serve the styled SVG with its
custom properties intact (no cairosvg/flatten needed) — which also powers a live
CSS editor that re-renders instantly as you tweak the palette.

    pip install -r scripts/requirements.txt
    # ANTHROPIC_API_KEY in scripts/.env or the environment
    python scripts/app.py            # -> http://127.0.0.1:5000
"""
from __future__ import annotations

import re
from pathlib import Path

import anthropic
from flask import Flask, jsonify, render_template_string, request

import theme  # loads scripts/.env on import
import svgtools

REPO_ROOT = Path(__file__).resolve().parent.parent
TEMPLATE = REPO_ROOT / "src" / "main" / "resources" / "templates" / "ticket-e-themed.svg"

SAMPLE_DATA = {
    "ticketType": "GENERAL ADMISSION",
    "eventName": "Aurora Fest 2026",
    "eventDate": "Sat · Aug 22, 2026",
    "eventTime": "8:00 PM",
    "location": "Harbour Pavilion",
    "locationDetail": "45 Marina Way, Toronto",
    "fullName": "Jordan A. Rivera",
}

app = Flask(__name__)
_template_svg = TEMPLATE.read_text(encoding="utf-8")
_client: anthropic.Anthropic | None = None


def slugify(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-") or "theme"


def get_client() -> anthropic.Anthropic:
    global _client
    if _client is None:
        _client = anthropic.Anthropic()  # reads ANTHROPIC_API_KEY (theme.py loaded .env)
    return _client


def build_svg(stylesheet: str, data: dict, slug: str,
              custom_defs: str = "", ornament: str = "") -> tuple[str, bool]:
    """Bind stylesheet + data + QR (+ model-authored slots) into the template.

    Returns (svg, used_slots). Model-authored fragments are sanitized and the
    assembled doc is XML-validated; if the slots break well-formedness we rebuild
    with them empty so a bad generation still produces a valid ticket.
    """
    qr = svgtools.build_qr_svg(f"https://tcketmanage.app/verify/{slug}", x=90, y=474, size=180)
    defs = svgtools.sanitize_svg_fragment(custom_defs)
    orn = svgtools.sanitize_svg_fragment(ornament)

    def assemble(d: str, o: str) -> str:
        context = dict(data, stylesheet=stylesheet, qrCode=qr, customDefs=d, ornament=o)
        svg = svgtools.render_thymeleaf(_template_svg, context)
        return re.sub(r"^<\?xml[^>]*\?>\s*", "", svg)  # strip prolog for inline DOM use

    svg = assemble(defs, orn)
    if (defs or orn) and not svgtools.is_well_formed(svg):
        return assemble("", ""), False
    return svg, bool(defs or orn)


@app.get("/")
def index():
    return render_template_string(PAGE)


@app.post("/api/theme")
def api_theme():
    payload = request.get_json(force=True, silent=True) or {}
    description = (payload.get("description") or "").strip()
    if not description:
        return jsonify(ok=False, error="Describe the look you want first."), 400

    data = dict(SAMPLE_DATA)
    if payload.get("event"):
        data["eventName"] = payload["event"].strip()
    if payload.get("holder"):
        data["fullName"] = payload["holder"].strip()

    try:
        result = theme.generate_stylesheet(get_client(), description)
    except anthropic.AuthenticationError:
        return jsonify(ok=False, error="ANTHROPIC_API_KEY is missing or invalid."), 401
    except anthropic.APIError as exc:
        return jsonify(ok=False, error=f"Claude API error: {exc}"), 502
    except Exception as exc:  # noqa: BLE001 — surface anything else to the UI
        return jsonify(ok=False, error=str(exc)), 500

    stylesheet = result["css"]
    if theme.missing_variables(stylesheet):
        stylesheet = theme.merge_with_defaults(stylesheet)

    custom_defs, ornament = result["custom_defs"], result["ornament"]
    svg, used_slots = build_svg(stylesheet, data, slugify(result["theme_name"]), custom_defs, ornament)
    return jsonify(
        ok=True, theme_name=result["theme_name"], css=stylesheet.strip(),
        custom_defs=custom_defs, ornament=ornament, used_slots=used_slots, svg=svg,
    )


@app.post("/api/restyle")
def api_restyle():
    """Re-bind the template with a client-edited stylesheet (no Claude call).

    The client passes back the last-generated slots so live CSS edits keep the
    model's decoration instead of dropping it.
    """
    payload = request.get_json(force=True, silent=True) or {}
    css = payload.get("css") or ""
    data = dict(SAMPLE_DATA)
    if payload.get("event"):
        data["eventName"] = payload["event"].strip()
    if payload.get("holder"):
        data["fullName"] = payload["holder"].strip()
    svg, _ = build_svg(css, data, "preview", payload.get("custom_defs", ""), payload.get("ornament", ""))
    return jsonify(ok=True, svg=svg)


PAGE = r"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>tCketManage — AI ticket themer</title>
<style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; }
  body {
    margin: 0; min-height: 100vh; display: grid; grid-template-columns: 380px 1fr;
    font: 15px/1.5 'Segoe UI', system-ui, sans-serif; color: #e7e7ea; background: #0e0e12;
  }
  .panel { padding: 28px; border-right: 1px solid #232329; display: flex; flex-direction: column; gap: 18px; overflow-y: auto; }
  h1 { font-size: 18px; margin: 0; letter-spacing: -.2px; }
  h1 span { color: #f97316; }
  p.sub { margin: 0; color: #8a8a93; font-size: 13px; }
  label { display: block; font-size: 12px; text-transform: uppercase; letter-spacing: .08em; color: #8a8a93; margin-bottom: 6px; }
  textarea, input {
    width: 100%; background: #17171c; border: 1px solid #2c2c34; border-radius: 10px;
    color: #f2f2f4; padding: 10px 12px; font: inherit; resize: vertical;
  }
  textarea:focus, input:focus { outline: none; border-color: #f97316; }
  .row { display: flex; gap: 10px; }
  .row > div { flex: 1; }
  button {
    background: #f97316; color: #1a1209; border: 0; border-radius: 10px; padding: 11px 16px;
    font: 600 15px/1 'Segoe UI', sans-serif; cursor: pointer;
  }
  button:disabled { opacity: .55; cursor: progress; }
  .hint { font-size: 12px; color: #6f6f78; }
  .chips { display: flex; flex-wrap: wrap; gap: 6px; }
  .chip { background: #1d1d23; border: 1px solid #2c2c34; border-radius: 999px; padding: 5px 11px; font-size: 12px; cursor: pointer; color: #c9c9d0; }
  .chip:hover { border-color: #f97316; color: #fff; }
  .css-editor { font: 12px/1.5 'Consolas', monospace; min-height: 180px; }
  .error { color: #ff8888; font-size: 13px; min-height: 18px; }
  .stage { display: flex; align-items: center; justify-content: center; padding: 40px; position: relative; }
  .stage svg { width: 340px; height: auto; filter: drop-shadow(0 18px 50px rgba(0,0,0,.5)); }
  .stage.empty::after { content: "Your themed ticket will appear here"; color: #4a4a53; }
  .badge { position: absolute; top: 18px; left: 18px; font-size: 12px; color: #8a8a93; }
  .spinner { position: absolute; inset: 0; display: none; align-items: center; justify-content: center; background: rgba(14,14,18,.6); }
  .spinner.on { display: flex; }
  .spinner div { width: 28px; height: 28px; border: 3px solid #2c2c34; border-top-color: #f97316; border-radius: 50%; animation: spin .8s linear infinite; }
  @keyframes spin { to { transform: rotate(360deg); } }
</style>
</head>
<body>
  <div class="panel">
    <div>
      <h1>tCketManage <span>AI themer</span></h1>
      <p class="sub">Describe a vibe — Claude Haiku writes the stylesheet and themes the ticket live.</p>
    </div>

    <div>
      <label for="desc">Prompt</label>
      <textarea id="desc" rows="3" placeholder="neon cyberpunk rave, hot pink and cyan on near-black">neon cyberpunk rave, hot pink and cyan on near-black</textarea>
    </div>

    <div class="chips" id="chips"></div>

    <div class="row">
      <div>
        <label for="event">Event name</label>
        <input id="event" placeholder="Aurora Fest 2026">
      </div>
      <div>
        <label for="holder">Holder</label>
        <input id="holder" placeholder="Jordan A. Rivera">
      </div>
    </div>

    <button id="go">Generate theme</button>
    <div class="error" id="error"></div>

    <div>
      <label for="css">Stylesheet (edit to tweak live)</label>
      <textarea id="css" class="css-editor" spellcheck="false" placeholder="The :root{} Claude generates shows here. Edit it and the ticket updates instantly."></textarea>
      <p class="hint">Ctrl/Cmd + Enter to generate. Editing the CSS re-renders without calling Claude.</p>
    </div>
  </div>

  <div class="stage empty" id="stage">
    <span class="badge" id="badge"></span>
    <div class="spinner" id="spinner"><div></div></div>
  </div>

<script>
const $ = (id) => document.getElementById(id);
let lastDefs = "", lastOrnament = "";
const PROMPTS = [
  "soft spring garden wedding", "art-deco gold gala", "Y2K vaporwave",
  "minimalist Scandinavian, warm paper tones", "deep-sea bioluminescence",
  "retro 70s sunset, mustard and rust",
];

const chips = $("chips");
for (const p of PROMPTS) {
  const c = document.createElement("span");
  c.className = "chip"; c.textContent = p;
  c.onclick = () => { $("desc").value = p; generate(); };
  chips.appendChild(c);
}

function setSpinner(on) { $("spinner").classList.toggle("on", on); $("go").disabled = on; }

function renderSVG(svg) {
  const stage = $("stage");
  stage.classList.remove("empty");
  // Drop any prior SVG (keep badge + spinner), then inject the new one.
  [...stage.querySelectorAll("svg")].forEach(n => n.remove());
  stage.insertAdjacentHTML("beforeend", svg);
}

async function generate() {
  const description = $("desc").value.trim();
  if (!description) return;
  $("error").textContent = "";
  setSpinner(true);
  try {
    const res = await fetch("/api/theme", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ description, event: $("event").value, holder: $("holder").value }),
    });
    const data = await res.json();
    if (!data.ok) { $("error").textContent = data.error || "Something went wrong."; return; }
    renderSVG(data.svg);
    $("css").value = data.css;
    lastDefs = data.custom_defs || "";
    lastOrnament = data.ornament || "";
    $("badge").textContent = data.theme_name + (data.used_slots ? "" : " (palette only)");
  } catch (e) {
    $("error").textContent = "Request failed: " + e.message;
  } finally {
    setSpinner(false);
  }
}

// Live CSS editing — re-bind server-side (cheap, no Claude call), debounced.
let restyleTimer = null;
async function restyle() {
  const css = $("css").value;
  if (!css.trim()) return;
  const res = await fetch("/api/restyle", {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ css, custom_defs: lastDefs, ornament: lastOrnament,
                           event: $("event").value, holder: $("holder").value }),
  });
  const data = await res.json();
  if (data.ok) renderSVG(data.svg);
}

$("css").addEventListener("input", () => {
  clearTimeout(restyleTimer);
  restyleTimer = setTimeout(restyle, 250);
});
$("go").onclick = generate;
$("desc").addEventListener("keydown", (e) => {
  if ((e.metaKey || e.ctrlKey) && e.key === "Enter") { e.preventDefault(); generate(); }
});
</script>
</body>
</html>"""


if __name__ == "__main__":
    app.run(host="127.0.0.1", port=5000, debug=True)
