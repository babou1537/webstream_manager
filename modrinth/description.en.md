# WebStream Manager

**Change the images shown on your WebStreamer screens from a web page. No config files, no external tools, nothing to install besides the mod.**

WebStream Manager runs a small web interface inside Minecraft. You upload images, create screens that each get a fixed address, and paste that address into a **WebStreamer** display block once. From then on, you manage what your screens show from your browser.

It is built as a companion to the **WebStreamer** mod (it is not affiliated with it).

## Features

- **Image library** with drag and drop, lightweight cached thumbnails, and a view of which screens use each image.
- **Screens with a fixed address** (`http://your-server:8283/metro-station-1.png`). Every screen keeps the aspect ratio of its resolution, so you see the image stretched exactly like in game.
- **Families** to organize screens (Metro, Park, Museum...).
- **Profiles**: one setup (families and screens) per world, created automatically the first time you open a world. Switch profiles at any time from the sidebar.
- **Multiplayer setup wizard** in the Settings page: open a public port that serves **only** the images, set your public address, test it. No file editing.
- **Import / export** of a profile as a JSON file.
- **In-game commands** with clickable links: `/webstream url <screen>`, `/webstream profile use <id>`...
- **Three languages**: English, Français, Español (interface, error messages and commands).
- **Self-contained**: the web server is built into the mod. No Node.js, no database, no extra download.

## How it works

1. Open the interface: press **H** in game, use the **WebStream** button of the pause menu, run `/webstream admin`, or browse to `http://localhost:8282`.
2. **Library**: upload your images. **Screens**: create a screen, give it a resolution, assign an image, and copy its address.
3. Paste the address into a **WebStreamer** display block. Done. To change the picture later, you only touch the web page.

## Profiles

A profile holds the families and screens of one setup. The image library is shared by all profiles.

By default a new profile is created for each world you open, and that world remembers it. You can duplicate a profile, rename it, export it, or activate another one at any time. Because screen addresses never change, switching profile changes what those same addresses serve.

## Playing with other people

WebStreamer downloads the images **from each player's computer**, so every player must be able to reach the address you paste into the block. `localhost` only works for the host.

WebStream Manager solves this with two separate ports:

| Port | Serves | Who can reach it |
|---|---|---|
| `8282` (interface) | the administration web page | this machine only, or a password |
| `8283` (public, optional) | **the images only** | everyone, no password |

In **Settings**, turn on "Open a public port", enter your public address (for example `http://my-server.com:8283`), and use **Test**. Then open or forward that port on your router, firewall or tunnel service. The administration page never has to be exposed.

## Commands

| Command | What it does | Who |
|---|---|---|
| `/webstream` | active profile, world and image address | everyone |
| `/webstream url <screen>` | clickable link to a screen's image | everyone |
| `/webstream profile` | active profile | everyone |
| `/webstream profile list` / `use <id>` | list / activate a profile (remembered for the current world) | operators |
| `/webstream admin` | link to the interface | operators |
| `/webstream reload` | reload profiles from disk | operators |

## Requirements

| | |
|---|---|
| Minecraft | 1.20.4 |
| Loader | Fabric (0.15 or newer) |
| Required | Fabric API |
| Recommended | the **WebStreamer** mod (this is what actually displays the images) |
| Java | 17 or newer |
| Side | **Server: required.** Client: optional (it adds the H key and the pause menu button). In singleplayer, install it in your instance as usual. |

## Good to know

- **WebStreamer keeps one cached copy of an image per exact address** and reloads it after a while, or when the address changes. A new picture can therefore take a moment to appear on a screen that is already loaded. WebStream Manager adds `?refresh=1` to the addresses it gives you; you can change or remove this parameter in Settings.
- A screen **without an image** answers "not found" by default: WebStreamer then retries every 30 seconds and shows the image as soon as you assign one. You can switch to a "NO SIGNAL" placeholder image in Settings, but WebStreamer will keep that placeholder cached until the address changes.
- Exports contain families and screens, **not the images** themselves.
- This is a **beta**. Please report anything odd.

## Security

- The administration page listens on `127.0.0.1` by default. A request only counts as "local" if it comes from the machine **and** uses a local host name, so tunnels and DNS-rebinding attacks must give the password.
- Cross-site requests are refused, uploads are size-limited and checked (the content must be a real image), and file names are sanitized.
- The password is sent in clear over plain HTTP. If you expose the interface on the Internet, put an HTTPS reverse proxy in front.

## Languages

English, Français and Español. Choose in **Settings > Language**. The default is your system language (English if it is not one of the three).

## Links

- Source code and issue tracker: <https://github.com/babou1537/webstream_manager>
- License: MIT
- The interface uses the pixel fonts *Pixelify Sans*, *Silkscreen* and *VT323* (SIL Open Font License 1.1), bundled in the mod. The logo and screen artwork were drawn for this mod.
