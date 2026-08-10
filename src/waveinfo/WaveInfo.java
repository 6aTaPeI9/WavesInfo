package waveinfo;

import arc.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.Element;
import arc.scene.ui.layout.*;
import arc.util.*;
import mindustry.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.mod.*;
import mindustry.ui.*;

import static arc.Core.*;
import static mindustry.Vars.*;

public class WaveInfo extends Mod {
    private WaveInfoDialog dialog;
    private Table buttonTable;

    @Override
    public void init() {
        Core.settings.defaults("waveinfo-path-ground", false);
        Core.settings.defaults("waveinfo-path-naval", false);
        Core.settings.defaults("waveinfo-path-legs", false);
        Core.settings.defaults("waveinfo-path-hover", false);
        Core.settings.defaults("waveinfo-path-flying", false);

        Events.on(ClientLoadEvent.class, e -> {
            dialog = new WaveInfoDialog();

            // floating button, anchored below the wave counter at the top left
            buttonTable = new Table();
            buttonTable.button(Icon.waves, Styles.cleari, dialog::toggle)
                .size(38f)
                .tooltip("WaveInfo [W]")
                .with(b -> b.name = "waveinfo-button");
            buttonTable.pack();
            buttonTable.update(() -> {
                //anchor below the wave status panel: localToStageCoordinates(0,0) is its bottom-left corner
                Element status = Core.scene.find("statustable");
                if (status != null) {
                    Vec2 pos = status.localToStageCoordinates(Tmp.v1.set(0, 0));
                    buttonTable.setPosition(pos.x, pos.y - buttonTable.getHeight() - 4f);
                }
            });
            buttonTable.visible(() -> state != null && state.isGame());
            scene.add(buttonTable);

            PathLineDraw.init();
        });
    }
}
