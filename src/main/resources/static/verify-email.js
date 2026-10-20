const message = document.getElementById("global-message");
const token = new URLSearchParams(window.location.search).get("token");
window.history.replaceState({}, document.title, "/verify-email.html");

if (!token) {
    message.textContent = "This verification link is incomplete. Request a new verification email.";
    message.className = "message error";
} else {
    fetch(`/api/auth/verify?token=${encodeURIComponent(token)}`, { credentials: "same-origin" })
        .then(async (response) => {
            const data = await response.json();
            if (!response.ok) throw new Error(data.message || "This verification link is invalid or expired.");
            message.textContent = data.message;
            message.className = "message success";
        })
        .catch((error) => {
            message.textContent = error.message || "Could not verify this email. Request a new link.";
            message.className = "message error";
        });
}
