"use strict";

(() => {
  const pages = ["dashboard", "modules", "settings", "appearance"];
  const titles = { dashboard: "OVERVIEW", modules: "MODULES", settings: "SETTINGS", appearance: "APPEARANCE" };
  const toast = document.getElementById("toast");
  let state = null;
  let toastTimer = 0;
  let closePending = false;

  function showError(message) {
    toast.textContent = message || "Ascend could not apply that change.";
    toast.classList.add("visible");
    window.clearTimeout(toastTimer);
    toastTimer = window.setTimeout(() => toast.classList.remove("visible"), 3200);
  }

  function send(payload) {
    return new Promise((resolve, reject) => {
      if (!window.cefQuery) {
        reject(new Error("Ascend's in-game connection is unavailable."));
        return;
      }
      window.cefQuery({
        request: JSON.stringify(payload),
        persistent: false,
        onSuccess: raw => {
          try {
            resolve(JSON.parse(raw));
          } catch {
            reject(new Error("Ascend received an invalid response."));
          }
        },
        onFailure: (code, message) => reject(new Error(message || `Request failed (${code}).`))
      });
    });
  }

  function setColor(color) {
    const normalized = color.toUpperCase();
    const red = parseInt(normalized.slice(1, 3), 16);
    const green = parseInt(normalized.slice(3, 5), 16);
    const blue = parseInt(normalized.slice(5, 7), 16);
    document.documentElement.style.setProperty("--accent", normalized);
    document.documentElement.style.setProperty("--accent-rgb", `${red}, ${green}, ${blue}`);
    document.documentElement.style.setProperty("--accent-soft", `rgba(${red}, ${green}, ${blue}, .13)`);
    document.getElementById("color-setting").value = normalized.toLowerCase();
    document.querySelectorAll(".color-choice").forEach(button => {
      button.classList.toggle("selected", button.dataset.color === normalized);
    });
  }

  function updateRange(input, output, value, minimum, maximum, format) {
    const clamped = Math.min(maximum, Math.max(minimum, value));
    input.value = String(clamped);
    output.value = format(clamped);
    const progress = ((clamped - minimum) / (maximum - minimum)) * 100;
    input.style.setProperty("--range-progress", `${progress}%`);
  }

  function setSwitch(button, checked) {
    button.setAttribute("aria-checked", String(Boolean(checked)));
  }

  function renderModules(modules) {
    const list = document.getElementById("module-list");
    list.replaceChildren();
    const query = document.getElementById("module-search").value.trim().toLowerCase();
    const filtered = modules.filter(module =>
      `${module.name} ${module.description} ${module.category}`.toLowerCase().includes(query)
    );
    document.getElementById("module-summary").textContent = `${filtered.length} MODULE${filtered.length === 1 ? "" : "S"}`;

    if (filtered.length === 0) {
      const wrapper = document.createElement("div");
      wrapper.className = "empty-state";
      const inner = document.createElement("div");
      inner.className = "empty-state-inner";
      const icon = document.createElement("div");
      icon.className = "empty-icon";
      icon.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h10"/><circle cx="2" cy="6" r=".5"/><circle cx="2" cy="12" r=".5"/><circle cx="2" cy="18" r=".5"/></svg>';
      const title = document.createElement("h2");
      title.textContent = query ? "No matching modules" : "Your modules will appear here";
      const description = document.createElement("p");
      description.textContent = query
        ? "Try another search to find a module."
        : "Ascend's module system is ready for its first features. Nothing is enabled until a module is added.";
      inner.append(icon, title, description);
      wrapper.append(inner);
      list.append(wrapper);
      return;
    }

    for (const module of filtered) {
      const row = document.createElement("article");
      row.className = "module-card";
      const copy = document.createElement("div");
      copy.className = "module-card-main";
      const name = document.createElement("div");
      name.className = "module-card-name";
      name.textContent = module.name;
      const description = document.createElement("div");
      description.className = "module-card-description";
      description.textContent = module.description;
      const category = document.createElement("div");
      category.className = "module-category";
      category.textContent = module.category;
      copy.append(name, description, category);
      const toggle = document.createElement("button");
      toggle.className = "toggle";
      toggle.type = "button";
      toggle.setAttribute("role", "switch");
      toggle.setAttribute("aria-checked", String(module.enabled));
      toggle.setAttribute("aria-label", `Toggle ${module.name}`);
      toggle.addEventListener("click", () => change({ action: "module", id: module.id, enabled: !module.enabled }));
      row.append(copy, toggle);
      list.append(row);
    }
  }

  function render(nextState) {
    state = nextState;
    const page = pages.includes(state.activePage) ? state.activePage : "dashboard";
    document.querySelectorAll("[data-page]").forEach(button => {
      const active = button.dataset.page === page;
      button.classList.toggle("active", active);
      button.setAttribute("aria-current", active ? "page" : "false");
    });
    document.querySelectorAll("[data-page-view]").forEach(section => {
      section.classList.toggle("active", section.dataset.pageView === page);
    });
    document.getElementById("breadcrumb-current").textContent = titles[page];
    document.getElementById("version-label").textContent = `Minecraft ${state.minecraftVersion}`;
    document.getElementById("stat-version").textContent = state.minecraftVersion;
    document.getElementById("keybind-display").textContent = state.openingKeybind.toUpperCase();
    document.getElementById("module-count").textContent = String(state.modules.length);
    document.getElementById("stat-total").textContent = String(state.modules.length);
    document.getElementById("stat-enabled").textContent = String(state.modules.filter(module => module.enabled).length);
    document.getElementById("connection-label").textContent = state.status === "ready" ? "CONNECTED" : "STARTING";
    document.getElementById("status-label").textContent = state.status === "ready" ? "READY" : "STARTING";
    document.querySelector(".connection-dot").classList.toggle("starting", state.status !== "ready");
    document.querySelector(".status-dot").classList.toggle("starting", state.status !== "ready");
    document.documentElement.classList.toggle("no-animations", !state.preferences.animationsEnabled);
    document.documentElement.style.zoom = String(state.preferences.uiScale);
    setColor(state.preferences.accentColor);
    updateRange(document.getElementById("scale-setting"), document.getElementById("scale-output"),
      state.preferences.uiScale, .8, 1.2, value => `${Math.round(value * 100)}%`);
    updateRange(document.getElementById("dimming-setting"), document.getElementById("dimming-output"),
      state.preferences.backgroundDimming, 0, .85, value => `${Math.round(value * 100)}%`);
    setSwitch(document.getElementById("escape-setting"), state.preferences.closeOnEscape);
    setSwitch(document.getElementById("animations-setting"), state.preferences.animationsEnabled);
    renderModules(state.modules);
  }

  async function change(payload) {
    try {
      const response = await send(payload);
      if (payload.action === "close") {
        return;
      }
      render(response);
    } catch (error) {
      showError(error.message);
    }
  }

  function requestClose() {
    if (closePending) return;
    closePending = true;
    document.querySelector(".window").classList.add("closing");
    window.setTimeout(() => change({ action: "close" }), 150);
  }

  document.querySelectorAll("[data-page]").forEach(button => {
    button.addEventListener("click", () => change({ action: "navigate", page: button.dataset.page }));
  });
  document.querySelectorAll("[data-go]").forEach(button => {
    button.addEventListener("click", () => change({ action: "navigate", page: button.dataset.go }));
  });
  document.getElementById("module-search").addEventListener("input", () => renderModules(state ? state.modules : []));
  document.getElementById("scale-setting").addEventListener("change", event => {
    change({ action: "setting", name: "uiScale", value: Number(event.currentTarget.value) });
  });
  document.getElementById("dimming-setting").addEventListener("change", event => {
    change({ action: "setting", name: "backgroundDimming", value: Number(event.currentTarget.value) });
  });
  document.getElementById("escape-setting").addEventListener("click", event => {
    change({ action: "setting", name: "closeOnEscape", value: event.currentTarget.getAttribute("aria-checked") !== "true" });
  });
  document.getElementById("animations-setting").addEventListener("click", event => {
    change({ action: "setting", name: "animationsEnabled", value: event.currentTarget.getAttribute("aria-checked") !== "true" });
  });
  document.getElementById("color-setting").addEventListener("change", event => {
    const color = event.currentTarget.value.toUpperCase();
    setColor(color);
    change({ action: "setting", name: "accentColor", value: color });
  });
  document.querySelectorAll(".color-choice").forEach(button => {
    button.addEventListener("click", () => {
      const color = button.dataset.color;
      setColor(color);
      change({ action: "setting", name: "accentColor", value: color });
    });
  });

  document.addEventListener("keydown", event => {
    if (event.key === "/" && !["INPUT", "TEXTAREA"].includes(document.activeElement.tagName)) {
      event.preventDefault();
      document.getElementById("module-search").focus();
    }
    if (event.key === "Escape" && state && state.preferences.closeOnEscape) {
      event.preventDefault();
      event.stopPropagation();
      requestClose();
    }
  });

  send({ action: "state" }).then(render).catch(error => showError(error.message));
})();
