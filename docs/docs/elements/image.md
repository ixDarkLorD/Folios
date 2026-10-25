# Image

`"type": "image"` · can stand in a page's [`elements`](../pages.md#elements) or be a page of its own.

Pictures, kept at their own proportions. Several are shown one at a time, with arrows to step through them.

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `image` | texture path, or list of 1–32 | required | The picture, or several shown in turn, as full texture paths. |
| `title` | text | none | A heading. |
| `text` | text | `""` | A caption under the pictures. |
| `border` | boolean | `true` | Whether the pictures are framed. |
| `height` | integer | `90` | The most room the pictures take, in page pixels. 16 to 140. |

```json
{
  "type": "image",
  "image": [
    "mymod:textures/gui/guide/valley.png",
    "mymod:textures/gui/guide/rift.png"
  ],
  "title": "Pictures",
  "text": "Several images are shown in turn."
}
```
