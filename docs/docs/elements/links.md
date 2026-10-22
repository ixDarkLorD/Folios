# Links

`"type": "links"` · can stand in a page's [`elements`](../pages.md#elements) or be a page of its own.

Rows linking to other entries, each with the entry's icon. Entries the reader can't see yet are left out; locked ones
show as `???`.

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `links` | list of 1–12 | required | Each an entry id (optionally with `#anchor`), or `{"entry": ..., "label": ...}`. |
| `title` | text | "See also" | A heading. |
| `text` | text | `""` | Text under the links. |

Each link object takes:

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `entry` | string | required | The entry id, optionally with `#anchor`. |
| `label` | text | the entry's name | The words shown for it. |

```json
{
  "type": "links",
  "links": [
    "getting_started/recipes",
    { "entry": "advanced/my_build", "label": "Build a shrine" }
  ]
}
```
