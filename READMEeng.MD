DBuild is a Gradle script that simplifies working with different mod loaders by assembling them at compile-time for each target mod loader.  
Unlike Architectury API, you do not need an additional runtime mod, and execution is faster.  
However, there are drawbacks: the `common` module has no direct access to mod loader APIs (unless you write code directly inside platform modules like `forge`, `fabric`, etc.), requiring access via reflection.

### Structure:
* **`common`** — the main module containing the core business logic of the modification (up to 100% of the codebase).
* **`fabric` / `forge` / `neoforge`** *(1.20.4+ only)* — platform integration modules containing only entry points (`ModInitializer`, `@Mod`) and package metadata (`fabric.mod.json`, `mods.toml`).

*P.S. All of this can be generated inside `common`; during compilation, unneeded files are automatically stripped (e.g., `fabric.mod.json` will not end up in the Forge build, etc.).*

Compiled JAR files are generated in the `build/libs/` directory (including dev/library artifacts).