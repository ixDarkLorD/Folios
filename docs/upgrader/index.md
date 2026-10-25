---
title: Book upgrader
description: Rewrites a Folios 2.x guide book into the 3.0 format, in your browser.
---
# Book upgrader

Folios 3.0 reads books in a new shape. It still loads a 2.x book, upgrading it as it loads with a warning in the log,
but a book is better kept in the shape Folios reads. This page does that upgrade for you, with the same rules as the
mod:

1. Pick a book's folder or a `.zip` (a whole resource pack, or just the book's folder), or drop either below.
2. See what will change in each file, and why, with each file before and after.
3. Download the upgraded book as a `.zip`: everything you gave it, with the upgraded files in place.

It all runs in your browser: nothing is uploaded. For what changed in 3.0 and what to check by hand afterwards, see
[Migrating to 3.0](../docs/migrating-to-3.0/).

!!! note "Entries that move"

    An entry whose `"category"` isn't the folder it is in moves into its category's folder, which changes its id. Links
    to it by its old id (`entry:<old path>`) need updating by hand: the upgrader names each file it moved. Folders
    under the entry's own come with it. An entry that would land on another stays where it is, its `"category"` kept,
    for you to settle.

!!! note "What it leaves to you"

    The upgrader never overwrites or drops what you wrote. Where a 2.x key's new name is already taken (both
    `"anchor"` and `"marker"`, say), it keeps both and tells you to merge them. A colour it can't read as one
    (`"red"`, `true`, `2.5`) is left as it is, to write as `"#RRGGBB"`; a colour written as a number keeps its alpha.

!!! note "Making quests server quests"

    Tick **Make quests server quests** to move each quest page's objectives to a
    [server quest](../docs/quests/#server-quests): a file in `data/<mod id>/folios/quests/`, beside the book's
    `assets/`, which the server follows and can reward. The page then names it with `"quest"`, and keeps its own title,
    text and reward text. Add `"rewards"` to the quest files for what they give. Quests that ask for `read_category` or
    `read_book` stay in the book, as the server can't follow those. This works on 3.0 books too.

A book that already says `"format_version": "3.0"` isn't changed. If parts of it still have a 2.x shape, which
Folios doesn't upgrade in a 3.0 book, the upgrader lists them so you can fix them.

<div id="folios-upgrader" class="fu" data-assets="../assets/upgrader/">
<noscript><p>The upgrader needs JavaScript.</p></noscript>
</div>
<script src="https://cdnjs.cloudflare.com/ajax/libs/jszip/3.10.1/jszip.min.js"></script>
<script src="../assets/upgrader/upgrader.js"></script>
<script src="../assets/upgrader/upgrader-ui.js"></script>
