# Java API

A mod doesn't need any code to ship a book: put the book's JSON in your mod's `assets/<modid>/folios/` and it loads
like one from a resource pack. The Java API is for giving and opening books, unlocking content from code, feeding live
lists from your mod's content, and adding your own page elements, reveal effects, objectives and styles.

| Class | Side | For | Page |
| --- | --- | --- | --- |
| `net.ixdarklord.folios.api.FoliosBooks` | both | Book items, and opening a book from the server. | [Opening books](books.md) |
| `net.ixdarklord.folios.api.FoliosBookItem` | both | An item of your own that opens a book. | [Book items](books.md#book-items) |
| `net.ixdarklord.folios.api.FoliosSources` | both | Live lists pages can show. | [Live lists](live-lists.md) |
| `net.ixdarklord.folios.api.FoliosQuests` | server | Server quests: finishing, flags, values, and being told. | [Server quests](quests.md) |
| `net.ixdarklord.folios.api.client.FoliosGuides` | client | The loaded books, opening them, read state, flags. | [Opening books](books.md), [Flags](progress.md#flags) |
| `net.ixdarklord.folios.api.client.FoliosLinks` | client | Kinds of link, and popup windows. | [Links and popups](links.md) |
| `net.ixdarklord.folios.api.client.FoliosObjectives` | client | Values for quest objectives. | [Objectives](progress.md#objective-sources) |
| `net.ixdarklord.folios.api.client.page.FoliosPageElements` | client | New element types. | [Page elements](page-elements.md) |
| `net.ixdarklord.folios.api.client.FoliosReveals` | client | New reveal effects. | [Reveal effects](reveal-effects.md) |
| `net.ixdarklord.folios.api.client.FoliosStyles` | client | New ways to show a book. | [Styles](styles.md) |

!!! note mc-121 mc-120 "1.21 and 1.20.1"
    The examples use `Identifier`, as on 26.1 and later. On 1.21 and 1.20.1 the same methods take a
    `ResourceLocation`.

## Setup

Folios is built on [CoolCatLib](https://modrinth.com/mod/coolcatlib) and needs it at runtime. Add both to your
development environment, and declare both as dependencies of your mod.

Both are on the [ModResources Maven](https://ixdarklord.github.io/ModResources/), free to use with no token. Its page
lists every version, ready to copy:

```groovy title="build.gradle"
repositories {
    maven {
        name = "ModResources"
        url = "https://ixdarklord.github.io/ModResources/maven/"
    }
}

dependencies {
    compileOnly "net.ixdarklord.folios:folios-common:<folios version>"        // in the common module
    implementation "net.ixdarklord.folios:folios-fabric:<folios version>"     // in each loader's module
    implementation "net.ixdarklord.coolcatlib:coolcatlib-fabric:<coolcatlib version>"
}
```

Artifacts are `net.ixdarklord.folios:folios-<module>:<minecraft>+<mod version>`, such as
<span class="mc-v mc-all"><code>26.1.2+3.0</code></span><span class="mc-v mc-only mc-26"><code>26.1.2+3.0</code></span><span class="mc-v mc-only mc-121"><code>1.21.1+3.0</code></span><span class="mc-v mc-only mc-120"><code>1.20.1+3.0</code></span>,
the module being <span class="mc-v mc-all"><code>common</code>, <code>fabric</code>, <code>neoforge</code> or <code>forge</code></span><span class="mc-v mc-only mc-26"><code>common</code>, <code>fabric</code> or <code>neoforge</code></span><span class="mc-v mc-only mc-121"><code>common</code>, <code>fabric</code>, <code>neoforge</code> or <code>forge</code></span><span class="mc-v mc-only mc-120"><code>common</code>, <code>fabric</code> or <code>forge</code></span>.<span class="mc-v mc-all"> On 1.21.1 and older, use <code>modImplementation</code> for the loader jars.</span><span class="mc-v mc-only mc-121 mc-120"> Use <code>modImplementation</code> for the loader jars.</span>

They are on Modrinth too, so [Modrinth's Maven](https://support.modrinth.com/en/articles/8801191-modrinth-maven) works
as well, by project slug: `maven.modrinth:folios:<folios version>` and `maven.modrinth:coolcatlib:<coolcatlib version>`.

## Coming from 2.x

Every API class was renamed from `Guide…` to `Folios…` in 3.0, with no old names kept, so code written against 2.x
needs its imports and class names updated. The packages are the same.

| 2.x | 3.0 |
| --- | --- |
| `GuidePageTypes` | `FoliosPageElements` |
| `GuidePage` | `FoliosPageElement` |
| `Guides` | `FoliosGuides` |
| `GuideBooks`, `GuideBookItem` | `FoliosBooks`, `FoliosBookItem` |
| `GuideBook`, `GuideCategory`, `GuideEntry`, `GuideLock` | `FoliosBook`, `FoliosCategory`, `FoliosEntry`, `FoliosLock` |
| `GuideObjective`, `GuideObjectives` | `FoliosObjective`, `FoliosObjectives` |
| `GuideReveal`, `GuideReveals`, `GuideStyles` | `FoliosReveal`, `FoliosReveals`, `FoliosStyles` |
| `GuideIcon`, `GuideLayout`, `GuidePainter`, `GuidePalette`, `GuideStack`, `GuideText` | `FoliosIcon`, `FoliosLayout`, `FoliosPainter`, `FoliosPalette`, `FoliosStack`, `FoliosText` |

Nested types keep their names (`FoliosPageElement.Laid`, `FoliosBook.Location`, `FoliosReveal.Look`). A few types
changed shape with the JSON:

- `FoliosLock` is `(book, requires, secret)`: a list of `FoliosObjective` conditions, all needed, in place of an
  advancement and a flag. `FoliosLock.read(json, book)` reads one.
- `FoliosObjective.read(json)` reads any condition, and `FoliosObjective.readAll(json)` one or a list.

The JSON changes for books are on [Migrating to 3.0](../migrating-to-3.0.md).
