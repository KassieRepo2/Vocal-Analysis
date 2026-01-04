package com.kass.vocalanalysistool.view;

import com.kass.vocalanalysistool.common.ChangeEvents;
import com.kass.vocalanalysistool.common.StageNames;
import com.kass.vocalanalysistool.model.UserSampleDatabase;
import com.kass.vocalanalysistool.view.util.StageFactory;
import com.kass.vocalanalysistool.view.util.StageRegistry;
import com.kass.vocalanalysistool.workflow.OpenAudioDataScene;
import com.kass.vocalanalysistool.workflow.PythonRunnerService;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class AudioDataController implements PropertyChangeListener {

    /**
     * The formant database object.
     */
    private final UserSampleDatabase myDataBase = new UserSampleDatabase(false);

    /**
     * The analyze recording button.
     */
    @FXML
    public Button myAnalyzeRecordingBtn;

    /**
     * The scatter plot container
     */
    @FXML
    public StackPane myScatterPlotContainer;

    /**
     * The scatterplot image container
     */
    @FXML
    private ImageView myScatterPlotImage;

    /**
     * The label inside the scrollable container
     */
    @FXML
    private Label myInformationLabel;

    /**
     * This stage.
     */
    private Stage myStage;

    /**
     * The python runner
     */

    private final PythonRunnerService myPythonScript = new PythonRunnerService();

    /**
     * The zoom lens
     */
    private ImageView lensView;

    /**
     * The lens diameter
     */
    private static final double LENS_DIAMETER = 100;

    /**
     * The zoom magnification
     */
    private static final double ZOOM = 1.5;

    /**
     * Initializes the scene prior to showcasing it.
     */
    @FXML
    private void initialize() {

        myScatterPlotImage.setImage(new Image(new ByteArrayInputStream(myDataBase.getScatterPlot())));

        setUpZoomView(myScatterPlotImage);


        myPythonScript.addPropertyChangeListener(this);

        myInformationLabel.setText(
                """    
                        This panel provides detailed explanations of the acoustic features shown
                        in your analysis charts. You can scroll and reference sections as needed.
                        --------------------------------------------------------------------------
                        
                        * Formant [F1–F4] are resonant frequency ranges shaped by the vocal tract.
                            Vowels and vocal-tract configuration (tongue, lips, jaw) strongly influence
                            formant placement.
                        
                        * Typical ranges for cisgender women and men are listed below, but note that
                            these values overlap and vary by age, dialect, context, and articulation.
                        
                        * Formant data can give you a good idea about how you are forming your
                            vowels, tongue placements, lip placements, jaw placements,
                            resonance locations, as well as how you are articulating your words.
                        
                        * Gender perception of your voice can be predicted based on pitch,
                            formant data, your breathiness index, and intonation data.
                        
                        ---------------------------------- Pitch ----------------------------------
                        
                        ** Pitch [F0] Cis Women [165 - 300] Hz | Cis Men [85 - 155] Hz:
                            - Pitch does play a major role in how a person may gender your voice.
                               However, pitch alone can not be reliably used to determine the
                               gender perception of your voice.
                        
                            - We must take into consideration of other vocal characteristics that
                               make up our vocal profile, such as, intonation, breathiness,
                               and resonance.
                        
                        -------------------------------- Resonance --------------------------------
                        
                        ** F1: Cis Women [300 - 900] Hz | Cis Men [250 - 750] Hz:
                            - Influenced by Tongue Height (Mouth Openness).
                        
                            - High tongue position (more closed mouth) -> Low F1:
                                + Vowels like /i/ ("ee" in beet), /u/ ("oo" in boot)
                        
                            - Low tongue position (more open mouth) -> High F1:
                                + Vowels like /a/ ("ah" in father), /æ/ ("a" in cat)
                        
                            - Inversely related to tongue height:
                                + To increase F1: lower the tongue (open the mouth more).
                                + To decrease F1: raise the tongue (close the mouth more).
                        
                        
                        ** F2: Cis Women [1450- 3200] Hz | Cis Men [725 - 1900] Hz:
                            - Influenced by tongue frontness.
                        
                            - Tongue forward -> High F2:
                                + Vowels like /i/ ("ee"), /e/ ("ay")
                        
                            - Tongue back -> Low F2:
                                + Vowels like /u/ ("oo"), /o/ ("oh"), /a/ ("ah")
                        
                            - Directly related to tongue frontness.
                                + To increase F2: keep your tongue in the frontmost part of your mouth.
                                + To decrease F2: keep your tongue in the backmost part of your mouth.
                        
                        
                        ** F3: Cis Women [2600 - 3600] Hz | Cis Men [1600 - 2800] Hz:
                            - Influenced by Lip Shape & Resonance Characteristics.
                        
                            - Influenced by lip rounding -> Lowers F3:
                                + Vowels like (/r/ ("rr" in red and run) and /u/ ("oo" in boot))
                                + Constrictions in the oral cavity.
                        
                            - Less dramatically tied to vowels than F1/F2, but:
                                + /i/ ("ee") tend to have a higher F3
                                + /u/ ("oo") and /r/ ("rr") tend to have a lower F3
                        
                            - Influenced indirectly by larynx position, mouth shape, and tongue
                              placement.
                                + To increase F3:
                                    - Spread your lips (think gentle smile) when speaking.
                                    - Keep your tongue as flat as possible, don't bunch up your tongue.
                                    - Maintain a slightly elevated larynx.
                                    - The tongue should be rubbing the back of your two front teeth.
                                    - "The tip of the tongue the teeth the lips".
                                + To decrease F3:
                                    - Round your lips more (think puckering your lips) when speaking.
                                    - Bunch up your tongue (think trilling your r's but not actually trilling).
                                    - Back the tongue slightly towards your throat.
                                    - Lower your larynx.
                        
                        ** F4: Cis Women [3500 - 5000] Hz | Cis Men [2800 - 3700] Hz:
                            - Speaker & Timbre Characteristics.
                        
                            - Not directly vowel-diagnostic.
                        
                            - Affected by:
                                + Vocal tract length
                                + Voice quality
                                + Subtle articulation cues
                        
                            - Used more in vocal profiling (e.g., gender, vocal effort), not vowel
                                identification.
                        
                        NOTE:
                              Your data is compared against a Machine Learning model in order to
                              generate a high-confidence prediction of the gender perception of your
                              vocal sample. Your data is yours and yours alone. Your data is not
                              shared or used as a training dataset for the Machine Learning Model.
                              With that said, if you would like to share your audio recording with
                              the developer in order to further improve the Machine Learning Model,
                              please contact the developer directly. Contact information can be found
                              in the about section. Thank you!
                        
                        
                        """
        );

    }

    /**
     * Sets up the magnifying cursor for the formant graph.
     *
     * @param imageView The formant graph.
     */
    private void setUpZoomView(final ImageView imageView) {
        // Create lens overlay
        lensView = new ImageView(imageView.getImage());
        lensView.setFitWidth(LENS_DIAMETER);
        lensView.setFitHeight(LENS_DIAMETER);
        lensView.setPreserveRatio(false);

        final Circle clip = new Circle(LENS_DIAMETER / 2.0);
        clip.setCenterX(LENS_DIAMETER / 2.0);
        clip.setCenterY(LENS_DIAMETER / 2.0);
        lensView.setClip(clip);

        lensView.setVisible(false);
        lensView.setMouseTransparent(true);

        myScatterPlotContainer.getChildren().add(lensView);

        imageView.setOnMouseMoved(e -> {

            lensView.setVisible(true);

            lensView.setTranslateX(e.getX() - imageView.getBoundsInLocal().getWidth() / 2.0);
            lensView.setTranslateY(e.getY() - imageView.getBoundsInLocal().getHeight() / 2.0);

            final Image img = imageView.getImage();
            final double viewW = imageView.getBoundsInLocal().getWidth();
            final double viewH = imageView.getBoundsInLocal().getHeight();

            final double mx = e.getX();
            final double my = e.getY();

            final double px = (mx / viewW) * img.getWidth();
            final double py = (my / viewH) * img.getHeight();

            final double vw = img.getWidth() / (ZOOM * (viewW / LENS_DIAMETER));
            final double vh = img.getHeight() / (ZOOM * (viewH / LENS_DIAMETER));

            final double vx = clamp(px - vw / 2.0, 0, img.getWidth() - vw);
            final double vy = clamp(py - vh / 2.0, 0, img.getHeight() - vh);

            lensView.setViewport(new Rectangle2D(vx, vy, vw, vh));
        });

        imageView.setOnMouseExited(e ->{
            lensView.setVisible(false);

        });
    }

    /**
     * Sets the boundary for the view port to keep it only on the image.
     *
     * @param v the current mouse position converted into image coordinates.
     * @param min The smallest boundary that the view port is allowed in
     * @param max The greatest value that the view port is allowed in
     * @return The greatest smallest value that is set as a boundary for the viewport.
     */
    private static double clamp(final double v, final double min, final double max) {
        return Math.max(min, Math.min(max, v));
    }

    /**
     * Opens the analysis scene where it breaks down formant data and outputs gender perception
     */
    @FXML
    private void handleAnalyzeNewRecordingButton() throws IOException {
        myStage = (Stage) myInformationLabel.getScene().getWindow();

        final FileChooser fileChooser = new FileChooser();

        fileChooser.setTitle("Select Audio File");

        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("WAV Audio Files", "*.wav"),
                new FileChooser.ExtensionFilter("AIFF Audio Files", "*.aiff"));

        final File file = fileChooser.showOpenDialog(myStage);

        if (file != null) {
            final String path = file.getAbsolutePath();

            myPythonScript.runScript(path);

        }
    }

    /**
     * Opens the embedded recording tool.
     */
    @FXML
    private void handleRecordNewSample() {

        StageRegistry.show(StageNames.VOICE_RECORDING.name(), () ->
                StageFactory.buildStage(this,
                        "AudioRecording.fxml",
                        "Voice Recorder",
                        false)
        );
    }


    /**
     * Opens the UserGuide scene.
     */
    @FXML
    private void handleMyUserGuideMenuItem() {

    }

    /**
     * Opens the about scene
     */
    @FXML
    private void handleMyAboutVocalAnalysisMenuItem() {

    }

    /**
     * Closes the program.
     */
    @FXML
    private void handleCloseProgram() {
        myStage = (Stage) myInformationLabel.getScene().getWindow();
        myStage.close();
        Platform.exit();
    }


    @Override
    public void propertyChange(final PropertyChangeEvent theEvent) {

        if (theEvent.getPropertyName().equals(ChangeEvents.WORKFLOW_RESULT.name())) {
            Platform.runLater(() -> {
                OpenAudioDataScene.openAnalysis(theEvent);
                myPythonScript.removePropertyChangeListener(this);

            });
        }
    }
}
