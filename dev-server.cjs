const http = require("node:http");
const fs = require("node:fs");
const path = require("node:path");

const ROOT = __dirname;
const PORT = Number(process.env.PORT || 5173);

const MIME_TYPES = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".webmanifest": "application/manifest+json; charset=utf-8",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".webp": "image/webp",
  ".svg": "image/svg+xml",
  ".ico": "image/x-icon",
  ".txt": "text/plain; charset=utf-8"
};

function resolveRequestPath(requestPath) {
  const decodedPath = decodeURIComponent(requestPath);
  const relativePath =
    decodedPath === "/" ? "index.html" : decodedPath.replace(/^\/+/, "");

  const resolvedPath = path.resolve(ROOT, relativePath);

  if (
    resolvedPath !== ROOT &&
    !resolvedPath.startsWith(`${ROOT}${path.sep}`)
  ) {
    return null;
  }

  return resolvedPath;
}

const server = http.createServer((request, response) => {
  const requestUrl = new URL(
    request.url,
    `http://${request.headers.host || "localhost"}`
  );

  let filePath = resolveRequestPath(requestUrl.pathname);

  if (!filePath) {
    response.writeHead(403, {
      "Content-Type": "text/plain; charset=utf-8"
    });
    response.end("Forbidden");
    return;
  }

  fs.stat(filePath, (statError, stats) => {
    if (!statError && stats.isDirectory()) {
      filePath = path.join(filePath, "index.html");
    }

    fs.readFile(filePath, (readError, data) => {
      if (readError) {
        response.writeHead(404, {
          "Content-Type": "text/plain; charset=utf-8"
        });
        response.end("Not found");
        return;
      }

      const extension = path.extname(filePath).toLowerCase();

      response.writeHead(200, {
        "Content-Type":
          MIME_TYPES[extension] || "application/octet-stream",
        "Cache-Control": "no-store"
      });

      response.end(data);
    });
  });
});

server.listen(PORT, "0.0.0.0", () => {
  console.log(`Focus Mode+ dev server: http://0.0.0.0:${PORT}\`);
});
