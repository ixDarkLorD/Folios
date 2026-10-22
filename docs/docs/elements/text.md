# Text

`"type": "text"` · can stand in a page's [`elements`](../pages.md#elements) or be a page of its own.

Text, with an optional title. It runs on over as many pages as it needs.

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `title` | text | none | A heading at the top of the page. |
| `text` | text | `""` | The page's text. |

```json
{
  "type": "text",
  "title": "First Steps",
  "text": "Text runs on over as many pages as it needs.\n\n## A heading\n- a bullet\n- another"
}
```
