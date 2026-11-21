"use strict";

(() => {
  const pages = ["dashboard", "modules", "settings", "appearance"];
  const titles = { dashboard: "OVERVIEW", modules: "MODULES", settings: "SETTINGS", appearance: "APPEARANCE" };
  const toast = document.getElementById("toast");
  const stage = document.querySelector(".stage");
  const windowElement = document.querySelector(".window");
  const scaleInput = document.getElementById("scale-setting");
  const scaleOutput = document.getElementById("scale-output");
  const minimumScale = 0.75;
  const maximumScale = 1.25;
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
    input.setAttribute("aria-valuetext", format(clamped));
  }

  function applyUiScale(scale) {
    const stageStyle = window.getComputedStyle(stage);
    const availableWidth = Math.max(1, stage.clientWidth
      - parseFloat(stageStyle.paddingLeft) - parseFloat(stageStyle.paddingRight));
    const availableHeight = Math.max(1, stage.clientHeight
      - parseFloat(stageStyle.paddingTop) - parseFloat(stageStyle.paddingBottom));
    const baseWidth = Math.max(1, Math.min(1160, availableWidth / maximumScale));
    const baseHeight = Math.max(1, Math.min(720, availableHeight / maximumScale));
    windowElement.style.width = `${baseWidth}px`;
    windowElement.style.height = `${baseHeight}px`;
    windowElement.style.setProperty("--ui-scale", String(
      Math.min(maximumScale, Math.max(minimumScale, scale))
    ));
  }

  function setSwitch(button, checked) {
    button.setAttribute("aria-checked", String(Boolean(checked)));
  }

  let expandedModuleId = null;

  function formatSettingValue(setting) {
    if (setting.type === "number") {
      const unit = setting.unit || "";
      return `${setting.value}${unit}`;
    }
    if (setting.type === "slots") {
      const slots = Array.isArray(setting.value) ? setting.value : [];
      return slots.length === 0 ? "Any" : slots.map(slot => slot + 1).join(", ");
    }
    if (setting.type === "text") {
      const text = String(setting.value || "");
      return text.trim() === "" ? "Any" : text;
    }
    return String(setting.value);
  }

  function moduleSettingPayload(moduleId, settingId, value) {
    return { action: "moduleSetting", module: moduleId, setting: settingId, value };
  }

  function appendSettingControl(container, module, setting) {
    const row = document.createElement("div");
    row.className = "setting-row module-setting-row";
    const label = document.createElement("label");
    label.className = "module-setting-label";
    label.textContent = setting.name;
    if (setting.description) {
      label.title = setting.description;
    }
    row.append(label);

    if (setting.type === "boolean") {
      const toggle = document.createElement("button");
      toggle.className = "toggle toggle-compact";
      toggle.type = "button";
      toggle.setAttribute("role", "switch");
      toggle.setAttribute("aria-checked", String(Boolean(setting.value)));
      toggle.setAttribute("aria-label", setting.name);
      toggle.addEventListener("click", () =>
        change(moduleSettingPayload(module.id, setting.id, !setting.value)));
      row.append(toggle);
    } else if (setting.type === "number") {
      const input = document.createElement("input");
      input.className = "range module-setting-range";
      input.type = "range";
      input.min = String(setting.minimum);
      input.max = String(setting.maximum);
      input.step = String(setting.step || 0.1);
      const output = document.createElement("output");
      output.className = "module-setting-value";
      const format = value => `${value}${setting.unit || ""}`;
      updateRange(input, output, Number(setting.value),
        Number(setting.minimum), Number(setting.maximum), format);
      input.addEventListener("change", event => {
        change(moduleSettingPayload(module.id, setting.id, Number(event.currentTarget.value)));
      });
      row.append(input, output);
    } else if (setting.type === "choice") {
      const group = document.createElement("div");
      group.className = "module-setting-choices";
      for (const option of setting.options || []) {
        const optionButton = document.createElement("button");
        optionButton.className = "choice-chip";
        optionButton.type = "button";
        optionButton.textContent = option;
        optionButton.classList.toggle("selected",
          String(option).toLowerCase() === String(setting.value).toLowerCase());
        optionButton.addEventListener("click", () =>
          change(moduleSettingPayload(module.id, setting.id, option)));
        group.append(optionButton);
      }
      row.append(group);
    } else if (setting.type === "slots") {
      const group = document.createElement("div");
      group.className = "module-setting-slots";
      const selected = Array.isArray(setting.value) ? setting.value.map(Number) : [];
      for (let slot = 0; slot < 9; slot++) {
        const slotButton = document.createElement("button");
        slotButton.className = "choice-chip slot-chip";
        slotButton.type = "button";
        slotButton.textContent = String(slot + 1);
        slotButton.classList.toggle("selected", selected.includes(slot));
        slotButton.addEventListener("click", () => {
          const next = selected.includes(slot)
            ? selected.filter(value => value !== slot)
            : [...selected, slot].sort((a, b) => a - b);
          change(moduleSettingPayload(module.id, setting.id, next));
        });
        group.append(slotButton);
      }
      row.append(group);
    } else {
      const input = document.createElement("input");
      input.className = "text-input module-setting-text";
      input.type = "text";
      input.value = String(setting.value || "");
      input.placeholder = "Any";
      input.addEventListener("change", event => {
        change(moduleSettingPayload(module.id, setting.id, String(event.currentTarget.value)));
      });
      row.append(input);
    }
    container.append(row);
  }

  function appendModuleSettingsPanel(list, module) {
    if (!module.hasSettings || expandedModuleId !== module.id) {
      return;
    }
    const panel = document.createElement("div");
    panel.className = "module-settings";
    const visibleSettings = (module.settings || []).filter(setting => setting.visible);
    if (module.error) {
      const error = document.createElement("div");
      error.className = "module-setting-error";
      error.textContent = module.error;
      panel.append(error);
    }
    if (visibleSettings.length === 0) {
      const empty = document.createElement("div");
      empty.className = "module-setting-empty";
      empty.textContent = "No configurable settings.";
      panel.append(empty);
    } else {
      for (const setting of visibleSettings) {
        appendSettingControl(panel, module, setting);
      }
    }
    list.append(panel);
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
      if (module.status) {
        const status = document.createElement("div");
        status.className = "module-status";
        status.textContent = module.status;
        copy.append(status);
      }
      const actions = document.createElement("div");
      actions.className = "module-card-actions";
      const toggle = document.createElement("button");
      toggle.className = "toggle";
      toggle.type = "button";
      toggle.setAttribute("role", "switch");
      toggle.setAttribute("aria-checked", String(module.enabled));
      toggle.setAttribute("aria-label", `Toggle ${module.name}`);
      toggle.addEventListener("click", () => change({ action: "module", id: module.id, enabled: !module.enabled }));
      actions.append(toggle);
      if (module.hasSettings) {
        const expand = document.createElement("button");
        expand.className = "module-expand";
        expand.type = "button";
        expand.setAttribute("aria-expanded", String(expandedModuleId === module.id));
        expand.setAttribute("aria-label", `${module.name} settings`);
        expand.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 9 6 6 6-6"/></svg>';
        expand.addEventListener("click", () => {
          expandedModuleId = expandedModuleId === module.id ? null : module.id;
          renderModules(modules);
        });
        expand.classList.toggle("open", expandedModuleId === module.id);
        actions.append(expand);
      }
      row.append(copy, actions);
      list.append(row);
      appendModuleSettingsPanel(list, module);
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
    applyUiScale(state.preferences.uiScale);
    setColor(state.preferences.accentColor);
    updateRange(document.getElementById("scale-setting"), document.getElementById("scale-output"),
      state.preferences.uiScale, minimumScale, maximumScale, value => `${Math.round(value * 100)}%`);
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
      if (state) {
        render(state);
      }
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
  scaleInput.addEventListener("input", event => {
    updateRange(scaleInput, scaleOutput, Number(event.currentTarget.value),
      minimumScale, maximumScale, value => `${Math.round(value * 100)}%`);
  });
  scaleInput.addEventListener("change", event => {
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

  window.addEventListener("resize", () => {
    if (state) {
      applyUiScale(state.preferences.uiScale);
    }
  });

  send({ action: "state" }).then(render).catch(error => showError(error.message));
})();
