package net.ixdarklord.folios.internal.client.export;

final class BookSite {
    private BookSite() {}

    static String html(String title) {
        return PAGE.replace("{title}", HtmlText.escape(title));
    }

    private static final String PAGE = """
            <!doctype html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>{title}</title>
            <style>
            :root { --paper: #f8f4eb; --ink: #35312b; --muted: #8e877b; --accent: #8b6cff; --cover: #3a2e58; --line: #d8ceba; }
            * { box-sizing: border-box; }
            html, body { margin: 0; height: 100%; }
            body { background: radial-gradient(ellipse at 50% 30%, color-mix(in srgb, var(--cover) 55%, #1a1c22), #101116 75%);
              color: var(--ink); font: 15px/1.5 Georgia, "Times New Roman", serif; display: flex; flex-direction: column; align-items: center; }
            header { width: 100%; max-width: 1180px; display: flex; align-items: center; gap: 12px; padding: 14px 18px 8px; color: #f3ece0; }
            header h1 { font-size: 20px; margin: 0; flex: 1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
            header a { color: #e6dcf9; font-size: 14px; }
            .search { position: relative; }
            .search input { width: 220px; padding: 6px 10px; border-radius: 6px; border: 2px solid color-mix(in srgb, var(--accent) 60%, #000);
              background: var(--paper); color: var(--ink); font: inherit; }
            .search ul { position: absolute; right: 0; top: 38px; width: 300px; max-height: 340px; overflow: auto; margin: 0; padding: 4px; list-style: none;
              background: var(--paper); border: 2px solid var(--accent); border-radius: 6px; z-index: 30; display: none; box-shadow: 0 10px 30px #0008; }
            .search ul.open { display: block; }
            .search li { padding: 6px 8px; border-radius: 4px; cursor: pointer; color: var(--ink); }
            .search li:hover, .search li.on { background: color-mix(in srgb, var(--accent) 18%, transparent); }
            .search li small { display: block; color: var(--muted); }
            main { flex: 1; width: 100%; display: flex; align-items: center; justify-content: center; gap: 10px; padding: 8px 12px 18px; min-height: 0; }
            .turn { width: 46px; height: 46px; border-radius: 50%; border: 2px solid color-mix(in srgb, var(--accent) 65%, #fff); background: var(--cover);
              color: #fff; font-size: 22px; cursor: pointer; flex: none; box-shadow: 0 4px 14px #0007; }
            .turn:disabled { opacity: .3; cursor: default; }
            .stage { position: relative; display: flex; align-items: stretch; }
            .book { position: relative; display: flex; padding: 10px; border-radius: 8px; background: var(--cover); perspective: 2400px;
              box-shadow: 0 18px 50px #000a, inset 0 0 0 2px color-mix(in srgb, var(--cover) 60%, #000); }
            .page { position: relative; width: var(--pw); height: var(--ph); background: var(--paper); overflow: hidden; }
            .page img { display: block; width: 100%; height: 100%; image-rendering: pixelated; }
            .page.left { box-shadow: inset -10px 0 14px -10px #0005; }
            .page.right { box-shadow: inset 10px 0 14px -10px #0005; }
            .num { position: absolute; bottom: -1px; width: 100%; text-align: center; font-size: 11px; color: var(--muted); display: none; }
            .hot { position: absolute; display: block; border-radius: 3px; cursor: pointer; }
            .hot:hover { background: color-mix(in srgb, var(--accent) 22%, transparent); outline: 1px solid color-mix(in srgb, var(--accent) 60%, transparent); }
            .leaf { position: absolute; top: 10px; width: var(--pw); height: var(--ph); transform-style: preserve-3d; transition: transform .55s cubic-bezier(.3,.1,.25,1); z-index: 10; }
            .leaf.fwd { left: calc(10px + var(--pw)); transform-origin: left center; }
            .leaf.back { left: 10px; transform-origin: right center; }
            .leaf div { position: absolute; inset: 0; backface-visibility: hidden; background: var(--paper); }
            .leaf div img { width: 100%; height: 100%; display: block; image-rendering: pixelated; }
            .leaf .b { transform: rotateY(180deg); }
            .tabs { display: flex; flex-direction: column; gap: 4px; padding-top: 18px; margin-left: -2px; }
            .tab { width: 44px; height: 34px; border: 0; border-radius: 0 8px 8px 0; background: color-mix(in srgb, var(--cover) 80%, #fff); cursor: pointer;
              display: flex; align-items: center; justify-content: center; box-shadow: 2px 2px 6px #0006; padding: 0; }
            .tab img { width: 24px; height: 24px; image-rendering: pixelated; }
            .tab.on { background: var(--accent); width: 52px; }
            footer { color: #b9b2c8; font-size: 12px; padding: 0 0 12px; }
            footer a { color: #d9cdf7; }
            @media (max-width: 760px) {
              header { flex-wrap: wrap; } .search input { width: 100%; } .search { width: 100%; } .search ul { width: 100%; }
              .page.right { display: none; } .tabs { display: none; } .turn { width: 38px; height: 38px; }
            }
            </style>
            </head>
            <body>
            <header>
              <h1 id="title">{title}</h1>
              <a href="contents.html">Text version</a>
              <div class="search"><input id="find" type="search" placeholder="Search the book..." autocomplete="off"><ul id="found"></ul></div>
            </header>
            <main>
              <button class="turn" id="prev" aria-label="Previous page">&#8249;</button>
              <div class="stage">
                <div class="book" id="book">
                  <div class="page left" id="left"></div>
                  <div class="page right" id="right"></div>
                </div>
                <nav class="tabs" id="tabs"></nav>
              </div>
              <button class="turn" id="next" aria-label="Next page">&#8250;</button>
            </main>
            <footer>Made with <a href="https://modrinth.com/mod/folios">Folios</a> &middot; arrow keys turn the pages</footer>
            <script src="book.js"></script>
            <script>
            (() => {
              const F = window.FOLIOS, root = document.documentElement;
              for (const [k, v] of Object.entries(F.colors)) root.style.setProperty('--' + k, v);
              const book = document.getElementById('book'), left = document.getElementById('left'), right = document.getElementById('right');
              const prev = document.getElementById('prev'), next = document.getElementById('next');
              let chapter = F.start, at = 0, busy = false;
              const single = () => window.matchMedia('(max-width: 760px)').matches;
              const step = () => single() ? 1 : 2;
              const pages = key => (F.chapters[key] || F.chapters[F.start]).pages;
              function size() {
                const ratio = F.pageHeight / F.pageWidth, spare = single() ? 120 : 220;
                let w = Math.min((window.innerWidth - spare) / (single() ? 1 : 2), (window.innerHeight - 130) / ratio);
                w = Math.max(160, Math.floor(w));
                root.style.setProperty('--pw', w + 'px');
                root.style.setProperty('--ph', Math.round(w * ratio) + 'px');
              }
              function fill(el, key, index) {
                el.innerHTML = '';
                const page = pages(key)[index];
                if (!page) return;
                const img = document.createElement('img');
                img.src = page.src; img.alt = page.alt || ''; el.appendChild(img);
                for (const link of page.links) {
                  const a = document.createElement('a');
                  a.className = 'hot';
                  a.style.left = link.x * 100 + '%'; a.style.top = link.y * 100 + '%';
                  a.style.width = link.w * 100 + '%'; a.style.height = link.h * 100 + '%';
                  if (link.href) { a.href = link.href; a.target = '_blank'; a.rel = 'noopener'; }
                  else { a.href = '#' + link.go + '/' + link.page; a.onclick = e => { e.preventDefault(); go(link.go, link.page); }; }
                  if (link.title) a.title = link.title;
                  el.appendChild(a);
                }
              }
              function face(key, index) {
                const d = document.createElement('div');
                const page = pages(key)[index];
                if (page) { const img = document.createElement('img'); img.src = page.src; d.appendChild(img); }
                return d;
              }
              function render() {
                const s = step();
                at = Math.max(0, Math.min(at - at % s, Math.max(0, pages(chapter).length - 1)));
                at -= at % s;
                fill(left, chapter, at);
                if (s === 2) fill(right, chapter, at + 1);
                prev.disabled = at === 0 && F.order.indexOf(chapter) <= 0;
                next.disabled = at + s >= pages(chapter).length && F.order.indexOf(chapter) >= F.order.length - 1;
                for (const tab of document.querySelectorAll('.tab')) tab.classList.toggle('on', tab.dataset.key === chapter || tab.dataset.key === F.chapters[chapter]?.category);
                history.replaceState(null, '', '#' + chapter + '/' + at);
              }
              function flip(forward, then) {
                if (busy || single()) { then(); render(); return; }
                busy = true;
                const leaf = document.createElement('div');
                leaf.className = 'leaf ' + (forward ? 'fwd' : 'back');
                const oldChapter = chapter, oldAt = at;
                then();
                const s = 2;
                at -= at % s;
                const front = forward ? face(oldChapter, oldAt + 1) : face(oldChapter, oldAt);
                const back = forward ? face(chapter, at) : face(chapter, at + 1);
                front.className = 'f'; back.className = 'b';
                leaf.append(front, back);
                book.appendChild(leaf);
                if (forward) { fill(right, chapter, at + 1); } else { fill(left, chapter, at); }
                requestAnimationFrame(() => requestAnimationFrame(() => leaf.style.transform = 'rotateY(' + (forward ? -180 : 180) + 'deg)'));
                leaf.addEventListener('transitionend', () => { leaf.remove(); busy = false; render(); }, { once: true });
              }
              function turn(direction) {
                const s = step(), list = pages(chapter);
                if (direction > 0) {
                  if (at + s < list.length) flip(true, () => at += s);
                  else { const i = F.order.indexOf(chapter); if (i >= 0 && i < F.order.length - 1) flip(true, () => { chapter = F.order[i + 1]; at = 0; }); }
                } else {
                  if (at > 0) flip(false, () => at -= s);
                  else { const i = F.order.indexOf(chapter); if (i > 0) flip(false, () => { chapter = F.order[i - 1]; const n = pages(chapter).length; at = Math.max(0, n - 1); at -= at % s; }); }
                }
              }
              function go(key, page) {
                if (!F.chapters[key]) return;
                const forward = F.order.indexOf(key) > F.order.indexOf(chapter) || (key === chapter && page > at);
                flip(forward, () => { chapter = key; at = page || 0; });
              }
              prev.onclick = () => turn(-1); next.onclick = () => turn(1);
              document.addEventListener('keydown', e => {
                if (e.target.tagName === 'INPUT') return;
                if (e.key === 'ArrowRight') turn(1);
                if (e.key === 'ArrowLeft') turn(-1);
              });
              book.addEventListener('wheel', e => { if (Math.abs(e.deltaY) > 20) { e.preventDefault(); turn(e.deltaY > 0 ? 1 : -1); } }, { passive: false });
              let touch = null;
              book.addEventListener('touchstart', e => touch = e.touches[0].clientX);
              book.addEventListener('touchend', e => { if (touch === null) return; const dx = e.changedTouches[0].clientX - touch; if (Math.abs(dx) > 40) turn(dx < 0 ? 1 : -1); touch = null; });
              const tabs = document.getElementById('tabs');
              for (const tab of F.tabs) {
                const b = document.createElement('button');
                b.className = 'tab'; b.dataset.key = tab.key; b.title = tab.title;
                if (tab.icon) { const img = document.createElement('img'); img.src = tab.icon; img.alt = ''; b.appendChild(img); }
                else b.textContent = tab.title.charAt(0);
                b.onclick = () => go(tab.key, 0);
                tabs.appendChild(b);
              }
              const find = document.getElementById('find'), found = document.getElementById('found');
              find.addEventListener('input', () => {
                const q = find.value.trim().toLowerCase();
                found.innerHTML = '';
                if (!q) { found.classList.remove('open'); return; }
                const hits = F.entries.filter(e => e.title.toLowerCase().includes(q) || e.text.toLowerCase().includes(q)).slice(0, 30);
                for (const hit of hits) {
                  const li = document.createElement('li');
                  li.innerHTML = '<span></span><small></small>';
                  li.firstChild.textContent = hit.title; li.lastChild.textContent = hit.category || '';
                  li.onclick = () => { found.classList.remove('open'); find.value = ''; go(hit.key, 0); };
                  found.appendChild(li);
                }
                if (!hits.length) { const li = document.createElement('li'); li.textContent = 'Nothing matches.'; found.appendChild(li); }
                found.classList.add('open');
              });
              find.addEventListener('keydown', e => { if (e.key === 'Enter' && found.firstChild && found.firstChild.onclick) found.firstChild.onclick(); if (e.key === 'Escape') found.classList.remove('open'); });
              const hash = decodeURIComponent(location.hash.slice(1));
              if (hash) { const cut = hash.lastIndexOf('/'); const key = cut > 0 && /^\\d+$/.test(hash.slice(cut + 1)) ? hash.slice(0, cut) : hash; if (F.chapters[key]) { chapter = key; at = cut > 0 ? +hash.slice(cut + 1) || 0 : 0; } }
              window.addEventListener('resize', () => { size(); render(); });
              size(); render();
            })();
            </script>
            </body>
            </html>
            """;
}
