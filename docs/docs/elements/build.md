# Build

`"type": "build"` · can stand in a page's [`elements`](../pages.md#elements) or be a page of its own.

Builds designed in the book's own files, turning in a window: `assets/<namespace>/folios/<book>/builds/<name>.json`.
They take the same keys as a [structure](structure.md), with `build` in place of `structure`: one build, or a list of
them (each a name, or `{"id": ..., "label": ...}`) picked between under the window. A bare name is a file of this book's (`"tower"`); `namespace:path` names any book's file under `folios/`
(`"mymod:my_guide/builds/tower"`). Like structures, builds have no requirement list and no eye clasp.

```json
{
  "type": "build",
  "title": "Builds",
  "build": ["watchtower", { "id": "well", "label": "The Old Well" }]
}
```


The format of the files themselves is on [Build files](build-files.md).
