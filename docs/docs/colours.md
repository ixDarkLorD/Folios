# Colours

A book has two colour schemes: the **page** scheme, `colors`, for the paper and what is on it, in light and dark mode;
and the **panel** scheme, `panel`, for what the book shows off its pages. `accent` and `cover` in [book.json](book.md)
set the book's two main colours, which both schemes start from.

## Page colours

`colors` sets the page scheme. Any colour left out keeps the style's own.

Colours are `"#RRGGBB"`, or `"#AARRGGBB"` with alpha.

| Key | What it colours |
| --- | --- |
| `paper` | The paper. |
| `text` | Body text. |
| `muted` | Quieter text: captions, counts, code. |
| `heading` | Headings and titles. |
| `accent` | Marks and rules on the page (the book's `accent` by default). |
| `link` | Links. |
| `panel` | Panels and slots on the page. |
| `line` | Frames and dividing lines. |
| `dark` | An object with the same keys, for dark mode (screen or handheld). |

```json title="book.json"
{
  "colors": {
    "paper": "#F1E7D0",
    "text": "#3B3026",
    "muted": "#8B7B66",
    "heading": "#2E241B",
    "link": "#5A3A8A",
    "panel": "#E6D8BA",
    "line": "#C4B08C",
    "dark": { "paper": "#1F222C", "text": "#D5D8E2" }
  }
}
```

## Panel colours

`panel`, at the top of book.json, colours what the book shows off its pages: its tooltips, its
[celebration](celebrations.md) toasts and the build guide's panel on the HUD. Each colour left out takes on the
book: a background from the cover colour, a gilt frame warmed by the accent.

| Key | What it colours |
| --- | --- |
| `background` | The ground. |
| `frame` | The frame, and in toasts and the build guide's panel the slot outlines, rules and bars. |
| `title` | Names and headings. |
| `detail` | Quieter lines. |

```json title="book.json"
{
  "panel": { "background": "#281E18", "frame": "#FFE28C", "title": "#F2E6CC", "detail": "#A89A86" }
}
```

Before 2.0 this was `colors` › `tooltip`, which is still read when a book has no `panel`.

A category can give its own tab a colour with `"color"` in its file. See
[Categories](categories-and-entries.md#categories).
