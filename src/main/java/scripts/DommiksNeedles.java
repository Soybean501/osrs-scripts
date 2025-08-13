package scripts;

import org.dreambot.api.Client;
import org.dreambot.api.methods.container.impl.Inventory;
import org.dreambot.api.methods.container.impl.Shop;
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
    private static final int BANK_THRESHOLD = 27; // bank when inventory nearly full
    private static final BankLocation TARGET_BANK = BankLocation.AL_KHARID;
    private static final int NEAR_BANK_DISTANCE = 25;
    private int exploreStepIndex = 0;

    private int boughtThisWorld = 0;
    private int consecutiveNoGainBuys = 0;

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

        // Bank if inventory is full or many needles held
        if (shouldBank()) {
            if (openAlKharidBank()) {
                if (Inventory.contains(ITEM_NAME)) {
                    Bank.depositAll(ITEM_NAME);
                    sleepUntil(() -> !Inventory.contains(ITEM_NAME), 50, 2000);
                }
                Bank.close();
            }
            return 400;
        }

        // If shop is open, buy up to 3 needles for this world
        if (Shop.isOpen()) {
            int remaining = BUY_PER_WORLD - boughtThisWorld;
            if (remaining > 0) {
                int before = Inventory.count(ITEM_NAME);
                boolean didBuy;
                // Always prefer Buy 5 to reduce click count
                didBuy = false;
                try {
                    // Try modern API: Shop.purchase(String name, int amount)
                    didBuy = (boolean) Shop.class
                            .getMethod("purchase", String.class, int.class)
                            .invoke(null, ITEM_NAME, 5);
                } catch (Throwable ignored) {
                    // Fallback to explicit helper if available
                }
                if (!didBuy) {
                    didBuy = Shop.purchaseFive(ITEM_NAME);
                }
                if (didBuy) {
                    sleepUntil(
                        () -> Inventory.count(ITEM_NAME) > before,
                        50,
                        1200
                    );
                    int gained = Math.max(
                        0,
                        Inventory.count(ITEM_NAME) - before
                    );
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
        return (
            Inventory.isFull() || Inventory.count(ITEM_NAME) >= BANK_THRESHOLD
        );
    }

    private boolean openAlKharidBank() {
        if (Bank.isOpen()) return true;
        if (Bank.open(BankLocation.AL_KHARID)) {
            return sleepUntil(Bank::isOpen, 50, 5000);
        }
        return false;
    }

    private void hopWorld() {
        // Hop to a free-to-play, normal world different from the current
        World target = Worlds.getRandomWorld(
            w ->
                w != null &&
                w.isNormal() &&
                !w.isPVP() &&
                !w.isMembers() &&
                w.getWorld() != Client.getCurrentWorld()
        );
        if (target != null) {
            log("Hopping to world " + target.getWorld());
            if (WorldHopper.hopWorld(target)) {
                sleepUntil(() -> Players.getLocal() != null, 50, 8000);
                boughtThisWorld = 0;
                consecutiveNoGainBuys = 0;
            }
        } else {
            log("No suitable world to hop found.");
        }
    }
}
