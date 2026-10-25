/*
 * Folios book upgrader: rewrites a book written for an older Folios into the current format, the same way the mod does
 * as a book loads (BookUpgrader.java in the mod, net.ixdarklord.folios.internal.format). This file is the core only: no
 * page, no zip; the upgrader page (upgrader-ui.js) and tools/check_upgrader.mjs both use it.
 *
 * KEEP IN STEP WITH THE MOD:
 *  - 3.0.json next to this file is a copy of the mod's common/src/main/resources/folios/upgrades/3.0.json. Re-copy it
 *    whenever the mod's rules change (and add the next format's file, and its number to FORMAT.KNOWN, when there is one).
 *  - The "run" steps below are ports of BookUpgrader.run(), under the same names. A change there is a change here.
 *  - tools/check_upgrader.mjs runs both on the same books and compares them file by file: run it after any change.
 *
 * JSON is read the way Gson reads it (leniently, numbers kept as written, objects keeping their key order) and written
 * the way the mod's Gson writes it (pretty, two spaces, null members left out), so the output is the same text.
 * Objects are Maps, arrays are arrays, numbers are JsonNumber (their text), JSON null is null and a missing key is
 * undefined.
 */
(function (root, factory) {
    const api = factory();
    if (typeof module === "object" && module.exports) module.exports = api;
    else root.FoliosUpgrader = api;
})(typeof self !== "undefined" ? self : this, function () {
    "use strict";

    // ---- Formats (FoliosFormat.java) ----

    const FORMAT = {
        /** The format of books written for Folios 2.x, and of any book that doesn't say. */
        LEGACY: "2.0",
        /** The format this upgrader writes. */
        CURRENT: "3.0",
        /** Every format so far, oldest first: each after the first has an upgrades/<format>.json. */
        KNOWN: ["2.0", "3.0"],
    };

    /** A version's numbers ("3.10" is 3 then 10); null when it isn't dot-separated numbers. */
    function parseVersion(version) {
        const parts = javaStrip(version).split(".");
        if (parts.length === 0 || parts[0] === "") return null;
        const numbers = [];
        for (const part of parts) {
            if (part === "" || part.length > 9 || !/^[0-9]+$/.test(part)) return null;
            numbers.push(parseInt(part, 10));
        }
        return numbers;
    }

    /** Compares two versions number by number, a missing number counting as 0. */
    function compareVersions(first, second) {
        const a = parseVersion(first);
        const b = parseVersion(second);
        if (a === null || b === null) throw new Error("Not a version: " + (a === null ? first : second));
        for (let index = 0; index < Math.max(a.length, b.length); index++) {
            const x = index < a.length ? a[index] : 0;
            const y = index < b.length ? b[index] : 0;
            if (x !== y) return x < y ? -1 : 1;
        }
        return 0;
    }

    /** The format a book naming `version` is read in: the newest known at or below it, LEGACY below them all. */
    function formatOf(version) {
        let found = FORMAT.LEGACY;
        for (const known of FORMAT.KNOWN) if (compareVersions(known, version) <= 0) found = known;
        return found;
    }

    // ---- JSON, as Gson reads and writes it ----

    /** A number, kept as written ("1.0" stays "1.0"), as Gson's LazilyParsedNumber does. */
    class JsonNumber {
        constructor(text) { this.text = text; }
        toString() { return this.text; }
    }

    // Java's String.strip(): Character.isWhitespace at both ends (no-break spaces aren't whitespace to Java).
    const JAVA_SPACE = "[\\t\\n\\u000B\\f\\r\\u001C-\\u001F \\u1680\\u2000-\\u2006\\u2008-\\u200A\\u2028\\u2029\\u205F\\u3000]";
    const STRIP = new RegExp("^" + JAVA_SPACE + "+|" + JAVA_SPACE + "+$", "g");
    function javaStrip(text) { return text.replace(STRIP, ""); }

    // Characters that end an unquoted word (JsonReader.isLiteral).
    const NOT_LITERAL = "/\\;#={}[]:, \t\f\r\n";
    const NUMBER = /^-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[eE][+-]?[0-9]+)?$/;

    /**
     * Parses JSON as Gson's JsonParser.parseString does: leniently, so comments (// /* # ), single quotes, unquoted
     * words, "=" or "=>" for ":", ";" for "," and an empty slot in an array (read as null) are all accepted. Throws an
     * Error with the line and column on anything Gson would refuse.
     */
    function parseJson(text) {
        let pos = text.charCodeAt(0) === 0xFEFF ? 1 : 0;

        function fail(message) {
            const before = text.slice(0, pos);
            const line = before.split("\n").length;
            const column = pos - before.lastIndexOf("\n");
            throw new Error(message + " at line " + line + " column " + column);
        }

        // The next character that isn't whitespace or inside a comment; -1 at the end.
        function next() {
            while (pos < text.length) {
                const c = text[pos++];
                if (c === " " || c === "\n" || c === "\t" || c === "\r") continue;
                if (c === "/" && text[pos] === "*") {
                    const end = text.indexOf("*/", pos + 1);
                    if (end < 0) fail("Unterminated comment");
                    pos = end + 2;
                    continue;
                }
                if (c === "#" || (c === "/" && text[pos] === "/")) {
                    while (pos < text.length && text[pos] !== "\n" && text[pos] !== "\r") pos++;
                    continue;
                }
                return c;
            }
            return -1;
        }

        function quoted(quote) {
            let out = "";
            for (;;) {
                if (pos >= text.length) fail("Unterminated string");
                const c = text[pos++];
                if (c === quote) return out;
                if (c !== "\\") { out += c; continue; }
                if (pos >= text.length) fail("Unterminated escape sequence");
                const escaped = text[pos++];
                switch (escaped) {
                    case "u": {
                        const hex = text.slice(pos, pos + 4);
                        if (!/^[0-9a-fA-F]{4}$/.test(hex)) fail("Malformed Unicode escape");
                        out += String.fromCharCode(parseInt(hex, 16));
                        pos += 4;
                        break;
                    }
                    case "t": out += "\t"; break;
                    case "b": out += "\b"; break;
                    case "n": out += "\n"; break;
                    case "r": out += "\r"; break;
                    case "f": out += "\f"; break;
                    case "\n": case "'": case "\"": case "\\": case "/": out += escaped; break;
                    default: fail("Invalid escape sequence");
                }
            }
        }

        function word() {
            const start = pos;
            while (pos < text.length && !NOT_LITERAL.includes(text[pos])) pos++;
            return text.slice(start, pos);
        }

        // A keyword is matched letter by letter in either case (Gson reads "True" as true), then must end there.
        function keyword(literal) {
            for (const [name, value] of [["true", true], ["false", false], ["null", null]]) {
                if (literal.length !== name.length) continue;
                let same = true;
                for (let index = 0; index < name.length; index++) {
                    const c = literal[index];
                    if (c !== name[index] && c !== name[index].toUpperCase()) same = false;
                }
                if (same) return {value};
            }
            return null;
        }

        // `inArray`: the value is an array's element, where "," or "]" with nothing before it means null.
        function value(c, inArray) {
            switch (c) {
                case -1: fail("End of input");
                // falls through
                case "{": return object();
                case "[": return array();
                case "\"": return quoted("\"");
                case "'": return quoted("'");
                case "]": case ",": case ";":
                    if (inArray) { pos--; return null; }
                    fail("Unexpected value");
            }
            pos--;
            if (NOT_LITERAL.includes(c)) fail("Expected value");
            const literal = word();
            const known = keyword(literal);
            if (known) return known.value;
            if (NUMBER.test(literal)) return new JsonNumber(literal);
            return literal;
        }

        function object() {
            const json = new Map();
            let c = next();
            if (c === "}") return json;
            for (;;) {
                let name;
                if (c === "\"") name = quoted("\"");
                else if (c === "'") name = quoted("'");
                else if (c === "}") fail("Expected name");
                else if (c !== -1 && !NOT_LITERAL.includes(c)) { pos--; name = word(); }
                else fail("Expected name");
                c = next();
                if (c === "=") { if (text[pos] === ">") pos++; }
                else if (c !== ":") fail("Expected ':'");
                // A key written twice keeps its first place and its last value, as in Gson's JsonObject.
                json.set(name, value(next(), false));
                c = next();
                if (c === "}") return json;
                if (c !== "," && c !== ";") fail("Unterminated object");
                c = next();
            }
        }

        function array() {
            const json = [];
            let c = next();
            if (c === "]") return json;
            for (;;) {
                json.push(value(c, true));
                c = next();
                if (c === "]") return json;
                if (c !== "," && c !== ";") fail("Unterminated array");
                c = next();
            }
        }

        const json = value(next(), false);
        if (json !== null && next() !== -1) fail("Did not consume the entire document");
        return json;
    }

    // Gson's string escaping, with HTML escaping off: quotes, backslashes, control characters, U+2028 and U+2029.
    function quote(text) {
        let out = "\"";
        for (const c of text) {
            const code = c.charCodeAt(0);
            if (c === "\"") out += "\\\"";
            else if (c === "\\") out += "\\\\";
            else if (c === "\t") out += "\\t";
            else if (c === "\b") out += "\\b";
            else if (c === "\n") out += "\\n";
            else if (c === "\r") out += "\\r";
            else if (c === "\f") out += "\\f";
            else if (code < 0x20 || code === 0x2028 || code === 0x2029) out += "\\u" + code.toString(16).padStart(4, "0");
            else out += c;
        }
        return out + "\"";
    }

    /**
     * Writes JSON as the mod's Gson does (pretty printing, HTML escaping off): two-space indents, "key": value, empty
     * objects and arrays as {} and [], and members whose value is null left out (Gson doesn't serialize nulls).
     */
    function writeJson(json, indent = "") {
        if (json === null) return "null";
        if (json instanceof JsonNumber) return json.text;
        if (typeof json === "boolean") return String(json);
        if (typeof json === "string") return quote(json);
        const inner = indent + "  ";
        if (Array.isArray(json)) {
            if (json.length === 0) return "[]";
            return "[\n" + json.map(element => inner + writeJson(element, inner)).join(",\n") + "\n" + indent + "]";
        }
        const members = [...json].filter(([, value]) => value !== null);
        if (members.length === 0) return "{}";
        return "{\n" + members.map(([key, value]) => inner + quote(key) + ": " + writeJson(value, inner)).join(",\n") + "\n" + indent + "}";
    }

    /** JSON on one line, as Gson's JsonElement.toString() writes it: no spaces, and null members kept. */
    function compactJson(json) {
        if (json === null) return "null";
        if (json instanceof JsonNumber) return json.text;
        if (typeof json === "boolean") return String(json);
        if (typeof json === "string") return quote(json);
        if (Array.isArray(json)) return "[" + json.map(compactJson).join(",") + "]";
        return "{" + [...json].map(([key, value]) => quote(key) + ":" + compactJson(value)).join(",") + "}";
    }

    const isObject = json => json instanceof Map;
    const isArray = json => Array.isArray(json);
    const isPrimitive = json => typeof json === "string" || typeof json === "boolean" || json instanceof JsonNumber;

    function deepCopy(json) {
        if (isObject(json)) return new Map([...json].map(([key, value]) => [key, deepCopy(value)]));
        if (isArray(json)) return json.map(deepCopy);
        return json;
    }

    // Gson's getAsString: a primitive's text, a one-element array's element's; anything else throws.
    function asString(json) {
        if (typeof json === "string") return json;
        if (json instanceof JsonNumber) return json.text;
        if (typeof json === "boolean") return String(json);
        if (isArray(json) && json.length === 1) return asString(json[0]);
        throw new Error("Not a string: " + (json === null ? "null" : writeJson(json)));
    }

    // A number's whole value, exactly (BigDecimal.longValueExact()): null for a fraction or a number out of a long's range.
    function exactInteger(number) {
        const [, sign, whole, fraction = "", exponent = "0"] = /^(-?)([0-9]+)(?:\.([0-9]+))?(?:[eE]([+-]?[0-9]+))?$/.exec(number.text);
        const digits = whole + fraction;
        const shift = parseInt(exponent, 10) - fraction.length;
        let value;
        if (/^0*$/.test(digits)) value = 0n;
        // 10^19 and up is past a long, whatever the digits.
        else if (shift > 19) return null;
        else if (shift >= 0) value = BigInt(digits) * 10n ** BigInt(shift);
        else {
            const cut = digits.length + shift;
            if (/[1-9]/.test(digits.slice(Math.max(0, cut)))) return null;
            value = BigInt(digits.slice(0, Math.max(0, cut)) || "0");
        }
        if (sign) value = -value;
        return value < -(2n ** 63n) || value >= 2n ** 63n ? null : value;
    }

    // Gson's JsonElement.equals: numbers by value, objects by members whatever their order, arrays element by element.
    function jsonEquals(a, b) {
        if (a === b) return true;
        if (a === undefined || b === undefined || a === null || b === null) return false;
        if (a instanceof JsonNumber && b instanceof JsonNumber) {
            const x = Number(a.text), y = Number(b.text);
            return x === y || (Number.isNaN(x) && Number.isNaN(y));
        }
        if (isArray(a) && isArray(b)) return a.length === b.length && a.every((element, index) => jsonEquals(element, b[index]));
        if (isObject(a) && isObject(b)) {
            if (a.size !== b.size) return false;
            for (const [key, value] of a) if (!b.has(key) || !jsonEquals(value, b.get(key))) return false;
            return true;
        }
        return false;
    }

    // Java's String.split("\\."): trailing empty parts dropped ("" alone stays one empty key).
    function keysOf(path) {
        if (path === "") return [""];
        const keys = path.split(".");
        while (keys.length > 0 && keys[keys.length - 1] === "") keys.pop();
        if (keys.length === 0) throw new Error("Not a key path: " + path);
        return keys;
    }

    function get(json, path) {
        let at = json;
        for (const key of keysOf(path)) {
            if (!isObject(at) || !at.has(key)) return undefined;
            at = at.get(key);
        }
        return at;
    }

    // A value at a dotted path, taken out; objects it leaves empty on the way are taken out too.
    function take(json, path) {
        const keys = keysOf(path);
        const chain = [];
        let at = json;
        for (let index = 0; index < keys.length - 1; index++) {
            if (!at.has(keys[index]) || !isObject(at.get(keys[index]))) return undefined;
            chain.push(at);
            at = at.get(keys[index]);
        }
        const value = at.get(keys[keys.length - 1]);
        if (value === undefined) return undefined;
        at.delete(keys[keys.length - 1]);
        for (let index = chain.length - 1; index >= 0 && at.size === 0; index--) {
            chain[index].delete(keys[index]);
            at = chain[index];
        }
        return value;
    }

    // A value put at a dotted path, making the objects on the way (and replacing anything there that isn't one).
    function put(json, path, value) {
        const keys = keysOf(path);
        let at = json;
        for (let index = 0; index < keys.length - 1; index++) {
            if (!at.has(keys[index]) || !isObject(at.get(keys[index]))) at.set(keys[index], new Map());
            at = at.get(keys[index]);
        }
        at.set(keys[keys.length - 1], value);
    }

    // A value moved from one dotted path to another; left where it is, and said, when the other is taken.
    function move(json, from, to, done) {
        if (get(json, from) === undefined) return;
        if (present(get(json, to))) {
            done.push(kept(from, to));
            return;
        }
        put(json, to, take(json, from));
        done.push(from + " moved to " + to);
    }

    // Whether there is a value: something other than nothing or null.
    const present = value => value !== undefined && value !== null;

    const kept = (from, to) => from + " left as it is: " + to + " is already there (merge them by hand)";

    // Map.delete that hands back what was there (undefined when nothing), like Gson's JsonObject.remove.
    function remove(json, key) {
        const value = json.get(key);
        json.delete(key);
        return value;
    }

    // ---- The upgrade (BookUpgrader.java) ----

    /** What changed in a book, by file: the mod's lines, and the same lines grouped under the "why" of their change. */
    class Changes {
        constructor() {
            this.lines = new Map();
            this.whys = new Map();
        }

        note(path, line, why) {
            if (!this.lines.has(path)) this.lines.set(path, []);
            this.lines.get(path).push(line);
            if (!this.whys.has(path)) this.whys.set(path, []);
            const groups = this.whys.get(path);
            let group = groups.find(one => one.why === why);
            if (!group) groups.push(group = {why, lines: []});
            group.lines.push(line);
        }

        /** The lines by file, files in the order the mod's TreeMap keeps them (by path). */
        sorted() {
            return new Map([...this.lines].sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0)));
        }
    }

    // A change's options, read as the mod reads them.
    const has = (change, key) => change.has(key);
    const flag = (change, key) => change.get(key) === true;

    /**
     * Upgrades a book's files from format `from` to `to`. The files given are left as they are.
     *
     * @param book   the book's id, namespace:path
     * @param files  Map of path in the book's folder ("book.json", "entries/basics/first_steps.json") to parsed JSON
     * @param steps  each format's rules, by format ("3.0" → the parsed upgrades/3.0.json)
     * @returns {{files: Map, lang: Map, changes: Changes, moves: Array}} the files rewritten; translations to add to
     *          the book's lang file; what changed; and which files moved where
     */
    function upgrade(book, files, from, to, steps) {
        const out = new Map([...files].map(([path, json]) => [path, deepCopy(json)]));
        const lang = new Map();
        const changes = new Changes();
        const moves = [];
        for (const format of FORMAT.KNOWN) {
            if (compareVersions(format, from) <= 0 || compareVersions(format, to) > 0) continue;
            for (const change of step(steps, format).get("changes")) apply(book, change, out, lang, changes, moves, false);
            const bookJson = out.get("book.json");
            if (bookJson !== undefined) bookJson.set("format_version", format);
        }
        if (out.has("book.json")) changes.note("book.json", "format_version set to \"" + to + "\"", FORMAT_WHY);
        return {files: out, lang, changes, moves};
    }

    const FORMAT_WHY = "A book says which format it is written in: \"format_version\".";

    /**
     * What in a book written in `format` still has the shape of an older format: each change upgrading to it would
     * make, by file (a Changes; no lines when the book is clean). The files given are left as they are.
     */
    function check(book, files, format, steps) {
        const copy = new Map([...files].map(([path, json]) => [path, deepCopy(json)]));
        const changes = new Changes();
        for (const change of step(steps, format).get("changes")) apply(book, change, copy, new Map(), changes, [], true);
        return changes;
    }

    function step(steps, format) {
        const rules = steps[format];
        if (!rules) throw new Error("Folios has no upgrade to format " + format);
        return typeof rules === "string" ? parseJson(rules) : rules;
    }

    function apply(book, change, files, lang, changes, moves, checking) {
        if (checking && has(change, "check") && !flag(change, "check")) return;
        if (checking && has(change, "run") && !(has(change, "check") && flag(change, "check"))) return;
        const why = has(change, "why") ? change.get("why") : "";
        const inKind = change.get("in");
        const where = has(change, "where") ? change.get("where") : null;
        // An entry's file moves to its category's folder: the one change of a file's path.
        if (has(change, "run") && change.get("run") === "entry_folder") {
            entryFolders(files, changes, moves, why);
            return;
        }
        for (const [path, json] of files) {
            for (const target of targets(inKind, path, json)) {
                if (where !== null && !matches(target.json, where)) continue;
                const done = [];
                if (has(change, "rename")) {
                    for (const [from, to] of change.get("rename")) {
                        if (!target.json.has(from)) continue;
                        if (present(target.json.get(to))) {
                            done.push(kept(from, to));
                            continue;
                        }
                        target.json.set(to, remove(target.json, from));
                        done.push(from + " renamed to " + to);
                    }
                }
                if (has(change, "remove")) {
                    for (const key of change.get("remove")) {
                        if (remove(target.json, key) !== undefined) done.push(key + " removed");
                    }
                }
                if (has(change, "move")) {
                    move(target.json, change.get("move").get("from"), change.get("move").get("to"), done);
                }
                if (has(change, "run")) done.push(...run(change.get("run"), book, change, target.json, lang));
                for (const line of done) changes.note(path, target.where === "" ? line : target.where + ": " + line, why);
            }
        }
    }

    // The changes that need code, ported from BookUpgrader.run() under the same names.
    const RUN = {
        // A 2.x book that named no name took guide.<namespace>.<path>.name; 3.0's default is folios.<...>.name.
        legacy_name(json, book) {
            const done = [];
            if (!json.has("name")) {
                const key = "guide." + book.replaceAll(":", ".").replaceAll("/", ".") + ".name";
                json.set("name", key);
                done.push("name set to the translation 2.x used, " + key);
            }
            return done;
        },

        // The cover's colour, texture, lettering and model gathered into "cover"; the boards' texture renamed.
        cover_group(json) {
            const done = [];
            if (json.has("cover") && isPrimitive(json.get("cover"))) {
                const color = remove(json, "cover");
                put(json, "cover.color", color);
                done.push("cover moved to cover.color");
            }
            for (const [from, to] of [["cover_texture", "cover.texture"], ["cover_text", "cover.text"], ["models.cover", "cover.model"],
                ["textures.cover", "textures.boards"]]) {
                move(json, from, to, done);
            }
            return done;
        },

        // "progress": "book" kept reading in the copy; anything else, by player.
        progress_scope(json) {
            const done = [];
            if (json.has("progress") && present(json.get("progress_scope"))) done.push(kept("progress", "progress_scope"));
            else if (json.has("progress")) {
                const scope = asString(remove(json, "progress")) === "book" ? "copy" : "player";
                json.set("progress_scope", scope);
                done.push("progress is progress_scope: \"" + scope + "\"");
            }
            return done;
        },

        color_strings(json) {
            const done = [];
            for (const key of ["accent", "color", "cover.color"]) colorString(json, key, done);
            for (const group of ["colors", "colors.dark", "panel"]) {
                const at = get(json, group);
                if (at === undefined || !isObject(at)) continue;
                for (const key of [...at.keys()]) colorString(json, group + "." + key, done);
            }
            return done;
        },

        // An entry's "text" made the first of its pages.
        entry_text(json) {
            const done = [];
            if (json.has("text")) {
                const text = remove(json, "text");
                const pages = [text];
                // What "pages" held after it, a list or one page.
                const had = json.get("pages");
                if (had !== undefined && isArray(had)) pages.push(...had);
                else if (present(had)) pages.push(had);
                json.set("pages", pages);
                done.push("text made the first of pages");
            }
            return done;
        },

        // "structures" and "builds" joined into "structure" and "build": one value, or a list of them.
        plural_lists(json) {
            const done = [];
            for (const [many, one] of [["structures", "structure"], ["builds", "build"]]) {
                if (!json.has(many)) continue;
                const all = [];
                // One flat list, whether each was one value or a list.
                for (const part of [remove(json, one), remove(json, many)]) {
                    if (part === undefined) continue;
                    if (isArray(part)) all.push(...part);
                    else all.push(part);
                }
                json.set(one, all.length === 1 ? all[0] : all);
                done.push(many + " joined into " + one);
            }
            return done;
        },

        // A structure's or build's "name" (the words it is shown by) is its "label".
        shown_labels(json) {
            const done = [];
            for (const key of ["structure", "build"]) {
                const value = json.get(key);
                if (value === undefined) continue;
                const shown = isArray(value) ? value : [value];
                for (const one of shown) {
                    if (!isObject(one) || !one.has("name")) continue;
                    if (present(one.get("label"))) {
                        done.push(key + ": " + kept("name", "label"));
                        continue;
                    }
                    one.set("label", remove(one, "name"));
                    done.push(key + ": name renamed to label");
                }
            }
            return done;
        },

        // A spread's "build" written in place is "multiblock"; a map place's "name" is its "label".
        spread_multiblock(json) {
            const done = [];
            if (json.has("build") && isObject(json.get("build")) && present(json.get("multiblock"))) {
                done.push(kept("build", "multiblock"));
            } else if (json.has("build") && isObject(json.get("build"))) {
                json.set("multiblock", remove(json, "build"));
                done.push("build (blocks written in place) renamed to multiblock");
            }
            const nodes = get(json, "map.nodes");
            if (nodes !== undefined && isArray(nodes)) {
                for (const node of nodes) {
                    if (!isObject(node) || !node.has("name")) continue;
                    if (present(node.get("label"))) {
                        done.push("map: a place's " + kept("name", "label"));
                        continue;
                    }
                    node.set("label", remove(node, "name"));
                    done.push("map: a place's name renamed to label");
                }
            }
            return done;
        },

        // A lock's "advancement", "flag" and "obtained" made conditions of "requires", before any it had.
        requires(json) {
            const done = [];
            const conditions = [];
            for (const key of ["advancement", "flag", "obtained"]) {
                if (!json.has(key)) continue;
                conditions.push(new Map([[key, remove(json, key)]]));
                done.push(key + " made a condition of requires");
            }
            if (conditions.length === 0) return done;
            const had = remove(json, "requires");
            if (present(had)) {
                if (isArray(had)) conditions.push(...had);
                else conditions.push(had);
            }
            json.set("requires", conditions.length === 1 ? conditions[0] : conditions);
            return done;
        },
    };

    function run(name, book, change, json, lang) {
        if (!Object.prototype.hasOwnProperty.call(RUN, name)) throw new Error("No upgrade step " + name);
        return RUN[name](json, book, change, lang);
    }

    // A colour written as a number (its alpha kept), or as hex without its "#" or with spaces about it, written
    // "#RRGGBB" or "#AARRGGBB". What can't be a colour is left as it is, and said.
    function colorString(json, path, done) {
        const value = get(json, path);
        if (value === undefined || !isPrimitive(value)) return;
        let written = null;
        if (value instanceof JsonNumber) {
            const whole = exactInteger(value);
            // An ARGB colour, as an int (alpha making it negative) or as the unsigned number.
            if (whole !== null && whole >= -(2n ** 31n) && whole <= 0xFFFFFFFFn) {
                const argb = BigInt.asUintN(32, whole);
                written = "#" + argb.toString(16).toUpperCase().padStart(argb > 0xFFFFFFn ? 8 : 6, "0");
            }
        } else if (typeof value === "string") {
            const text = javaStrip(value);
            const marked = text.startsWith("#");
            const hex = marked ? text.slice(1) : text;
            if (/^(?:[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$/.test(hex)) {
                written = "#" + (marked ? hex : hex.toUpperCase());
                if (written === value) return;
            }
        }
        if (written === null) {
            done.push(path + " is " + compactJson(value) + ", which isn't a colour: left for you to write as \"#RRGGBB\"");
            return;
        }
        take(json, path);
        put(json, path, written);
        done.push(path + " written as \"" + written + "\"");
    }

    // Entries whose "category" isn't the folder they are in: moved into their category's folder, keeping the folders
    // under it (which changes their id, so links to them by the old one need updating by hand), and the key dropped.
    // One that would land on another entry stays where it is, its key kept, for whoever updates the book to settle.
    function entryFolders(files, changes, moves, why) {
        for (const path of [...files.keys()]) {
            if (!path.startsWith("entries/")) continue;
            const json = files.get(path);
            if (!json.has("category")) continue;
            const named = json.get("category");
            if (!isPrimitive(named)) {
                changes.note(path, "category is " + compactJson(named) + ", which isn't a category's id: left for you to fix", why);
                continue;
            }
            const category = asString(named);
            const inside = path.slice("entries/".length);
            const slash = inside.indexOf("/");
            const folder = slash >= 0 ? inside.slice(0, slash) : "";
            if (folder === category) {
                json.delete("category");
                changes.note(path, "category removed (the folder says it)", why);
                continue;
            }
            const moved = "entries/" + category + "/" + (slash >= 0 ? inside.slice(slash + 1) : inside);
            if (files.has(moved)) {
                changes.note(path, "can't move to " + moved + ", the folder of its category " + category
                    + ": another entry is there. Left where it is, with its category, for you to settle", why);
                continue;
            }
            json.delete("category");
            files.delete(path);
            files.set(moved, json);
            moves.push({from: path, to: moved});
            changes.note(path, "moved to " + moved + ", the folder of its category " + category
                + " (its id changes: update links to it by hand)", why);
        }
    }

    // The objects of a file a change of a kind applies to, with where they are in it.
    function targets(inKind, path, json) {
        const found = [];
        const kind = path === "book.json" ? "book" : path.startsWith("categories/") ? "category" : path.startsWith("entries/") ? "entry"
            : path.startsWith("builds/") ? "build" : "";
        if (inKind === kind || (inKind === "lockable" && (kind === "category" || kind === "entry"))) found.push({json, where: ""});
        if (kind !== "entry" || !json.has("pages") || !isArray(json.get("pages"))) return found;
        if (!["page", "element", "objective", "lockable"].includes(inKind)) return found;
        const pages = json.get("pages");
        for (let index = 0; index < pages.length; index++) {
            if (!isObject(pages[index])) continue;
            const page = pages[index];
            const where = "page " + (index + 1);
            if (inKind === "page" || inKind === "lockable") {
                found.push({json: page, where});
                continue;
            }
            // A page written as one element is that element; a page of elements, each of them.
            const elements = [];
            if (!page.has("elements")) elements.push({json: page, where});
            else if (isArray(page.get("elements"))) {
                page.get("elements").forEach((element, at) => {
                    if (isObject(element)) elements.push({json: element, where: where + ", element " + (at + 1)});
                });
            }
            if (inKind === "element") {
                found.push(...elements);
                continue;
            }
            for (const element of elements) {
                if (!element.json.has("objectives") || !isArray(element.json.get("objectives"))) continue;
                element.json.get("objectives").forEach((objective, at) => {
                    if (isObject(objective)) found.push({json: objective, where: element.where + ", objective " + (at + 1)});
                });
            }
        }
        return found;
    }

    function matches(json, where) {
        for (const [key, value] of where) {
            let had = json.get(key);
            // A page or element with no type is text.
            if (had === undefined && key === "type") had = "text";
            if (!jsonEquals(value, had)) return false;
        }
        return true;
    }

    // ---- Books in a folder or zip ----

    /**
     * The books among a list of paths ("a/b/c.json", forward slashes): each folder holding a book.json. As in the mod's
     * own tool, a book is under a "folios" folder (assets/<namespace>/folios/<path>/book.json), its id
     * <namespace>:<path>; a book.json under no "folios" folder is taken as a book folder on its own, its namespace
     * unknown (null).
     *
     * @returns {Array<{folder: string, namespace: string|null, path: string, assets: string|null}>} folder: the book's
     *          folder ("" for the top); assets: the namespace's folder, where its lang/ is
     */
    function findBooks(paths) {
        const books = [];
        for (const path of paths) {
            const parts = path.split("/");
            if (parts[parts.length - 1] !== "book.json") continue;
            const folder = parts.slice(0, -1);
            // The nearest "folios" folder above the book's folder (never the book's folder itself).
            let folios = -1;
            for (let index = folder.length - 2; index >= 0; index--) {
                if (folder[index] === "folios") { folios = index; break; }
            }
            if (folios >= 0) {
                books.push({
                    folder: folder.join("/"),
                    namespace: folios > 0 ? folder[folios - 1] : null,
                    path: folder.slice(folios + 1).join("/"),
                    assets: folios > 0 ? folder.slice(0, folios).join("/") : null,
                });
            } else {
                books.push({folder: folder.join("/"), namespace: null, path: folder.length ? folder[folder.length - 1] : "", assets: null});
            }
        }
        return books;
    }

    /** The .json files of the book in `folder`, by their path in it, in the order the mod's Files.walk reads them. */
    function bookFiles(paths, folder) {
        const prefix = folder === "" ? "" : folder + "/";
        return paths.filter(path => path.startsWith(prefix) && path.endsWith(".json"))
            .map(path => path.slice(prefix.length))
            .sort(walkOrder);
    }

    // Depth-first, names compared ignoring case (as Windows lists a folder; close enough elsewhere).
    function walkOrder(a, b) {
        const x = a.toUpperCase().split("/"), y = b.toUpperCase().split("/");
        for (let index = 0; index < Math.min(x.length, y.length); index++) {
            if (x[index] !== y[index]) return x[index] < y[index] ? -1 : 1;
        }
        return x.length - y.length;
    }

    /**
     * Upgrades one book, as the mod's tool does, from the texts of its files: the step a page or script wants.
     *
     * @param id     the book's id, namespace:path
     * @param texts  Map (or object) of path in the book's folder → file text
     * @param steps  each format's rules, by format ("3.0" → upgrades/3.0.json, as text or parsed)
     * @returns {{from: string, upToDate: boolean, files: Map<string, {before: string|null, after: string, moved: string|null}>,
     *            changes: Changes, moves: Array, lang: Map, check: Changes|null}}
     *          upToDate: the book is already in the current format, and nothing is rewritten (check then says what in
     *          it still has an older shape); files: each output file's text, before (re-indented, null for a file that
     *          moved here) and after; moved: the path a moved file came from
     */
    function upgradeBook(id, texts, steps) {
        const entries = texts instanceof Map ? [...texts] : Object.entries(texts);
        const files = new Map();
        for (const [path, text] of entries) {
            let json;
            try {
                json = parseJson(text);
            } catch (error) {
                throw new Error(path + ": " + error.message);
            }
            if (!isObject(json)) throw new Error(path + ": not a JSON object");
            files.set(path, json);
        }
        const bookJson = files.get("book.json");
        if (bookJson === undefined) throw new Error("No book.json");
        const named = bookJson.get("format_version");
        const from = named !== undefined ? formatOf(asString(named)) : FORMAT.LEGACY;
        const upToDate = compareVersions(from, FORMAT.CURRENT) >= 0;
        if (upToDate) {
            const same = new Map([...files].map(([path, json]) => [path, {before: writeJson(json), after: writeJson(json), moved: null}]));
            return {from, upToDate, files: same, changes: new Changes(), moves: [], lang: new Map(), check: check(id, files, FORMAT.CURRENT, steps)};
        }
        const result = upgrade(id, files, from, FORMAT.CURRENT, steps);
        const movedFrom = new Map(result.moves.map(move => [move.to, move.from]));
        const out = new Map();
        for (const [path, json] of result.files) {
            const moved = movedFrom.get(path) || null;
            const original = files.get(moved || path);
            out.set(path, {before: original === undefined ? null : writeJson(original), after: writeJson(json), moved});
        }
        return {from, upToDate, files: out, changes: result.changes, moves: result.moves, lang: result.lang, check: null};
    }

    // ---- Server quests (Folios 3.0's data packs' folios/quests) ----

    // The conditions a server quest can't follow: the server doesn't have the books.
    const BOOK_ONLY = ["read_category", "read_book"];

    // Whether a condition, or any it holds ("any"), is one only a book can follow.
    function bookOnly(json) {
        if (isArray(json)) return json.some(bookOnly);
        if (!isObject(json)) return false;
        for (const [key, value] of json) {
            if (BOOK_ONLY.includes(key) || bookOnly(value)) return true;
        }
        return false;
    }

    /**
     * Moves a book's quests to server quests: each quest page's (or element's) objectives become a quest file of the
     * server's, and the page names it instead. Quests that ask for what only a book can follow (how much of a category
     * or of the book is read) stay as they are, with a note.
     *
     * @param id    the book's id, namespace:path
     * @param texts Map (or object) of path in the book's folder → file text, in the current format
     * @returns {{files: Map<string, {before: string, after: string}>, quests: Map<string, {id: string, text: string}>,
     *            notes: Array<{path: string, line: string}>}} files: the book's files the move changed; quests: each
     *          quest file, by its path under data/<namespace>/folios/quests/, with its id
     */
    function toServerQuests(id, texts) {
        const colon = id.indexOf(":");
        const namespace = id.slice(0, colon);
        const bookPath = id.slice(colon + 1);
        const files = new Map();
        const quests = new Map();
        const notes = [];
        for (const [path, text] of (texts instanceof Map ? [...texts] : Object.entries(texts))) {
            if (!path.startsWith("entries/") || !path.endsWith(".json")) continue;
            const json = parseJson(text);
            if (!isObject(json)) continue;
            const entry = path.slice("entries/".length, -".json".length);
            let count = 0;
            for (const element of targets("element", path, json)) {
                const quest = element.json;
                if (quest.get("type") !== "quest" || quest.has("quest") || !isArray(quest.get("objectives"))) continue;
                if (bookOnly(quest.get("objectives"))) {
                    notes.push({path, line: element.where + ": kept in the book, as it asks for read_category or read_book, which the server can't follow"});
                    continue;
                }
                count++;
                const name = bookPath + "/" + entry + (count > 1 ? "_" + count : "");
                const questId = namespace + ":" + name;
                const written = new Map([["book", id]]);
                if (quest.get("in_order") === true) written.set("in_order", true);
                written.set("objectives", deepCopy(quest.get("objectives")));
                quests.set(name + ".json", {id: questId, text: writeJson(written)});
                // The page names it in place of its objectives, and keeps its own words.
                const rest = new Map();
                for (const [key, value] of quest) {
                    if (key === "objectives" || key === "in_order") continue;
                    rest.set(key, value);
                    if (key === "type") rest.set("quest", questId);
                }
                quest.clear();
                rest.forEach((value, key) => quest.set(key, value));
                notes.push({path, line: element.where + ": now the server quest " + questId});
            }
            if (count > 0) files.set(path, {before: writeJson(parseJson(text)), after: writeJson(json)});
        }
        return {files, quests, notes};
    }

    return {
        FORMAT, parseVersion, compareVersions, formatOf,
        JsonNumber, parseJson, writeJson, jsonEquals,
        upgrade, check, upgradeBook, findBooks, bookFiles, toServerQuests,
        RUN,
    };
});
