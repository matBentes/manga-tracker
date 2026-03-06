#!/usr/bin/env node
// Merges additional MCP server entries into ~/.claude/settings.json.
// Playwright MCP is handled by the marketplace plugin and Chrome sandbox
// works via SYS_ADMIN capability in devcontainer.json.
const fs = require("fs");
const path = require("path");

const SETTINGS_PATH = "/home/node/.claude/settings.json";

const mcpServers = {
  // Add MCP servers here as needed:
  // excalidraw: { command: "npx", args: ["..."] },
};

if (Object.keys(mcpServers).length > 0) {
  const existing = fs.existsSync(SETTINGS_PATH)
    ? JSON.parse(fs.readFileSync(SETTINGS_PATH, "utf8"))
    : {};

  existing.mcpServers = { ...existing.mcpServers, ...mcpServers };

  fs.mkdirSync(path.dirname(SETTINGS_PATH), { recursive: true });
  fs.writeFileSync(SETTINGS_PATH, JSON.stringify(existing, null, 2));
  console.log("MCP settings merged into", SETTINGS_PATH);
} else {
  console.log("No additional MCP servers to configure.");
}
