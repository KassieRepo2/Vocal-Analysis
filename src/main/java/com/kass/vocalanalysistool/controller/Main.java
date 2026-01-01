package com.kass.vocalanalysistool.controller;

import com.kass.vocalanalysistool.common.ChangeEvents;
import com.kass.vocalanalysistool.view.LoadingScreenController;
import com.kass.vocalanalysistool.view.util.EnvironmentUtils;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import java.io.IOException;
import javafx.stage.StageStyle;

/**
 * Opens the file selector scene.
 *
 * @author Kassie Whitney
 * @version 9.4.25
 */
public class Main extends Application implements PropertyChangeListener {

    /**
     * Logger for debugging.
     */
    private static final Logger logger = Logger.getLogger(Main.class.getName());

    /**
     * Fires property changes.
     */
    private final PropertyChangeSupport myChanges = new PropertyChangeSupport(this);


    @Override
    public void start(final Stage theStage) throws IOException, InterruptedException {
        final FXMLLoader fxmlLoader = new FXMLLoader(Main.class.getResource(
                "/com/kass/vocalanalysistool/gui/SelectAudioFile.fxml"));

        final Scene scene = new Scene(fxmlLoader.load(), 453, 400);
        theStage.setTitle("Select Audio File");
        theStage.setScene(scene);
        theStage.setResizable(false);
        theStage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream(
                "/com/kass/vocalanalysistool/icons/vocal_analysis_icon.png"))));

        if (EnvironmentUtils.isEnvironmentReady()) {
            theStage.show();
        } else {
            startEnvironmentCheckAsync(theStage);
        }

    }

    /**
     * Launches the stages.
     * @param args System arguments from the command line; not used.
     */
    public static void main(String[] args) {
        initLogging();
        launch();

    }


    /**
     * Initializes logging file prior to starting the environment.
     */
    private static void initLogging() {
        try {
            final String localAppData = System.getenv("LOCALAPPDATA");
            final Path logDir = (localAppData != null && !localAppData.isBlank())
                    ? Path.of(localAppData, "VocalAnalysisTool", "logs")
                    : Path.of(System.getProperty("user.home"), "VocalAnalysisTool", "logs");

            Files.createDirectories(logDir);

            final Path logFile = logDir.resolve("app.log");

            final FileHandler fh = new FileHandler(logFile.toString(), true);
            fh.setFormatter(new SimpleFormatter());

            final Logger root = Logger.getLogger("");
            root.addHandler(fh);
            root.setLevel(Level.INFO);

        } catch (final IOException theEvent) {
            logger.log(Level.SEVERE, "Unable to initialize logging", theEvent);

            Throwable cause = theEvent.getCause();

            while(cause != null) {
                logger.log(Level.SEVERE, "[Stack Trace] " , cause.getStackTrace());
                cause = cause.getCause();
            }
        }
    }

    /**
     * Starts environment check in the background so the UI thread never blocks/crashes.
     */
    private void startEnvironmentCheckAsync(final Stage theStage) {
        final AtomicReference<Stage> loadingStageRef = new AtomicReference<>();

        // Show loading screen on FX thread.
        Platform.runLater(() -> {
            try {

                final FXMLLoader loadingScreenFXML = new FXMLLoader(Main.class.getResource(
                        "/com/kass/vocalanalysistool/gui/LoadingScreen.fxml"));
                final Scene loadingScreenScene = new Scene(loadingScreenFXML.load());
                final LoadingScreenController loadingScreenController = loadingScreenFXML.getController();

                // Let the loading screen listen to progress messages.
                // (Assumes your LoadingScreenController uses property change events.)
                loadingScreenController.addMainToChangeListener(this);

                final Stage loadingScreenStage = new Stage();
                loadingScreenStage.initStyle(StageStyle.UNDECORATED);
                loadingScreenStage.setScene(loadingScreenScene);
                loadingScreenStage.getIcons().add(new Image(Objects.requireNonNull(
                        Main.class.getResourceAsStream("/com/kass/vocalanalysistool/icons/vocal_analysis_icon.png"))));
                loadingScreenStage.setResizable(false);
                loadingScreenStage.setAlwaysOnTop(true);
                loadingScreenStage.show();

                loadingStageRef.set(loadingScreenStage);
            } catch (final IOException e) {
                logger.log(Level.SEVERE, "Failed to load LoadingScreen.fxml", e);
                // If loading screen fails, just continue without it.
            }
        });


        final Thread t = getThread(theStage, loadingStageRef);
        t.start();

    }


    /**
     * Runs the installation thread and starts the FX main thread upon completion.
     * @param theStage the Main stage
     * @param loadingStageRef the stage Reference
     * @return the thread object.
     */
    private Thread getThread(final Stage theStage,
                             final AtomicReference<Stage> loadingStageRef) {
        final Task<Void> envTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                checkNewEnvironment(); // background thread
                return null;
            }
        };

        envTask.setOnSucceeded(evt -> {
            final Stage st = loadingStageRef.get();
            if (st != null) st.close();

            logger.info("Environment check completed.");

            Platform.runLater(theStage::show);
        });

        envTask.setOnFailed(evt -> {
            final Stage st = loadingStageRef.get();
            if (st != null) st.close();

            final Throwable ex = envTask.getException();
            logger.log(Level.SEVERE, "Environment check failed.", ex);

            Platform.runLater(() -> {
                final Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Setup Failed");
                alert.setHeaderText("Python environment setup failed.");
                alert.setContentText("""
                        The application could not set up its Python environment.
                        
                        Please try running the app again, or check your internet connection.
                        If this keeps happening, run the EXE with --win-console and send the log output.
                        """);
                alert.showAndWait();
                Platform.exit();
            });
        });

        final Thread t = new Thread(envTask, "EnvSetup");
        t.setDaemon(true);
        return t;
    }

    /**
     * Checks if the virtual environment is already installed.
     *
     * @throws IOException          if the path to the virtual environment is invalid
     * @throws InterruptedException thrown if the thread was interrupted
     */
    private void checkNewEnvironment() throws IOException, InterruptedException {

        final Path dataDir = getDataDir();
        final Path readyMark = dataDir.resolve(".venv").resolve(".ready");
        final Path venvPy = dataDir.resolve(".venv").resolve("Scripts").resolve("python.exe");
        final boolean venvReady = Files.exists(readyMark) && Files.exists(venvPy);

        final Path setupBat = extractResourceToDir(
                "/pythonInstall.bat",
                dataDir,
                "pythonInstall.bat"
        );

        if (venvReady) {
            logger.info("Venv already ready. Skipping setup.");
            myChanges.firePropertyChange(ChangeEvents.SKIP_INSTALL.name(), null, null);

            return;
        }

        myChanges.firePropertyChange(ChangeEvents.NEW_INSTALL_A.name(), null,
                null);

        myChanges.firePropertyChange(ChangeEvents.NEW_INSTALL_B.name(), null, 0.05);

        final ProcessBuilder setupPB = new ProcessBuilder(
                "cmd.exe", "/c", "call", setupBat.getFileName().toString()
        );

        setupPB.directory(dataDir.toFile());
        setupPB.redirectErrorStream(true);

        final Process setupProc = setupPB.start();

        try (final BufferedReader reader =
                     new BufferedReader(new InputStreamReader(setupProc.getInputStream()))) {

            String ln;
            double prog = 0.05;

            while ((ln = reader.readLine()) != null) {
                logger.info("[setup] " + ln);

                prog = Math.min(0.99, prog + 0.005);
                myChanges.firePropertyChange(ChangeEvents.NEW_INSTALL_B.name(), ln, prog);
            }
        }

        final int setupExit = setupProc.waitFor();
        if (setupExit != 0) {
            throw new IOException("Environment setup failed (exit " + setupExit + ")");
        }

        myChanges.firePropertyChange(ChangeEvents.NEW_INSTALL_B.name(), null, 1.0);
    }


    /**
     * Gets a writable directory for the python environment.
     *
     * @return the path of the directory that the python environment is installed on.
     * @throws IOException thrown if the directory can not be created.
     */
    private static Path getDataDir() throws IOException {

        // If running from a jpackage launcher, store python/venv in LocalAppData
        final String jpackageAppPath = System.getProperty("jpackage.app-path");
        if (jpackageAppPath != null && !jpackageAppPath.isBlank()) {
            final String localAppData = System.getenv("LOCALAPPDATA");
            final Path base = (localAppData != null && !localAppData.isBlank())
                    ? Path.of(localAppData)
                    : Path.of(System.getProperty("user.home"));
            final Path dir = base.resolve("VocalAnalysisTool");
            Files.createDirectories(dir);
            return dir.toAbsolutePath();
        }

        // IntelliJ / dev run: keep everything relative to the project working directory
        final Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Files.createDirectories(dir);
        return dir;
    }


    /**
     * Extracts a resource from the jar into a stable, writable directory.
     *
     * @param resourcePath the path of the resource file.
     * @param outDir       the directory to copy to.
     * @param filename     the filename to write as.
     * @return returns the path of the extracted file.
     * @throws IOException Thrown if the input stream is null
     */
    private static Path extractResourceToDir(final String resourcePath,
                                             final Path outDir,
                                             final String filename) throws IOException {

        Files.createDirectories(outDir);
        final Path out = outDir.resolve(filename);

        try (final InputStream in = Main.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Resource not found");
            }
            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
        }

        return out;
    }

    /**
     * Adds the object to this property listener list.
     *
     * @param theListener The component that is being added to this listener.
     */
    public void addPropertyChangeListener(final PropertyChangeListener theListener) {
        myChanges.addPropertyChangeListener(theListener);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {

    }
}