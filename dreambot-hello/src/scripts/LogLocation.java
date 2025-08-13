package scripts;

import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.map.Tile;
import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;

@ScriptManifest(
        name = "LogLocation",
        description = "Logs the player's current coordinates to the console",
        author = "You",
        version = 1.1,
        category = Category.MISC
)
public class LogLocation extends AbstractScript {
    private boolean hasLoggedCoordinates = false;

    @Override
    public void onStart() {
        log("LogLocation starting. Waiting for login...");
    }

    @Override
    public int onLoop() {
        if (Players.getLocal() == null) {
            // Wait for DreamBot to log in or spawn the local player
            return 600;
        }

        if (!hasLoggedCoordinates) {
            Tile tile = Players.getLocal().getTile();
            if (tile != null) {
                log(String.format("player coordinates: (%d, %d, %d)", tile.getX(), tile.getY(), tile.getZ()));
            } else {
                log("player coordinates unavailable (tile is null)");
            }
            hasLoggedCoordinates = true;
            // Stop after logging once so this can be used as a quick tool
            stop();
            return 0;
        }

        return 600;
    }
}
