package com.phantomstorage.devharness;

import com.phantomstorage.PhantomStorage;
import com.phantomstorage.entity.PhantomGatewayEntity;
import com.phantomstorage.entity.PhantomPetEntity;
import com.phantomstorage.summon.AnchorManager;
import com.phantomstorage.summon.PetKind;
import com.phantomstorage.summon.PetManager;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Scripted in-game checks for the dev client. Lives in tools/ so it never ships: copy it to
 * src/main/java/com/phantomstorage/devharness/ (gitignored) to use it, and delete it before a release
 * build. Inert unless PHANTOM_HARNESS is set: "1" runs the saddle + gateway script, "gateway" the gateway
 * look from every side, "collector" the collector + bell script. It creates a flat creative world,
 * steps through a script (each step waits its ticks, then runs), logs PASS/FAIL lines to run/harness.log
 * and the console, saves screenshots to run/screenshots, and quits. See CLAUDE.md for the command line.
 */
@EventBusSubscriber(modid = PhantomStorage.MODID, value = Dist.CLIENT)
public final class Harness {
    private static final boolean ON = System.getenv("PHANTOM_HARNESS") != null;
    private static final List<Runnable> STEPS = new ArrayList<>();
    private static final List<Integer> WAITS = new ArrayList<>();
    private static final List<BooleanSupplier> UNTIL = new ArrayList<>();
    private static int index;
    private static int wait;
    private static boolean worldCreated;
    private static FileWriter log;
    private static Vec3 mark = Vec3.ZERO;
    private static int failures;
    private static int debugTicks;
    private static boolean waited;

    private static void step(int ticksAfter, Runnable r) {
        STEPS.add(r);
        WAITS.add(ticksAfter);
        UNTIL.add(null);
    }

    private static void until(BooleanSupplier c) {
        STEPS.add(() -> {});
        WAITS.add(0);
        UNTIL.add(c);
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static LocalPlayer p() {
        return mc().player;
    }

    private static ServerPlayer sp() {
        return mc().getSingleplayerServer().getPlayerList().getPlayer(p().getUUID());
    }

    static void log(String s) {
        System.out.println("[HARNESS] " + s);
        try {
            if (log == null) {
                log = new FileWriter("harness.log");
            }
            log.write(s + "\n");
            log.flush();
        } catch (IOException ignored) {
        }
    }

    static void check(String what, boolean ok) {
        if (!ok) {
            failures++;
        }
        log((ok ? "PASS " : "FAIL ") + what);
    }

    private static void cmd(String c) {
        p().connection.sendCommand(c);
    }

    private static void shot(String name) {
        mc().gui.getChat().clearMessages(false);

        Screenshot.grab(mc().gameDirectory, name + ".png", mc().getMainRenderTarget(), c -> {});
    }

    private static void use() {
        p().getCooldowns().removeCooldown(com.phantomstorage.registry.ModRegistries.PHANTOM_CHARM.get());
        server(() -> sp().getCooldowns().removeCooldown(com.phantomstorage.registry.ModRegistries.PHANTOM_CHARM.get()));
        mc().gameMode.useItem(p(), InteractionHand.MAIN_HAND);
    }

    private static boolean[] held = new boolean[4];

    private static void keys(boolean up, boolean jump, boolean shift, boolean sprint) {
        held = new boolean[] {up, jump, shift, sprint};
        applyKeys();
    }

    private static void applyKeys() {
        mc().options.keyUp.setDown(held[0]);
        mc().options.keyJump.setDown(held[1]);
        mc().options.keyShift.setDown(held[2]);
        mc().options.keySprint.setDown(held[3]);
    }

    private static void look(float yaw, float pitch) {
        p().setYRot(yaw);
        p().setXRot(pitch);
        p().yRotO = yaw;
        p().xRotO = pitch;
    }

    /** Runs on the server thread and waits for it. */
    private static void server(Runnable r) {
        mc().getSingleplayerServer().executeBlocking(r);
    }

    private static Entity vehicle() {
        return p().getVehicle();
    }

    static {
        if (ON) {
            if ("collector".equals(System.getenv("PHANTOM_HARNESS"))) {
                collectorScript();
            } else if ("gateway".equals(System.getenv("PHANTOM_HARNESS"))) {
                gatewayScript();
            } else {
                script();
            }
        }
    }

    private static int count(net.minecraft.world.item.Item item) {
        int[] n = new int[1];
        server(() -> n[0] = sp().getData(com.phantomstorage.registry.ModRegistries.STORAGE).countItem(item)
                + sp().getInventory().countItem(item));
        return n[0];
    }

    private static int groundCount(net.minecraft.world.item.Item item) {
        int[] n = new int[1];
        server(() -> {
            for (ItemEntity e : sp().serverLevel().getEntitiesOfClass(ItemEntity.class, sp().getBoundingBox().inflate(40))) {
                if (e.getItem().is(item)) {
                    n[0] += e.getItem().getCount();
                }
            }
        });
        return n[0];
    }

    private static void spawnItem(net.minecraft.world.item.Item item, int count, double dx, double dz) {
        server(() -> {
            ServerLevel level = sp().serverLevel();
            ItemEntity e = new ItemEntity(level, sp().getX() + dx, sp().getY() + 0.5, sp().getZ() + dz, new ItemStack(item, count));
            e.setDefaultPickUpDelay();
            level.addFreshEntity(e);
        });
    }

    private static void collectorCase(String name, Runnable drop, net.minecraft.world.item.Item item, int waitTicks) {
        int[] before = new int[1];
        step(5, () -> {
            before[0] = count(item);
            log(name + ": before held/stored=" + before[0] + " ground=" + groundCount(item));
            drop.run();
        });
        step(waitTicks, () -> log(name + ": after held/stored=" + count(item) + " (+" + (count(item) - before[0]) + ") ground=" + groundCount(item)));
    }

    /** Collector: what it picks up, following and anchored, creative and survival. */
    private static void collectorScript() {
        until(() -> p() != null && mc().level != null && mc().screen == null);
        step(60, () -> {
            mc().options.pauseOnLostFocus = false;
            cmd("gamerule doDaylightCycle false");
            cmd("time set 6000");
            cmd("gamerule doMobSpawning false");
            cmd("weather clear");
            cmd("item replace entity @s hotbar.0 with phantomstorage:phantom_charm[phantomstorage:charm_mode=\"collector\"]");
            cmd("item replace entity @s hotbar.1 with minecraft:cobblestone 16");
            cmd("tp @s 0.5 -60 0.5 0 0");
        });
        step(20, () -> {
            p().getInventory().selected = 0;
            use();
        });
        step(40, () -> server(() -> log("collector " + PetManager.getActive(PetKind.COLLECTOR, p().getUUID()))));
        collectorCase("A spawned diamonds 4 blocks away (creative)", () -> spawnItem(Items.DIAMOND, 3, 4, 0), Items.DIAMOND, 60);
        step(5, () -> cmd("gamemode survival"));
        collectorCase("B owner Q-drop cobblestone (survival)", () -> {
            server(() -> {
                ItemStack st = sp().getInventory().removeItem(1, 16);
                ItemEntity thrown = sp().drop(st, false, true);
                log("thrown entity " + thrown + " owner=" + (thrown == null ? null : thrown.getOwner()));
            });
        }, Items.COBBLESTONE, 20);
        step(1, () -> check("own throw still on the ground at 1 s", groundCount(Items.COBBLESTONE) == 16));
        step(100, () -> check("own throw collected after 5 s", count(Items.COBBLESTONE) == 16 && groundCount(Items.COBBLESTONE) == 0));
        step(5, () -> cmd("gamemode creative"));
        collectorCase("C block broken 4 away (creative)", () -> {
            cmd("setblock ~4 ~ ~ minecraft:oak_log");
            cmd("setblock ~4 ~ ~ minecraft:air destroy");
        }, Items.OAK_LOG, 60);
        collectorCase("D mob killed 4 away (creative)", () -> {
            cmd("summon minecraft:chicken ~4 ~ ~ {NoAI:1b}");
            cmd("kill @e[type=minecraft:chicken]");
        }, Items.FEATHER, 60);
        step(5, () -> {
            p().getInventory().selected = 0;
            cmd("gamemode survival");
        });
        collectorCase("E spawned emeralds 4 away (survival)", () -> spawnItem(Items.EMERALD, 2, 4, 2), Items.EMERALD, 60);
        collectorCase("F block broken 5 away (survival)", () -> {
            cmd("setblock ~5 ~ ~1 minecraft:birch_log");
            cmd("setblock ~5 ~ ~1 minecraft:air destroy");
        }, Items.BIRCH_LOG, 60);
        collectorCase("G spawned gold 7 away (survival)", () -> spawnItem(Items.GOLD_INGOT, 4, 0, 7), Items.GOLD_INGOT, 60);
        // Anchored: told to stay, owner walks off 30 blocks.
        step(10, () -> server(() -> {
            PhantomPetEntity c = PetManager.getActive(PetKind.COLLECTOR, p().getUUID());
            log("collector at " + (c == null ? null : c.position()));
            if (c != null) {
                c.toggleStay(sp());
            }
        }));
        step(10, () -> cmd("tp @s ~30 ~ ~"));
        step(20, () -> server(() -> {
            PhantomPetEntity c = PetManager.getActive(PetKind.COLLECTOR, p().getUUID());
            log("anchored=" + (c != null && c.isAnchored()) + " at " + (c == null ? null : c.position()));
            if (c != null) {
                ItemEntity e = new ItemEntity(c.level(), c.getX() + 3, c.getY(), c.getZ(), new ItemStack(Items.LAPIS_LAZULI, 5));
                e.setDefaultPickUpDelay();
                c.level().addFreshEntity(e);
            }
        }));
        step(60, () -> check("anchored collector takes lapis by its spot", count(Items.LAPIS_LAZULI) == 5));
        // Bell: summon all (creative: everything unlocked), anchored collector stays as it is.
        step(5, () -> {
            cmd("gamemode creative");
            cmd("item replace entity @s hotbar.2 with phantomstorage:phantom_charm[phantomstorage:charm_mode=\"bell\"]");
        });
        step(10, () -> {
            p().getInventory().selected = 2;
            server(() -> sp().getInventory().selected = 2);
            use();
        });
        step(20, () -> server(() -> {
            int out = 0;
            for (PetKind k : PetKind.values()) {
                if (PetManager.getActive(k, p().getUUID()) != null) {
                    out++;
                }
            }
            log("bell: pets out " + out);
            check("bell summons every pet (4 out incl. anchored collector)", out == 4);
        }));
        step(40, () -> {
            look(-150, 20);
            mc().options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        });
        step(10, () -> shot("c1_bell_pets"));
        step(5, () -> {
            mc().options.setCameraType(CameraType.FIRST_PERSON);
            keys(false, false, true, false);
        });
        step(2, () -> use());
        step(10, () -> {
            keys(false, false, false, false);
            server(() -> {
                boolean chest = PetManager.getActive(PetKind.CHEST, p().getUUID()) != null;
                boolean tank = PetManager.getActive(PetKind.TANK, p().getUUID()) != null;
                boolean anchor = PetManager.getActive(PetKind.ANCHOR, p().getUUID()) != null;
                boolean collector = PetManager.getActive(PetKind.COLLECTOR, p().getUUID()) != null;
                log("after sneak-bell chest=" + chest + " tank=" + tank + " anchor=" + anchor + " collector=" + collector);
                check("sneak-bell dismisses following pets", !chest && !tank && !anchor);
                check("sneak-bell leaves the anchored collector", collector);
            });
        });
        // New void look, up close: anchor + collector side by side.
        step(5, () -> {
            cmd("tp @s 200.5 -60 0.5 0 0");
            p().getInventory().selected = 0;
            server(() -> sp().getInventory().selected = 0);
        });
        step(30, () -> server(() -> {
            PhantomPetEntity c = PetManager.getActive(PetKind.COLLECTOR, p().getUUID());
            if (c != null) {
                PetManager.dismiss(PetKind.COLLECTOR, sp(), false);
            }
        }));
        step(5, () -> {
            cmd("item replace entity @s hotbar.3 with phantomstorage:phantom_charm[phantomstorage:charm_mode=\"anchor\"]");
        });
        step(10, () -> use());
        step(5, () -> {
            p().getInventory().selected = 3;
            server(() -> sp().getInventory().selected = 3);
        });
        step(5, () -> use());
        step(10, () -> server(() -> {
            PhantomPetEntity c = PetManager.getActive(PetKind.COLLECTOR, p().getUUID());
            PhantomPetEntity a = PetManager.getActive(PetKind.ANCHOR, p().getUUID());
            if (c != null) {
                c.relocate(199.3, -59.0, 2.6);
                c.toggleStay(sp());
            }
            if (a != null) {
                a.relocate(201.7, -59.0, 2.6);
                a.toggleStay(sp());
            }
        }));
        step(30, () -> {
            cmd("tp @s 200.5 -59.6 -0.2 0 15");
            look(0, 15);
        });
        step(15, () -> shot("c2_void_day"));
        step(1, () -> cmd("time set 18000"));
        step(15, () -> shot("c3_void_night"));
        step(1, () -> {
            cmd("time set 6000");
            p().getInventory().selected = 2;
        });
        step(10, () -> shot("c4_hotbar"));
        step(5, () -> log("DONE failures=" + failures));
        step(20, () -> mc().stop());
    }

    /** Gateway look from the front, the back and the side. */
    private static void gatewayScript() {
        until(() -> p() != null && mc().level != null && mc().screen == null);
        step(60, () -> {
            mc().options.pauseOnLostFocus = false;
            mc().options.hideGui = true;
            cmd("gamerule doDaylightCycle false");
            cmd("time set 6000");
            cmd("gamerule doMobSpawning false");
            cmd("weather clear");
            cmd("item replace entity @s hotbar.0 with phantomstorage:phantom_charm[phantomstorage:charm_mode=\"anchor\"]");
            cmd("item replace entity @s hotbar.1 with phantomstorage:phantom_charm[phantomstorage:charm_mode=\"gateway\"]");
            cmd("tp @s 100.5 -60 0.5 0 0");
        });
        step(20, () -> {
            p().getInventory().selected = 0;
            use();
        });
        step(20, () -> server(() -> {
            PhantomPetEntity anchor = PetManager.getActive(PetKind.ANCHOR, p().getUUID());
            if (anchor != null) {
                anchor.toggleStay(sp());
            }
        }));
        step(10, () -> cmd("tp @s 140.5 -60 0.5 90 0"));
        step(30, () -> {
            p().getInventory().selected = 1;
            look(90, 0);
            use();
        });
        // Front: the side that faces whoever opened it.
        step(20, () -> {
            cmd("tp @s 141.5 -60 0.5 90 0");
        });
        step(10, () -> shot("g1_front"));
        // Back.
        step(5, () -> cmd("tp @s 135.5 -60 0.5 -90 0"));
        step(10, () -> shot("g2_back"));
        // Back, at an angle.
        step(5, () -> cmd("tp @s 135.5 -60 3.5 -130 0"));
        step(10, () -> shot("g3_back_angle"));
        // Edge-on side.
        step(5, () -> cmd("tp @s 138.5 -60 4.5 180 0"));
        step(10, () -> shot("g4_side"));
        // Front, at an angle.
        step(5, () -> cmd("tp @s 141.5 -60 3.5 130 0"));
        step(10, () -> shot("g5_front_angle"));
        step(5, () -> log("DONE failures=" + failures));
        step(20, () -> mc().stop());
    }

    private static void script() {
        until(() -> p() != null && mc().level != null && mc().screen == null);
        step(60, () -> {
            mc().options.pauseOnLostFocus = false;
            cmd("gamerule doDaylightCycle false");
            cmd("time set 6000");
            cmd("gamerule doMobSpawning false");
            cmd("weather clear");
            cmd("tp @s 0.5 -60 0.5 0 0");
            String[] modes = {"chest", "tank", "anchor", "collector", "saddle", "gateway"};
            for (int i = 0; i < modes.length; i++) {
                cmd("item replace entity @s hotbar." + i + " with phantomstorage:phantom_charm[phantomstorage:charm_mode=\"" + modes[i] + "\"]");
            }
        });
        step(20, () -> {
            p().getInventory().selected = 0;
            look(0, 20);
        });
        step(10, () -> shot("01_hotbar"));

        // ---- saddle ----
        step(5, () -> {
            p().getInventory().selected = 4;
            log("mode slot4 = " + p().getMainHandItem().getComponents());
            use();
        });
        step(15, () -> {
            check("saddle use mounts the chest", vehicle() instanceof PhantomPetEntity pet && pet.kind() == PetKind.CHEST);
            mc().options.setCameraType(CameraType.THIRD_PERSON_BACK);
            look(-30, 25);
        });
        step(15, () -> shot("02_mounted"));
        step(1, () -> {
            mark = vehicle().position();
            log("start " + mark);
            debugTicks = 6;
            look(0, -30);
            keys(true, false, false, false);
        });
        step(40, () -> {
            Vec3 now = vehicle().position();
            log("after 40t W looking up: " + now + " speed " + vehicle().getDeltaMovement().length());
            check("W + look up climbs", now.y > mark.y + 3);
            check("W + look up goes forward (+z)", now.z > mark.z + 5);
            mark = now;
            keys(true, false, false, true);
        });
        step(30, () -> {
            Vec3 now = vehicle().position();
            log("sprint 30t: dist " + now.distanceTo(mark) + " speed " + vehicle().getDeltaMovement().length() + " sprinting " + p().isSprinting());
            check("sprint is faster than cruise (>0.5 b/t)", vehicle().getDeltaMovement().length() > 0.5);
            keys(false, false, false, false);
        });
        step(30, () -> {
            log("braked speed " + vehicle().getDeltaMovement().length());
            check("brakes to a hover with no keys", vehicle().getDeltaMovement().length() < 0.02);
            mark = vehicle().position();
            keys(false, true, false, false);
        });
        step(20, () -> {
            check("space rises", vehicle().getY() > mark.y + 2);
            keys(false, false, false, false);
            mc().options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        });
        step(20, () -> shot("03_flying_front"));
        // Wall ahead: fly at it level.
        step(5, () -> {
            mc().options.setCameraType(CameraType.THIRD_PERSON_BACK);
            look(0, 0);
            int z = (int) Math.floor(vehicle().getZ()) + 6;
            int y = (int) Math.floor(vehicle().getY());
            cmd("fill ~-4 " + (y - 4) + " " + z + " ~4 " + (y + 6) + " " + z + " minecraft:stone");
            mark = new Vec3(0, y, z);
            keys(true, false, false, false);
        });
        step(50, () -> {
            double front = vehicle().getBoundingBox().maxZ;
            log("wall at z=" + mark.z + " chest front z=" + front + " player inWall " + p().isInWall());
            check("chest stops at the wall", front <= mark.z + 1.0E-3);
            check("rider not in the wall", !p().isInWall());
            keys(false, false, false, false);
        });
        step(10, () -> shot("04_wall"));
        // Ceiling: 3 blocks above the rider's feet, then hold jump.
        step(5, () -> {
            int ceil = (int) Math.floor(p().getY()) + 3;
            cmd("fill ~-3 " + ceil + " ~-3 ~3 " + ceil + " ~3 minecraft:glass");
            mark = new Vec3(0, ceil, 0);
            keys(false, true, false, false);
        });
        step(40, () -> {
            log("ceiling y=" + mark.y + " eyeY " + p().getEyeY() + " inWall " + p().isInWall());
            check("rider's eyes stay under the ceiling", p().getEyeY() < mark.y);
            keys(false, false, false, false);
        });
        // Sneak to get off.
        step(5, () -> keys(false, false, true, false));
        step(5, () -> {
            keys(false, false, false, false);
            check("sneak gets off", vehicle() == null);
        });
        // Remount with the charm, then use again to get off.
        step(20, () -> use());
        step(15, () -> check("remount with the charm", vehicle() != null));
        step(15, () -> use());
        step(10, () -> check("use again gets off", vehicle() == null));

        // ---- gateway ----
        step(10, () -> {
            mc().options.setCameraType(CameraType.FIRST_PERSON);
            cmd("tp @s 100.5 -60 0.5 0 0");
        });
        step(20, () -> {
            p().getInventory().selected = 2;
            use();
        });
        step(30, () -> server(() -> {
            PhantomPetEntity anchor = PetManager.getActive(PetKind.ANCHOR, p().getUUID());
            log("anchor " + (anchor == null ? null : anchor.position()));
            if (anchor != null) {
                anchor.toggleStay(sp());
            }
        }));
        step(10, () -> {
            check("anchor anchored", AnchorManager.isAnchored(sp(), PetKind.ANCHOR));
            cmd("tp @s 140.5 -60 0.5 90 0");
        });
        step(30, () -> {
            p().getInventory().selected = 5;
            look(90, 0);
            use();
        });
        step(20, () -> {
            List<PhantomGatewayEntity> gates = mc().level.getEntitiesOfClass(PhantomGatewayEntity.class, p().getBoundingBox().inflate(6));
            check("gateway opened in front", gates.size() == 1);
            if (!gates.isEmpty()) {
                log("gateway at " + gates.get(0).position() + " yaw " + gates.get(0).getYRot());
            }
            look(90, 5);
        });
        step(5, () -> shot("05_gateway"));
        step(1, () -> {
            mc().options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            look(-60, 15);
        });
        step(5, () -> shot("05b_gateway_side"));
        step(1, () -> {
            mc().options.setCameraType(CameraType.FIRST_PERSON);
            look(90, 0);
            // An item dropped into it.
            server(() -> {
                ServerLevel level = sp().serverLevel();
                PhantomGatewayEntity gate = level.getEntitiesOfClass(PhantomGatewayEntity.class, sp().getBoundingBox().inflate(6)).getFirst();
                ItemEntity item = new ItemEntity(level, gate.getX(), gate.getY() + 0.5, gate.getZ(), new ItemStack(Items.DIAMOND, 3));
                item.setDeltaMovement(Vec3.ZERO);
                level.addFreshEntity(item);
            });
        });
        step(5, () -> server(() -> {
            ServerLevel level = sp().serverLevel();
            List<ItemEntity> near = level.getEntitiesOfClass(ItemEntity.class, new AABB(95, -64, -5, 106, -50, 6));
            check("item through the gateway lands by the anchor", !near.isEmpty());
        }));
        step(1, () -> keys(true, false, false, false));
        until(() -> p().getX() < 120);
        step(10, () -> {
            keys(false, false, false, false);
            log("after walking in: " + p().position());
            check("walking in lands by the anchor (x~100)", Math.abs(p().getX() - 100.5) < 4);
        });
        step(10, () -> shot("06_arrived"));

        // ---- across dimensions, bringing the chest along ----
        step(5, () -> {
            cmd("execute in minecraft:the_nether run forceload add -16 -16 16 16");
            cmd("execute in minecraft:the_nether run fill -6 119 -6 6 124 6 minecraft:air");
            cmd("execute in minecraft:the_nether run fill -6 119 -6 6 119 6 minecraft:obsidian");
            cmd("execute in minecraft:the_nether run tp @s 0.5 120 0.5 90 0");
        });
        step(60, () -> {});
        step(10, () -> {
            log("in " + p().level().dimension().location() + " at " + p().position());
            p().getInventory().selected = 0;
            use();
        });
        step(110, () -> {
            check("chest out in the nether", PetManager.getActive(PetKind.CHEST, p().getUUID()) != null);
            p().getInventory().selected = 5;
            look(90, 0);
            use();
        });
        step(20, () -> keys(true, false, false, false));
        until(() -> !p().level().dimension().location().getPath().equals("the_nether"));
        step(20, () -> {
            keys(false, false, false, false);
            log("after nether gateway: " + p().level().dimension().location() + " " + p().position());
            check("nether gateway lands in the overworld by the anchor", p().level().dimension().location().getPath().equals("overworld")
                    && Math.abs(p().getX() - 100.5) < 4);
        });
        step(10, () -> server(() -> {
            PhantomPetEntity chest = PetManager.getActive(PetKind.CHEST, p().getUUID());
            check("following chest came along", chest != null && chest.level() == sp().level());
        }));
        // Open and close with sneak-use.
        step(110, () -> {
            look(90, 0);
            use();
        });
        step(20, () -> {
            keys(false, false, true, false);
        });
        step(2, () -> use());
        step(5, () -> {
            keys(false, false, false, false);
        });
        step(20, () -> check("sneak-use closes the gateway",
                mc().level.getEntitiesOfClass(PhantomGatewayEntity.class, p().getBoundingBox().inflate(8)).isEmpty()));

        // ---- chest panel header ----
        step(10, () -> server(() -> {
            PhantomPetEntity chest = PetManager.getActive(PetKind.CHEST, p().getUUID());
            if (chest != null) {
                chest.interact(sp(), InteractionHand.MAIN_HAND);
            }
        }));
        step(20, () -> shot("07_chest_panel"));
        step(5, () -> {
            p().closeContainer();
            log("DONE failures=" + failures);
        });
        step(20, () -> mc().stop());
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        if (!ON) {
            return;
        }
        Minecraft mc = mc();
        if (!worldCreated && mc.level == null && (mc.screen instanceof TitleScreen
                || mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen)) {
            worldCreated = true;
            log("creating world");
            GameRules rules = new GameRules();
            LevelSettings settings = new LevelSettings("harness", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules,
                    WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel("harness", settings, new WorldOptions(1234L, false, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                    mc.screen);
            return;
        }
        if (index >= STEPS.size()) {
            return;
        }
        if (mc.player != null) {
            applyKeys();
            if (debugTicks > 0 && mc.player.getVehicle() != null) {
                debugTicks--;
                Entity v = mc.player.getVehicle();
                Entity sv = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()).getVehicle();
                log(String.format("dbg c=%s speed=%.3f zza=%.2f sprinting=%s | s=%s", v.position(), v.getDeltaMovement().length(), mc.player.zza,
                        mc.player.isSprinting(), sv == null ? null : sv.position()));
            }
        }
        if (!waited) {
            waited = true;
            wait = WAITS.get(index);
        }
        if (wait > 0) {
            wait--;
            return;
        }
        BooleanSupplier cond = UNTIL.get(index);
        if (cond != null && !cond.getAsBoolean()) {
            return;
        }
        try {
            STEPS.get(index).run();
        } catch (Throwable t) {
            failures++;
            log("FAIL step " + index + " threw " + t);
            t.printStackTrace();
        }
        waited = false;
        index++;
    }
}
