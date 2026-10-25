// Checks the site's book upgrader (docs/assets/upgrader/upgrader.js) against the mod's own (BookUpgrader.java): both
// upgrade the same books, and every file they write must be the same, as JSON (values and key order) and as text.
// Their dry runs (--check, for books already in the current format) must name the same things too.
//
//   node tools/check_upgrader.mjs
//
// Needs the mod built (common/build/classes) and a JDK. Paths can be changed with environment variables:
//   FOLIOS_MOD  the mod's repo        (default F:/Programing/Minecraft/Mods/Folios-26.1.2)
//   JAVA        the java executable   (default the Adoptium JDK 25)
//   GSON        a Gson jar            (default the newest in ~/.gradle/caches)
// Outputs go to a temporary folder, never into the mod's tree. Exits 1 on any difference.

import {createRequire} from "node:module";
import {execFileSync} from "node:child_process";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import {fileURLToPath} from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const site = path.resolve(here, "..");
const require = createRequire(import.meta.url);
const Upgrader = require(path.join(site, "docs/assets/upgrader/upgrader.js"));

const MOD = process.env.FOLIOS_MOD || "F:/Programing/Minecraft/Mods/Folios-26.1.2";
const JAVA = process.env.JAVA || "C:/Program Files/Eclipse Adoptium/jdk-25.0.2.10-hotspot/bin/java";
const GSON = process.env.GSON || findGson();
const TEMP = fs.mkdtempSync(path.join(os.tmpdir(), "folios-upgrader-check-"));

// The books to check, each a folder the two upgraders walk.
const CASES = [
    {name: "old_notes (2.x test book)", root: path.join(MOD, "testmods/fabric/src/main/resources/assets/foliostest/folios/old_notes")},
    {name: "my_guide (3.0 template)", root: path.join(MOD, "templates/folios-book-template/assets/mymod/folios/my_guide"), check: true},
    {name: "every_rule (synthetic 2.x)", root: path.join(here, "upgrader-test/every_rule_pack")},
    // The same book claiming to be 3.0 already: the dry run must name every old shape in it.
    {name: "every_rule as 3.0 (dry run)", root: claimCurrent(path.join(here, "upgrader-test/every_rule_pack")), check: true},
];

// The rules, as the page fetches them.
const steps = {};
for (const format of Upgrader.FORMAT.KNOWN.slice(1)) {
    steps[format] = fs.readFileSync(path.join(site, "docs/assets/upgrader", format + ".json"), "utf8");
}
// The copy must be the mod's file, unchanged.
const modRules = fs.readFileSync(path.join(MOD, "common/src/main/resources/folios/upgrades/3.0.json"), "utf8");
let failed = 0;
if (normalize(modRules) !== normalize(steps["3.0"])) fail("docs/assets/upgrader/3.0.json differs from the mod's: re-copy it");

for (const test of CASES) {
    console.log("\n== " + test.name);
    const javaOut = path.join(TEMP, "java-" + CASES.indexOf(test));
    java([test.root, javaOut]);
    const jsOut = upgradeWithJs(test.root);
    compareFolders(javaOut, jsOut);
    if (test.check) {
        const javaCheck = java(["--check", test.root]).trim().split(/\r?\n/);
        const jsCheck = checkWithJs(test.root);
        const same = javaCheck.length === jsCheck.length && javaCheck.every((line, index) => line === jsCheck[index]);
        if (same) console.log("  check: same (" + (jsCheck.length === 1 ? jsCheck[0].trim() : jsCheck.length - 1 + " lines") + ")");
        else fail("check differs\n  java:\n    " + javaCheck.join("\n    ") + "\n  js:\n    " + jsCheck.join("\n    "));
    }
}

console.log(failed ? "\n" + failed + " difference(s)." : "\nAll the same.");
console.log("(outputs in " + TEMP + ")");
process.exit(failed ? 1 : 0);

// ---- The mod's upgrader ----

function java(args) {
    const classpath = [path.join(MOD, "common/build/classes/java/main"), path.join(MOD, "common/src/main/resources"), GSON].join(path.delimiter);
    return execFileSync(JAVA, ["-cp", classpath, "net.ixdarklord.folios.internal.format.BookUpgrader", ...args], {encoding: "utf8"});
}

function findGson() {
    const base = path.join(os.homedir(), ".gradle/caches/modules-2/files-2.1/com.google.code.gson/gson");
    const jars = [];
    for (const version of fs.existsSync(base) ? fs.readdirSync(base) : []) {
        for (const hash of fs.readdirSync(path.join(base, version))) {
            for (const file of fs.readdirSync(path.join(base, version, hash))) {
                if (file === "gson-" + version + ".jar") jars.push({version, file: path.join(base, version, hash, file)});
            }
        }
    }
    if (jars.length === 0) throw new Error("No Gson jar found: set GSON");
    const number = version => version.split(".").map(Number);
    jars.sort((a, b) => {
        const x = number(a.version), y = number(b.version);
        for (let index = 0; index < Math.max(x.length, y.length); index++) if ((x[index] || 0) !== (y[index] || 0)) return (x[index] || 0) - (y[index] || 0);
        return 0;
    });
    return jars[jars.length - 1].file;
}

// ---- The site's upgrader, run as the mod's tool runs: every book under a folder, written to another ----

function walk(folder) {
    const found = [];
    for (const entry of fs.readdirSync(folder, {withFileTypes: true})) {
        const full = path.join(folder, entry.name);
        if (entry.isDirectory()) found.push(...walk(full));
        else found.push(full);
    }
    return found;
}

function slash(file) {
    return file.split(path.sep).join("/");
}

// The books under a folder; absolute paths, so a book's namespace is found above the folder as the mod finds it.
function books(root) {
    const all = walk(root).map(slash);
    return Upgrader.findBooks(all).map(book => ({...book, files: Upgrader.bookFiles(all, book.folder)}));
}

function upgradeWithJs(root) {
    const out = path.join(TEMP, "js-" + path.basename(root) + "-" + Math.random().toString(36).slice(2, 8));
    for (const book of books(root)) {
        const texts = new Map(book.files.map(file => [file, fs.readFileSync(path.join(book.folder, file), "utf8")]));
        const result = Upgrader.upgradeBook(book.namespace + ":" + book.path, texts, steps);
        for (const [file, text] of result.files) {
            const target = path.join(out, path.relative(root, path.join(book.folder, file)));
            fs.mkdirSync(path.dirname(target), {recursive: true});
            fs.writeFileSync(target, text.after + "\n");
        }
        console.log("  js: " + book.namespace + ":" + book.path + (result.upToDate ? " (up to date)" : " (" + result.from + " to " + Upgrader.FORMAT.CURRENT + ", "
            + result.changes.lines.size + " files changed, " + result.moves.length + " moved)"));
    }
    return out;
}

// What the mod's --check prints: each book in the current format, "clean" or what in it has an older shape.
function checkWithJs(root) {
    const lines = [];
    for (const book of books(root)) {
        const texts = new Map(book.files.map(file => [file, fs.readFileSync(path.join(book.folder, file), "utf8")]));
        const named = Upgrader.parseJson(texts.get("book.json")).get("format_version");
        if (named === undefined) continue;
        const result = Upgrader.upgradeBook(book.folder.split("/").pop(), texts, steps);
        if (!result.upToDate) continue;
        const old = result.check.sorted();
        lines.push(book.folder.split("/").pop() + (old.size === 0 ? ": clean" : ":"));
        for (const [file, notes] of old) for (const note of notes) lines.push("  " + file + ": " + note);
    }
    return lines;
}

// ---- Comparing ----

function compareFolders(javaOut, jsOut) {
    const list = folder => (fs.existsSync(folder) ? walk(folder).map(file => slash(path.relative(folder, file))).sort() : []);
    const javaFiles = list(javaOut), jsFiles = list(jsOut);
    for (const file of javaFiles) if (!jsFiles.includes(file)) fail("only the mod wrote " + file);
    for (const file of jsFiles) if (!javaFiles.includes(file)) fail("only the site wrote " + file);
    let same = 0;
    for (const file of javaFiles.filter(file => jsFiles.includes(file))) {
        const javaText = normalize(fs.readFileSync(path.join(javaOut, file), "utf8"));
        const jsText = normalize(fs.readFileSync(path.join(jsOut, file), "utf8"));
        if (!orderedEquals(Upgrader.parseJson(javaText), Upgrader.parseJson(jsText))) fail(file + ": different JSON\n--- java\n" + javaText + "--- js\n" + jsText);
        else if (javaText !== jsText) fail(file + ": same JSON, different text\n--- java\n" + javaText + "--- js\n" + jsText);
        else same++;
    }
    console.log("  files: " + same + " of " + javaFiles.length + " the same");
}

// Deep equality that also holds keys to their order.
function orderedEquals(a, b) {
    if (a instanceof Upgrader.JsonNumber && b instanceof Upgrader.JsonNumber) return a.text === b.text;
    if (Array.isArray(a) && Array.isArray(b)) return a.length === b.length && a.every((value, index) => orderedEquals(value, b[index]));
    if (a instanceof Map && b instanceof Map) {
        const x = [...a], y = [...b];
        return x.length === y.length && x.every(([key, value], index) => key === y[index][0] && orderedEquals(value, y[index][1]));
    }
    return a === b;
}

function normalize(text) {
    return text.replace(/\r\n/g, "\n");
}

function fail(message) {
    failed++;
    console.log("  DIFFERENT: " + message);
}

// A copy of a pack whose books all say "format_version": "3.0", for the dry run.
function claimCurrent(root) {
    const copy = path.join(fs.mkdtempSync(path.join(os.tmpdir(), "folios-upgrader-as-3.0-")), path.basename(root));
    fs.cpSync(root, copy, {recursive: true});
    for (const file of walk(copy).filter(file => path.basename(file) === "book.json")) {
        const json = Upgrader.parseJson(fs.readFileSync(file, "utf8"));
        json.set("format_version", "3.0");
        fs.writeFileSync(file, Upgrader.writeJson(json) + "\n");
    }
    return copy;
}
