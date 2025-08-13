package scripts;

import org.dreambot.api.script.AbstractScript;
import org.dreambot.api.script.Category;
import org.dreambot.api.script.ScriptManifest;
import org.dreambot.api.methods.interactive.Players;
import org.dreambot.api.methods.map.Tile;

@ScriptManifest(
        name = "HelloWorld",
        description = "Logs 'hello world!' to the debug console",
        author = "You",
        version = 1.1,
        category = Category.MISC
)
public class HelloWorld extends AbstractScript {
    private boolean hasLoggedMessage = false;

    @Override
    public void onStart() {
        log("Starting HelloWorld. Waiting for login...");
    }

    @Override
    public int onLoop() {
        if (Players.getLocal() == null) {
            // Wait for DreamBot's auto-login/account manager
            return 600;
        }

        if (!hasLoggedMessage) {
            Tile tile = Players.getLocal().getTile();
            if (tile != null) {
                log(String.format("hello world! tile=(%d, %d, %d)", tile.getX(), tile.getY(), tile.getZ()));
            } else {
                log("hello world! (player not found)");
            }
            hasLoggedMessage = true;
            stop();
            return 0;
        }

        return 600;
    }
}
