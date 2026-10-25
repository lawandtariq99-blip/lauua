# Lauua Cheats (experimental)

Client-only Fabric mod project for **Minecraft Java 26.2**, mod ID `lauuacheats`, package `com.lauua.lauuacheats`, version `1.0.0`. Based on the official [Fabric example mod's 26.2 branch](https://github.com/FabricMC/fabric-example-mod/tree/26.2). Java **25**, Fabric Loader **0.20.1**, Fabric API **0.161.0+26.2**, Gradle wrapper **9.8.2**, Loom **1.19-SNAPSHOT**.

**Build:** Install JDK 25, put `java` on PATH / set `JAVA_HOME`, then run `./gradlew build` from this directory. Find the distributable in `build/libs/`; install it alongside Fabric API in a 26.2 Fabric client. Only the client entrypoint is registered; never install on a dedicated server.

**Important build status:** The supplied coding sandbox had **no Java executable** and could not reach `services.gradle.org` or `maven.fabricmc.net`. `./gradlew build` was attempted after each stage and immediately stopped with `JAVA_HOME is not set`. Consequently **this code has not been compiled or play-tested**. It is a scaffold/prototype, *not* a verified working cheat client. Source was parsed for Java syntax only. Run the build on a networked JDK 25 machine and fix API/mixin errors before using it.

**Mappings choice:** Yarn has no 26.2 branch (its newest published branch at creation was 1.21.11). The official 26.2 example uses Mojang's official mappings by default; this project follows it instead of inventing an incompatible Yarn version. This is a deliberate departure from the request.

## Controls

| Key | Action |
|---|---|
| Right Shift | Open ClickGUI (left-click module to toggle/select) |
| End | Panic: disable all modules and clear queued actions |
| Insert | Log current coordinate |
| F9 | Start the anchor recipe macro (does not select inventory items automatically; slots 0/1/2) |
| Unbound | Module keybinds default to -1; set via profile JSON |
| Right click settings row | Reset setting to default |

Chat messages beginning `.lauua` are intercepted client-side and not sent to the server. Commands: `.lauua gui`, `.lauua panic`, `.lauua toggle <module>`, `.lauua save [profile]`, `.lauua load [profile]`, `.lauua macro anchor`, `.lauua waypoint <name>`, `.lauua export`, `.lauua scan`, `.lauua weight minecraft:chest 7`, `.lauua hud Coordinates 20,30`. For module names containing spaces, profile JSON/GUI is easier than the `toggle` command. Profiles are one JSON file each under `config/lauuacheats/profiles/`; coordinates and exports are under `config/lauuacheats/waypoints/`. All lists and queues are bounded. Settings change at runtime.

## Modules

Tiers are **descriptive labels**, not a guarantee of server compatibility or safety. Tier D modules are off by default. All modules except Vanilla Mode are initially off.

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
| NewChunks | C | Scored heuristic: **UNKNOWN** or **LIKELY_MODIFIED**, never claims fresh worldgen |
| ClusterFinder | C | Rare-block weighted scoring with height adjustment; no complete biome baseline |
| SpawnerESP | B | HUD count of scanned spawners; no world-space boxes |
| StorageESP | B | HUD count of scanned storage; no world-space boxes |
| CoordLogger | A | Manual chat/file/list logging |
| WaypointExport | A | CSV, JSON, tentative Xaero format export |

The ClickGUI has category collapsing, live search, module toggles, tier labels and basic setting toggles/stepping. Non-numeric complex settings (lists, colors, keybinds and strings) currently need JSON editing and a profile reload. HUD registry renders coordinates, reach and active modules, and has configurable background/opacity/shadow fields; drag the blue `::` handles in the ClickGUI to reposition, or use the chat HUD move command.

The macro engine supports place, break, use, hotbar switching, attack, look, chat and wait steps, delays/loops and release cancellation; actions are queued through the shared rotation manager. Macro definitions are currently Java registrations rather than an editor or persistent macro files. Rotation modes are instant/linear/smoothed, but Vanilla Mode forces immediate ordering. The explosion damage predictor uses distance without armor/obstruction for self damage, and line of sight for target damage; **it is not a reliable PvP safety guarantee**.

### Text and sign test

The client-only mixins try to rewrite the outgoing brand payload to `vanilla` and preserve our own translation keys when `Component.getString()` is used for plain text. The latter is global to the conversion method, not sign-specific. The identity `TextPolicy.signPayload` avoids rewriting sign-writing text. **Neither mixin has been tested on a running 26.2 client.** Example command to create a sign whose front text contains a translation key (use a supported block underneath it):

```mcfunction
/setblock ~ ~1 ~ oak_sign{front_text:{messages:['{"translate":"key.lauuacheats.zoom"}','{"text":"plain"}','{"text":""}','{"text":""}']}}
```

Edit the sign and inspect what is sent/retained; expected raw key is `key.lauuacheats.zoom`. Server sign sanitization and future component syntax can change this result.

### Scanner scope and safety

An exact palette/packed-storage comparator is provided for use with a trusted baseline, but no vanilla worldgen baseline generator is implemented. The scanner reads at most 256 block states per client tick and retains at most 128 results per world, 16 world keys, and bounded logger entries. It drops work on disconnect/dimension change/chunk unload. A true fresh-versus-modified determination would require the **server seed, generator settings, biome and exact worldgen version** to create a vanilla reference and compare *ordered raw palette entries plus packed bit-storage*. None of these are available for most remote servers. This prototype therefore only ranks unusual block distributions and explicitly does not assert fresh generation. The Xaero format is an approximation; check it with your Xaero version before importing.

### Not implemented

Stage 8 was explicitly conditional on stages 0–7 building clean; the build cannot run in this sandbox, so none of the long stage-8 wishlist was attempted. Stage 7's exact vanilla-worldgen palette comparison and world-space ESP, stage 2's complex settings editors, stage 3's verified sign/anvil behavior, and several advanced combat features are likewise incomplete. Do not mistake the module table for a claim that all requested functions are complete.
