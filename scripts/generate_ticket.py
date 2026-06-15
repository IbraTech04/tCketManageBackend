#!/usr/bin/env python3
"""
Proof of concept: describe a vibe -> Claude Haiku writes a ticket stylesheet ->
we inject it into the themeable SVG template and render a PNG.

Pipeline
--------
1. Read a free-text description from the CLI ("neon cyberpunk rave", "soft spring
   wedding", ...).
2. Ask Claude Haiku (claude-haiku-4-5) to turn it into a CSS :root{} stylesheet
   that defines every custom property the template's style contract expects.
3. Emulate just enough Thymeleaf to bind the stylesheet, the sample data, and a
   generated QR code into src/main/resources/templates/ticket-e-themed.svg.
4. Flatten the CSS custom properties to concrete values (cairosvg doesn't resolve
   var()), then rasterize to PNG with cairosvg.

This is intentionally a single-file POC. The production app renders the same
template with Thymeleaf + Batik; only steps 3-4 are re-implemented here so the
demo runs end to end in Python.

Usage
-----
    export ANTHROPIC_API_KEY=sk-ant-...
    pip install -r scripts/requirements.txt
    python scripts/generate_ticket.py "neon cyberpunk rave, hot pink on near-black"

    # options
    python scripts/generate_ticket.py "art-deco gala" --event "The Gatsby Ball" \
        --holder "Nick Carraway" --out scripts/out --no-render
"""
from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path

import anthropic

import theme  # local: style-contract + Claude call
import svgtools  # local: tiny Thymeleaf emulation, var() flattening, QR

REPO_ROOT = Path(__file__).resolve().parent.parent
TEMPLATE = REPO_ROOT / "src" / "main" / "resources" / "templates" / "ticket-e-themed.svg"

# Sample ticket data — stands in for what the backend would pass to Thymeleaf.
SAMPLE_DATA = {
    "ticketType": "GENERAL ADMISSION",
    "eventName": "Aurora Fest 2026",
    "eventDate": "Sat · Aug 22, 2026",
    "eventTime": "8:00 PM",
    "location": "Harbour Pavilion",
    "locationDetail": "45 Marina Way, Toronto",
    "fullName": "Jordan A. Rivera",
}


def slugify(text: str) -> str:
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-") or "theme"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("description", help="Plain-English description of the look you want.")
    parser.add_argument("--event", help="Override the sample event name.")
    parser.add_argument("--holder", help="Override the sample ticket-holder name.")
    parser.add_argument("--out", default=str(REPO_ROOT / "scripts" / "out"), help="Output directory.")
    parser.add_argument("--model", default="claude-opus-4-8", help="Claude model id.")
    parser.add_argument("--no-render", action="store_true", help="Write the styled SVG but skip PNG rasterization.")
    args = parser.parse_args(argv)

    api_key = os.environ.get("ANTHROPIC_API_KEY")
    if not api_key:
        print("error: set ANTHROPIC_API_KEY in your environment.", file=sys.stderr)
        return 2

    data = dict(SAMPLE_DATA)
    if args.event:
        data["eventName"] = args.event
    if args.holder:
        data["fullName"] = args.holder

    template = TEMPLATE.read_text(encoding="utf-8")

    # 1-2. Ask Claude Haiku for a stylesheet matching our contract.
    print(f"Asking {args.model} to theme: {args.description!r} ...")
    client = anthropic.Anthropic(api_key=api_key)
    result = theme.generate_stylesheet(client, args.description, model=args.model)
    theme_name = result["theme_name"]
    stylesheet = result["css"]
    print(f"  -> theme: {theme_name}")

    missing = theme.missing_variables(stylesheet)
    if missing:
        print(f"  warning: stylesheet is missing {len(missing)} contract variable(s): "
              f"{', '.join(sorted(missing))}", file=sys.stderr)
        print("  filling the gaps with the template defaults so render stays valid.")
        stylesheet = theme.merge_with_defaults(stylesheet)

    # 3. Bind stylesheet + data + QR + model-authored slots into the template.
    qr_markup = svgtools.build_qr_svg(
        payload=f"https://tcketmanage.app/verify/{slugify(theme_name)}",
        x=90, y=474, size=180,
    )
    custom_defs = svgtools.sanitize_svg_fragment(result["custom_defs"])
    ornament = svgtools.sanitize_svg_fragment(result["ornament"])

    def assemble(defs: str, orn: str) -> str:
        context = dict(data, stylesheet=stylesheet, qrCode=qr_markup, customDefs=defs, ornament=orn)
        return svgtools.render_thymeleaf(template, context)

    styled_svg = assemble(custom_defs, ornament)
    if (custom_defs or ornament) and not svgtools.is_well_formed(styled_svg):
        print("  warning: model-authored SVG slots were malformed — dropping them.", file=sys.stderr)
        styled_svg = assemble("", "")
    elif custom_defs or ornament:
        print("  -> applied model-authored decoration (custom_defs/ornament)")

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    slug = slugify(theme_name)

    svg_path = out_dir / f"ticket-{slug}.svg"
    svg_path.write_text(styled_svg, encoding="utf-8")
    print(f"  wrote {svg_path}")

    css_path = out_dir / f"ticket-{slug}.css"
    css_path.write_text(stylesheet.strip() + "\n", encoding="utf-8")
    print(f"  wrote {css_path}")

    if args.no_render:
        print("Done (skipped rasterization).")
        return 0

    # 4. Flatten var() then rasterize.
    flattened = svgtools.flatten_css_vars(styled_svg)
    png_path = out_dir / f"ticket-{slug}.png"
    try:
        svgtools.render_png(flattened, png_path, width=720)
    except svgtools.RenderUnavailable as exc:
        print(f"  note: {exc}", file=sys.stderr)
        print(f"  open {svg_path} in a browser to view the result.")
        return 0
    print(f"  wrote {png_path}")
    print("Done.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
