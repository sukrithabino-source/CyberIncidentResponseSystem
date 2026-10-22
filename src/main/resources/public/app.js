
const incidentForm = document.getElementById("incidentForm");
const actionForm = document.getElementById("actionForm");
const incidentRows = document.getElementById("incidentRows");
const actionRows = document.getElementById("actionRows");
const notice = document.getElementById("notice");

async function request(url, options = {}) {
    const response = await fetch(url, {
        ...options,
        headers: {
            "Content-Type": "application/json",
            ...(options.headers || {})
        }
    });

    const data = await response.json();

    if (!response.ok) {
        throw new Error(data.error || "Something went wrong.");
    }

    return data;
}

function showMessage(message) {
    notice.textContent = message;
}

function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, char => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;"
    })[char]);
}

function renderIncidents(incidents) {
    incidentRows.innerHTML = "";

    if (incidents.length === 0) {
        incidentRows.innerHTML =
            '<tr><td colspan="4" class="empty-row">' +
            "No incidents reported yet." +
            "</td></tr>";
        return;
    }

    incidents.forEach(incident => {
        const row = document.createElement("tr");

        row.innerHTML = `
            <td>${incident.id}</td>
            <td>${escapeHtml(incident.title)}</td>
            <td>
                <span class="severity severity-${escapeHtml(incident.severity)}">
                    ${escapeHtml(incident.severity)}
                </span>
            </td>
            <td>${escapeHtml(incident.description || "—")}</td>
        `;

        incidentRows.appendChild(row);
    });
}

function renderActions(actions) {
    actionRows.innerHTML = "";

    if (actions.length === 0) {
        actionRows.innerHTML =
            '<tr><td colspan="2" class="empty-row">' +
            "No response actions recorded yet." +
            "</td></tr>";
        return;
    }

    actions.forEach(action => {
        const row = document.createElement("tr");

        row.innerHTML = `
            <td>${action.id}</td>
            <td>${escapeHtml(action.text)}</td>
        `;

        actionRows.appendChild(row);
    });
}

async function refreshData() {
    try {
        const [incidents, actions] = await Promise.all([
            request("/api/incidents"),
            request("/api/actions")
        ]);

        renderIncidents(incidents);
        renderActions(actions);
    } catch (error) {
        showMessage(
            "Cannot connect to the server. Start the Java program first."
        );
    }
}

incidentForm.addEventListener("submit", async event => {
    event.preventDefault();

    const incident = {
        title: document.getElementById("title").value.trim(),
        severity: document.getElementById("severity").value,
        description: document.getElementById("description").value.trim()
    };

    try {
        await request("/api/incidents", {
            method: "POST",
            body: JSON.stringify(incident)
        });

        incidentForm.reset();
        document.getElementById("severity").value = "MEDIUM";

        await refreshData();
        showMessage("Incident added successfully.");
    } catch (error) {
        showMessage(error.message);
    }
});

document.getElementById("process").addEventListener("click", async () => {
    try {
        const result = await request("/api/process", {
            method: "POST"
        });

        await refreshData();
        showMessage(result.message);
    } catch (error) {
        showMessage(error.message);
    }
});

actionForm.addEventListener("submit", async event => {
    event.preventDefault();

    const text = document.getElementById("actionText").value.trim();

    if (!text) {
        showMessage("Please enter a response action.");
        return;
    }

    try {
        // This records the action through the backend endpoint.
        // The endpoint is added in the next correction step.
        await request("/api/actions", {
            method: "POST",
            body: JSON.stringify({ text })
        });

        actionForm.reset();
        await refreshData();
        showMessage("Response action saved.");
    } catch (error) {
        showMessage(error.message);
    }
});

document.getElementById("undo").addEventListener("click", async () => {
    try {
        const result = await request("/api/undo", {
            method: "POST"
        });

        await refreshData();
        showMessage(result.message);
    } catch (error) {
        showMessage(error.message);
    }
});

document.getElementById("reset").addEventListener("click", async () => {
    if (!confirm("Are you sure you want to reset the entire system?")) {
        return;
    }

    try {
        const result = await request("/api/reset", {
            method: "POST"
        });

        await refreshData();
        showMessage(result.message);
    } catch (error) {
        showMessage(error.message);
    }
});

document.getElementById("refresh").addEventListener("click", refreshData);

refreshData();
