# Astral World — Changelog

Major changes only. Nothing from 0.1.48 onward has been tested in-game yet; every build compiled green on CI.

## 0.2.231
- The Mother Tree fight in Photon. Fifteen new particle effects (assets/astral_world/fx/mtod/, written by tools/fx/gen_fx.py) are played beside every attack: fire, smoke and cinders trailing each meteor on its way down; a flash, fire, thrown sparks, a skirt of dust and a smoke column where it lands (in petals for the thorn spike, in flesh colours after she changes); earth and sparks thrown up along the rolling ground rings; lightning, glare and leaves on the ring in the air; a thick pollen haze that drifts with the cloud and goes when it is dispelled; light streaming into the Purge and the supernova, then a blast; violet wisps and black smoke in the curse bubbles; sheet lightning and sparks across the arena for the storms; petals and pollen off the flowers; red mist on her death puddle. The exact warning shapes drawn before (where a ring is, how far a blast reaches) are kept, since those are what you dodge by. Without LOTM's Photon bridge the fight looks as it did.
- Untested in game.

## 0.2.230
- Opening a card pack. The pack hangs in the air like a foil pouch instead of sitting flat: it bobs, leans toward the mouse with its far edge showing, casts a shadow, and a sheen runs across it; the place to start tearing pulses. As you tear, it trembles harder the further you get, light pours out of the open part of the seam and sparks fly off the tearing point. When it gives there is a flash, a burst of turning rays and a spray of light, the strip spins away, light stands in the open mouth, and the cards come up out of the mouth small, rise, and swing out to their places with a little overshoot (they used to fade in where they stood).
- Untested in game.

## 0.2.229
- Arbiter's scales: the heart is one smooth surface now (the real heart outline, bulging front and back, shaded in the cleft, with a sheen and the stubs of the great vessels) instead of two balls and a wedge; the feather has a bending quill, two vanes of separate barbs, a notch and down at the root instead of nine stacked bars.
- Paragon train: the wheels were bugged. The tyres were laid flat like plates while the spokes stood upright, and the coupling rods went round the opposite way to the wheels. Rebuilt: upright tyre with a flange and gilt rim, tapered spokes into a hub, counterweights and crank pins on the driving wheels, and the rods now ride the pins. The carriage wheels get a tyre and flange too.
- Untested in game.

## 0.2.228
- The Sword of Twilight now outdoes LOTM's own on every count. Swing it at the air and a blade out of the void strikes the foe you are looking at, up to 48 blocks away, for 70 (LOTM: 54), once every second and a half. Right click: the colossal blade lands for 100 (LOTM's greatsword: 80; was 24) and leaves a hurricane of twilight for 15 seconds that drags foes in for 10 a pulse (was a 3.6 second field for 4). Every blow, strike and pulse ages and slows what it touches. Holding it you take a quarter less damage (35% less once unsealed), and allies within six blocks an eighth less. A Seq 1/0 Warrior gets 110, 220 and a 20 second hurricane at 20 a pulse. Cost 2500, cooldown 20 seconds for everyone.
- Untested in game.

## 0.2.227
- The Feathered Serpent is now the Book of Feathered Serpents: an illusory book hanging open by your hand, held together by two small feathered serpents that run up the edges of its boards and curl their heads over the top, with a page that turns by itself. New inventory icon to match.
- Its ability gives you the Hands of Death for five seconds. LOTM's own Hands of Life and Death is switched on for you as a system cast (no cost, no cooldown, any pathway) and off again when the time is up, so a bare-fisted punch does exactly what a Death's does; a hit with something in your hand lays the same decay on through LOTM's trait. If LOTM cannot be reached the hit takes 12% of their health and withers them. Seq 1/0 Death: every hit also catches each foe within four blocks. Cost and cooldown unchanged (4000, 60 s).
- Untested in game.

## 0.2.226
- The Wheel of Fortune pays out properly. Seven spins in ten land on a boon (nine in ten, and two spins, for a Seq 1/0 Monster): Wellspring (a third of your spirituality back at once, then 4% of your maximum every second for 30 seconds), Vigor (+40 maximum health for a minute, a full heal, then 5% of maximum health every second for 30 seconds), Lucky Strike (Strength IV, Haste III), Charmed Life (Resistance III, fire resistance, twenty absorption), Fair Wind (Speed IV, Luck V, jump, slow falling), or one spin in twelve the Jackpot: all five at once. The banes are short and never deadly.
- Untested in game.

## 0.2.225
- Hunger games: grazed souls, scribed abilities and enslaved spirits are taken away on the way in (before, they were only restored on the way out, so they could be used inside), given back exactly as they were on the way out, and anything grazed, scribed or enslaved inside the arena is dropped.
- Untested in game.

## 0.2.224
- The Mother Tree scales with the crowd: up to raid_full_damage_players fighters (settings.properties, default 3) hurt her at full strength, and past that every hit is scaled so any number together do what that many would. The count is the most fighters the raid has had at once, so she does not soften as they fall.
- Depravity can no longer delete her health. Her health now only falls by hits the raid's own damage rules passed: anything that sets it directly (the Hanged Man's Depravity, and whatever else does the same) is put back on the next tick and logged.
- Untested in game.

## 0.2.223
- The hunger games leaderboard is a monument now, not a row of signs: /hungergames board sets down one entity drawn as a six-block slab of dark stone in a gold frame on a stepped plinth, a pillar each side with a burning brazier, and a turning gold emblem on the pediment. Cut into the front: the last game's champion with their face, kills and the field size, places two to nine behind them, and on the right the champions of up to six earlier games. It follows the games by itself, cannot be hurt or walked into, and /hungergames board remove takes the nearest one away. Old sign boards stay where they are and still update.
- Untested in game.

## 0.2.222
- Holograms. River of Eternal Darkness: the symbols stand in one straight row (each used to face the placer's exact heading while the row followed the quarter-turn frame). Lord of Mysteries: the ring of doors is centred on the middle of the table instead of on the card, a little wider, and the real doorways moved with it. Corpse Collector 0: the symbol is square to the throne. Shiny cards: every pathway symbol gets a dark edge, so a gold symbol no longer vanishes into a gold scene (the Criminal's worst of all). Monster 0: 18 coins in the air, down from 170. Shiny Sailor 0: the waterspouts are rebuilt as a smooth swaying funnel with bright strands winding up it, a churning foot, a spreading crown and spray.
- Untested in game.

## 0.2.221
- /endgame fullreset confirm (ops, or the console) puts the whole story back to day one so the server can open again after it has ended: it stops the pending close and its kicks, lets the fallen back in, restores Earth and the bosses, clears the raid, the stats and the allies, and sets the stage to 0 with a full barrier. Players keep their own items and sequences.
- Untested in game.

## 0.2.220
- The amethyst, emerald and diamond coins now come from a built-in resource pack that is always on and always on top, so they replace Numismatics' cog, crown and sun whatever order the mods load in. Relying on load order alone left the old coins showing.
- Untested in game.

## 0.2.219
- The shrooms no longer touch the hitbox, the Mystic Shroom included: all three only change how you are drawn. They are more extreme (a quarter size; a beanpole; a pancake over three times as wide), spring into shape with a bounce, wobble like jelly while they last, and come with a run of notes, a puff of particles and a pop when they wear off.
- The confetti popper throws two and a half times the confetti with a flash and a bang; the bubble wand blows three times the bubbles.
- Untested in game.

## 0.2.218
- The Quill tells its story now. LOTM drops a story unless the teller holds a Prophecy and deletes a Prophecy from anyone who should not have one, so lending the item could never work. The mod now watches for LOTM's "tell this story" packet from whoever has the Quill's screen open and tells it itself: Prophecy into the hand, LOTM's own tell(), hand put back, all in one server step with nothing in between.
- Untested in game.

## 0.2.217
- Rainbow and Shiny weekly packs share one allowance: rainbow 2 and shiny 2 on a rank now means two of either (two Rainbow, two Shiny, or one of each), not two of each. No change to ranks.snbt is needed. The menu says how many you may take.
- Untested in game.

## 0.2.216
- Cards can be placed again. 0.2.209 added the synched "who put this down" value to the card entity but never defined it, so building the entity threw and every placement failed on the server with "has not defined synched data value 13". Nothing else was wrong; the card and the item were fine.
- Untested in game.

## 0.2.215
- Rank numbers work again. FTB Ranks returns an OptionalInt from asInteger, not an Optional, so every numeric node was read as missing: weekly_packs fell back to the default, fun_items read as none, and perks.title never fired. Only the true/false nodes (the commands, the credit card, the terminal) ever worked. Node reading is now one tolerant helper that takes OptionalInt, OptionalLong, OptionalDouble, Optional or a bare value, finds getPermissionValue by name rather than by exact signature, and falls back to the string and decimal readers.
- /astralranks [player] (ops) prints what the server actually reads for every node this mod uses, plus the pack and fun-item totals it works out from them.
- Fun items are a menu now, like the packs: /funitem opens a grid of the five items, click one, confirm, take it. Logging in with one waiting prints a clickable message the same way the weekly packs do. /funitem reset <player> clears someone's wait.
- The weekly-pack login message now waits a moment before it is sent, so FTB Ranks has the player's rank first; before this a player could log in and be told they had none.
- Untested in game.

## 0.2.214
- The amethyst, emerald and diamond coins are round struck coins now, not gears: same size, margin, hard dark rim and bright centre as the LOTM bronze, silver and gold coins, with the stone cut into the face.
- Untested in game.

## 0.2.213
- The three Numismatics coins this server does not trade in are now amethyst, emerald and diamond: cog, crown and sun are redrawn as gem coins and renamed. The sprites are drawn from scratch (tools/art/coins.py), not copied, and the mod now loads after Numismatics so its own art and names win. Values are untouched, so nothing in the coin bridge changes.
- Untested in game.

## 0.2.212
- JEI hides the LOTM potion recipes whose formula you have not found yet. LOTM's own JEI plugin already hid the potion items, but the cauldron recipes are ordinary data-pack recipes every client knows, so the formulas were on show anyway. The server now tells each client which ones it has unlocked (LOTM keeps that only on its own side) and JEI hides the rest. Without JEI, without LOTM, or on a server without this mod, nothing is hidden and JEI behaves as before.
- Untested in game.

## 0.2.211
- Hunger games: the arena has cover again (pillars, broken walls and hollow mounds, never over the church), the shrinking border now shoves you back inside instead of letting you walk out, the countdown no longer flings you into space, a full heal happens after your sequence is applied rather than before, /hungergames spectate and unspectate, and the board keeps the last six games.
- The Mother Tree's hitbox is measured off her own model now: twenty boxes, one per slice of height, each only as wide as the tree really is there, and they grow with her. The old hand-written boxes were up to three times too wide around the trunk, so swings at thin air connected.
- The parry is much harder: the marker sweeps about twice as fast, the score falls away sharply from the centre (the green band is a few percent of the bar), and a click is now scored where you saw the marker instead of up to a whole tick behind it.
- No divine kingdom of your own opens during the raid: hers is the only one in that arena.
- At 5% she gathers a supernova. Ten seconds of warning, then everyone still inside the arena loses a life whatever their health, totems or revivals.
- The curse is a dodge now: a bubble closes round each fighter where they stand and bursts after three quarters of a second. Step out and it costs nothing; stay in and you take one to five of chaos, decay, withering, venom, fatigue, misfortune, silence, freezing, burning and anchoring through LOTM's own silentcurse.
- Prayer line 6 added.
- Untested in game.

## 0.2.210
- Three fun shrooms: Mystic Shroom shrinks you (through the vanilla scale attribute, so your hitbox shrinks too), Slim Shroom makes you very thin and Wide Shroom very wide (those two change how you are drawn; the hitbox stays). Right click yourself or someone else; it lasts a minute.
- /funitem lets a rank take one of the shrooms, the bubble wand or the confetti popper, as often as its rank allows: the node astral_world.fun_items is how many days between picks (7 for Supporter, 3 for Supporter+, 1 for VIP).
- /enchanting and /anvil are no longer ops-only, and /enderchest is new. Each is opened to a rank with astral_world.cmd.enchanting, .anvil and .enderchest.
- A rank's items and title are handed over the moment the server sees the rank, once each: astral_world.perks.credit_card gives the Supporter+ Credit Card, .bank_terminal the VIP Portable Bank Terminal (both marked astral_keep, so they survive death) and .title grants that title through the title mod's own command.
- Titles no longer announce themselves in chat (hide_title_messages in settings.properties turns that back on).
- Weekly pack counts need no code: astral_world.weekly_packs with .rainbow and .shiny per rank.
- Untested in game.

## 0.2.209
- Cards cannot be hurt, burned or killed any more (Death's Pallor took them); a Seq 1, Seq 0 or Great Old One card lying on the ground does not burn either.
- Shiny: the Apothecary's bats are gold (a very dark colour was mapping to near-black), the Marauder's black error lines are gone (they fought with the pillars of light), the Sailor's waterspouts use the model so they are gold too, and the Hanged Man Seq 0 floor is a gold swirl instead of a greyed rainbow.
- A dark sphere is now solid from outside as well, so it hides the card and the particles inside it.
- Prisoner Seq 4 uses the proper padlock. Bard Seq 0's symbol stands forward, clear of the sun. Wheel Seq 0 throws far fewer coins. The Arbiter's gavel is gone. The Demon of Knowledge keeps its books and loses the loose paper.
- Father of Devils had the abyss symbol twice (the single symbol over its head was the same one); it is gone and the pair sits much higher. The glow behind a Great Old One's symbols no longer slides about with the camera, and God Almighty's rings stay inside its pillars.
- Corpse Seq 0's graves keep clear of the throne and the way up to it. Chained Seq 0's cage hangs empty. Fool Seq 1 loses the eye. Spectator Seq 0 writes three lines a card at a third of the speed.
- Hanged Man Seq 0: a taller cross, the ring on the ground centred under it, and the face on it is now whoever put the card down rather than whoever is looking.
- Fool Seq 1: the round table leaves its middle open, so the card can be picked up. Fool Seq 0: the round room is centred on the card, and a sitter's symbol hangs over their chair instead of beside it.
- Lord of Mysteries keeps court in the Fool's own hall: the same size, the same real seats (so sitters' symbols show), under a sphere of grey fog. Its doors teleport like the Door Seq 0 ring.
- The Door ring sets you down in front of the door you come out of, facing away from it, instead of behind it.
- The river bridge has a gentler arch, so you walk onto it instead of jumping; the rowboat has three places, bow, middle and stern.
- A Marauder or Lord of Mysteries wearing another card's face now changes the held card's art too, and a shiny one mimics the shiny art.
- Turning off the eye beside the Curios Uniqueness slot hides the worn Uniqueness; the ability still works.
- Untested in game.

## 0.2.208
- The Quill works. LOTM only accepts a told story while a Prophecy is in your main hand, so the screen opened but nothing was ever submitted. The Quill now lends you LOTM's own Prophecy item while the screen is up and takes it straight back (it cannot be dropped, and it is returned if you log out or wander off).
- Writhing Shadows and the Bandaged Doll no longer fling you into the sky. Holding someone used Jump Boost at an amplifier that is sent to the client as a single byte, so it became an enormous upward jump; they are now held tick by tick instead.
- Writhing Shadows: the violet spots, suckers and pool are red. The shadow stands for five seconds and takes anyone who walks into it. Anyone in the air is surged up at and dragged down rather than ignored.
- The Ball of Lightning holds you in now (the old ring moved players with a teleport the client never heard about, so you rubber-banded straight back out), and it hits far harder: 56 a pulse at Seq 1/0, 26 otherwise.
- The Eyes of the Door hold everyone inside the supernova while it runs.
- The Bandaged Doll holds for four seconds instead of ten, and the plain version can be outrun: get far enough and the doll never arrives. The Seq 1/0 dolls still appear beside you.
- The Sword of Twilight carries LOTM's Aura of Twilight while held: time crawls for anything near you, further when it is unsealed.
- Using a Uniqueness you never took in costs half your corruption limit on its own pathway.
- New costs and waits: Writhing Shadows 3000/35s, serpent 4000/60s, Wheel 1500/30s, Ball of Lightning 3000/50s, Eyes of the Door 4000/40s, Doll 3000/35s, Sun 3000/60s, sword right click 2500/20s.
- Untested in game.

## 0.2.207
- Demoness Seq 1 and 0: anyone standing inside the scene takes on LOTM's own Witch body (the Assassin Sequence 7 model), and gets their own back on leaving. (Found it in LOTM: AssassinWitchChestLayer.)
- Goddess of Origin: the tree rebuilt after the two pictures together: a vast trunk plaited from strands wound both ways, veins of blue light climbing it, roots and limbs that end in curls, broad flat tiers of green canopy stacked to a crown with light breaking through, standing stones in the mist.
- Paragon: the locomotive remodelled after the picture (black and gilt, banded boiler, tall flared chimney, great lamp, brass domes and a swinging bell, gilt scrollwork, red-spoked wheels worked by rods, leading bogie, cowcatcher, arched cab), with a gilt-framed tender and maroon coaches with lit windows and clerestory roofs. Airships after the picture: a plump cream-gold envelope with seams, a red nose and tall red fins, broad wooden propellers on outriggers and a tower of round railed galleries below instead of a boat. The warship after the pictures: a gilded bow with carved scrolls, a tan deck, two tall funnels close together, white boats on davits and a second mast.
- Arbiter: the heart (now beating) and the feather on the scales are much bigger and lit.
- Untested in game.

## 0.2.206
- Shiny: the black is now part of the effect, not a shadow: a crisp black plate rimmed in gold exactly under the rings, every pathway symbol a gold emblem on a black disc, and rings in the air backed in black.
- Pathway symbols redrawn at 3x resolution with crisp edges (same look, no blur; tools/art/sharpen_syms.py).
- Spectator Seq 0: smaller quill; shiny scene uses the shiny card art; card rows no longer clip; cards show their real backs; the finale is now ten seconds of coloured pillars, rings and light before the cards are flung out and it resets.
- Black Emperor: the crown's band and plates are black (and the small crowns have black velvet); Seq 1/0 spikes are tall and black again.
- Hanged Man Seq 4/2 use LOTM's own Bearing of Sins effects. Warrior Seq 2: small swords circle it instead of shards.
- Demoness Seq 1: no mirror, no fog; Seq 0: smaller hats and potions, only a breath of mist.
- Apothecary Seq 1/0: the shelves are gone; vampires' coffins standing ajar and altars with a turning crimson crescent over moonflowers.
- Sailor: the symbol sits clear above the trident; Seq 0 has far less fog and spray. Paragon Seq 0: the water is redone (the pale cap that hid it is gone): deep water in a stone quay with currents, ripples and glints.
- Fool Seq 1: the round table is solid (walk on it, lay things on it) and the chairs are where the seats are (they were misplaced, so you could not sit); thin fog at player level. Mystery Pryer: the orb no longer dips into the floor.
- Marauder: Worms of Time as described in the books: small translucent worms of twelve clear rings.
- Door Seq 0: the ring of doors stands still and the light in each doorway no longer turns; walking into a door brings you out of another one at random.
- Monster Seq 0: no extra wheels or big dice, a cloud of small dice instead. Planter Seq 0: the spiky things were my butterflies (drawn with a wing texture); they are small two-winged butterflies now.
- Fool Seq 0 and Lord of Mysteries: chairs and table remodelled after the reference scenes (block seats with tall framed backs, the Fool's with a spire; a long patinated bronze table with a border and sunk panels). Fool Seq 0: a far smaller sky sphere centred on the hall, soft nebula (no cloud edges), more fog, bright and dim and red lights, the head chair close to the table. Lord of Mysteries: the same long table with seven real seats, ten doors all facing the table, the ring of 22 symbols removed.
- River of Eternal Darkness: the river is a loop round the whole scene; one unbroken ribbon of water and banks (no splitting strips); a whole rowboat (no holes) sails the loop and you can ride it; the rocks are solid; the bridge no longer z-fights or meets a tombstone.
- Untested in game.

## 0.2.205
- Uniqueness abilities now cost spirituality (shown on each tooltip; refunded if the ability does nothing).
- Seq 1/0 forms, for someone of that item's own pathway at Sequence 1 or 0: Quill half cost and half cooldown; Doll goes to every nearby enemy, appears beside it after a second and stuns for 5; Wheel has three times the luck; Writhing Shadows 1.5x bigger, double damage, 2 s stun, then bleed, twilight, slowness and darkness for 5 s; Eyes of the Door step you 20 blocks clear (sneak: to the target) and the last blast lights a second, greater round; Sun has five larger suns in a wider fan and then six more around you (each hits less hard); Feathered Serpent only strikes enemies, 50 blocks, 30% of max health, Slowness III; Sword of Twilight calls a barrage of small swords from every side (twilight and stun), then a far larger sword with five times the ground, heavy damage and 25% ageing, and heavier basic attacks; Ball of Lightning hits much harder over ten times the ground, lasts 9 s, lets no player out and blinds for half a second every three.
- Sword of Twilight remodelled after the reference: a white blade edged in gold, a golden crescent guard, a blue grip (item, falling sword and all its light are now white and gold). Its sealed form: if seanSD2311 holds it and says "I bless this sword, to release its power", it becomes black and red, does 25% more damage and all its effects turn black and red.
- Ball of Lightning effects redone: the ball is pure ball lightning (no cloud), and it bursts into a storm with a wall of thunderhead, forks, rain and flashes.
- Eyes of the Door: the last explosion now draws everything in to a point first, then throws a pillar of stars and rings across the ground.
- Writhing Shadows: thicker tentacles with bulges, suckers and curling tips, a round creeping pool of shadow, barbed spears; the flood is a round pool full of tentacles.
- Feathered Serpent redesigned (emerald body, gold belly, plume crest, beating wings, tail streamers, horned head with a fan of plumes) and new inventory sprites for the Serpent, Doll, Shadows, Wheel, Sun, Ball of Lightning and both swords (tools/art/icons.py).
- Untested in game.

## 0.2.204
- Scene particles are now Photon effects when LOTM is installed: every motes/sparks/petals/leaves/pages/smoke/coins call in the holograms (112 effects, one per call, with gold versions for shiny cards) plus the low fog rings and the floor fog of Sleepless, the River and the Lord of Mysteries. They are generated straight from the renderer's own calls (tools/fx/gen_fx.py), and the old sprites remain as the fallback when LOTM or an effect is missing.
- Sword of Twilight: Twilight Verdict now really drops a colossal sword of dusk (it was only a crescent before): it falls point first, buries itself, cracks the ground with light and stands burning while the field pulses.
- Sailor trident rebuilt from scratch as a classic fork: banded shaft with a butt spike, flared socket, tall middle prong, and side prongs that leave sideways and bend up in a U, all with flat arrowheads. (The old side prongs bent the wrong way.)
- Ball of lightning: the item no longer draws any hand model (that mesh was the flag-like thing); the Photon ball is the only visual.
- Fixes to the last two builds: Marauder clock hands were invisible and skewed; Fool Seq 0 head chair sat far from the table; a bobbing anchor restarted some Photon effects every frame; Sailor Seq 0 had no waterspouts without LOTM; shiny Hanged Man figure now gold; a seam in the gold ramp.
- Untested in game.

## 0.2.203
- Fool Seq 1: the fog is further out and higher and slightly lighter. Fool Seq 0: much smaller sky sphere, the table moved away from the card (the card sits on the floor, easy to pick up), and a cleaner table.
- Sleepless Seq 1/0: a dark haze and fog puffs now cover the whole floor.
- Apothecary Seq 1/0: shelved apothecary cabinets of glowing bottles fill the empty space; Seq 0 cauldron smaller and mushrooms no longer clip it.
- Demoness Seq 1: decluttered (fewer roses/arches/petals, no hats, broom or extra bottles; those stay at Seq 0). Player model change still not done.
- Arbiter Seq 0: gavel moved out of the scales. Door Seq 0 and Lord of Mysteries: LOTM door models. Lord of Mysteries table is now a smooth round annulus table and the fog is denser.
- Marauder Seq 1/0: a slowly turning clock with cogs and smooth crawling worms (no glitching). Shiny Marauder draws solid black error lines.
- Paragon Seq 1 orb lifted by one block. Hanged Man Seq 0 rainbow swirl centred on the cross. Chained Seq 0 locks and rings no longer clip the cage.
- Wheel of Fortune Seq 0: smaller dice and small wheels turning around the big one. Mystery Pryer Seq 0: ring of tall gilded mirrors. Planter Seq 0: fewer flowers (less lag) plus fluttering butterflies. Sailor Seq 0: waterspouts are now Photon effects.
- Goddess of Origin: branches fork three ways and carry real leaves. Key of Light: halos removed, coloured light everywhere. Calamity: much less smoke. Almighty: stands clear of the card, small beam on the card.
- Demon of Knowledge: a water moat all the way round with an armada of steamships (replaces the floating ghost steamers).
- Marauder error card disguise: right click view and item name now show the mimicked card (name in a slightly different shade).
- Untested in game.

## 0.2.202
- Shiny cards are now truly gold and black: every texture (pathway symbols, sprites, LOTM models) is converted to brightness only and tinted gold, so no more orange/green/yellow-green shinies, and a solid black base sits under every shiny scene so the gold has black to contrast against.
- Sword of Twilight right click redone: Twilight Verdict. Eight crescents converge on where you look, a colossal blade falls, and a field of dusk lingers for ~3 seconds dragging in and burning everything inside (long cooldown).
- Warrior Seq 4 uses the Silver Sword instead of the Sword of Twilight.
- The off-hand placeholder for the ball of lightning no longer draws anything in first person (that was the flag-like thing).
- Warrior Seq 1: banners replaced with flames on the poles.
- Spectator Seq 0: the ring of cards now faces the middle.
- Father of Devils: removed the translucent shells that hid entities.
- River of Eternal Darkness: much longer, enclosed by a dark star-filled sphere, darker water, banks no longer overlap like notepads, denser dark mist, bridge parapets fixed, boatman removed, new rowboat moored in the river with a real seat you can sit in.
- Untested in game. Rest of the long list (Fool, Marauder, Sailor trident, Demon of Knowledge moat, etc.) follows in the next versions.

## 0.2.201
- Marauder (Seq 0) and Lord of Mysteries: crouch and right click in the air to open a menu of cards (Marauder: any pathway and Seq 0 to 9; Lord of Mysteries: that and the other Great Old Ones). Pick one and the card wears that card's face; when placed it shows that card's scene (and its real seats and bridge). Picking it up gives you your own card back; "Show my own face" clears it.
- Marauder scenes are calmer and easier to look at: slower stepped time, far fewer flashes and dead pixels.
- Trident fully redone: wrapped shaft, finned collar, a long leaf-bladed middle prong and two sweeping barbed side prongs.
- Arbiter scales: one pan now holds a heart and the other a proper feather (the heart against the feather of truth).
- Chained: each padlock and its chain are one pendulum, hooked through the shackle and swaying together.
- Seq 0 is now clearly different from Seq 1 for: Monster (giant dice and a coin fountain), Planter (many more, smaller flowers), White Tower (ring of lesser spires and orbiting books), Sailor (waterspouts and a great whirlpool), Justiciar (a court of columns and a falling gavel), Corpse Collector (a rising graveyard and streaming souls), Door (a ring of coloured doors), Mystery Pryer (a great watching eye sweeping a beam) and Prisoner (a vast cage with a bound figure).
- Untested in game. Not done: the Demoness chest change on players (needs player-model work), which I'd like to talk through first.

## 0.2.200
- God Almighty and the Goddess of Origin now stand away from the card like the White Tower. Almighty's four pillars reach the ground. The Goddess's tree is vastly bigger, with massive forking limbs, leaves of many colours, hundreds of glowing lights, shafts of coloured light and fireflies.
- Father of Devils: a dark dome, a great shell of hellfire round the maw, heavy chains binding it to the ground and a crown of horns.
- Calamity of Destruction: the meteors hang in the sky instead of falling, and the ruin is full of burning roses, a giant bloom, blood petals and craters (more Demoness).
- Key of Light redone: the veil and crystals are gone; seven spectrum halos turn up the length of the great key with beams of every colour.
- Lord of Mysteries: the white platform and the great door are removed.
- River of Eternal Darkness: much longer and wider, a real stone bridge you can walk over (a hollow arch for the boat), a rowboat with a lantern and a hooded boatman drifting downriver, a darker mist, and a small beam above the card so you can find it.
- Untested in game.

## 0.2.199
- Secrets Supplicant / Hanged Man: Seq 0 cross now stands back from the card so the card can be picked up, the grey curtains (white lines and eyes) are gone, the rainbow floor is much bigger and the pathway symbol hangs in front instead of inside the cross; Seq 1 loses its big orb; the Seq 2 hanging symbol is higher.
- Demoness: wings and thorn vines removed; now a scene of beauty and witchcraft with a tall rose-framed mirror, witch hats, a broom and perfume bottles.
- Apothecary / Moon: the dark dome is filled with many more stars, glowing mushrooms and drifting wisps.
- Warrior Seq 1 is now a dawn (pale sky, golden light, a ring of upright swords and banners) so it no longer mirrors Seq 0's dusk.
- Abyss Seq 0: the wall spirals spin in place instead of drifting around the edge.
- Lawyer Seq 0: far fewer, smaller spikes; Lawyer Seq 2 orbits little crowns instead of shards.
- Darkness fog now covers the whole floor and is a little darker.
- Fool: Seq 1 table is bigger with candles and inlay; Seq 0 hall is half as large (matching the other Seq 0 scenes).
- Spectator Seq 0: the old floating cards are gone. A quill now writes every card in the game once each (no repeats); they fly into a ring of small floating cards, then a wave of brilliant light clears them and it starts again.
- Untested in game. Still to come: Marauder, the Seq 1/0 look-alike pairs, Arbiter scales, Chained locks, the Great Old Ones.

## 0.2.198
- Every shiny card is now gold and black only: all colours are mapped by brightness onto black, deep gold, gold and pale gold (no more white or stray colours on shiny Rare, Epic, Legendary and Mythic cards).
- Sword of Twilight: the strike is one big crescent; the right click is now Dusk Rend: three crescents (an upright one between a rising and a falling stroke) fan out, then the dusk detonates where the middle one ends (area damage and a burst of crescents).
- Feathered Serpent coils further from the body; the storm ball on your hand is much smaller and the storm cloud is lighter and cleaner.
- Airship tail fins are attached to the hull.
- Seq 4: Apothecary's moon is gold on shiny, the Corpse Collector skull is gone, the Prisoner's lock and chain bob together, the Seer is now a scrying crystal ball on a tripod, and the Warrior swords use the real sword models.
- Untested in game.

## 0.2.197
- Sword of Twilight: both slashes (the strike and the right-click wave) are now a proper upright crescent moon, tilted like a sword stroke, turned to face you and cutting through the target, instead of the sideways billboard. The strike is a crossing pair.
- Ball of Lightning: the lightning in the storm is much thinner, the ball on your hand is smaller, and the off hand no longer shows a flat item picture.
- Feathered Serpent coils further away from the body.
- Hanged Man (Seq 0): the floor under the cross is a swirling rainbow instead of plain red.
- Airships rebuilt: smooth belly-shaded envelope, swept tail fins, wooden gondola with windows, engine nacelles with propellers; the gunship now hangs small armoured turrets under the hull instead of big boxes.
- The steamer is now an iron steam warship: armoured hull and ram bow, citadel, raked funnels, twin gun turrets, tripod mast. No flag, no paddle wheels.
- Demon of Knowledge: airships fly in separate tiers and can no longer pass through each other.
- Untested in game.

## 0.2.196
- Demon of Knowledge now has far more vehicles than the Paragon: three phantom train lines (a new inner ring), six airships, two gunships and two ghost steamers sailing low over the floor.
- Every one of them phases in and out of reality on its own beat: it solidifies, flickers, fades out entirely and returns.
- Untested in game.

## 0.2.195
- Secret Supplicant 0 (Last Hope) redrawn from your screenshot: a vast black upside-down cross with iron bands, end caps, a rune plate and red runes scratched over it, chains across the body, on a black stone mound, your own skin hanging on it. Paragon 0 gets two more airships (a sister to the first and a great gunship riding high with five turret pairs and a dorsal gun). Demon of Knowledge gets a second outer rail line with an armoured gun train, two more airships and a gunship overhead.
- Uniqueness: the Writhing Shadows tentacles now rise out of the ordinary shadow under your feet, short, fading out about a block off the ground (so none while flying) and in the dark; at night they reach farther out and there are about twice as many. The ball of storms and the Twilight sword strikes and wave are now this mod's own Photon effects (storm_ball, twilight_hit, twilight_wave). The Feathered Serpent is rebuilt with all-white plumes, a bigger horned fanged head with a halo of plumes, and now bursts up out of the ground and strikes upward from below. The Wheel of Fortune is rebuilt as a gilded double-rimmed wheel with a blue gem band, a star and carved spokes. Untested in game.

## 0.2.194
- Holograms: Warrior Seq 1 and Seq 0 swap swords (Seq 1 now has the Swords of Dawn, Seq 0 the Swords of Twilight). Darkness: far fewer stars, fewer star colours, and the dome is a solid shell from outside again so it hides whatever stands in it. All dark domes shade the ground from outside again. Mystery Pryer's big lantern is replaced by a great scrying sphere in a gimbal of turning rings. Secret Supplicant 0: the big black sphere and its rings are gone; the Last Hope scene is now an upside-down cross of squared timber on a mound with the hanged figure drawn from your own skin, arms nailed out along the bar, chains and bleeding nails.
- Uniqueness items, rework: new worn models (shadow tentacles that fade with your shadow, a feathered serpent with long plumes, a proper wheel of fortune, a bandaged thorn doll, a ball of storms in the hand) drawn depth-tested so they no longer show through blocks; the Mini Sun is a Photon flaring sun on your shoulder. Sword of Twilight: hit particles and a right-click crescent wave. Shadows: spikes of shadow impale caged foes, the cage lasts 12 s and the shadow lasts longer. Suns: spawned farther out, farther apart and bigger. Ball of Lightning: it is thrown, then swells into a 14-block storm sphere; carrying it fills your off hand so no shield fits. Feathered Serpent: a serpent strikes everything near you (you too). Wheel of Fortune: the boon or bane lands only when the spin stops. Doll: a doll model climbs down and walks to the nearest foe before binding them. New icons are still the earlier ones. Untested in game.

## 0.2.193
- Fixed a crash when a worn Uniqueness was held or worn (the worn visuals fetched two render buffers at once; each box now fetches its own). The Uniqueness items now have their own icons (sun, lightning ball, feathered serpent, wheel, doll, shadows) instead of the old weapon art. Untested in game.

## 0.2.192
- Uniqueness items stage 2. Writhing Shadows (G): the shadow under your feet floods out to 20 blocks and every foe in it is caged (held still 8s, 6 damage). Bandaged Doll (G): the doll leaves your shoulder, a trail walks to the nearest foe within 24 blocks and binds them in place for 10s (no cooldown if nobody is near). Ball of Lightning (G): throws a ball that swells into a 14-block sphere of storm for 4s, striking and flinging everything inside; while it is carried, shields block nothing. Allies and tamed pets are skipped by the cage and the doll; the serpent still hits everyone. The doll and storm are particle effects, not entities. Untested in game.

## 0.2.191
- Uniquenesses are now worn items, not weapons. Put one in the Uniqueness curio slot and press G. Stage 1: Eyes of the Door burst damage 80 to 320; Quill of Alzuhod opens the Seq 1 Storytelling screen; Feathered Serpent (Death) shows coiled at your feet and G deals 25 to everything within 8 blocks, you and allies included; Wheel of Fortune (Monster) is a wheel at your back that spins on G and lands on a random boon or bane; Mini Sun (Sun) floats over your right shoulder and G spawns three flaring suns (ahead and either side); Sword of Twilight is a real sword (netherite damage) with a new sprite. Writhing Shadows, Ball of Lightning and the Bandaged Doll are visible when worn but their abilities (shadow cage, lightning storm, doll binding, no shield) come next. The Shadow Shield no longer appears. Old 3D weapon models replaced by icons for now. Untested in game.

## 0.2.190
- Numismatics coins stacked to 99 because LOTM only lifts the 99 cap for a private list of its own items. The six Numismatics coins are now added to that list at startup, so they get the same 9999 stacks (and saving) as the LOTM coins. Untested in game.

## 0.2.189
- Selling bin: a bronze coin is now worth the same as an emerald (100 bin units), silver 1000, gold 10000, keeping the 1/10/100 coin ratios. Untested in game.

## 0.2.188
- Starcatcher selling bin can pay out in LOTM coins: added a selling_bin_currencies data map (bronze 1, silver 10, gold 100, the same ratios as the coins). Create: Numismatics coins (spur, bevel, sprocket, cog, crown, sun) now stack to 9999 instead of 64, so converting a big purse no longer fills the inventory. Untested in game.

## 0.2.187
- Hologram fixes from your screenshots: found the real cause of the Spectator cards "stuck in the ground" (flat sprites and cards ignore the model position, so they all drew at the centre) and fixed the same bug that put a glow blob at the middle (on every chair, the airship and the steamship); Spectator 0 has no paper any more, the real quill writes in the air in glowing ink and each finished card is a real floating card; Secret Supplicant 0 is rebuilt after the real Sacrifice Authority: Last Hope (a great cross on a mound, a mercury likeness nailed to it, chained and bleeding, inside grey curtain walls with eyes); Savant 0 is much smaller and more spread out with a solid blue lake, a tiny steamship and a smaller airship; train cars no longer clip together; the trident has a big broad middle spoke; chair backs are shorter to match the seat and Fool 1 shows the sitter's pathway over each chair again; the chained locks no longer bob; Hanged Man 2's symbol is higher; Sleepless and Eternal Darkness fog are thin and cover the whole ground. Untested in game.

## 0.2.186
- Hologram fixes, batch 4: Paragon 0 gets a lake in the middle with a paddle steamship cruising it, a bigger outer track run the other way by an armoured gun train with long cannon, a more detailed airship (fins, propeller, rigging, windows, flag) and locomotive (cowcatcher, headlamp, dome, bell); Demon of Knowledge now has a train on a track and two airships circling its brass armillary; Spectator 0 writes with the real Quill of Alzuhod item art and its finished cards, plus nine more standing ones, are real tarot cards from the packs floating round the area (display only, not pickable); Fool 0 loses the dome, the ring of tall columns and the eye rosette, leaving just the hall floor, table and chairs; the hall chairs are rebuilt (turned legs, cushion, arched slatted back, scrolled arms, finials); Fool 1's eight chairs are now real seats you can sit in; Lord of Mysteries gets the same chairs as real seats, a table open in the middle so the card is easy to pick up, symbols lifted out of the table, the white blob removed, and four stone-arch doors pushed out to the edge. Untested in game.

## 0.2.185
- Hologram fixes, batch 3: Father of Devils is now an Abyss-style black maw over a whirling pit wrapped in tentacles, no skull or horns; Key of Light spreads all seven colours everywhere (bands round the sky, coloured rings, spires, sparks and motes); Calamity of Destruction is a big meteor with a fire trail high above, no more orb, and its symbols moved clear; Demon of Knowledge redone as a brass armillary with turning gears and bookcases, in the Paragon style; God Almighty gets a second layer of wings and a spiked crown; the Sailor trident is rebuilt (wrapped shaft, crescent bowl, barbed prongs) and the rain is less than half as dense. Untested in game.

## 0.2.184
- Hologram fixes, batch 2: Hanged Man 0 now uses the look of Sacrifice Authority: Last Hope (a mercury-silver cross-bodied figure with circling crosses) instead of the black pillar; the Warrior's side swords fan out much wider; chained padlocks rebuilt as proper padlocks (boxy body, U shackle, keyhole) with the chain meeting the shackle; the Eternal Darkness river is redone as a continuous winding river with foam banks, current streaks, rocks and a stone arch bridge. Untested in game.

## 0.2.183
- Hologram fixes, batch 1: Wheel of Fortune and God Almighty lose the white square; Twilight Giant lights are now fixed in the world, not on your camera, and its sky is more twilight; Sleepless fog is thinner and lower; Hanged Man 2 symbol raised; models no longer show through pathway symbols; Warrior 0 uses dawn swords; the Scales' chains now end exactly on the pans; Abyss sky spirals no longer follow the camera and its floor has far less clutter; Eternal Darkness mist is paler, the river is wider and continuous with bank/flow streaks; Goddess of Origin is bigger, in world-tree greens and gold, its sun is real geometry and its symbols sit clear of the canopy; God Almighty symbols always face outward while orbiting; apothecary potions spread across the sphere. Untested in game.

## 0.2.182
- The supernova finale is now a real rendered effect instead of particles, modelled on the Sun manifestation: a dome of light about 30-45 blocks across that bursts open in half a second (white core, cream and gold halo), four coloured shock spheres (orange, magenta, blue, teal) racing out to over 100 blocks, and 52 long rays of light 90-260 blocks long shooting into the sky and along the ground. Additive and depth tested, so the ground cuts it off. Sent to everyone within 400 blocks. The old huge sprite flash is removed. Untested in game; size and brightness are guesses against your screenshot.

## 0.2.181
- Eyes of the Door redrawn to match the reference: two round, overlapping silver metal eyeballs with sphere shading and a travelling glint, bright blue irises with a dark limbal ring, a small keyhole pupil and twinkling stars in the iris. Untested in game.

## 0.2.180
- The Door Uniqueness is now the Eyes of the Door, a pair of animated pixel eyes (void sclera, nebula irises with keyhole pupils, brass lids, twinkling stars) instead of a sword. There is a new Curios slot, Uniqueness, that every Uniqueness item fits in (tag curios:uniqueness; needs Curios). Wearing the eyes puts glowing eyes on your face. A new keybind, Door (default G, rebindable), replaces right click: it picks the creature in your crosshair (48 blocks) and starts the supernova; cooldown 15s. Holding the eyes in a hand works too. The sword, its combo, thrust and swing effects are gone.
- The supernova is much bigger: 11 doors on a 9-block ring, a 16-block field of 170 big swelling stars, star-sized nova flashes and shock shells (up to 180 blocks across), 280 huge nebula clouds, white screen flash for everyone within 96 blocks, 80 damage in a 12-block radius. Untested in game; needs Curios installed for the slot.

## 0.2.179
- Rapier of the Door: the explosion is now literally stars going supernova. Spiky glowing stars (blue-white, yellow, orange, red) bloom across the whole area and swell and flicker; a big one forms in the middle. They collapse and go supernova one after another, rippling out: a spiky nova flash, an expanding shock shell, hot ejecta and drifting gas clouds. Then the central star goes nova: a screen-filling flash, four huge shock shells, a nebula of coloured gas thrown across the area that lingers for seconds, two jets of stars from the poles, pinwheels and shells. New particles: spiky star, nebula gas. Untested in game; likely heavy on particles.

## 0.2.178
- Rapier of the Door: sword sits a little higher in the hand and is 15% bigger. Doors are upright and face the target (before, every door got the same rotation, so they showed edge-on or odd) and now last the whole ability (they close at the finale). The burst is now a chain reaction: after the fill, 110 star clusters detonate one after another rippling out from the middle (each a flash, a shockwave ring and a pop of stars, with the target slowed so it stays put), then the finale: a white-out, six shockwave rings and the big shells, pillar and pinwheels. New particle types: big photon flash and shockwave ring. Sword swings leave a black door-seam line packed with blue stars instead of the coloured puff. Untested in game; door facing may still need a nudge.

## 0.2.177
- Rapier of the Door, late-game scale: right click no longer teleports you (the real cause was the LOTM door's default mirage mode pulling its creator in; the doors are now set to a mode that does nothing unless someone sneaks into them). Nine doors on a wider ring, a much denser star stream and a thick fill that builds for 3 seconds, a 12-tick implosion with a rising hum, then a huge blast: blinding core, thick cloud, four star shells, five crossed shockwave rings, a pillar of stars through sky and ground, eight spinning pinwheel arms, and two more shells (the last with a second sonic boom). Radius 7 blocks, 60 damage, strong knockback and lift, 10s cooldown. Star particles now live 3-5 seconds and the swing puff is 26 stars. Untested in game; numbers are guesses and it may lag.

## 0.2.176
- Rapier of the Door: the burst is now a photon explosion with no vanilla explosion, flash or fireworks. Stars are pulled inward for a moment, then a dense bright core and three coloured star shells blast out, followed by a spinning pinwheel of star arms, an upward fountain and three shockwave rings. Untested in game.

## 0.2.175
- Rapier of the Door: held like a normal sword now (vanilla sword display and first-person position, no crossbow arm pose). Right click no longer teleports you (the LOTM doors are told not to teleport anyone). Star particles are now multicoloured (gold, magenta, cyan, violet, mint, coral, white), travel properly out of the doors, and the swing releases a single puff instead of a stream. The explosion is a big bang: flash, explosion, two spherical star shells, fireworks and three shockwave rings. Untested in game.

## 0.2.174
- Rapier of the Door: the seven doors around the target are now LOTM's own door entities (spawned by reflection, removed at the burst); the particle doors only show if LOTM is missing. Door facing, size and what the doors do on their own are unverified. Untested in game.

## 0.2.173
- Rapier of the Door: right click is now an entity ability instead of the dash. Right click an enemy and seven doorways (arched star outlines with a void inside) open in a ring around it, stars pour out of them and fill the space, then burst for 14 damage and knockback in a small radius (8s cooldown). The doors are drawn with particles, not the LOTM door entity, so it works without LOTM. Untested in game; numbers are guesses.

## 0.2.172
- Rapier of the Door: texture animation made cheaper (no interpolation, slower frames) to fix lag; star particles redrawn as bright blue-white dots with a glow so they are no longer grey; sword smaller in hand. Untested in game.

## 0.2.171
- Rapier of the Door: drawn as a flat two-sided plane (the old extruded 128px model was thousands of faces, thick and slow); bigger in hand; held further in front and toward the middle in first person. Replaced all its particles with tiny glowing star dots (far fewer of them) and its custom sounds with vanilla chimes and sweeps. Untested in game.

## 0.2.170
- Test: the Door Uniqueness is now the Rapier of the Door, a 128px animated pixel sword (twinkling stars, drifting nebula, pulsing light). It attacks fast with long reach and light damage, builds a 3-hit combo (x1.0, x1.25, x1.7) with a chime on the finisher, has a first-person thrust instead of the swing, star trails, and its own sounds. Right click (not sneaking) is a short door-dash lunge that damages everything in its path (2.5s cooldown). Sneak + hold right click still takes the Uniqueness in. Untested in game; numbers are guesses.

## 0.2.169
- Wheel of Fortune Rifle rebuilt as a detailed steampunk rifle: perforated brass shroud with the barrel and rods visible inside, steel receiver with rivets and a window onto working gears and a glowing tube, leather stock and grip, copper pipes, ring sight, brass-shell magazine, the wheel and the coiled snake. Untested in game.

## 0.2.168
- The weapon meshes now carry the same lighting, reflections, bump detail and ambient shadowing as the artwork renders (baked into the vertex colours), and are slightly slimmer, so they look like the pictures instead of flat blobs.
- Untested in game.

## 0.2.167
- First-person weapons are about 40% smaller (they filled the screen); removed the checkered glow pattern on the longsword and other blades (glow now pulses in a smooth wave along the weapon).
- Untested in game.

## 0.2.166
- The nine Uniqueness weapons are now smooth meshes (marching cubes from the same shapes as the artwork) with smooth shading instead of blocks; glow, twinkle, flames and the moving parts are animated by code.
- Longsword of Twilight remodelled to match its icon (short, broad orange blade, gold guard).
- Sizes set from real lengths (about 2 blocks for guns and swords in third person, smaller in first person); blades and the spear are held pointing forward and up.
- Untested in game.

## 0.2.165
- Models rebuilt at double the resolution so edges are much smoother, and the Longsword of Twilight is now a 3D model too.
- Sniper and Wheel of Fortune rifle are held out in front with both arms raised (crossbow pose); the bow is held up in the bow pose.
- All weapons are larger in third person and smaller in first person; blades and the spear now point forward instead of straight up; the Shield of Shadow is bigger.
- Untested in game.

## 0.2.164
- Recomputed how the Uniqueness weapons and the Shield of Shadow are held (third and first person) from each model's grip point and axes: guns point forward with the grip in the hand, blades and the spear point forward and up, the bow stands upright, the shield faces forward on the forearm. Untested in game.

## 0.2.163
- Uniqueness items: sneak + hold right click to take one in; normal right click now uses the item normally. Enchant glint removed. Held 3D weapons are scaled up (guns, spear, bow, greatsword), and the Shield of Shadow is much smaller. Hold positions are still guesses. Untested in game.

## 0.2.162
- Uniqueness items reworked into 3D models (untested in game, built by CI only): Starry Sniper Rifle (Door), Sword of Shadow (Hanged Man), Spear of Sun and Justice (Sun), Storm Katana (Tyrant), Great Bone Sword with green fire (Death), Wheel of Fortune Rifle with a snake (Monster), Chained Bow (Chained). The Quill of Alzuhod and Longsword of Twilight stay as they were.
- The 3D models use animated colours (twinkling stars, flickering fire, lightning, glow) and moving parts (spinning wheel and sun, bow string pulls back while you hold, sniper bolt cycles).
- Holding the Sword of Shadow puts the Shield of Shadow in your off hand; putting the sword away removes it.
- Removed the old cane, cloak, sun, ball of storms, book, die and bandaged figure items.

## 0.2.161
- Monocle removed. Nine Uniqueness items in its place (creative, Tools tab; hold right click 5s to take one in through LOTM): Cane Inlaid with Star Speckles (Door), Quill of Alzuhod (Visionary), Cloak of Shadows (Hanged Man), Radiant Miniature Golden Sun (Sun), Ball of Storms (Tyrant), Illusory Book of Coiled Feathered Serpents (Death), Longsword of Twilight (Warrior), Six-Sided White Die (Monster), Tiny Illusory Bandaged Figure (Chained). Untested in game.

## 0.2.160
- The Monocle now renders over your eye while worn. A worn monocle can't be used; hold right click from your hand to accommodate. Untested in game.

## 0.2.159
- New testing item: the Monocle (creative, Tools tab). Sneak + right click wears it on your head; hold right click for 5 seconds to accommodate it as the Marauder Uniqueness (a sequence 0 characteristic) through LOTM's own accommodation. Untested in game.

## 0.2.158
- Black Emperor (and Anarchy) crown rebuilt from the Ace of Swords: a floating band of dark silver diamond plates with pale gems, points above and below and small spikes between.
- Dark domes: the ground shade now reaches the wall, no more ring of grass at the edge.
- Arbiter scale: the pan rims lie flat round the pans (they were standing up and down); small lugs where the chains meet the rim.
Untested in game.

## 0.2.157
Great Old One redesigns (batch 3, untested in game):
- God Almighty: five-tier golden dais with four pillars, a huge five-ring armillary round a blazing sun, wings and rays of light, five symbols circling.
- Father of Devils: giant horned skull above the whirling pit, ring of black spires, braziers, hanging chains and eyes.
- The Anarchy: everything 1.5x bigger, with falling rubble, lightning and more broken pillars.
- Lord of Mysteries: huge door of light over a round bronze table with chairs, six more coloured doors, all 22 pathway symbols circling, bigger glitching Fool symbol.
- Eternal Darkness: much wider river with pale banks, moonlight and ripples, gravestones, dead trees; floating figures and souls removed.
- Goddess of Origin: a giant mystical tree with twisting trunk, forking branches, glowing blossom canopies, roots and fruit of light.
- Demon of Knowledge: towers of books, great tome on a lectern, giant horned eye, rune rings, page whirlwind.
- Calamity of Destruction: bigger core, erupting spikes, falling meteors, many more and larger shards.

## 0.2.156
- Savant 1: a steam train (locomotive, tender, two carriages) circling a ring of track with smoke; Savant 0 adds an airship circling above. Gears and armillary replaced.
- Fool 1: bigger, with a round bronze table, eight tall carved chairs and fluted columns.
- Black Emperor 0: space distortion round the throne (warped expanding rings, stepped tears, flickers).
- Abyss 0: swirls and cracks across the whole floor out to the dome, and swirls turning in the sky.
- Secrets Supplicant 0: the great bound figure from your picture (arms along a beam, chains, gold and red bands, black tentacles, hanging block, red grid sky). Untested in game.

## 0.2.155
- Arbiter: scales use one gold (no blue and red pans); seq 0 uses silver swords too.
- Prisoner: the padlocks are round and link-like to match the chain, and each hangs straight from its chain.
- Planter 1: the flowers stay put.
- Mystery Pryer: the hanging lantern rebuilt as a six-sided lantern with posts, glass, cap and flame.
- Spectator 0: crystals removed; a quill writes on a card, each finished card flies off and floats round the area.
- Warrior: 1 and 0 now sit in a twilight sky; swords are the Sword of Twilight, crossed closer together.
- Hunter: spears are flipped point-down into the ground; the symbol is higher.
- Assassin: star removed, mirrors replaced by rose-wound thorn arches; seq 0 has many more roses and chaos (tumbling roses, writhing vines).
- Apprentice 0: a small galaxy turning above the door. Untested in game.

## 0.2.154
- Hanged Man: the cross and flower are drawn fixed in space instead of Photon billboards that flickered and followed the camera; the legendary's camera-facing circle is now crossed rings.
- Savant 4: the gears were tipped flat; now three gears really mesh, turning opposite ways at the right speeds.
- Corpse Collector 4: skull drawn as a model (no flat rectangles), flame dimmer.
- Moon: seq 4 has a cratered moon instead of the big potion; seq 1 loses the pink camera-facing sphere and gains a ring of moon phases, a small cauldron, bats and falling stars; seq 0 has fewer potions and a moon-phase ring.
- Monster legendary: symbol on the wheel's hub (it was hidden inside the backing), and prizes (coins, clovers, gems, cards) on the wheel.
- Sailor trident rebuilt with three real prongs, barbs, grip rings.
- Clouds now always sit up in the sky so they cannot cover symbols.
- Error: glitch ground patches are purple and black, one per third of the circle so they never overlap.
- Sleepless: fog moved up and thinner, many more round stars. Untested in game.

## 0.2.153
- Hermit legendary: the lanterns had huge, nearly opaque purple glows (the biggest 7 wide) that formed a ball washing out the symbol. Glows are now about half the size and half the strength. Untested in game.

## 0.2.152
- Epic cards: seq 2 was missing two of the circles seq 3 had. It now has all of them plus one extra outer ring. Untested in game.

## 0.2.151
- Monster epic: removed the little wheel that clipped through the symbol; dice now tumble round it instead. Untested in game.

## 0.2.150
- Warrior swords: the two crossed swords were positioned before the model was turned to face you, so they only crossed from one direction. They now turn with the hologram and always form the X behind the upright sword. Untested in game.

## 0.2.149
- Lord of Mysteries: grey fog with colourful drifting lights, a ring of seven Lord of Mysteries doors in different colours, the glitching Fool symbol with the three pathways; doors and colours flicker when it glitches.
- Eternal Darkness: a river of eternal darkness where the dead rest, with shrouded pale figures lying on the black water, souls rising, reeds and mist. Untested in game.

## 0.2.148
- All GOO cards redesigned legendary-style: one hero model per card on a small circle, no huge dome, stars or ring of scenery. Almighty (armillary and seed with the five symbols circling), Darkness (black pool, reeds, crescent), Calamity (molten core and shards), Knowledge (open book with orbiting books), Key (key through a gate with spectrum beams), Goddess (vine-wrapped seed), Devils (horns over a whirling pit), Anarchy (tipped scales and loose crown). Lord of Mysteries stays the compact glitch stage. Untested in game.

## 0.2.147
- GOO cards sit between the old size and legendary size (radius 12, height 19, scale 2.35). Untested in game.

## 0.2.146
- GOO cards toned down to legendary-card scale (about 15-25% smaller scenes, smaller domes and spreads).
- Lord of Mysteries is now a compact stage: the glitching Fool symbol over the three pathways, with a small door of light behind. The giant hall, stair and dome are gone from it (the Fool legendary keeps its hall). Untested in game.

## 0.2.145
- Lag fix for the Fool/LoM space: about half the background pillars (plain, no carving), fewer table details, a lighter dome and sky clouds, and 60% fewer Photon particles in the fog, stars and door. Untested in game.

## 0.2.144
- Lord of Mysteries: the marble stair to the door of light is now solid, so you can walk up it (each step is one block high, so jump). Untested in game.

## 0.2.143
- Fool and Lord of Mysteries space rebuilt much bigger (hall scale 2.4): a round dome on a ring of twelve columns, far taller darker pillars standing off in the fog (none tiny), a round dark floor.
- New very long bronze table (mottled green), wider, with chairs 10% smaller and spaced well apart. The table now works like an item frame: right click with an item to set it down, empty hand or sneak to take it back (Fool only; untested).
- Brighter sky: lighter indigo dome, far nebula clouds and stronger Photon nebula. All sky stars are now round, many-coloured Photon dots instead of squares.
- Spell circle removed under Fool and Lord of Mysteries only.
- Pathway symbols now sit to the left of each chair; Lord of Mysteries shows all three (Door, Fool, Error) over the head chair, with the glitching Fool above them.
- Door of light remade from the tarot art: big dark gothic arch on carved columns, white whorls of light, black tentacles, grey marble stair; placed higher and further back.
- Bard: seq 1 sun 20% smaller, seq 0 sun higher.
Untested in game (CI build only).

## 0.2.142
- Fool hall, from your new references: a ribbed stone dome with an open eye now covers the table, on ten fluted columns joined by pointed arches. Looking up you see the eye: a bright white-teal rosette inside a blue ring of ticks and arrowheads.
- Better columns (plinth, ringed and grooved shaft, carved collar, flared capital with horned corners, a red glint) standing in the fog all around, tall and broken.
- A colourful nebula sky (blue, pink, cyan) and stars above the grey fog.
- The old square pillars and far round towers are gone, replaced by the above.
- Untested in game.

## 0.2.141
- Lord of Mysteries: the whole space now glitches now and then (a short burst about every 14 seconds, and a shorter one in between): the hall jumps, motion stutters and torn slabs of colour flash across the sphere.
- Its door of light is now smaller, further back, at the top of a stair behind the head seat of the table, and white. New model: carved jambs and a pointed arch edged in light, open leaves, a glowing white doorway with a pillar of light, a lit stair with railings.
- Fool hall, from your new references: pillars are square with recessed carved panels outlined in teal, great round stepped towers stand far off in the fog, and the fog fx gained floating red lights, a teal and green nebula high up, and drifting white ribbons.
- The last chair stays past the end of the table (moved in 0.2.140).
- Untested in game.

## 0.2.140
- Fool hall rebuilt bigger (1.9x a person) and in more detail to match the picture: a long ribbed stone table like a bridge with teal inlay, rails and lamps, tall carved chairs with gabled backs and teal edges, a tall high seat at the head, a dark stone floor with teal edging, and tall carved pillars with glowing slits. The real seats and table (sit and walk on them) are scaled to match.
- The symbol behind each chair is now the pathway of whoever is sitting in it (read from LOTM on the server and synced). Empty chairs show only a faint glow. The head seat shows the Fool's symbol.
- The last chair is now past the end of the table instead of inside it.
- Fool fog is much greyer and darker but thin enough to see through. One fog and set of lights now fills the whole sphere, with a thicker grey fog around the table.
- Bard sun is twice as big again.
- Untested in game.

## 0.2.139
- Removed the "click the Great Old One again" hint; it just waits for the second click.
- Untested in game.

## 0.2.138
- Confetti from the popper lasts about five times as long and falls more slowly.
- Great Old One cards need a second click: the first click shakes and peels the gold away to show the pathway back, and it waits there until you click it again to turn it over.
- Pack cards are now handed to you as you flip them, not all at once when the pack opens, so the Great Old One advancement no longer spoils the reveal. Cards you have not flipped are given when the screen closes, when you open another pack, when you leave the game, or after three minutes.
- Untested in game.

## 0.2.137
- Warrior swords now match the icon: three swords of our own model, the big one upright with its point at the ground and its pommel up, and two behind it crossed in an X (rare and Twilight). They no longer use LOTM's blocky sword model.
- Fool hall: the fog is much thinner and the hall (dome, pillars, table, seats) is lighter, so you can see the room.
- Bard sun is about twice the size.
- Untested in game.

## 0.2.136
- Abyss rare redone to match the others: a glowing void with a bright rim and thin tendrils at the same size, instead of a large solid black ball with huge tendrils.
- Corpse Collector's rising wisps were showing as white rectangles; they now use our own soft wisp texture.
- Untested in game.

## 0.2.135
- The mod now ships its own Photon effects (assets/astral_world/fx, generated by tools/fx/gen_fx.py from LOTM's own emitters) and plays them through LOTM's FX system: Bearing of Sins (burning red cross, Hanged Man flower, red light, petals), the Fool hall fog, Seer fog, Sleepless fog, Eternal Darkness mist, Key of Light motes, Calamity embers and Anarchy sparks.
- Each hologram falls back to its old drawn version if LOTM or the effect cannot be loaded.
- Untested in game; these effects could not be previewed, so expect to need tuning.

## 0.2.134
- Rares: Corpse Collector skull and wisps redone, Apprentice door redone, Bard back to the music note, Abyss no longer has a ground effect, Criminal swirl removed.
- Warrior uses three upright Sword of Dawn models (rare and Twilight); hunter keeps its fire and gains a ring of spears around it.
- Nothing on the cards rotates any more; they face you and stay still.
- Arbiter scales are coloured. Mirrors are opaque with a glint. Chained locks are padlocks hanging on the chains and facing you.
- Wheel of fortune faces are no longer black.
- Hanged Man's Bearing of Sins uses LOTM's Photon sin-bearing effects plus the cross symbol (approximation).
- Fool 0 hall rebuilt at player scale: 22 real seats you can sit in, a solid table, the card lying at the head of the table, a symbol behind each seat that changes, and LOTM's nightmare fog (Photon). Lord of Mysteries' door now stands at the table's foot.
- Untested in game.

## 0.2.133
- Bard sun now plays LOTM's own flaring_sun Photon effect inside the hologram (rare and legendary), through LOTM's ClientFX. It is killed when the hologram is no longer drawn. If LOTM or the effect cannot be found it falls back to the texture-built sun.

## 0.2.132
- Monster wheel of fortune now has a solid backing disc, so clouds and fog no longer show through it.

## 0.2.131
- Great Old One holograms are smaller (about 70% of their old area), and the small symbol-only form of a GOO card now shows all of its pathway symbols instead of just the first.
- Lord of Mysteries: a full Sefirah hall with the Fool's symbol glitching through the other pathways and a door of light at the far end.
- God Almighty: round golden dais, great gold armillary round a seed of light, five tall stelae each carrying a symbol.
- Eternal Darkness: a winding river of black water with pale lights drifting down it, ripples, mist and black reeds.
- Calamity of Destruction: the core is now a real 3D molten sphere in glowing shells with cracks, so it no longer turns to face the camera.
- Key of Light: an enormous golden key standing through a vertical gate ring, a spectrum of seven beams fanning out, colour shards circling.
- The Anarchy: broken scales tipped and half sunk, a cracked crown spinning above, snapped pillars and splitting law tablets.

## 0.2.130
- Justiciar scales: far more detailed model (stepped plinth, fluted column, scrolled beam, chains, dished pans with an orb of law and a feather) used at every step.
- Hanged Man: the Bearing of Sins is now the thick red cross with a burning white cross at its heart over the flower symbol, as in LOTM; epic Hanged Man hangs it from a chain with falling sins and shackles instead of orbiting copies.
- Hunter: much less fire, more spears (a field of planted spears; at Seq 0 also axes, swords and circling spears).
- Demoness: no spiky ball; mirrors are now tall arched standing mirrors.
- Prisoner: cage removed.
- Warrior: three swords like its icon (rare and legendary).
- Bard sun: rebuilt from LOTM's flaring_sun fx emitters using LOTM's own particle textures.
- Monster wheel: only spins (no turning), spins for a few seconds then rests, smaller; dice much smaller everywhere.
- Fool/Seer 0 hall: dark teal stone table with ladder lines and rail posts, tall dark chairs, square pillars, thicker fog, many faint distant lights, a pathway symbol behind every seat that changes with its occupant, giant eye removed.
- Fog texture softened so it no longer shows hard rectangle edges (Seer fog screenshot).
- Sleepless fog spread out across the whole dark dome.
- Corpse 0, Mother 1/0 and Error 0 calmed down (fewer props and particles); Error symbol smaller, fewer windows, plus chromatic ghosts, dead pixels and a stuttering loading bar.
- Apothecary 0 scaled down.
- Rare models no longer all spin.

## 0.2.129
- Rare holograms (Seq 5 and 4) all get a bespoke small model instead of a spinning sprite on a beam: Apothecary potions and bat, Apprentice opening door, Arbiter scales, Bard mini flaring sun, Corpse skull in green fire, Criminal void with tentacles, Lawyer crown, Monster wheel of fortune, Prisoner padlock and chains, Reader white tower, Sailor whirlpool, Savant meshing gears, Hanged Man Bearing of Sins, Seer armillary, Sleepless night orb, Spectator crystal, Warrior sword. Demoness, Planter, Hunter, Marauder and Mystery Pryer keep theirs.

## 0.2.128
- Epic Black Emperor and Abyss no longer carry miniature legendary set pieces (throne, void orb and tentacles); epic Sailor loses its mini ocean, epic Monster loses its dice, epic Mystery Pryer's eyeballs are now vfx eyes.
- Bard sun reworked again to match the Photon flaring sun: compact, soft orange body with a bright heart, tiny ray flecks, wisps, embers. No big spikes.
- Hanged Man: the model is replaced by the Bearing of Sins symbol (LOTM's red cross and flower, double sided and glowing), also used for the epic orbit.
- All night domes (Moon, Abyss, Black Emperor, Hanged Man, Sleepless, Great Old Ones) are darker, and Black Emperor and Hanged Man now have stars.
- Apothecary 0: many more potions in three rings, a blood cauldron, vampire bats, blood rain and a blood ring.
- Monster wheel of fortune rebuilt: thick rim with chasing bulbs, spokes, hub, ticker that flicks, spins up and lands on a segment, coins, clovers and cards.
- Error: far fewer binary digits.
- Spectator 0: the big star on the floor is gone.
- Fool 0 hall restyled after the reference: dark teal sky and storm clouds, grey-teal fog rolling along the floor, pale light round the Fool's seat.

## 0.2.127
- Great Old One card fronts and backs: wedge colours fixed (Warrior orange, Corpse green, Door blue, Error grey, Bard yellow, Spectator light blue, Sailor dark blue, Hanged Man dark red, White Tower white) and every wedge is now centred on its symbol, with symbols spaced evenly in a triangle/pentagon.

## 0.2.126
- Bard sun is now a pure-vfx flaring sun like LOTM's, not a model: orange body and bright heart, ray halo, drifting sacred-flame wisps, prominences that flare out and fall back, kindling streaks pulled inward and drifting embers. `sun_halo` model removed from the scene.

## 0.2.125
- Fool 0 rebuilt from the books' Sefirah Castle: an ancient mottled bronze long table with ten high backed chairs a side and one at each end (the Fool's at the head, taller), crimson constellations glowing on the chair backs, tall stone pillars around it.
- Thrones no longer float: the seat sits on a solid block and the arms are supported (Black Emperor, Death and the Lawyer thrones). The Corpse Collector throne is empty.
- Bard uses the LOTM flaring sun model instead of a ball. Hanged Man uses the LOTM hanged man creature model everywhere.
- Error: when it glitches into another pathway's symbol it now stays that symbol for a while and then glitches back.
- Assassin and Planter scenes are now fields of many small flowers instead of one big one (and a few small flowers at Rare). Rare Mystery Pryer is a small flat eye effect instead of a 3D eyeball.
- Seq 2 keeps only the dashed ground circle (no extra ground effects). Stars in dark domes are smaller. Apothecary 0 moon is a small, far away flat full moon.
- Arbiter's scales stand on the ground. Arbiter and Warrior swapped sword models. Savant 1 is smaller. Mystery Pryer 1 lantern no longer covers the symbol. Prisoner 0 symbol is solid and in front of the cage. Spectator 0 eyes replaced by thought crystals. Reader 0's tower moved behind the card so the card can be seen and clicked. Not tested in game.

## 0.2.124
- Great Old One holograms rebuilt from scratch, one new scene each, none reusing the Seq 0 scenes: Lord of Mysteries (a standing ring of all 22 symbols with a fog whirl and an eye), God Almighty (a seven step golden sanctum, pillar of light, halo), Eternal Darkness (a black sun with accretion discs and black spires), Calamity of Destruction (a molten core bursting into shards, shockwaves, cracks), Demon of Knowledge (ring of bookcases around a giant open book), Key of Light (a crystal splitting into seven beams behind mirrors), Goddess of Origin (a vine wrapped seed with roots), Father of Devils (two vast horns over a whirling pit, hellfire braziers, eyes) and The Anarchy (a split slab of law with spinning tablets and a breaking floor).
- Great Old One card fronts redesigned: an eye shaped window of swirling colour in the group's triangle or wedge split, dark grey fog, an eye in place of the number, a ribbon name banner. The revealed card no longer carries the gold shine from the back.
- New advancement "Something Ancient Stirs" for pulling a Great Old One card from a pack. Not tested in game.

## 0.2.123
- Ancient One Pack has its final art: swirling colours under drifting dark grey fog, with an unbroken gold border. Not tested in game.

## 0.2.122
- Great Old One cards now hide behind the back of one of their own pathways in a pack. Click one and it shakes, turns gold, the gold peels away in strips to show its pathway coloured back (split into diagonal triangles or wedges, one colour per pathway, with trim in the same colours and a gold shine), then the card turns over with a gold flash and keeps a gold shine. Nine new backs.
- You can no longer tear a pack open and press Esc to skip turning the cards over. Esc only works once every card has been turned over and its animation has finished.
- New Ancient One Pack (creative tab and /give): 3 cards, one of them always a Great Old One. Art is a placeholder pick of three options. Not tested in game.

## 0.2.121
- Crouch-placing a card now leaves the card on the ground with its pathway symbol floating above it (no hologram scene). Not tested in game.

## 0.2.120
- Great Old Ones: a new rarity above Mythic, nine cards, one for each group of pathways: Lord of Mysteries, God Almighty, Eternal Darkness, Calamity of Destruction, Demon of Knowledge, Key of Light, Goddess of Origin, Father of Devils and The Anarchy. Each card shows its group's pathway symbols together under a crown. Pack drop: 0.1% per card in normal and shiny packs, 0.5% in rainbow packs, and a pack's own group counts 5x. Each has its own very large hologram scene with a dome. They are in the Astral Cards creative tab and /astralcards gives a white shulker box of all nine. Crouch-placing one shows just its symbol. Not tested in game.

## 0.2.119
- Crouch and right click on a block to place a card as just its pathway symbol (a floating glowing symbol, no card and no hologram scene). Right click it to pick the card back up. Not tested in game.

## 0.2.118
- Hologram round three. Flowers (Demoness rose, Planter, Mother) now have real cupped petals and real leaves and are smaller. Real eyeballs for Mystery Pryer, real dice for Monster, a real silhouette bound in chains (Bearing of Sins) for Hanged Man, scales for Arbiter epic, a flickering flame for Hunter. LOTM models: sword of dawn (Arbiter), spear of dawn (Hunter), silver sword (Warrior Seq 1), twilight sword (Warrior Seq 0). The Door legendary door now faces you. Bard has a single flaring sun and no wings. Abyss is a red and purple night with tentacles wrapping the orb. Death is an emperor sitting on the throne. Throne now has real steps and a skirt into the ground. Darker domes (Darkness, Hanged Man, Abyss, Black Emperor), Minecraft-style twinkling stars, Apothecary dark with a full moon at Seq 0, dark gray Fool fog with coloured lights (and a bronze table and chairs at Seq 0), Sailor uses wave sheets again, Prisoner has an iron cage and locks, Spectator is less cluttered, Marauder has purple and rare symbol swaps and its ground patches lie on the real blocks. Cards are easier to pick up. Not tested in game.

## 0.2.117
- Pale Emperor (Death) is green again (a clear, lighter green), keeping the bone throne, crowned skull and orbiting skulls. Thrones (Death and Black Emperor, Seq 1 and 0) are now solid: invisible collision boxes follow the steps, seat, armrests and back, so you can walk up and stand on them, and right click the seat to sit (sneak to get off). Throne holograms now turn in quarter turns so the boxes line up. Not tested in game.

## 0.2.116
- Pale Emperor (Death) is now pale bone and cold white-blue instead of green, with a bone throne, a crowned skull and a ring of orbiting skulls with ghost flames. Not tested in game.

## 0.2.115
- Hologram fixes, round two. The pathway symbol is now one solid, bright plane in front of everything (no doubling, nothing covering it). Effects are much more opaque. Uses real LOTM models where possible: the Door pathway doors, the Hanged Man cross, and the Sword of Twilight for Warrior. Fire is now real particle flame. Abyss tentacles grow from the ground. Darkness, Hanged Man, Black Emperor and Abyss domes are bigger, shade the ground, and can be seen into from outside. Arbiter is gold with new swords; Assassin, Bard, Marauder (now a server error), Monster (a real wheel of fortune), Mystery Pryer (eyes, no cone), Planter (spread-out flower), Chained, Reader, Seer, Sailor (smoother waves, no drops), Sleepless, Savant, Spectator (mind palace) and Warrior reworked. New testing command /astralcards [normal|shiny|both] gives shulker boxes of every Seq 4, 2, 1 and 0 card. Not tested in game.

## 0.2.114
- Hologram rework. Holograms no longer turn to follow your camera: everything big is fixed in the world and faces the way the card was placed. Palettes now come from each pathway's own symbol (Apprentice blue, Marauder grey, Monster silver, Black Emperor black and white, Warrior dimmer). Epic and up use the pathway symbol. Real lit 3D models: swords, daggers, chains, the tower, a flower, a trident, a door, a throne, a wheel, a harp, lanterns, gears, skull, flasks, crown and more. New flames, a real heaving sea and breaking waves for Sailor (no sun), a black night dome for Hanged Man, Black Emperor, Abyss and Darkness. Redone: Seer (eye and tarot fan), Demoness (blood moon, wings, daggers), Spectator (glass lens and light), Abyss (void and horns, no eye), Black Emperor (throne, no towers), Marauder (glitching symbol and shards, no gears), Chained (iron chains and shackles), Hanged Man (much emptier). No glow orbs. Legendary scenes are smaller. Fades are stronger so effects are not nearly invisible. Not tested in game.

## 0.2.113
- New creative tab "Astral Cards" with every tarot card (all 22 pathways, Seq 9 to 0, normal and shiny: 440 cards). Not tested in game.

## 0.2.112
- Pack rates: rainbow packs now roll Epic 75%, Legendary 20%, Mythic 5% per card. All shiny chances halved (Common 2.5%, Uncommon 5%, Rare 5%, Epic 7.5%, Legendary 10%, Mythic 20%). Not tested in game.

## 0.2.111
- Every pathway now has its own hand-built Legendary and Mythic hologram scene instead of the shared recipe (Moon, Door, Justiciar, Demoness, Sun, Death, Abyss, Red Priest, Black Emperor, Error, Wheel of Fortune, Hermit, Mother, Chained, White Tower, Paragon, Hanged Man, Fool, Darkness, Visionary, Twilight Giant; Tyrant already had one). Same large scale as the Sailor storm. Not tested in game, expect tuning.

## 0.2.110
- Sailor (Tyrant) Legendary and Mythic holograms rebuilt as a real storm: a whirlpool and waterspout on the ground, rising wave crests, rain sheets, storm clouds that light up, jagged lightning strikes, and a big spinning trident. All Legendary and Mythic holograms are now much bigger. Not tested in game.

## 0.2.109
- Fixed the holograms showing as solid white squares: the glow textures and fades are now pre-darkened because the glow blend ignores transparency. Not tested in game.

## 0.2.108
- Placed tarot cards: right-click the top of a block with a card to put it down; right-click it again to pick it up. Rare and better cards (Seq 5 and up) show a custom-drawn hologram above them (no vanilla particles): Rare is a small spinning symbol, Epic adds a beam, a ring of rune medallions and orbiting props, Legendary and Mythic are full scenes with a recipe per pathway (symbol, props, ambient motion and extras such as wings, chains, lightning, clouds, rays). Shiny cards glow gold. Everyone nearby sees it. Not tested in game, the visuals will likely need tuning.

## 0.2.107
- Fixed the weekly pack menu: the Take packs and Close buttons were off the bottom of the screen at larger GUI scales. They now sit at the top beside the tabs, and the grid shrinks to fit the screen. Not tested in game.

## 0.2.106
- New Seq 0 (Mythic) cards for all 22 pathways, normal and shiny, in the same style as Seq 9 to 1 but over the top: a huge glowing symbol, rings of gold, a spiked crown halo, tinted glass petals, four tiers of wings, rune medallions and a lotus pedestal. They are 480x720, twice the size of the other cards.
- Darkness, Hanged Man, Abyss, Black Emperor and Chained are dark and muted, and Death is a bit less dark.
- Seq 0 is no longer animated, so no cards are animated now. The resources are about 45 MB, down from 88 MB.
- Nothing here is tested in game.

## 0.2.105
- New static art for every pathway's Seq 2 and Seq 1 cards (22 pathways, normal and shiny): Seq 2 has wings, a rune ring and a nameplate; Seq 1 has bigger wings, a sun-crown halo and a laurel banner. Only Seq 0 is animated now.
- Removed the animated Seq 2/1 sheets, which cuts about 32 MB from the mod's resources.
- New "Astral World" creative tab with every tarot pack (normal, rainbow, shiny), the confetti popper, bubble wand, tree smiter and osu cabinet.
- Nothing here is tested in game.

## 0.2.104
- Confetti popper now has 10 uses and the bubble wand has 100 seconds of use (durability bar). Creative mode doesn't use durability. Untested in game.

## 0.2.103
- Confetti popper fires about 4x more confetti in a slightly wider cone. Untested in game.

## 0.2.102
- Monster cards are now grey/silver instead of green (static Seq 9-3, animated and shiny Seq 2/1/0). Untested in game.

## 0.2.101
- The weekly pack menu asks you to confirm your picks (a list of what you chose, with Confirm and Back) before you take the packs.
- Corpse Collector's pack is green now, Prisoner's is more purple and Savant's is more orange.

## 0.2.100
- Players who log in with weekly tarot packs they have not picked yet get a message with a clickable link that opens the weekly menu.

## 0.2.99
- Fixed the glitch when clicking a rare card in a pack: for a few frames its shine and sparkle overlays showed as a washed out diagonal band and a solid gold frame (blending was being switched off by the flip burst behind another card). All card and shine textures are also loaded when the pack opens now.
- The animated Seq 2, 1 and 0 cards are 50 frames at 5 per second instead of 100 at 10 per second, which roughly halves their size in the mod file and in video memory.
- Weekly tarot packs: players use /weeklypacks (or /packs) to open a menu and pick their packs for the week, then take them. How many packs a player gets, and whether some can be Rainbow or Shiny packs, comes from their FTB Ranks rank through the permission nodes astral_world.weekly_packs (a number), astral_world.weekly_packs.rainbow and astral_world.weekly_packs.shiny (a number, or true for any). Players without those nodes get weekly_packs_default from settings.properties (1) and only normal packs. The week resets on weekly_reset_day (settings.properties, 1 = Monday). Ops can give someone another go with /weeklypacks reset <player>.

## 0.2.98
- Animated Seq 2, 1 and 0 cards for every pathway (Seer, Sailor and Warrior were already done, this adds the other 19), each with its own scene, plus an animated black and gold shiny version of each. Every scene is made from that pathway's own theme (Moon: petals and a huge moon, Door: floating portals and a key of stars, Justiciar: scales and golden light, Sun: a blazing sun, Death: ghosts and a soul vortex, Abyss: red cracks and eyes, Mother: a glowing tree, Chained: swaying chains, Paragon: a clockwork of gears, and so on), with a finale at the end of each loop. They show in pack openings and the card viewer, and as a still in the inventory.
- The mod download is much bigger because of the new animation sheets.

## 0.2.97
- Seer Seq 2, 1 and 0 are now animated cards (and animated black and gold shiny versions), each with its own scene. Seq 2 Miracle Invoker: a violet night sky where constellations draw themselves between the stars, with meteors, and a finale where they all blaze gold. Seq 1 Attendant of Mysteries: a dark fog vortex behind two turning rune rings, with eyes opening and closing around the emblem. Seq 0 Fool: grey fog with six small coloured lights drifting in it and twelve tarot cards orbiting the emblem. Their inventory icons show a still of the animation.

## 0.2.96
- Sailor Seq 0 (Tyrant) is now an animated card: a storm sky with rain and a crown of spikes around the emblem. Its inventory icon shows a still of it.
- Sailor Seq 2, 1 and 0 now have animated black and gold shiny versions (the same animations recoloured, with a gold emblem). They show in pack openings and the card viewer, and as a still in the inventory, with the sparkles on top.
- Fixed the inventory models of cards: the model list was in an order that could make every card of a pathway show its Seq 3 art. It is sorted now.

## 0.2.95
- Shiny cards of Seq 9 to 3 (every pathway) have their own art: black with gold for the frame, ornaments, icons, text and the pathway emblem, with the same tiers (moon, planet, black hole and galaxy). The shiny sparkles still play over them, in the pack opening, the card viewer and (as the model) in the inventory. Shiny cards of Seq 2 to 0 still use the normal art with sparkles.
- New packs for every pathway: a Rainbow Tarot Pack (all 3 cards are Seq 3, 2, 1 or 0) and a Shiny Tarot Pack (all 3 cards are shiny). Both come in all 22 pathways with their own art, and cards of the pack's own pathway come up 5x as often, like the normal packs.

## 0.2.94
- Tarot cards now have a model like the packs instead of the plain swatch: each card that has designed art (Seq 9 to 3 of every pathway, and Sailor 2 and 1) is a flat card in the inventory, the hand and on the ground, showing its own art. Sailor 2 and 1 show a still of the animation there. Cards of Seq 2 to 0 (other than Sailor 2 and 1) still show the old swatch.

## 0.2.93
- Designed tarot card art for Seq 9 to 3 of all 22 pathways (154 cards): a starfield with orbit rings, in the pathway's own colour, with that pathway's own icons. What a card shows depends on its sequence: Seq 9-8 a plain dark sky and a thin frame; Seq 7-6 a silver frame, an arch window, an icon ring and a small moon; Seq 5-4 a gold frame with scrolls, feather wings, a sunburst on the number, an icon band, coloured gas clouds and a ringed planet; Seq 3 larger wings, a gold halo ring, a black hole and a bright star on top of that.
- Sailor Seq 2 (Calamity) and Seq 1 (Thunder God) are animated cards (rain, lightning, a slow shine; Calamity's funnel and a wave over the whole card).
- The new art is shown when a pack is opened. Right click a card to look at it (shiny sparkles included). Cards of Seq 2 to 0 (apart from Sailor 2 and 1) still use the old temporary front; the inventory icon is still the plain rarity swatch.

## 0.2.92
- Fix: the game no longer crashes when the hunger games end. Clearing the arena removed items while looping over the world's entity list, which blew up; it now loops over a copy (same fix in the end-event cleanup).
- The Mother Tree of Desire model no longer shows up in the hunger games arena: players are told there is no tree when they are carried in.
- Duels now happen in a duel arena instead of wherever the challenge was made: a round platform far from the rest of the world, with invisible walls and a lid and no digging or building. Both duelists are carried there and carried back to exactly where they were (also if they logged out or the server restarted in the middle). A duel that ends in a death lets the winner stay 20 seconds to pick up the drops; the others go back after 3 seconds. Because nobody else can be near the platform, other players' World of Pallor can no longer touch duelists.
- Bosses' immunity to the World of Pallor now also sets LOTM's in-memory protective mark (the old flag did nothing on the acting line of LOTM).

## 0.2.91
- The confetti popper and bubble wand now use their own particles instead of vanilla ones: confetti is a flat paper piece in a random colour that tumbles and flutters down, and bubbles are soap bubbles (with a faint rainbow rim) that wobble, drift up and pop.

## 0.2.90
- Pack odds. Each card rolls its rarity first: Common 50%, Uncommon 25%, Rare 20%, Epic 3%, Legendary 1.5%, Mythic 0.5%. Then the sequence within that rarity (even split), then the pathway (the pack's own pathway counts 5x any single other one), then the shiny roll for that rarity: Common 5%, Uncommon 10%, Rare 10%, Epic 15%, Legendary 20%, Mythic 40%.
- Shiny cards now get only twinkling sparkles on top of their normal rarity shine (Mythic keeps its moving rainbow). This replaces the whole-card foil from 0.2.89, which was not what was wanted.

## 0.2.89
- Shiny cards: a card with `shiny:1b` in its custom data gets an animated foil over the whole card, back and front: a faint prismatic tint that drifts across, a bright sweep once every 4 seconds, and twinkling sparkles. It sits on top of the rarity shine, so every rarity (Mythic's rainbow included) can be shiny. The item also glints in the inventory and is named "Shiny ...". Nothing rolls shiny from packs yet; test with `/give @s astral_world:tarot_card[custom_data={pathway:"seer",seq:9,shiny:1b}]`.

## 0.2.88
- Tarot pack opening. Right-click a pack: it fills the screen, hold left click and drag across the top to tear it open, three face-down cards rise out and you click each to flip it. The cards are already in your inventory when the screen opens (closing it early loses nothing), and the pack is used up on the right-click.
- Card backs: black with gold lines and the pathway symbol. The shine depends on rarity: Common and Uncommon none, Rare a faint gold shine, Epic a gold shine, Legendary a rainbow shine, Mythic a rainbow shine that slowly travels across the card.
- Rarity by sequence: Seq 9-8 Common, 7-6 Uncommon, 5-4 Rare, 3-2 Epic, 1 Legendary, 0 Mythic. The card item has a temporary sprite per rarity. To get one: `/give @s astral_world:tarot_card[custom_data={pathway:"seer",seq:9}]` (use seq 9, 7, 5, 3, 1, 0 for each rarity).
- The card faces are temporary: a frame in the rarity's colour with the pathway symbol, name and sequence.

## 0.2.87
- Tarot packs, `/give` only: 22 pathway packs (`astral_world:seer_pack` etc.) that open on right-click for 3 cards. A card from the pack's own pathway is 5x as likely as any single other card, and lower sequences are rarer (Seq 9 most common, Seq 0 rarest). The cards are one item, `astral_world:tarot_card`, with a temporary sprite; `/give @s astral_world:tarot_card` gives the plain test card, and `/give @s astral_world:tarot_card[custom_data={pathway:"seer",seq:0}]` gives a specific one.

## 0.2.86
- New cosmetic items, `/give` only: `astral_world:confetti_popper` (right-click pops it with a sound and sprays coloured confetti in a cone, short cooldown, never used up) and `astral_world:bubble_wand` (hold right-click to blow a stream of bubbles that drift forward and up, no damage).

## 0.2.85
- New `/duel <player> <safe|normal>` for everyone, then `/duel accept`, `/duel decline`, `/duel forfeit`. During a duel only your opponent can hurt or affect you, and you can only hurt or affect them. Safe: a killing blow just ends the duel, nothing is lost and no sequence regresses. Normal: the loser really dies, with the usual item loss and regression.

## 0.2.83
- New ops commands `/enchanting` and `/anvil` open those screens anywhere. The enchanting screen always offers levels 10, 20 and 30.

## 0.2.82
- **Coin swap can't lose coins any more:** LOTM coins stack to 9999 but Numismatics coins only to 64, so a big purse could overflow the inventory. Now only as many coins are swapped as there is room for; the rest stay LOTM coins, untouched, and are swapped the next time you use a shop or the bank. Nothing is dropped.

## 0.2.81
- **Numismatics reads in LOTM values:** the mod now re-values the Numismatics coins to match LOTM: spur (Bronze) 1, bevel (Silver) 10, sprocket (Gold) 100, so shop prices, the bank and tooltips read in LOTM coins. The three higher coins (cog, crown, sun) are still in the game but never handed out. The coin swap is now one-to-one by tier. Needs the mod on the server and on every client, and bank balances are unchanged. Old coins already in chests change worth (a bevel was 8, is now 10). If the re-valuing fails the log says so and the exact-by-value swap stays in use.

## 0.2.80
- **Keep on death:** any item carrying the tag `astral_keep` (the Supporter+ credit card and VIP portable bank terminal, made with the give commands) is no longer dropped when its owner dies and comes back with them when they respawn.

## 0.2.79
- **LOTM coins work in Create: Numismatics.** Use any shop, vendor, depositor, bank terminal, bank card or the portable terminal and your LOTM coins (gold 100, silver 10, bronze 1) are swapped for Numismatics coins of exactly the same value (1 bronze = 1 spur); a couple of seconds after you stop, every Numismatics coin you carry turns back into LOTM coins. You only ever hold LOTM coins and nothing is lost or created. Only plain coins are swapped. If neither mod is there the feature stays off.
- **The bank:** the bank terminal and blaze banker can't be broken or blown up, and the banking guide can't be used to make a banker, except by an op in creative mode. (To also stop them being crafted, install the "no bank crafting" datapack.)

## 0.2.78
- **New Lord of the Mysteries version:** everything that talks to LOTM now works with the newer build (the "acting factions" one) and still works with the older one. Pathway and sequence lookups, corruption (adding, doubling, clearing), misfortune, sanity, the combat check, resizing summoned entities, and the stun / bleeding / frenzy / awe / paralysis / no-regeneration effects used by the god curses all use LOTM's new code. Mental plague now needs a cast record in the new LOTM, so where the mod has none it uses frenzy instead. LOTM has no lava damage type any more, so lava damage from the Ravings is plain vanilla lava damage.
- **Mother Tree:** Cursing Aura and Thorn Paradise are found at their new place in LOTM, and Thorn Paradise is cast the way the new LOTM casts toggled abilities. The crimson moon check in the sky and god scenes uses its new location.
- If something in LOTM changes again, only the affected feature switches off and the log says which one.

## 0.2.77
- **Astral Barrier:** the sky barrier was sent to every player; now only Sequence 0s can see it. Everyone else has it switched off (it is re-checked every second, so a new Seq 0 sees it within a moment).

## 0.2.76
- **Arena reset:** after every Mother Tree raid and every hunger games the whole arena is regenerated block by block against how the world made it (broken blocks, placed blocks, chests, leftovers), a few chunks a tick, about half a minute. Also at the start of a raid. `/arenareset` (ops) does it by hand, which also fixes damage left over from earlier games. A new hunger games waits until the reset has finished.
- **Hunger games:** pickaxes now turn up in chests (bare hands on stone bricks is very slow), and a block break by a contestant that some other mod had cancelled is allowed again. I found nothing in this mod that stops breaking, so if it still fails the server log will say "had been cancelled elsewhere" and it is another mod or claim.

## 0.2.75
- **Mind attack:** a new, harder osu attack (the tree tries to break your mind; curved sliders, tighter timing) that comes up far more often, and Desire's call now uses the hard version too. Fixed a bug where a shrunken boss game could never count as passed.
- **Parry attack:** the tree gathers its power and explodes. A bar appears (Undertale style): hit the middle for no damage, near it for less, miss and it is devastating.
- **Tyrant:** a Tyrant's storms and summons now do 40% less, and bolts, storms and summons from a fighter who is down or spectating no longer hurt the tree.
- **Curse attack:** the animation now draws the branches in slowly, snaps out at the strike, then relaxes (it was a constant shudder).
- **Hunger games:** wild pigs, cows, chickens, fire/frost/earth spirits and spirit eaters roam the arena; chests can hold torches, wind charges and bottles of luck; the arena closes in on the middle (damage outside the circle, fully closed after about 12 minutes); everyone starts in a circle in the middle for 15 seconds, then is sent to the edge; kills and placements are tracked and listed at the end; **`/hungergames board`** (ops) places a row of signs that always shows the last game's placements and kills; LOTM data outside the inventory (enslaved spirits, grazed souls, personas) is put back when you leave.

## 0.2.74
- **`/astralend revive <player>`** (ops): puts a fallen or kicked fighter back into the raid with full lives, in survival, at the edge of the arena.

## 0.2.73
- **Hunger games:** players can hurt each other now. The arena's "no damage between players" rule (made for the tree fight) was also applying to the games.

## 0.2.72
- **Spawn:** the raid's edge pull-back was dragging players from outside the church wall back inside within a second. It is off while everyone gathers, so you really start outside the south gate.
- **Opening cutscene:** it never played again after it had been skipped once (the skip stayed on for the whole session). Every new cutscene now clears an old skip. Tree cutscene too.
- **Hitting the tree:** swings were ignored entirely until the game had once tracked the tree (it only does within about 80 blocks), so from the gate you could not hit it at all. They now work from anywhere. The reach is 34 blocks.
- **Creative stays creative:** the raid no longer forces survival on you. Fallen players go to spectator and come back to the mode they had.
- **Tempest:** the final web strike comes another 0.25 seconds later.
- **Meteor scene:** standing inside the tree no longer spins your view round its middle.
- **Osu:** circles after a slider come later, with room to let go (Desire's game has fewer shapes to fit; quick play leaves a longer gap). Editing a level and saving over the same level no longer asks to replace it (that was why a newly linked disc looked not saved: it was waiting for a second Save click).
- **Arena materials:** no more blackstone, gold or deepslate. The altar ring, paths, rings and rim are mixed stone, mossy and cracked bricks, polished andesite and chiseled bricks (new worlds only).

## 0.2.71
- **The church is now the whole raised arena:** a huge round cathedral covers the entire stone platform. Tall outer wall with pointed windows and four arched gates, buttresses, a ring of great pillars and a broken inner ring, and a roof over the outer ring that is about half fallen in (the middle is open for the tree, with fallen-roof ribs left standing). Invisible lamps keep the covered part from being pitch black. New raid worlds only.
- **Starting the fight:** everyone now arrives outside the south gate and is carried just inside it. The opening camera scene waits six seconds after everyone is inside, and the tree is drawn again at the start of every raid (it could stay hidden after an earlier one).
- **Roots and heart crystals** now only grow on the grass patch (they were being placed out on the stone, where they cannot grow at all, which is why they were impossible). Fewer of them, more time: 3 to 5 roots for one player, 2 heart crystals.
- **Hitting the tree:** a swing now reaches 34 blocks (plus 3 around every part), so you can hit the part of the tree you are looking at from where you stand.
- **Osu:** slower (rings take longer to close, wider timing), and shapes no longer stack on top of each other (Desire's game and quick play keep new shapes away from ones still on screen). Quick play has sliders. Curved sliders: in the editor press "Sliders: straight" to switch to curved, drag to make the slider, then click where it should bend. Seven colour themes (button on the cabinet menu). Opening a cabinet with no jukebox next to it tells you there will be no music.
- Hunger games work under the new roof.

## 0.2.70
- **Hunger games use the whole stone platform:** players now start evenly spaced on a wide ring (about 270 blocks out, outside the church), chests are spread over the entire platform (170 anywhere, 60 inside the church, 50 with better loot in the middle), and the edge of the arena is the edge of the stone.

## 0.2.69
- **The arena edge:** during the Mother Tree fight you can now walk to the very edge of the stone platform. Only beyond the stone are you pulled back. Hunger games use the same edge.
- **Hunger games and curios:** if the Curios mod is installed, curio slots are now wiped when the games start (and for eliminated players) and given back afterwards, like the rest of the inventory.

## 0.2.68
- **Hunger games:** `/hungergames start` (ops) asks every player yes or no with clickable buttons (60 seconds, or sooner when everyone has answered). The yeses are carried to the raid arena (no tree), spaced evenly around the edge of the grass and held still for a 10 second countdown. About 150 chests are spread over the arena, 50 of them in the middle with better loot. Last one standing wins; the dead become spectators and drop what they gathered.
  - `/hungergames start noseq`: not a sequence event. Beyonders drop to sequence 9 for the games and get their sequence back afterwards.
  - `/hungergames start seq keep`: everyone keeps their sequence. `/hungergames start seq set <0-9>`: everyone plays at that sequence and is put back afterwards.
  - Afterwards everyone gets their place, inventory, health, food, experience and sequence back. Players who log out mid-game are restored when they log in. `/hungergames stop` ends it early, `/hungergames status` shows it.

## 0.2.67
- **Osu sliders:** levels can now have sliders. In the editor, click and drag to make one (the drag length sets how long it is). In play, click the head on time, then hold the mouse and follow the ball to the other end. Following it at least half the time keeps your combo; following it nearly all the time keeps the full grade. Desire's click game has sliders too (every fourth shape). Old levels and share codes still work.
- **Editor layout:** the name box now has a "Level name:" label and a hint, and saving, testing, copying or publishing without a name tells you to name it. Buttons are on their own row so Back and Link disc no longer cover each other. Saving over a level with the same name asks first ("Replace?").
- **Cabinet:** new look: dark wood with iron bands, a screen showing a circle and its ring, and two buttons. It faces you when you place it.
- **Etched discs:** if Etched's own hook doesn't start the disc in the jukebox next to the cabinet, the mod now sends Etched's play packet itself (it writes a line in the log either way).

## 0.2.66
- **The ruined church:** a huge, abandoned, half-collapsed church now stands around the tree (new raid worlds only): a nave with a door in the south, an apse in the north and a transept across, walls of mixed mossy, cracked and plain stone with pointed windows and broken tops, buttresses, two towers either side of the door, rows of pillars, broken pews and rubble. The tree grows in the middle of it. Walls are solid cover now (for the 15% meteor).
- **Starting the fight:** everyone arrives in front of the church and is told to go inside. After 60 seconds anyone still outside is carried in. The opening camera scene starts once everyone is inside.
- **Her death:** when she falls, the flesh melts: she sags and spreads, darkens, drips, and is gone, leaving a spreading puddle of blood that dries away into ash and smoke (about 15 seconds). Then the victory comes.
- The raid logs a warning if the world border is smaller than the arena.

## 0.2.65
- **Hitting the tree:** the invisible hitbox entities (the floating boxes in your screenshot) are gone. They only worked with the Pehkui mod and were capped to small boxes without it. Now your swing is checked against the whole tree model: trunk, roots, every branch and the leaf clusters, with 1.5 blocks of extra reach around every part. Swinging at any of them (from outside) hurts her. The old boxes are removed from existing worlds.
- **Meteors:** drawn bigger and falling from much higher, so the rock is visible all the way down.

## 0.2.64
Tree fight changes.
- **Roots and heart seeds:** they now spawn out beyond the tree's own roots, so none is hidden inside it.
- **Tree size:** the tree grows with the number of fighters (1x for one, up to 1.5x for thirteen or more). Meteors and slams grow with it too.
- **Ring attacks:** the rings roll all the way to the edge of the arena.
- **Meteors:** they fall slower (about 3 seconds), from higher up, and are bigger, so you can see them coming.
- **15% meteor:** it can't hurt you if solid blocks stand between you and the blast.
- **Tempest:** the lightning and the meteors now hit at the same moment and share the same safe circles; there are far more of both (up to 150 meteors and 300 bolts); the final web comes half a second later than before.
- **Fruit and flowers:** the fruit, and the three flowers of Mirror Bloom, stay on the field much longer (about 27 seconds), and those attacks leave more time before the next one.
- **Counters:** they no longer need the vanilla cooldown to show, work from further away on the big pollen clouds (anywhere inside the cloud), and work up to Sequence 4 for cleansing. A cloud that was cleansed (or calmed) changes colour so everyone can see it was done. Each use is logged as `counter attempt` in the server log.
- **Fog at the tree's base:** the pollen clouds are what sat on the tree; they now spawn far from it.

## 0.2.63
- **Tempest:** the three strikes are now spaced out: the lightning, then the meteors land 0.5 s later, then the arena-wide web strikes 0.5 s after the meteors.

## 0.2.62
- **Osu cabinet:** a new block (Functional Blocks tab; recipe: polished blackstone around a note block over redstone). Use it to play the click game for fun: quick play, your own levels, levels other players shared, or a pasted code.
- **Level editor:** put circles on a timeline (click to add, right click to remove, wheel or arrows to move through time, space to preview), set the BPM, test it, and save it on your computer (`astral_osu` folder).
- **Sharing:** copy a level as a code (`OSU1:...`) to paste to anyone, or publish it to every cabinet in the world, where anyone can play it, edit a copy or copy its code.
- **Music discs:** hold a vanilla or Etched disc and press Link disc in the editor. When the level plays, the disc plays from a jukebox standing next to the cabinet (the cabinet puts a copy in an empty jukebox and takes it away again).

## 0.2.61
- **Phase lines:** fixed her getting stuck just above a line (750,001 and the like). The cause was my anti-regeneration guard: when she regenerated faster than you hit her, it put her back to her health from before your hit. It now remembers the health she must have after each hit, so it can't undo one, and anything within 3 HP of a line counts as being on it, so the phase starts.

## 0.2.60
- **Dodging:** a bit more warning on the normal meteors (1.9 s, was 1.5 s), the slam (2.5 s, was 2 s) and the Meteor Storm (1.7 s to the meteors and 1.9 s to the lightning, was 1.3 and 1.5 s).

## 0.2.59
- **Tree Smiter (testing):** a creative-only item (Tools tab, or `/give @s astral_world:tree_smiter`). Hitting the Mother Tree or any of her hitboxes with it in creative takes 10% of her maximum health, whatever the damage caps say. The phase lines (75, 50, 25%) still hold, and it does nothing while she is shielded (roots, call, meteor, kingdom, sleeping).

## 0.2.58
- **Battlefield edge:** the invisible edge no longer pulls you back at about 160 blocks. It used to shrink with the number of players, which kept everyone off the stone; now the whole temple (340 blocks) is the battlefield.
- **Raised temple:** the platform now stands 20 blocks above the field around it (was 7), with a tall brick cliff and longer steps at the four gateways. The field beyond is bigger so the steps fit.

## 0.2.57
- **Door in the sky:** the sign for a Door (Apprentice) Seq 0 is now the LOTM mod's own door (its arched frame with blue glyphs, starry doorway and vortex), baked from the mod's model and texture.
- **Door and the barrier:** a Door Seq 0 now counts as two Seq 0s for the barrier's maximum HP (+200), matching how it already counts for healing. Before, it added the same 100 as anyone else.

## 0.2.56
The temple arena. It only appears in a newly generated raid world: any chunks already generated keep their old flat grass.
- **Raised stone temple:** the fight now happens on a huge stone platform (arena radius 340, was 260), standing 7 blocks above the grass field around it. A 2-block wall runs round the rim, with four gateways and steps down to the field.
- **Grass patch:** the tree stands on a grass patch 185 blocks wide, wide enough that none of its roots touch the stone.
- **Altar and floor:** a dark polished ring with a gold line, then stone bricks mixed from normal, mossy and cracked, two dark rings with glowing glyphs, eight pale chequered paths, and a deep-slate rim.
- **Tempest:** the web grid spreads over the bigger arena.
- The cleanup of the old pillar ring is removed, since it would have torn down the new rim.

## 0.2.55
- **Tempest:** the final lightning web now grows from a grid of points about 50 blocks apart across the whole arena, so there are no bare patches (only gaps between the lines).
- **Tempest safe circles:** each one is placed 4 to 11 blocks from a fighter, inside the arena, and the meteors' circle sits right on top of the lightning's circle so you can step from one to the other.

## 0.2.54
- **Tempest (the low-health attack):** now one short wind-up that shows every safe circle (blue for the lightning, green for the meteors), then the lightning, the meteors and the arena-wide web follow one another almost instantly.
- **Meteor Storm:** put back to how it was before 0.2.53 (the 0.2.53 change was meant for Tempest).

## 0.2.53
- **Meteor Storm:** the meteors and the first lightning now land almost instantly (a fraction of a second after the circles show). The follow-up lightning branches over a far bigger area: each web reaches about 300 blocks, and the arena-wide grid is denser with longer, busier webs.

## 0.2.52
- **Phase 5 cutscene:** the prayer is now voiced. Lines 1 to 5 and 7 play as each line appears. Line 6 ("And praying for the bestowment that stems from good luck.") has no recording yet, so it is silent. The recordings were volume-matched.

## 0.2.51
- **Prayer:** the first three lines now read "The Fool that does not belong to this era.", "You are the mysterious ruler above the gray fog." and "You are the King of Yellow and Black who wields good luck."

## 0.2.50
- **15% meteor:** the "take cover behind the tree" line is gone.

## 0.2.49
- **Accepting her offer:** no minigame, it just works again.
- **Refusing her offer:** the memory game is gone; she only says her line.

## 0.2.48
Tree fight changes.
- **Cutscenes:** the camera now stays inside the part of the world that is loaded around you (outside it you only saw a few blocks of ground, the "dirt block"). The opening camera stays on your side of the tree. Camera moves are tick-based, so the phase change glides instead of jittering. Cutscene text is now high on the screen, clear of the chat.
- **Ground ring:** back to a plain purple wave. The sky ring stays a lightning-and-leaves wave.
- **Damage to the tree:** every hit is now evened out. Weak attacks (low sequences) really hurt her, huge ones are squeezed, and a stronger hit is still always worth more. Ocean of Lightning only drains her at 0.08% of her health a second. Nothing hurts her inside her divine kingdom.
- **Hitbox:** measured from the model. The trunk box is 120 wide and 84 tall, and seven invisible boxes (roots, lower trunk, canopy in layers, top) wrap the rest, up to 360 wide and 250 tall. Swinging at the drawn roots still works through them.
- **Pollen:** 25% smaller than before, and it now appears far from the tree.
- **Slowness:** the tree no longer slows you (Desire and the curse).
- **Minigames:** the random memory/thread attacks are gone. Desire's Call is the click game (25% slower). Taking her hand (accepting her offer) is now a thread-following trial, and fail it and she lets go of you. Refusing still starts the old memory game.
- **Meteor Storm:** lightning now spreads over the whole arena, not just where players stand.
- **15% meteor:** 3x bigger, starts far higher, and the explosion is 3x bigger. You run and take cover behind the tree (more cover to come).

## 0.2.47
- **Tree hitbox, done properly:** the trunk keeps a narrow box (24 wide, 150 tall) and two invisible parts add the flat root plate (300 wide, 26 tall) and the canopy (290 wide, 105 tall, from 122 up). Anything that hits a part hits her. The parts don't push players and shrink away whenever she can't be hurt, like the trunk does. The sizes are in `TREE_PARTS` to fine-tune.

## 0.2.46
- **Hitbox:** now covers the whole tree, 300 wide (the full canopy) and 160 tall. To be fine-tuned.

## 0.2.45
Fixes and tree-fight changes.
- **Barrier:** if the only Seq 0 dies and another rises, the barrier starts at 50 HP again instead of 0.
- **World tree scene:** when the tree isn't loaded yet, the cutscene camera now uses her real position instead of your height (that could put it inside the ground, a "dirt block").
- **Pollen:** the clouds are 5x wider.
- **Desire:** its counter now shares the action bar line with your lives instead of covering them.
- **Fruit:** a proper peach-shaped fruit model; the beam is pink before the change and crimson after, like the tree.
- **Summon:** the werewolf summon attack is removed.
- **Minigames:** Desire's Call is always the click game (no change to its speed from 0.2.44). The rhythm game is gone; a memory game (repeat the order four pads light up) replaces it as its own attack, and the thread game stays as another.
- **Lightning storms:** faster, with much more sound (charge-up, thunder and impact layers, and explosions on every meteor). Tempest is faster too.
- **Lightning resistance:** Star of Lightning can no longer hurt her at all, and Ocean of Lightning only drains her slowly (0.3% of her health a second at most).
- **Hitbox:** 40 wide and 150 tall, to match the tree (it was 125 by 375).
- **Ground ring:** redone as a ridge of earth with burning thorns, instead of the odd curved roots.
- **Phase lines:** she can no longer heal on her own (that is what stopped you reaching 25% and the others); she only heals through the fight's own mechanics.

## 0.2.44
- **Click game:** circles are back to their original size and count; they just appear 15% slower.

## 0.2.43
Tree fight changes.
- **Phase 5 cutscene:** players are teleported right after the error part. The last line ("I've heard your prayers.") now appears as golden-black gradient text instead of part of the scene.
- **Opening scene:** zoomed out so you can see the whole tree. Chat messages now arrive a little later, after the scene has really finished.
- **Gifts:** if nobody picks one within 60 seconds, a random gift is chosen.
- **Rings:** much bigger. The sky ring is now a tall wave of lightning and leaves (you must move well clear of its height), and the ground ring is a surge of burning roots.
- **Mirror Bloom:** the flowers now have a real model, with a beam of light showing each spot in the tree's colours (pink, then crimson after the change). The circle is gone.
- **New attack, Tempest (below 35%):** lightning strikes the whole arena at once (stand in a safe circle), then meteors fall everywhere (different safe circles), then lightning webs the entire arena and you must find a gap.
- **Meteor Storm:** the lightning branches are more random and split into more, smaller branches.
- **Lightning resistance:** the Mother Tree takes 60% less damage from lightning.
- **50% cutscene:** smoother, much less jitter.
- **Minigames:** the click game keeps its original circles, but they appear 15% slower. Two new ones, a rhythm game (D F J K or arrow keys) and a thread-following game. Desire's Call picks one at random, and each also exists as its own attack.
- **15% meteor:** far bigger, and it starts much higher so it no longer begins inside the tree.

## 0.2.42
Tree fight changes.
- **Start of the fight:** you arrive 20 blocks further from the tree. Nothing is said in chat until the opening camera scene is over, and the "she sleeps / the peace is over" warnings are gone. The ring of stone pillars at the edge of the arena is gone.
- **Corruption:** every attack now gives 2% (was 5%), and a hit adds at most 10% of your corruption cap per pathway instead of fully doubling it. The Astral god (testing) takes no corruption from attacks.
- **Phases:** the hit that crosses 75%, 50% or 25% now lands exactly on the line and starts the phase, instead of being cut to almost nothing or thrown away.
- **Fruit:** real fruit (apples with a stem and leaf, mouldy when rotten) fall with a golden beam, red once she has transformed. The circle under them is gone.
- **Fog:** the pollen veil is now a huge bank of drifting mist (about three times wider) with no circle.
- **Meteors:** the mod's own meteors are much bigger and look better: a tumbling rock with glowing cracks, a fire and smoke trail, thrown debris and a hot crater.
- **Meteor Storm (new variant of the meteors):** every meteor lands at the same moment, then lightning branches wildly out of each impact across the arena. Red lines show where the branches will strike.
- **Purge:** it now gives 7 seconds. A safe circle appears near everyone, the tree visibly winds up and the purge ends in a huge explosion.
- **Temptation:** the drag on your view turns smoothly instead of jumping. The Astral god is never dragged by it.
- **Cursing Aura ("your legs stop agreeing with you"):** it is switched off after 15 seconds and everyone's legs agree with them again, the Astral god never feels it, and the tree never uses the Purge while it is on or anyone still has it.
- **Bigger attacks, longer gaps:** meteors and the slam are bigger (meteors double their size before the tree transforms), and there are about 3 more seconds between attacks.

## 0.2.41
- **Astral Barrier:** it now starts at 50 HP when the first Seq 0 appears, instead of 0.

## 0.2.40
The big rework of the tree fight, from the design document.
- **Hits:** a hit now doubles the corruption you already have (1% on a random pathway if you have none), takes 15% of your max health, and blocks all healing for 60 seconds. A life is lost at 0 health. Hit grace is 1 second. Everyone has 3 lives, and you come back 100 blocks from the tree with your corruption cleared. After a hit your sanity cannot drop for 30 seconds.
- **Every attack** gives every fighter 5% corruption on a random pathway, hit or dodged.
- **Anti-heal:** each werewolf attack adds 5 seconds, stacking. Heals that come from the tree's own attacks (sweet fruit, the true bloom, holy clouds) ignore it.
- **Attack order:** each attack can now be followed by only two others, so the fight can be read.
- **Numbers:** giving in to temptation is 50% corruption, the curse is 10%, rotten fruit costs 15%. "Thralls" are now just werewolves. No effects are skipped for big groups any more.
- **Rings:** fighters more than 5 blocks up are no longer hit by the ground ring; instead a sky ring is sent at their height.
- **Slam:** the strike is now a falling bud that bursts into a flower. Before 50% it is pink and white blossom with gold and green leaves; after, a crimson and violet bloom with thorns, rotten leaves and red veins in the ground.
- **Desire's call (50%):** five seconds of the temptation rules, then your cursor is freed for a five second click game. Her attacks pause for the whole call.
- **Refusing Desire's offer** (or not answering) starts a memory game with four flowers, three rounds. Failing it costs a life.
- **Divine Kingdom (under 10%):** she raises LOTM's Divine Kingdom: Thorn Paradise once. It ends after 20 seconds, or when the lives lost reach 80% of the fighters who were there.
- **During the 15% meteor** she takes no damage until it lands.
- **The traitor** now starts at Strength I, Resistance I, Speed I and Sharpness X and grows with each kill they are credited with (80% of the damage). Order Strength, Strength, Resistance, Speed; caps Strength V, Resistance IV, Speed II; once capped, each kill heals them 10%.
- **Pathway counters:** several Beyonder abilities now change how some attacks play out. They are not explained in game.

## 0.2.39
- The tree's meteor attack now uses LOTM's own meteors (lotm:meteor_no_hurt): one falls onto every red circle and lands as the circle completes. It breaks no blocks and cannot hurt the tree; the life is still taken by the circle, exactly as before. The mod's impact flash, ring and fire column still play on top. If LOTM's meteor is missing the mod falls back to its own.

## 0.2.38
- The health bar glitch now leaves its number behind: when a glitch ends, the bar keeps showing the random value it landed on (her real health plus or minus up to 15 points, and it follows her real health as it changes) until the next glitch picks a new one. Healing back above half clears it.

## 0.2.37
- The 15% meteor can be used again and again: whenever she is at 15% or lower and it is off cooldown. The cooldown is 120 to 180 seconds, drawn fresh each time. If she heals above 15% and drops back, it only falls again once it is off cooldown.
- The meteor feeds her: 1% of her health for every fighter it hits, and another 1.5% for each one it kills (their last life). Chat says how much she healed.
- The health bar glitch now shows a random value within 15 points of her real health, on both the number and the bar (at 10% it reads anywhere from -5% to 25%).
- Until the reveal her real entity name is "The World Tree" (Jade and similar showed Mother Tree of Desire); it changes to Mother Tree of Desire at the reveal.

## 0.2.36
- Before the change at 50% she is only "The World Tree": the name tag over her is hidden (it showed Mother Tree of Desire), and the arrival, waking, roots-drink and "sings" messages and the intro scene say "The World Tree". The name tag and the name in messages switch to Mother Tree of Desire when she is revealed.

## 0.2.35
- The ruin under the Mother Tree (paved dais, broken columns, inner wall, rubble) is gone: the middle of the field is plain grass. New fields generate without it, and existing worlds have it cleared when the raid starts. The broken pillars on the far edge stay.

## 0.2.34
- Tree fight start: after the teleport everyone watches a slow camera scene circling the Mother Tree (hold SPACE to skip), then there is a 30 second grace period.
  - During the scene and the grace period the tree cannot be hurt and does not attack, and nobody takes damage during the scene.
  - After that the tree stays asleep until a fighter comes within 45 blocks of it or anyone hits it. Then it wakes and the normal attacks begin.

## 0.2.33
- The tree transformation cutscene camera stays 70% further away (and a bit higher), so the whole tree fits in view.

## 0.2.32
- Mother Tree fight: proper attack visuals, drawn on your screen (nothing changes in damage or timing).
  - Meteors fall from the sky as a glowing fireball with a long fiery trail, and burst into a flash, shock ring and fire column where they land.
  - Slam: a huge thorn spike crashes down onto the red circle, the ground cracks outward and shards of earth are thrown up.
  - Rolling rings: a glowing wall of light rolls out of the tree (jump it).
  - Pollen veil: drifting violet clouds with swirling motes, so you can see where they are.
  - Curse: dark sparks spiral up every fighter.
  - Final blast and the 15% meteor: a real blazing meteor with a trail falls the whole 30 s, then a huge dome, shock wall and column when it lands.

## 0.2.31
- Nerfed the Ravings curse the same way: it can no longer kill a Seq 0. For a Seq 0 the opening sanity loss is 20% instead of 50%, the corruption it piles on every half second is about a third as much, and the fire damage cannot kill. A Seq 1 and weaker take it at full strength and can die. (Its corruption only started actually applying in 0.2.24, which is probably why it suddenly felt much harder.)

## 0.2.30
- Nerfed the Fate curse the same way: it can no longer kill a Seq 0. For a Seq 0 its damage cannot kill, the stacking misfortune is a third as strong and stops (with the bleeding) when they are close to death, and the lethal random effects (wither, poison, bleeding) are left out. A Seq 1 and weaker are hit at full strength and can die.

## 0.2.29
- Nerfed the High-Dimensional Overseer curse: it can no longer kill a Seq 0. A Seq 0 gets slow falling for the fall, never has its sanity drained below 15%, and its damage cannot kill. A Seq 1 (and anyone weaker) can still die to it, the fall included.

## 0.2.28
- Heart seeds can be broken: the click handler that lets you hit the drawn roots was swallowing swings aimed at a seed, because the huge drawn roots lie right over the spots where seeds grow. Swings at heart seeds and root blocks now always go to the block.
- `/cosmos` puts you on the field, not near bedrock: the height was read from a chunk that had not loaded yet. It now loads the chunk and uses the field's fixed height.

## 0.2.27
- Fixed the tree rising and sinking with you when you go to the arena with `/cosmos` (no fight running): with no tree entity around, it was drawn at your own height. It is now drawn at the arena's fixed ground level.

## 0.2.26
- The Mother Tree no longer drifts up and down: hits (knockback) and the hit box changing size could push the mob, and it is now put back on its spot every tick with its velocity zeroed.

## 0.2.25
- New op-only testing command `/astralbarrier freeze` (or `freeze on` / `freeze off`): while on, the barrier takes no damage from the stage timers, the sieges or the gods. `/astralbarrier set` and `add` still work. It is in memory only, so a restart turns it off.

## 0.2.24
- Fixed "the curse could not add corruption": the newer LOTM calls its random-pathway method `chooseRandomPathway`, which the mod did not know, so corruption from the Curse attack (and the god curse, and the hits that corrupt) was never applied. It now finds the new name, and falls back to the player's own pathway if there is none.

## 0.2.23
- Fixed a client crash (ArrayIndexOutOfBounds in the Mother Tree's health bar, in the new under-half glitch): the random helper could return exactly 1.0 and index past the end of a list. It now never reaches 1.0 (the fog cutscene used the same helper).

## 0.2.22
- Heart seeds (and roots) can be broken again. The tree's bigger hit box (0.2.20) covered the spots where they grow, so clicks went to the untouchable tree. While the tree cannot be hurt (roots, seeds, the call, the transformation) its hit box now shrinks to almost nothing, and it grows back afterwards.

## 0.2.21
- The two chat messages around the Mother Tree's transformation ("withers..." and "curses you all...") are gone; the cutscene's on-screen text is all that remains.

## 0.2.20
- The Mother Tree's real hit box is 2.5 times bigger: 125 wide and 375 tall (was 50 x 150).

## 0.2.19
- The two figures are removed from the fog cutscene. The glitching and the red lines go straight into "I've heard your prayers".

## 0.2.18
- The Astral World's background colour is much lower: saturation went from 11x to 2.6x, the "lift everything to full brightness" step is now only a small lift for near-black colours, and the veil is thinner, so the bright colours stop clipping into blue and red.

## 0.2.17
- New op-only command `/astralgod` (or `/astralgod on` / `off`): god mode for testing the tree fight. While on, nothing can damage you and you cannot lose lives, including to the 15% meteor.

## 0.2.16
- On-screen text wraps to the screen width (stage titles and subtitles, the fog prayer, the tree's transformation subtitles), and chat is hidden and blocked during the fog cutscene, so no phase messages or chat cover the words. The prayer is no longer printed in chat.
- The two figures in the fog are now the Fool (top hat, long coat, gold eyes) on the left and the Mother Tree (trunk, branches, crown, red eyes) on the right, facing each other.
- The fog is darker for the whole cutscene.
- The tree's hit box is now the size of its trunk (50 x 150), not the whole canopy.
- The 15% meteor has a cutscene: when it starts to fall every view swings up to it with black bars, shaking and a red glow for 8 s (everyone is immune meanwhile). The meteor then falls slowly, over 30 s instead of 15.

## 0.2.15
- The Astral World's background is 1000% more colourful: saturation is 11 times the original, every colour is lifted to full brightness, and the veil behind the swirls is almost solid across the whole sky, so little to no black shows.

## 0.2.14
- The Astral World's swirling background is 200% more colourful: the saturation of every swirl and of the veil behind them is tripled (brightness unchanged).

## 0.2.13
- Everyone is immune to all damage during the fog cutscene.
- The Mother Tree's hit box is much wider (260 x 150), so it covers the whole canopy and trunk. The roots stay separate.

## 0.2.12
- The fog cutscene now speaks your seven-line prayer in golden text, then the fog starts to glitch (tearing bars, black flickers, colour splits) along with the text, then "Something heard you / Something is watching you / Something is angry" appear in glitching red-and-black gradient text as the fog darkens. Two dark hooded figures with red eyes appear on the left and right, and then "I've heard your prayers" shows in gold before everyone is teleported.
- The Mother Tree's health bar now glitches now and then once she is under half health (torn slices, ghosted name, a broken percentage), since the bar is lying.

## 0.2.11
- No more hit-box entities for the roots (they lagged and looked wrong). Instead, swinging at a root works: the client checks your aim against the roots and the server verifies it and hurts the tree like a normal hit, using your weapon's damage and attack cooldown. Leftover hit boxes from earlier versions are removed.

## 0.2.10
- Root hit boxes are invisible pigs scaled up (slimes are removed in peaceful difficulty and did not work). The server log says how many were built.

## 0.2.9
- The Astral World's background is brighter again: the sky and fog colours are doubled, and the swirls and veil are drawn 1.5 times stronger than 0.2.4 (alpha is capped, so the veil gains less).

## 0.2.8
- /cosmos is operator-only.

## 0.2.7
- The Astral World is back to what it was (the flat astral floor under the swirling sky). The arena is only in the Cosmos.
- /cosmos works again: it takes ops, Door Seq 3+ and the Outer Deity Seq 0s to the Cosmos, which is just the night-sky arena field, and /cosmos leave brings you back.

## 0.2.6
- The sky is the plain vanilla night again (midnight, full stars).
- The roots are part of the tree's hitbox: invisible hit boxes lie along every root (and are rebuilt for the new roots when she changes form). Hitting a root hits the tree; one attack touching many roots still counts once.
- The arena is bigger (radius 260) and the invisible wall is much further out (150 blocks plus more for a crowd).
- The tree has leaves: broad glowing violet and teal ones while healthy, dry curled yellow, brown and red ones when rotten. They move with their limb and flutter.

## 0.2.5
- The Mother Tree arena is in the Cosmos dimension (astral_world:cosmos), a night-sky field with the ruin, separate from the Astral World that Seq 0s visit.

## 0.2.4
- The colourful swirls in the Astral World sky are twice as bright (the veil and every vortex). The gods' signs are unchanged.
- (0.2.3 brightened the world's light instead, which was not what was meant: the light is kept at 0.6 for now.)

## 0.2.3
- The Astral World and the arena are about twice as bright: ambient light 0.3 to 0.6, and the fixed time moves from midnight to early night (13800) so the sky and stars are lighter.

## 0.2.2
- The Mother Tree now animates every attack. Her model is split into trunk, roots, twelve limbs, their blossoms or fruit, and the hanging vines, each moving on its own (a gentle sway at rest too).
- Meteors/ultimate: limbs rise and shudder. Rings: roots heave in waves. Summon: roots writhe, limbs droop. Slam: limbs rear up, then crash down with the trunk squashing and dust. Purge: limbs draw in, then burst out. Temptation: slow reaching sway. Power: limbs tremble and recoil with a bright flash. Curse: purple shudder. Pollen: blossoms puff and shed pollen. Mirror bloom: three limbs glow and sparkle. Fruit rain: the limbs shake, the fruit falls off in waves and regrows.
- Moving limbs shake falling leaves loose (blossom petals while healthy, withered leaves once rotten).

## 0.2.1
- Seq 0 (and Door Seq 3+) can reach the Astral World again: the ascension teleport, the message and /astral are back. Only the Cosmos and the planets stay removed.
- The Astral World is now the same round night field with the ancient ruin as the arena, with the same vanilla night sky.

## 0.2.0
- The end is now a single fight. The planets, the Cosmos, the sovereign bosses, the evacuation and /cosmos are gone.
- The arena is a round grassy field under a vanilla night sky (always midnight), with an ancient ruin in the middle and a ring of broken pillars at the edge. Beyond the field there is only sky.
- When the barrier breaks (phase 5): everyone's view turns to the sky, the world fills with grey fog and drifting coloured lights, and a voice prays to the Fool. Then everyone is taken to the arena, fully healed, and anyone weaker than Sequence 4 is raised to Sequence 4 of their pathway. Everyone receives the Fool's blessing and picks one gift: +50 max HP, +5 attack damage, or +8 armor with +6 armor toughness (/astralblessing).
- Reaching Seq 0 no longer takes anyone to the Astral World, and /astral is operator-only. Door Seq 3 is no longer sent there either.

## 0.1.105
- Every summon is a werewolf (no zombies, spirits or "thralls").
- Accepting Desire's Offer: Sequence 0 in their pathway, +200 max HP, Strength IV, Resistance I, Speed I, Sharpness X on the held item (if lower), and they leave their LOTM faction. Sequence and HP are restored when the raid ends.

## 0.1.104
- Summons are now LOTM giant zombies, werewolves and spirits (fire, frost, earth).
- New attack Pollen Veil: drifting clouds stack Desire (slow, dizzy, sanity drain).
- New attack Mirror Bloom: three flowers, one true. False ones burst for 25% health and stop healing for 10s; the true one cleanses; if none is found all wither.
- New attack Fruit Rain: fruit heals, uncollected fruit sprouts a thrall. After half health the fruit is rotten (25% damage) and thralls are werewolves.
- Desire's Offer (once per fight, below 40%): one fighter may type accept or refuse. Accepting makes them the tree's ally (buffs, thralls ignore them, only they and the others can hurt each other, no damage to the tree, removed from their scoreboard team, leaderboard shows damage to players only). Refusing costs a life.
- The leaderboard now prints on a loss too.

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
