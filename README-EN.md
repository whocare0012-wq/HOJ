# HOJ Optimized Edition

A community fork of [HimitZH/HOJ](https://github.com/HimitZH/HOJ), the original Hcode Online Judge by Himit_ZH and other contributors. This repository is a [public GitHub fork](https://github.com/whocare0012-wq/HOJ). Original code remains attributed to its authors; contributors own their respective changes. The original [MIT license](./LICENSE) and copyright notice are retained.

[![CI](https://github.com/whocare0012-wq/HOJ/actions/workflows/upgrade-ci.yml/badge.svg)](https://github.com/whocare0012-wq/HOJ/actions/workflows/upgrade-ci.yml)

> Blockly is a visual frontend mode that generates Python code; it is not a separate judge runtime.

[简体中文](./README.md) · [Deployment and updates (Chinese)](./docs/docs/deploy/optimized.md) · [Changelog](./CHANGELOG.md) · [Report a bug](https://github.com/whocare0012-wq/HOJ/issues/new/choose) · [Discussions](https://github.com/whocare0012-wq/HOJ/discussions) · [Original HOJ](https://github.com/HimitZH/HOJ)

## Changes relative to the original project

| Area | Changes in this edition |
| --- | --- |
| Frontend | Migration to Vue 3 and Vite, with updated components and dependencies. |
| Beginner programming | Blockly editor and a separate window to build blocks, view and copy generated Python, and submit using Python. |
| Learning | Configurable difficulty levels, OJ points, learning resources, daily check-in, and daily fortune. |
| Problem assistance | An AI problem assistant with permission and usage limits; keys are configured privately by each deployer. |
| Import and data safety | AtCoder sample-based local import and additional safeguards for removing contest problems. |
| Maintenance | Local validation scripts, database migrations, and a service-specific deployment workflow. |

Existing judging languages, contests, groups, discussions, and remote judging come from upstream HOJ. Validate each feature in the target environment before relying on it.

## Layout

- `hoj-vue/`: Vue frontend and Blockly editor.
- `hoj-springboot/`: Java backend, judge server, and shared API.
- `sandbox/`: judge sandbox source.
- `sqlAndsetting/`: schema, generic seed data, and migrations.
- `scripts/`: development and verification tools.
- `docs/`: [project documentation](./docs/README.md) and [historical upgrade notes](./docs/upgrade-notes/README.md).

## Quick source check

```bash
git clone https://github.com/whocare0012-wq/HOJ.git
cd HOJ/hoj-vue
npm ci
npm audit --audit-level=high
npm run build
cd ../hoj-springboot
mvn -B -ntp -pl DataBackup,JudgeServer -am test -DskipTests=false
```

These commands check and build the source; they do not start the full judge. A complete installation also needs isolated MySQL, Redis, Nacos, judge services, and private configuration. Never use production problem or account data in public examples.

## Build and deployment

The frontend requires Node.js 20.9+; the backend uses JDK 8 and Maven. See the [deployment and update guide](./docs/docs/deploy/optimized.md) for build, installation, migration, verification, and rollback. This repository does not provide a complete production Compose file ready to replace an existing deployment. Pulling the original project's images will not deploy this fork's code.

## Contributing and support

Report reproducible problems through [Issues](https://github.com/whocare0012-wq/HOJ/issues/new/choose) and ask usage questions in [Discussions](https://github.com/whocare0012-wq/HOJ/discussions). See [CONTRIBUTING.md](./CONTRIBUTING.md), [SUPPORT.md](./SUPPORT.md), and [SECURITY.md](./SECURITY.md) for contributions, help, and private vulnerability reports.

## Public repository boundary

Publish source code, generic configuration templates, schema and migration scripts, and public documentation. Do not publish problem packages or testcases, user accounts or passwords, database backups, uploads, private `.env` files, certificates, or browser test artifacts. The public SQL seed contains no administrator account; create the initial administrator privately during installation. Review staged files before every commit.

## License and credit

The project remains under the [MIT License](./LICENSE). Credit belongs to the [original HOJ authors and contributors](https://github.com/HimitZH/HOJ/graphs/contributors); retain their copyright and license notices when redistributing.
