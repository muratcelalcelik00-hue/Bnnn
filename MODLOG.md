# MODLOG — Polyfield → Minecraft Fabric 1.20.1

## Input
- `com.MA.Polyfield_0.7.5.7z.001..008` → `com.MA.Polyfield_0.7.5.apks` (base.apk + split_config.arm64_v8a.apk).
- Unity 2022.3.62f3, IL2CPP (metadata v31), Android. Data: `assets/bin/Data/data.unity3d` (bundle with
  globalgamemanagers, sharedassets0-3, level0-3, resources.assets) + external `*.resource` (audio).
- Online-capable game: only its files are read, offline. Nothing is launched, patched or hooked.

## Route
Mashup pattern 1 (port the content) with a converter run on the user's own copy. Host = Fabric 1.20.1
mod; guest assets → private resource dir packed into the user's personal jar, never committed.

## What the game data holds
- Weapons: `<Name>_Prop` (world/third-person model, flat material colours, no textures) and
  `<Name>_Prefab` (first person + `Weapon` MonoBehaviour). Props are ~1.65x real size → scale 0.6.
- `Weapon` fields (metadata order): weaponClass, allowedClass(string), model, fireMode, fireRate(rounds/s),
  bulletSpeed, range, damage, magnification, aimFOV, aimZoffset, boltAction, [needBoltAction not serialized],
  bulletShellDelay, ..., forces(Vector3 recoil), isReloading, isBoltAction, fireCoolDown, MAG_SIZE, CLIP_SIZE,
  ammo(magazine), mag(reserve), hasSoundLoop, pitchOffset, reloadSound, weaponSoundSub, Close, Tail, Distant,
  boltActionSound. Tail decoded by anchoring on the run of AudioClip PPtrs.
- Launchers use class `Gadget` (fireRate, bulletSpeed, range, damage, …, ammo 1, mag 3/6).
- Soldiers: `PolySoldier_GER`, `PolySoldier_USA` — Mixamo rig (22 bones + Armature), 3 SkinnedMeshRenderers
  (body, helmet, backpack) with **different bind poses per renderer** (bindpose includes renderer transform).
  Helmet uses a 1-component bone-index channel and no weight channel → weight 1.
- Animation clips are humanoid muscle clips (binding typeID 95) → not evaluated; procedural pose + IK instead.
- Tanks: `Tank_ShermanFirefly`, `Tank_Jadgpanther`: `_Turret` (yaw), `_Cannon` (pitch), `_Cannon_Explosion`
  (muzzle), `_Seats_*`; tracks are skinned meshes, baked in prefab pose.
- Higgins boat in resources.assets (ramp at -X; rotated to +Z, scaled 0.75).
- Audio: all FSB5 Vorbis (203 stereo, 9 mono) → fsb5.rebuild_sample → ffmpeg mono OGG.

## Gotchas
1. `fsb5` 1.0 sdist fails to build on modern setuptools (`install_layout`); copy the pure-Python package.
2. UnityPy `TypeTreeGeneratorAPI` failed on this libil2cpp ("Sequence contains no matching element");
   metadata parsed by hand instead (typedef size 88 bytes for v31).
3. Skinned parts must use their own renderer's bind poses, or helmet/backpack land 1.2 m off.
4. Network policy of the build container blocked maven.fabricmc.net and Mojang hosts (403) and GitHub's
   blob storage (artifacts/logs). Builds run in GitHub Actions instead; CI commits the code-only jar,
   logs and screenshots to `ci-out/` on the branch, and `converter/pack_jar.py` adds the locally
   converted assets to that jar.
5. Weapon origin must be the trigger's x, not the bounding-box centre (MG42 drum, Sten side magazine).
6. ADS line of sight = top of the receiver (+1.2 cm), not muzzle height: otherwise the stock hides the view.
7. `Entity.getRandom()` does not exist in 1.20.1 (only LivingEntity); a YAML plain scalar containing
   `: ` silently breaks a workflow (run shows 0 jobs).

## Verification so far
- Converter: 25 models (16 weapons, 4 projectiles, 2 soldiers, 2 tanks, Higgins boat — the boat is converted but not yet an entity), 78 sound events; preview sheet rendered and inspected; real-world sizes checked
  (Kar98k 1.11 m, MP40 0.86 m, P38 0.20 m, MG42 1.13 m).
- Soldier IK pose prototyped in Python (hold, aim up/down, walk) and inspected.
- Java code checked against Yarn 1.20.1 mappings (class names, member names, descriptors).
- PFM/PFS files re-read by a standalone Java reader (indices in range, weights sum to 1, no trailing bytes).
- First-person framing simulated with FOV 70 perspective (hip and ADS) for every weapon class.
- GitHub Actions (Loom 1.7.4, Gradle 8.10.2, JDK 17):
  - `gradle build` passes;
  - dev server self-test: 10 entities spawned, 16/16 weapons fired and reloaded, soldiers of both
    factions fought (8 → 7 alive after 27 s), tank damage applied → PASS;
  - production server: the shipped (intermediary) jar on Fabric Loader 0.16.10 + Fabric API → PASS;
  - real client under xvfb: mixin audit clean, MP40 fired (32 → 14 rounds), reload, crossbow-hold pose,
    Sherman mounted and cannon fired (HUD reload timer) → PASS, 5 screenshots inspected.
- Not verified: rendering with the converted Polyfield assets in a real client (CI has no game files);
  the final personal jar has not been launched.
