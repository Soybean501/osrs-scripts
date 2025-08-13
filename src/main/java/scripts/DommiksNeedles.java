package scripts;

import org.dreambot.api.Client;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.container.impl.Shop;
import org.dreambot.api.wrappers.items.Item;
import org.dreambot.api.methods.container.impl.bank.Bank;
import org.dreambot.api.methods.container.impl.bank.BankLocation;
import org.dreambot.api.methods.interactive.NPCs;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.methods.walking.impl.Walking;
import org.dreambot.api.methods.world.World;
import org.dreambot.api.methods.world.Worlds;
import org.dreambot.api.methods.worldhopper.WorldHopper;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.wrappers.interactive.NPC;

// Mouse speed slowing is attempted via reflection to support multiple API versions

@ScriptManifest(
    name = "Dommiks Needles",
    description = "Simple script: Tele Al Kharid, buy 3 needles from Dommik's Crafting Store, hop. repeat, bank, repeat",
    author = "You",
    version = 1.1,
    category = Category.MONEYMAKING
)
public class DommiksNeedles extends AbstractScript {

    private static final String ITEM_NAME = "Needle";
    private static final String NPC_NAME = "Dommik";
    private static final Tile SHOP_TILE = new Tile(3318, 3193, 0);
    private static final int BUY_PER_WORLD = 3;
    private static final int BANK_THRESHOLD = 27; // unused when only banking on full
    private static final BankLocation TARGET_BANK = BankLocation.AL_KHARID;
    private static final int NEAR_BANK_DISTANCE = 25;
    private int exploreStepIndex = 0;

    private int boughtThisWorld = 0;
    private int consecutiveNoGainBuys = 0;
    private final java.util.Set<Integer> rejectedWorldIds = new java.util.HashSet<>();

    @Override
    public void onStart() {
        log(
            "Dommiks Needles started. Will buy 3 per world, then hop. Banking when full."
        );
        try {
            // Try a few possible MouseSettings class locations across API versions
            String[] candidates = new String[] {
                "org.dreambot.api.input.MouseSettings",
                "org.dreambot.api.methods.input.MouseSettings",
                "org.dreambot.api.methods.input.mouse.MouseSettings",
                "org.dreambot.api.settings.MouseSettings",
            };
            for (String className : candidates) {
                try {
                    Class<?> cls = Class.forName(className);
                    try {
                        // Prefer static setSpeed(int)
                        cls.getMethod("setSpeed", int.class).invoke(null, 8);
                        break;
                    } catch (NoSuchMethodException ignored) {
                        // Try instance-based API: get() or instance() returning settings object
                        try {
                            Object instance = null;
                            try {
                                instance = cls.getMethod("get").invoke(null);
                            } catch (NoSuchMethodException e) {
                                /* ignore */
                            }
                            if (instance == null) {
                                try {
                                    instance = cls
                                        .getMethod("instance")
                                        .invoke(null);
                                } catch (NoSuchMethodException e) {
                                    /* ignore */
                                }
                            }
                            if (instance != null) {
                                cls
                                    .getMethod("setSpeed", int.class)
                                    .invoke(instance, 8);
                                break;
                            }
                        } catch (Throwable ignored2) {
                            /* continue to next candidate */
                        }
                    }
                } catch (ClassNotFoundException ignored) {
                    // Try next candidate
                }
            }
        } catch (Throwable t) {
            // Safe fallback: do nothing if API class not present
        }
    }

    @Override
    public int onLoop() {
        // Wait for login
        if (Players.getLocal() == null) {
            return 600;
        }

        // Needles stack; never bank for needles

        // If shop is open, buy up to 3 needles for this world
        if (Shop.isOpen()) {
            int remaining = BUY_PER_WORLD - boughtThisWorld;
            if (remaining > 0) {
                int before = Inventory.count(ITEM_NAME);
                boolean didBuy = false;
                Item item = null;
                for (Item it : Shop.all()) {
                    if (it != null && ITEM_NAME.equalsIgnoreCase(it.getName()) && it.getAmount() > 0) { item = it; break; }
                }
                if (item == null) {
                    log("No needles in stock; hopping world.");
                    Shop.close();
                    hopWorld();
                    return 600;
                }
                didBuy = item.interact("Buy 5");
                if (!didBuy) didBuy = item.interact("Buy-5");
                if (!didBuy) didBuy = item.interact("Buy Five");
                if (!didBuy) {
                    log("Buy 5 failed; likely out of stock. Hopping world.");
                    Shop.close();
                    hopWorld();
                    return 600;
                }
                if (didBuy) {
                    sleepUntil(() -> Inventory.count(ITEM_NAME) > before, 50, 3000);
                    int first = Inventory.count(ITEM_NAME);
                    long stableSince = System.currentTimeMillis();
                    long deadline = System.currentTimeMillis() + 2500;
                    int last = first;
                    while (System.currentTimeMillis() < deadline) {
                        sleep(120);
                        int cur = Inventory.count(ITEM_NAME);
                        if (cur > last) {
                            last = cur;
                            stableSince = System.currentTimeMillis();
                        } else if (System.currentTimeMillis() - stableSince >= 350) {
                            break;
                        }
                    }
                    int gained = Math.max(0, last - before);
                    if (gained > 0) {
                        boughtThisWorld += gained;
                        consecutiveNoGainBuys = 0;
                    } else {
                        consecutiveNoGainBuys++;
                        if (consecutiveNoGainBuys >= 2) {
                            log(
                                "No stock detected after buy attempts; hopping world."
                            );
                            Shop.close();
                            hopWorld();
                            return 300;
                        }
                    }
                } else {
                    log("Buy action failed; likely no stock. Hopping world.");
                    Shop.close();
                    hopWorld();
                    return 300;
                }
            }
            if (boughtThisWorld >= BUY_PER_WORLD) {
                Shop.close();
                hopWorld();
                return 600; // pause a bit after hop request
            }
            return 300;
        }

        // Not in shop UI; navigate to Dommik and open shop
        NPC dommik = NPCs.closest(NPC_NAME);
        if (dommik == null) {
            // Walk directly to the shop location and wait for NPC to load
            if (Walking.shouldWalk(6)) {
                if (Players.getLocal().distance(SHOP_TILE) > 4) {
                    log("Walking to Dommik's Crafting Shop...");
                    Walking.walk(SHOP_TILE);
                }
            }
            return 400;
        }

        // If NPC is visible, trade; otherwise walk to him
        if (dommik.distance(Players.getLocal()) > 6) {
            if (Walking.shouldWalk(4)) {
                log("Walking to Dommik...");
                Walking.walk(dommik);
            }
            return 400;
        }
        if (dommik.interact("Trade")) {
            log("Opening shop...");
            sleepUntil(Shop::isOpen, 50, 3000);
        }

        return 400;
    }

    private boolean shouldBank() {
        // Only bank when inventory is actually full
        return Inventory.isFull();
    }

    private boolean openAlKharidBank() {
        if (Bank.isOpen()) return true;
        if (Bank.open(BankLocation.AL_KHARID)) {
            return sleepUntil(Bank::isOpen, 50, 5000);
        }
        return false;
    }

    private void hopWorld() {
        // Prefer sequential hopping to nearest eligible F2P non-restricted world
        java.util.List<World> eligible = getEligibleWorlds();
        if (eligible.isEmpty()) { log("No suitable world to hop found."); return; }
        int current = Client.getCurrentWorld();
        int startIndex = 0;
        for (int i = 0; i < eligible.size(); i++) {
            if (eligible.get(i).getWorld() > current) { startIndex = i; break; }
        }
        int attempts = 0;
        for (int offset = 0; offset < eligible.size(); offset++) {
            World candidate = eligible.get((startIndex + offset) % eligible.size());
            int wid = candidate.getWorld();
            if (wid == current || rejectedWorldIds.contains(wid)) continue;
            if (!isEligibleWorld(candidate)) { rejectedWorldIds.add(wid); continue; }
            log("Hopping to world " + wid);
            attempts++;
            int previousWorld = current;
            if (WorldHopper.hopWorld(candidate)) {
                // Wait until the client's world actually changes
                boolean worldChanged = sleepUntil(() -> Client.getCurrentWorld() == wid, 100, 15000);
                if (!worldChanged) {
                    // Fallback: allow some extra time if login widgets in between
                    sleep(1000);
                    worldChanged = (Client.getCurrentWorld() == wid);
                }
                if (worldChanged) {
                    boughtThisWorld = 0;
                    consecutiveNoGainBuys = 0;
                    return;
                }
            }
            // Mark as rejected if hop failed or didn't land
            rejectedWorldIds.add(wid);
            current = Client.getCurrentWorld();
        }
        log("Unable to hop after " + attempts + " attempts; will retry later.");
    }

    private boolean isEligibleWorld(World world) {
        if (world == null) return false;
        // Only consider standard, non-members, non-PvP worlds
        if (!world.isNormal()) return false;
        if (world.isMembers()) return false;
        if (world.isPVP()) return false;

        // Reject if there is any explicit minimum total level requirement
        try {
            java.lang.reflect.Method m = World.class.getMethod("getMinimumLevel");
            Object value = m.invoke(world);
            if (value instanceof Integer && ((Integer) value) > 0) return false;
        } catch (Throwable ignored) { /* method may not exist in some API versions */ }

        // Inspect activity text for restrictions
        String activity = null;
        try { activity = (String) World.class.getMethod("getActivity").invoke(world); } catch (Throwable ignored) {}
        if (activity != null && !activity.isEmpty()) {
            String a = activity.toLowerCase();
            // Any skill total or total level requirement
            if (a.contains("skill total")) return false;
            // Generic "total" with a number (e.g., "1250 total")
            if (a.contains("total") && a.matches(".*\\b[0-9]{3,4}\\b.*")) return false;
            // Known restricted styles
            if (a.contains("pvp")) return false;              // PvP, PK, Arena variants
            if (a.contains("wilderness")) return false;       // Wilderness PK worlds
            if (a.contains(" pk")) return false;              // any ' PK' marker
            if (a.contains("arena")) return false;            // PvP Arena (legacy or not)
            if (a.contains("deadman")) return false;
            if (a.contains("bounty")) return false;           // Bounty Hunter
            if (a.contains("lms")) return false;              // LMS casual/competitive
            if (a.contains("speedrunning")) return false;
            if (a.contains("fresh start")) return false;
            if (a.contains("beta")) return false;
            if (a.contains("high risk")) return false;
            if (a.contains("tournament")) return false;
        }

        // Finally, avoid selecting the current world
        return world.getWorld() != Client.getCurrentWorld();
    }

    private java.util.List<World> getEligibleWorlds() {
        java.util.List<World> worlds = null;
        try { worlds = (java.util.List<World>) Worlds.class.getMethod("getWorlds").invoke(null); } catch (Throwable ignored) {}
        if (worlds == null) {
            worlds = new java.util.ArrayList<>();
            for (int w = 301; w <= 628; w++) {
                World wo = Worlds.getWorld(w);
                if (wo != null) worlds.add(wo);
            }
        }
        java.util.List<World> eligible = new java.util.ArrayList<>();
        for (World w : worlds) {
            try {
                if (isEligibleWorld(w)) eligible.add(w);
            } catch (Throwable t) {
                // Defensive: skip any world that triggers API issues
            }
        }
        eligible.sort(java.util.Comparator.comparingInt(World::getWorld));
        return eligible;
    }
}
