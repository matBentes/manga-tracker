#!/usr/bin/env node

/**
 * Lints SCSS files for hardcoded hex color values.
 *
 * Any hex color literal (#rgb, #rrggbb, #rrggbbaa) outside of _tokens.scss
 * is a violation — colors must come from design tokens.
 *
 * Usage: node lint-design-tokens.js <file1.scss> [file2.scss ...]
 * Exit code 1 if violations found, 0 otherwise.
 */

const fs = require("fs");
const path = require("path");

const HEX_COLOR_RE = /#[0-9a-fA-F]{3,8}\b/g;
const TOKENS_BASENAME = "_tokens.scss";

const files = process.argv.slice(2);

if (files.length === 0) {
  process.exit(0);
}

let violations = 0;

for (const filePath of files) {
  if (path.basename(filePath) === TOKENS_BASENAME) {
    continue;
  }

  let content;
  try {
    content = fs.readFileSync(filePath, "utf8");
  } catch {
    console.error(`Could not read file: ${filePath}`);
    continue;
  }

  const lines = content.split("\n");
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];

    // Skip single-line comments
    if (line.trimStart().startsWith("//")) {
      continue;
    }

    let match;
    HEX_COLOR_RE.lastIndex = 0;
    while ((match = HEX_COLOR_RE.exec(line)) !== null) {
      violations++;
      console.error(
        `  ${filePath}:${i + 1}  hardcoded color ${match[0]} — use a design token instead`,
      );
    }
  }
}

if (violations > 0) {
  console.error(
    `\n✗ Found ${violations} hardcoded color${violations === 1 ? "" : "s"}. Move them to _tokens.scss.`,
  );
  process.exit(1);
}
