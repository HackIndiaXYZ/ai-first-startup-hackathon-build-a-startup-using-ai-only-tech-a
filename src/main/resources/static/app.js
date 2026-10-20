const state = {
    draft: null,
    confirmedOrder: null,
    busy: false,
    todaySummary: null,
    historyFilter: "all",
    outstandingOrders: [],
    captureMode: "order",
    paymentReview: null,
    reminderDefaultLanguage: "HINGLISH"
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

let csrf = null;

async function loadCsrf() {
    const response = await fetch("/api/auth/session", { credentials: "same-origin" });
    const session = await response.json();
    if (!session.authenticated) {
        window.location.replace("/login.html");
        throw new Error("Please sign in again.");
    }
    csrf = { header: session.csrfHeader, token: session.csrfToken };
    if (session.ownerEmail) {
        localStorage.setItem("dukaan-owner-email", session.ownerEmail);
        const theme = localStorage.getItem(`dukaan-theme:${session.ownerEmail}`);
        if (theme) document.documentElement.dataset.theme = theme;
    }
    return session;
}

async function request(url, options = {}, retried = false) {
    const method = (options.method || "GET").toUpperCase();
    const isFormData = options.body instanceof FormData;
    const headers = { ...(isFormData ? {} : { "Content-Type": "application/json" }), ...(options.headers || {}) };
    if (method !== "GET" && method !== "HEAD") {
        if (!csrf) await loadCsrf();
        headers[csrf.header] = csrf.token;
    }
    const response = await fetch(url, { ...options, headers, credentials: "same-origin" });
    if (response.status === 401) {
        window.location.replace("/login.html");
        throw new Error("Please sign in again.");
    }
    if (response.status === 403 && !retried && method !== "GET") {
        csrf = null;
        return request(url, options, true);
    }
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
    if (draft.language) $("note-language").value = draft.language;
    const edited = draft.originalTranscript && draft.originalTranscript !== draft.transcript;
    $("original-note").hidden = !edited;
    $("original-note-text").textContent = edited ? draft.originalTranscript : "";

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
            body: JSON.stringify({ transcript, language: $("note-language").value })
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
            body: JSON.stringify({ transcript, language: $("note-language").value })
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
        await Promise.all([loadOutstanding(), loadHistory(), loadTodaySummary()]);
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
                method: $("payment-method").value || null,
                note: $("payment-note").value.trim() || null
            })
        });
        renderPaymentOrder(state.confirmedOrder, result.totalPaid, result.outstandingBalance);
        $("payment-amount").value = "";
        $("payment-note").value = "";
        $("payment-method").value = "";
        showMessage(`Payment saved. Balance: ${formatMoney(result.outstandingBalance, state.confirmedOrder.currency)}.`);
        await Promise.all([loadOutstanding(), loadHistory(), loadTodaySummary()]);
    });
}

function renderOutstanding() {
    const list = $("dues-list");
    const search = $("dues-search").value.trim().toLocaleLowerCase();
    const orders = state.outstandingOrders
        .filter((order) => (order.customerName || "").toLocaleLowerCase().includes(search))
        .sort((a, b) => $("dues-sort").value === "oldest"
            ? b.daysPending - a.daysPending || a.orderDate.localeCompare(b.orderDate)
            : Number(b.outstandingBalance) - Number(a.outstandingBalance));
    list.replaceChildren();
    if (!state.outstandingOrders.length) {
        $("dues-loading").textContent = "No outstanding dues. You are all caught up.";
        $("dues-loading").hidden = false;
        list.hidden = true;
        return;
    }
    if (!orders.length) {
        $("dues-loading").textContent = "No customers match your search.";
        $("dues-loading").hidden = false;
        list.hidden = true;
        return;
    }
    orders.forEach((order) => {
        const row = document.createElement("article");
        row.className = `due-row${order.daysPending > 7 ? " overdue" : ""}`;
        const details = document.createElement("div");
        const customer = document.createElement("a");
        customer.className = "customer-ledger-link";
        customer.href = `/ledger.html?customerId=${encodeURIComponent(order.customerId)}`;
        customer.textContent = order.customerName || "Unnamed customer";
        const metadata = document.createElement("p");
        metadata.textContent = `Order ${formatDate(order.orderDate)} · Total ${formatMoney(order.total, order.currency)} · Paid ${formatMoney(order.totalPaid, order.currency)}`;
        const dueStatus = el("span", "due-status", order.dueStatus || "Due date not set");
        const reminded = el("p", "last-reminded", `Last reminded: ${order.lastRemindedAt ? formatDate(order.lastRemindedAt) : "Never"}`);
        details.append(customer, metadata, dueStatus, reminded);
        const balance = document.createElement("div");
        const due = document.createElement("strong");
        due.textContent = formatMoney(order.outstandingBalance, order.currency);
        const label = el("span", "", "outstanding");
        balance.append(due, label);

        const actions = el("div", "due-row-actions");
        const toggle = el("button", "secondary", "Record payment");
        toggle.type = "button";
        const form = document.createElement("form");
        form.className = "due-payment-form";
        form.hidden = true;
        const amountLabel = document.createElement("label");
        amountLabel.textContent = "Amount";
        const amount = document.createElement("input");
        amount.type = "number";
        amount.min = "0.01";
        amount.step = "0.01";
        amount.max = String(order.outstandingBalance);
        amount.inputMode = "decimal";
        amount.required = true;
        amount.setAttribute("aria-label", `Payment amount for ${order.customerName || "customer"}`);
        amountLabel.appendChild(amount);
        const noteLabel = document.createElement("label");
        noteLabel.textContent = "Note (optional)";
        const note = document.createElement("input");
        note.type = "text";
        note.maxLength = 500;
        note.setAttribute("aria-label", "Optional payment note");
        noteLabel.appendChild(note);
        const error = el("p", "due-payment-error", "");
        error.setAttribute("role", "alert");
        const save = el("button", "primary", "Save payment");
        save.type = "submit";
        const cancel = el("button", "secondary", "Cancel");
        cancel.type = "button";
        cancel.addEventListener("click", () => { form.hidden = true; });
        form.append(amountLabel, noteLabel, error, save, cancel);
        toggle.addEventListener("click", () => {
            form.hidden = !form.hidden;
            if (!form.hidden) amount.focus();
        });
        form.addEventListener("submit", async (event) => {
            event.preventDefault();
            const value = Number(amount.value);
            if (!Number.isFinite(value) || value <= 0 || value > Number(order.outstandingBalance)) {
                error.textContent = `Enter an amount greater than zero and no more than ${formatMoney(order.outstandingBalance, order.currency)}.`;
                return;
            }
            save.disabled = true;
            error.textContent = "";
            try {
                const result = await request(`/api/orders/${encodeURIComponent(order.orderId)}/payments`, {
                    method: "POST",
                    body: JSON.stringify({ amount: value, note: note.value.trim() || null })
                });
                form.hidden = true;
                showMessage(`Payment saved. Balance: ${formatMoney(result.outstandingBalance, order.currency)}.`);
                await Promise.all([loadOutstanding(), loadHistory(), loadTodaySummary()]);
            } catch (requestError) {
                error.textContent = requestError.message || "Could not save payment. Please try again.";
            } finally {
                save.disabled = false;
            }
        });
        actions.append(toggle);
        row.append(details, balance, actions, form);
        list.appendChild(row);
    });
    $("dues-loading").hidden = true;
    list.hidden = false;
}

async function loadOutstanding() {
    $("dues-loading").hidden = false;
    $("dues-loading").textContent = "Loading dues…";
    try {
        state.outstandingOrders = await request("/api/orders/outstanding");
        if ($("global-message").classList.contains("error")) clearMessage();
        renderOutstanding();
    } catch (error) {
        $("dues-loading").textContent = "Could not load outstanding dues.";
        showMessage(error.message || "Could not load outstanding dues.", "error");
    }
}

const METHOD_LABELS = { CASH: "Cash", UPI: "UPI", CARD: "Card", BANK_TRANSFER: "Bank transfer", OTHER: "Other" };

function formatDate(value) {
    if (!value) return "—";
    const date = /^\d{4}-\d{2}-\d{2}$/.test(value) ? new Date(`${value}T00:00:00`) : new Date(value);
    return date.toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric", timeZone: /T/.test(value) ? "Asia/Kolkata" : undefined });
}

function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
}

function renderHistoryOrder(order) {
    const card = el("article", "history-order");
    const head = el("div", "history-head");
    const who = el("div");
    const customerLink = el("a", "customer-ledger-link", order.customerName || "Unnamed customer");
    customerLink.href = `/ledger.html?customerId=${encodeURIComponent(order.customerId)}`;
    who.append(customerLink,
        el("p", "", `${formatDate(order.orderDate)} · ${order.status === "CANCELLED" ? "Cancelled" : "Confirmed"}`));
    const bal = el("div", "history-balance");
    bal.append(el("strong", order.outstandingBalance > 0 ? "owed" : "settled", formatMoney(order.outstandingBalance, order.currency)),
        el("span", "", order.outstandingBalance > 0 ? "outstanding" : "paid in full"));
    head.append(who, bal);
    card.append(head, el("p", "history-totals",
        `Total ${formatMoney(order.total, order.currency)} · Paid ${formatMoney(order.totalPaid, order.currency)}`));
    const visiblePayments = state.historyFilter === "today-payments"
        ? order.payments.filter((payment) => localDateKey(payment.paidAt) === state.todaySummary?.businessDate)
        : order.payments;
    if (visiblePayments.length) {
        const list = el("ul", "payment-entries");
        visiblePayments.forEach((payment) => {
            const label = [METHOD_LABELS[payment.method], payment.note].filter(Boolean).join(" · ");
            list.appendChild(el("li", "", `${formatDate(payment.paidAt)} — ${formatMoney(payment.amount, order.currency)}${label ? ` (${label})` : ""}`));
        });
        card.appendChild(list);
    } else {
        card.appendChild(el("p", "empty-state", "No payments recorded yet."));
    }
    const actions = el("div", "history-actions");
    const receipt = el("a", "button-link", "Receipt");
    receipt.href = `/receipt.html?id=${encodeURIComponent(order.orderId)}`;
    receipt.target = "_blank";
    receipt.rel = "noopener";
    actions.appendChild(receipt);
    if (order.status !== "CANCELLED" && order.outstandingBalance > 0) {
        const pay = el("button", "secondary", "Record payment");
        pay.type = "button";
        pay.addEventListener("click", () => {
            renderPaymentOrder({ id: order.orderId, customerName: order.customerName, total: order.total,
                currency: order.currency }, order.totalPaid, order.outstandingBalance);
            $("payment-section").scrollIntoView({ behavior: "smooth" });
        });
        actions.appendChild(pay);
        const reminder = el("button", "secondary", "Draft payment reminder");
        reminder.type = "button";
        actions.appendChild(reminder);
        const reminderPanel = createReminderPanel(order);
        reminderPanel.hidden = true;
        reminder.addEventListener("click", () => {
            reminderPanel.hidden = !reminderPanel.hidden;
            if (!reminderPanel.hidden) reminderPanel.scrollIntoView({ behavior: "smooth", block: "nearest" });
        });
        card.appendChild(reminderPanel);
    }
    card.appendChild(actions);
    return card;
}

const REMINDER_LANGUAGES = [
    ["HINDI", "Hindi"], ["HINGLISH", "Hinglish"], ["ENGLISH", "English"],
    ["MARATHI", "Marathi"], ["BENGALI", "Bengali"], ["TAMIL", "Tamil"]
];

const reminderSettingsReady = request("/api/settings")
    .then((settings) => {
        state.reminderDefaultLanguage = settings.reminderDefaultLanguage || "HINGLISH";
    })
    .catch((error) => {
        showMessage(error.message || "Could not load your default reminder language.", "error");
    });

function preferredReminderCode(value) {
    const normalized = (value || "").trim().toLowerCase();
    const aliases = {
        hi: "HINDI", hindi: "HINDI", "हिन्दी": "HINDI", "हिंदी": "HINDI",
        hinglish: "HINGLISH", en: "ENGLISH", english: "ENGLISH",
        mr: "MARATHI", marathi: "MARATHI", "मराठी": "MARATHI",
        bn: "BENGALI", bengali: "BENGALI", "বাংলা": "BENGALI",
        ta: "TAMIL", tamil: "TAMIL", "தமிழ்": "TAMIL"
    };
    return aliases[normalized] || null;
}

function createReminderPanel(order) {
    const panel = el("div", "reminder-panel");
    const preferred = preferredReminderCode(order.preferredReminderLanguage);
    const label = el("label", "", preferred
        ? `Using customer's preferred language: ${order.preferredReminderLanguage}`
        : "Choose reminder language");
    const select = document.createElement("select");
    select.setAttribute("aria-label", "Reminder language");
    select.appendChild(new Option("Choose a language", ""));
    REMINDER_LANGUAGES.forEach(([code, name]) => select.appendChild(new Option(name, code)));
    if (preferred) {
        select.value = preferred;
        select.disabled = true;
    } else {
        select.value = state.reminderDefaultLanguage;
    }
    label.htmlFor = `reminder-language-${order.orderId}`;
    select.id = label.htmlFor;
    const generate = el("button", "secondary", "Create draft");
    generate.type = "button";
    const message = document.createElement("textarea");
    message.rows = 5;
    message.maxLength = 2000;
    message.setAttribute("aria-label", "Editable payment reminder message");
    message.placeholder = "Your reminder draft will appear here.";
    message.hidden = true;
    const actions = el("div", "history-actions");
    const copy = el("button", "secondary", "Copy message");
    copy.type = "button";
    copy.hidden = true;
    const whatsapp = el("button", "secondary", "Open WhatsApp");
    whatsapp.type = "button";
    whatsapp.hidden = true;
    const sms = el("button", "secondary", "Open SMS");
    sms.type = "button";
    sms.hidden = true;
    actions.append(copy, whatsapp, sms);
    const status = el("p", "quiet", "");
    status.setAttribute("aria-live", "polite");
    panel.append(label, select, generate, message, actions, status);
    let reminderLanguage = preferred || "";

    generate.addEventListener("click", async () => {
        if (!preferred && !select.value) {
            status.textContent = "Choose a language to create the reminder.";
            return;
        }
        generate.disabled = true;
        status.textContent = "Preparing reminder…";
        try {
            const response = await request(`/api/orders/${encodeURIComponent(order.orderId)}/payment-reminder`, {
                method: "POST",
                body: JSON.stringify({ language: preferred || select.value })
            });
            message.value = response.message;
            reminderLanguage = response.language;
            message.hidden = false;
            copy.hidden = false;
            whatsapp.hidden = false;
            sms.hidden = false;
            status.textContent = "Edit the draft if needed. Nothing is sent until you choose a recipient and send it yourself.";
            await loadOutstanding();
        } catch (error) {
            status.textContent = error.message;
        } finally {
            generate.disabled = false;
        }
    });
    copy.addEventListener("click", async () => {
        try {
            await navigator.clipboard.writeText(message.value);
            status.textContent = "Reminder copied.";
        } catch {
            message.focus();
            message.select();
            status.textContent = "Clipboard access is unavailable. The message is selected; copy it manually.";
        }
    });
    async function openReminder(channel) {
        const openedWindow = window.open("about:blank", "_blank");
        if (!openedWindow) {
            status.textContent = "Allow pop-ups to open the reminder app. No reminder was logged as opened.";
            return;
        }
        try {
            await request(`/api/orders/${encodeURIComponent(order.orderId)}/reminder-opened`, {
                method: "POST",
                body: JSON.stringify({ language: reminderLanguage, channel })
            });
            await loadOutstanding();
            const phone = String(order.customerPhone || "").replace(/\D/g, "");
            const text = encodeURIComponent(message.value);
            openedWindow.location.href = channel === "WHATSAPP"
                ? `https://wa.me/${phone ? `${phone}/` : ""}?text=${text}`
                : `sms:${String(order.customerPhone || "").replace(/[^\d+]/g, "")}?body=${text}`;
            status.textContent = `Opened ${channel === "WHATSAPP" ? "WhatsApp" : "SMS"} with the draft. Review it and press send yourself.`;
        } catch (error) {
            openedWindow.close();
            status.textContent = error.message || "Could not log or open the reminder. Please try again.";
        }
    }
    whatsapp.addEventListener("click", () => {
        void openReminder("WHATSAPP");
    });
    sms.addEventListener("click", () => {
        void openReminder("SMS");
    });
    return panel;
}

async function loadHistory() {
    $("history-loading").textContent = "Loading history…";
    $("history-loading").hidden = false;
    $("history-list").hidden = true;
    try {
        await reminderSettingsReady;
        const orders = await request("/api/orders");
        const date = state.todaySummary?.businessDate;
        const filtered = orders.filter((order) => {
            if (state.historyFilter === "today-orders") {
                return order.status === "CONFIRMED" && order.orderDate === date;
            }
            if (state.historyFilter === "today-payments") {
                return order.status === "CONFIRMED"
                    && order.payments.some((payment) => localDateKey(payment.paidAt) === date);
            }
            if (state.historyFilter === "outstanding") {
                return order.status === "CONFIRMED" && Number(order.outstandingBalance) > 0;
            }
            return true;
        });
        const list = $("history-list");
        list.replaceChildren(...filtered.map(renderHistoryOrder));
        $("history-filter-label").textContent = ({
            all: "All orders",
            "today-orders": `Confirmed orders placed on ${formatDate(date)}`,
            "today-payments": `Payments recorded on ${formatDate(date)}`,
            outstanding: "Confirmed orders with a balance due"
        })[state.historyFilter];
        $("clear-history-filter").hidden = state.historyFilter === "all";
        if (!filtered.length) {
            $("history-loading").textContent = state.historyFilter === "all"
                ? "No orders yet. Confirmed orders will appear here."
                : "No matching orders for this list.";
            return;
        }
        $("history-loading").hidden = true;
        list.hidden = false;
    } catch (error) {
        $("history-loading").textContent = "Could not load order history.";
        showMessage(error.message, "error");
    }
}

function localDateKey(value) {
    if (!value) return "";
    return new Intl.DateTimeFormat("en-CA", {
        timeZone: "Asia/Kolkata", year: "numeric", month: "2-digit", day: "2-digit"
    }).format(new Date(value));
}

async function loadTodaySummary() {
    $("today-summary-loading").hidden = false;
    $("today-summary-error").hidden = true;
    $("today-summary-cards").hidden = true;
    try {
        const summary = await request("/api/orders/today-summary");
        state.todaySummary = summary;
        $("today-date").textContent = `Business date: ${formatDate(summary.businessDate)} (Asia/Kolkata)`;
        $("today-order-count").textContent = String(summary.confirmedOrderCount);
        $("today-order-total").textContent = `Total value ${formatMoney(summary.confirmedOrderTotal)}`;
        $("today-payment-total").textContent = formatMoney(summary.paymentsRecordedToday);
        $("today-outstanding-total").textContent = formatMoney(summary.currentOutstandingDues);
        $("today-summary-loading").hidden = true;
        $("today-summary-cards").hidden = false;
    } catch (error) {
        $("today-summary-loading").hidden = true;
        $("today-summary-error").textContent = error.message || "Could not load today's summary.";
        $("today-summary-error").hidden = false;
    }
}

let pendingAudio = null;
let recordingFailed = false;
let activeRecorder = null;
let activeStream = null;
let recordingTimer = null;

function setAudioStatus(message, isError = false) {
    $("audio-status").textContent = message;
    $("audio-status").classList.toggle("error-text", isError);
}

function activeTranscript() {
    return $(state.captureMode === "payment" ? "payment-transcript" : "transcript");
}

function setCaptureMode(mode) {
    state.captureMode = mode;
    const isPayment = mode === "payment";
    $("order-text-capture").hidden = isPayment;
    $("payment-text-capture").hidden = !isPayment;
    $("create-draft").hidden = isPayment;
    $("update-draft").hidden = isPayment || !state.draft;
    $("extract-payment").hidden = !isPayment;
    $("capture-heading").textContent = isPayment ? "Capture a payment" : "Capture an order";
    $("original-note").hidden = isPayment || $("original-note-text").textContent === "";
    $("capture-order-mode").setAttribute("aria-pressed", String(!isPayment));
    $("capture-payment-mode").setAttribute("aria-pressed", String(isPayment));
}

function fileExtensionFor(type) {
    const subtype = (type || "audio/webm").split("/")[1]?.split(";")[0] || "webm";
    return subtype === "mpeg" ? "mp3" : subtype === "mp4" ? "m4a" : subtype;
}

function releaseAudio() {
    pendingAudio = null;
    $("audio-upload").value = "";
    $("transcribe-audio").disabled = true;
    if (activeStream) activeStream.getTracks().forEach((track) => track.stop());
    activeStream = null;
    activeRecorder = null;
    clearTimeout(recordingTimer);
    $("start-recording").disabled = false;
    $("stop-recording").disabled = true;
}

async function startVoiceRecording() {
    if (!navigator.mediaDevices?.getUserMedia || !window.MediaRecorder) {
        setAudioStatus("Voice recording is not supported in this browser. Choose an audio file or type the order.", true);
        return;
    }
    try {
        recordingFailed = false;
        activeStream = await navigator.mediaDevices.getUserMedia({ audio: true });
        const candidates = ["audio/webm;codecs=opus", "audio/mp4", "audio/ogg;codecs=opus"];
        const recorderType = typeof MediaRecorder.isTypeSupported === "function"
            ? candidates.find((type) => MediaRecorder.isTypeSupported(type))
            : undefined;
        try {
            activeRecorder = recorderType
                ? new MediaRecorder(activeStream, { mimeType: recorderType })
                : new MediaRecorder(activeStream);
        } catch (typeError) {
            activeRecorder = new MediaRecorder(activeStream);
        }
        const chunks = [];
        activeRecorder.addEventListener("dataavailable", (event) => {
            if (event.data.size) chunks.push(event.data);
        });
        activeRecorder.addEventListener("error", () => {
            recordingFailed = true;
            releaseAudio();
            setAudioStatus("The browser could not finish the recording. Try again or choose an audio file.", true);
        }, { once: true });
        activeRecorder.addEventListener("stop", () => {
            if (recordingFailed) return;
            pendingAudio = new Blob(chunks, { type: activeRecorder?.mimeType || recorderType || "audio/webm" });
            $("transcribe-audio").disabled = !pendingAudio.size;
            $("start-recording").disabled = false;
            $("stop-recording").disabled = true;
            if (activeStream) activeStream.getTracks().forEach((track) => track.stop());
            activeStream = null;
            clearTimeout(recordingTimer);
            setAudioStatus(pendingAudio.size
                ? `Recording ready. Transcribe it to fill the editable ${state.captureMode} text box.`
                : "The recording was empty. Try again or type the order.", !pendingAudio.size);
        }, { once: true });
        activeRecorder.start();
        $("start-recording").disabled = true;
        $("stop-recording").disabled = false;
        setAudioStatus("Recording… Stop within 60 seconds.");
        recordingTimer = setTimeout(() => {
            if (activeRecorder?.state === "recording") activeRecorder.stop();
        }, 60_000);
    } catch (error) {
        releaseAudio();
        if (["NotAllowedError", "PermissionDeniedError", "SecurityError"].includes(error?.name)) {
            setAudioStatus("Microphone permission was denied. Allow microphone access in browser settings or choose an audio file.", true);
        } else if (["NotFoundError", "DevicesNotFoundError"].includes(error?.name)) {
            setAudioStatus("No microphone was found. Connect a microphone, choose an audio file or type the note.", true);
        } else if (["NotReadableError", "TrackStartError"].includes(error?.name)) {
            setAudioStatus("The microphone could not start. It may be in use by another app; choose a file or type the note.", true);
        } else {
            setAudioStatus("Recording could not start. Check microphone access and try an audio file or typed note.", true);
        }
    }
}

async function transcribeVoiceNote() {
    if (!pendingAudio) return;
    if (!pendingAudio.size) {
        setAudioStatus("The selected recording is empty. Record again, choose another file or type the note.", true);
        releaseAudio();
        return;
    }
    const target = activeTranscript();
    if (target.value.trim()
            && !window.confirm(`Replace the current ${state.captureMode} text with the voice transcription?`)) {
        return;
    }
    $("transcribe-audio").disabled = true;
    $("start-recording").disabled = true;
    setAudioStatus("Transcribing voice note…");
    const form = new FormData();
    form.append("audio", pendingAudio, `voice-note.${fileExtensionFor(pendingAudio.type)}`);
    form.append("language", $("note-language").value || "AUTO");
    try {
        const result = await request("/api/transcription", { method: "POST", body: form });
        target.value = result.transcript;
        target.focus();
        setAudioStatus(`Transcribed (${result.detectedLanguage}). Edit the text before reviewing the ${state.captureMode}.`);
    } catch (error) {
        setAudioStatus(error.message || "Transcription failed. Type the order instead.", true);
    } finally {
        releaseAudio();
    }
}

function showPaymentCaptureError(message) {
    $("payment-capture-error").textContent = message;
    $("payment-capture-error").hidden = !message;
}

function renderPaymentOrders() {
    const review = state.paymentReview;
    const selectedId = $("payment-customer-match").value;
    const match = review?.customerMatches.find((candidate) => candidate.customerId === selectedId);
    const orderSelect = $("payment-order-match");
    const previousOrderId = orderSelect.value;
    orderSelect.replaceChildren();
    if (!match?.outstandingOrders.length) {
        orderSelect.appendChild(new Option("No outstanding orders", ""));
        $("payment-order-balance").textContent = "This customer has no confirmed order with a balance due.";
        $("confirm-payment-capture").disabled = true;
        $("payment-capture-amount").removeAttribute("max");
        return;
    }
    match.outstandingOrders.forEach((order) => {
        const option = new Option(
            `${formatDate(order.orderDate)} · ${formatMoney(order.outstandingBalance, order.currency)} due`,
            order.orderId);
        orderSelect.appendChild(option);
    });
    if (match.outstandingOrders.some((order) => order.orderId === previousOrderId)) {
        orderSelect.value = previousOrderId;
    }
    const selected = match.outstandingOrders.find((order) => order.orderId === orderSelect.value)
        || match.outstandingOrders[0];
    $("payment-capture-amount").max = String(selected.outstandingBalance);
    $("payment-order-balance").textContent =
        `Outstanding: ${formatMoney(selected.outstandingBalance, selected.currency)}.`;
    $("confirm-payment-capture").disabled = false;
}

function renderPaymentReview(review) {
    state.paymentReview = review;
    $("payment-review-section").hidden = false;
    $("payment-capture-summary").textContent =
        `Extracted ${formatMoney(review.amount)} for “${review.extractedCustomerName}”. Confirm the match, order and amount before saving.`;
    $("payment-capture-amount").value = review.amount;
    $("payment-capture-note").value = review.note || "";
    const customerSelect = $("payment-customer-match");
    customerSelect.replaceChildren();
    review.customerMatches.forEach((match) => {
        customerSelect.appendChild(new Option(
            `${match.customerName}${match.phone ? ` · ${match.phone}` : ""}`,
            match.customerId));
    });
    if (!review.customerMatches.length) {
        customerSelect.appendChild(new Option("No matching customer", ""));
        $("payment-order-match").replaceChildren(new Option("No outstanding order", ""));
        showPaymentCaptureError("No matching customer was found in this account. Check the name or use typed entry.");
        $("confirm-payment-capture").disabled = true;
        $("payment-order-balance").textContent = "";
    } else {
        showPaymentCaptureError(review.customerMatches.length > 1
            ? "More than one customer matched. Choose the correct customer."
            : "");
        renderPaymentOrders();
    }
    $("payment-review-section").scrollIntoView({ behavior: "smooth", block: "start" });
}

async function extractPaymentCapture() {
    const transcript = $("payment-transcript").value.trim();
    if (!transcript) {
        showPaymentCaptureError("Enter or transcribe a payment note first.");
        $("payment-review-section").hidden = false;
        return;
    }
    await runAction(async () => {
        const review = await request("/api/payment-capture/extract", {
            method: "POST",
            body: JSON.stringify({ transcript, language: $("note-language").value || "AUTO" })
        });
        renderPaymentReview(review);
        showMessage("Check the customer, outstanding order and amount before recording.", "success");
    });
}

async function confirmPaymentCapture() {
    const review = state.paymentReview;
    if (!review) return;
    const customer = review.customerMatches.find((match) => match.customerId === $("payment-customer-match").value);
    const order = customer?.outstandingOrders.find((candidate) => candidate.orderId === $("payment-order-match").value);
    const amount = Number($("payment-capture-amount").value);
    if (!order) {
        showPaymentCaptureError("Choose a customer with an outstanding order.");
        return;
    }
    if (!Number.isFinite(amount) || amount <= 0 || amount > Number(order.outstandingBalance)) {
        showPaymentCaptureError(`Enter an amount greater than zero and no more than ${formatMoney(order.outstandingBalance, order.currency)}.`);
        return;
    }
    showPaymentCaptureError("");
    await runAction(async () => {
        const result = await request(`/api/orders/${encodeURIComponent(order.orderId)}/payments`, {
            method: "POST",
            body: JSON.stringify({ amount, note: $("payment-capture-note").value.trim() || null })
        });
        $("payment-review-section").hidden = true;
        $("payment-transcript").value = "";
        state.paymentReview = null;
        showMessage(`Payment saved. Balance: ${formatMoney(result.outstandingBalance, order.currency)}.`);
        await Promise.all([loadOutstanding(), loadHistory(), loadTodaySummary()]);
    });
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
$("capture-order-mode").addEventListener("click", () => setCaptureMode("order"));
$("capture-payment-mode").addEventListener("click", () => setCaptureMode("payment"));
$("extract-payment").addEventListener("click", extractPaymentCapture);
$("payment-customer-match").addEventListener("change", renderPaymentOrders);
$("payment-order-match").addEventListener("change", renderPaymentOrders);
$("confirm-payment-capture").addEventListener("click", confirmPaymentCapture);
$("cancel-payment-capture").addEventListener("click", () => {
    $("payment-review-section").hidden = true;
    state.paymentReview = null;
    showPaymentCaptureError("");
});
$("record-payment").addEventListener("click", recordPayment);
$("refresh-dues").addEventListener("click", () => runAction(loadOutstanding));
$("dues-search").addEventListener("input", renderOutstanding);
$("dues-sort").addEventListener("change", renderOutstanding);
$("refresh-history").addEventListener("click", () => runAction(loadHistory));
$("refresh-today").addEventListener("click", () => runAction(async () => {
    await loadTodaySummary();
    await loadHistory();
}));
$("today-summary-cards").addEventListener("click", (event) => {
    const link = event.target.closest("[data-history-filter]");
    if (!link) return;
    event.preventDefault();
    state.historyFilter = link.dataset.historyFilter;
    loadHistory().then(() => $("history-section").scrollIntoView({ behavior: "smooth" }));
});
$("clear-history-filter").addEventListener("click", () => {
    state.historyFilter = "all";
    loadHistory();
});
$("start-recording").addEventListener("click", startVoiceRecording);
$("stop-recording").addEventListener("click", () => {
    if (activeRecorder?.state === "recording") activeRecorder.stop();
});
$("audio-upload").addEventListener("change", (event) => {
    pendingAudio = event.target.files?.[0] || null;
    $("transcribe-audio").disabled = !pendingAudio;
    if (pendingAudio) {
        setAudioStatus(`${pendingAudio.name} selected. Transcribe it to fill the editable order text box.`);
    }
});
$("transcribe-audio").addEventListener("click", transcribeVoiceNote);
$("logout").addEventListener("click", async () => {
    try {
        await request("/logout", { method: "POST" });
    } catch {
        // fall through to the login page either way
    }
    window.location.replace("/login.html");
});

function closeWelcome() {
    $("welcome-overlay").hidden = true;
}

$("skip-welcome").addEventListener("click", closeWelcome);

function renderProfileBadge(profile) {
    const badge = $("profile-avatar-badge");
    badge.replaceChildren();
    if (profile.avatarUrl) {
        const image = document.createElement("img");
        image.src = profile.avatarUrl;
        image.alt = "";
        badge.appendChild(image);
    } else {
        badge.textContent = (profile.name || "DD").trim().split(/\s+/).slice(0, 2)
            .map((part) => part[0]).join("").toUpperCase();
    }
}

async function loadNotifications() {
    try {
        const data = await request("/api/notifications");
        $("notification-count").textContent = String(data.unreadCount);
        $("notification-count").hidden = data.unreadCount <= 0;
        $("notification-summary").textContent = data.unreadCount
            ? `${data.unreadCount} unread` : "You are all caught up.";
        $("notifications-error").hidden = true;
        const list = $("notifications-list");
        list.replaceChildren();
        if (!data.notifications.length) {
            list.appendChild(el("p", "quiet", "No notifications."));
            return;
        }
        data.notifications.forEach((notification) => {
            const row = document.createElement("article");
            row.className = `notification-row${notification.readAt ? "" : " unread"}`;
            const text = document.createElement("div");
            text.append(el("strong", "", notification.title),
                el("p", "", notification.message),
                el("p", "quiet", new Date(notification.createdAt).toLocaleString()));
            row.appendChild(text);
            if (!notification.readAt) {
                const mark = el("button", "secondary", "Mark read");
                mark.type = "button";
                mark.addEventListener("click", async () => {
                    try {
                        await request(`/api/notifications/${encodeURIComponent(notification.id)}/read`,
                            { method: "POST" });
                        await loadNotifications();
                    } catch (error) {
                        showMessage(error.message || "Could not mark notification as read.", "error");
                    }
                });
                row.appendChild(mark);
            }
            list.appendChild(row);
        });
    } catch (error) {
        $("notifications-error").textContent = error.message || "Could not load notifications.";
        $("notifications-error").hidden = false;
    }
}

$("notification-bell").addEventListener("click", async () => {
    const panel = $("notifications-panel");
    panel.hidden = !panel.hidden;
    $("notification-bell").setAttribute("aria-expanded", String(!panel.hidden));
    if (!panel.hidden) await loadNotifications();
});

$("clear-notifications").addEventListener("click", async () => {
    try {
        await request("/api/notifications", { method: "DELETE" });
        await loadNotifications();
    } catch (error) {
        $("notifications-error").textContent = error.message || "Could not clear notifications.";
        $("notifications-error").hidden = false;
    }
});

loadCsrf().then((session) => {
    if (!session.showWelcome) return;
    $("welcome-name").textContent = session.ownerName || "shopkeeper";
    $("welcome-overlay").hidden = false;
    $("skip-welcome").focus();
    window.setTimeout(closeWelcome, 2500);
}).catch((error) => {
    showMessage(error.message || "Could not check your sign-in session.", "error");
});

request("/api/profile").then(renderProfileBadge).catch((error) => {
    showMessage(error.message || "Could not load profile.", "error");
});

const notificationStream = new EventSource("/api/notifications/stream");
notificationStream.addEventListener("notification", loadNotifications);
notificationStream.onerror = () => {
    $("notifications-error").textContent = "Live updates disconnected. Notifications will refresh when opened.";
    $("notifications-error").hidden = false;
};

loadOutstanding();
loadHistory();
loadTodaySummary();
