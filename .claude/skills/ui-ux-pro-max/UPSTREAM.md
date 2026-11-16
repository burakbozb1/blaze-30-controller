# Upstream

- Source: https://github.com/nextlevelbuilder/ui-ux-pro-max-skill (MIT, see LICENSE)
- Skill: `ui-ux-pro-max` v2.15.0, copied from commit `0e8d408204fdc66b8189960dd28cc20d71db188e` on 2026-11-15
- Copied: `SKILL.md`, `data/`, `references/`, `scripts/` (search engine). Not copied: `scripts/tests/` (upstream's own tests), the other
  skills in that repo (banner-design, brand, design, design-system, slides, ui-styling), the npm CLI, gallery and screenshots.
- Reviewed before installing (2026-11-15): runtime scripts use only the Python standard library, make no network calls, run no
  subprocesses and write files only with `--persist` (into `design-system/`); `SKILL.md` and the data contain no instructions
  beyond design search. Files are unmodified.
- Running it in this project: `SKILL.md` calls the script through `${CLAUDE_PLUGIN_ROOT}`, which is not set for a project skill. Use the
  project path instead:
  `python .claude/skills/ui-ux-pro-max/scripts/search.py "<query>" --stack jetpack-compose` (this app is Kotlin + Jetpack Compose).
- Do not pass `--persist`/`--force` without a clear reason; prefer printing results (default) and treat results as recommendations.
- To update: re-clone upstream, review the diff, replace the folder, update the commit above.
