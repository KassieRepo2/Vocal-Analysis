package com.kass.vocalanalysistool.workflow;

import com.kass.vocalanalysistool.common.ChangeEvents;
import com.kass.vocalanalysistool.common.WorkflowResult;
import com.kass.vocalanalysistool.view.LoadingScreenController;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Runs the python script when passed the path
 *
 * @author Kassie Whitney
 * @version 12/25/2025
 */
public class PythonRunnerService implements PropertyChangeListener {

    /**
     * Property change listener object
     */
    private final PropertyChangeSupport myChanges = new PropertyChangeSupport(this);

    /**
     * Logger for verbose data.
     */
    private final Logger logger = Logger.getLogger(PythonRunnerService.class.getName());



    /**
     * Runs the vocal analysis python script
     *
     * @param thePath The path of the audio file
     * @throws IOException Thrown if the path to the audio file is invalid.
     */
    public void runScript(final String thePath) throws IOException {
        final FXMLLoader loadingScreenFXML = new FXMLLoader(getClass().getResource(
                "/com/kass/vocalanalysistool/gui/LoadingScreen.fxml"));
        final Scene loadingScreenScene = new Scene(loadingScreenFXML.load());
        final LoadingScreenController loadingScreenController = loadingScreenFXML.getController();
        loadingScreenController.addLoadingScreenAsListener(this);
        final Stage loadingScreenStage = new Stage();
        loadingScreenStage.initStyle(StageStyle.UNDECORATED);
        loadingScreenStage.setScene(loadingScreenScene);
        loadingScreenStage.getIcons().add(new Image(Objects.requireNonNull
                (getClass().getResourceAsStream
                        ("/com/kass/vocalanalysistool/icons/vocal_analysis_icon.png"))));
        loadingScreenStage.setResizable(false);
        loadingScreenStage.setAlwaysOnTop(true);
        loadingScreenStage.show();

        final Task<Void> task = getThreadedTask(thePath);

        final Thread worker = new Thread(task, "PythonRunner");
        worker.setDaemon(true);
        worker.start();

        task.setOnSucceeded(theEvent -> {
            loadingScreenStage.close();
            myChanges.firePropertyChange(ChangeEvents.WORKFLOW_RESULT.name(), null,
                    WorkflowResult.SUCCESS);
        });

        task.setOnFailed(theEvent -> {
            loadingScreenStage.close();
            myChanges.firePropertyChange(ChangeEvents.WORKFLOW_RESULT.name(), null,
                    WorkflowResult.FAILED);
//            throw new IllegalArgumentException("The run time failed to process.");
        });


    }

    /**
     * Runs the python script on a separate thread.
     *
     * @param thePath the path of the python script.
     * @return a task object of the thread.
     */
    private Task<Void> getThreadedTask(final String thePath) {
        return new Task<>() {
            @Override
            protected Void call() {
                runPythonScript(thePath); // must throw on failure
                return null;
            }
        };
    }

    /**
     * Runs the python script to extract praat data from the chosen audio file.
     *
     * @param theFilePath the file path of the audio file.
     */
    private void runPythonScript(final String theFilePath) {
        try {
            final Path appDir = getAppDir(); //The directory of the program install location
            final Path dataDir = getDataDir(); // Writable directory for venv + extracted resources

            logger.info("Resolved appDir: " + appDir);
            logger.info("Resolved dataDir: " + dataDir);

            // Extract resources to a stable, writable location (NOT temp, NOT install dir)
            final Path pythonScript = extractResourceToDir(
                    "/VocalAnalysisToolKit/Vocal_Analysis_Script.py",
                    dataDir,
                    "Vocal_Analysis_Script.py"
            );
            final Path setupBat = extractResourceToDir(
                    "/pythonInstall.bat",
                    dataDir,
                    "pythonInstall.bat"
            );
            final Path requirements = extractResourceToDir(
                    "/VocalAnalysisToolKit/requirements.txt",
                    dataDir,
                    "requirements.txt"
            );

            final Path modelFile = extractResourceToDir(
                    "/VocalAnalysisToolKit/gender_model.joblib",
                    dataDir,
                    "gender_model.joblib"
            );

            final Path venvPy = dataDir.resolve(".venv").resolve("Scripts").resolve("python.exe");


            myChanges.firePropertyChange(ChangeEvents.UPDATE_PROGRESS.toString(),
                    "Installing environment updates if needed...",
                    (double) 32 / 100);

            // 2) Resolve venv python; do not silently fall back
            if (!Files.exists(venvPy)) {
                throw new IllegalStateException("Venv python not found at " + venvPy + ". Ensure setup ran in " + dataDir);
            }
            final String pythonExe = venvPy.toString();

            // Helper to run a short python/pip command and log all output
            Function<String[], Integer> run = (args) -> {
                try {
                    Process p = getProcess(new ProcessBuilder(args), dataDir);
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                        String s;
                        while ((s = br.readLine()) != null) logger.info("[pip] " + s);
                    }
                    return p.waitFor();
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Subprocess failed: " + String.join(" ", args), e);
                    return -1;
                }
            };

            myChanges.firePropertyChange(ChangeEvents.UPDATE_PROGRESS.toString(), "Parsing " +
                            "dependency requirements...",
                    (double) 48 / 100);

            // 3) Ensure matplotlib is installed in the venv
            int code;
            if (Files.exists(requirements)) {
                logger.info("Installing requirements from: " + requirements);
                myChanges.firePropertyChange(ChangeEvents.UPDATE_PROGRESS.toString(),
                        "Installing dependency requirements...", (double) 55 / 100);
                code = run.apply(new String[]{pythonExe, "-m", "pip", "install", "-r", requirements.toString()});
                if (code != 0)
                    throw new IllegalStateException("pip install -r failed with code " + code);
            } else {
                // Minimal guarantee
                logger.info("requirements.txt not found in " + dataDir + " — installing matplotlib explicitly.");
                myChanges.firePropertyChange(ChangeEvents.UPDATE_PROGRESS.toString(),
                        "Installing basic requirements...", (double) 55 / 100);
                code = run.apply(new String[]{pythonExe, "-m", "pip", "install", "matplotlib"});
                if (code != 0)
                    throw new IllegalStateException("pip install matplotlib failed with code " + code);
            }

            myChanges.firePropertyChange(ChangeEvents.UPDATE_PROGRESS.toString(),
                    "Importing dependencies...", (double) 64 / 100);
            // 4) Probe: show interpreter & matplotlib version (fail fast if missing)
            code = run.apply(new String[]{pythonExe, "-c",
                    "import sys; print('[PyProbe] exe:', sys.executable); " +
                            "import matplotlib; print('[PyProbe] matplotlib version:', matplotlib.__version__)"
            });
            if (code != 0)
                throw new IllegalStateException("Probe failed; matplotlib not importable.");

            myChanges.firePropertyChange(ChangeEvents.UPDATE_PROGRESS.toString(), "Analyzing" +
                            " vocal recording...",
                    (double) 95 / 100);

            // 5) Runs the python script
            final Process process = getProcess(new ProcessBuilder(pythonExe,
                    pythonScript.toString(), theFilePath, modelFile.toString()), dataDir);
            try (final BufferedReader reader =
                         new BufferedReader(new InputStreamReader(process.getInputStream()))) {

                String line;
                while ((line = reader.readLine()) != null) {

                    logger.info("[Python] " + line);

                    if (line.contains("No valid frames after filtering; skipping file")) {
                        myChanges.firePropertyChange(ChangeEvents.WORKFLOW_RESULT.name()
                                , "The audio recorder did not detect any valid acoustics" +
                                        ". Please try again!", WorkflowResult.INVALID);
                    }
                }

            }
            int exit = process.waitFor();
            if (exit != 0) {
                logger.severe("Python script exited with code " + exit);
                myChanges.firePropertyChange(ChangeEvents.WORKFLOW_RESULT.name(), null,
                        WorkflowResult.FAILED);
                throw new IllegalStateException("The python script could not write to file!");
            }

        } catch (final IOException | InterruptedException theEvent) {
            logger.log(Level.SEVERE, "Failed to run Python script", theEvent);
            Thread.currentThread().interrupt();
        }

        myChanges.firePropertyChange(ChangeEvents.UPDATE_PROGRESS.name(), "Completed!",
                (double) 1);

    }

    /**
     * Helper method to condense code. Gets the process object based on the process builder.
     *
     * @param theCommandArgs the arguments of which process builder is executing
     * @param theAppDir      The path of the application directory
     * @return Returns a process object to execute the commands.
     * @throws IOException Thrown if the path is invalid.
     */
    private static Process getProcess(final ProcessBuilder theCommandArgs,
                                      final Path theAppDir) throws IOException {

        theCommandArgs.directory(theAppDir.toFile());
        theCommandArgs.redirectErrorStream(true);

        final Map<String, String> env = theCommandArgs.environment();
        env.remove("PYTHONHOME");
        env.remove("PYTHONPATH");
        env.put("MPLBACKEND", "Agg");
        env.putIfAbsent("PYTHONIOENCODING", "utf-8");


        return theCommandArgs.start();
    }


    /**
     * Gets the temporary directory of the python script.
     *
     * @return the path of the directory that the python script is installed on.
     */
    private Path getAppDir() {
        final String jpackageAppPath = System.getProperty("jpackage.app-path");
        if (jpackageAppPath != null && !jpackageAppPath.isBlank()) {
            return Path.of(jpackageAppPath).toAbsolutePath().getParent();
        }
        return Path.of(System.getProperty("user.dir")).toAbsolutePath();
    }

    /**
     * Gets a writable directory for the python environment.
     *
     * @return the path of the directory that the python environment is installed on.
     * @throws IOException thrown if the directory can not be created.
     */
    private Path getDataDir() throws IOException {

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
    private Path extractResourceToDir(final String resourcePath,
                                      final Path outDir,
                                      final String filename) throws IOException {

        Files.createDirectories(outDir);
        final Path out = outDir.resolve(filename);

        try (final InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Resource not found");
            }
            Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
        }

        return out;
    }

    /**
     * Adds component to this property change listener list
     *
     * @param theListener the component
     */
    public void addPropertyChangeListener(final PropertyChangeListener theListener) {
        myChanges.addPropertyChangeListener(theListener);
    }

    /**
     * Removes the component from this property change listener list
     *
     * @param theListener the component
     */
    public void removePropertyChangeListener(final PropertyChangeListener theListener) {
        myChanges.removePropertyChangeListener(theListener);
    }

    @Override
    public void propertyChange(final PropertyChangeEvent theEvent) {

    }
}
