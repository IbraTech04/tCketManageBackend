"""
Style contract + the Claude Haiku call that fills it.

The contract is the single source of truth for which CSS custom properties the
ticket-e-themed.svg template consumes. We feed it to Claude so the model knows
exactly what to produce, and reuse it locally to validate / backfill the result.
"""
from __future__ import annotations

import json
import re
from pathlib import Path

import dotenv

# Load scripts/.env relative to this file, so it works regardless of the CWD the
# script/server was launched from. In prod these should already be in the env.
dotenv.load_dotenv(Path(__file__).resolve().parent / ".env")

# Each entry: (variable, human description) — order matches the template's :root.
STYLE_CONTRACT: list[tuple[str, str]] = [
    ("--font-family", "CSS font-family stack. Must end in a generic (sans-serif/serif) and use only widely available fonts."),
    ("--ticket-bg", "Card background fill."),
    ("--accent-1", "Top stop of the vertical spine gradient."),
    ("--accent-2", "Middle stop of the spine gradient."),
    ("--accent-3", "Bottom stop of the spine gradient."),
    ("--orb-color", "Color of the soft glow in the top-right corner."),
    ("--shadow-color", "Tint of the card drop shadow (usually a near-black)."),
    ("--logo-stroke", "Stroke color of the tCketManage logo glyph."),
    ("--logo-fill", "Fill of the small '+' mark inside the logo."),
    ("--logo-ink", "Color of the 'tCketManage' wordmark."),
    ("--admit-stroke", "Border color of the ADMIT ONE pill."),
    ("--admit-ink", "Text color of ADMIT ONE."),
    ("--ticket-type", "Eyebrow label above the event name (e.g. GENERAL ADMISSION)."),
    ("--ink", "Event-name color. Highest-contrast text on the card."),
    ("--value", "Field values: date, time, location, holder name."),
    ("--label", "Small uppercase field labels (DATE, DOORS, LOCATION...)."),
    ("--ink-muted", "Secondary detail line (street address)."),
    ("--divider", "Hairline rules. Drawn faint via stroke-opacity, so pick a solid color."),
    ("--perforation", "Dashed tear line across the perforation."),
    ("--qr-panel", "Background panel the QR code sits on. Keep it light for scannability."),
    ("--qr-fg", "QR modules. Must strongly contrast --qr-bg (think near-black on light)."),
    ("--qr-bg", "QR quiet zone. Usually matches or is lighter than --qr-panel."),
    ("--footer", "Footer text color. Subtle."),
]

CONTRACT_VARS = {name for name, _ in STYLE_CONTRACT}

# Default (midnight) values, kept in sync with the template's committed :root.
DEFAULTS: dict[str, str] = {
    "--font-family": "'Product Sans', 'Helvetica Neue', Helvetica, Arial, sans-serif",
    "--ticket-bg": "#17171c",
    "--accent-1": "#f97316",
    "--accent-2": "#ea580c",
    "--accent-3": "#ef4444",
    "--orb-color": "#f97316",
    "--shadow-color": "#18181b",
    "--logo-stroke": "#ffffff",
    "--logo-fill": "#ffffff",
    "--logo-ink": "#ffffff",
    "--admit-stroke": "#ffffff",
    "--admit-ink": "#d7d7dc",
    "--ticket-type": "#ff8a45",
    "--ink": "#ffffff",
    "--value": "#f4f4f5",
    "--label": "#7e7e87",
    "--ink-muted": "#9a9aa3",
    "--divider": "#ffffff",
    "--perforation": "#ffffff",
    "--qr-panel": "#ffffff",
    "--qr-fg": "#18181b",
    "--qr-bg": "#ffffff",
    "--footer": "#5b5b63",
}

_SYSTEM_PROMPT = """You are a brand designer who art-directs a single SVG event \
ticket. You return JSON only — no prose.

CANVAS. The ticket is `viewBox="0 0 360 720"`. The card is a rounded rect spanning \
x 16..344, y 16..704. A vertical accent spine runs down the far left; the rest is \
open.

WHAT THE TEMPLATE OWNS (you do NOT draw these — they always render ON TOP of your \
work, so you can paint freely behind them):
- logo + 'tCketManage' wordmark, top-left (~y35-57)
- an ADMIT ONE pill, top-right
- the ticket-type eyebrow (~y150) and the event-name hero (~y186), left-aligned
- labelled fields: DATE/DOORS (~y250-270), LOCATION (~y330-367), TICKET HOLDER (~y421)
- a dashed perforation line across y432
- a QR panel: opaque rounded rect at x66 y450, 228x222, with the scannable QR inside

WHAT YOU CONTROL — three things, returned as three JSON fields:

1) css  — a CSS `:root {{ ... }}` block assigning ALL of these custom properties \
exactly once (only these names):
{contract}

2) custom_defs — SVG markup placed inside <defs> (never renders directly). Define \
any <linearGradient>/<radialGradient>/<pattern>/<filter>/<clipPath> you want here, \
each with a UNIQUE id prefixed `fx-` (e.g. <pattern id="fx-grid">...). Reference them \
from the ornament via url(#fx-...). May be an empty string.

3) ornament — SVG markup painted INSIDE the card, clipped to it, BEHIND all the text \
and the QR. This is your decoration layer: backgrounds, gradients, textures, patterns, \
geometric motifs, glows, blobs, line-work. May be an empty string.

RULES:
- css values are CSS colors (hex/rgb()/hsl()) except --font-family (a safe font stack). \
--ink and --value must contrast strongly with --ticket-bg; --qr-fg must contrast with \
--qr-bg; keep --qr-panel/--qr-bg light unless the brief demands otherwise.
- In custom_defs / ornament you may use the palette via var(--x) and url(#fx-...).
- Keep the area behind the left-column text (roughly x16..330, y140..430) calm enough \
that the text stays legible — busy texture is fine elsewhere.
- Coordinates must stay within 0..360 (x) and 0..720 (y). The clip handles overflow, \
but stay close to the card.
- Produce well-formed SVG fragments. FORBIDDEN: <script>, any on* event attributes, and \
any external/remote href or <image> (only internal url(#id) references are allowed).
- Do not redefine these ids: g, orb, sh, cm, cc, customDefs, ornament, qrCode."""

_OUTPUT_SCHEMA = {
    "type": "object",
    "additionalProperties": False,
    "properties": {
        "theme_name": {"type": "string", "description": "Short kebab-case name for this theme, e.g. neon-cyberpunk."},
        "css": {"type": "string", "description": "The :root { ... } stylesheet defining every contract variable."},
        "custom_defs": {"type": "string", "description": "SVG <defs> content (gradients/patterns/filters, ids prefixed fx-). May be empty."},
        "ornament": {"type": "string", "description": "SVG decoration painted behind the text/QR, clipped to the card. May be empty."},
    },
    "required": ["theme_name", "css", "custom_defs", "ornament"],
}


def _contract_block() -> str:
    return "\n".join(f"  {name}: {desc}" for name, desc in STYLE_CONTRACT)


def generate_stylesheet(client, description: str, *, model: str = "claude-opus-4-8") -> dict:
    """Call Claude Haiku and return {'theme_name': str, 'css': str}.

    Prefers structured outputs (output_config.format) so the response is guaranteed-
    parseable JSON. If that's rejected (e.g. the account needs a beta header for
    structured outputs), it falls back to a plain-text request and extracts the
    :root{} block. Haiku 4.5 supports neither adaptive thinking nor effort, so both
    paths are plain create() calls.
    """
    import anthropic

    system = _SYSTEM_PROMPT.format(contract=_contract_block())
    user = f"Theme the ticket for: {description}"

    try:
        response = client.messages.create(
            model=model,
            max_tokens=4000,
            system=system,
            messages=[{"role": "user", "content": user}],
            output_config={"format": {"type": "json_schema", "schema": _OUTPUT_SCHEMA}},
        )
    except anthropic.BadRequestError:
        # Structured outputs unavailable here — ask for raw JSON and parse leniently.
        response = client.messages.create(
            model=model,
            max_tokens=4000,
            system=system + '\n\nReturn ONLY a JSON object with keys "theme_name", '
            '"css", "custom_defs", "ornament".',
            messages=[{"role": "user", "content": user}],
        )

    text = "".join(block.text for block in response.content if block.type == "text")
    return _parse_result(text)


def _parse_result(text: str) -> dict:
    cleaned = text.strip()
    # Strip a ```json ... ``` fence if the model wrapped its output.
    fenced = re.search(r"```(?:json)?\s*(\{.*\})\s*```", cleaned, re.DOTALL)
    if fenced:
        cleaned = fenced.group(1)
    try:
        obj = json.loads(cleaned)
        return {
            "theme_name": str(obj["theme_name"]),
            "css": str(obj["css"]),
            "custom_defs": str(obj.get("custom_defs", "")),
            "ornament": str(obj.get("ornament", "")),
        }
    except (json.JSONDecodeError, KeyError, TypeError):
        # Last resort: pull a :root{...} block straight out of the text (palette only).
        match = re.search(r":root\s*\{.*?\}", text, re.DOTALL)
        if not match:
            raise ValueError(f"Could not parse a stylesheet from model output:\n{text[:500]}")
        return {"theme_name": "theme", "css": match.group(0), "custom_defs": "", "ornament": ""}


def declared_variables(css: str) -> set[str]:
    return set(re.findall(r"(--[\w-]+)\s*:", css))


def missing_variables(css: str) -> set[str]:
    return CONTRACT_VARS - declared_variables(css)


def merge_with_defaults(css: str) -> str:
    """Append any contract variables the model omitted, using midnight defaults.

    Appending later declarations keeps the model's choices winning (CSS cascade),
    while guaranteeing every var() in the template resolves.
    """
    missing = missing_variables(css)
    if not missing:
        return css
    fill = "\n".join(f"  {name}: {DEFAULTS[name]};" for name in STYLE_CONTRACT_ORDER if name in missing)
    return f"{css.rstrip()}\n:root {{\n{fill}\n}}\n"


STYLE_CONTRACT_ORDER = [name for name, _ in STYLE_CONTRACT]
