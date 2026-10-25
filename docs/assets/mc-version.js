// The Minecraft version picker in the header: which version the reader plays, kept in their browser. Each page then
// shows what is true for it: notes and inline details marked for another version are hidden, version tabs switch to it,
// and the version table marks its row. Runs on every page load, instant navigation too.
//
// Marking content for versions (any of mc-26, mc-121, mc-120 on the same element):
//   !!! note mc-120 "1.20.1"                          an admonition
//   <span class="mc-v mc-121 mc-120">...</span>       a part of a paragraph
//   class mc-only: shown only when one of its versions is picked (not for "All versions");
//   class mc-all: shown only for "All versions" (the wording that covers them all)
// Version tabs ("=== "1.20.1"") switch by their label.
(function () {
  const KEY = "folios-mc-version";
  const VERSIONS = [
    {id: "", label: "All versions"},
    {id: "26", label: "26.1 – 26.3"},
    {id: "121", label: "1.21 – 1.21.1"},
    {id: "120", label: "1.20.1"},
  ];

  function chosen() {
    try {
      return localStorage.getItem(KEY) || "";
    } catch (e) {
      return "";
    }
  }

  function remember(id) {
    try {
      localStorage.setItem(KEY, id);
    } catch (e) {
      // Private windows and blocked storage: the choice lasts until the page is left.
    }
  }

  // The versions a tab's label is about: "26.1 and later", "1.21 and 1.20.1", "1.20.5 and later", "1.20.1".
  function tabVersions(label) {
    const text = label.toLowerCase();
    if (/1\.20\.5 and later/.test(text)) return ["26", "121"];
    const found = [];
    if (/\b26\./.test(text)) found.push("26");
    if (/1\.21/.test(text)) found.push("121");
    if (/1\.20\.1/.test(text)) found.push("120");
    if (/and later/.test(text) && found.includes("121")) found.push("26");
    return found;
  }

  function apply(id) {
    document.documentElement.dataset.mc = id;
    // Marked content: shown for its versions, or when no version is picked.
    document.querySelectorAll(".mc-v, .admonition[class*='mc-'], details[class*='mc-']").forEach((element) => {
      const marked = [...element.classList].filter((name) => /^mc-\d+$/.test(name)).map((name) => name.slice(3));
      if (element.classList.contains("mc-all")) element.hidden = id !== "";
      else if (element.classList.contains("mc-only")) element.hidden = id === "" || !marked.includes(id);
      else element.hidden = id !== "" && marked.length > 0 && !marked.includes(id);
    });
    // Version tabs: the one for the picked version.
    if (id !== "") {
      document.querySelectorAll(".tabbed-set").forEach((set) => {
        const labels = set.querySelectorAll(":scope > .tabbed-labels > label");
        const inputs = set.querySelectorAll(":scope > input");
        labels.forEach((label, index) => {
          if (tabVersions(label.textContent).includes(id) && inputs[index]) inputs[index].checked = true;
        });
      });
    }
    // The table of versions: the picked one's row marked.
    document.querySelectorAll(".md-typeset table tr").forEach((row) => {
      const first = row.cells[0] ? row.cells[0].textContent : "";
      const versions = tabVersions(first.replace("–", "and"));
      row.classList.toggle("mc-row", id !== "" && first.length < 20 && versions.length === 1 && versions[0] === id);
    });
    document.querySelectorAll(".mc-picker select").forEach((select) => (select.value = id));
  }

  // The picker, before the header's search.
  function picker() {
    if (document.querySelector(".mc-picker")) return;
    const header = document.querySelector(".md-header__inner");
    if (!header) return;
    const box = document.createElement("label");
    box.className = "mc-picker";
    box.title = "Show what is true for your Minecraft version";
    const name = document.createElement("span");
    name.className = "mc-picker__name";
    name.textContent = "Minecraft";
    const select = document.createElement("select");
    select.setAttribute("aria-label", "Minecraft version");
    VERSIONS.forEach((version) => {
      const option = document.createElement("option");
      option.value = version.id;
      option.textContent = version.label;
      select.appendChild(option);
    });
    select.addEventListener("change", () => {
      remember(select.value);
      apply(select.value);
    });
    box.append(name, select);
    const search = header.querySelector(".md-search");
    header.insertBefore(box, search || null);
  }

  function setup() {
    picker();
    apply(chosen());
  }

  if (window.document$ && window.document$.subscribe) window.document$.subscribe(setup);
  else document.addEventListener("DOMContentLoaded", setup);
})();
