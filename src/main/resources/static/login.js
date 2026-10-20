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

async function login(event) {
    event.preventDefault();
    submit.disabled = true;
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
