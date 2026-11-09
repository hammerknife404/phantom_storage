package com.phantomstorage.devharness;

import com.phantomstorage.PhantomStorage;
import com.phantomstorage.entity.PhantomGatewayEntity;
import com.phantomstorage.entity.PhantomPetEntity;
import com.phantomstorage.summon.AnchorManager;
import com.phantomstorage.summon.GatewayManager;
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
 * look from every side, "collector" the collector + bell script, "void" the End portal void of the
 * anchor, collector and gateway at full and at 10% opacity, "filters" the chest's void grid and the
 * collector's filter clicked through their screens, "gatesize" the gateway's 3 x 3 / 2 x 3 / 1 x 2 fit, "gateflat" gateways laid flat (floor, a caught fall, mid-air, ceiling), "docs" the wiki's screenshots (d_*.png, 1280x720; panel crop boxes in the log), "gallery" the Modrinth gallery's frames (g_*.png, 1920x1080, in a normal world; PHANTOM_SEED picks it). It creates a flat creative world (normal for "gallery"),
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
            } else if ("void".equals(System.getenv("PHANTOM_HARNESS"))) {
                voidScript();
            } else if ("filters".equals(System.getenv("PHANTOM_HARNESS"))) {
                filtersScript();
            } else if ("gatesize".equals(System.getenv("PHANTOM_HARNESS"))) {
                gateSizeScript();
            } else if ("gateflat".equals(System.getenv("PHANTOM_HARNESS"))) {
                gateFlatScript();
            } else if ("docs".equals(System.getenv("PHANTOM_HARNESS"))) {
                docsScript();
            } else if ("gallery".equals(System.getenv("PHANTOM_HARNESS"))) {
                galleryScript();
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
            server(() -> sp().getInventory().selected = 0);
            use();
        });
        step(20, () -> server(() -> {
            logActionBar();
            PhantomPetEntity anchor = PetManager.getActive(PetKind.ANCHOR, p().getUUID());
            log("anchor " + anchor);
            if (anchor != null) {
                anchor.toggleStay(sp());
            }
        }));
        step(10, () -> cmd("tp @s 140.5 -60 0.5 90 0"));
        step(30, () -> {
            p().getInventory().selected = 1;
            server(() -> sp().getInventory().selected = 1);
            look(90, 0);
            use();
        });
        // Front: the side that faces whoever opened it.
        step(20, () -> {
            logActionBar();
            log("gate " + openGate() + " player at " + p().position());
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

    /** Sets the all-pets opacity, in percent, as the config screen would. */
    private static void opacity(int percent) {
        try {
            java.lang.reflect.Field f = com.phantomstorage.client.PhantomClientConfig.class.getDeclaredField("ALL_PETS_OPACITY");
            f.setAccessible(true);
            ((net.neoforged.neoforge.common.ModConfigSpec.IntValue) f.get(null)).set(percent);
            log("all-pets opacity " + percent + "%");
        } catch (ReflectiveOperationException e) {
            check("set opacity: " + e, false);
        }
    }

    /** Holds the owner's pet of {@code kind} (already staying) at (x, y, z), facing south (+z). */
    private static void place(PetKind kind, double x, double y, double z) {
        server(() -> {
            PhantomPetEntity pet = PetManager.getActive(kind, p().getUUID());
            if (pet == null) {
                check(kind + " is out", false);
                return;
            }
            if (!pet.isOrderedToSit()) {
                pet.toggleStay(sp());
            }
            pet.setYRot(0.0F);
            pet.yBodyRot = 0.0F;
            pet.yHeadRot = 0.0F;
            pet.relocate(x, y, z);
        });
    }

    /**
     * The End portal void of the anchor, collector and gateway at full and at 10% all-pets opacity,
     * from the front and from below. Run it with a shader pack too: see CLAUDE.md.
     */
    private static void voidScript() {
        until(() -> p() != null && mc().level != null && mc().screen == null);
        step(100, () -> {
            mc().options.pauseOnLostFocus = false;
            mc().options.hideGui = true;
            cmd("gamerule doDaylightCycle false");
            cmd("time set 6000");
            cmd("gamerule doMobSpawning false");
            cmd("weather clear");
            cmd("item replace entity @s hotbar.0 with phantomstorage:phantom_charm[phantomstorage:charm_mode=\"anchor\"]");
            cmd("item replace entity @s hotbar.1 with phantomstorage:phantom_charm[phantomstorage:charm_mode=\"collector\"]");
            cmd("item replace entity @s hotbar.2 with phantomstorage:phantom_charm[phantomstorage:charm_mode=\"gateway\"]");
            cmd("tp @s 0.5 -60 0.5 180 0");
            opacity(100);
        });
        step(20, () -> {
            p().getInventory().selected = 0;
            server(() -> sp().getInventory().selected = 0);
            use();
        });
        step(20, () -> {
            p().getInventory().selected = 1;
            server(() -> sp().getInventory().selected = 1);
            use();
        });
        step(20, () -> {
            place(PetKind.ANCHOR, -0.3, -58.9, -1.0);
            place(PetKind.COLLECTOR, 1.0, -58.9, -1.0);
        });
        // Front, eye level, 1.5 blocks away.
        step(60, () -> cmd("tp @s 0.35 -60 0.5 180 6"));
        step(40, () -> shot("v1_front_full"));
        step(5, () -> opacity(10));
        step(20, () -> shot("v2_front_10pct"));
        // From below and to the side, like looking up at them in the sky.
        step(5, () -> {
            opacity(100);
            place(PetKind.ANCHOR, -0.3, -57.2, -1.0);
            place(PetKind.COLLECTOR, 1.0, -57.2, -1.0);
            cmd("tp @s 1.6 -60 0.2 134 -42");
        });
        step(60, () -> shot("v3_below_full"));
        step(5, () -> opacity(10));
        step(20, () -> shot("v4_below_10pct"));
        // Gateway, front, at full and at 10%.
        step(5, () -> {
            opacity(100);
            cmd("tp @s 20.5 -60 0.5 90 0");
        });
        step(30, () -> {
            p().getInventory().selected = 2;
            server(() -> sp().getInventory().selected = 2);
            look(90, 0);
            use();
        });
        step(20, () -> cmd("tp @s 21.5 -60 2.0 110 0"));
        step(30, () -> shot("v5_gateway_full"));
        step(5, () -> opacity(10));
        step(20, () -> shot("v6_gateway_10pct"));
        step(5, () -> {
            opacity(100);
            log("DONE failures=" + failures);
        });
        step(20, () -> mc().stop());
    }

    /** Clicks menu slot {@code index} as a player does: press and release over it on the open screen. */
    private static void clickSlot(int index, int button) {
        if (!(mc().screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen)) {
            check("a container screen is open (got " + mc().screen + ")", false);
            return;
        }
        net.minecraft.world.inventory.Slot slot = screen.getMenu().slots.get(index);
        double x = screen.getGuiLeft() + slot.x + 8;
        double y = screen.getGuiTop() + slot.y + 8;
        screen.mouseClicked(x, y, button);
        screen.mouseReleased(x, y, button);
    }

    /** Moves the real mouse pointer over menu slot {@code index}, so the screen draws its hover highlight. */
    private static void hover(int index) {
        if (!(mc().screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen)) {
            return;
        }
        net.minecraft.world.inventory.Slot slot = screen.getMenu().slots.get(index);
        double scale = mc().getWindow().getGuiScale();
        try {
            // GLFW's cursor-move doesn't reach the game under Xvfb, so set where the game thinks the mouse is.
            java.lang.reflect.Field x = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            java.lang.reflect.Field y = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            x.setAccessible(true);
            y.setAccessible(true);
            x.setDouble(mc().mouseHandler, (screen.getGuiLeft() + slot.x + 8) * scale);
            y.setDouble(mc().mouseHandler, (screen.getGuiTop() + slot.y + 8) * scale);
        } catch (ReflectiveOperationException e) {
            log("hover: " + e);
        }
    }

    /** Places the cursor on a slot: 0 left click, 1 right click, 2 left press that drifts 2 px before release. */
    private static void placeOn(int index, int gesture) {
        if (gesture < 2) {
            clickSlot(index, gesture);
            return;
        }
        if (!(mc().screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen)) {
            check("a container screen is open (got " + mc().screen + ")", false);
            return;
        }
        net.minecraft.world.inventory.Slot slot = screen.getMenu().slots.get(index);
        double x = screen.getGuiLeft() + slot.x + 6;
        double y = screen.getGuiTop() + slot.y + 6;
        screen.mouseClicked(x, y, 0);
        screen.mouseDragged(x + 2, y + 1, 0, 2, 1);
        screen.mouseReleased(x + 2, y + 1, 0);
    }

    /**
     * For each test item: pick its stack up from the first inventory row, click filter entry k with it,
     * put the stack back; then the entry should hold one ghost copy on both sides and the stack be whole.
     */
    private static void filterCases(String name, int filterStart, int invStart, int gesture,
                                    java.util.function.Supplier<net.minecraft.world.Container> serverFilter) {
        // A local, not a static: the scripts are built from this class's static initializer.
        net.minecraft.world.item.Item[] items = {Items.COBBLESTONE, Items.DIAMOND, Items.STICK, Items.DIAMOND_SWORD, Items.ROTTEN_FLESH};
        step(2, () -> server(() -> serverFilter.get().clearContent()));
        for (int k = 0; k < items.length; k++) {
            int i = k;
            net.minecraft.world.item.Item item = items[k];
            int[] before = new int[1];
            step(4, () -> {
                before[0] = p().getInventory().countItem(item);
                clickSlot(invStart + i, 0);
                log(name + " " + item + ": carried " + p().containerMenu.getCarried());
            });
            step(4, () -> placeOn(filterStart + i, gesture));
            step(4, () -> clickSlot(invStart + i, 0));
            step(6, () -> {
                ItemStack client = p().containerMenu.slots.get(filterStart + i).getItem();
                ItemStack[] server = new ItemStack[1];
                server(() -> server[0] = serverFilter.get().getItem(i).copy());
                log(name + " entry " + i + ": client " + client + " server " + server[0]
                        + " carried " + p().containerMenu.getCarried() + " held " + p().getInventory().countItem(item));
                check(name + " takes a ghost " + item, client.is(item) && client.getCount() == 1
                        && server[0].is(item) && server[0].getCount() == 1
                        && p().containerMenu.getCarried().isEmpty() && p().getInventory().countItem(item) == before[0]);
            });
        }
        // Clicking an entry empty-handed clears it.
        step(4, () -> clickSlot(filterStart, 0));
        step(6, () -> {
            ItemStack[] server = new ItemStack[1];
            server(() -> server[0] = serverFilter.get().getItem(0).copy());
            check(name + " entry clears on an empty-handed click", server[0].isEmpty()
                    && p().containerMenu.slots.get(filterStart).getItem().isEmpty());
        });
    }

    /**
     * The chest's void grid and the collector's filter, filled through their screens with blocks, items
     * and a tool: left click, right click and a drifting press in survival, and left click in creative.
     */
    private static void filtersScript() {
        until(() -> p() != null && mc().level != null && mc().screen == null);
        step(60, () -> {
            mc().options.pauseOnLostFocus = false;
            cmd("gamerule doDaylightCycle false");
            cmd("time set 6000");
            cmd("gamerule doMobSpawning false");
            cmd("weather clear");
            cmd("item replace entity @s hotbar.0 with phantomstorage:phantom_charm[phantomstorage:charm_mode=\"chest\"]");
            cmd("item replace entity @s hotbar.1 with phantomstorage:phantom_charm[phantomstorage:charm_mode=\"collector\"]");
            cmd("item replace entity @s inventory.0 with minecraft:cobblestone 16");
            cmd("item replace entity @s inventory.1 with minecraft:diamond 3");
            cmd("item replace entity @s inventory.2 with minecraft:stick 8");
            cmd("item replace entity @s inventory.3 with minecraft:diamond_sword");
            cmd("item replace entity @s inventory.4 with minecraft:rotten_flesh 5");
            cmd("tp @s 0.5 -60 0.5 180 0");
            cmd("gamemode survival");
        });
        step(20, () -> {
            p().getInventory().selected = 0;
            server(() -> sp().getInventory().selected = 0);
            use();
        });
        step(20, () -> {
            p().getInventory().selected = 1;
            server(() -> sp().getInventory().selected = 1);
            use();
        });
        String[] variants = {"left click", "right click", "drag", "creative left click"};
        for (int v = 0; v < variants.length; v++) {
            int gesture = v == 3 ? 0 : v;
            String label = variants[v];
            boolean creative = v == 3;
            step(5, () -> {
                cmd(creative ? "gamemode creative" : "gamemode survival");
                log("--- " + label);
            });
            step(20, () -> server(() -> PetManager.getActive(PetKind.CHEST, p().getUUID()).interact(sp(), InteractionHand.MAIN_HAND)));
            step(20, () -> log("screen " + mc().screen));
            filterCases("chest void (" + label + ")", com.phantomstorage.menu.PhantomChestMenu.VOID_START,
                    com.phantomstorage.menu.PhantomChestMenu.INV_START, gesture,
                    () -> sp().getData(com.phantomstorage.registry.ModRegistries.STORAGE).voidFilter());
            if (v == 0) {
                step(5, () -> hover(com.phantomstorage.menu.PhantomChestMenu.VOID_START + 1));
                step(10, () -> shot("f1_chest_void"));
            }
            step(5, () -> p().closeContainer());
            step(20, () -> server(() -> PetManager.getActive(PetKind.COLLECTOR, p().getUUID()).interact(sp(), InteractionHand.MAIN_HAND)));
            step(20, () -> log("screen " + mc().screen));
            filterCases("collector (" + label + ")", com.phantomstorage.menu.PhantomCollectorMenu.FILTER_START,
                    com.phantomstorage.menu.PhantomCollectorMenu.INV_START, gesture,
                    () -> sp().getData(com.phantomstorage.registry.ModRegistries.COLLECTOR).filter());
            if (v == 0) {
                step(5, () -> hover(com.phantomstorage.menu.PhantomCollectorMenu.FILTER_START + 2));
                step(10, () -> shot("f2_collector"));
            }
            step(5, () -> p().closeContainer());
        }
        // The void list only acts on what the collector takes: the owner's own pickups are left alone.
        int[] held = new int[1];
        step(5, () -> server(() -> {
            sp().getData(com.phantomstorage.registry.ModRegistries.STORAGE).voidFilter().setItem(0, new ItemStack(Items.COBBLESTONE));
            sp().getData(com.phantomstorage.registry.ModRegistries.COLLECTOR).filter().clearContent();
            PetManager.dismiss(PetKind.COLLECTOR, sp(), false);
        }));
        step(20, () -> {
            held[0] = count(Items.COBBLESTONE);
            spawnItem(Items.COBBLESTONE, 3, 0.0, 0.0);
        });
        step(40, () -> check("own pickup keeps a void-listed item (" + held[0] + " -> " + count(Items.COBBLESTONE) + ")",
                count(Items.COBBLESTONE) == held[0] + 3 && groundCount(Items.COBBLESTONE) == 0));
        step(5, () -> {
            p().getInventory().selected = 1;
            server(() -> sp().getInventory().selected = 1);
            use();
        });
        step(40, () -> {
            held[0] = count(Items.COBBLESTONE);
            spawnItem(Items.COBBLESTONE, 3, 4.0, 0.0);
        });
        step(60, () -> check("collector destroys a void-listed item (" + held[0] + " -> " + count(Items.COBBLESTONE)
                        + ", ground " + groundCount(Items.COBBLESTONE) + ")",
                count(Items.COBBLESTONE) == held[0] && groundCount(Items.COBBLESTONE) == 0));
        step(5, () -> {
            log("DONE failures=" + failures);
        });
        step(20, () -> mc().stop());
    }

    /**
     * Summons the anchor (hotbar 0) where the player stands and anchors it, retrying the charm until the
     * anchor is out: the first use right after the world loads doesn't always land.
     */
    private static void anchorHere() {
        anchorHere(0);
    }

    /** As {@link #anchorHere()}, with the anchor charm in hotbar {@code slot}. */
    private static void anchorHere(int slot) {
        int[] tries = new int[1];
        until(() -> {
            boolean[] out = new boolean[1];
            server(() -> out[0] = PetManager.getActive(PetKind.ANCHOR, p().getUUID()) != null);
            if (!out[0] && tries[0]++ % 20 == 0) {
                p().getInventory().selected = slot;
                server(() -> sp().getInventory().selected = slot);
                use();
            }
            return out[0];
        });
        step(10, () -> server(() -> {
            PhantomPetEntity anchor = PetManager.getActive(PetKind.ANCHOR, p().getUUID());
            if (!AnchorManager.isAnchored(sp(), PetKind.ANCHOR)) {
                anchor.toggleStay(sp());
            }
        }));
        step(5, () -> {
            boolean[] anchored = new boolean[1];
            server(() -> anchored[0] = AnchorManager.isAnchored(sp(), PetKind.ANCHOR));
            check("anchor anchored", anchored[0]);
        });
    }

    /** The owner's open gateway, if any (server side). */
    private static PhantomGatewayEntity openGate() {
        PhantomGatewayEntity[] found = new PhantomGatewayEntity[1];
        server(() -> {
            for (PhantomGatewayEntity g : sp().serverLevel().getEntitiesOfClass(PhantomGatewayEntity.class,
                    sp().getBoundingBox().inflate(40))) {
                if (!g.isRemoved() && sp().getUUID().equals(g.getOwnerId())) {
                    found[0] = g;
                }
            }
        });
        return found[0];
    }

    /**
     * Uses the gateway charm (hotbar 1) the way a player does, the selected slot set on both sides,
     * facing west. The action bar's answer is logged a few ticks later by {@link #logActionBar}.
     */
    private static void openGateNow() {
        p().getInventory().selected = 1;
        server(() -> sp().getInventory().selected = 1);
        look(90, 0);
        use();
    }

    private static void logActionBar() {
        try {
            java.lang.reflect.Field f = net.minecraft.client.gui.Gui.class.getDeclaredField("overlayMessageString");
            f.setAccessible(true);
            log("action bar: " + f.get(mc().gui));
        } catch (ReflectiveOperationException e) {
            log("action bar: " + e);
        }
    }

    /** Opens a gateway from (x, -60, z) facing west, after closing any open one. */
    private static void openGateFrom(double x, double z) {
        server(() -> GatewayManager.closeFor(sp()));
        cmd("tp @s " + x + " -60 " + z + " 90 0");
    }

    /**
     * The gateway's size: 3 x 3 in the open (walking in sends you to the anchor); 2 x 3, on the clear side,
     * when a block stands where the third column would go; 1 x 2 under a 2-high ceiling and in a 1 x 2
     * tunnel (walking in goes through); none in a 1-high gap.
     */
    private static void gateSizeScript() {
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
        anchorHere();
        // A: the open field.
        step(10, () -> openGateFrom(140.5, 0.5));
        step(20, () -> openGateNow());
        step(10, () -> {
            logActionBar();
            PhantomGatewayEntity g = openGate();
            log("A gate " + (g == null ? null : g.position() + " width " + g.doorWidth() + " box " + g.getBoundingBox()));
            check("open field: 3 x 3, 2 blocks ahead", g != null && g.doorWidth() == 3 && g.doorHeight() == 3
                    && Math.abs(g.getX() - 138.5) < 1.0E-6 && Math.abs(g.getZ() - 0.5) < 1.0E-6
                    && Math.abs(g.getBoundingBox().getZsize() - 3.0) < 1.0E-6 && Math.abs(g.getBoundingBox().getXsize() - 1.0) < 1.0E-6
                    && Math.abs(g.getBoundingBox().getYsize() - 3.0) < 1.0E-6);
        });
        step(5, () -> cmd("tp @s 142.5 -60 0.5 90 0"));
        step(20, () -> shot("s1_gate_3x3"));
        // Walking in sends you to the anchor.
        step(5, () -> cmd("tp @s 139.2 -60 0.5 90 0"));
        step(20, () -> check("walking in lands by the anchor (at " + p().position() + ")", p().position().distanceTo(new Vec3(100.5, -60, 0.5)) < 6.0));
        // B: a block where the third column would go: 2 wide, on the clear side.
        step(10, () -> {
            cmd("setblock 138 -58 1 minecraft:stone");
            cmd("setblock 137 -58 1 minecraft:stone");
            openGateFrom(140.5, 0.5);
        });
        step(20, () -> openGateNow());
        step(10, () -> {
            logActionBar();
            PhantomGatewayEntity g = openGate();
            log("B gate " + (g == null ? null : g.position() + " width " + g.doorWidth() + " box " + g.getBoundingBox()));
            check("a block in the way: 2 x 3 on the clear side", g != null && g.doorWidth() == 2 && g.doorHeight() == 3
                    && Math.abs(g.getZ() - 0.0) < 1.0E-6 && Math.abs(g.getBoundingBox().getZsize() - 2.0) < 1.0E-6);
        });
        step(5, () -> cmd("tp @s 142.5 -60 0.5 90 0"));
        step(20, () -> shot("s2_gate_2x3"));
        // C: a 2-high ceiling: 1 x 2 in the middle column.
        step(10, () -> {
            cmd("setblock 138 -58 1 minecraft:air");
            cmd("setblock 137 -58 1 minecraft:air");
            cmd("fill 136 -58 -3 138 -58 4 minecraft:stone");
            openGateFrom(140.5, 0.5);
        });
        step(20, () -> openGateNow());
        step(10, () -> {
            logActionBar();
            PhantomGatewayEntity g = openGate();
            log("C gate " + (g == null ? null : g.position() + " size " + g.doorWidth() + "x" + g.doorHeight() + " box " + g.getBoundingBox()));
            check("under a 2-high ceiling: 1 x 2 in the middle", g != null && g.doorWidth() == 1 && g.doorHeight() == 2
                    && Math.abs(g.getZ() - 0.5) < 1.0E-6 && Math.abs(g.getBoundingBox().getYsize() - 2.0) < 1.0E-6);
        });
        // D: a 1-wide, 2-high tunnel: 1 x 2 inside it, and walking in goes through.
        step(10, () -> {
            cmd("fill 135 -60 -1 139 -59 -1 minecraft:stone");
            cmd("fill 135 -60 1 139 -59 1 minecraft:stone");
            openGateFrom(140.5, 0.5);
        });
        step(20, () -> openGateNow());
        step(10, () -> {
            logActionBar();
            PhantomGatewayEntity g = openGate();
            log("D gate " + (g == null ? null : g.position() + " size " + g.doorWidth() + "x" + g.doorHeight()));
            check("in a 1 x 2 tunnel: 1 x 2 inside it", g != null && g.doorWidth() == 1 && g.doorHeight() == 2
                    && Math.abs(g.getX() - 138.5) < 1.0E-6 && Math.abs(g.getZ() - 0.5) < 1.0E-6);
        });
        step(5, () -> cmd("tp @s 141.5 -60 0.5 90 0"));
        step(20, () -> shot("s3_gate_1x2_tunnel"));
        step(5, () -> cmd("tp @s 139.2 -60 0.5 90 0"));
        step(20, () -> check("walking into the tunnel's gateway lands by the anchor (at " + p().position() + ")",
                p().position().distanceTo(new Vec3(100.5, -60, 0.5)) < 6.0));
        // E: a 1-high gap: no room.
        step(10, () -> {
            cmd("fill 135 -59 -1 139 -59 1 minecraft:stone");
            openGateFrom(140.5, 0.5);
        });
        step(20, () -> openGateNow());
        step(10, () -> {
            logActionBar();
            check("a 1-high gap: no gateway", openGate() == null);
        });
        step(5, () -> {
            cmd("fill 135 -60 -3 139 -58 4 minecraft:air");
            log("DONE failures=" + failures);
        });
        step(20, () -> mc().stop());
    }

    /** Uses the gateway charm (hotbar 1) facing {@code yaw} at {@code pitch}, set on both sides. */
    private static void openGateLooking(float yaw, float pitch) {
        server(() -> GatewayManager.closeFor(sp()));
        p().getInventory().selected = 1;
        server(() -> {
            sp().getInventory().selected = 1;
            sp().setYRot(yaw);
            sp().setXRot(pitch);
        });
        look(yaw, pitch);
        use();
    }

    private static String describe(PhantomGatewayEntity g) {
        return g == null ? "none" : g.lie() + " " + g.doorWidth() + "x" + g.doorHeight() + " at " + g.position() + " box " + g.getBoundingBox();
    }

    /**
     * Gateways laid flat: on the floor below a pillar (screenshot); on the floor at the opener's feet,
     * which takes them through; on the floor under a 30-block fall in survival, caught with no damage;
     * in mid-air above, looking up at open sky, with an arrow shot up through it; under a ceiling.
     */
    private static void gateFlatScript() {
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
            cmd("phantomstorage unlock all"); // survival (the caught fall) needs the gateway unlocked
        });
        anchorHere();
        Vec3 anchorSpot = new Vec3(100.5, -60, 0.5);
        // A: from a 4-high pillar, looking down at the ground ahead: a 3 x 3 floor gateway, the opener clear of it.
        step(10, () -> {
            cmd("fill 140 -60 0 140 -57 0 minecraft:stone");
            cmd("tp @s 140.5 -56 0.5 90 60");
        });
        step(20, () -> openGateLooking(90.0F, 60.0F));
        step(10, () -> {
            logActionBar();
            PhantomGatewayEntity g = openGate();
            log("A " + describe(g));
            check("looking down from a pillar: a 3 x 3 floor gateway on the ground", g != null
                    && g.lie() == PhantomGatewayEntity.Lie.FLOOR && g.doorWidth() == 3 && g.doorHeight() == 3
                    && Math.abs(g.getY() + 60.0) < 1.0E-6 && Math.abs(g.getBoundingBox().getYsize() - 1.0) < 1.0E-6);
        });
        step(20, () -> shot("f1_floor_from_pillar"));
        step(5, () -> cmd("fill 140 -60 0 140 -57 0 minecraft:air"));
        // B: looking down at your own feet: it opens under you and takes you through.
        step(10, () -> cmd("tp @s 150.5 -60 0.5 90 80"));
        step(20, () -> openGateLooking(90.0F, 80.0F));
        step(5, () -> {
            logActionBar();
            log("B " + describe(openGate()));
        });
        step(30, () -> check("a floor gateway under you takes you through (at " + p().position() + ")",
                p().position().distanceTo(anchorSpot) < 6.0));
        // C: falling 30 blocks in survival, looking straight down: caught by a floor gateway, no damage.
        step(10, () -> {
            cmd("gamemode survival");
            cmd("effect clear @s");
            cmd("tp @s 160.5 -30 0.5 90 89");
        });
        step(2, () -> openGateLooking(90.0F, 89.0F));
        step(5, () -> {
            logActionBar();
            log("C " + describe(openGate()) + " player y " + p().getY());
        });
        step(60, () -> {
            float[] health = new float[1];
            server(() -> health[0] = sp().getHealth());
            check("a 30-block fall caught by a floor gateway, unhurt (health " + health[0] + ", at " + p().position() + ")",
                    p().position().distanceTo(anchorSpot) < 6.0 && health[0] >= 20.0F);
        });
        step(5, () -> cmd("gamemode creative"));
        // D: looking up at open sky: in mid-air 6 blocks along the look; an arrow flying up through it goes through.
        step(10, () -> cmd("tp @s 170.5 -60 0.5 90 -70"));
        step(20, () -> openGateLooking(90.0F, -70.0F));
        step(15, () -> {
            logActionBar();
            PhantomGatewayEntity g = openGate();
            log("D " + describe(g));
            check("looking up at open sky: a 3 x 3 gateway in mid-air above", g != null
                    && g.lie() == PhantomGatewayEntity.Lie.AIR_ABOVE && g.doorWidth() == 3 && g.getY() > -56.0);
            if (g != null) {
                Vec3 below = new Vec3(g.getX(), g.getY() - 4.0, g.getZ());
                server(() -> {
                    net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(
                            sp().serverLevel(), below.x, below.y, below.z, new ItemStack(Items.ARROW), null);
                    arrow.setDeltaMovement(0.0, 3.0, 0.0);
                    sp().serverLevel().addFreshEntity(arrow);
                });
            }
        });
        step(5, () -> shot("f2_air_above"));
        step(10, () -> {
            boolean[] arrived = new boolean[1];
            server(() -> arrived[0] = !sp().serverLevel().getEntitiesOfClass(net.minecraft.world.entity.projectile.Arrow.class,
                    new AABB(anchorSpot, anchorSpot).inflate(6.0)).isEmpty());
            check("an arrow flying up through it at 3 blocks a tick lands by the anchor", arrived[0]);
        });
        // E: under a ceiling, looking up: just below it.
        step(10, () -> {
            cmd("fill 176 -55 -4 184 -55 4 minecraft:stone");
            cmd("tp @s 180.5 -60 0.5 90 -70");
        });
        step(20, () -> openGateLooking(90.0F, -70.0F));
        step(15, () -> {
            logActionBar();
            PhantomGatewayEntity g = openGate();
            log("E " + describe(g));
            check("looking up at a ceiling: a 3 x 3 gateway just under it", g != null
                    && g.lie() == PhantomGatewayEntity.Lie.CEILING && g.doorWidth() == 3 && Math.abs(g.getY() + 56.0) < 1.0E-6);
        });
        step(10, () -> shot("f3_ceiling"));
        // F: looking down at a lava pool from its edge: on the lava's surface, not its bed.
        step(5, () -> {
            cmd("fill 176 -55 -4 184 -55 4 minecraft:air");
            cmd("fill 191 -63 -3 197 -61 3 minecraft:lava");
            cmd("fill 189 -60 0 189 -57 0 minecraft:stone");
            cmd("tp @s 189.5 -56 0.5 270 60");
        });
        step(20, () -> openGateLooking(270.0F, 60.0F));
        step(10, () -> {
            logActionBar();
            PhantomGatewayEntity g = openGate();
            log("F " + describe(g));
            check("looking down at lava from a pillar: just above its surface", g != null && Math.abs(g.getY() + 60.0) < 1.0E-6);
        });
        step(5, () -> {
            cmd("fill 191 -63 -3 197 -61 3 minecraft:grass_block");
            cmd("fill 189 -60 0 189 -57 0 minecraft:air");

            log("DONE failures=" + failures);
        });
        step(20, () -> mc().stop());
    }

    /** Selects hotbar {@code slot} on both sides and uses the charm there. */
    private static void useSlot(int slot) {
        p().getInventory().selected = slot;
        server(() -> sp().getInventory().selected = slot);
        use();
    }

    /** Puts where the game thinks the mouse is in the window's top-left corner. */
    private static void parkMouse() {
        try {
            java.lang.reflect.Field x = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            java.lang.reflect.Field y = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            x.setAccessible(true);
            y.setAccessible(true);
            x.setDouble(mc().mouseHandler, 2.0);
            y.setDouble(mc().mouseHandler, 2.0);
        } catch (ReflectiveOperationException e) {
            log("mouse: " + e);
        }
    }

    /** Logs where the open container screen's panel is in the screenshot, in pixels, for cropping. */
    private static void logPanel(String name) {
        if (mc().screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen) {
            double k = mc().getWindow().getGuiScale();
            log("PANEL " + name + " " + (int) (screen.getGuiLeft() * k) + " " + (int) (screen.getGuiTop() * k)
                    + " " + (int) (screen.getXSize() * k) + " " + (int) (screen.getYSize() * k));
        } else {
            check(name + " screen open (got " + mc().screen + ")", false);
        }
    }

    /** Fills the storage, the tanks, the void grid and the collector's filter and bank, for panel shots. */
    private static void stockAll() {
        net.minecraft.world.item.Item[] items = {Items.DIAMOND, Items.IRON_INGOT, Items.GOLD_INGOT, Items.REDSTONE, Items.LAPIS_LAZULI,
                Items.EMERALD, Items.OAK_LOG, Items.SPRUCE_PLANKS, Items.COBBLESTONE, Items.DEEPSLATE, Items.TORCH, Items.BREAD,
                Items.GOLDEN_APPLE, Items.ENDER_PEARL, Items.ARROW, Items.BONE, Items.STRING, Items.GUNPOWDER, Items.AMETHYST_SHARD,
                Items.COPPER_INGOT, Items.GLASS, Items.BOOK, Items.EXPERIENCE_BOTTLE, Items.SHULKER_SHELL};
        int[] counts = {64, 48, 23, 64, 37, 12, 64, 64, 64, 64, 64, 32, 3, 16, 64, 21, 30, 17, 9, 41, 64, 7, 12, 2};
        for (int i = 0; i < items.length; i++) {
            stock(i, items[i], counts[i]);
        }
        server(() -> {
            com.phantomstorage.storage.ItemFilter voids = sp().getData(com.phantomstorage.registry.ModRegistries.STORAGE).voidFilter();
            voids.setItem(0, new ItemStack(Items.ROTTEN_FLESH));
            voids.setItem(1, new ItemStack(Items.POISONOUS_POTATO));
            voids.setItem(2, new ItemStack(Items.DIRT));
            com.phantomstorage.storage.CollectorSettings collector = sp().getData(com.phantomstorage.registry.ModRegistries.COLLECTOR);
            collector.filter().setItem(0, new ItemStack(Items.BONE));
            collector.filter().setItem(1, new ItemStack(Items.ARROW));
            collector.filter().setItem(2, new ItemStack(Items.STRING));
            collector.filter().setItem(3, new ItemStack(Items.GUNPOWDER));
            collector.bank(345);
            com.phantomstorage.storage.PhantomTanks tanks = sp().getData(com.phantomstorage.registry.ModRegistries.TANKS);
            tanks.tank(0).fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 24000),
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            tanks.tank(1).fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 9000),
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            tanks.setActive(0);
        });
    }

    private static void stock(int slot, net.minecraft.world.item.Item item, int count) {
        server(() -> sp().getData(com.phantomstorage.registry.ModRegistries.STORAGE).setItem(slot, new ItemStack(item, count)));
    }

    /**
     * Screenshots for the wiki, at 1280x720 with the HUD hidden: the four pets in a row at full and at 40%
     * opacity; each pet's panel, stocked (crop boxes logged as PANEL lines); riding the chest; and the
     * gateway standing 3 x 3, 2 x 3 and 1 x 2 and lying on a floor, in mid-air and under a ceiling.
     */
    private static void docsScript() {
        until(() -> p() != null && mc().level != null && mc().screen == null);
        step(40, () -> org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc().getWindow().getWindow(), 1280, 720));
        step(40, () -> {
            log("window " + mc().getWindow().getWidth() + "x" + mc().getWindow().getHeight() + " gui scale " + mc().getWindow().getGuiScale());
            mc().options.pauseOnLostFocus = false;
            mc().options.hideGui = true;
            cmd("gamerule doDaylightCycle false");
            cmd("time set 5000");
            cmd("gamerule doMobSpawning false");
            cmd("weather clear");
            cmd("phantomstorage unlock all");
            String[] modes = {"chest", "tank", "anchor", "collector", "gateway"};
            for (int i = 0; i < modes.length; i++) {
                cmd("item replace entity @s hotbar." + i + " with phantomstorage:phantom_charm[phantomstorage:charm_mode=\"" + modes[i] + "\"]");
            }
            cmd("tp @s 0.5 -60 1.6 180 3");
            opacity(100);
        });
        // Stock the storage, tanks and filters so the panels show something.
        step(10, Harness::stockAll);
        // The four pets in a row, facing the camera.
        for (int i = 0; i < 4; i++) {
            int slot = i;
            step(15, () -> useSlot(slot));
        }
        step(20, () -> {
            place(PetKind.CHEST, -1.45, -58.9, -2.0);
            place(PetKind.TANK, -0.15, -58.9, -2.0);
            place(PetKind.ANCHOR, 1.15, -58.9, -2.0);
            place(PetKind.COLLECTOR, 2.45, -58.9, -2.0);
        });
        step(80, () -> cmd("tp @s 0.5 -60 1.6 180 3"));
        step(20, () -> {
            place(PetKind.CHEST, -1.45, -58.9, -2.0);
            place(PetKind.TANK, -0.15, -58.9, -2.0);
            place(PetKind.ANCHOR, 1.15, -58.9, -2.0);
            place(PetKind.COLLECTOR, 2.45, -58.9, -2.0);
        });
        step(30, () -> shot("d_pets_100"));
        step(5, () -> opacity(40));
        step(20, () -> shot("d_pets_40"));
        step(5, () -> opacity(100));
        // Each panel, the mouse parked in a corner (opening a screen centres it) so no slot is highlighted.
        PetKind[] kinds = {PetKind.CHEST, PetKind.TANK, PetKind.ANCHOR, PetKind.COLLECTOR};
        String[] names = {"chest", "tank", "anchor", "collector"};
        for (int i = 0; i < kinds.length; i++) {
            PetKind kind = kinds[i];
            String name = names[i];
            step(10, () -> server(() -> PetManager.getActive(kind, p().getUUID()).interact(sp(), InteractionHand.MAIN_HAND)));
            step(20, () -> parkMouse());
            step(5, () -> {
                logPanel(name);
                shot("d_" + name + "_panel");
            });
            step(10, () -> p().closeContainer());
        }
        // Riding the chest, seen from behind.
        step(10, () -> {
            cmd("tp @s 0.5 -60 6.5 200 0");
        });
        step(10, () -> server(() -> {
            PhantomPetEntity chest = PetManager.getActive(PetKind.CHEST, p().getUUID());
            chest.carry(sp(), sp().position().add(0.0, 2.5, 0.0));
        }));
        step(10, () -> {
            mc().options.setCameraType(CameraType.THIRD_PERSON_BACK);
            look(200, 12);
        });
        step(30, () -> shot("d_saddle"));
        step(5, () -> {
            mc().options.setCameraType(CameraType.FIRST_PERSON);
            server(() -> sp().stopRiding());
        });
        // Gateways: the anchor in the row above is anchored, so they lead there. openGateLooking uses hotbar 1.
        step(20, () -> {
            cmd("item replace entity @s hotbar.1 with phantomstorage:phantom_charm[phantomstorage:charm_mode=\"gateway\"]");
            cmd("tp @s 140.5 -60 0.5 90 0");
        });
        step(20, () -> openGateLooking(90.0F, 0.0F));
        step(25, () -> {
            log("gate 3x3 " + describe(openGate()));
            cmd("tp @s 143.5 -60 2.0 110 -5");
        });
        step(25, () -> shot("d_gate_3x3"));
        step(5, () -> {
            cmd("setblock 138 -58 1 minecraft:stone");
            cmd("setblock 137 -58 1 minecraft:stone");
            cmd("tp @s 140.5 -60 0.5 90 0");
        });
        step(15, () -> openGateLooking(90.0F, 0.0F));
        step(25, () -> {
            log("gate 2x3 " + describe(openGate()));
            cmd("tp @s 143.5 -60 1.0 100 -5");
        });
        step(25, () -> shot("d_gate_2x3"));
        step(5, () -> {
            cmd("setblock 138 -58 1 minecraft:air");
            cmd("setblock 137 -58 1 minecraft:air");
            cmd("fill 135 -60 -1 139 -59 -1 minecraft:stone_bricks");
            cmd("fill 135 -60 1 139 -59 1 minecraft:stone_bricks");
            cmd("fill 135 -58 -1 139 -58 1 minecraft:stone_bricks");
            cmd("tp @s 140.5 -60 0.5 90 0");
        });
        step(15, () -> openGateLooking(90.0F, 0.0F));
        step(25, () -> {
            log("gate 1x2 " + describe(openGate()));
            cmd("tp @s 142.5 -60 0.5 90 8");
        });
        step(25, () -> shot("d_gate_1x2"));
        step(5, () -> {
            cmd("fill 135 -60 -1 139 -58 1 minecraft:air");
            cmd("fill 160 -60 0 160 -57 0 minecraft:stone");
            cmd("tp @s 160.5 -56 0.5 90 55");
        });
        step(15, () -> openGateLooking(90.0F, 55.0F));
        step(30, () -> {
            log("gate floor " + describe(openGate()));
            shot("d_gate_floor");
        });
        step(5, () -> {
            cmd("fill 160 -60 0 160 -57 0 minecraft:air");
            cmd("tp @s 170.5 -60 0.5 90 -65");
        });
        step(15, () -> openGateLooking(90.0F, -65.0F));
        step(30, () -> {
            log("gate air " + describe(openGate()));
            shot("d_gate_air");
        });
        step(5, () -> {
            cmd("fill 176 -55 -4 184 -55 4 minecraft:deepslate_tiles");
            cmd("tp @s 180.5 -60 0.5 90 -65");
        });
        step(15, () -> openGateLooking(90.0F, -65.0F));
        step(30, () -> {
            log("gate ceiling " + describe(openGate()));
            shot("d_gate_ceiling");
        });
        step(5, () -> log("DONE failures=" + failures));
        step(20, () -> mc().stop());
    }

    // ---- gallery: Modrinth frames in a normal world ------------------------------------------

    /** The gallery world's seed: PHANTOM_SEED if set, else a fixed one. */
    private static final long GALLERY_SEED = seedFromEnv();
    /**
     * The gallery's site, found once the world is up: the top of a cliff, the way down it as a unit x/z
     * step, and the ground heights on top and at the foot. Spots are (along, side): blocks from the top
     * toward the foot, and to the right of that line.
     */
    private static int topX, topZ, downX = 1, downZ, topY, footY;
    /** Along the line: the cliff face at the foot's level, and the first open column at the rider's height. */
    private static double faceAtFoot, faceAtRider;
    private static int riderY;
    private static float yawDown;

    private static long seedFromEnv() {
        String s = System.getenv("PHANTOM_SEED");
        if (s == null || s.isBlank()) {
            return 4815162342L;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return s.hashCode();
        }
    }

    private static double gx(double along, double side) {
        return topX + 0.5 + along * downX - side * downZ;
    }

    private static double gz(double along, double side) {
        return topZ + 0.5 + along * downZ + side * downX;
    }

    /**
     * The first air block above the ground at (x, z): below any tree (logs and leaves), plants and snow
     * layers. Loads the chunk; client thread only.
     */
    private static int ground(double x, double z) {
        int bx = net.minecraft.util.Mth.floor(x);
        int bz = net.minecraft.util.Mth.floor(z);
        int[] out = new int[1];
        server(() -> {
            ServerLevel level = sp().serverLevel();
            level.getChunk(bx >> 4, bz >> 4);
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            while (y > level.getMinBuildHeight()) {
                net.minecraft.world.level.block.state.BlockState below = level.getBlockState(new net.minecraft.core.BlockPos(bx, y - 1, bz));
                if (below.is(net.minecraft.tags.BlockTags.LOGS) || below.is(net.minecraft.tags.BlockTags.LEAVES) || below.canBeReplaced()) {
                    y--;
                } else {
                    break;
                }
            }
            out[0] = y;
        });
        return out[0];
    }

    private static int groundAt(double along, double side) {
        return ground(gx(along, side), gz(along, side));
    }

    private static void tp(double x, double y, double z, float yaw, float pitch) {
        cmd(String.format(java.util.Locale.ROOT, "tp @s %.3f %.3f %.3f %.2f %.2f", x, y, z, yaw, pitch));
    }

    /** Teleports the player's feet to (along, side) at height y, their eyes on the world point (lx, ly, lz). */
    private static void tpLooking(double along, double side, double y, double lx, double ly, double lz) {
        double x = gx(along, side);
        double z = gz(along, side);
        double dx = lx - x;
        double dy = ly - (y + 1.62);
        double dz = lz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        tp(x, y, z, yaw, pitch);
    }

    private static void setAt(double along, double side, int y, String block) {
        cmd(String.format(java.util.Locale.ROOT, "setblock %d %d %d %s", net.minecraft.util.Mth.floor(gx(along, side)), y,
                net.minecraft.util.Mth.floor(gz(along, side)), block));
    }

    /** Holds the owner's pet of {@code kind} (staying) at (x, y, z), facing {@code yaw}. */
    private static void pose(PetKind kind, double x, double y, double z, float yaw) {
        server(() -> {
            PhantomPetEntity pet = PetManager.getActive(kind, p().getUUID());
            if (pet == null) {
                check(kind + " is out", false);
                return;
            }
            if (!pet.isOrderedToSit()) {
                pet.toggleStay(sp());
            }
            pet.setYRot(yaw);
            pet.yRotO = yaw;
            pet.yBodyRot = yaw;
            pet.yHeadRot = yaw;
            pet.relocate(x, y, z);
        });
    }

    private static void poseAt(PetKind kind, double along, double side, double above, float yaw) {
        pose(kind, gx(along, side), groundAt(along, side) + above, gz(along, side), yaw);
    }

    private static void fly(boolean on) {
        p().getAbilities().flying = on;
        p().onUpdateAbilities();
        server(() -> {
            sp().getAbilities().flying = on;
            sp().onUpdateAbilities();
        });
    }

    /** Waits {@code ticks}, then until the client has built every chunk section it has. */
    private static void settle(int ticks) {
        step(ticks, () -> {});
        until(() -> mc().levelRenderer.hasRenderedAllSections());
        step(20, () -> {});
    }

    /**
     * Finds the site near the closest cherry grove: the steepest dry drop of 6 to 40 blocks over 4, with
     * level ground on top and at the foot, from the terrain generator's heights (no chunks needed).
     */
    private static void scout() {
        server(() -> {
            ServerLevel level = sp().serverLevel();
            net.minecraft.core.BlockPos from = sp().blockPosition();
            var grove = level.findClosestBiome3d(b -> b.is(net.minecraft.world.level.biome.Biomes.CHERRY_GROVE), from, 6400, 32, 64);
            net.minecraft.core.BlockPos c = grove == null ? from : grove.getFirst();
            log("cherry grove " + (grove == null ? "not found, using spawn" : "at " + c));
            var gen = level.getChunkSource().getGenerator();
            var random = level.getChunkSource().randomState();
            int r = 112;
            int s = 2;
            int n = 2 * r / s + 1;
            int[][] land = new int[n][n];
            int[][] top = new int[n][n];
            // 2: cherry grove, 1: meadow, 0: other green land, -1: snow, ice or anything else.
            int[][] kind = new int[n][n];
            var biomes = gen.getBiomeSource();
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    int x = c.getX() - r + i * s;
                    int z = c.getZ() - r + j * s;
                    land[i][j] = gen.getBaseHeight(x, z, net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, level, random);
                    top[i][j] = gen.getBaseHeight(x, z, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, level, random);
                    var b = biomes.getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(x), net.minecraft.core.QuartPos.fromBlock(land[i][j]),
                            net.minecraft.core.QuartPos.fromBlock(z), random.sampler());
                    kind[i][j] = b.is(net.minecraft.world.level.biome.Biomes.CHERRY_GROVE) ? 2
                            : b.is(net.minecraft.world.level.biome.Biomes.MEADOW) ? 1
                            : b.is(net.minecraft.world.level.biome.Biomes.PLAINS) || b.is(net.minecraft.world.level.biome.Biomes.SUNFLOWER_PLAINS)
                                    || b.is(net.minecraft.world.level.biome.Biomes.FOREST) || b.is(net.minecraft.world.level.biome.Biomes.FLOWER_FOREST)
                                    || b.is(net.minecraft.world.level.biome.Biomes.BIRCH_FOREST) ? 0 : -1;
                }
            }
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            int best = -1;
            double bestDist = 0;
            for (int i = 4; i < n - 4; i++) {
                for (int j = 4; j < n - 4; j++) {
                    for (int[] d : dirs) {
                        int hTop = land[i][j];
                        int fi = i + 2 * d[0];
                        int fj = j + 2 * d[1];
                        int hFoot = land[fi][fj];
                        int drop = hTop - hFoot;
                        if (drop < 6 || drop > 40 || hFoot < 64 || top[fi][fj] != hFoot || top[i][j] != hTop
                                || kind[i][j] < 1 || kind[fi][fj] < 0 || kind[i + 4 * d[0]][j + 4 * d[1]] < 0) {
                            continue;
                        }
                        if (Math.abs(land[i + 3 * d[0]][j + 3 * d[1]] - hFoot) > 2 || Math.abs(land[i + 4 * d[0]][j + 4 * d[1]] - hFoot) > 3
                                || Math.abs(land[i - d[0]][j - d[1]] - hTop) > 2 || Math.abs(land[i - 3 * d[0]][j - 3 * d[1]] - hTop) > 3) {
                            continue;
                        }
                        int score = Math.min(drop, 18) + 6 * kind[i][j] + 3 * kind[fi][fj];
                        double dist = Math.hypot(i * s - r, j * s - r);
                        if (score > best || score == best && dist < bestDist) {
                            best = score;
                            bestDist = dist;
                            topX = c.getX() - r + i * s;
                            topZ = c.getZ() - r + j * s;
                            downX = d[0];
                            downZ = d[1];
                        }
                    }
                }
            }
            if (best < 0) {
                topX = c.getX();
                topZ = c.getZ();
                log("no cliff found; using the grove itself");
            }
            log("site top " + topX + ", " + topZ + " down " + downX + ", " + downZ + " drop score " + best);
        });
    }

    /** The highest ground on the line between two spots (along, side), a block either side of it. */
    private static int maxGround(double a0, double s0, double a1, double s1) {
        int top = Integer.MIN_VALUE;
        int steps = (int) Math.ceil(Math.hypot(a1 - a0, s1 - s0) * 2) + 1;
        for (int i = 0; i <= steps; i++) {
            double a = a0 + (a1 - a0) * i / steps;
            double s = s0 + (s1 - s0) * i / steps;
            for (double ds = -1; ds <= 1; ds++) {
                top = Math.max(top, groundAt(a, s + ds));
            }
        }
        return top;
    }

    /**
     * A first-person camera hovering at (along, side), its feet {@code above} blocks over the highest ground
     * between it and the subject (so nothing hides it), looking at the world point (lx, ly, lz).
     */
    private static void camera(double along, double side, double above, double lx, double ly, double lz) {
        double sa = (gx(along, side) - topX - 0.5) * downX + (gz(along, side) - topZ - 0.5) * downZ;
        double la = (lx - topX - 0.5) * downX + (lz - topZ - 0.5) * downZ;
        double ls = -(lx - topX - 0.5) * downZ + (lz - topZ - 0.5) * downX;
        int floor = maxGround(sa, side, sa + (la - sa) * 0.6, side + (ls - side) * 0.6);
        fly(true);
        tpLooking(along, side, Math.max(floor, groundAt(along, side)) + above, lx, ly, lz);
    }

    /**
     * Removes whole trees (connected logs and leaves) that reach into the box round two spots, so a view
     * is clear without leaving floating leaves. Stays within 8 blocks of the box.
     */
    private static void clearTrees(double a0, double s0, double a1, double s1, double pad, int y0, int y1) {
        double xa = gx(a0, s0);
        double za = gz(a0, s0);
        double xb = gx(a1, s1);
        double zb = gz(a1, s1);
        int x0 = net.minecraft.util.Mth.floor(Math.min(xa, xb) - pad);
        int x1 = net.minecraft.util.Mth.floor(Math.max(xa, xb) + pad);
        int z0 = net.minecraft.util.Mth.floor(Math.min(za, zb) - pad);
        int z1 = net.minecraft.util.Mth.floor(Math.max(za, zb) + pad);
        server(() -> {
            ServerLevel level = sp().serverLevel();
            java.util.ArrayDeque<net.minecraft.core.BlockPos> queue = new java.util.ArrayDeque<>();
            java.util.Set<net.minecraft.core.BlockPos> tree = new java.util.HashSet<>();
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(x, y, z);
                        if (isTree(level, pos) && tree.add(pos)) {
                            queue.add(pos);
                        }
                    }
                }
            }
            while (!queue.isEmpty() && tree.size() < 20000) {
                net.minecraft.core.BlockPos pos = queue.poll();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            net.minecraft.core.BlockPos next = pos.offset(dx, dy, dz);
                            if (next.getX() >= x0 - 8 && next.getX() <= x1 + 8 && next.getZ() >= z0 - 8 && next.getZ() <= z1 + 8
                                    && next.getY() >= y0 - 4 && next.getY() <= y1 + 24 && isTree(level, next) && tree.add(next)) {
                                queue.add(next);
                            }
                        }
                    }
                }
            }
            for (net.minecraft.core.BlockPos pos : tree) {
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                        net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE);
            }
            log("cleared " + tree.size() + " tree blocks");
        });
    }

    private static boolean isTree(ServerLevel level, net.minecraft.core.BlockPos pos) {
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        return state.is(net.minecraft.tags.BlockTags.LOGS) || state.is(net.minecraft.tags.BlockTags.LEAVES);
    }

    /** Sends away every pet of the owner's but the anchor, and those in {@code keep}. */
    private static void onlyPets(PetKind... keep) {
        java.util.List<PetKind> kept = java.util.List.of(keep);
        server(() -> {
            for (PetKind kind : new PetKind[] {PetKind.CHEST, PetKind.TANK, PetKind.COLLECTOR}) {
                if (!kept.contains(kind) && PetManager.getActive(kind, p().getUUID()) != null) {
                    PetManager.dismiss(kind, sp(), false);
                }
            }
        });
    }

    /** The open gateway's middle, or the world point at (along, side) if none is open. */
    private static Vec3 gateMiddle(double along, double side) {
        PhantomGatewayEntity gate = openGate();
        return gate == null ? new Vec3(gx(along, side), topY + 1.5, gz(along, side)) : gate.getBoundingBox().getCenter();
    }

    /**
     * Frames for the Modrinth gallery, 1920x1080, HUD hidden, in a normal world (PHANTOM_SEED picks it), at a
     * cliff by a cherry grove: the pets in a row; each panel; the bell; the gateway standing, from the side
     * and head on; riding the chest by the cliff face with a ledge being built; the collector at work; a
     * flat gateway from above, under a fall and from the cliff edge; the 1 x 2 gateway in a tunnel; the
     * gateway, tank and collector at night. Saved as g_*.png. Cameras hover over the ground between them
     * and what they show, and trees in the way are taken out whole.
     */
    private static void galleryScript() {
        until(() -> p() != null && mc().level != null && mc().screen == null);
        step(40, () -> org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc().getWindow().getWindow(), 1920, 1080));
        step(40, () -> {
            log("window " + mc().getWindow().getWidth() + "x" + mc().getWindow().getHeight() + " gui scale " + mc().getWindow().getGuiScale()
                    + " seed " + GALLERY_SEED);
            mc().options.pauseOnLostFocus = false;
            mc().options.hideGui = true;
            cmd("gamerule doDaylightCycle false");
            cmd("gamerule doMobSpawning false");
            cmd("gamerule doFireTick false");
            cmd("time set 2000");
            cmd("weather clear 100000");
            cmd("phantomstorage unlock all");
            String[] modes = {"anchor", "gateway", "chest", "tank", "collector", "saddle", "bell"};
            for (int i = 0; i < modes.length; i++) {
                cmd("item replace entity @s hotbar." + i + " with phantomstorage:phantom_charm[phantomstorage:charm_mode=\"" + modes[i] + "\"]");
            }
            cmd("item replace entity @s hotbar.8 with minecraft:spruce_planks 64");
            opacity(100);
        });
        step(10, Harness::stockAll);
        step(10, Harness::scout);
        step(5, () -> {
            fly(true);
            tp(topX + 0.5, 200, topZ + 0.5, 0, 0);
        });
        settle(400);
        step(5, () -> {
            topY = groundAt(0, 0);
            footY = groundAt(4, 0);
            yawDown = (float) Math.toDegrees(Math.atan2(-downX, downZ));
            faceAtFoot = 4;
            for (double a = 4; a >= -2; a -= 0.5) {
                if (groundAt(a, 0) >= footY + 3) {
                    faceAtFoot = a;
                    break;
                }
            }
            riderY = footY + Math.max(2, (topY - footY) / 2);
            faceAtRider = 0;
            for (double a = 0; a <= 6; a += 0.5) {
                if (groundAt(a, 0) <= riderY) {
                    faceAtRider = a;
                    break;
                }
            }
            log("heights top " + topY + " foot " + footY + " face at foot " + faceAtFoot + " rider y " + riderY + " at " + faceAtRider
                    + " yaw down " + yawDown);
            // Trees out of the cliff top's views, the foot's and the drop between.
            clearTrees(-12.0, 0, 0.5, 0, 5.0, topY - 2, topY + 14);
            clearTrees(3.0, 0, 15.0, 0, 5.0, footY - 2, topY + 14);
        });
        settle(40);

        // The four pets in a row over the cliff top, level with each other, the drop behind them.
        int[] summon = {2, 3, 0, 4};
        for (int slot : summon) {
            step(15, () -> useSlot(slot));
        }
        PetKind[] row = {PetKind.CHEST, PetKind.TANK, PetKind.ANCHOR, PetKind.COLLECTOR};
        double[] sides = {-1.95, -0.65, 0.65, 1.95};
        int[] rowY = new int[1];
        Runnable poseRow = () -> {
            for (int i = 0; i < row.length; i++) {
                pose(row[i], gx(-2.0, sides[i]), rowY[0] + 1.0, gz(-2.0, sides[i]), yawDown + 180.0F);
            }
        };
        step(30, () -> {
            rowY[0] = maxGround(-3.0, 0, -1.0, 0);
            for (double s = -2.5; s <= 2.5; s += 1.0) {
                rowY[0] = Math.max(rowY[0], groundAt(-2.0, s));
            }
            poseRow.run();
        });
        step(60, () -> {
            poseRow.run();
            camera(-7.0, 0, 0.4, gx(-2.0, 0), rowY[0] + 1.3, gz(-2.0, 0));
        });
        step(30, () -> shot("g_pets"));

        // Each panel, over the same view.
        PetKind[] kinds = {PetKind.CHEST, PetKind.TANK, PetKind.ANCHOR, PetKind.COLLECTOR};
        String[] names = {"chest", "tank", "anchor", "collector"};
        for (int i = 0; i < kinds.length; i++) {
            PetKind kind = kinds[i];
            String name = names[i];
            step(10, () -> server(() -> PetManager.getActive(kind, p().getUUID()).interact(sp(), InteractionHand.MAIN_HAND)));
            step(20, () -> parkMouse());
            step(5, () -> shot("g_panel_" + name));
            step(10, () -> p().closeContainer());
        }

        // The bell: every pet at once, seen from the front with the drop behind.
        step(10, () -> server(() -> PetManager.dismissAll(sp(), false)));
        step(20, () -> {
            fly(true);
            tp(gx(-1.5, 0), maxGround(-2.5, 0, -0.5, 0) + 0.3, gz(-1.5, 0), yawDown + 180.0F, -12.0F);
            mc().options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        });
        step(10, () -> useSlot(6));
        step(20, () -> shot("g_bell_a"));
        step(25, () -> shot("g_bell_b"));
        step(40, () -> shot("g_bell_c"));
        step(5, () -> mc().options.setCameraType(CameraType.FIRST_PERSON));

        // The anchor, anchored out of sight down the valley, for the gateways to lead to.
        step(5, () -> {
            onlyPets();
            server(() -> PetManager.dismiss(PetKind.ANCHOR, sp(), false));
            fly(false);
            tp(gx(30, 24), groundAt(30, 24), gz(30, 24), yawDown, 0);
        });
        settle(20);
        anchorHere(0);

        // The gateway standing on the cliff top: from the side, then head on.
        step(5, () -> {
            fly(false);
            tp(gx(-5.5, 0), groundAt(-5.5, 0), gz(-5.5, 0), yawDown, 0);
        });
        step(20, () -> openGateLooking(yawDown, 0.0F));
        step(25, () -> {
            log("gallery gate " + describe(openGate()));
            Vec3 m = gateMiddle(-3.0, 0);
            camera(-10.5, 4.5, 0.3, m.x, m.y, m.z);
        });
        step(25, () -> shot("g_gate"));
        step(5, () -> {
            Vec3 m = gateMiddle(-3.0, 0);
            camera(-10.5, 0, 0.3, m.x, m.y, m.z);
        });
        step(20, () -> shot("g_gate_through"));

        // Riding the chest by the cliff face, a ledge being built on it, planks in hand.
        step(5, () -> {
            server(() -> GatewayManager.closeFor(sp()));
            for (double side = -1; side <= 1; side++) {
                setAt(faceAtRider, side, riderY + 1, "minecraft:spruce_planks keep");
            }
            setAt(faceAtRider, 1, riderY + 2, "minecraft:lantern keep");
            setAt(faceAtRider, -1, riderY + 2, "minecraft:spruce_fence keep");
            setAt(faceAtRider, -1, riderY + 3, "minecraft:lantern keep");
            p().getInventory().selected = 8;
            server(() -> sp().getInventory().selected = 8);
            fly(true);
            tp(gx(faceAtRider + 2.5, -3.5), riderY, gz(faceAtRider + 2.5, -3.5), yawDown, 0);
            useSlot(2);
            p().getInventory().selected = 8;
            server(() -> sp().getInventory().selected = 8);
        });
        step(15, () -> server(() -> {
            PhantomPetEntity chest = PetManager.getActive(PetKind.CHEST, p().getUUID());
            if (chest == null) {
                check("chest out for the saddle shot", false);
                return;
            }
            chest.carry(sp(), new Vec3(gx(faceAtRider + 2.5, -3.5), riderY, gz(faceAtRider + 2.5, -3.5)));
        }));
        step(10, () -> {
            double x = gx(faceAtRider + 2.5, -3.5) - gx(faceAtRider, 0);
            double z = gz(faceAtRider + 2.5, -3.5) - gz(faceAtRider, 0);
            look((float) Math.toDegrees(Math.atan2(x, -z)), 18.0F);
            mc().options.setCameraType(CameraType.THIRD_PERSON_BACK);
        });
        step(30, () -> shot("g_saddle"));
        step(5, () -> {
            server(() -> sp().stopRiding());
            mc().options.setCameraType(CameraType.FIRST_PERSON);
            p().getInventory().selected = 0;
            server(() -> sp().getInventory().selected = 0);
            onlyPets();
        });

        // The collector at work at the cliff's foot: drops lying round it, taken one by one.
        step(10, () -> {
            server(() -> {
                com.phantomstorage.storage.CollectorSettings settings = sp().getData(com.phantomstorage.registry.ModRegistries.COLLECTOR);
                for (int i = 0; i < 4; i++) {
                    settings.filter().setItem(i, ItemStack.EMPTY);
                }
            });
            camera(4.5, -2.5, 2.0, gx(8.5, 0), groundAt(8.5, 0) + 0.8, gz(8.5, 0));
        });
        step(10, () -> useSlot(4));
        step(20, () -> poseAt(PetKind.COLLECTOR, 8.5, 0, 1.6, yawDown + 160.0F));
        step(10, () -> {
            net.minecraft.world.item.Item[] drops = {Items.BONE, Items.ARROW, Items.ROTTEN_FLESH, Items.STRING, Items.GUNPOWDER,
                    Items.SPIDER_EYE, Items.IRON_NUGGET, Items.COPPER_INGOT, Items.BONE, Items.ARROW, Items.STRING, Items.ROTTEN_FLESH,
                    Items.REDSTONE, Items.BONE, Items.GUNPOWDER, Items.ARROW};
            java.util.Random rand = new java.util.Random(7);
            double[][] spots = new double[drops.length][3];
            for (int i = 0; i < drops.length; i++) {
                double ang = rand.nextDouble() * Math.PI * 2;
                double rad = 1.2 + rand.nextDouble() * 2.6;
                spots[i][0] = gx(8.5 + Math.cos(ang) * rad, Math.sin(ang) * rad);
                spots[i][1] = gz(8.5 + Math.cos(ang) * rad, Math.sin(ang) * rad);
                spots[i][2] = ground(spots[i][0], spots[i][1]) + 0.05;
            }
            server(() -> {
                ServerLevel level = sp().serverLevel();
                for (int i = 0; i < drops.length; i++) {
                    ItemEntity item = new ItemEntity(level, spots[i][0], spots[i][2], spots[i][1], new ItemStack(drops[i], 1 + i % 3));
                    item.setDeltaMovement(Vec3.ZERO);
                    item.setPickUpDelay(90 + i * 6);
                    level.addFreshEntity(item);
                }
            });
            camera(4.5, -2.5, 2.0, gx(8.5, 0), groundAt(8.5, 0) + 0.8, gz(8.5, 0));
        });
        step(20, () -> {
            double ox = gx(9.2, -1.0);
            double oz = gz(9.2, -1.0);
            double oy = ground(ox, oz) + 0.3;
            server(() -> {
                for (int i = 0; i < 5; i++) {
                    sp().serverLevel().addFreshEntity(new net.minecraft.world.entity.ExperienceOrb(sp().serverLevel(), ox + i * 0.3, oy, oz - i * 0.25, 7));
                }
            });
        });
        step(2, () -> shot("g_collector_a"));
        step(70, () -> shot("g_collector_b"));
        step(9, () -> shot("g_collector_c"));
        step(9, () -> shot("g_collector_d"));
        step(15, () -> shot("g_collector_e"));

        // A gateway laid flat on the ground at the foot: from straight above, under a fall, from the edge.
        step(120, () -> {
            onlyPets();
            fly(true);
            tp(gx(6.0, 0), footY + 7, gz(6.0, 0), yawDown, 65.0F);
        });
        step(15, () -> openGateLooking(yawDown, 65.0F));
        step(25, () -> {
            PhantomGatewayEntity gate = openGate();
            log("gallery flat gate " + describe(gate));
            if (gate != null) {
                tp(gate.getX(), gate.getY() + 7.0, gate.getZ(), yawDown, 90.0F);
            }
        });
        step(25, () -> shot("g_gate_flat"));
        step(5, () -> {
            PhantomGatewayEntity gate = openGate();
            if (gate != null) {
                // Falling toward it, looking up: the camera in front of them, above, looking back down past them to it.
                tp(gate.getX() - downX * 0.6, gate.getY() + 5.0, gate.getZ() - downZ * 0.6, yawDown, -58.0F);
            }
            mc().options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        });
        step(25, () -> shot("g_gate_fall"));
        step(5, () -> {
            mc().options.setCameraType(CameraType.FIRST_PERSON);
            Vec3 m = gateMiddle(10.0, 0);
            fly(true);
            tpLooking(-0.6, 1.5, groundAt(-0.6, 1.5) + 0.2, m.x, m.y, m.z);
        });
        step(25, () -> shot("g_gate_below"));
        step(5, () -> server(() -> GatewayManager.closeFor(sp())));

        // The 1 x 2 gateway in a tunnel dug into the cliff, lit from its ceiling.
        step(10, () -> {
            for (double a = faceAtFoot + 2.0; a >= faceAtFoot - 8.0; a -= 1.0) {
                setAt(a, 0, footY, "minecraft:air");
                setAt(a, 0, footY + 1, "minecraft:air");
            }
            setAt(faceAtFoot - 5.0, 0, footY + 2, "minecraft:shroomlight");
            setAt(faceAtFoot - 2.0, 0, footY + 2, "minecraft:shroomlight");
            fly(false);
            tp(gx(faceAtFoot + 1.0, 0), footY, gz(faceAtFoot + 1.0, 0), yawDown + 180.0F, 0);
        });
        step(20, () -> openGateLooking(yawDown + 180.0F, 0.0F));
        step(25, () -> {
            log("gallery tunnel gate " + describe(openGate()));
            fly(true);
            tp(gx(faceAtFoot + 3.5, 0), footY + 0.4, gz(faceAtFoot + 3.5, 0), yawDown + 180.0F, 3.0F);
        });
        step(25, () -> shot("g_tunnel"));

        // Night: the gateway on the cliff top, the tank and collector by it; the brightness up, as many play.
        step(5, () -> {
            server(() -> GatewayManager.closeFor(sp()));
            cmd("time set 14500");
            mc().options.gamma().set(1.0);
            fly(false);
            tp(gx(-5.5, 0), groundAt(-5.5, 0), gz(-5.5, 0), yawDown, 0);
        });
        step(20, () -> openGateLooking(yawDown, 0.0F));
        step(5, () -> useSlot(4));
        step(10, () -> useSlot(3));
        step(20, () -> {
            Vec3 m = gateMiddle(-3.0, 0);
            pose(PetKind.COLLECTOR, gx(-3.2, -2.7), m.y + 0.2, gz(-3.2, -2.7), yawDown + 200.0F);
            pose(PetKind.TANK, gx(-3.6, 2.6), m.y - 1.0, gz(-3.6, 2.6), yawDown + 160.0F);
        });
        step(25, () -> {
            log("gallery night gate " + describe(openGate()));
            Vec3 m = gateMiddle(-3.0, 0);
            camera(-11.0, 2.0, 0.3, m.x, m.y, m.z);
        });
        step(30, () -> shot("g_night"));
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
            check("W + look up climbs", now.y > mark.y + 2);
            check("W + look up goes forward (+z)", now.z > mark.z + 4);
            double cruise = vehicle().getDeltaMovement().length();
            check("cruise is a little under a walk (0.19..0.21 b/t, walk 0.216)", cruise > 0.19 && cruise < 0.21);
            mark = now;
            keys(true, false, false, true);
        });
        step(30, () -> {
            Vec3 now = vehicle().position();
            log("sprint 30t: dist " + now.distanceTo(mark) + " speed " + vehicle().getDeltaMovement().length() + " sprinting " + p().isSprinting());
            double sprint = vehicle().getDeltaMovement().length();
            check("sprint is a little under a player's sprint (0.26..0.28 b/t, sprint 0.281)", sprint > 0.26 && sprint < 0.28);
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
        // An anchor still out from earlier would be anchored where it is: start fresh, by the player.
        step(20, () -> server(() -> PetManager.dismiss(PetKind.ANCHOR, sp(), false)));
        step(10, () -> {});
        anchorHere(2);
        step(10, () -> cmd("tp @s 140.5 -60 0.5 90 0"));
        step(30, () -> {
            p().getInventory().selected = 5;
            server(() -> sp().getInventory().selected = 5);
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
            // The gallery wants real terrain; every other script a flat world.
            boolean gallery = "gallery".equals(System.getenv("PHANTOM_HARNESS"));
            mc.createWorldOpenFlows().createFreshLevel(gallery ? "gallery" : "harness", settings,
                    new WorldOptions(gallery ? GALLERY_SEED : 1234L, gallery, false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(gallery ? WorldPresets.NORMAL : WorldPresets.FLAT).value().createWorldDimensions(),
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
