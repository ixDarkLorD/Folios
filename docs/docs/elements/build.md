# Build

`"type": "build"` · can stand in a page's [`elements`](../pages.md#elements) or be a page of its own.

Builds designed in the book's own files, turning in a window: `assets/<namespace>/folios/<book>/builds/<name>.json`.
They take the same keys as a [structure](structure.md), with `build` and `builds` in place of `structure` and
`structures`. A bare name is a file of this book's (`"tower"`); `namespace:path` names any book's file under `folios/`
(`"mymod:my_guide/builds/tower"`). Like structures, builds have no requirement list and no eye clasp.

```json
{
  "type": "build",
  "title": "Builds",
  "builds": ["watchtower", { "id": "well", "name": "The Old Well" }]
}
```


The format of the files themselves is on [Build files](build-files.md).
