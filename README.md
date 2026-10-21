This is a fork of the archived [iPortalTeam/ImmersivePortalsMod](https://github.com/iPortalTeam/ImmersivePortalsMod), ported to Fabric for Minecraft 26.1 to 26.3. It includes [DimLib](https://github.com/iPortalTeam/DimLib) (Apache-2.0, by qouteall) in `qouteall.dimlib`.

# Immersive Portals Mod

It's a Minecraft mod that provides see-through portals and seamless teleportation. It also can create "Non-Euclidean" (Uneuclidean) space effect.

![immptl.png](https://i.loli.net/2021/09/30/chHMG45dsnZNqep.png)

[On CurseForge](https://www.curseforge.com/minecraft/mc-mods/immersive-portals-mod)     [On Modrinth](https://modrinth.com/mod/immersiveportals)     [Website](https://qouteall.fun/immptl/)

This mod changes a lot of underlying Minecraft mechanics. This mod allows the client to load multiple dimensions at the same time and synchronize remote world information(blocks/entities) to client. It can render portal-in-portals. The portal rendering is roughly compatible with some versions of Sodium and Iris. The portal can transform player scale and gravity direction.  [Implementation Details](https://qouteall.fun/immptl/wiki/Implementation-Details)

(This is the Fabric version of Immersive Portals. [The Forge version](https://github.com/iPortalTeam/ImmersivePortalsModForNeo))

## API

This mod also provides some API for:

* Manage see-through portals
* Dynamically add dimensions
* Synchronize remote chunks to client
* Render the world into GUI
* Other utilities

[API description](https://qouteall.fun/immptl/wiki/API-for-Other-Mods.html).

## How to run this code
https://fabricmc.net/wiki/tutorial:setup

## Development

Java 25 (`export JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64`), one Gradle project per Minecraft version (`26.1.2`, `26.2`, `26.3`), shared run directory `run/`:

```bash
./gradlew :26.3:runServer -PmixinAudit                        # console commands go to stdin
./gradlew :26.3:runClient -PmixinAudit -PquickPlay=<world>    # opens run/saves/<world> directly
```

- `-PmixinAudit` adds `-Dmixin.debug.verify=true -Dimm_ptl.mixin_audit=true`; the mod then calls `MixinEnvironment.audit()` once the server or client has started, which force-loads every mixin target (Fabric Loader 0.20 has no switch for that).
- On a first launch the iPortal intro screen comes before quick play, unless `"initialScreenShown": true` is set in `run/config/immersive_portals.json`.
- Portals are only rendered on the OpenGL backend. On Wayland with NVIDIA, 26.3 fails to create the OpenGL context and falls back to Vulkan; start the client with `env -u WAYLAND_DISPLAY SDL_VIDEODRIVER=x11 ./gradlew ...` there.

## Other

[Wiki](https://qouteall.fun/immptl/wiki/)

[Discord Server](https://discord.gg/BZxgURK)

[Support qouteall on Patreon](https://www.patreon.com/qouteall)

