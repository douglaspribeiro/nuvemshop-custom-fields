(function () {
    "use strict";

    const script = document.currentScript;
    const clientId = script && script.dataset.clientId ? script.dataset.clientId : "";
    const mode = script && script.dataset.mode ? script.dataset.mode : "page";
    const debug = script && script.dataset.debug === "true";
    const parentWindow = window.parent;
    const embedded = parentWindow && parentWindow !== window;
    const handlers = [];
    let connected = false;

    function log(message, payload) {
        if (debug) {
            console.debug("[nexo]", message, payload || "");
        }
    }

    function dispatch(type, payload) {
        if (!embedded) {
            return;
        }
        const message = payload === undefined ? { type } : { type, payload };
        log("dispatch", message);
        parentWindow.postMessage(message, "*");
    }

    function subscribe(type, callback) {
        const handler = function (message) {
            if (message && message.type === type) {
                log("receive", message);
                callback(message.payload || {});
            }
        };
        handlers.push(handler);
        return function () {
            const index = handlers.indexOf(handler);
            if (index > -1) {
                handlers.splice(index, 1);
            }
        };
    }

    function connect(timeout) {
        if (!embedded) {
            return Promise.reject(new Error("Not embedded"));
        }
        return new Promise(function (resolve, reject) {
            const timer = window.setTimeout(function () {
                unsubscribe();
                reject(new Error("Nexo connection timeout"));
            }, timeout || 3000);
            const unsubscribe = subscribe("app/connected", function () {
                window.clearTimeout(timer);
                unsubscribe();
                connected = true;
                resolve();
            });
            dispatch("app/connected");
        });
    }

    function asyncAction(type, payload, timeout) {
        return new Promise(function (resolve, reject) {
            const timer = window.setTimeout(function () {
                unsubscribe();
                reject(new Error(type + " timeout"));
            }, timeout || 5000);
            const unsubscribe = subscribe(type, function (response) {
                window.clearTimeout(timer);
                unsubscribe();
                resolve(response || {});
            });
            dispatch(type, payload);
        });
    }

    function currentPath() {
        return window.location.pathname + window.location.search + window.location.hash;
    }

    function syncCurrentPath() {
        dispatch("app/navigate/sync", { pathname: currentPath() });
    }

    function bindRouteSync() {
        subscribe("app/navigate/sync", function (payload) {
            const path = payload.path || payload.pathname;
            if (path && path !== currentPath()) {
                window.location.assign(path);
            }
        });
    }

    function ready() {
        dispatch("app/ready");
        syncCurrentPath();
        bindRouteSync();
    }

    function fallbackToInstall() {
        window.location.replace("/install");
    }

    function bootstrapSession() {
        asyncAction("app/auth/sessionToken", undefined, 7000)
            .then(function (response) {
                if (!response.token) {
                    throw new Error("Missing Nexo session token");
                }
                return fetch("/admin/nexo/session", {
                    method: "POST",
                    credentials: "same-origin",
                    headers: {
                        "Content-Type": "application/json"
                    },
                    body: JSON.stringify({ token: response.token })
                });
            })
            .then(function (response) {
                if (!response.ok) {
                    throw new Error("Nexo session rejected");
                }
                return response.json();
            })
            .then(function (body) {
                window.location.replace(body.redirect || "/admin");
            })
            .catch(function (error) {
                log("bootstrap failed", error.message);
                fallbackToInstall();
            });
    }

    window.addEventListener("message", function (event) {
        handlers.slice().forEach(function (handler) {
            handler(event.data);
        });
    });

    window.addEventListener("popstate", function () {
        if (connected) {
            syncCurrentPath();
        }
    });

    if (!clientId) {
        log("missing client id");
    }

    connect()
        .then(function () {
            ready();
            if (mode === "bootstrap") {
                bootstrapSession();
            }
        })
        .catch(function (error) {
            log("connection failed", error.message);
            if (mode === "bootstrap") {
                fallbackToInstall();
            }
        });
})();
