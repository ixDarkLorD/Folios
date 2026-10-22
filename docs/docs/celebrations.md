# Celebrations

Finishing part of a book can be celebrated: a toast, a sound, sparkles and, for mastery, fireworks. A book celebrates
nothing unless it says so with `celebrations` in [book.json](book.md), and it chooses which events are celebrated and
with which parts.

## Turning them on

`celebrations` decides how finishing part of the book is celebrated. A book that says nothing celebrates nothing.
`true` turns everything on:

```json
"celebrations": true
```

An object turns everything on but what it turns off. Its keys are the **parts** of a celebration, for every event, and
the **events**, each `false`, `true`, or an object of its own parts:

```json
"celebrations": {
  "sound": false,
  "entry": false,
  "mastery": { "particles": false }
}
```

| Part | What it is |
| --- | --- |
| `toast` | A toast in the book's own look, naming what was finished. |
| `sound` | A chime; the challenge fanfare for mastery. |
| `particles` | Sparkles rising round the player; a burst of totem sparks for mastery. |
| `fireworks` | Fireworks in the book's colours over the player. For mastery only. |

| Event | When |
| --- | --- |
| `objective` | An objective of a quest is done. |
| `entry` | An entry is read. |
| `category` | Every open entry of a category is read. |
| `quest` | Every objective of a quest is done. |
| `mastery` | Every objective of every quest in the book is done. |

A part or event left out of the object is on. Each is celebrated once, ever: what has been finished is remembered even
while celebrations are off, so turning them on later plays nothing old.

## What each event shows

Finishing part of a book can be celebrated, once, ever, per book. What each event shows, with every part on:

- **An objective** of a quest done: a toast naming the objective, a chime and a few sparkles.
- **An entry** read: a toast naming the entry, a chime and a few sparkles.
- **A category** read through (every open entry in it read): a toast with the category's icon, a chime and a ring of
  sparkles.
- **A quest** done (every objective of it): a toast with the quest's icon, a chime and a ring of sparkles.
- **Mastery** (every objective of every quest in the book done): a toast with the trophy, the challenge fanfare, totem
  sparks and fireworks in the book's colours.

The toasts are in the book's own look: its [panel colours](colours.md#panel-colours), the icon in a slot, a
small heading ("Quest complete", "Book mastered") over the name, and a line along the foot running down while it
shows.

What has been celebrated and what has been read are kept with the player's progress in the book. What is finished
while celebrations are off is remembered too, so turning them on later plays nothing old.
