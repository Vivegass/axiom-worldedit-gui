# Публикация на Modrinth и CurseForge

Готовые материалы в этой папке:

| Файл | Зачем |
|---|---|
| `icon-512.png` (`icon.svg` — исходник) | иконка проекта на обеих площадках |
| `description.md` | описание страницы мода (Markdown, подходит обеим площадкам) |
| `media/snow-brush.webp`, `media/brushes-tab.webp`, `media/forest-brush.webp`, `media/selection-regen.webp` | анимации в описании (подключены ссылками на GitHub, поэтому папка должна быть в репозитории на ветке `main`) |
| `media/gallery-*.webp`, `media/gallery-brushes-tab.png` | картинки для галереи |
| `../CHANGELOG.md` | changelog версии (раздел `## <версия>+mc<версия MC>`) |
| `../build/axiom-worldedit-tools-<версия>+mc26.3.jar` | файл версии (после `./build.sh`) |

Видео-исходники лежат в `C:\Users\vvvkt\Videos\Medal\Edits\` (`MedalTVMinecraft20261123011258710-trim-*.mp4`).

## Modrinth

- **Name:** Axiom: WorldEdit Tools · **Slug:** `axiom-worldedit-tools` (на 23.11.2026 свободен)
- **Summary:** `WorldEdit inside the Axiom editor: brushes as Axiom tools, forms for every command, shared selections and one Ctrl+Z history.`
- **Categories:** Utility, Management (как у самого Axiom); можно добавить Decoration / Worldgen как дополнительные
- **Client side:** Required · **Server side:** Unsupported
- **License:** GPL-3.0-only · **Source:** https://github.com/Vivegass/axiom-worldedit-gui · **Issues:** https://github.com/Vivegass/axiom-worldedit-gui/issues
- **Версия:** номер `0.3.0+mc26.3`, канал Beta (или Release), загрузчик Fabric, игра 26.3
- **Зависимости (все Required, загрузчик скачает их сам):** Axiom (`axiom`, id `N6n5dqoA`), WorldEdit (`worldedit`, id `1u6JkXh5`), Fabric API (`fabric-api`, id `P7dR8mSH`)
- **Галерея** (подписи на английском):
  - `media/gallery-brushes-tab.png` (featured) — *WorldEdit brushes as Axiom tools*
  - `media/gallery-snow.webp` — *Painting snow with a WorldEdit brush*
  - `media/gallery-forest.webp` — *Painting with a WorldEdit brush*
  - `media/gallery-selection-box.webp` — *Axiom's box selection used by WorldEdit's //regen*
  - `media/gallery-selection-lasso.webp` — *A lasso selection: 10,798 blocks selected in Axiom, 10,798 changed by WorldEdit*

## CurseForge

- **Project name:** Axiom: WorldEdit Tools · **Class:** Mods · **Main category:** Map and Information / Utility & QoL
- **Summary:** то же, что на Modrinth
- **Description:** вставить `description.md` (редактор Markdown)
- **License:** GPL-3.0
- **Source URL / Issues URL:** ссылки на GitHub выше
- **Файл:** Fabric, Minecraft 26.3, Java 25; Related Projects, все Required Dependency: **AxiomTool** (`axiomtool`, это настоящий Axiom от Moulberry; проект `axiom` на CurseForge — чужой мод), WorldEdit (`worldedit`), Fabric API (`fabric-api`)
- **Логотип:** `icon-512.png`
- **Images:** CurseForge не принимает картинки через API — загрузить файлы `media/gallery-*` вручную во вкладке Images.

## Загрузка версии

1. Проекты на обеих площадках должны уже существовать. Их id вписать в `../mod.properties`
   (`modrinth_project_id`, `curseforge_project_id`).
2. `./build.sh` — собрать jar.
3. В `publish/`: `./gradlew publishMods` (пробный прогон), затем `./gradlew publishMods -Prelease -Pchannel=beta`.
   Токены берутся из `MODRINTH_TOKEN` и `CURSEFORGE_TOKEN`; `-Pplatforms=modrinth` или `-Pplatforms=curseforge` — только одна площадка.

## Следующие версии

Поднять `mod_version` в `../mod.properties`, дописать раздел в `../CHANGELOG.md`, затем шаги 2–3.

## Перед первой публикацией

- Закоммитить и запушить всё, включая `publishing/media/`, иначе картинки в описании не откроются.
- Мод назван неофициальным в описании; кода Axiom и WorldEdit в jar нет.
