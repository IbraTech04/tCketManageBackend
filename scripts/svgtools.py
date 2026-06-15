"""
The non-Claude plumbing for the POC: a tiny Thymeleaf emulator, CSS var() flattening,
QR generation, and cairosvg rasterization.

None of this is needed in production — the Spring app already renders ticket-e-themed.svg
with real Thymeleaf + Batik. These helpers only exist so the demo runs in pure Python.
"""
from __future__ import annotations

import re
import xml.etree.ElementTree as ET
from pathlib import Path


class RenderUnavailable(Exception):
    """Raised when cairosvg (or its native cairo dependency) can't rasterize."""


# --------------------------------------------------------------------------- #
# Sanitizing model-authored SVG fragments (custom_defs / ornament).           #
# --------------------------------------------------------------------------- #

_SCRIPT = re.compile(r"<script\b.*?</script\s*>", re.DOTALL | re.IGNORECASE)
_SCRIPT_SELF = re.compile(r"<script\b[^>]*/>", re.IGNORECASE)
_ON_ATTR = re.compile(r"""\son\w+\s*=\s*(?:"[^"]*"|'[^']*')""", re.IGNORECASE)
# href/xlink:href that is NOT an internal #id reference — strip it (blocks remote
# fetches / SSRF and javascript: URLs when the SVG is later rendered by Batik).
_EXT_HREF = re.compile(r"""\s(?:xlink:)?href\s*=\s*(?:"(?!#)[^"]*"|'(?!#)[^']*')""", re.IGNORECASE)


def sanitize_svg_fragment(fragment: str) -> str:
    """Strip script, event handlers, and external references from model-authored SVG.

    Regex-based and deliberately conservative — this is defense for a PoC, not a
    hardened XML sanitizer. The fragment is also XML-validated downstream
    (see build guards in the callers), so anything malformed is dropped entirely.
    """
    if not fragment:
        return ""
    out = _SCRIPT.sub("", fragment)
    out = _SCRIPT_SELF.sub("", out)
    out = _ON_ATTR.sub("", out)
    out = _EXT_HREF.sub("", out)
    return out


def is_well_formed(svg: str) -> bool:
    """True if the assembled SVG parses as XML (so the model didn't break the doc)."""
    try:
        ET.fromstring(svg)
        return True
    except ET.ParseError:
        return False


# --------------------------------------------------------------------------- #
# Minimal Thymeleaf: th:text (escaped) and th:utext (raw) on a single element. #
# --------------------------------------------------------------------------- #

_TH_ELEMENT = re.compile(
    r'<(?P<tag>[\w-]+)(?P<pre>[^>]*?)\sth:(?P<u>u?)text="\$\{(?P<key>[\w.]+)\}"(?P<post>[^>]*?)>'
    r'(?P<body>.*?)</(?P=tag)>',
    re.DOTALL,
)


def _xml_escape(value: str) -> str:
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def render_thymeleaf(template: str, context: dict) -> str:
    """Resolve th:text / th:utext bindings against `context`.

    Each matched element keeps all of its other attributes; only the th: attribute
    is dropped and its body replaced. Unknown keys leave the element's default body
    in place. This covers exactly what ticket-e-themed.svg uses (text fields, the
    <style> stylesheet, and the qrCode group).
    """

    def replace(match: re.Match) -> str:
        tag = match.group("tag")
        key = match.group("key")
        raw = match.group("u") == "u"
        if key not in context:
            # Leave the element untouched apart from removing the binding attribute.
            inner = match.group("body")
        else:
            value = str(context[key])
            inner = value if raw else _xml_escape(value)
        attrs = match.group("pre") + match.group("post")
        return f"<{tag}{attrs}>{inner}</{tag}>"

    return _TH_ELEMENT.sub(replace, template)


# --------------------------------------------------------------------------- #
# CSS custom-property flattening (cairosvg doesn't resolve var()).             #
# --------------------------------------------------------------------------- #

_DECL = re.compile(r"(--[\w-]+)\s*:\s*([^;{}]+)")
_VAR = re.compile(r"var\(\s*(--[\w-]+)\s*(?:,\s*([^()]*?))?\s*\)")


def flatten_css_vars(svg: str) -> str:
    """Replace every var(--x) in `svg` with its concrete value.

    Reads `--name: value` declarations from anywhere in the document (the injected
    :root block), then substitutes usages in both the <style> block and inline
    presentation attributes. Later declarations win, matching the CSS cascade.
    """
    decls: dict[str, str] = {}
    for name, value in _DECL.findall(svg):
        decls[name] = value.strip()

    def resolve(match: re.Match) -> str:
        name, fallback = match.group(1), match.group(2)
        value = decls[name] if name in decls else (fallback.strip() if fallback else "inherit")
        # var() usages land in double-quoted presentation attributes, so a value
        # with double quotes (e.g. a font stack like "Helvetica Neue", Arial) would
        # break the attribute. Single quotes are valid CSS and XML-attribute-safe.
        return value.replace('"', "'")

    # Resolve vars that reference other vars inside declaration values.
    for _ in range(5):
        changed = False
        for key, value in list(decls.items()):
            new_value = _VAR.sub(resolve, value)
            if new_value != value:
                decls[key] = new_value
                changed = True
        if not changed:
            break

    out = svg
    for _ in range(5):
        new_out = _VAR.sub(resolve, out)
        if new_out == out:
            break
        out = new_out
    return out


# --------------------------------------------------------------------------- #
# QR code -> SVG <g> (themed via --qr-fg / --qr-bg so the stylesheet drives it).#
# --------------------------------------------------------------------------- #

def build_qr_svg(payload: str, *, x: float, y: float, size: float) -> str:
    """Return an SVG group of QR modules filling the box (x, y, size, size)."""
    try:
        import qrcode  # lazy: keep the dep optional for --no-render / binding-only runs
    except ImportError:
        return _qr_placeholder(x, y, size)

    qr = qrcode.QRCode(border=2, error_correction=qrcode.constants.ERROR_CORRECT_M)
    qr.add_data(payload)
    qr.make(fit=True)
    matrix = qr.get_matrix()
    n = len(matrix)
    module = size / n

    parts = [f'<rect x="{x:.2f}" y="{y:.2f}" width="{size:.2f}" height="{size:.2f}" fill="var(--qr-bg)"/>']
    for r, row in enumerate(matrix):
        for c, filled in enumerate(row):
            if filled:
                parts.append(
                    f'<rect x="{x + c * module:.2f}" y="{y + r * module:.2f}" '
                    f'width="{module:.2f}" height="{module:.2f}" fill="var(--qr-fg)"/>'
                )
    return "".join(parts)


def _qr_placeholder(x: float, y: float, size: float) -> str:
    return (
        f'<rect x="{x:.2f}" y="{y:.2f}" width="{size:.2f}" height="{size:.2f}" '
        f'fill="var(--qr-bg)" stroke="var(--qr-fg)"/>'
        f'<text x="{x + size / 2:.2f}" y="{y + size / 2:.2f}" text-anchor="middle" '
        f'font-size="14" fill="var(--qr-fg)">QR (install qrcode)</text>'
    )


# --------------------------------------------------------------------------- #
# Rasterization.                                                               #
# --------------------------------------------------------------------------- #

def render_png(svg: str, path: Path, *, width: int = 720) -> None:
    try:
        import cairosvg
    except (ImportError, OSError) as exc:  # OSError: native cairo lib not found
        raise RenderUnavailable(f"cairosvg unavailable ({exc}); install it or rerun with --no-render.")
    cairosvg.svg2png(bytestring=svg.encode("utf-8"), write_to=str(path), output_width=width)
