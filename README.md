# Lauua Cheats (experimental)

Client-only Fabric mod project for **Minecraft Java 26.2**, mod ID `lauuacheats`, package `com.lauua.lauuacheats`, version `1.2.0`. Based on the official [Fabric example mod's 26.2 branch](https://github.com/FabricMC/fabric-example-mod/tree/26.2). Java **25**, Fabric Loader **0.20.1**, Fabric API **0.161.0+26.2**, Gradle wrapper **9.8.2**, Loom **1.19-SNAPSHOT**.

**Download:** Use the [experimental v1.2.0 release](https://github.com/lawandtariq99-blip/lauua/releases/tag/lauuacheats-v1.2.0-experimental), not the old v1.0.0 artifact. The JAR is built after the CI client game test passes; the release also provides a PNG preview captured from that test. Only install it in a test profile first.

**Build:** Install JDK 25, put `java` on PATH / set `JAVA_HOME`, then run `./gradlew build` from this directory. Find the distributable in `build/libs/`; install it alongside Fabric API in a 26.2 Fabric client. Only the client entrypoint is registered; never install on a dedicated server.

**Build status:** This sandbox has no Java executable and cannot reach the Gradle/Fabric Maven hosts; its local `./gradlew build` checks stop with `JAVA_HOME is not set`. [Java 25 CI](https://github.com/lawandtariq99-blip/lauua/actions) runs both `./gradlew build` (including scoring unit tests) and `runClientGameTest` in a virtual display. This exercises a disposable world, storage/spawner/ore/portal markers, ranked cluster scoring, live block updates and unload handling, GUI navigation, settings changes, and toggling a module. It does **not** verify every module or every multiplayer server; test in a disposable world before relying on it.

**Mappings choice:** Yarn has no 26.2 branch (its newest published branch at creation was 1.21.11). The official 26.2 example uses Mojang's official mappings by default; this project follows it instead of inventing an incompatible Yarn version. This is a deliberate departure from the request.

## Controls

| Key | Action |
|---|---|
| Right Shift | Open dashboard (click card to inspect; click switch to toggle; scroll for more) |
| End | Panic: disable all modules and clear queued actions |
| Insert | Log current coordinate |
| F9 | Start the anchor recipe macro (does not select inventory items automatically; slots 0/1/2) |
| Unbound | Module keybinds default to -1; click Keybind in the inspector to assign, Escape/Backspace to clear |
| Right click settings row | Reset setting to default |

Chat messages beginning `.lauua` are intercepted client-side and not sent to the server. Commands: `.lauua gui`, `.lauua panic`, `.lauua toggle <module>`, `.lauua save [profile]`, `.lauua load [profile]`, `.lauua macro anchor`, `.lauua waypoint <name>`, `.lauua export`, `.lauua scan`, `.lauua weight minecraft:chest 7`, `.lauua hud Coordinates 20,30`. Module names with spaces work with `toggle` (for example `.lauua toggle FPS HUD`). Profiles are one JSON file each under `config/lauuacheats/profiles/`; coordinates and exports are under `config/lauuacheats/waypoints/`. All lists and queues are bounded. Settings change at runtime.

## Modules

Tiers are **descriptive labels**, not a guarantee of server compatibility or safety. Tier D modules are off by default. Basefinding modules ClusterFinder, SpawnerESP and StorageESP are **ON by default**; all Tier D modules remain off.

| Module | Tier | Current implementation / limitation |
|---|---|---|
| Vanilla Mode | A | On by default; 3-block reach clamp, configurable brand and packet pacing |
| Reach | C | Distance setting; applies to internal target selection, not vanilla interaction range |
| KillAura | D | Targets hostile `Mob` entities, not players; rotated action queue |
| AutoTotem | B | Inventory hotbar/main -> offhand swap |
| AutoOffhand | B | Golden apple when offhand empty |
| AutoEat | B | Uses food already in main hand |
| CrystalAura | D | Basic place/break, approximate damage, self-damage/no-totem checks; obsidian placement when held |
| AnchorAura | D | Interacts with a targeted anchor outside Nether; no full damage model |
| AnchorMacro | D | Preset place/charge/detonate/replace recipe in macro engine; experimental |
| ClusterFinder | B | Indexed base clusters (storage + utility combinations), editable weights, adjustable per-tick budget; no server seed |
| SpawnerESP | B | Actual spawner block coordinates, 3D translucent boxes + HUD radar, ON by default |
| StorageESP | B | ON by default; indexed storage subtypes, configurable colors, filters, range, opacity and fill/outline; radar respects filters |
| OreESP | B | Rare diamond/emerald/ancient-debris markers in loaded scanned chunks; off by default |
| PortalESP | B | Nether portal and End frame markers from loaded chunks; off by default |
| UtilityESP | B | Utility block overlays from loaded chunks; off by default |
| CoordLogger | B | Automatic route journal after configurable distance and time; manual Insert/`.lauua waypoint` still works. `.lauua export` writes JSON/CSV/approximate Xaero text |
| SessionTimer | A | Elapsed session HUD |
| FPS HUD | A | Client FPS HUD, no ping/TPS |
| TargetHUD | B | Hovered mob health HUD |
| TriggerBot | D | Attack hovered mob at cooldown |
| AutoTool | B | Select fastest hotbar tool while breaking |

The ClickGUI is an original custom-rendered dashboard (inspired by the category/search pattern of open-source clients, not copied code) with navigation sidebar, live search, module cards, on/off switches, tier labels, an inspector with clickable sliders/toggles and a Save button. At small GUI resolutions, selecting a card opens a full-width inspector with a **Modules** back button. Scroll the module list or inspector; right-click a setting to reset it. Keybinds can be set in the inspector; lists and string settings remain profile-JSON-only. HUD registry renders coordinates, reach and active modules. Click **HUD** to enter reposition mode, drag labeled handles, then exit the mode. The handles stay hidden during normal use. You can also use the `.lauua hud` command. Click **SAVE** to persist changes in the default profile.

The macro engine supports place, break, use, hotbar switching, attack, look, chat and wait steps, delays/loops and release cancellation; actions are queued through the shared rotation manager. Macro definitions are currently Java registrations rather than an editor or persistent macro files. Rotation modes are instant/linear/smoothed, but Vanilla Mode forces immediate ordering. The explosion damage predictor uses distance without armor/obstruction for self damage, and line of sight for target damage; **it is not a reliable PvP safety guarantee**.

### Text and sign test

The client-only mixins try to rewrite the outgoing brand payload to `vanilla` and preserve our own translation keys when `Component.getString()` is used for plain text. The latter is global to the conversion method, not sign-specific. The identity `TextPolicy.signPayload` avoids rewriting sign-writing text. **Neither mixin has been tested on a running 26.2 client.** Example command to create a sign whose front text contains a translation key (use a supported block underneath it):

```mcfunction
/setblock ~ ~1 ~ oak_sign{front_text:{messages:['{"translate":"key.lauuacheats.zoom"}','{"text":"plain"}','{"text":""}','{"text":""}']}}
```

Edit the sign and inspect what is sent/retained; expected raw key is `key.lauuacheats.zoom`. Server sign sanitization and future component syntax can change this result.

### Scanner scope and safety

A canonical repacked-palette/bit-storage comparator is provided for use with a trusted baseline. It does not expose the *original raw in-memory palette ordering*, and no vanilla worldgen baseline generator is implemented. The scanner reads at most 8,192 block states per client tick **and stops after about 1.5 ms**, skips palette sections that contain no tracked blocks, indexes up to 96 block entities per tick, and retains at most 512 results, 128 queued chunks, 96 marks per chunk and 192 visible markers plus bounded logger entries. Priority caps prevent artificial blocks from hiding rare ore markers. It drops work on disconnect/dimension change/chunk unload. A true fresh-versus-modified determination would require the **server seed, generator settings, biome and exact worldgen version** to create a vanilla reference and compare *ordered raw palette entries plus packed bit-storage*. None of these are available for most remote servers. This prototype therefore only ranks unusual block distributions and explicitly does not assert fresh generation. The Xaero format is an approximation; check it with your Xaero version before importing.

### Not implemented

Stage 8 started only after the Java 25 CI build passed. Several small stage-8 modules were added; the rest of its long wishlist was not attempted. The inert NewChunks and WaypointExport toggles were removed; `.lauua scan` provides ranked leads and `.lauua export` exports waypoints, but neither implies the ability to identify newly generated terrain. Stage 7's exact vanilla-worldgen palette comparison, stage 2's complex settings editors, stage 3's verified sign/anvil behavior, and several advanced combat features are likewise incomplete. Do not mistake the module table for a claim that all requested functions are complete.

### Basefinding test setup

For a simple in-game test in a disposable creative world, place a chest, crafting table and barrel close together with `/setblock`, then walk within the configured ESP range (default 96 blocks). ClusterFinder, StorageESP and SpawnerESP start enabled. Expect colored 3D overlays and a top-right radar; use Right Shift → WORLD for scan budget, range, radar and weights; use RENDER → StorageESP for colors, subtype filters, overlay style, opacity and range. Enable OreESP/PortalESP/UtilityESP there separately. A single natural chest or spawner is **not** enough to assert a base. Use `.lauua scan` to see scored chunk leads. ESP shows only currently loaded and scanned chunks; joining a new world needs a few ticks to populate the index.

### Verification and limitations

The client gametest uses a disposable local world, virtual display screenshots of both the dashboard and StorageESP inspector, and synthetic mouse clicks. It confirms scanner output for chest, spawner, ore, portal frame and live barrel updates, tests storage subtype/filter routing and unload invalidation, and exercises the GUI. It does **not** prove every overlay is visible under every server's shaders, cover every combat/movement module, detect structures behind unloaded chunks, or guarantee compatibility with other mods or anticheats. Tier labels are not test badges. The ESP is client-side and only sees scanned loaded chunks. The palette and geometry use Minecraft 26.2's debug-filled-box render node; the outline is thin filled strips, not a shader outline.
