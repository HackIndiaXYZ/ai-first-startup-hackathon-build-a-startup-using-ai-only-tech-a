const message = document.getElementById("global-message");

function show(text, kind) {
    message.textContent = text;
    message.className = `message ${kind}`;
    message.hidden = false;
}

async function session() {
    const response = await fetch("/api/auth/session", { credentials: "same-origin" });
    return response.json();
}

async function postJson(url, body) {
    const { csrfHeader, csrfToken } = await session();
    const response = await fetch(url, {
        method: "POST",
        credentials: "same-origin",
        headers: { "Content-Type": "application/json", [csrfHeader]: csrfToken },
        body: JSON.stringify(body)
    });
    let data = {};
    try {
        data = await response.json();
    } catch {
        // body is optional
    }
    if (!response.ok) throw new Error(data.message || `Request failed (${response.status}).`);
    return data;
}

function wire(id, handler) {
    const form = document.getElementById(id);
    if (!form) return;
    const submit = form.querySelector('button[type="submit"]');
    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        if (id === "signup-form" && !validateSignup()) return;
        const initialText = submit.textContent;
        submit.disabled = true;
        submit.setAttribute("aria-busy", "true");
        submit.textContent = id === "signup-form" ? "Creating account…" : "Working…";
        message.hidden = true;
        try {
            await handler();
        } catch (error) {
            show(error.message || "Something went wrong. Please try again.", "error");
        } finally {
            submit.disabled = false;
            submit.removeAttribute("aria-busy");
            submit.textContent = initialText;
        }
    });
}

const value = (id) => document.getElementById(id).value;

function setFieldError(id, text) {
    const field = document.getElementById(id);
    const error = document.getElementById(`${id}-error`);
    if (!field || !error) return !text;
    error.textContent = text || "";
    error.hidden = !text;
    field.setAttribute("aria-invalid", String(Boolean(text)));
    field.classList.toggle("invalid", Boolean(text));
    return !text;
}

function signupFieldError(id) {
    const field = document.getElementById(id);
    const current = field.value.trim();
    if (id === "name") return current ? "" : "Enter your name.";
    if (id === "email") {
        if (!current) return "Enter your email address.";
        return field.validity.typeMismatch ? "Enter a valid email address." : "";
    }
    if (id === "password") {
        const password = field.value;
        if (password.length < 8) return "Use at least 8 characters.";
        if (new TextEncoder().encode(password).length > 72) return "Password must be no more than 72 bytes.";
        if (!/[A-Z]/.test(password) || !/[a-z]/.test(password)
                || !/\d/.test(password) || !/[^A-Za-z0-9]/.test(password)) {
            return "Include uppercase, lowercase, a number and a symbol.";
        }
        return "";
    }
    if (id === "confirm-password") {
        return current && field.value === value("password") ? "" : "Passwords must match.";
    }
    if (id === "phone") {
        return current && !/^\d{6,13}$/.test(current.replace(/[ ()-]/g, ""))
            ? "Enter the local phone number using digits only; the country code is added for you."
            : "";
    }
    if (id === "postal-code") {
        return current && !/^\d{6}$/.test(current) ? "Enter a 6-digit PIN code." : "";
    }
    return "";
}

function validateSignupField(id) {
    return setFieldError(id, signupFieldError(id));
}

function validateSignup() {
    const ids = ["name", "email", "password", "confirm-password", "phone", "postal-code"];
    ids.forEach((id) => {
        document.getElementById(id).dataset.validated = "true";
    });
    const valid = ids.map(validateSignupField).every(Boolean);
    if (!valid) {
        document.querySelector("#signup-form .invalid")?.focus();
    }
    return valid;
}

for (const id of ["name", "email", "password", "confirm-password", "phone", "postal-code"]) {
    const field = document.getElementById(id);
    if (!field) continue;
    field.addEventListener("blur", () => {
        field.dataset.validated = "true";
        validateSignupField(id);
    });
    field.addEventListener("input", () => {
        if (field.dataset.validated === "true") validateSignupField(id);
        if (id === "password" && document.getElementById("confirm-password").dataset.validated === "true") {
            validateSignupField("confirm-password");
        }
    });
}

let signupWelcomeTimer;

function closeSignupWelcome() {
    const overlay = document.getElementById("signup-welcome");
    if (!overlay) return;
    overlay.hidden = true;
    clearTimeout(signupWelcomeTimer);
    document.querySelector('#signup-form a[href="/login.html"]')?.focus();
}

document.getElementById("signup-welcome-continue")?.addEventListener("click", closeSignupWelcome);

function showSignupWelcome(name) {
    const overlay = document.getElementById("signup-welcome");
    if (!overlay) return;
    document.getElementById("signup-welcome-name").textContent = name || "shopkeeper";
    overlay.hidden = false;
    document.getElementById("signup-welcome-continue").focus();
    signupWelcomeTimer = window.setTimeout(closeSignupWelcome, 2500);
}

wire("signup-form", async () => {
    const name = value("name").trim();
    const email = value("email").trim();
    const password = value("password");
    const localPhone = value("phone").replace(/[ ()-]/g, "");
    await postJson("/api/auth/signup", {
        name,
        email,
        password,
        confirmPassword: value("confirm-password"),
        phone: localPhone ? `${document.getElementById("phone-country").value}${localPhone}` : null,
        street: value("street").trim() || null,
        city: value("city").trim() || null,
        state: value("state").trim() || null,
        postalCode: value("postal-code").trim() || null
    });
    show("If this address can be registered, check your email for a verification link before signing in.", "success");
    document.getElementById("signup-form").reset();
    showSignupWelcome(name);
});

wire("forgot-form", async () => {
    const data = await postJson("/api/auth/forgot-password", { email: value("email").trim() });
    show(data.message, "success");
});

wire("reset-form", async () => {
    const token = new URLSearchParams(window.location.search).get("token");
    if (!token) throw new Error("This reset link is incomplete. Please request a new one.");
    const data = await postJson("/api/auth/reset-password", { token, password: value("password") });
    show(data.message, "success");
    document.getElementById("reset-form").reset();
    setTimeout(() => window.location.replace("/login.html"), 2000);
});
