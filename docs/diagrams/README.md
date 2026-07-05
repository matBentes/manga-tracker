# Architecture Diagrams

This directory stores versionable diagram sources for MangaTracker.

## Rules

- Treat `*.source.md` and `*.mmd` files as the canonical diagram sources.
- Treat files under `rendered/` as generated output that can be recreated.
- Update sources before updating rendered images.
- Do not use Figma, draw.io, Excalidraw, PNG, or SVG files as the only source of truth.
- Do not invent AWS resources or runtime components. If the repository does not document them, mark them as `TBD` or ask for confirmation.
- Keep local/dev architecture separate from AWS/prod architecture.

## Files

- `application.source.md` describes the local/application architecture in structured text.
- `application.mmd` is the Mermaid source for the application architecture.
- `application.excalidraw` is a derived Excalidraw render of the application architecture.
- `aws-deployment.source.md` describes the documented AWS production deployment shape.
- `aws-deployment.mmd` is the Mermaid source for the AWS deployment architecture.
- `aws-deployment.excalidraw` is a derived Excalidraw render of the AWS deployment architecture.

## Rendering

Mermaid sources can be previewed directly in GitHub or rendered with a Mermaid-compatible tool.

The `.excalidraw` files are derived renders, not sources of truth — regenerate them from the
`*.source.md` files if those change. Open them at <https://excalidraw.com> (Open) or with the
Excalidraw VS Code extension. Export PNG/SVG from there and save into `rendered/` if a static
image is needed.
