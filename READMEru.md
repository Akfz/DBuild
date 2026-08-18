[**English**] | [Русский](README_ru.md)

# DBuild

**DBuild** is an annotation processor (code generator) that simplifies cross-platform Minecraft mod development by generating entry points at compile-time.

Unlike Architectury API, it does not require an additional runtime library mod, ensuring zero overhead and better performance.

**Limitations:** The `common` module lacks direct compile-time access to platform-specific mod loader APIs (unless code is written directly inside platform modules like `forge`, `fabric`, etc.), requiring reflection or code generation.

## Recommended Structure:
* **`common`** — the main module containing core business logic (up to 100% of the codebase).
* **`fabric` / `forge` / `neoforge`** *(1.20.4+ only)* — platform integration modules containing only entry points (`ModInitializer`, `@Mod`) and metadata (`fabric.mod.json`, `mods.toml`).

*P.S. Everything can be generated directly inside `common`; during compilation, unneeded metadata files are automatically stripped (e.g., `fabric.mod.json` will not end up in a Forge build).*

**Project Template:** https://github.com/Akfz/DBuild-TEMPLATE