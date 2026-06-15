# AI ticket theming — proof of concept

Describe a vibe in plain English → Claude Haiku writes a CSS stylesheet for the
ticket → we inject it into the themeable SVG template and render a PNG.

This is a **Python POC only**. The production Spring app renders the same template
(`src/main/resources/templates/ticket-e-themed.svg`) with real Thymeleaf + Batik;
the script below re-implements just enough of that binding to run end-to-end here.

## How it fits together

```
description ──► theme.py ──► Claude Haiku ──► :root{} stylesheet
                                                  │
ticket-e-themed.svg ──► svgtools.render_thymeleaf ┤  (+ sample data + QR)
                                                  ▼
                          svgtools.flatten_css_vars ──► cairosvg ──► PNG
```

- **`ticket-e-themed.svg`** (in `src/main/resources/templates/`) — the third ticket.
  Same layout as `ticket-c-midnight`, but every color/font is a CSS custom property.
  A single `<style th:utext="${stylesheet}">` block is the injection point; the
  `:root{}` committed in the file is the default (midnight) theme **and** the
  contract of every variable a generated stylesheet must define.
- **`theme.py`** — the style contract (`STYLE_CONTRACT`) and the Claude Haiku call.
  Uses structured outputs when available, falls back to plain text + `:root{}`
  extraction, and backfills any variable the model omitted with midnight defaults.
- **`svgtools.py`** — the non-Claude plumbing: a tiny Thymeleaf emulator
  (`th:text` / `th:utext`), CSS `var()` flattening (cairosvg can't resolve `var()`),
  QR generation, and cairosvg rasterization.
- **`generate_ticket.py`** — the CLI that wires it together.

## Setup

```sh
pip install -r scripts/requirements.txt
export ANTHROPIC_API_KEY=sk-ant-...        # PowerShell: $env:ANTHROPIC_API_KEY="sk-ant-..."
```

> **Windows + cairosvg:** cairosvg needs the native cairo library, which isn't
> bundled. If it can't load, the script still writes the styled `.svg` (and `.css`)
> and tells you to open the SVG in a browser. To get PNGs, install the GTK3 runtime
> (which provides `libcairo-2.dll`) or run in WSL/conda. Or just use `--no-render`
> and open the `.svg`.

## Run

```sh
python scripts/generate_ticket.py "neon cyberpunk rave, hot pink and cyan on near-black"
python scripts/generate_ticket.py "soft spring garden wedding" --event "The Hartley Wedding" --holder "Aunt Marie"
python scripts/generate_ticket.py "art-deco gold gala" --no-render
```

Outputs land in `scripts/out/` (git-ignored):

- `ticket-<theme>.svg` — the styled, data-bound ticket
- `ticket-<theme>.css` — the stylesheet Claude generated
- `ticket-<theme>.png` — the rasterized ticket (when cairosvg can render)

## Web playground (live preview)

For an interactive version — type a prompt, watch the ticket theme itself, then
tweak the generated CSS and see it re-render instantly:

```sh
python scripts/app.py        # -> http://127.0.0.1:5000
```

The page calls the same `theme.generate_stylesheet` pipeline. It serves the styled
SVG **with `var()` intact** (browsers resolve custom properties natively, so no
cairosvg/flatten is needed for on-screen preview) and inlines it. Editing the
stylesheet textarea re-binds the template live via `/api/restyle` — no Claude call,
so it's free and instant. Prompt presets are one click away.

`ANTHROPIC_API_KEY` is read from `scripts/.env` (or the environment).

### CLI options

| Flag          | Default              | Meaning                                  |
| ------------- | -------------------- | ---------------------------------------- |
| `--event`     | sample event name    | Override the event name on the ticket    |
| `--holder`    | sample holder        | Override the ticket-holder name          |
| `--out`       | `scripts/out`        | Output directory                         |
| `--model`     | `claude-haiku-4-5`   | Claude model id                          |
| `--no-render` | off                  | Write the SVG/CSS but skip rasterization |

## How much control the model has

The model authors three things, layered so it can be expressive without ever
endangering the data or the QR:

| Field         | What the model controls                                                        | Safety                                                            |
| ------------- | ------------------------------------------------------------------------------ | ---------------------------------------------------------------- |
| `css`         | The full `:root{}` palette + font (23 contract variables)                       | Validated/backfilled against the contract                        |
| `custom_defs` | Free-form SVG `<defs>` — gradients, patterns, filters (ids prefixed `fx-`)       | Sanitized; referenced by id from the ornament                    |
| `ornament`    | Free-form SVG decoration painted **inside the card, behind all text and the QR** | Clipped to the card; sanitized; data + QR always render on top   |

The event fields and QR stay template-controlled and are drawn *after* the ornament
in document order, so model decoration can fill the whole card but can never cover or
break them. Model-authored fragments are run through `svgtools.sanitize_svg_fragment`
(strips `<script>`, `on*` handlers, and external/remote `href`s) and the assembled SVG
is XML-validated — if the slots break well-formedness, they're dropped and the ticket
still renders from the palette alone.

> Going further (full structural rewrite, model-controlled text layout) is possible but
> needs geometry/legibility/overlap validation on every generation — out of scope for
> this PoC.

## Notes & limitations

- The script emulates only the Thymeleaf features this template uses (`th:text`,
  `th:utext` on a single element). It is **not** a general Thymeleaf engine.
- `var()` flattening exists because cairosvg doesn't resolve CSS custom properties.
  Browsers and Batik handle the template's `var()` natively; the production renderer
  would apply the same flatten step (or rely on Batik's CSS support).
- The QR code is generated fresh from a demo verify-URL so the rendered ticket
  shows a real, scannable code.
