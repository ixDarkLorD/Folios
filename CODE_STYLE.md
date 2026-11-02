# Folios code style

How code in this repository is written. Follow it for new code and for code you touch.
The same file sits in every version folder (26.1.2, 26.2, 26.3, 1.21, 1.20.1).

## Imports

- Import every class you use. Never write a package path inside code:
  `StyleSettings.transitions()`, not `net.ixdarklord.coolcatcanvas.internal.client.gui.style.StyleSettings.transitions()`.
  The same goes for nested types (`HolderSet.Named`, import `HolderSet`), annotations and `instanceof` patterns.
  Annotated types too: write `@Nullable FoliosPopup popup`, not `net.ixdarklord.folios.api.client.page.@Nullable FoliosPopup popup`.
- Classes in the same package and in `java.lang` need no import.
- Keep the full name only when an import would be ambiguous, and only for that one use:
  - two different classes with the same simple name are used in one file (import the more common one);
  - the simple name is already taken by another import, a type declared in the file, a class in the same package, or a `java.lang` class.
- Layout: one group of non-`java` imports in alphabetical order, a blank line, then the `java.*`/`javax.*` imports.
  No wildcard (`*`) imports and no static imports.
- Remove imports you no longer use.

## Packages

- `api` is public. Keep it stable and documented with Javadoc. Anything an addon or book author may call goes here.
- `internal` is everything else: no promises to other mods, free to change between releases.
  - `internal/client`: rendering, screens, the hand-held book, pages (`client/page`), painters (`client/paint`).
  - `internal/mixin`: all mixins. Client-only ones live in `mixin/client`.
  - `internal/compat` (1.20.1 and 1.21 only): the layer that lets shared code call the 26.x drawing API.

## Comments

- `internal`: no comments, or a short one only where the code would truly mislead without it.
- `api`: Javadoc on public types and members. Explain behaviour and JSON keys, not implementation.

## Naming and types

- Classes: `PascalCase`. Prefix public API types with `Folios` (`FoliosBook`, `FoliosPainter`).
- Constants: `UPPER_SNAKE_CASE`, `private static final` unless shared.
- Prefer `final` classes, and `record`s for plain data.
- Nullability: `@Nullable` from `org.jspecify.annotations` on 26.x, `org.jetbrains.annotations` on 1.20.1 and 1.21.
- Resource ids: `Identifier` on 26.x, `ResourceLocation` on 1.20.1 and 1.21. Build Folios ids with `Folios.rl(path)`.

## Mixins

- One class per target: `<Target>Mixin` for injections, `<Target>Accessor` for field access, `<Target>Invoker` for method calls.
- Register each new mixin in `common/src/main/resources/folios.common.mixins.json` (client ones under `"client"`).
- Prefix every injected method and `@Unique` member with `folios$` (constants `FOLIOS$`), so they never clash with other mods.
- Keep logic out of mixins: hook in, then call a method in `internal/client` that does the work.

## Rendering

- Draw through `FoliosPainter` (`ScreenPainter` on screen, `WorldPainter` in the hand), not straight through Minecraft's graphics, so pages work in both views.
- Static decoration made of many fills (edges, ribbons, tucks) goes inside `painter.baked("site", () -> ...)`: it is drawn once into a texture and reused.
  - Only fills may go inside a baked block. Textures, text or items drawn inside would end up under the baked image.
  - Give each distinct shape its own site name. Shapes that animate are fine; they draw directly until they settle.
- Avoid per-frame allocations and per-element work in draw paths; check a change with a profile, not just the frame counter.

## Versions

- Write a change once on 26.1.2, then port it to 26.2, 26.3, 1.21 and 1.20.1, adjusting only what differs (ids, nullability, the compat drawing layer).
- Every version must compile (`./gradlew compileJava`) before a change is done.
