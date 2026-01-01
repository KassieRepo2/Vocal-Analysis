package com.kass.vocalanalysistool.view.util;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import javafx.stage.Stage;

public class StageRegistry {

    /**
     * Stores the stages in a map for references.
     */
    private static final Map<String, Stage> STAGES = new HashMap<>();

    private StageRegistry() {}

    public static boolean isOpen(final String theKey) {
        return STAGES.containsKey(theKey);
    }

    /**
     * Registers the stage in a directory to track if its already instantiated.
     *
     * @param theKey the name of the stage
     * @param theFactory the stage factory
     * @return the stage.
     */
    public static Stage show(final String theKey, final Supplier<Stage> theFactory) {
        return STAGES.compute(theKey, (k, s) -> {
            if(s == null || !s.isShowing()) {
                s = theFactory.get();
                s.setOnHidden(e -> STAGES.remove(k));
                s.show();
            } else {
                s.toFront();
            }
            return s;
        });
    }



    /**
     * Gets the open stage for other stages to manipulate.
     * If the stage does not exist, null is returned.
     * @param theKey The key used when instantiating the stage.
     * @return The stage with the correlated key
     */
    public static Stage getStage(final String theKey) {
        Stage stage;
        stage = STAGES.getOrDefault(theKey, null);
        return stage;
    }
}
