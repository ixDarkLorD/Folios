# Folios

Guide books for Minecraft mods, written as JSON in a resource pack.

This private repository holds the source of the Folios website: its homepage, documentation and license, at
**https://ixdarklord.github.io/Folios/**. The mod's own code is in [ixDarkLorD/Folios](https://github.com/ixDarkLorD/Folios),
and bugs and ideas go to its [Issues](https://github.com/ixDarkLorD/Folios/issues).

- Download: [CurseForge](https://www.curseforge.com/minecraft/mc-mods/folios) · [Modrinth](https://modrinth.com/mod/folios)
- License: [Folios License](https://ixdarklord.github.io/Folios/license/)

The site is built with [Zensical](https://zensical.org): `pip install zensical`, then `zensical serve` to preview and
`zensical build` to build. Pushing to `main` builds it and publishes it to the `gh-pages` branch of the public Folios
repository (the `FOLIOS_DEPLOY_KEY` secret is a deploy key with write access there), which GitHub Pages serves.
