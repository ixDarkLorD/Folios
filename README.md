<p align="center">
  <img src="https://raw.githubusercontent.com/ixDarkLorD/Folios/26.1.2/common/src/main/resources/mod_logo.png" alt="Folios" width="160">
</p>

<h1 align="center">Folios</h1>

<p align="center">
  <b>Write the manual your mod deserves.</b><br>
  Guide books for Minecraft mods, written as JSON in a resource pack.
</p>

<p align="center">
  <a href="https://modrinth.com/mod/folios"><img src="https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/badges/folios/modrinth.svg" alt="Modrinth" height="26"></a>
  <a href="https://www.curseforge.com/minecraft/mc-mods/folios"><img src="https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/badges/folios/curseforge.svg" alt="CurseForge" height="26"></a>
  <a href="https://ixdarklord.github.io/Folios/"><img src="https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/badges/folios/docs.svg" alt="Docs" height="26"></a>
  <a href="https://discord.gg/ZapGEPqm2V"><img src="https://img.shields.io/discord/1173075408556654592?style=flat&logo=discord&logoColor=white&label=&labelColor=2a9d8f&color=3A3F58" alt="Discord" width="110" height="26"></a>
</p>
<p align="center">
  <img src="https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/badges/folios/fabric.svg" alt="Fabric" height="26">
  <img src="https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/badges/folios/neoforge.svg" alt="NeoForge" height="26">
  <img src="https://raw.githubusercontent.com/ixDarkLorD/ModResources/main/badges/folios/forge.svg" alt="Forge" height="26">
</p>

<hr>

## ✨ About

Folios gives a mod a manual players actually want to open: a book on the screen, or held open in their hands in the
world, with recipes, builds, quests and more. A book is a folder of JSON in a resource pack, so packs can add, translate
and restyle books, and **F3 + T** reloads them while you write. No code is needed to make one.

## 🧩 Features

- **📖 Two ways to read.** *Screen* books lie in the middle of the screen with ribbon tabs, bookmarks, search and light or
  dark paper. *Handheld* books are opened in your hands, their leaves turning in the world, and players nearby can read
  along. Every page works in both.
- **🗂️ Every kind of page.** Text in a small Markdown, spotlit items, images, crafting, smelting, stonecutting and
  smithing recipes, turning 3D scenes of entities and builds, maps of linked places, links, quests, and spreads.
- **🏗️ Builds you can follow.** A build lists the blocks it takes and stands in the world as ghost blocks to place layer
  by layer, with a panel tracking what is left.
- **🏆 Quests and progress.** Objectives followed live, toasts when a quest is done, and celebrations when part of the
  book is mastered. Entries can be locked and hidden.
- **✨ Pages that reveal.** Each spread can appear with an effect (ink, burn, runes and more): every time, once, or only
  on pages the reader hasn't seen.
- **💾 Progress that follows the player.** What is read, bookmarks and where the reader left off are kept on the server,
  per player, or on each copy of a book.
- **🌐 Translatable.** Every text can be a translation key, so a book's words live in lang files.
- **🔌 Made for mods.** Open books from code, give book items, and add your own page types, reveal effects, styles and
  objectives.

### ✍️ Write a book

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

## 📦 Download

| CurseForge | Modrinth |
|---|---|
| [folios](https://www.curseforge.com/minecraft/mc-mods/folios) | [folios](https://modrinth.com/mod/folios) |

It needs [CoolCatLib](https://modrinth.com/mod/coolcatlib), and [Fabric API](https://modrinth.com/mod/fabric-api) on
Fabric.

## 🧭 Versions

Each Minecraft version lives on its own branch.

| Minecraft | Loaders | Branch | Status | Latest |
|---|---|---|:-:|---|
| **26.3** | Fabric, NeoForge | [`26.3`](https://github.com/ixDarkLorD/Folios/tree/26.3) | ✅ Supported | 26.3+1.0 |
| **26.2** | Fabric, NeoForge | [`26.2`](https://github.com/ixDarkLorD/Folios/tree/26.2) | ✅ Supported | 26.2+1.0 |
| **26.1 – 26.1.2** | Fabric, NeoForge | [`26.1.2`](https://github.com/ixDarkLorD/Folios/tree/26.1.2) | ✅ Supported | 26.1.2+1.0 |
| **1.21 – 1.21.1** | Fabric, NeoForge, Forge | [`1.21-1.21.1`](https://github.com/ixDarkLorD/Folios/tree/1.21-1.21.1) | ✅ Supported | 1.21.1+1.0 |
| **1.20.1** | Fabric, Forge | [`1.20.1`](https://github.com/ixDarkLorD/Folios/tree/1.20.1) | ✅ Supported | 1.20.1+1.0 |

## 🔌 For mod developers

Ship a book in your mod's own `assets` and it loads with no code. To use the API, Folios is on the
[ModResources Maven](https://ixdarklord.github.io/ModResources/), free to use with no token:

```groovy
repositories {
    maven {
        name = "ModResources"
        url = "https://ixdarklord.github.io/ModResources/maven/"
    }
}

dependencies {
    compileOnly "net.ixdarklord.folios:folios-common:26.1.2+1.0"            // in the common module
    implementation "net.ixdarklord.folios:folios-fabric:26.1.2+1.0"         // in each loader's module
    implementation "net.ixdarklord.coolcatlib:coolcatlib-fabric:26.1.2-6"   // and CoolCatLib, which Folios runs on
}
```

Artifacts are `net.ixdarklord.folios:folios-<module>:<minecraft>+<version>`, the module being `common`, `fabric`,
`neoforge` or `forge`. On 1.21.1 and older, use `modImplementation` for the loader jars. The
[Maven's page](https://ixdarklord.github.io/ModResources/) lists every version, ready to copy.

```java
FoliosBooks.open(serverPlayer, MyMod.id("handbook"));             // open a book from the server
ItemStack book = FoliosBooks.createStack(MyMod.id("handbook"));    // a book item to give
FoliosGuides.open(MyMod.id("handbook"), "basics/first_steps");          // on the client, at an entry
```

Page types, reveal effects, styles and objectives each have a register call: see
[For mod developers](https://ixdarklord.github.io/Folios/docs/for-mod-developers/).

## 🛠️ Building

Check out the branch for your Minecraft version, then run:

```bash
./gradlew build                      # the jars, in each loader's build/libs
./gradlew :testmod-fabric:runClient  # a game with the demo books (or :testmod-neoforge / :testmod-forge)
```

It needs the Java its Minecraft version runs on (25 for 26.x, 21 for 1.21, 17 for 1.20.1). CoolCatLib comes from the
ModResources Maven, and the build scripts and dependency versions from
[ModResources](https://github.com/ixDarkLorD/ModResources); each release is published to its Maven automatically.
`testmods/` holds the test mods with the demo books, and `templates/` the book template.

## 🐞 Issues and ideas

Found a bug or have an idea? Open an [issue](https://github.com/ixDarkLorD/Folios/issues), or drop by the
[Discord](https://discord.gg/ZapGEPqm2V).

## 📄 License

Folios is under the [Folios License](https://ixdarklord.github.io/Folios/license/) ([`LICENSE`](LICENSE)). You can use it
in modpacks, write books and addons for it, and reuse its code or fork it, as long as you credit Folios and ixDarkLorD
with a link. Re-uploading Folios unchanged, or a fork that calls itself Folios, is not allowed. The book template is
public domain (CC0).
