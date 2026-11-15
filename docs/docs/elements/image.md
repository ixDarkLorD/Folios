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
| `frames` | integer | none | Animates a picture made of this many frames, one under the other, played top to bottom. 1 to 256. |
| `frame_time` | integer | `2` | How long each frame shows, in ticks (20 a second), when `frames` is set. 1 to 200. |

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

## Moving pictures

A picture can move in three ways:

- **A strip of frames.** Draw the frames one under the other in one texture and set `frames` (and `frame_time` for
  the speed). Each frame is the texture's width wide and its height divided by `frames` tall.
- **An animated texture.** A texture with a `.mcmeta` file beside it, as Minecraft's own animated textures have, plays
  by itself: its frames are square unless the `.mcmeta` gives a `height`, and its `frametime` sets the speed.
- **A GIF.** A path ending in `.gif` plays as the GIF does, each frame for its own time. At most 128 frames are read.

```json
{
  "type": "image",
  "image": "mymod:textures/gui/guide/furnace_working.png",
  "frames": 8,
  "frame_time": 3,
  "text": "The furnace lights once it has fuel."
}
```

Moving pictures play in screen books and in a book held in the hands alike.
