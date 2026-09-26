First public beta of WebStream Manager.

**What it does**
- Built-in web interface to manage the images shown by WebStreamer screens: image library, screens with fixed addresses, families.
- One profile per world (created automatically), switchable at any time. The image library is shared.
- Multiplayer: optional public port that serves only the images, with a setup and test page in Settings.
- Import / export of profiles (JSON).
- Commands: `/webstream`, `/webstream url <screen>`, `/webstream admin`, `/webstream profile [list|use <id>]`, `/webstream reload`.
- Interface, errors and commands in English, French and Spanish.
- Fully self-contained: no Node.js, no database.

**Beta notice**
- Tested with automated tests and in a real browser. Still being validated in game on a dedicated server with several players: please report any issue on GitHub.
- Upgrading from 1.0.x: the configuration file is migrated automatically; the old Node.js based version is no longer needed.

**Requirements:** Minecraft 1.20.4, Fabric Loader 0.15+, Fabric API, Java 17. WebStreamer is recommended.
