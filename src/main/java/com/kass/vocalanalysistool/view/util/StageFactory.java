package com.kass.vocalanalysistool.view.util;

import java.io.IOException;
import java.util.Objects;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * Builds the stage of the next scene
 *
 * @author Kassie Whitney
 * @version 12/28/2025
 */
public class StageFactory {
    private StageFactory() {
    }

    /**
     * Builds the stage of the next scene.
     *
     * @param theCurrentClass The current class that is building the stage. (this).
     * @param theFxmlName The name of the fxml file. Must be in the gui directory.
     * @param theStageTitle The title of the stage being built.
     * @param theStageIconPath The relative path of the icon file.
     * @param theSetResizable True makes the stage resizable, false makes the size static.
     * @return A stage object with all the corresponding attributes.
     */
    public static Stage buildStage(final Object theCurrentClass, final String theFxmlName,
                                   final String theStageTitle, final String theStageIconPath,
                                   final boolean theSetResizable) {
        final FXMLLoader fxmlLoader = new FXMLLoader(theCurrentClass.getClass().
                getResource("/com/kass/vocalanalysistool/gui/" + theFxmlName));
        try {

            final Scene scene = new Scene(fxmlLoader.load());
            final Stage stage = new Stage();
            stage.setScene(scene);
            stage.setTitle(theStageTitle);
            stage.getIcons().add(new Image(Objects.requireNonNull(theCurrentClass.getClass().
                    getResourceAsStream(theStageIconPath))));
            stage.setResizable(theSetResizable);
            return stage;

        } catch (final IOException theException) {
            throw new RuntimeException("The scene failed to load!");
        }
    }

    /**
     * Builds the stage of the next scene.
     * <P>Sets a default icon to the stage</P>
     *
     * @param theCurrentClass The current class that is building the stage. (this).
     * @param theFxmlName The name of the fxml file. Must be in the gui directory.
     * @param theStageTitle The title of the stage being built.
     * @param theSetResizable True makes the stage resizable, false makes the size static.
     * @return A stage object with all the corresponding attributes.
     */
    public static Stage buildStage(final Object theCurrentClass, final String theFxmlName,
                                   final String theStageTitle, final boolean theSetResizable) {
        final FXMLLoader fxmlLoader = new FXMLLoader(theCurrentClass.getClass().
                getResource("/com/kass/vocalanalysistool/gui/" + theFxmlName));
        try {

            final Scene scene = new Scene(fxmlLoader.load());
            final Stage stage = new Stage();
            stage.setScene(scene);
            stage.setTitle(theStageTitle);
            stage.getIcons().add(new Image(Objects.requireNonNull(theCurrentClass.getClass().
                    getResourceAsStream("com/kass/vocalanalysistool/icons/vocal_analysis_icon.png"))));
            stage.setResizable(theSetResizable);
            return stage;

        } catch (final IOException theException) {
            throw new RuntimeException("The scene failed to load!");
        }
    }
}
