(() => {
  const root = document.documentElement;
  const body = document.body;
  const tabs = Array.from(document.querySelectorAll("[data-tab]"));
  const tabLinks = Array.from(document.querySelectorAll("[data-tab-link]"));
  const panels = Array.from(document.querySelectorAll("[data-panel]"));
  const menuButton = document.getElementById("menuButton");
  const themeToggle = document.getElementById("themeToggle");
  const search = document.getElementById("wikiSearch");
  const results = document.getElementById("searchResults");

  const validTabs = new Set(panels.map(panel => panel.dataset.panel));

  function showTab(name, updateHash = true) {
    if (!validTabs.has(name)) name = "overview";

    panels.forEach(panel => panel.classList.toggle("active", panel.dataset.panel === name));
    tabs.forEach(tab => tab.classList.toggle("active", tab.dataset.tab === name));
    tabLinks.forEach(link => link.classList.toggle("active", link.dataset.tabLink === name));

    if (updateHash) history.replaceState(null, "", "#" + name);
    body.classList.remove("menu-open");
    window.scrollTo({ top: 0, behavior: "instant" });
  }

  tabs.forEach(tab => tab.addEventListener("click", () => showTab(tab.dataset.tab)));
  tabLinks.forEach(link => {
    link.addEventListener("click", event => {
      event.preventDefault();
      showTab(link.dataset.tabLink);
    });
  });

  menuButton?.addEventListener("click", () => body.classList.toggle("menu-open"));

  document.addEventListener("click", event => {
    if (window.innerWidth <= 820 && body.classList.contains("menu-open")) {
      if (!event.target.closest("#sidebar") && !event.target.closest("#menuButton")) {
        body.classList.remove("menu-open");
      }
    }
  });

  const storedTheme = localStorage.getItem("inspectmc-wiki-theme");
  if (storedTheme === "light" || storedTheme === "dark") {
    root.dataset.theme = storedTheme;
  }

  themeToggle?.addEventListener("click", () => {
    root.dataset.theme = root.dataset.theme === "light" ? "dark" : "light";
    localStorage.setItem("inspectmc-wiki-theme", root.dataset.theme);
  });

  const searchable = panels.map(panel => {
    const title = panel.querySelector("h1")?.textContent.trim() || panel.dataset.panel;
    const headings = Array.from(panel.querySelectorAll("h2,h3")).map(el => el.textContent.trim());
    return {
      tab: panel.dataset.panel,
      title,
      headings,
      text: panel.textContent.replace(/\s+/g, " ").trim()
    };
  });

  function closeResults() {
    results.hidden = true;
    results.innerHTML = "";
  }

  function renderSearch(query) {
    const q = query.trim().toLowerCase();
    if (q.length < 2) {
      closeResults();
      return;
    }

    const matches = [];
    searchable.forEach(item => {
      if (!item.text.toLowerCase().includes(q)) return;

      let label = item.title;
      const heading = item.headings.find(h => h.toLowerCase().includes(q));
      if (heading) label += " · " + heading;

      const pos = item.text.toLowerCase().indexOf(q);
      const start = Math.max(0, pos - 70);
      const end = Math.min(item.text.length, pos + q.length + 100);
      const excerpt = (start > 0 ? "…" : "") + item.text.slice(start, end) + (end < item.text.length ? "…" : "");

      matches.push({ ...item, label, excerpt });
    });

    results.innerHTML = "";
    if (!matches.length) {
      results.innerHTML = '<div class="search-empty">No result found</div>';
      results.hidden = false;
      return;
    }

    matches.slice(0, 8).forEach(match => {
      const link = document.createElement("a");
      link.href = "#" + match.tab;
      link.className = "search-result";
      link.innerHTML = "<strong></strong><span></span>";
      link.querySelector("strong").textContent = match.label;
      link.querySelector("span").textContent = match.excerpt;
      link.addEventListener("click", event => {
        event.preventDefault();
        showTab(match.tab);
        search.value = "";
        closeResults();
      });
      results.appendChild(link);
    });
    results.hidden = false;
  }

  search?.addEventListener("input", () => renderSearch(search.value));
  search?.addEventListener("keydown", event => {
    if (event.key === "Escape") {
      search.value = "";
      closeResults();
      search.blur();
    }
  });

  document.addEventListener("keydown", event => {
    if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") {
      event.preventDefault();
      search?.focus();
    }
  });

  document.addEventListener("click", event => {
    if (!event.target.closest(".search-wrap")) closeResults();
  });

  const initial = location.hash.replace("#", "");
  showTab(validTabs.has(initial) ? initial : "overview", false);
})();

(() => {
  async function getJson(url, timeout = 6500) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeout);
    try {
      const response = await fetch(url, { cache: "no-store", signal: controller.signal });
      if (!response.ok) throw new Error("HTTP " + response.status);
      return await response.json();
    } finally {
      clearTimeout(timer);
    }
  }

  function compact(value) {
    const number = Number(value);
    if (!Number.isFinite(number)) return String(value ?? "");
    return new Intl.NumberFormat("en", { notation: "compact", maximumFractionDigits: 1 }).format(number);
  }

  async function loadDiscord() {
    const target = document.getElementById("discordStats");
    const badge = document.getElementById("discordBadge");
    if (!target || !badge) return;
    try {
      const data = await getJson("https://discord.com/api/v10/invites/kb8NSTF45n?with_counts=true&with_expiration=true");
      const online = data.approximate_presence_count;
      const members = data.approximate_member_count;
      if (!Number.isFinite(online) || !Number.isFinite(members)) return;
      target.innerHTML = '<span class="status-dot"></span>' + compact(online) + ' online <span class="community-divider">•</span> ' + compact(members) + ' members';
      badge.title = compact(online) + " online • " + compact(members) + " members";
    } catch {
      target.innerHTML = '<span class="status-dot"></span>Join the server';
    }
  }

  async function loadShield(url, targetId, suffix) {
    const target = document.getElementById(targetId);
    if (!target) return;
    try {
      const data = await getJson(url);
      const value = String(data.message || "").trim();
      if (value) target.textContent = value + " " + suffix;
    } catch {}
  }

  loadDiscord();
  loadShield("https://img.shields.io/curseforge/dt/1410772.json", "curseforgeStats", "downloads");
  loadShield("https://img.shields.io/modrinth/dt/recipe-item-sync.json", "modrinthStats", "downloads");

  setInterval(loadDiscord, 300000);
  setInterval(() => loadShield("https://img.shields.io/curseforge/dt/1410772.json", "curseforgeStats", "downloads"), 600000);
  setInterval(() => loadShield("https://img.shields.io/modrinth/dt/recipe-item-sync.json", "modrinthStats", "downloads"), 600000);
})();