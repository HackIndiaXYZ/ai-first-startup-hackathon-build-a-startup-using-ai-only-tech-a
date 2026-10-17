const state = {
    draft: null,
    confirmedOrder: null,
    busy: false
};

const $ = (id) => document.getElementById(id);

function formatMoney(value, currency = "INR") {
    if (value === null || value === undefined || value === "") return "—";
    try {
        return new Intl.NumberFormat("en-IN", {
            style: "currency",
            currency: currency || "INR",
            maximumFractionDigits: 2
        }).format(Number(value));
    } catch {
        return `₹${Number(value).toLocaleString("en-IN", { maximumFractionDigits: 2 })}`;
    }
}

function showMessage(message, kind = "success") {
    const element = $("global-message");
    element.textContent = message;
    element.className = `message ${kind}`;
    element.hidden = !message;
}

function clearMessage() {
    $("global-message").hidden = true;
}

async function request(url, options = {}) {
    const response = await fetch(url, {
        headers: { "Content-Type": "application/json", ...(options.headers || {}) },
        ...options
    });
    const text = await response.text();
    let body = null;
    if (text) {
        try {
            body = JSON.parse(text);
        } catch {
            body = text;
        }
    }
    if (!response.ok) {
        const message = body?.message || body?.error || `Request failed (${response.status})`;
        throw new Error(message);
    }
    return body;
}

function setBusy(busy) {
    state.busy = busy;
    document.querySelectorAll("button").forEach((button) => {
        button.disabled = busy;
    });
    if (busy) {
        showMessage("Working…", "success");
    }
}

function setText(id, value) {
    $(id).textContent = value ?? "—";
}

function renderDraft(draft) {
    state.draft = draft;
    $("draft-section").hidden = false;
    $("update-draft").hidden = false;
    $("transcript").value = draft.transcript || "";

    const status = $("draft-status");
    status.textContent = (draft.status || "UNKNOWN").replaceAll("_", " ");
    status.dataset.status = draft.status || "";

    const order = draft.extractedOrder;
    setText("customer-name", order?.customerName);
    setText("customer-phone", order?.customerPhone);
    setText("order-date", order?.orderDate);
    setText("due-date", order?.dueDate);
    setText("order-total", formatMoney(order?.total, order?.currency));
    setText("advance-amount", formatMoney(order?.advanceAmount, order?.currency));

    const items = $("items-list");
    items.replaceChildren();
    if (!order?.items?.length) {
        const empty = document.createElement("p");
        empty.className = "empty-items";
        empty.textContent = "No item lines extracted.";
        items.appendChild(empty);
    } else {
        order.items.forEach((item) => {
            const row = document.createElement("div");
            row.className = "item-row";
            const details = document.createElement("div");
            const title = document.createElement("strong");
            title.textContent = item.description || "Unnamed item";
            const meta = document.createElement("small");
            const quantity = item.quantity == null ? "" : `${item.quantity} ${item.unit || ""}`.trim();
            meta.textContent = [quantity, item.unitPrice == null ? "" : formatMoney(item.unitPrice, order.currency)]
                .filter(Boolean).join(" · ");
            details.append(title, meta);
            const amount = document.createElement("strong");
            amount.textContent = formatMoney(item.lineTotal, order.currency);
            row.append(details, amount);
            items.appendChild(row);
        });
    }

    const notes = [...(draft.reviewNotes || [])];
    if (draft.persistedReviewNotes && !notes.includes(draft.persistedReviewNotes)) {
        notes.push(draft.persistedReviewNotes);
    }
    $("review-notes").hidden = notes.length === 0;
    $("review-notes-list").replaceChildren(...notes.map((note) => {
        const item = document.createElement("li");
        item.textContent = note;
        return item;
    }));

    const requiresReview = Boolean(draft.reviewRequired) || draft.status === "NEEDS_REVIEW";
    $("review-alert").hidden = !requiresReview;
    $("review-alert").textContent = requiresReview
        ? "Please check the extracted details before confirming."
        : "";
    $("acknowledgement-row").hidden = !requiresReview;
    $("acknowledged-review").checked = false;
    $("confirm-draft").disabled = draft.status === "CONFIRMED" || draft.status === "DISCARDED";
}

function renderPaymentOrder(order, totalPaid = null, outstanding = null) {
    state.confirmedOrder = order;
    $("payment-section").hidden = false;
    setText("payment-customer", order.customerName || "Confirmed order");
    const balance = outstanding == null ? Number(order.total) : outstanding;
    $("payment-balance").textContent = `Outstanding ${formatMoney(balance, order.currency)}`;
    $("payment-amount").max = balance > 0 ? balance : "";
    $("record-payment").disabled = balance <= 0;
}

async function createDraft() {
    const transcript = $("transcript").value.trim();
    if (!transcript) {
        showMessage("Enter a transcript before extracting an order.", "error");
        $("transcript").focus();
        return;
    }
    await runAction(async () => {
        const draft = await request("/api/drafts", {
            method: "POST",
            body: JSON.stringify({ transcript })
        });
        renderDraft(draft);
        showMessage("Draft extracted. Review it before confirming.");
    });
}

async function updateDraft() {
    if (!state.draft?.id) return;
    const transcript = $("transcript").value.trim();
    if (!transcript) {
        showMessage("Enter a transcript before rerunning extraction.", "error");
        return;
    }
    await runAction(async () => {
        const draft = await request(`/api/drafts/${state.draft.id}`, {
            method: "PUT",
            body: JSON.stringify({ transcript })
        });
        renderDraft(draft);
        showMessage("Extraction rerun. Please review the updated details.");
    });
}

async function confirmDraft() {
    if (!state.draft?.id) return;
    await runAction(async () => {
        const result = await request(`/api/drafts/${state.draft.id}/confirm`, {
            method: "POST",
            body: JSON.stringify({ acknowledgedReview: $("acknowledged-review").checked })
        });
        renderDraft(result.draft);
        const initialPayment = Number(result.draft.extractedOrder?.advanceAmount || 0);
        renderPaymentOrder(result.order, initialPayment, Number(result.order.total) - initialPayment);
        showMessage("Order confirmed. You can record a payment below.");
        await loadOutstanding();
    });
}

async function recordPayment() {
    if (!state.confirmedOrder?.id) return;
    const amount = Number($("payment-amount").value);
    if (!Number.isFinite(amount) || amount <= 0) {
        showMessage("Enter a payment amount greater than zero.", "error");
        return;
    }
    await runAction(async () => {
        const result = await request(`/api/orders/${state.confirmedOrder.id}/payments`, {
            method: "POST",
            body: JSON.stringify({
                amount,
                note: $("payment-note").value.trim() || null
            })
        });
        renderPaymentOrder(state.confirmedOrder, result.totalPaid, result.outstandingBalance);
        $("payment-amount").value = "";
        $("payment-note").value = "";
        showMessage(`Payment saved. Balance: ${formatMoney(result.outstandingBalance, state.confirmedOrder.currency)}.`);
        await loadOutstanding();
    });
}

async function loadOutstanding() {
    $("dues-loading").hidden = false;
    $("dues-list").hidden = true;
    try {
        const orders = await request("/api/orders/outstanding");
        const list = $("dues-list");
        list.replaceChildren();
        if (!orders.length) {
            $("dues-loading").textContent = "No outstanding dues. You are all caught up.";
            $("dues-loading").hidden = false;
            return;
        }
        orders.forEach((order) => {
            const row = document.createElement("article");
            row.className = "due-row";
            const details = document.createElement("div");
            const customer = document.createElement("strong");
            customer.textContent = order.customerName || "Unnamed customer";
            const metadata = document.createElement("p");
            metadata.textContent = `Total ${formatMoney(order.total, order.currency)} · Paid ${formatMoney(order.totalPaid, order.currency)}`;
            details.append(customer, metadata);
            const balance = document.createElement("div");
            const due = document.createElement("strong");
            due.textContent = formatMoney(order.outstandingBalance, order.currency);
            const label = document.createElement("span");
            label.textContent = "outstanding";
            balance.append(due, label);
            row.append(details, balance);
            list.appendChild(row);
        });
        $("dues-loading").hidden = true;
        list.hidden = false;
    } catch (error) {
        $("dues-loading").textContent = "Could not load outstanding dues.";
        showMessage(error.message, "error");
    }
}

async function runAction(action) {
    clearMessage();
    setBusy(true);
    try {
        await action();
    } catch (error) {
        showMessage(error.message, "error");
    } finally {
        setBusy(false);
    }
}

$("create-draft").addEventListener("click", createDraft);
$("update-draft").addEventListener("click", updateDraft);
$("confirm-draft").addEventListener("click", confirmDraft);
$("record-payment").addEventListener("click", recordPayment);
$("refresh-dues").addEventListener("click", () => runAction(loadOutstanding));

loadOutstanding();
