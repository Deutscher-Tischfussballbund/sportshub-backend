# ER diagram renderer

Turns `docs/dbdiagram.io/current_model.txt` into `docs/dbdiagram.io/sportshub-model.svg`: an
auto-laid-out ER diagram with as few crossing lines as the layout engine finds.

dbdiagram.io stores table positions on its website, not in the DBML, so its own view can't be
laid out from the repo. This script gives a reproducible picture of the same model instead.

## Usage

Needs Node 20+.

```bash
cd scripts/erd-diagram
npm install          # once
npm run render       # writes docs/dbdiagram.io/sportshub-model.svg
```

Options (after `npm run render --`):

| Option | Default | Meaning |
|---|---|---|
| `--in <file>` | `docs/dbdiagram.io/current_model.txt` | DBML source |
| `--out <file>` | `docs/dbdiagram.io/sportshub-model.svg` | SVG output |
| `--seeds <n>` | `40` | layout attempts; the one with the fewest crossings wins |

Re-run it whenever `current_model.txt` changes.

## How it works

- **Parsing:** reads the `Table` blocks and the standalone `Ref:` lines (the subset
  `current_model.txt` uses). Enums show only as column types. Optional refs (`>?`) are drawn
  like required ones.
- **Layout:** [ELK](https://eclipse.dev/elk/) layered layout, left to right, parents before
  children, with lines routed at right angles from the exact column rows. Several random seeds
  are tried and the one with the fewest line crossings is kept (it prints the count).
- **Colours:** table headers are coloured by domain (`DOMAINS` in `render-erd.mjs`). A new
  table gets a grey header and a warning until it's added there.
