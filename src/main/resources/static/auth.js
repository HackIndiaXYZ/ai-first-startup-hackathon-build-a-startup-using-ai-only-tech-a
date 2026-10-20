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
    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        const submit = document.getElementById("submit");
        submit.disabled = true;
        message.hidden = true;
        try {
            await handler();
        } catch (error) {
            show(error.message || "Something went wrong. Please try again.", "error");
        } finally {
            submit.disabled = false;
        }
    });
}

const value = (id) => document.getElementById(id).value;

wire("signup-form", async () => {
    const email = value("email").trim();
    const password = value("password");
    if (password !== value("confirm-password")) {
        throw new Error("Passwords do not match.");
    }
    const localPhone = value("phone").replace(/[ ()-]/g, "");
    await postJson("/api/auth/signup", {
        name: value("name").trim(),
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
