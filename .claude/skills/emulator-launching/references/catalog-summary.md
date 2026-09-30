# Emulator catalogue summary

Generated 2026-09-30 by `scripts/build_emulator_catalog.py` from:
- Daijishō platform definitions (MIT, revision `cde33bb64af2`): https://github.com/TapiocaFox/Daijishou/tree/main/platforms
- ES-DE Android find rules (MIT, revision `master`): https://gitlab.com/es-de/emulationstation-de/-/raw/master/resources/systems/android/es_find_rules.xml

121 platforms, 958 emulator recipes, 151 ES-DE package/activity pairs, 1 warnings.

Regenerate with `python scripts/build_emulator_catalog.py` (add `--offline` to reuse `scripts/.cache`).

## Platforms

| id | name | extensions | emulators |
|---|---|---|---|
| `3do` | 3DO | chd, cue, iso | 3 |
| `cpc` | Amstrad CPC | cdt, cpr, dsk, kcr, m3u, sna, tap, voc, zip | 6 |
| `appleii` | Apple II | cmd | 2 |
| `fbneo` | Arcade (FinalBurn Neo) | 7z, ccd, cue, iso, zip | 6 |
| `mame` | Arcade (MAME) | 7z, chd, cmd, zip | 20 |
| `arcadia` | Arcadia 2001 | cmd, zip | 3 |
| `arduboy` | Arduboy | arduboy, hex | 6 |
| `atari2600` | Atari 2600 | 7z, a26, bin, zip | 11 |
| `atari5200` | Atari 5200 | 7z, a52, atr, atx, bin, car, cas, cdm, com, rom, xex, xfd… | 6 |
| `atari7800` | Atari 7800 | 7z, a78, bin, zip | 3 |
| `jaguar` | Atari Jaguar | 7z, abs, bin, cof, j64, jag, prg, rom, zip | 4 |
| `jaguarcd` | Atari Jaguar CD | 7z, abs, bin, cof, j64, jag, prg, rom, zip | 4 |
| `lynx` | Atari Lynx | 7z, lnx, o, zip | 10 |
| `atarist` | Atari ST | dim, ipf, m3u, msa, st, stx, zip | 3 |
| `atomiswave` | Atomiswave | 7z, bin, cdi, chd, cue, dat, elf, gdi, iso, lst, m3u, zip | 7 |
| `bbcmicro` | Acorn Computers BBC Micro | dsd, ssd | 3 |
| `ebook` | Book Reader | azw, azw3, cbr, cbz, djvu, doc, docx, epub, epub3, fb2, html, mobi… | 5 |
| `cps1` | CP System I | 7z, iso, zip | 11 |
| `cps2` | CP System II | 7z, iso, zip | 10 |
| `cps3` | CP System III | 7z, iso, zip | 10 |
| `cannonball` | Cannonball OutRun Engine | 88, game | 3 |
| `cavestory` | Cave Story Game Engine | exe | 6 |
| `chip8` | CHIP-8 | ch8, zip | 3 |
| `coleco` | ColecoVision | 7z, bin, cas, col, cv, dsk, m3u, mx1, mx2, ri, rom, sc… | 6 |
| `c64` | Commodore 64 | bin, cmd, crt, d2m, d4m, d64, d6z, d71, d7z, d80, d81, d82… | 13 |
| `amiga` | Commodore Amiga | 7z, adf, adz, ccd, chd, cue, dms, fdi, hdf, hdz, info, ipf… | 6 |
| `pet` | Commodore - PET | bin, cmd, crt, d2m, d4m, d64, d6z, d71, d7z, d80, d81, d82… | 3 |
| `plus4` | Commodore - PLUS/4 | bin, cmd, crt, d2m, d4m, d64, d6z, d71, d7z, d80, d81, d82… | 3 |
| `doom` | DOOM Game Engine | iwad, pwad, wad, zip | 3 |
| `dos` | DOS | bat, com, conf, cue, dosz, exe, ima, img, ins, iso, m3u, m3u8… | 9 |
| `elektor` | Elektor TV Games Computer |  | 0 |
| `channelf` | Fairchild Channel F | bin, chf, zip | 3 |
| `fds` | Famicom Disk System | 7z, bin, fds, nes, nsf, qd, rom, unf, unif, zip | 11 |
| `flash` | Flash Player | swf, swiffid | 4 |
| `flashback` | Flashback Game Engine | aba, lev, map, seq | 3 |
| `idtech` | idTech4A++ | idtech | 1 |
| `intellivision` | Intellivision | 7z, bin, int, rom, zip | 3 |
| `vc4000` | Interton VC 4000 | cmd, zip | 3 |
| `j2me` | Java Me | jad, jam, jar, java, kjx, sqc | 5 |
| `lowresnx` | LowRes NX | nx, zip | 3 |
| `msx` | MSX | 7z, cas, col, dsk, fdi, m3u, mx1, mx2, ri, rom, sc, sg… | 7 |
| `odyssey2` | Magnavox Odyssey 2 | 7z, bin, zip | 3 |
| `megaduck` | Mega Duck | bin, zip | 3 |
| `xbox` | Microsoft - Xbox | iso, xiso | 4 |
| `xbox360` | Microsoft - Xbox 360 | iso, x360url, xbla, xex | 7 |
| `moonlight` | Moonlight Streaming | art, moonlight | 2 |
| `pc88` | NEC PC-88 | d88, m3u, u88 | 3 |
| `pc98` | NEC PC-98 | 2hd, 88d, 98d, cmd, cue, d88, d98, dup, fdd, fdi, hdd, hdi… | 6 |
| `pcfx` | NEC PC-FX | ccd, chd, cue, toc | 3 |
| `neogeo` | Neo Geo | 7z, iso, neo, zip | 10 |
| `neogeocd` | Neo Geo CD | chd, cue | 6 |
| `ngp` | NeoGeo Pocket | 7z, ngc, ngp, zip | 7 |
| `ngpc` | NeoGeo Pocket Color | 7z, ngc, ngp, zip | 7 |
| `ngage` | Nokia N-Gage | json | 1 |
| `3ds` | Nintendo 3DS | 3ds, 3dsx, app, axf, cci, cxi, elf, zcci, zcxi | 19 |
| `n64` | Nintendo 64 | 7z, bin, n64, ndd, u1, v64, z64, zip | 13 |
| `nds` | Nintendo DS | 7z, bin, nds, rar, zip | 24 |
| `ndsi` | Nintendo DSi | 7z, bin, nds, rar, zip | 17 |
| `nes` | Nintendo Entertainment System | 7z, bin, fds, nes, nsf, qd, rom, unf, unif, zip | 17 |
| `gw` | Nintendo - Game & Watch | 7z, mgw, zip | 6 |
| `gb` | Nintendo - Game Boy | 7z, bin, cgb, dmg, gb, gbc, gbs, rom, sgb, zip | 34 |
| `gba` | Nintendo - Game Boy Advance | 7z, agb, bin, cgb, dmg, gb, gba, gbc, gbz, sgb, zip | 31 |
| `gbc` | Nintendo - Game Boy Color | 7z, bin, cgb, dmg, gb, gbc, gbs, rom, sgb, zip | 31 |
| `gc` | Nintendo - GameCube | ciso, dff, dol, elf, gcm, gcz, iso, m3u, rvz, tgc, wad, wbfs… | 13 |
| `satellaview` | Nintendo Satellaview | 7z, bml, bs, bsx, dx2, fig, gb, gbc, gd3, gd7, rom, sfc… | 28 |
| `switch` | Nintendo Switch | nca, nro, nso, nsp, xci | 15 |
| `wii` | Nintendo Wii | ciso, dff, dol, elf, gcm, gcz, iso, json, m3u, rvz, tgc, wad… | 13 |
| `wiiu` | Nintendo Wii U | rpx, wua, wud, wux | 1 |
| `wiiware` | Nintendo WiiWare | wad | 11 |
| `pico8` | PICO-8 | p8, png | 9 |
| `palm` | Palm OS | img, pdb, pqa, prc | 3 |
| `cdi` | Philips CD-i | chd, cue, iso | 3 |
| `g7400` | Philips Videopac+ G7400 | 7z, bin, zip | 3 |
| `pokemini` | Pokemon Mini | 7z, min, zip | 3 |
| `ports` | Ports | port | 1 |
| `quake` | Quake Game Engine | pak | 3 |
| `quake2` | Quake II Game Engine | pak | 12 |
| `rpgmaker` | RPG Maker | easyrpg, ldb, zip | 3 |
| `scummvm` | ScummVM | dpt, scummvm, sh, svm | 5 |
| `sega32x` | Sega 32X | 32x, 68k, 7z, bin, chd, cue, gen, gg, iso, m3u, md, pco… | 3 |
| `segacd` | Sega CD | 32x, 68k, 7z, bin, bms, chd, cue, gen, gg, iso, m3u, md… | 10 |
| `dreamcast` | Sega Dreamcast | 7z, bin, cdi, chd, cue, elf, gdi, lst, m3u, zip | 8 |
| `gamegear` | Sega Game Gear | 32x, 68k, 7z, bin, bms, chd, col, cue, gen, gg, iso, m3u… | 18 |
| `genesis` | Sega Genesis | 32x, 68k, 7z, bin, bms, chd, cue, gen, gg, iso, m3u, md… | 12 |
| `genesismsu` | Sega Genesis - MSU | 7z, md, zip | 6 |
| `master` | Sega Master System | 32x, 68k, 7z, bin, bms, chd, col, cue, gen, gg, iso, m3u… | 17 |
| `model3` | Sega Model 3 | 7z, zip | 4 |
| `naomi` | Sega Naomi | 7z, dat, lst, zip | 5 |
| `pico` | Sega Pico | 7z, md, zip | 3 |
| `sg1000` | Sega SG-1000 | 7z, bin, cas, col, dsk, gg, m3u, mx1, mx2, ri, rom, sc… | 6 |
| `saturn` | Sega Saturn | bin, ccd, chd, cue, iso, m3u, mds, toc, zip | 16 |
| `stv` | Sega Titan Video | zip | 3 |
| `x1` | Sharp X1 | 2hd, 7z, 88d, cmd, d88, dim, dup, hdf, hdm, img, m3u, xdf… | 3 |
| `x68000` | Sharp X68000 | 2hd, 7z, 88d, cmd, d88, dim, dup, hdf, hdm, img, m3u, xdf… | 3 |
| `psp` | Sony PSP | chd, cso, elf, iso, pbp, prx | 7 |
| `pspminis` | Sony PSP Minis | chd, cso, elf, iso, pbp, prx | 7 |
| `vita` | Sony PS Vita | dpt | 4 |
| `psx` | Sony PlayStation | bin, cbn, ccd, chd, cue, ecm, exe, img, iso, m3u, mdf, mds… | 24 |
| `ps2` | Sony PlayStation 2 | bin, chd, ciso, cso, elf, gz, img, irx, iso, isz, mdf, nrg… | 17 |
| `ps3` | Sony PlayStation 3 | iso, ps3folder, ps3titleid | 7 |
| `steam` | Steam | steamappid | 7 |
| `supergrafx` | SuperGrafx | 7z, ccd, chd, cue, m3u, pce, sgx, toc, zip | 7 |
| `snes` | Super Nintendo Entertainment System | 7z, bml, bs, bsx, dx2, fig, gb, gbc, gd3, gd7, rom, sfc… | 58 |
| `snesmsu1` | Super Nintendo Entertainment System - MSU-1 | 7z, bml, bs, bsx, dx2, fig, gb, gbc, gd3, gd7, rom, sfc… | 13 |
| `tic80` | TIC-80 | tic | 3 |
| `triforce` | Triforce | gcm, gcz, iso, wia | 3 |
| `tg16` | TurboGrafx-16 | 7z, ccd, chd, cue, m3u, pce, sgx, toc, zip | 7 |
| `tgcd` | TurboGrafx-CD | 7z, ccd, chd, cue, m3u, pce, sgx, toc, zip | 7 |
| `uzebox` | Uzebox | 7z, uze, zip | 3 |
| `vic20` | VIC-20 | 20, 40, 60, a0, b0, bin, cmd, crt, d2m, d4m, d64, d6z… | 3 |
| `vectrex` | Vectrex | 7z, bin, vec, zip | 3 |
| `videos` | Video Player | avi, m4a, mkv, mov, mp4, mpeg, mpg, webm | 10 |
| `virtualboy` | Virtual Boy | 7z, bin, vb, vboy, zip | 4 |
| `wasm4` | WASM-4 | wasm | 3 |
| `supervision` | Watara Supervision | 7z, bin, sv, zip | 3 |
| `windows` | Windows | amazon, customgame, desktop, epicgame, gog, lnk, localgameid, steamappid | 15 |
| `ws` | WonderSwan | 7z, pc2, ws, wsc, zip | 4 |
| `wsc` | WonderSwan Color | 7z, pc2, ws, wsc, zip | 4 |
| `xcloud` | XBox Game Pass | xcloud | 1 |
| `zx81` | ZX 81 | 7z, p, t81, tzx, zip | 3 |
| `zxspectrum` | ZX Spectrum | 7z, dsk, rzx, scl, tap, trd, tzx, z80, zip | 3 |

## Packages seen

- `Ali.Xanite`: xbox, xbox360
- `Ali.Xanite.green`: xbox, xbox360
- `aenu.aps3e`: ps3
- `aenu.ax360e`: xbox360
- `aenu.ax360e.free`: xbox360
- `app.gamenative`: steam, windows
- `banner.hub`: steam, windows
- `banner.hub.lite`: steam, windows
- `com.PceEmu`: supergrafx, tg16, tgcd
- `com.amigan.droidarcadia`: arcadia, vc4000
- `com.androidemu.atari`: atari2600
- `com.androidemu.gens`: genesis
- `com.androidemu.gg`: gamegear
- `com.androidemu.nes`: fds, nes
- `com.antutu.ABenchMark`: 3ds, steam, windows
- `com.armsx2`: ps2
- `com.armsx3`: ps3
- `com.cmodded.winlator`: windows
- `com.dsemu.drastic`: nds, ndsi
- `com.emulator.fpse`: psx
- `com.emulator.fpse64`: psx
- `com.epsxe.ePSXe`: psx
- `com.explusalpha.A2600Emu`: atari2600
- `com.explusalpha.C64Emu`: c64
- `com.explusalpha.GbaEmu`: gba
- `com.explusalpha.GbcEmu`: gb, gbc
- `com.explusalpha.LynxEmu`: lynx
- `com.explusalpha.MdEmu`: genesis, master, segacd
- `com.explusalpha.MsxEmu`: msx
- `com.explusalpha.NeoEmu`: neogeo
- `com.explusalpha.NesEmu`: fds, nes
- `com.explusalpha.NgpEmu`: ngp, ngpc
- `com.explusalpha.SaturnEmu`: saturn
- `com.explusalpha.Snes9xPlus`: satellaview, snes, snesmsu1
- `com.explusalpha.SwanEmu`: ws, wsc
- `com.explusalpha.neoemu`: cps1, cps2, cps3
- `com.fastemulator.gba`: gba
- `com.fastemulator.gbafree`: gba
- `com.fastemulator.gbc`: gb, gbc
- `com.fastemulator.gbcfree`: gb, gbc
- `com.flycast.emulator`: atomiswave, dreamcast, naomi
- `com.fms.mg`: gamegear
- `com.foobnix.pdf.reader`: ebook
- `com.foobnix.pro.pdf.reader`: ebook
- `com.github.axet.bookreader`: ebook
- `com.github.eka2l1`: ngage
- `com.github.stenzek.duckstation`: psx
- `com.hydra.noods`: nds, ndsi
- `com.issess.flashplayer`: flash
- `com.issess.flashplayerpro`: flash
- `com.izzy2lost.super3`: model3
- `com.izzy2lost.x1box`: xbox
- `com.joeyos.dolphinemu`: gc, triforce, wii, wiiware
- `com.karin.idTech4Amm`: idtech
- `com.limelight`: moonlight
- `com.limelight.noir`: moonlight
- `com.ludashi.aibench`: steam, windows
- `com.miHoYo.Yuanshen`: switch
- `com.micewine.emu`: windows
- `com.mxtech.videoplayer.ad`: videos
- `com.mxtech.videoplayer.pro`: videos
- `com.nanodata.armsx`: psx
- `com.pixelrespawn.linkboy`: gb, gba, gbc
- `com.reicast.emulator`: atomiswave, dreamcast
- `com.retroarch`: 3do, 3ds, amiga, appleii, arcadia, arduboy, atari2600, atari5200, atari7800, atarist, atomiswave, bbcmicro, c64, cannonball, cavestory, cdi, channelf, chip8, coleco, cpc, cps1, cps2, cps3, doom, dos, dreamcast, fbneo, fds, flashback, g7400, gamegear, gb, gba, gbc, gc, genesis, genesismsu, gw, intellivision, j2me, jaguar, jaguarcd, lowresnx, lynx, mame, master, megaduck, model3, msx, n64, naomi, nds, ndsi, neogeo, neogeocd, nes, ngp, ngpc, odyssey2, palm, pc88, pc98, pcfx, pet, pico, pico8, plus4, pokemini, ps2, psp, pspminis, psx, quake, quake2, rpgmaker, satellaview, saturn, scummvm, sega32x, segacd, sg1000, snes, snesmsu1, stv, supergrafx, supervision, tg16, tgcd, tic80, uzebox, vc4000, vectrex, vic20, videos, virtualboy, wasm4, wii, wiiware, ws, wsc, x1, x68000, zx81, zxspectrum
- `com.retroarch.aarch64`: 3do, 3ds, amiga, appleii, arcadia, arduboy, atari2600, atari5200, atari7800, atarist, atomiswave, bbcmicro, c64, cannonball, cavestory, cdi, channelf, chip8, coleco, cpc, cps1, cps2, cps3, doom, dos, dreamcast, fbneo, fds, flashback, g7400, gamegear, gb, gba, gbc, gc, genesis, genesismsu, gw, intellivision, j2me, jaguar, jaguarcd, lowresnx, lynx, mame, master, megaduck, model3, msx, n64, naomi, nds, ndsi, neogeo, neogeocd, nes, ngp, ngpc, odyssey2, palm, pc88, pc98, pcfx, pet, pico, pico8, plus4, pokemini, ps2, psp, pspminis, psx, quake, quake2, rpgmaker, satellaview, saturn, scummvm, sega32x, segacd, sg1000, snes, snesmsu1, stv, supergrafx, supervision, tg16, tgcd, tic80, uzebox, vc4000, vectrex, vic20, videos, virtualboy, wasm4, wii, wiiware, ws, wsc, x1, x68000, zx81, zxspectrum
- `com.retroarch.ra32`: 3do, amiga, arduboy, atari2600, atari5200, atari7800, atarist, atomiswave, bbcmicro, c64, cannonball, cavestory, cdi, channelf, chip8, coleco, cpc, cps1, cps2, cps3, doom, dos, dreamcast, fbneo, fds, flashback, g7400, gamegear, gb, gba, gbc, genesis, genesismsu, gw, intellivision, j2me, jaguar, jaguarcd, lowresnx, lynx, mame, master, megaduck, model3, msx, n64, naomi, nds, ndsi, neogeo, neogeocd, nes, ngp, ngpc, odyssey2, palm, pc88, pc98, pcfx, pet, pico, pico8, plus4, pokemini, ps2, psp, pspminis, psx, quake, quake2, rpgmaker, satellaview, saturn, scummvm, sega32x, segacd, sg1000, snes, snesmsu1, stv, supergrafx, supervision, tg16, tgcd, tic80, uzebox, vectrex, vic20, videos, virtualboy, wasm4, ws, wsc, x1, x68000, zx81, zxspectrum
- `com.rfandango.haku_x`: xbox
- `com.sa_moo_rai.picpic`: pico8
- `com.sbro.emucorea`: psp, pspminis
- `com.sbro.emucorec`: ps3
- `com.sbro.emucoreh`: dreamcast
- `com.sbro.emucorer`: psx
- `com.sbro.emucorev`: vita
- `com.sbro.emucorex`: ps2
- `com.seleuco.mame4d2024`: gw, mame
- `com.seleuco.mame4droid`: mame
- `com.simongellis.vvb`: virtualboy
- `com.sky.SkyEmu`: gb, gba, gbc, nds
- `com.studio08.xbgamestream`: xcloud
- `com.tencent.ig`: steam, windows
- `com.winlator`: windows
- `com.winlator.cmod`: windows
- `come.nanodata.armsx2`: ps2
- `come.nanodata.armsx2.debug`: ps2
- `dev.anilbeesetti.nextplayer`: videos
- `dev.eden.eden_emulator`: switch
- `dev.eden.eden_emulator.nightly`: switch
- `dev.eden.eden_nightly`: switch
- `dev.legacy.eden_emulator`: switch
- `dev.suyu.suyu_emu.relWithDebInfo`: switch
- `emu.x360.mobile`: xbox360
- `emu.x360mobile.com`: xbox360
- `gamehub.lite`: steam, windows
- `info.cemu.cemu`: wiiu
- `io.github.azaharplus.android`: 3ds
- `io.github.borked3ds.android`: 3ds
- `io.github.lime3ds.android`: 3ds
- `io.github.mandarine3ds.mandarine`: 3ds
- `io.navivani.swiff`: flash
- `io.recompiled.redream`: atomiswave, dreamcast
- `io.wip.pico8`: pico8
- `is.xyz.mpv`: videos
- `it.dbtecno.pizzaboy`: gb, gbc
- `it.dbtecno.pizzaboygba`: gba
- `it.dbtecno.pizzaboygbapro`: gba
- `it.dbtecno.pizzaboypro`: gb, gbc
- `it.dbtecno.pizzaboyscpro`: gamegear, genesis, master
- `me.dt2dev.infinity`: pico8
- `me.magnum.melonds`: nds, ndsi
- `me.magnum.melonds.dev`: nds, ndsi
- `me.magnum.melonds.nightly`: nds, ndsi
- `me.magnum.melondualds`: nds, ndsi
- `org.azahar_emu.azahar`: 3ds
- `org.benjisc.android`: switch
- `org.citra.citra_emu`: 3ds
- `org.citra.citra_emu.canary`: 3ds
- `org.citra.emu`: 3ds
- `org.citron.citron_emu`: switch
- `org.courville.nova`: videos
- `org.devmiyax.yabasanshioro2`: saturn
- `org.devmiyax.yabasanshioro2.pro`: saturn
- `org.dolphin.ishiirukadark`: gc, wii, wiiware
- `org.dolphinemu.dolphinemu`: gc, triforce, wii, wiiware
- `org.dolphinemu.dolphinemu.debug`: gc, triforce, wii, wiiware
- `org.dolphinemu.handheld`: gc, wii, wiiware
- `org.dolphinemu.mmjr`: gc, wii, wiiware
- `org.dolphinemu.mmjr3`: gc, wii, wiiware
- `org.dolphinemu.primehack`: gc, wii
- `org.force9.starboard`: ports
- `org.gamerytb.lemonade.canary`: 3ds
- `org.kenjinx.android`: switch
- `org.koreader.launcher`: ebook
- `org.mm.j`: gc, wii, wiiware
- `org.mm.jr`: gc, wii, wiiware
- `org.mupen64plusae.v3.alpha`: n64
- `org.mupen64plusae.v3.fzurita`: n64
- `org.mupen64plusae.v3.fzurita.pro`: n64
- `org.ppsspp.ppsspp`: psp, pspminis
- `org.ppsspp.ppssppgold`: psp, pspminis
- `org.ppsspp.ppsspplegacy`: psp, pspminis
- `org.readera`: ebook
- `org.scummvm.scummvm`: scummvm
- `org.scummvm.scummvm.debug`: scummvm
- `org.shiiion.primehack`: gc, wii
- `org.stratoemu.strato`: switch
- `org.sudachi.sudachi_emu`: switch
- `org.sudachi.sudachi_emu.ea`: switch
- `org.uoyabause.android`: saturn
- `org.uoyabause.android.pro`: saturn
- `org.videolan.vlc`: videos
- `org.vita3k.emulator`: vita
- `org.vita3k.emulator.ikhoeyZX`: vita
- `org.vita3kplus.emulator`: vita
- `org.xbmc.kodi`: videos
- `org.yuzu.yuzu_emu`: switch
- `org.yuzu.yuzu_emu.ea`: switch
- `paulscode.android.mupen64plusae`: n64
- `rs.ruffle`: flash
- `ru.playsoftware.j2meloader`: j2me
- `ru.vastness.altmer.iratajaguar`: jaguar, jaguarcd
- `ru.woesss.j2meloader`: j2me
- `skyline.emu`: switch
- `xendroid.compose`: xbox360
- `xyz.aethersx2.android`: ps2
- `xyz.aethersx2.cturnip`: ps2
- `xyz.aethersx2.custom`: ps2
- `xyz.aethersx2.tturnip`: ps2

## Warnings

- `elektor` / `None`: no extensions could be read from the platform or its emulators
