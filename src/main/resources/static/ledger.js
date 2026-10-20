const $ = (id) => document.getElementById(id);
const methods = { CASH: "Cash", UPI: "UPI", CARD: "Card", BANK_TRANSFER: "Bank transfer", OTHER: "Other" };

function money(value, currency) {
    return new Intl.NumberFormat("en-IN", { style: "currency", currency: currency || "INR" }).format(Number(value));
}

function dateText(value) {
    if (!value) return "—";
    const date = /^\d{4}-\d{2}-\d{2}$/.test(value) ? new Date(`${value}T00:00:00`) : new Date(value);
    return date.toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" });
}

function cell(row, text, className = "") {
    const node = document.createElement("td");
    node.textContent = text;
    if (className) node.className = className;
    row.appendChild(node);
}

async function loadLedger() {
    const customerId = new URLSearchParams(window.location.search).get("customerId");
    if (!customerId) {
        $("ledger-status").hidden = true;
        $("ledger-error").textContent = "A customer was not selected.";
        $("ledger-error").hidden = false;
        return;
    }
    try {
        const response = await fetch(`/api/customers/${encodeURIComponent(customerId)}/ledger`, {
            credentials: "same-origin"
        });
        if (response.status === 401) {
            window.location.replace("/login.html");
            return;
        }
        if (!response.ok) {
            throw new Error(response.status === 404 ? "Customer ledger not found." : "Could not load the customer ledger.");
        }
        const ledger = await response.json();
        $("customer-name").textContent = ledger.customerName || "Unnamed customer";
        $("customer-contact").textContent = ledger.customerPhone || "";
        const currency = ledger.entries.find((entry) => entry.currency)?.currency || "INR";
        $("current-outstanding").textContent = money(ledger.currentOutstanding, currency);
        const rows = ledger.entries.map((entry) => {
            const row = document.createElement("tr");
            const label = entry.type === "ORDER" ? `Order · ${entry.orderStatus}` : "Payment";
            const details = entry.type === "PAYMENT"
                ? [methods[entry.paymentMethod], entry.note].filter(Boolean).join(" · ") || "Payment received"
                : "Order total";
            cell(row, dateText(entry.date));
            cell(row, label);
            cell(row, details);
            const amount = entry.type === "PAYMENT" ? `−${money(entry.amount, entry.currency)}` : money(entry.amount, entry.currency);
            cell(row, amount, "num");
            cell(row, money(entry.runningBalance, entry.currency), "num");
            return row;
        });
        $("ledger-entries").replaceChildren(...rows);
        $("ledger-status").hidden = true;
        $("ledger-content").hidden = false;
        $("print-ledger").hidden = false;
    } catch (error) {
        $("ledger-status").hidden = true;
        $("ledger-error").textContent = error.message || "Could not load the customer ledger.";
        $("ledger-error").hidden = false;
    }
}

$("print-ledger").addEventListener("click", () => window.print());
loadLedger();
