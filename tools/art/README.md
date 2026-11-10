# Weapon art tools and session notes

These scripts made the Uniqueness weapon art. They are NOT part of the build. Paths inside them point at `/tmp/r3` (sdf), `/tmp/px` (pixel), `/tmp/mesh`, `/tmp/vox`:
copy `sdf/*` to `/tmp/r3` and `pixel/*` to `/tmp/px` to run them. Needs numpy, scipy, scikit-image, Pillow, ffmpeg.

- `sdf/` : signed-distance-field scenes (`m2`/`m3`/`m4`/`m5` builders, `eng` renderer), `meshgen.py` (marching cubes to `assets/astral_world/meshes/*.bin`, vertex colours with baked lighting), `hold.py` (hand display transforms from grip points), `export.py` (writes models, meshes, `UniqAnim.java`). `vox.py` is the older voxel path.
- `pixel/` : `door5.py` is the current Door sword (128x128, 8 frames, outputs a strip + gif). `px2.py` is the 64px draft framework for the other swords.

## State at the end of this session (mod 0.2.172)
- Door (item id `starry_sniper`, shown as "Rapier of the Door") is the pixel sword: flat two-sided plane model, animated texture `starry_sniper.png` + mcmeta, class `DoorRapier` (fast, reach, 3-hit combo, right-click dash lunge, sneak+hold still accommodates), `DoorClient` (first-person thrust, star trails, whoosh), star dot particle `PartyItems.STAR` / `PartyParticles.StarDot`. Sounds are vanilla. Untested in game; lag and hold position were still being tuned.
- The other 7 weapons (shadow_sword, spear_of_sun, storm_katana, bone_greatsword, snake_rifle, chained_bow, twilight_longsword) are still the smooth baked-colour meshes (`UniqClient`, `meshes/*.bin`). The user disliked them and decided to switch ALL weapons to pixel swords, done ONE AT A TIME with the user choosing blade, guard and hilt styles first (AskUserQuestion). The quill stays as the original flat icon. Shield of Shadow stays a model.
- Pixel sword design lessons from the user: not blocky (128px), not all at once, short and fast for Door, no ball pommel, no brown handle, it should "feel" like its pathway, animate the blade.
- Door answers used: slim rapier blade, swept-back wings, midnight-blue wrap with silver wire, faceted star-gem pommel.
- Next weapons to do as pixel swords, in order of the user's choice: unknown (ask). Drafts for all nine are in `pixel/px2.py` (user rejected them).
- Still open from before: Monster 1 grey-white box, "bug attached to darkness ones", Arbiter scale/other bugs, Warrior 1 sword direction.

## Routine per change
Bump `mod_version` in `gradle.properties`; add a `## x.y.z` entry in `CHANGELOG.md` after the header paragraph above the previous entry; commit with the Co-Authored-By and Claude-Session trailers; push; poll `gh run list -L 1` (CI about 1 minute; `gh run view <id> --log-failed` on failure). Always say it is untested in game: builds are CI only and the user tests and sends screenshots.
