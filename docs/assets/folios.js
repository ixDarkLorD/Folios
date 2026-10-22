// Folios site: page transitions, scroll reveals, tilting cards, magnetic buttons, sparkles and typed code. Every page
// load (including Zensical's instant navigation) runs `setup`; whatever it starts is stopped before the next one.
(function () {
  const calm = window.matchMedia("(prefers-reduced-motion: reduce)");
  let teardown = [];

  function cleanup() {
    teardown.forEach((stop) => stop());
    teardown = [];
  }

  function setup() {
    cleanup();
    const landing = document.querySelector(".fh");
    document.body.classList.toggle("fh-page", !!landing);
    enter();
    reveal();
    if (!landing) progress();
    if (calm.matches) {
      document.querySelectorAll("[data-type]").forEach((block) => type(block, true));
      return;
    }
    tilt();
    glow();
    magnetic();
    sparkles();
    document.querySelectorAll("[data-type]").forEach((block) => type(block, false));
  }

  // The article rises in, its parts one after another.
  function enter() {
    const inner = document.querySelector(".md-content__inner");
    if (!inner || calm.matches) return;
    [...inner.children].forEach((child, index) => child.style.setProperty("--n", index));
    document.body.classList.remove("f-enter");
    void document.body.offsetWidth;
    document.body.classList.add("f-enter");
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

  // A bar along the top showing how far down the page you are.
  function progress() {
    const bar = document.createElement("div");
    bar.className = "f-progress";
    document.body.appendChild(bar);
    const update = () => {
      const room = document.documentElement.scrollHeight - window.innerHeight;
      bar.style.setProperty("--p", room > 0 ? Math.min(1, window.scrollY / room) : 0);
    };
    update();
    window.addEventListener("scroll", update, { passive: true });
    window.addEventListener("resize", update);
    teardown.push(() => {
      window.removeEventListener("scroll", update);
      window.removeEventListener("resize", update);
      bar.remove();
    });
  }

  // Cards and the book lean towards the pointer.
  function tilt() {
    document.querySelectorAll("[data-tilt]").forEach((element) => {
      const book = element.classList.contains("fh-book");
      const base = book ? getComputedStyle(element).transform : "";
      const move = (event) => {
        const box = element.getBoundingClientRect();
        const x = (event.clientX - box.left) / box.width - 0.5;
        const y = (event.clientY - box.top) / box.height - 0.5;
        if (book) {
          element.style.transform = `translateX(var(--shift)) rotateX(${18 - y * 14}deg) rotateY(${-24 + x * 22}deg) rotateZ(2deg)`;
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
      const target = book ? element.closest(".fh-hero") : element;
      target.addEventListener("pointermove", move);
      target.addEventListener("pointerleave", leave);
      teardown.push(() => {
        target.removeEventListener("pointermove", move);
        target.removeEventListener("pointerleave", leave);
      });
      void base;
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

  // Buttons drift a little towards the pointer.
  function magnetic() {
    document.querySelectorAll("[data-magnetic]").forEach((element) => {
      const move = (event) => {
        const box = element.getBoundingClientRect();
        element.style.setProperty("--mx", `${((event.clientX - box.left - box.width / 2) * 0.18).toFixed(1)}px`);
        element.style.setProperty("--my", `${((event.clientY - box.top - box.height / 2) * 0.3).toFixed(1)}px`);
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
        pen.fillStyle = `rgba(242, 196, 109, ${Math.min(1, alpha)})`;
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
