const http = require("node:http");
const fs = require("node:fs");
const path = require("node:path");
const root = __dirname;
const port = Number(process.env.PORT || 5173);
const mimeTypes = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".webmanifest": "application/manifest+json; charset=utf-8",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".webp": "image/webp",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon"
};
http.createServer((request, response) => {
  const requestUrl = new URL(
    request.url || "/",
    `http://${request.headers.host || "localhost"}`
  );
  let requestedPath = decodeURIComponent(requestUrl.pathname);
  if (requestedPath === "/") {
    requestedPath = "/index.html";
  }
  const filePath = path.resolve(
    root,
    requestedPath.replace(/^\/+/, "")
  );
  if (
    filePath !== root &&
    !filePath.startsWith(root + path.sep)
  ) {
    response.writeHead(403, {
      "Content-Type": "text/plain; charset=utf-8"
    });
    response.end("Forbidden");
    return;
  }
  fs.readFile(filePath, (error, content) => {
    if (error) {
      response.writeHead(404, {
        "Content-Type": "text/plain; charset=utf-8"
      });
      response.end("Not found");
      return;
    }
    const extension = path.extname(filePath).toLowerCase();
    response.writeHead(200, {
      "Content-Type":
        mimeTypes[extension] || "application/octet-stream",
      "Cache-Control": "no-store"
    });
    response.end(content);
  });
}).listen(port, "0.0.0.0", () => {
  console.log(`Focus Mode+ running on http://0.0.0.0:${port}\`);
});
