const $ = (id) => document.getElementById(id);
const METHODS = { CASH: "Cash", UPI: "UPI", CARD: "Card", BANK_TRANSFER: "Bank transfer", OTHER: "Other" };

function money(value, currency) {
    return new Intl.NumberFormat("en-IN", { style: "currency", currency: currency || "INR" }).format(Number(value));
}

function day(value) {
    const date = /^\d{4}-\d{2}-\d{2}$/.test(value) ? new Date(`${value}T00:00:00`) : new Date(value);
    return date.toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" });
}

function row(cells, numericLast) {
    const tr = document.createElement("tr");
    cells.forEach((text, index) => {
        const td = document.createElement("td");
        td.textContent = text;
        if (numericLast && index === cells.length - 1) td.className = "num";
        tr.appendChild(td);
    });
    return tr;
}

async function load() {
    const id = new URLSearchParams(window.location.search).get("id");
    try {
        const response = await fetch(`/api/orders/${encodeURIComponent(id || "")}`, { credentials: "same-origin" });
        if (response.status === 401) {
            window.location.replace("/login.html");
            return;
        }
        if (!response.ok) throw new Error("Receipt not found.");
        const order = await response.json();
        const cur = order.currency;
        $("meta").textContent = `${order.customerName || "Customer"} · ${day(order.orderDate)}`;
        $("items").replaceChildren(...(order.items.length
            ? order.items.map((i) => row([i.description, [i.quantity, i.unit].filter((v) => v != null && v !== "").join(" "),
                i.lineTotal == null ? "—" : money(i.lineTotal, cur)], true))
            : [row(["No itemised lines", "", ""], true)]));
        $("payments").replaceChildren(...(order.payments.length
            ? order.payments.map((p) => row([day(p.paidAt), METHODS[p.method] || "—", money(p.amount, cur)], true))
            : [row(["No payments recorded", "", ""], true)]));
        $("total").textContent = money(order.total, cur);
        $("paid").textContent = money(order.totalPaid, cur);
        $("balance").textContent = money(order.outstandingBalance, cur);
        $("status").hidden = true;
        $("receipt").hidden = false;
        $("print").hidden = false;
        $("print").addEventListener("click", () => window.print());
    } catch (error) {
        $("status").textContent = error.message;
    }
}

load();
