import org.quifft.QuiFFT;
import org.quifft.output.FFTFrame;
import org.quifft.output.FFTStream;
import org.quifft.output.FrequencyBin;
import processing.core.PApplet;
import processing.event.MouseEvent;

import javax.sound.sampled.*;
import java.awt.event.MouseWheelEvent;
import java.io.*;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main extends PApplet {
    static int base = 70;
    static int WIDTH = 700, HEIGHT = 700;
    static int cols = 16;
    static int rows = 16;
    float theta = 0;
    int r = 0, g = 0, b = 0;
    int x_offset = -250;//-WIDTH * 2 / 5;
    int y_offset = 490;//580; // 295;
    int z_offset = -290;//500;//-140;
    float weight = 0.8f; // 2.4f;
    float rot_factor = -1.51f;//.59
    static double[][] z_vals = new double[rows][cols];
    long nanoTimeBetweenFFTs;
//    String[] fileList;
    private File song;
    // FFTStream used to compute FFT frames
    private static FFTStream fftStream;
    boolean isSynced = false;

    // Next frame to graph
    private static FFTFrame nextFrame;
    Clip audioClip = null;

    enum State {PLAY, PAUSE, STOP}

    ;
    static State state = State.STOP;

    ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public void keyPressed() {
        if (key == '=') {
            weight += .1f;
        } else if (key == '-') {
            weight -= .1f;
        }
        if (key == 'k') {
            x_offset += 15;
        } else if (key == 'K') {
            x_offset -= 15;
        }
        if (key == 'l') {
            y_offset += 15;
        } else if (key == 'L') {
            y_offset -= 15;
        }
        if (key == ';') {
            z_offset += 15;
        } else if (key == ':') {
            z_offset -= 15;
        }
        if (key == 'o') {
            rot_factor -= .1f;
        } else if (key == 'p') {
            rot_factor += .01f;
        }


        if (keyCode == ENTER || keyCode == RETURN) {
            System.out.println(
                    "x: " + x_offset +
                            ", y: " + y_offset +
                            ", z: " + z_offset);
            System.out.println("rotFact: " + rot_factor);
            System.out.println("weight: " + weight);
        }
        if (key == ' ') {
            if (state == State.STOP) {
                startMusic();
                state = state.PLAY;
            }
//            else if (state == State.PLAY) pauseMusic();
//            else if (state == State.PAUSE) resumeMusic();
        }


    }


    public void startMusic() {
        isSynced = false;
        song = new File("src/music/penguinmusic-modern-chillout-12641.wav");
        QuiFFT quiFFT = null;
        try {
            quiFFT = new QuiFFT(song).windowSize(rows * cols * 2).windowOverlap(0.75);
        } catch (IOException | UnsupportedAudioFileException e) {
            e.printStackTrace();
        }

        fftStream = quiFFT.fftStream();
        System.out.println(fftStream);
        nextFrame = fftStream.next();

        if (audioClip != null) {
            audioClip.close();
        }
        // Set up audio
        try {
            InputStream in = new FileInputStream(song);
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(song);
            AudioFormat format = audioStream.getFormat();
            DataLine.Info info = new DataLine.Info(Clip.class, format);
            audioClip = (Clip) AudioSystem.getLine(info);
            audioClip.open(audioStream);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (UnsupportedAudioFileException e) {
            e.printStackTrace();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }

        double msBetweenFFTs = fftStream.windowDurationMs * (1 - fftStream.fftParameters.windowOverlap);
        nanoTimeBetweenFFTs = Math.round(msBetweenFFTs * Math.pow(10, 6));
        audioClip.setMicrosecondPosition((long) nextFrame.frameEndMs);
        audioClip.start();

        while (!isSynced) {
            if (audioClip.isRunning()) {
                long delay = 0;
                if (nextFrame.frameEndMs > audioClip.getMicrosecondPosition() - audioClip.getFrameLength()) {
                    delay = (long) (nextFrame.frameEndMs - audioClip.getMicrosecondPosition()) * -1;
//                    delay -= (long) (nextFrame.frameEndMs - nextFrame.frameStartMs) * .001;
                    System.out.println("LAGGING");
                }
                executorService = Executors.newSingleThreadScheduledExecutor();
                executorService.scheduleAtFixedRate(Main::updateHeightMap, delay, nanoTimeBetweenFFTs, TimeUnit.NANOSECONDS);
                isSynced = true;
            }
        }
    }

    public void settings() {
        size(WIDTH, HEIGHT, P3D);
    }

    public static double easeInCirc(double prog) {
        double x = prog / 80;
        return (double) (1 - sqrt((float) (1 - Math.pow(x, 2))));
    }

    public static double easeInOutExpo(double prog) {
        double x = prog / 80;
        return x == 0
                ? 0
                : x == 1
                ? 1
                : x < 0.5 ? Math.pow(2, 20 * x - 10) / 2
                : (2 - Math.pow(2, -20 * x + 10)) / 2;
    }

    public static double easeInCubic(double prog) {
        double x = prog / 80;
        return x < 0.5 ? 4 * x * x * x : 1 - Math.pow(-2 * x + 2, 3) / 2;
    }

    public static double easeInQuart(double prog) {
        double x = prog / 70;
        if (x > 1) x = 1;
        return x * x * x * x;
    }

    public static void updateHeightMap() {
        FrequencyBin[] bins = nextFrame.bins;
        int j = 0;
        for (int i = 0; i < bins.length; ++i) {
            if (j >= 256) {
                j += 1;
                j = j % 16;
            }


            int row = floor(j / rows) % 16;
            int col = j % 16;
            double newVal = (bins[i].amplitude + 80);
//            System.out.println(newVal);
            newVal = easeInQuart(newVal) * 150;
            double oldVal = z_vals[row][col];
            if (newVal < oldVal) z_vals[row][col] = oldVal - 0.55;
            else if (i % 6 == 0) {
                z_vals[row][col] = newVal;
            } else if (i % 5 == 0) {
                z_vals[row][col] = newVal * .4;
            } else if (i % 4 == 0) {
                z_vals[row][col] = newVal * .6;
            } else if (i % 2 == 0) {
                z_vals[row][col] = newVal * .8;
            } else z_vals[row][col] = newVal * .3;

            j += 2;
        }

        if (fftStream.hasNext()) {
            nextFrame = fftStream.next();
        } else { // otherwise song has ended, so end program
            System.exit(0);
        }
    }

    public void draw() {
        background(0);
        stroke(255);
        fill(0);
        strokeWeight(weight);
        translate(x_offset, y_offset, z_offset);
        rotateX((float) (rot_factor * PI));
        mouseWheel();
        float height = WIDTH / cols * sqrt(3) / 2;
        boolean even;
        for (int i = 0; i < 1; i++) {
            for (int y = 0; y <= rows; y++) {
                int offset;
                if (y % 2 == 0) {
                    offset = 0; // to line up the triangles in a pattern that forms hexagons ygm ;)
                    even = true;
                } else {
                    offset = base / 2;
                    even = false;
                }

                int color = (y + 1) * 15;
                stroke(color);
                beginShape(TRIANGLE_STRIP);
                for (int x = 0; x < cols; x++) {
                    for (int v = 0; v < 2; v++) {
                        double z = 0;
                        if (even && v == 1)
                            z = z_vals[y % (rows - 1)][x % (cols - 1)];

                        if (even && v == 0)
                            z = z_vals[(rows - 1 + y) % (rows)][(cols + x - 2) % (cols - 1)];
                        if (!even && v == 1)
                            z = z_vals[y][x % (cols - 1)];
                        if (!even && v == 0) {
                            z = z_vals[y - 1][x % (cols - 1)];
                        }
                        vertex(i * (base * cols - base) + (x * base + v * base / 2) + offset, height * y + v * height, (float) z);
                    }
                }
                endShape();
            }
        }
    }

    public static void main(String[] passedArgs) {
        String[] appletArgs = new String[]{"Main"};
        PApplet.main(appletArgs);
        System.out.println(cols * rows);
        System.out.println("cols: " + cols + " Rows: " + rows);
    }
}
