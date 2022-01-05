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
    static int rows = 17;
    static int offset = 128;
    int x_offset = -250;//-WIDTH * 2 / 5;
    int y_offset = 490;//580; // 295;
    int z_offset = -290;//500;//-140;
    static double n = 0.0173;
    float weight = 0.8f; // 2.4f;
    float rot_factor = -1.51f;//.59
    static double[][] z_vals = new double[rows + 1][cols];
    static File fileIn = new File("src/09. Visitor.wav");
    long nanoTimeBetweenFFTs;
    String[] fileList;
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

    static ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public void keyPressed() {
        if (key == '=') {
            n += .00002;
        } else if (key == '-') {
            n -= .00002;
        }
        if (key == '=' || key == '-') System.out.println("Constant n = " + n);
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
                startMusic(0);
                state = state.PLAY;
            }
            if (state == State.PLAY) {
                for (int i = 0; i < rows; i++) {
                    System.out.println(Arrays.toString(z_vals[i]));
                }
            }
//            else if (state == State.PLAY) pauseMusic();
//            else if (state == State.PAUSE) resumeMusic();
        }


    }


    public void startMusic(int index) {
        isSynced = false;
        song = new File("src/music/" + fileList[index]);
        System.out.println(song.getAbsolutePath());
        QuiFFT quiFFT = null;
        try {
            quiFFT = new QuiFFT(song).windowSize((rows - 1) * cols * 2).windowOverlap(0.75);
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
                    delay += (long) (nextFrame.frameEndMs - nextFrame.frameStartMs) * -1 * .01;
                    System.out.println("LAGGING");
                    System.out.println("DELAY: " + delay);
                }
                executorService = Executors.newSingleThreadScheduledExecutor();
                executorService.scheduleAtFixedRate(Main::updateHeightMap, delay, nanoTimeBetweenFFTs, TimeUnit.NANOSECONDS);
                isSynced = true;
            }
        }
    }

    public void settings() {
        size(WIDTH, HEIGHT, P3D);
        // build list of files;
        File musicDir = new File("src/music");
        fileList = musicDir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".wav");
            }
        });
        System.out.println(fileList.length);
        System.out.println(Arrays.toString(z_vals));

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

    public static double uniqueCurve(double amp) {
        return Math.pow(amp, amp * n) - 1;
    }

    public static void updateHeightMap() {
        if (state == State.STOP){
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    z_vals[i][j] = (z_vals[i][j] > 0) ? z_vals[i][j] - 0.25 : 0;
                }
            }

        }
        else {
            FrequencyBin[] bins = nextFrame.bins;
            int j = offset;
            for (int i = 0; i < bins.length; ++i) {
                if (j >= 256 + offset) {
                    j = 1 + offset;
                }


                int row = floor(j / (rows)) % 17 + 1;
                int col = j % 16;
                double newVal = (bins[i].amplitude + 80);
                double oldVal = z_vals[row][col];
                if (i % 8 == 0) newVal = 0;
                else {
                    newVal = uniqueCurve(newVal);
                    if (i % 6 == 0) {

                    } else if (i % 5 == 0) {
                        newVal *= .8;
                    } else if (i % 4 == 0) {
                        newVal *= .6;
                    } else if (i % 2 == 0) {
                        newVal *= .5;
                    } else newVal *= .4;
                }
                if (newVal < oldVal) z_vals[row][col] = (oldVal > 0) ? oldVal - 0.25 : 0;
                else if (newVal < 4) z_vals[row][col] = 0;
                else z_vals[row][col] = newVal;

                j += 2;
            }

            if (fftStream.hasNext()) {
                nextFrame = fftStream.next();
            } else { // otherwise song has ended, so end program
                state = State.STOP;
            }
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
//        if (state == State.STOP) {
//            try {
//                executorService.shutdown();
//                executorService.awaitTermination(1,TimeUnit.NANOSECONDS);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
    }

    public static void main(String[] passedArgs) {
        String[] appletArgs = new String[]{"Main"};
        PApplet.main(appletArgs);
        System.out.println(cols * rows);
        System.out.println("cols: " + cols + " Rows: " + rows);
    }
}
