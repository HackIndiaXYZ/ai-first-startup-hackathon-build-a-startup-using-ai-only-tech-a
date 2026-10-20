const form = document.getElementById("login-form");
const message = document.getElementById("global-message");
const submit = document.getElementById("login-submit");

function showError(text) {
    message.textContent = text;
    message.className = "message error";
    message.hidden = false;
}

async function fetchSession() {
    const response = await fetch("/api/auth/session", { credentials: "same-origin" });
    return response.json();
}

function setFieldError(fieldId, errorId, text) {
    const field = document.getElementById(fieldId);
    const error = document.getElementById(errorId);
    error.textContent = text || "";
    error.hidden = !text;
    field.setAttribute("aria-invalid", String(Boolean(text)));
    field.classList.toggle("invalid", Boolean(text));
    return !text;
}

function validateLogin() {
    const email = document.getElementById("email");
    const password = document.getElementById("password");
    email.dataset.validated = "true";
    password.dataset.validated = "true";
    const emailError = !email.value.trim()
        ? "Enter your email address."
        : email.validity.typeMismatch ? "Enter a valid email address." : "";
    const passwordError = password.value ? "" : "Enter your password.";
    const emailValid = setFieldError("email", "login-email-error", emailError);
    const passwordValid = setFieldError("password", "login-password-error", passwordError);
    const valid = emailValid && passwordValid;
    if (!valid) document.querySelector("#login-form .invalid")?.focus();
    return valid;
}

for (const [id, fieldId] of [["email", "login-email"], ["password", "login-password"]]) {
    const field = document.getElementById(id);
    field.addEventListener("blur", () => {
        field.dataset.validated = "true";
        if (id === "email") {
            setFieldError(id, `${fieldId}-error`, !field.value.trim()
                ? "Enter your email address."
                : field.validity.typeMismatch ? "Enter a valid email address." : "");
        } else {
            setFieldError("password", "login-password-error", field.value ? "" : "Enter your password.");
        }
    });
    field.addEventListener("input", () => {
        if (field.dataset.validated !== "true") return;
        if (id === "email") {
            setFieldError(id, `${fieldId}-error`, !field.value.trim()
                ? "Enter your email address."
                : field.validity.typeMismatch ? "Enter a valid email address." : "");
        } else {
            setFieldError("password", "login-password-error", field.value ? "" : "Enter your password.");
        }
    });
}

async function login(event) {
    event.preventDefault();
    if (!validateLogin()) return;
    const initialText = submit.textContent;
    submit.disabled = true;
    submit.textContent = "Signing in…";
    submit.setAttribute("aria-busy", "true");
    message.hidden = true;
    try {
        const session = await fetchSession();
        if (session.authenticated) {
            window.location.replace("/");
            return;
        }
        const response = await fetch("/login", {
            method: "POST",
            credentials: "same-origin",
            headers: {
                "Content-Type": "application/x-www-form-urlencoded",
                [session.csrfHeader]: session.csrfToken
            },
            body: new URLSearchParams({
                email: document.getElementById("email").value.trim(),
                password: document.getElementById("password").value
            })
        });
        if (response.ok) {
            localStorage.setItem("dukaan-owner-email", document.getElementById("email").value.trim().toLowerCase());
            window.location.replace("/");
            return;
        }
        let text = `Sign in failed (${response.status}).`;
        try {
            text = (await response.json()).message || text;
        } catch {
            // keep the generic message
        }
        showError(text);
    } catch {
        showError("Could not reach the server. Check your connection and try again.");
    } finally {
        submit.disabled = false;
        submit.textContent = initialText;
        submit.removeAttribute("aria-busy");
    }
}

form.addEventListener("submit", login);
fetchSession().then((session) => {
    if (session.ownerEmail) localStorage.setItem("dukaan-owner-email", session.ownerEmail);
    if (session.authenticated) window.location.replace("/");
}).catch(() => {
    showError("Could not check your sign-in session. Try again after refreshing.");
});

fetch("/api/auth/google-enabled", { credentials: "same-origin" })
    .then((response) => response.json())
    .then((data) => {
        document.getElementById("google-login").hidden = !data.enabled;
    })
    .catch(() => {
        document.getElementById("google-login").hidden = true;
    });
