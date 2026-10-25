// vuegram.com: progressive touches only. The page reads and works without this script.
(function () {
  "use strict";

  var doc = document.documentElement;
  var motionQuery = window.matchMedia ? window.matchMedia("(prefers-reduced-motion: reduce)") : null;
  var reduceMotion = !!(motionQuery && motionQuery.matches);
  var hasIO = "IntersectionObserver" in window;
  doc.classList.add("js");

  var $ = function (sel, root) { return (root || document).querySelector(sel); };
  var $$ = function (sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); };

  // ---------- Footer year ----------
  var year = $("[data-year]");
  if (year) year.textContent = String(new Date().getFullYear());

  // ---------- Navigation: frosted after scrolling, active section, phone menu ----------
  var nav = $(".nav");
  var toggle = $(".nav-toggle");
  var links = $("#nav-links");

  if (toggle && links) {
    var setOpen = function (open) {
      links.classList.toggle("open", open);
      nav.classList.toggle("menu-open", open);
      toggle.setAttribute("aria-expanded", open ? "true" : "false");
      toggle.setAttribute("aria-label", open ? "Close menu" : "Open menu");
      document.body.style.overflow = open ? "hidden" : "";
    };
    toggle.addEventListener("click", function () { setOpen(!links.classList.contains("open")); });
    links.addEventListener("click", function (e) { if (e.target.closest("a")) setOpen(false); });
    document.addEventListener("keydown", function (e) { if (e.key === "Escape" && links.classList.contains("open")) { setOpen(false); toggle.focus(); } });
    window.addEventListener("resize", function () { if (window.innerWidth > 900 && links.classList.contains("open")) setOpen(false); });
  }

  if (hasIO) {
    var navLinks = $$("[data-section]");
    var current = null;
    var sectionIO = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) current = entry.target.id;
        else if (current === entry.target.id) current = null;
      });
      navLinks.forEach(function (a) { a.classList.toggle("active", a.getAttribute("data-section") === current); });
    }, { rootMargin: "-45% 0px -50% 0px" });
    navLinks.forEach(function (a) {
      var s = document.getElementById(a.getAttribute("data-section"));
      if (s) sectionIO.observe(s);
    });
  }

  // ---------- Scroll: nav state and the TV's tilt, in one rAF ----------
  var tv = $("[data-tilt]");
  var ticking = false;
  var onScroll = function () {
    if (ticking) return;
    ticking = true;
    window.requestAnimationFrame(function () {
      ticking = false;
      var y = window.scrollY || window.pageYOffset;
      if (nav) nav.classList.toggle("scrolled", y > 8);
      if (tv && !reduceMotion) {
        // Leans back at the top of the page and settles flat as it scrolls towards the middle of the screen.
        var p = Math.min(1, Math.max(0, y / (window.innerHeight * 0.75)));
        var e = 1 - Math.pow(1 - p, 3);
        tv.style.setProperty("--tilt", (16 * (1 - e)).toFixed(2) + "deg");
        tv.style.setProperty("--scale", (0.94 + 0.06 * e).toFixed(4));
      }
    });
  };
  window.addEventListener("scroll", onScroll, { passive: true });
  window.addEventListener("resize", onScroll, { passive: true });
  onScroll();

  // ---------- Reveal on scroll, staggered within each group ----------
  var reveals = $$(".reveal");
  if (!reduceMotion && hasIO) {
    var counts = new Map();
    reveals.forEach(function (el) {
      var parent = el.parentElement;
      var i = counts.get(parent) || 0;
      counts.set(parent, i + 1);
      el.style.setProperty("--d", Math.min(i, 6) * 70 + "ms");
    });
    var revealIO = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) { entry.target.classList.add("in"); revealIO.unobserve(entry.target); }
      });
    }, { rootMargin: "0px 0px -8% 0px", threshold: 0.12 });
    reveals.forEach(function (el) { revealIO.observe(el); });
  } else {
    reveals.forEach(function (el) { el.classList.add("in"); });
  }

  // ---------- Features: a spotlight that follows the pointer ----------
  $$(".card").forEach(function (card) {
    card.addEventListener("pointermove", function (e) {
      var r = card.getBoundingClientRect();
      card.style.setProperty("--mx", (e.clientX - r.left).toFixed(0) + "px");
      card.style.setProperty("--my", (e.clientY - r.top).toFixed(0) + "px");
    });
  });

  // ---------- The TV: a D-pad you can drive ----------
  var screen = $("[data-demo]");
  if (screen) {
    var items = $$(".item", screen);
    var rows = [0, 1, 2].map(function (r) { return items.filter(function (it) { return it.getAttribute("data-row") === String(r); }); });
    var toastEl = $("[data-toast-el]", screen);
    var live = $("[data-demo-live]");
    var focused = null;
    var engaged = false;   // the visitor has used the TV: up and down now move the focus too
    var inView = false;
    var toastTimer = 0;

    var centerX = function (el) { var r = el.getBoundingClientRect(); return r.left + r.width / 2; };
    var rowOf = function (el) { return Number(el.getAttribute("data-row")); };

    var focusItem = function (el, announce) {
      if (!el || el === focused) return;
      if (focused) focused.classList.remove("is-focused");
      focused = el;
      el.classList.add("is-focused");
      if (announce && live) live.textContent = el.getAttribute("data-label") || "";
    };

    var showToast = function (text) {
      if (!toastEl || !text) return;
      toastEl.textContent = text;
      toastEl.classList.add("show");
      window.clearTimeout(toastTimer);
      toastTimer = window.setTimeout(function () { toastEl.classList.remove("show"); }, 1600);
      if (live) live.textContent = text;
    };

    var press = function (el) {
      if (!el) return;
      el.classList.add("is-pressed");
      window.setTimeout(function () { el.classList.remove("is-pressed"); }, 140);
      var text = el.getAttribute("data-toast");
      if (!text) text = rowOf(el) === 2 ? el.getAttribute("data-label") + " · Finding videos…" : "Search all channels";
      showToast(text);
    };

    // True when the focus moved; false at an edge, so the key keeps its usual job (scrolling the page).
    var move = function (dir) {
      if (!focused) { focusItem(rows[1][0], true); return true; }
      var r = rowOf(focused);
      var row = rows[r];
      var i = row.indexOf(focused);
      if (dir === "left" || dir === "right") {
        var next = row[i + (dir === "left" ? -1 : 1)];
        if (next) { focusItem(next, true); return true; }
        return false;
      }
      var target = rows[r + (dir === "up" ? -1 : 1)];
      if (!target || !target.length) return false;
      var x = centerX(focused);
      var best = target[0];
      target.forEach(function (c) { if (Math.abs(centerX(c) - x) < Math.abs(centerX(best) - x)) best = c; });
      focusItem(best, true);
      return true;
    };

    // Auto-demo until the visitor takes over; never with reduced motion.
    var path = [rows[2][0], rows[2][1], rows[2][2], rows[2][3], rows[1][1], rows[1][2], rows[2][5], rows[2][4], rows[0][0], rows[1][0]];
    var step = 0;
    var timer = null;
    var startDemo = function () {
      if (timer || engaged || reduceMotion || !inView || document.hidden) return;
      timer = window.setInterval(function () { step = (step + 1) % path.length; focusItem(path[step], false); }, 1700);
    };
    var stopDemo = function () { window.clearInterval(timer); timer = null; };
    var engage = function () { if (!engaged) { engaged = true; stopDemo(); } };
    focusItem(path[0], false);

    if (hasIO) {
      new IntersectionObserver(function (entries) {
        entries.forEach(function (e) { inView = e.isIntersecting; if (inView) startDemo(); else stopDemo(); });
      }, { threshold: 0.4 }).observe(screen);
    } else {
      inView = true;
      startDemo();
    }
    document.addEventListener("visibilitychange", function () { if (document.hidden) stopDemo(); else startDemo(); });
    if (motionQuery && motionQuery.addEventListener) {
      motionQuery.addEventListener("change", function (e) { reduceMotion = e.matches; if (reduceMotion) stopDemo(); else startDemo(); });
    }

    items.forEach(function (it) {
      it.addEventListener("click", function () { engage(); focusItem(it, true); press(it); });
    });
    screen.addEventListener("focus", engage);

    var keyDir = { ArrowLeft: "left", ArrowRight: "right", ArrowUp: "up", ArrowDown: "down" };
    document.addEventListener("keydown", function (e) {
      if (e.altKey || e.ctrlKey || e.metaKey || e.shiftKey) return;
      var a = document.activeElement;
      var onScreen = a === screen;
      // Only when nothing else wants the keys: the page itself, or the TV screen with keyboard focus.
      if (!onScreen && a && a !== document.body && a !== doc) return;
      if (!onScreen && !inView) return;
      var dir = keyDir[e.key];
      if (dir) {
        // Left and right are free on this page; up and down scroll it, so take them only once the TV is in use.
        if ((dir === "up" || dir === "down") && !engaged && !onScreen) return;
        engage();
        if (move(dir)) e.preventDefault();
      } else if ((e.key === "Enter" || e.key === " ") && onScreen) {
        e.preventDefault();
        press(focused);
      }
    });
  }

  // ---------- A decorative QR code (it encodes nothing) ----------
  var qr = $("[data-qr]");
  if (qr) {
    var size = 25;
    var seed = 20231;
    var rand = function () { seed = (seed * 16807) % 2147483647; return seed / 2147483647; };
    var finder = function (x, y) {
      var corners = [[0, 0], [size - 7, 0], [0, size - 7]];
      for (var i = 0; i < corners.length; i++) {
        var dx = x - corners[i][0], dy = y - corners[i][1];
        if (dx >= -1 && dx <= 7 && dy >= -1 && dy <= 7) {
          if (dx < 0 || dy < 0 || dx > 6 || dy > 6) return 0;
          if (dx === 0 || dy === 0 || dx === 6 || dy === 6) return 1;
          if (dx >= 2 && dx <= 4 && dy >= 2 && dy <= 4) return 1;
          return 0;
        }
      }
      return -1;
    };
    var ns = "http://www.w3.org/2000/svg";
    for (var y = 0; y < size; y++) {
      for (var x = 0; x < size; x++) {
        var f = finder(x, y);
        if (!(f === -1 ? rand() > 0.52 : f === 1)) continue;
        var r = document.createElementNS(ns, "rect");
        r.setAttribute("x", x); r.setAttribute("y", y);
        r.setAttribute("width", 1.02); r.setAttribute("height", 1.02);
        qr.appendChild(r);
      }
    }
    qr.setAttribute("shape-rendering", "crispEdges");
  }

  // ---------- Latest release and stars from GitHub's public API ----------
  // The repository may be private or the API rate-limited: then the page keeps its plain text.
  var REPO = "vuegram/vuegram";
  var CACHE_KEY = "vuegram:gh";
  var CACHE_MS = 60 * 60 * 1000;

  var readCache = function () {
    try {
      var raw = window.sessionStorage.getItem(CACHE_KEY);
      if (!raw) return null;
      var data = JSON.parse(raw);
      return Date.now() - data.at < CACHE_MS ? data : null;
    } catch (e) { return null; }
  };
  var writeCache = function (data) {
    try { data.at = Date.now(); window.sessionStorage.setItem(CACHE_KEY, JSON.stringify(data)); } catch (e) { /* private mode */ }
  };
  var getJson = function (url) {
    var ctrl = "AbortController" in window ? new AbortController() : null;
    var t = ctrl ? window.setTimeout(function () { ctrl.abort(); }, 6000) : 0;
    return fetch(url, { headers: { Accept: "application/vnd.github+json" }, signal: ctrl ? ctrl.signal : undefined, credentials: "omit", referrerPolicy: "no-referrer" })
      .then(function (res) { window.clearTimeout(t); if (!res.ok) throw new Error(String(res.status)); return res.json(); });
  };
  var safeUrl = function (u) { return typeof u === "string" && u.indexOf("https://github.com/") === 0 ? u : null; };

  var apply = function (data) {
    if (data.version) {
      var tag = String(data.version).slice(0, 24);
      var short = tag.charAt(0) === "v" ? tag : "v" + tag;
      $$("[data-gh-version]").forEach(function (el) { el.textContent = "VueGram " + short + " is out"; });
      $$("[data-gh-version-long]").forEach(function (el) { el.textContent = "Latest: " + short; });
      if (data.url) $$("[data-gh-release-link]").forEach(function (el) { el.href = data.url; });
    }
    if (typeof data.stars === "number" && data.stars > 0) {
      var n = data.stars >= 1000 ? (data.stars / 1000).toFixed(data.stars >= 10000 ? 0 : 1) + "k" : String(data.stars);
      $$("[data-gh-stars]").forEach(function (el) { el.textContent = n; el.hidden = false; });
    }
  };

  if (!("fetch" in window)) return;
  var cached = readCache();
  if (cached) { apply(cached); return; }

  var result = {};
  Promise.all([
    getJson("https://api.github.com/repos/" + REPO + "/releases/latest").then(function (r) {
      if (r && typeof r.tag_name === "string") { result.version = r.tag_name; result.url = safeUrl(r.html_url); }
    }).catch(function () {}),
    getJson("https://api.github.com/repos/" + REPO).then(function (r) {
      if (r && typeof r.stargazers_count === "number") result.stars = r.stargazers_count;
    }).catch(function () {})
  ]).then(function () {
    writeCache(result);
    apply(result);
  });
})();
