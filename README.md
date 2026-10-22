<p align="center">
  <img src="https://ixdarklord.github.io/Folios/assets/banner.png" alt="Folios" width="100%">
</p>

<p align="center"><b>Guide books for Minecraft mods, written as JSON in a resource pack.</b></p>

<p align="center">
  <a href="https://modrinth.com/mod/folios">Modrinth</a> ·
  <a href="https://www.curseforge.com/minecraft/mc-mods/folios">CurseForge</a> ·
  <a href="https://ixdarklord.github.io/Folios/docs/">Documentation</a> ·
  <a href="https://ixdarklord.github.io/Folios/downloads/">Book template</a> ·
  <a href="https://discord.gg/ZapGEPqm2V">Discord</a>
</p>

<p align="center">
  <img src="https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/badges/fabric.svg" alt="Fabric" height="26">
  <img src="https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/badges/neoforge.svg" alt="NeoForge" height="26">
  <img src="https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/badges/forge.svg" alt="Forge" height="26">
</p>

<hr>

Folios gives a mod a manual players actually want to open: a book on the screen, or held open in their hands in the
world, with recipes, builds, quests and more. A book is a folder of JSON in a resource pack, so packs can add, translate
and restyle books, and **F3 + T** reloads them while you write. No code is needed to make one.

## ✨ Features

- **📖 Two ways to read.** *Screen* books lie in the middle of the screen with ribbon tabs, bookmarks, search and light or
  dark paper. *Handheld* books are raised from the hotbar and opened in your hands, their leaves turning in the world;
  players nearby see you reading and can read along. Every page works in both.
- **🧩 Every kind of page.** Text in a small Markdown, spotlit items, images, crafting, smelting, stonecutting and smithing
  recipes, turning 3D scenes of entities and builds, maps of linked places, links, quests, and spreads across both pages.
- **🏗️ Builds you can follow.** A build lists the blocks it takes and, with one click, stands in the world as ghost blocks
  to place layer by layer, with a panel tracking what is left.
- **🏆 Quests and progress.** Objectives followed live (advancements, entries read, items carried, flags set from code),
  toasts when a quest is done, and celebrations when part of the book is mastered. Entries can be locked and hidden.
- **✨ Pages that reveal.** Each spread can appear with an effect (ink, burn, runes and more): every time, once, or only on
  pages the reader hasn't seen.
- **💾 Progress that follows the player.** What is read, bookmarks and where the reader left off are kept on the server,
  per player, or on each copy of a book that keeps its own.
- **🌐 Translatable.** Every text can be a translation key, so a book's words live in lang files.
- **🔌 Made for mods.** Open books from code, give players book items, and add your own page types, reveal effects,
  styles and quest objectives.

## 📦 Versions

Each Minecraft version has its own branch.

| Branch | Minecraft | Loaders |
|---|---|---|
| [`26.3`](https://github.com/ixDarkLorD/Folios/tree/26.3) | 26.3 | Fabric, NeoForge |
| [`26.2`](https://github.com/ixDarkLorD/Folios/tree/26.2) | 26.2 | Fabric, NeoForge |
| [`26.1.2`](https://github.com/ixDarkLorD/Folios/tree/26.1.2) | 26.1 – 26.1.2 | Fabric, NeoForge |
| [`1.21-1.21.1`](https://github.com/ixDarkLorD/Folios/tree/1.21-1.21.1) | 1.21 – 1.21.1 | Fabric, NeoForge, Forge |
| [`1.20.1`](https://github.com/ixDarkLorD/Folios/tree/1.20.1) | 1.20.1 | Fabric, Forge |

Folios needs [CoolCatLib](https://modrinth.com/mod/coolcatlib), and Fabric API on Fabric.

## ✍️ Write a book

1. Download the [book template](https://ixdarklord.github.io/Folios/downloads/), a resource pack with one complete book,
   and put it in `.minecraft/resourcepacks/`.
2. Rename `assets/mymod` to your namespace and `folios/my_guide` to your book's id.
3. In game: `/give @s folios:guide_book[folios:guide_book="mymod:my_guide"]`.
4. Edit the JSON and press **F3 + T** to see it change.

```
assets/<namespace>/folios/<book>/
  book.json                         the book: name, style, colours, reveal, where progress is kept...
  categories/<id>.json              one per tab
  entries/<category>/<id>.json      one per entry, its pages in order
assets/<namespace>/lang/en_us.json  the book's words
```

The [documentation](https://ixdarklord.github.io/Folios/docs/) covers every key, page type and option.

## 🔌 For mod developers

Ship a book in your mod's own `assets` and it loads with no code. To use the API, depend on Folios from
[Modrinth's Maven](https://support.modrinth.com/en/articles/8801191-modrinth-maven):

```groovy
repositories {
    exclusiveContent {
        forRepository { maven { url = "https://api.modrinth.com/maven" } }
        filter { includeGroup "maven.modrinth" }
    }
}

dependencies {
    modImplementation "maven.modrinth:folios:<folios version>"
    modImplementation "maven.modrinth:coolcatlib:<coolcatlib version>"
}
```

```java
GuideBooks.open(serverPlayer, MyMod.id("handbook"));             // open a book from the server
ItemStack book = GuideBooks.createStack(MyMod.id("handbook"));    // a book item to give
Guides.open(MyMod.id("handbook"), "basics/first_steps");          // on the client, at an entry
```

Page types, reveal effects, styles and objectives each have a register call: see
[For mod developers](https://ixdarklord.github.io/Folios/docs/for-mod-developers/).

## 🛠️ Building

The `common` module and one module per loader are in the root folder, with the mod's settings in `gradle.properties`;
`testmods/` holds the test mods with the demo books, and `templates/` the book template.

```bash
./gradlew build                      # the jars, in each loader's build/libs
./gradlew :testmod-fabric:runClient  # a game with the demo books (or :testmod-neoforge / :testmod-forge)
```

Building needs the Java its Minecraft version runs on (25 for 26.x, 21 for 1.21, 17 for 1.20.1) and the matching
CoolCatLib (`coolcatlib_version` in `gradle.properties`) in a Maven repository Gradle can reach: for example published
to Maven Local from [CoolCatLib](https://github.com/ixDarkLorD/CoolCatLib) with `./gradlew publishToMavenLocal`.

## 🐛 Issues and ideas

Report bugs and suggest ideas in [Issues](https://github.com/ixDarkLorD/Folios/issues), or ask on
[Discord](https://discord.gg/ZapGEPqm2V).

## 📜 License

Folios is under the [Folios License](https://ixdarklord.github.io/Folios/license/) ([`LICENSE`](LICENSE)). You can use it
in modpacks, write books and addons for it, and reuse its code or fork it, as long as you credit Folios and ixDarkLorD
with a link. Re-uploading Folios unchanged, or a fork that calls itself Folios, is not allowed. The book template is
public domain (CC0).
