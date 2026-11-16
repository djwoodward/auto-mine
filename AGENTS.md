# Repository Guidelines

## Project Structure & Module Organization

auto-mine is a Java 25 Maven plugin for Paper 26.2. The entry point,
`src/main/java/dev/auto_mine/AutoMinePlugin.java`, handles commands, player
preferences, and scheduled food consumption. `src/main/resources/plugin.yml`
declares the plugin, commands, and permissions; `config.yml` contains default
settings and the food allowlist. `pom.xml` defines dependencies and compilation.
Build output belongs in the ignored `target/` directory. There are currently no
test sources or separate assets.

## Build, Test, and Development Commands

- `mise trust` and `mise install`: trust the repository configuration and install
  its Java 25 and Maven 3.9 toolchain.
- `mise run build`: run `mvn clean package` to produce
  `target/auto-mine-0.1.0.jar`.
- `mise run install`: build, then move the JAR into
  `~/docker/minecraft/data/plugins/`. The directory must exist; restart Paper
  afterward to load changes.
- `mvn test`: run Maven's test phase when tests are present. With mise, use
  `mise exec -- mvn test`.

## Coding Style & Naming Conventions

Follow the existing four-space Java indentation and same-line opening braces.
Use `UpperCamelCase` classes, `lowerCamelCase` methods and fields, and lowercase
package names under `dev.auto_mine`. YAML uses two-space indentation and
hyphenated configuration keys. No formatter or linter is configured. Keep
command and permission declarations synchronized with Java behavior, and align
versions in `pom.xml` and `plugin.yml` when releasing.

## Testing Guidelines

No automated testing framework or coverage threshold is configured. Build each
change and follow the manual verification checklist in `README.md` on Paper.
Check default-enabled behavior and opt-outs, reconnect/restart persistence, inventory and offhand
restoration, excluded game modes, and cancelled `PlayerItemConsumeEvent` events.
If adding automated tests, place them under `src/test/java/dev/auto_mine/` with
names ending in `Test` and configure the needed test dependencies.

## Commit & Pull Request Guidelines

History currently contains only `Initial commit`, so no established message
convention exists. Use concise, imperative subjects describing the change.
Pull requests should explain the resulting behavior, link relevant issues, and
record build results and manual Paper checks. Document configuration, command,
or permission changes in the README. Preserve default-enabled auto-eat, saved opt-outs, and
native Paper consumption/event handling when extending the plugin.
