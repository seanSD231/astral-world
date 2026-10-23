# Astral World — Changelog

Major changes only. Nothing from 0.1.48 onward has been tested in-game yet; every build compiled green on CI.

## 0.1.103
- The beam attack is now a summon: 5 Sequence 4 Beyonders (Desire's Thralls) rise and hunt the players; they vanish when the raid ends.
- Hardened the Mother Tree form: once she changes she never goes back to the World Tree name or pleading lines.

## 0.1.102
- The tree is "The World Tree" above half health and the Mother Tree of Desire after the 50% change. Every 5% of health above half she says a random line about how killing her is a mistake; they stop at 50%.
- Her boss bar name and percent now use a moving gradient in the bar's own colours (blossom pink, lilac and green before the change; blood red and pustule yellow after it).
- After the change the bar lies: at her real 50% it reads 65%, and it reads -25% only when she is really at 0. The lie is drawn by the client; her real health is untouched. Her phase marker sits where her real 25% reads (20%).

## 0.1.101
- The vanilla 1024 max health cap is lifted at startup (client and server). The Mother Tree now has 1,000,000 real HP with no hit shrinking, so abilities that check health or kill under a threshold see her true health. Start a new raid for it to apply (an existing tree keeps its old health). The sovereigns are unchanged.

## 0.1.100
- Raid: the Mother Tree of Desire has ten times the health: 1,000,000 effective HP (was 100,000). The per-hit and per-second caps are still the same share of her health, so in raw damage they allow ten times more as well.

## 0.1.99
- Bosses: woven curses (the Auditory Curse and the rest of the Prisoner's curse weave), curse dolls and cursed items are wiped from every boss each tick, along with every harmful potion effect.

## 0.1.98
- Bosses: Depravity and Corruption (Hanged Man's aura, Profane Language, Sphere of Darkness) are wiped from every boss each tick. They wore the tree down outside the phase rules and killed it past the last roots.
- Boss fights (planets and the raid): no regression anywhere. LOTM's own regression protection is held on for everyone, a sanity about to collapse is lifted (the raid still takes a life for it), a sequence that drops anyway is put back, and any fallen persona (the corrupted rampager LOTM spawns on a regress) is removed at once.

## 0.1.97
- Raid: nobody regresses in the arena. A fighter whose sanity drops to 3% loses a life and is reset to 50% before LOTM's own sanity collapse (which lowers the sequence and kills) can fire; and every second each fighter's sequence is checked against the one they came in with and put back if it dropped.

## 0.1.96
- Raid: losing your mind (0 sanity) now costs a life instead of doing nothing. The arena blocks all damage, which also blocked LOTM's sanity death. Sanity is set to 50% afterwards so it cannot repeat at once.

## 0.1.95
- Raid: at 15% the tree casts a giant meteor from the sky, once per fight. It is never announced and nothing marks the ground; everyone further than 26 blocks from the trunk loses a life. Only running under the tree is safe.
- Raid: the curse now adds 35% corruption (was 15%) and logs a warning if LOTM refuses.
- Ascension: a Door already in the Astral World when it reaches Seq 0 now gets the ascension message and title (it used to get nothing); everyone else is still teleported.

## 0.1.94
- Diagnostics: every hit on a boss that is cancelled or reduced to nothing is now written to the server log ("hit on ... did nothing") with its damage type and attacker, at most once a second per boss.

## 0.1.93
- Bosses are now immune to the World of Pallor (it shrank their hitbox, drained health outside the phase rules and stripped abilities). Uses LOTM's own protected-structure flag, refreshed every tick.

## 0.1.92
- Raid: rings and the beam now hit anyone flying or hovering; only a real jump avoids them.
- Raid: the battlefield edge no longer drops flying players to the ground (it kept their height).
- Raid: new CURSE attack that slows, weakens, makes unlucky and corrupts everyone.
- Bosses: if an ability such as the Death pathway's withering wind sets a boss to 0 health without a normal death, it now counts as a kill near the end, or the tree returns with the health it had, not at 100%.

## 0.1.91
- Raid: meteors are back to their old size until the 50% change, then grow. Rolling rings and the beam are wider; safe circles are smaller.
- A second copy of a boss can no longer enter the world while the real one is alive (stops summoning the tree back from a spirit store).

## 0.1.90
- The Death pathway can no longer enslave the sovereigns or the Mother Tree: any claim placed on a boss is undone every tick.

## 0.1.89
- Raid: the battlefield now has an edge that grows with the crowd (about 100 blocks for one fighter, the whole arena at 100); going past it puts you back inside, so nobody can outrun the attacks. Meteors and slams are larger.
- The tree can no longer be dropped below the next phase threshold during a phase either (World of Pallor skipped it while roots stood).
- Losing the raid sends everyone home, removes the tree model on every client and no longer replays the old stage messages.
- The tree transformation has sound: growl, a quickening heartbeat, shriek, the swap and a roar.

## 0.1.87 — Hitboxes, fairer raid mechanics (current)
- Bosses have real hitboxes now, sized to their models and set through Pehkui (reflection; falls back to the vanilla scale attribute, capped at 16x, if Pehkui is not usable): the tree is 30 wide and 150 tall, sovereigns 8-24 wide and 7-20 tall.
- Temptation: the drag is much gentler, 3 s grace, wider aim tolerance, and the meter takes 5 s of failing to fill.
- Roots scale with the crowd: about 7 for one fighter (near the middle), up to 70 for a crowd; 45 s plus a little per root. Seeds likewise.
- The tree's health can never be pushed past its next phase threshold, even by abilities that set health directly.

## 0.1.86 — Raid scale
- The arena is 190 blocks in radius and the raid attacks are sized for the tree: the purple rings now roll past the last roots, meteors and slams are 9 and 13 blocks wide, safe circles are 10 wide.
- The tree stays on screen in the raid arena however far you go, even when the game stops tracking the boss.

## 0.1.84 — Raid arena for a crowd
- The raid arena is now 160 blocks in radius (was 45), built for up to ~100 players: the beam, rings and safe circles scale to it, there is one safe circle per four fighters, and more roots and seeds spawn the more fighters there are. Fighters arrive on four rings at 60-90 blocks.
- Markers are sent in one batched packet per tick. With a crowd, only some strikes get a real lightning bolt.
- The raid terrain is flat out to 180 blocks and the ruined pillar ring is at 156 (36 pillars). Only newly generated chunks use this: delete the raid dimension's region files to get the new layout.

## 0.1.83 — Raid readability
- Raid attack markers (rings, beam, meteor/slam circles) and the root/seed beacons are now drawn by the client on top of everything, so the huge tree model can no longer hide them. A compass in the action bar points to the nearest root or seed.
- Roots and seeds appear further out (18-42 blocks); fighters arrive at 38 blocks, outside the trunk.
- The tree can never be hit past its next phase threshold (75/50/25%), however hard. Damage is also capped per second (0.1.82).
- A brand-new player is shown only the current stage's message, not every earlier one.

## 0.1.79 — Tree transformation cutscene
- At 50% HP the Mother Tree plays a 13-second cutscene for everyone in the raid: wither, rot, then it swallows flesh and becomes its fleshy form. Letterbox, subtitles, hold SPACE to skip. The fleshy form now stays for good (the server marks it), and Desire's call starts when the scene ends.

## 0.1.76 — Sovereign powers
- Every third attack in a sovereign's cycle is a POWER: a ring charges for 2.5 s, then it casts one of two LOTM abilities of its pathway at a random fighter. Otherwise they never cast on their own.

## 0.1.67 — Boss movement
- Planet bosses move on a script, never just chasing: Mercury and Uranus blink (Uranus behind the nearest player), Mars walks and keeps its distance while circling, Venus, Jupiter, Saturn and Neptune fly their own orbits. All stay within 34 blocks of the arrival point.

## 0.1.65 — Boss animation
- Sovereign models are split into parts and animated client-side: hovering, breathing, wing flaps and head sway (Venus), swaying ribbons and drifting worlds (Mercury), swinging arms (Mars), pulsing core and spinning rings (Jupiter), turning funnel (Saturn), turning wheel and swaying cloth (Uranus), turning ring of three (Neptune). Models jolt when hit.

## 0.1.59 — Earth is the overworld
- The separate Earth dimension is gone. The Earth planet in the Cosmos and `/cosmos to earth` take you to the overworld spawn.

## 0.1.58 — Raid arena plaza removed
- The stone plaza is gone; the raid arena is plain grass. Pillars and ruins remain.

## 0.1.57 — Sovereign models
- The seven planet sovereigns are drawn as custom meshes (Sublunary Eye, Mind Dragon, Second Law, Condenser, Tail-Devourer, Eternal Edict, Eternal Aeon) at 0.55 scale, facing the player. Boss mobs are hidden.
- Boss pathways switched to complete mod pathways: Mercury Door, Venus Visionary, Mars Twilight Giant, Jupiter Tyrant, Saturn Death, Uranus Wheel of Fortune, Neptune Sun.
- Not yet in game: bloom, texture noise, a matching hitbox. Unverified in-game.

## 0.1.56 — Mother Tree models
- The Mother Tree of Desire is drawn as a custom smooth mesh (about 44k quads) in place of the boss mob: braided trunk, 16 flat roots, forking branches, blossoms, glowing veins.
- At 50% HP it switches to a rotten, fleshy form (about 33k quads): tentacle roots, growths, sparse sickly fruit.
- Not in the in-game render: bloom glow and drifting motes (previews only). The hitbox is still human-size. Shader packs may break it.

## 0.1.48 – 0.1.55 — The End Game
- **0.1.48** Evacuation phase, seven planet bosses, the raid, and recovery after the barrier breaks.
- **0.1.49** Raid attack order as a graph (meteors only after a slam or rings, and so on). Operators are not kicked. The server closes after the end. Top-20 leaderboard for damage, healing and deaths.
- **0.1.50** Mother Tree mechanics: roots, temptation, 80% max-HP hits with corruption.
- **0.1.51** A different mechanic every 25% (roots at 75%, Desire's call at 50%, heart seeds at 25%). Each hit does 80% corruption and 50% sanity loss, and bypasses revives.
- **0.1.52** Planet bosses get raid-style attacks. The first use of each attack shows a notice on how to avoid it. Desire's call is now a 10-second camera-pull minigame (crouch, look away, resist).
- **0.1.53** Each planet is a large biome shaped like the real planet.
- **0.1.54** The raid arena is an old overgrown sanctuary, left plain for builders.
- **0.1.55** Everyone in the boss fights is automatically a LOTM ally.

## 0.1.46 – 0.1.47 — Cosmos
- Cosmos dimension: outer space with the planets as portals to planet dimensions. Zero gravity, major corruption, `/cosmos` command (Door Seq 3 or lower, Outer Deity Seq 2 or lower).

## Stages 4 and 5
- Stage 4 starts 30 days after stage 3: faster damage, god sieges, stronger curses.
- Stage 5 is the end: silence, chaos, kicks, reset and stop.

## 0.1.x polish — Sky and barrier (0.1.15 – 0.1.44)
- Custom astral sky, swirling colour vortexes, 22 distinct god signs on their own sky slots.
- Barrier tuned many times: HP x10, regen per minute, small non-overlapping white/grey cracks, rounded animated bar.
- Outer God curses use LOTM abilities per pathway, with stronger effects and super vexes.
- Fixes: ascension teleport limits, upside-down symbols, renamed `astral_space` to `astral_world`.

## 0.1 – 0.5 — Foundation
- Astral World dimension, corruption, `/astral`, barrier health, HUD.
- Stages 1 to 3: Outer Gods notice the world, then the sky barrier with cracks.
- Per-god curses, name-corruption kill line, supernova teleports.
- Unbreakable Astral Floor block with auto-repair, and no entering or leaving during the LOTM combat timer.

## Design work (not in the mod yet)
- Seven sovereign designs, one per planet. Each sits on a complete mod pathway (all 10 sequences filled in) and is shaped like the matching boon pathway's mythical creature form from the wiki:

| Planet | Mod pathway | Boon pathway form |
|---|---|---|
| Mercury | Door (Apprentice) | Sublunary Eye |
| Venus | Visionary (Spectator) | Mind Dragon (winged) |
| Mars | Twilight Giant (Warrior) | Second Law |
| Jupiter | Tyrant (Sailor) | Condenser |
| Saturn | Death (Corpse Collector) | Tail-Devourer |
| Uranus | Wheel of Fortune (Monster) | Eternal Edict (single entity) |
| Neptune | Sun (Bard) | Eternal Aeon (ring of three) |

- Seer and Marauder are incomplete pathways in the mod, so Mercury and Uranus need their boss pathway switched when these go in.
- Previews cover the Mother Tree and all seven sovereigns.

## Open items
- Arena scale: the 150-block tree overlaps the 48-block arena and the spawn ring. Decide whether to enlarge the plaza (about r150) or shrink the tree.
- Verify in game: planet terrain, raid arena, attacks, camera-pull, LOTM ally reflection, boss spawning, damage scaling.
- Build the seven sovereign models and switch the boss pathways.
