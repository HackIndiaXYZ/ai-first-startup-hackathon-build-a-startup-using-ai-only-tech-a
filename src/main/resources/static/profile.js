const $ = (id) => document.getElementById(id);
const LANGUAGES = ["HINDI", "HINGLISH", "ENGLISH", "MARATHI", "BENGALI", "TAMIL"];
let csrf;
let profile;
let settings;

function showProfileMessage(text, kind = "success") {
    const element = $("profile-message");
    element.textContent = text;
    element.className = `message ${kind}`;
    element.hidden = !text;
}

async function session() {
    const response = await fetch("/api/auth/session", { credentials: "same-origin" });
    const data = await response.json();
    if (!data.authenticated) {
        window.location.replace("/login.html");
        throw new Error("Please sign in again.");
    }
    csrf = { header: data.csrfHeader, token: data.csrfToken };
    if (data.ownerEmail) {
        localStorage.setItem("dukaan-owner-email", data.ownerEmail);
    }
}

async function api(url, options = {}) {
    const headers = { ...(options.body instanceof FormData ? {} : { "Content-Type": "application/json" }) };
    if (options.method && !["GET", "HEAD"].includes(options.method.toUpperCase())) {
        headers[csrf.header] = csrf.token;
    }
    const response = await fetch(url, { ...options, headers, credentials: "same-origin" });
    if (response.status === 401) {
        window.location.replace("/login.html");
        throw new Error("Please sign in again.");
    }
    const text = await response.text();
    let body = null;
    if (text) {
        try { body = JSON.parse(text); } catch { body = text; }
    }
    if (!response.ok) throw new Error(body?.message || `Request failed (${response.status}).`);
    return body;
}

function setTheme(theme, email = profile?.email, persist = true) {
    document.documentElement.dataset.theme = theme;
    if (persist && email) localStorage.setItem(`dukaan-theme:${email}`, theme);
}

function renderAvatar(url, name) {
    const elements = [$("profile-avatar"), $("profile-avatar-badge")];
    const initials = (name || "DD").trim().split(/\s+/).slice(0, 2).map((part) => part[0]).join("").toUpperCase();
    elements.forEach((element) => {
        element.replaceChildren();
        if (url) {
            const image = document.createElement("img");
            image.src = url;
            image.alt = "Profile picture";
            element.appendChild(image);
        } else {
            element.textContent = initials || "DD";
        }
    });
}

function fillLanguages(id, selected) {
    const select = $(id);
    select.replaceChildren();
    LANGUAGES.forEach((language) => {
        const option = document.createElement("option");
        option.value = language;
        option.textContent = language.charAt(0) + language.slice(1).toLowerCase();
        select.appendChild(option);
    });
    select.value = selected;
}

async function loadPage() {
    await session();
    [profile, settings] = await Promise.all([api("/api/profile"), api("/api/settings")]);
    $("profile-name").value = profile.name || "";
    $("profile-email").value = profile.email || "";
    $("profile-phone").value = profile.phone || "";
    $("shop-name").value = profile.shopName || "";
    $("profile-street").value = profile.street || "";
    $("profile-city").value = profile.city || "";
    $("profile-state").value = profile.state || "";
    $("profile-postal").value = profile.postalCode || "";
    $("phone-verification-status").textContent = profile.phone
        ? (profile.phoneVerified ? "Phone verified" : "Phone not verified (contact only)")
        : "No phone number added.";
    renderAvatar(profile.avatarUrl, profile.name);
    if (!profile.passwordLoginEnabled) {
        $("change-password-form").hidden = true;
        const note = document.createElement("p");
        note.className = "quiet";
        note.textContent = "This account signs in with Google. Use Forgot password if you want to add password sign-in.";
        $("change-password-form").before(note);
    }
    $("theme").value = settings.theme;
    fillLanguages("ui-language", settings.uiLanguage);
    fillLanguages("reminder-language", settings.reminderDefaultLanguage);
    for (const id of [
        "in-app-notifications", "browser-push-enabled", "payment-due-notifications",
        "overdue-notifications", "new-order-notifications", "payment-received-notifications",
        "daily-summary-notifications"
    ]) {
        const property = id.replace(/-([a-z])/g, (_, letter) => letter.toUpperCase());
        $(id).checked = settings[property];
    }
    setTheme(settings.theme);
}

$("profile-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    try {
        profile = await api("/api/profile", {
            method: "PUT",
            body: JSON.stringify({
                name: $("profile-name").value.trim(),
                phone: $("profile-phone").value.trim() || null,
                shopName: $("shop-name").value.trim() || null,
                street: $("profile-street").value.trim() || null,
                city: $("profile-city").value.trim() || null,
                state: $("profile-state").value.trim() || null,
                postalCode: $("profile-postal").value.trim() || null
            })
        });
        $("phone-verification-status").textContent = profile.phone
            ? (profile.phoneVerified ? "Phone verified" : "Phone not verified (contact only)")
            : "No phone number added.";
        renderAvatar(profile.avatarUrl, profile.name);
        showProfileMessage("Profile saved.");
    } catch (error) {
        showProfileMessage(error.message || "Could not save profile.", "error");
    }
});

$("upload-avatar").addEventListener("click", async () => {
    const file = $("avatar-upload").files?.[0];
    if (!file) {
        showProfileMessage("Choose a JPG, PNG or WebP image first.", "error");
        return;
    }
    if (file.size > 2 * 1024 * 1024) {
        showProfileMessage("Profile pictures must be no larger than 2 MB.", "error");
        return;
    }
    const form = new FormData();
    form.append("avatar", file);
    try {
        profile = await api("/api/profile/avatar", { method: "POST", body: form });
        renderAvatar(profile.avatarUrl, profile.name);
        $("avatar-upload").value = "";
        showProfileMessage("Profile picture updated.");
    } catch (error) {
        showProfileMessage(error.message || "Could not upload profile picture.", "error");
    }
});

$("delete-avatar").addEventListener("click", async () => {
    try {
        await api("/api/profile/avatar", { method: "DELETE" });
        profile.avatarUrl = null;
        renderAvatar(null, profile.name);
        showProfileMessage("Profile picture deleted.");
    } catch (error) {
        showProfileMessage(error.message || "Could not delete profile picture.", "error");
    }
});

$("settings-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const body = {
        theme: $("theme").value,
        uiLanguage: $("ui-language").value,
        reminderDefaultLanguage: $("reminder-language").value
    };
    for (const id of [
        "in-app-notifications", "browser-push-enabled", "payment-due-notifications",
        "overdue-notifications", "new-order-notifications", "payment-received-notifications",
        "daily-summary-notifications"
    ]) {
        const property = id.replace(/-([a-z])/g, (_, letter) => letter.toUpperCase());
        body[property] = $(id).checked;
    }
    try {
        const settings = await api("/api/settings", { method: "PUT", body: JSON.stringify(body) });
        setTheme(settings.theme, profile.email);
        showProfileMessage("Preferences saved.");
    } catch (error) {
        showProfileMessage(error.message || "Could not save preferences.", "error");
    }
});

$("theme").addEventListener("change", () => {
    setTheme($("theme").value, profile?.email, false);
});

$("change-password-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const password = $("new-password").value;
    if (password !== $("confirm-new-password").value) {
        showProfileMessage("Passwords do not match.", "error");
        return;
    }
    try {
        const result = await api("/api/profile/change-password", {
            method: "POST",
            body: JSON.stringify({
                currentPassword: $("current-password").value,
                newPassword: password,
                confirmPassword: $("confirm-new-password").value
            })
        });
        $("change-password-form").reset();
        showProfileMessage(result.message);
    } catch (error) {
        showProfileMessage(error.message || "Could not change password.", "error");
    }
});

$("delete-account").addEventListener("click", async () => {
    if (!window.confirm("Permanently delete your account and all shop data? This cannot be undone.")) return;
    try {
        await api("/api/profile", {
            method: "DELETE",
            body: JSON.stringify({ password: $("delete-password").value })
        });
        localStorage.removeItem(`dukaan-theme:${profile.email}`);
        window.location.replace("/login.html");
    } catch (error) {
        showProfileMessage(error.message || "Could not delete account.", "error");
    }
});

loadPage().catch((error) => showProfileMessage(error.message || "Could not load profile.", "error"));
