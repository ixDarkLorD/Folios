# Entity

`"type": "entity"` · can stand in a page's [`elements`](../pages.md#elements) or be a page of its own.

A creature in a window of its own, standing on a grid with its shadow, turning slowly. Drag it to turn it, use the
mouse wheel to zoom, and press the clasp to put it back.

| Key | Type | Default | What it does |
| --- | --- | --- | --- |
| `entity` | identifier | required | The entity type, such as `minecraft:allay`. |
| `title` | text | the entity's name | A heading. Without one, the entity's name is shown under the window. |
| `text` | text | `""` | Text under the window. |
| `zoom` | number | `1.0` | How near it is drawn. 0.25 to 4. |
| `spin` | number | `24` | How fast it turns by itself, in degrees per second. `0` stops it. |
| `yaw` | number | `30` | The angle it starts at, in degrees. |
| `height` | integer | `74` | The window's height in page pixels. 40 to 130. |

```json
{
  "type": "entity",
  "entity": "minecraft:allay",
  "title": "Allay",
  "text": "A helpful spirit.",
  "spin": 12
}
```
