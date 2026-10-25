/*
 * The book upgrader page: takes a folder, a .zip or a drop, finds every Folios book in it, upgrades each with
 * upgrader.js (the same rules as the mod) and shows what changed, then hands back a .zip of the whole input with the
 * upgraded files in place. Everything happens in the browser: nothing is uploaded.
 *
 * The page holds <div id="folios-upgrader" data-assets="../assets/upgrader/">; this script fills it, and adds
 * upgrader.css from that folder. The site uses instant navigation, so setup runs on every page change (Material's
 * document$) and only acts where that div is.
 */
(function () {
    "use strict";

    const JSZIP = "https://cdnjs.cloudflare.com/ajax/libs/jszip/3.10.1/jszip.min.js";

    // ---- Small helpers ----

    // An element, its attributes and children (strings become text: nothing from a file is ever read as HTML).
    function el(tag, attributes, ...children) {
        const node = document.createElement(tag);
        for (const [key, value] of Object.entries(attributes || {})) {
            if (value === false || value === null || value === undefined) continue;
            if (key.startsWith("on")) node.addEventListener(key.slice(2), value);
            else if (key === "text") node.textContent = value;
            else node.setAttribute(key, value === true ? "" : value);
        }
        for (const child of children.flat()) {
            if (child === null || child === undefined || child === false) continue;
            node.append(child instanceof Node ? child : document.createTextNode(String(child)));
        }
        return node;
    }

    function loadScript(src) {
        return new Promise((resolve, reject) => {
            const script = el("script", {src});
            script.onload = resolve;
            script.onerror = () => reject(new Error("Couldn't load " + src));
            document.head.append(script);
        });
    }

    const plural = (count, one, many) => count + " " + (count === 1 ? one : many || one + "s");

    // ---- Reading what was given ----

    // Every file of a folder dropped on the page, by its path from the folder's parent (as the folder picker gives).
    async function readDropped(entry, into) {
        if (entry.isFile) {
            const file = await new Promise((resolve, reject) => entry.file(resolve, reject));
            into.set(entry.fullPath.replace(/^\/+/, ""), {blob: file});
            return;
        }
        const reader = entry.createReader();
        // readEntries hands back a batch at a time, until an empty one.
        for (;;) {
            const batch = await new Promise((resolve, reject) => reader.readEntries(resolve, reject));
            if (batch.length === 0) break;
            for (const child of batch) await readDropped(child, into);
        }
    }

    /** The files given, by path (forward slashes), each a {blob} or a {zip} entry, and a name for the download. */
    class Input {
        constructor(name, files) {
            this.name = name;
            this.files = files;
        }

        static fromFolder(fileList) {
            const files = new Map();
            for (const file of fileList) files.set((file.webkitRelativePath || file.name).replace(/\\/g, "/"), {blob: file});
            const first = files.keys().next().value || "book";
            return new Input(first.split("/")[0], files);
        }

        static async fromZip(file) {
            const zip = await window.JSZip.loadAsync(file);
            const files = new Map();
            zip.forEach((path, entry) => {
                if (!entry.dir) files.set(path.replace(/\\/g, "/"), {zip: entry});
            });
            return new Input(file.name.replace(/\.zip$/i, ""), files);
        }

        text(path) {
            const source = this.files.get(path);
            return source.zip ? source.zip.async("string") : source.blob.text();
        }

        // What goes in the download for a file left as it is: its bytes, untouched.
        content(path) {
            const source = this.files.get(path);
            return source.zip ? source.zip.async("uint8array") : source.blob;
        }
    }

    // ---- Line diff, for the before/after view ----

    // Which lines of `before` went and which of `after` came: a longest common subsequence of lines.
    function diff(before, after) {
        const a = before.split("\n"), b = after.split("\n");
        const gone = new Set(), came = new Set();
        // Too big to compare line by line in a page: shown without marks.
        if ((a.length + 1) * (b.length + 1) > 4e6) return {a, b, gone, came};
        const width = b.length + 1;
        const table = new Uint32Array((a.length + 1) * width);
        for (let i = a.length - 1; i >= 0; i--) {
            for (let j = b.length - 1; j >= 0; j--) {
                table[i * width + j] = a[i] === b[j] ? table[(i + 1) * width + j + 1] + 1 : Math.max(table[(i + 1) * width + j], table[i * width + j + 1]);
            }
        }
        let i = 0, j = 0;
        while (i < a.length && j < b.length) {
            if (a[i] === b[j]) { i++; j++; }
            else if (table[(i + 1) * width + j] >= table[i * width + j + 1]) gone.add(i++);
            else came.add(j++);
        }
        while (i < a.length) gone.add(i++);
        while (j < b.length) came.add(j++);
        return {a, b, gone, came};
    }

    function codeView(title, lines, marked, kind) {
        return el("figure", {class: "fu-code"},
            el("figcaption", {text: title}),
            el("pre", {}, el("code", {}, lines.map((line, index) =>
                el("span", {class: marked.has(index) ? "fu-line fu-line--" + kind : "fu-line"}, line)))));
    }

    // ---- The page ----

    class Page {
        constructor(root) {
            this.root = root;
            this.assets = new URL(root.dataset.assets || "../assets/upgrader/", document.baseURI);
            this.steps = null;
            this.input = null;
            this.books = [];
        }

        async start() {
            // The page's stylesheet, from beside this script (in the head, so it stays put as the page is redrawn).
            const css = new URL("upgrader.css", this.assets).href;
            if (![...document.querySelectorAll("link[rel=stylesheet]")].some(link => link.href === css)) document.head.append(el("link", {rel: "stylesheet", href: css}));
            this.root.replaceChildren(this.picker(), this.status = el("div", {class: "fu-status", role: "status", "aria-live": "polite"}),
                this.results = el("div", {class: "fu-results"}));
            try {
                const need = [];
                if (!window.FoliosUpgrader) need.push(loadScript(new URL("upgrader.js", this.assets).href));
                if (!window.JSZip) need.push(loadScript(JSZIP));
                await Promise.all(need);
                // Each format's rules, from the copy of the mod's files next to upgrader.js.
                this.steps = {};
                for (const format of window.FoliosUpgrader.FORMAT.KNOWN.slice(1)) {
                    const response = await fetch(new URL(format + ".json", this.assets));
                    if (!response.ok) throw new Error("Couldn't load the " + format + " rules (" + response.status + ")");
                    this.steps[format] = window.FoliosUpgrader.parseJson(await response.text());
                }
            } catch (error) {
                this.say("The upgrader couldn't start: " + error.message, "error");
                this.root.classList.add("fu--broken");
            }
        }

        // The drop zone and its two buttons.
        picker() {
            const folder = el("input", {type: "file", webkitdirectory: true, multiple: true, hidden: true,
                onchange: () => folder.files.length && this.take(() => Input.fromFolder(folder.files))});
            const zip = el("input", {type: "file", accept: ".zip,application/zip", hidden: true,
                onchange: () => zip.files.length && this.take(() => Input.fromZip(zip.files[0]))});
            const zone = el("div", {class: "fu-drop"},
                el("p", {class: "fu-drop__title", text: "Drop a book folder or a .zip here"}),
                el("div", {class: "fu-drop__buttons"},
                    el("button", {type: "button", class: "md-button md-button--primary", onclick: () => { folder.value = ""; folder.click(); }}, "Choose a folder"),
                    el("button", {type: "button", class: "md-button", onclick: () => { zip.value = ""; zip.click(); }}, "Choose a .zip")),
                el("p", {class: "fu-drop__note", text: "A resource pack, an assets folder or one book's folder. Nothing leaves your browser."}),
                folder, zip);
            zone.addEventListener("dragover", event => {
                event.preventDefault();
                zone.classList.add("fu-drop--over");
            });
            zone.addEventListener("dragleave", () => zone.classList.remove("fu-drop--over"));
            zone.addEventListener("drop", event => {
                event.preventDefault();
                zone.classList.remove("fu-drop--over");
                const items = [...event.dataTransfer.items].map(item => item.webkitGetAsEntry && item.webkitGetAsEntry()).filter(Boolean);
                const files = [...event.dataTransfer.files];
                if (files.length === 1 && /\.zip$/i.test(files[0].name) && (items.length === 0 || items[0].isFile)) {
                    this.take(() => Input.fromZip(files[0]));
                } else if (items.length) {
                    this.take(async () => {
                        const read = new Map();
                        for (const item of items) await readDropped(item, read);
                        return new Input(items.length === 1 ? items[0].name : "books", read);
                    });
                }
            });
            return zone;
        }

        say(message, kind) {
            this.status.replaceChildren(message ? el("p", {class: "fu-note" + (kind ? " fu-note--" + kind : "")}, message) : "");
        }

        // Reads what was given, then finds and upgrades its books.
        async take(read) {
            if (!this.steps) return;
            this.say("Reading…");
            this.results.replaceChildren();
            try {
                this.input = await read();
                await this.upgradeAll();
            } catch (error) {
                this.say("Couldn't read that: " + error.message, "error");
            }
        }

        async upgradeAll() {
            const Upgrader = window.FoliosUpgrader;
            const paths = [...this.input.files.keys()];
            // (A Mac's zip carries a __MACOSX copy of every file's metadata: never a book.)
            const found = Upgrader.findBooks(paths).filter(book => !book.folder.split("/").includes("__MACOSX"));
            this.books = [];
            for (const book of found) {
                const files = Upgrader.bookFiles(paths, book.folder);
                const texts = new Map();
                for (const file of files) texts.set(file, await this.input.text(book.folder ? book.folder + "/" + file : file));
                this.books.push({...book, files, texts, namespaceGiven: ""});
            }
            this.books.forEach(book => this.upgrade(book));
            this.render();
        }

        // One book's upgrade, again whenever its namespace is typed in.
        upgrade(book) {
            const Upgrader = window.FoliosUpgrader;
            book.id = (book.namespace || book.namespaceGiven || "namespace") + ":" + book.path;
            try {
                book.result = Upgrader.upgradeBook(book.id, book.texts, this.steps);
                book.error = null;
            } catch (error) {
                book.result = null;
                book.error = error.message;
            }
            book.quests = null;
            if (book.result && this.serverQuests) {
                const texts = book.result.upToDate ? book.texts : new Map([...book.result.files].map(([path, file]) => [path, file.after]));
                book.quests = Upgrader.toServerQuests(book.id, texts);
            }
        }

        // Whether a book has anything to write: an upgrade, or quests moved to the server's.
        static changes(book) {
            return book.result && (!book.result.upToDate || (book.quests && book.quests.quests.size > 0));
        }

        render() {
            const books = this.books;
            if (books.length === 0) {
                this.say("No book found. A book is a folder with a book.json in it (in a resource pack, assets/<mod id>/folios/<book>/).", "warn");
                this.results.replaceChildren();
                return;
            }
            const toUpgrade = books.filter(book => book.result && !book.result.upToDate).length;
            const toWrite = books.filter(Page.changes).length;
            const upToDate = books.filter(book => book.result && book.result.upToDate).length;
            const broken = books.filter(book => book.error).length;
            const parts = [plural(toUpgrade, "to upgrade", "to upgrade"), plural(upToDate, "up to date", "up to date")];
            if (broken) parts.push(plural(broken, "couldn't be read", "couldn't be read"));
            this.status.replaceChildren(el("div", {class: "fu-summary"},
                el("p", {}, el("strong", {}, plural(books.length, "book") + " found"), " in ", el("code", {}, this.input.name), ": " + parts.join(", ") + "."),
                el("label", {class: "fu-option"},
                    el("input", {type: "checkbox", checked: !!this.serverQuests, onchange: event => {
                        this.serverQuests = event.currentTarget.checked;
                        this.books.forEach(book => this.upgrade(book));
                        this.render();
                    }}),
                    " Make quests server quests: their objectives move to a data pack (",
                    el("code", {}, "data/<mod id>/folios/quests/"), "), where the server follows them and can give rewards."),
                el("div", {class: "fu-summary__buttons"},
                    el("button", {type: "button", class: "md-button md-button--primary", disabled: toWrite === 0,
                        onclick: event => this.download(event.currentTarget)}, "Download upgraded .zip"))));
            this.results.replaceChildren(...books.map(book => this.bookView(book)));
        }

        bookView(book) {
            const result = book.result;
            const badge = book.error ? el("span", {class: "fu-badge fu-badge--error"}, "Couldn't be read")
                : result.upToDate ? el("span", {class: "fu-badge fu-badge--ok"}, "Up to date (" + result.from + ")")
                    : el("span", {class: "fu-badge fu-badge--upgrade"}, result.from + " → " + window.FoliosUpgrader.FORMAT.CURRENT);
            const header = el("header", {class: "fu-book__head"},
                el("h2", {class: "fu-book__id"}, book.id), badge,
                el("p", {class: "fu-book__folder"}, el("code", {}, (book.folder || this.input.name) + "/")));
            const body = [];
            if (book.error) body.push(el("p", {class: "fu-note fu-note--error"}, book.error));
            else if (result.upToDate) body.push(...this.checkView(result));
            else body.push(...this.namespaceField(book), ...this.upgradeView(book));
            if (book.quests) body.push(...this.questsView(book));
            return el("section", {class: "fu-book"}, header, body);
        }

        // A book without a namespace folder above it (just its own folder was given) that needs one for its name.
        namespaceField(book) {
            const result = book.result;
            const named = (result.changes.lines.get("book.json") || []).some(line => line.startsWith("name set to"));
            if (book.namespace || !named) return [];
            const input = el("input", {type: "text", class: "fu-input", value: book.namespaceGiven, placeholder: "your mod id", spellcheck: "false",
                onchange: () => {
                    book.namespaceGiven = input.value.trim();
                    this.upgrade(book);
                    this.render();
                }});
            return [el("div", {class: "fu-note fu-note--warn"},
                el("p", {}, "This book has no ", el("code", {}, "name"), ", so it gets the translation key 2.x gave it, which holds your mod id. "
                    + "Only the book's folder was given, so type the mod id (the folder above folios/):"),
                el("label", {class: "fu-field"}, "Mod id ", input))];
        }

        upgradeView(book) {
            const result = book.result;
            const whys = result.changes.whys;
            const shown = [], same = [];
            for (const [path, file] of result.files) {
                if (file.moved || file.before !== file.after) shown.push([path, file]);
                else same.push(path);
            }
            const views = shown.map(([path, file]) => {
                // Notes are kept by the path a file had when they were made: a moved file has some under each.
                const groups = [];
                for (const at of [file.moved, path]) {
                    for (const group of (at && whys.get(at)) || []) {
                        const had = groups.find(one => one.why === group.why);
                        if (had) had.lines.push(...group.lines);
                        else groups.push({why: group.why, lines: [...group.lines]});
                    }
                }
                const replaced = file.moved && book.texts.has(path);
                return this.fileView(path, file, groups, replaced);
            });
            const out = [el("p", {class: "fu-book__count"}, plural(shown.length, "file") + " upgraded"
                + (result.moves.length ? ", " + plural(result.moves.length, "moved", "moved") : "")
                + (same.length ? "; " + plural(same.length, "file") + " already fine, left as " + (same.length === 1 ? "it is." : "they are.") : "."))];
            out.push(...views);
            return out;
        }

        fileView(path, file, groups, replaced) {
            const count = groups.reduce((sum, group) => sum + group.lines.length, 0);
            const changes = el("ul", {class: "fu-whys"}, groups.map(group => el("li", {},
                el("p", {class: "fu-why"}, group.why),
                el("ul", {class: "fu-lines"}, group.lines.map(line => el("li", {}, line))))));
            const {a, b, gone, came} = diff(file.before, file.after);
            return el("details", {class: "fu-file"},
                el("summary", {},
                    el("code", {class: "fu-file__path"}, path),
                    file.moved ? el("span", {class: "fu-file__moved"}, "moved from ", el("code", {}, file.moved)) : null,
                    el("span", {class: "fu-file__count"}, plural(count, "change"))),
                replaced ? el("p", {class: "fu-note fu-note--warn"}, "A file was already at this path: the moved one takes its place.") : null,
                changes,
                el("div", {class: "fu-diff"},
                    codeView("Before" + (file.moved ? " (" + file.moved + ")" : ""), a, gone, "gone"),
                    codeView("After", b, came, "came")));
        }

        // The quests moved to the server's: each quest file, and what stayed in the book.
        questsView(book) {
            const moved = book.quests.quests;
            if (moved.size === 0 && book.quests.notes.length === 0) return [el("p", {class: "fu-book__count"}, "No quests to move to the server.")];
            const out = [el("p", {class: "fu-book__count"}, plural(moved.size, "quest") + " moved to the server's, in ",
                el("code", {}, this.questFolder(book) + "/"), ":")];
            out.push(el("ul", {class: "fu-lines"}, book.quests.notes.map(note => el("li", {}, el("code", {}, note.path), " " + note.line))));
            for (const [path, quest] of moved) {
                const lines = quest.text.split("\n");
                out.push(el("details", {class: "fu-file"},
                    el("summary", {}, el("code", {class: "fu-file__path"}, path), el("span", {class: "fu-file__count"}, quest.id)),
                    el("div", {class: "fu-diff"}, codeView("Quest file", lines, new Set(lines.map((line, at) => at)), "came"))));
            }
            return out;
        }

        // Where a book's quest files go: data/<namespace>/folios/quests/, beside its assets/ when there is one.
        questFolder(book) {
            const namespace = book.id.slice(0, book.id.indexOf(":"));
            if (book.assets !== null && /(^|\/)assets\/[^/]+$/.test(book.assets)) return book.assets.replace(/assets\/([^/]+)$/, "data/$1") + "/folios/quests";
            return "data/" + namespace + "/folios/quests";
        }

        // A book already in the current format: what in it still has an older shape, if anything.
        checkView(result) {
            const old = result.check.whys;
            if (old.size === 0) return [el("p", {class: "fu-book__count"}, "Nothing to upgrade, and nothing in it has an older shape.")];
            const paths = [...old.keys()].sort();
            return [el("div", {class: "fu-note fu-note--warn"},
                el("p", {}, "Already 3.0, so it isn't upgraded, but some of it still has a 2.x shape, which Folios doesn't upgrade in a 3.0 book. "
                    + "Fix these by hand (or remove its ", el("code", {}, "format_version"), " to have it upgraded here):")),
            ...paths.map(path => el("div", {class: "fu-check"},
                el("p", {}, el("code", {}, path)),
                el("ul", {class: "fu-whys"}, old.get(path).map(group => el("li", {},
                    el("p", {class: "fu-why"}, group.why),
                    el("ul", {class: "fu-lines"}, group.lines.map(line => el("li", {}, line))))))))];
        }

        // The whole input again, with each upgraded book's changed files written and its moved ones moved.
        async download(button) {
            const Upgrader = window.FoliosUpgrader;
            button.disabled = true;
            const label = button.textContent;
            button.textContent = "Zipping…";
            try {
                const written = new Map();
                const dropped = new Set();
                const langs = new Map();
                for (const book of this.books) {
                    if (!Page.changes(book)) continue;
                    const full = file => (book.folder ? book.folder + "/" + file : file);
                    if (book.quests) {
                        // The quests moved: their files in the data pack, and the book's files naming them.
                        for (const [path, quest] of book.quests.quests) written.set(this.questFolder(book) + "/" + path, quest.text + "\n");
                        for (const [path, file] of book.quests.files) written.set(full(path), file.after + "\n");
                    }
                    if (book.result.upToDate) continue;
                    for (const file of book.files) dropped.add(full(file));
                    for (const [path, file] of book.result.files) {
                        // A file the upgrade didn't change keeps its own bytes and formatting; one the quests changed
                        // is already written.
                        if (book.quests && book.quests.files.has(path)) continue;
                        if (!file.moved && file.before === file.after) dropped.delete(full(path));
                        else written.set(full(path), file.after + "\n");
                    }
                    // Translations an upgrade adds go in the namespace's lang/en_us.json (none in 3.0, as yet).
                    if (book.result.lang.size && book.assets !== null) {
                        const lang = book.assets + "/lang/en_us.json";
                        if (!langs.has(lang)) langs.set(lang, new Map());
                        book.result.lang.forEach((value, key) => langs.get(lang).set(key, value));
                    }
                }
                for (const [path, added] of langs) {
                    const merged = this.input.files.has(path) ? Upgrader.parseJson(await this.input.text(path)) : new Map();
                    added.forEach((value, key) => merged.set(key, value));
                    written.set(path, Upgrader.writeJson(merged) + "\n");
                }
                const zip = new window.JSZip();
                for (const path of this.input.files.keys()) {
                    if (!dropped.has(path) && !written.has(path)) zip.file(path, this.input.content(path));
                }
                for (const [path, text] of written) zip.file(path, text);
                const blob = await zip.generateAsync({type: "blob", compression: "DEFLATE"});
                const link = el("a", {href: URL.createObjectURL(blob), download: this.input.name + "-folios-3.0.zip"});
                document.body.append(link);
                link.click();
                link.remove();
                setTimeout(() => URL.revokeObjectURL(link.href), 60000);
            } catch (error) {
                this.say("Couldn't make the zip: " + error.message, "error");
            } finally {
                button.disabled = false;
                button.textContent = label;
            }
        }
    }

    // ---- Start ----

    function setup() {
        const root = document.getElementById("folios-upgrader");
        if (!root || root.dataset.ready) return;
        root.dataset.ready = "true";
        new Page(root).start();
    }

    // Set up once however many times this script runs (instant navigation runs it again on each visit), then on every
    // page shown. (Down here: document$ calls setup at once, which needs Page defined.)
    if (window.__foliosUpgraderPage) window.__foliosUpgraderPage();
    else {
        window.__foliosUpgraderPage = setup;
        if (window.document$ && window.document$.subscribe) window.document$.subscribe(setup);
        else if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", setup);
        else setup();
    }
})();
