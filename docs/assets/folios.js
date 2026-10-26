// Folios site: the frame around every page (the menu's button, its folding groups, "/" for search), and on the landing
// page only (the docs pages stay still) scroll reveals, tilting cards, magnetic buttons, sparkles and typed code. Every
// page load (including Zensical's instant navigation) runs `setup`; whatever it starts is stopped before the next one.
(function () {
  const calm = window.matchMedia("(prefers-reduced-motion: reduce)");
  let teardown = [];
  let wired = false;
  const wide = window.matchMedia("(min-width: 76.25em)");
  const FOLDED = "folios-menu-folded";
  const CLOSED = "folios-menu-closed";

  // What the reader chose, kept in their browser (where it can be: a private window may refuse).
  function stored(key, fallback) {
    try {
      const value = localStorage.getItem(key);
      return value === null ? fallback : JSON.parse(value);
    } catch (e) {
      return fallback;
    }
  }

  function store(key, value) {
    try {
      localStorage.setItem(key, JSON.stringify(value));
    } catch (e) {
      // The choice lasts until the page is left.
    }
  }

  // The bar stays through instant navigation, so it is wired once. Its menu button opens the drawer on narrow screens
  // (it is the drawer's label) and, on wide ones, folds the menu away and back.
  function bar() {
    document.body.classList.toggle("f-closed", stored(CLOSED, false));
    const menu = document.querySelector(".f-menu");
    if (menu) {
      menu.addEventListener("click", (event) => {
        if (!wide.matches) return;
        event.preventDefault();
        store(CLOSED, document.body.classList.toggle("f-closed"));
      });
      menu.addEventListener("keydown", (event) => {
        if (event.key !== "Enter" && event.key !== " ") return;
        event.preventDefault();
        menu.click();
      });
    }
    // "/" opens the search on the docs' pages, as the field's hint says (Ctrl+K does too). It opens once this key is
    // done with, so the search's own keys don't take it as typed into it.
    document.addEventListener("keydown", (event) => {
      if (event.key !== "/" || event.ctrlKey || event.metaKey || event.altKey) return;
      if (event.target.closest && event.target.closest("input, textarea, select, [contenteditable]")) return;
      const search = document.querySelector(".md-search__button");
      if (!search || !document.querySelector(".md-content.f-docs")) return;
      event.preventDefault();
      event.stopPropagation();
      setTimeout(() => search.click());
    });
  }

  // The menu is drawn again with each page: its groups are folded as the reader left them (the one being read stays
  // open), and the page being read is brought into the menu's view.
  function groups() {
    const folded = stored(FOLDED, []);
    document.querySelectorAll(".f-group").forEach((group) => {
      const head = group.querySelector(".f-group__head");
      const key = group.dataset.group;
      const shut = folded.includes(key) && !group.classList.contains("has-on");
      group.classList.toggle("is-shut", shut);
      head.setAttribute("aria-expanded", String(!shut));
      head.addEventListener("click", () => {
        const now = group.classList.toggle("is-shut");
        head.setAttribute("aria-expanded", String(!now));
        const keep = stored(FOLDED, []).filter((name) => name !== key);
        if (now) keep.push(key);
        store(FOLDED, keep);
      });
    });
    const on = document.querySelector(".f-group__links a.is-on");
    const wrap = on && on.closest(".md-sidebar__scrollwrap");
    if (wrap) {
      const top = on.getBoundingClientRect().top - wrap.getBoundingClientRect().top;
      if (top < 0 || top > wrap.clientHeight - 40) wrap.scrollTop += top - wrap.clientHeight / 3;
    }
  }

  function cleanup() {
    teardown.forEach((stop) => stop());
    teardown = [];
  }

  function setup() {
    cleanup();
    if (!wired) {
      wired = true;
      bar();
    }
    groups();
    const landing = document.querySelector(".fh");
    document.body.classList.toggle("fh-page", !!landing);
    if (!landing) return;
    reveal();
    if (calm.matches) {
      document.querySelectorAll("[data-type]").forEach((block) => type(block, true));
      return;
    }
    zoom();
    tilt();
    glow();
    magnetic();
    sparkles();
    bookToggle();
    document.querySelectorAll("[data-type]").forEach((block) => type(block, false));
  }

  // Elements marked data-reveal fade and rise in as they come into view.
  // What is already on screen shows at once; the rest as it scrolls in (an observer, a scroll check behind it, and a
  // last timeout, so nothing can stay hidden where the observer misbehaves).
  function reveal() {
    const items = [...document.querySelectorAll("[data-reveal]")];
    if (calm.matches) {
      items.forEach((item) => item.classList.add("is-in"));
      return;
    }
    const check = () => {
      const bottom = window.innerHeight * 0.94;
      items.forEach((item) => {
        if (!item.classList.contains("is-in") && item.getBoundingClientRect().top < bottom) item.classList.add("is-in");
      });
    };
    requestAnimationFrame(check);
    let queued = false;
    const onScroll = () => {
      if (queued) return;
      queued = true;
      requestAnimationFrame(() => {
        queued = false;
        check();
      });
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onScroll);
    let observer = null;
    if ("IntersectionObserver" in window) {
      observer = new IntersectionObserver((entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add("is-in");
            observer.unobserve(entry.target);
          }
        });
      }, { rootMargin: "0px 0px -6% 0px", threshold: 0.05 });
      items.forEach((item) => observer.observe(item));
    }
    const safety = setTimeout(check, 1500);
    teardown.push(() => {
      if (observer) observer.disconnect();
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("resize", onScroll);
      clearTimeout(safety);
    });
  }

  // The screenshots in the cards open large when clicked, to look at closely; a click anywhere or Esc closes them.
  function zoom() {
    document.querySelectorAll(".fh-shot img").forEach((image) => {
      const open = (event) => {
        event.stopPropagation();
        const view = document.createElement("figure");
        view.className = "f-zoom";
        view.setAttribute("role", "dialog");
        view.setAttribute("aria-label", image.alt);
        const large = document.createElement("img");
        large.src = image.currentSrc || image.src;
        large.alt = image.alt;
        const caption = document.createElement("figcaption");
        caption.textContent = image.alt;
        view.append(large, caption);
        document.body.appendChild(view);
        requestAnimationFrame(() => view.classList.add("is-open"));
        const close = () => {
          view.classList.remove("is-open");
          document.removeEventListener("keydown", key);
          setTimeout(() => view.remove(), 350);
        };
        const key = (press) => {
          if (press.key === "Escape") close();
        };
        view.addEventListener("click", close);
        document.addEventListener("keydown", key);
      };
      image.addEventListener("click", open);
      teardown.push(() => image.removeEventListener("click", open));
    });
  }

  // Cards and the book lean towards the pointer. The book follows it over the whole hero, measured against the hero
  // (not the book itself, or a pointer far to one side would swing it right round) and only a few degrees each way.
  function tilt() {
    document.querySelectorAll("[data-tilt]").forEach((element) => {
      const book = element.classList.contains("fh-book");
      const target = book ? element.closest(".fh-hero") : element;
      const clamp = (value) => Math.max(-0.5, Math.min(0.5, value));
      const move = (event) => {
        const box = target.getBoundingClientRect();
        const x = clamp((event.clientX - box.left) / box.width - 0.5);
        const y = clamp((event.clientY - box.top) / box.height - 0.5);
        if (book) {
          element.style.transform = `rotateX(${(18 - y * 6).toFixed(2)}deg) rotateY(${(-24 + x * 10).toFixed(2)}deg) rotateZ(2deg)`;
        } else {
          element.style.setProperty("--rx", `${(-y * 6).toFixed(2)}deg`);
          element.style.setProperty("--ry", `${(x * 8).toFixed(2)}deg`);
        }
      };
      const leave = () => {
        if (book) element.style.transform = "";
        else {
          element.style.setProperty("--rx", "0deg");
          element.style.setProperty("--ry", "0deg");
        }
      };
      target.addEventListener("pointermove", move);
      target.addEventListener("pointerleave", leave);
      teardown.push(() => {
        target.removeEventListener("pointermove", move);
        target.removeEventListener("pointerleave", leave);
      });
    });
  }

  // A light that follows the pointer over cards and buttons.
  function glow() {
    document.querySelectorAll("[data-glow], .fh-btn").forEach((element) => {
      const move = (event) => {
        const box = element.getBoundingClientRect();
        element.style.setProperty("--gx", `${event.clientX - box.left}px`);
        element.style.setProperty("--gy", `${event.clientY - box.top}px`);
      };
      element.addEventListener("pointermove", move);
      teardown.push(() => element.removeEventListener("pointermove", move));
    });
  }

  // Buttons lean a touch towards the pointer.
  function magnetic() {
    document.querySelectorAll("[data-magnetic]").forEach((element) => {
      const move = (event) => {
        const box = element.getBoundingClientRect();
        element.style.setProperty("--mx", `${((event.clientX - box.left - box.width / 2) * 0.06).toFixed(1)}px`);
        element.style.setProperty("--my", `${((event.clientY - box.top - box.height / 2) * 0.1).toFixed(1)}px`);
      };
      const leave = () => {
        element.style.setProperty("--mx", "0px");
        element.style.setProperty("--my", "0px");
      };
      element.addEventListener("pointermove", move);
      element.addEventListener("pointerleave", leave);
      teardown.push(() => {
        element.removeEventListener("pointermove", move);
        element.removeEventListener("pointerleave", leave);
      });
    });
  }

  // The hero book plays its loop by itself until it is clicked; from then on each click opens it (leafing through to its
  // spread, and resting there) or closes it, picking up from wherever it is. Every part of it runs on the same 12s loop,
  // so all of them are moved to the same moment and stopped there together.
  function bookToggle() {
    const book = document.querySelector(".fh-book");
    if (!book) return;
    const LOOP = 12000, OPENED = 0.14, SPREAD = 0.56, CLOSING = 0.81;
    const parts = () => [book.closest(".fh-book__mover"), ...book.querySelectorAll(".fh-book__cover, .fh-book__leaf, .fh-book__bar")]
      .flatMap((part) => (part ? part.getAnimations() : []))
      .filter((motion) => /^f-(cover|leaf-\d|bar|shift)$/.test(motion.animationName));
    let frame = 0;
    const at = () => ((parts()[0]?.currentTime ?? 0) % LOOP) / LOOP;
    // Plays every part from `from` to `to` (fractions of the loop) and holds them there.
    const run = (from, to) => {
      cancelAnimationFrame(frame);
      const motions = parts();
      motions.forEach((motion) => { motion.currentTime = from * LOOP; motion.play(); });
      const step = () => {
        if ((motions[0].currentTime ?? 0) >= to * LOOP) motions.forEach((motion) => { motion.pause(); motion.currentTime = (to % 1) * LOOP; });
        else frame = requestAnimationFrame(step);
      };
      frame = requestAnimationFrame(step);
    };
    const click = () => {
      const now = at();
      const open = now >= OPENED && now < CLOSING;
      if (open) run(Math.max(now, CLOSING), 1);       // close: the bar goes, the leaves turn back, the cover shuts
      else run(now >= CLOSING ? 0 : now, SPREAD);     // open: from shut (or from part way open) to the spread
    };
    book.addEventListener("click", click);
    teardown.push(() => {
      cancelAnimationFrame(frame);
      book.removeEventListener("click", click);
    });
  }

  // Golden motes drifting up through the hero, brighter near the pointer.
  function sparkles() {
    const canvas = document.querySelector(".fh-sparkles");
    if (!canvas) return;
    const pen = canvas.getContext("2d");
    let width = 0, height = 0, frame = 0, pointer = { x: -999, y: -999 };
    const ratio = Math.min(2, window.devicePixelRatio || 1);
    const resize = () => {
      width = canvas.clientWidth;
      height = canvas.clientHeight;
      canvas.width = width * ratio;
      canvas.height = height * ratio;
      pen.setTransform(ratio, 0, 0, ratio, 0, 0);
    };
    resize();
    const motes = Array.from({ length: Math.round(Math.min(90, width / 14)) }, () => spawn(true));
    function spawn(anywhere) {
      return {
        x: Math.random() * width, y: anywhere ? Math.random() * height : height + 10,
        r: 0.6 + Math.random() * 1.9, speed: 0.15 + Math.random() * 0.5, sway: Math.random() * Math.PI * 2,
        life: Math.random(), star: Math.random() < 0.18,
      };
    }
    const hero = canvas.closest(".fh-hero");
    const point = (event) => {
      const box = canvas.getBoundingClientRect();
      pointer = { x: event.clientX - box.left, y: event.clientY - box.top };
    };
    hero.addEventListener("pointermove", point);
    const draw = () => {
      pen.clearRect(0, 0, width, height);
      for (const mote of motes) {
        mote.y -= mote.speed;
        mote.sway += 0.012;
        mote.x += Math.sin(mote.sway) * 0.3;
        mote.life += 0.006;
        if (mote.y < -10) Object.assign(mote, spawn(false));
        const near = Math.max(0, 1 - Math.hypot(mote.x - pointer.x, mote.y - pointer.y) / 160);
        const alpha = (0.35 + 0.35 * Math.sin(mote.life * 6)) + near * 0.6;
        pen.fillStyle = `rgba(242, 193, 78, ${Math.min(1, alpha)})`;
        if (mote.star) {
          const s = mote.r * 3;
          pen.beginPath();
          pen.moveTo(mote.x, mote.y - s);
          pen.lineTo(mote.x + s * 0.25, mote.y - s * 0.25);
          pen.lineTo(mote.x + s, mote.y);
          pen.lineTo(mote.x + s * 0.25, mote.y + s * 0.25);
          pen.lineTo(mote.x, mote.y + s);
          pen.lineTo(mote.x - s * 0.25, mote.y + s * 0.25);
          pen.lineTo(mote.x - s, mote.y);
          pen.lineTo(mote.x - s * 0.25, mote.y - s * 0.25);
          pen.closePath();
          pen.fill();
        } else {
          pen.beginPath();
          pen.arc(mote.x, mote.y, mote.r + near * 1.5, 0, Math.PI * 2);
          pen.fill();
        }
      }
      frame = requestAnimationFrame(draw);
    };
    draw();
    window.addEventListener("resize", resize);
    teardown.push(() => {
      cancelAnimationFrame(frame);
      window.removeEventListener("resize", resize);
      hero.removeEventListener("pointermove", point);
    });
  }

  // The JSON types itself out once it is in view, coloured as it goes.
  function type(block, instant) {
    const code = block.querySelector("code");
    const text = code.dataset.text || "";
    const paint = (shown) => shown
      .replace(/&/g, "&amp;").replace(/</g, "&lt;")
      .replace(/("[^"\n]*")(\s*:)/g, '<span class="k">$1</span>$2')
      .replace(/(:\s*|\[\s*|\n\s*)("[^"\n]*"?)/g, (all, lead, value) => lead + '<span class="s">' + value + "</span>")
      .replace(/([{}\[\],])/g, '<span class="p">$1</span>');
    if (instant) {
      code.innerHTML = paint(text);
      return;
    }
    let index = 0, timer = 0, started = false;
    const step = () => {
      index += text[index] === " " ? 2 : 1;
      code.innerHTML = paint(text.slice(0, index));
      if (index < text.length) timer = setTimeout(step, 18 + Math.random() * 30);
    };
    const observer = new IntersectionObserver((entries) => {
      if (!started && entries.some((entry) => entry.isIntersecting)) {
        started = true;
        step();
        observer.disconnect();
      }
    }, { threshold: 0.4 });
    observer.observe(block);
    teardown.push(() => {
      clearTimeout(timer);
      observer.disconnect();
    });
  }

  if (window.document$ && window.document$.subscribe) window.document$.subscribe(setup);
  else document.addEventListener("DOMContentLoaded", setup);
})();
