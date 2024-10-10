import org.json.JSONException;
import org.json.JSONObject;

import javax.swing.*;
import java.io.*;
import java.net.Socket;
import java.sql.SQLOutput;
import java.util.Arrays;
import java.util.Properties;

public class WireGuardVPNManager {

    private static String WIREGUARD_CMD;
    private static String CONFIG_PATH;
    private static String GPS_IP;
    private static int GPS_PORT;

    private static double MIN_LAT;
    private static double MAX_LAT;
    private static double MIN_LON;
    private static double MAX_LON;

    static {
        loadConfiguration();
    }

    private static void loadConfiguration() {
        Properties properties = new Properties();
        try (FileInputStream fis = new FileInputStream("VPNmanager.properties")) {
            properties.load(fis);
            WIREGUARD_CMD = properties.getProperty("WIREGUARD_CMD");
            CONFIG_PATH = properties.getProperty("CONFIG_PATH");
            GPS_IP = properties.getProperty("GPS_IP");
            GPS_PORT = Integer.parseInt(properties.getProperty("GPS_PORT"));
            MIN_LAT = Double.parseDouble(properties.getProperty("MIN_LAT"));
            MAX_LAT = Double.parseDouble(properties.getProperty("MAX_LAT"));
            MIN_LON = Double.parseDouble(properties.getProperty("MIN_LON"));
            MAX_LON = Double.parseDouble(properties.getProperty("MAX_LON"));
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Fout bij het laden van de configuratie: " + e.getMessage());
            System.exit(1);
        }
    }

    public static boolean startWireGuardVPN() {
        System.out.println("Probeer WireGuard VPN te starten...");
        try {
            File configFile = new File(CONFIG_PATH);
            if (!configFile.exists()) {
                System.out.println("Configuratiebestand niet gevonden: " + CONFIG_PATH);
                JOptionPane.showMessageDialog(null, "Configuratiebestand niet gevonden: " + CONFIG_PATH);
                return false;
            }

            ProcessBuilder builder = new ProcessBuilder(WIREGUARD_CMD, "up", CONFIG_PATH);
            Process process = builder.start();
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            StringBuilder errorOutput = new StringBuilder();
            String errorLine;
            while ((errorLine = errorReader.readLine()) != null) {
                errorOutput.append(errorLine).append("\n");
            }
            process.waitFor();

            if (process.exitValue() == 0) {
                System.out.println("VPN succesvol gestart.");
                long pingTime = pingHost("8.8.8.8");

                if (pingTime >= 0) {
                    JOptionPane.showMessageDialog(null, "WireGuard VPN is succesvol verbonden. Ping tijd: " + pingTime + " ms.");
                    return true;
                } else {
                    JOptionPane.showMessageDialog(null, "VPN is verbonden, maar geen succesvolle ping ontvangen.");
                    return false;
                }
            } else {
                System.out.println("Fout bij het verbinden van WireGuard VPN\n" + errorOutput.toString());
                JOptionPane.showMessageDialog(null, "Er is een fout opgetreden bij het verbinden van WireGuard VPN\n" + errorOutput.toString());
                return false;
            }

        } catch (IOException | InterruptedException e) {
            System.out.println("Fout bij het verbinden met de VPN: " + e.getMessage());
            JOptionPane.showMessageDialog(null, "Fout bij het verbinden met de VPN: " + e.getMessage());
            return false;
        }
    }

    public static boolean stopWireGuardVPN() {
        System.out.println("Probeer WireGuard VPN te stoppen...");
        try {
            ProcessBuilder builder = new ProcessBuilder(WIREGUARD_CMD, "down", CONFIG_PATH);
            Process process = builder.start();
            process.waitFor();

            if (process.exitValue() == 0) {
                System.out.println("WireGuard VPN is succesvol gestopt.");
                JOptionPane.showMessageDialog(null, "WireGuard VPN is succesvol gestopt.");
                return true;
            } else {
                System.out.println("Er is een fout opgetreden bij het stoppen van WireGuard VPN.");
                JOptionPane.showMessageDialog(null, "Er is een fout opgetreden bij het stoppen van WireGuard VPN.");
                return false;
            }

        } catch (IOException | InterruptedException e) {
            System.out.println("Fout bij het verbreken van de VPN: " + e.getMessage());
            JOptionPane.showMessageDialog(null, "Fout bij het verbreken van de VPN: " + e.getMessage());
            return false;
        }
    }

    public static void main(String[] args) {
        while (true) {
            // Toon een dialoogvenster met de vier opties
            Object[] options = {"Start VPN", "Stop VPN", "Test Snelheid", "Stuur Data", "Exit"};
            int choice = JOptionPane.showOptionDialog(
                    null,
                    "Kies een optie:",
                    "WireGuard VPN Manager",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[0]
            );

            // Voer de actie uit op basis van de keuze
            if (choice == 0) { // Start VPN
                if (isOpLocatie()) {
                    startWireGuardVPN();
                } else {
                    JOptionPane.showMessageDialog(null, "Locatie niet binnen het toegestane bereik. VPN niet gestart.");
                }
            } else if (choice == 1) { // Stop VPN
                stopWireGuardVPN();
            } else if (choice == 2) { // Test Snelheid
                testInternetSpeed();
            } else if (choice == 3) { // Stuur Data
                stuurData();
            } else if (choice == 4 || choice == JOptionPane.CLOSED_OPTION) { // Sluit het programma
                JOptionPane.showMessageDialog(null, "Programma wordt afgesloten.");
                System.exit(0);
            }
        }
    }

    // Controleer of de huidige GPS-locatie binnen het bereik ligt
    private static boolean isOpLocatie() {
        System.out.println("Locatie opvragen...\n");
        try (Socket socket = new Socket(GPS_IP, GPS_PORT);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.print("Readline: ");
                System.out.println(line);
                if (line.startsWith("$GPGGA") || line.startsWith("$GPRMC")) {
                    String[] parts = line.split(",");
                    int latIndex = line.startsWith("$GPGGA") ? 2 : 3;
                    int lonIndex = line.startsWith("$GPGGA") ? 4 : 5;

                    String latitude = conversieDecimaalGetal(parts[latIndex], parts[latIndex + 1]);
                    String longitude = conversieDecimaalGetal(parts[lonIndex], parts[lonIndex + 1]);

                    if (latitude != null && longitude != null) {
                        return printCoordinaten(latitude, longitude);
                    }
                }
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Fout bij het ophalen van GPS-gegevens: " + e.getMessage());
        }
        return false;
    }

    private static void stuurData() {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder("java", "-jar", "/home/cfns/producerRMQ/producerRMQ.jar");
            process = builder.start();

            // Read the output from the process
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            System.out.println("Output from ProducerRBMQ:");
            while ((line = reader.readLine()) != null) {
                System.out.println(line); // Print to terminal
            }

            // Optionally, read the error stream
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            StringBuilder errorOutput = new StringBuilder();
            while ((line = errorReader.readLine()) != null) {
                System.err.println(line); // Print error to terminal
                errorOutput.append(line).append("\n");
            }

            // Wait for the process to complete
            int exitCode = process.waitFor(); // This will block until the process is finished

            // Check exit code after the process has finished
            if (exitCode == 0) {
                JOptionPane.showMessageDialog(null, "Data succesvol verzonden.");
            } else {
                JOptionPane.showMessageDialog(null, "Er is een fout opgetreden bij het verzenden van de data.\n" + errorOutput.toString());
            }
        } catch (IOException | InterruptedException e) {
            JOptionPane.showMessageDialog(null, "Fout bij het uitvoeren van ProducerRBMQ: " + e.getMessage());
        } finally {
            if (process != null) {
                process.destroy(); // Clean up the process if it was started
            }
        }
    }



    private static boolean printCoordinaten(String latitude, String longitude) {
        double lat = Double.parseDouble(latitude.replace(',', '.'));
        double lon = Double.parseDouble(longitude.replace(',', '.'));

        boolean isOpLocatie = (lat >= MIN_LAT) && (lat <= MAX_LAT) && (lon >= MIN_LON) && (lon <= MAX_LON);

        if (isOpLocatie) {
            JOptionPane.showMessageDialog(null, String.format("Coördinaten: %s, %s. Je bent in het toegestane gebied.", latitude, longitude));
        } else {
            // Notificatie als de locatie buiten de grenzen valt
            JOptionPane.showMessageDialog(null, String.format("Coördinaten: %s, %s. Je bent buiten het toegestane gebied.", latitude, longitude),
                    "Locatie buiten bereik", JOptionPane.WARNING_MESSAGE);
        }

        return isOpLocatie;
    }

    private static String conversieDecimaalGetal(String value, String direction) {
        if (value == null || value.isEmpty() || direction == null) return null;

        boolean isLatitude = direction.equals("N") || direction.equals("S");
        int degreeLength = isLatitude ? 2 : 3;

        double degrees = Double.parseDouble(value.substring(0, degreeLength));
        double minutes = Double.parseDouble(value.substring(degreeLength));
        double decimalDegrees = degrees + (minutes / 60);

        if (direction.equals("S") || direction.equals("W")) {
            decimalDegrees = -decimalDegrees;
        }

        return String.format("%.6f", decimalDegrees);
    }

    // Methode om een host te pingen
    private static long pingHost(String host) {
        try {
            ProcessBuilder builder = new ProcessBuilder("ping", "-c", "1", host);
            Process process = builder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("time=")) {
                    // Haal de pingtijd op uit de regel met "time="
                    String[] parts = line.split("time=");
                    String pingTime = parts[1].split(" ")[0];
                    return (long) Double.parseDouble(pingTime); // Retourneer de pingtijd in milliseconden
                }
            }
            process.waitFor();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Fout bij het pingen van de host: " + e.getMessage());
        }
        return -1; // Retourneer -1 als de ping niet succesvol is
    }

    private static void testInternetSpeed() {
        int numTests = 5; // Number of tests
        int interval = 10000; // Interval in milliseconds
        double[] transferSpeeds = new double[numTests]; // Array to store transfer speeds

        // Create a progress dialog
        JProgressBar progressBar = new JProgressBar(0, numTests);
        progressBar.setStringPainted(true);
        JFrame frame = new JFrame("Speed Test Progress");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(progressBar);
        frame.setSize(300, 100);
        frame.setVisible(true);

        try {
            for (int i = 0; i < numTests; i++) {
                ProcessBuilder processBuilder = new ProcessBuilder("timeout", "5", "iperf3", "-c", "192.168.20.63", "-J", "-t", "1");
                Process process = processBuilder.start();

                // Read the output
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                StringBuilder output = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }

                process.waitFor();

                // Controleer de output
                if (output.length() == 0) {
                    System.err.println("Geen uitvoer van iperf3. Controleer de verbinding.");
                    return; // Of geef een foutmelding en ga verder
                }

                // Print de output om te inspecteren
                //System.out.println("Output van iperf3:\n" + output.toString());

                // Parse the JSON output
                JSONObject jsonObject;
                try {
                    jsonObject = new JSONObject(output.toString());
                } catch (JSONException e) {
                    System.err.println("Fout bij het parseren van JSON: " + e.getMessage());
                    return;
                }

                // Controleer op de aanwezigheid van de vereiste velden
                if (!jsonObject.has("end") || !jsonObject.getJSONObject("end").has("streams")) {
                    System.err.println("Ongeldige JSON-structuur. Geen 'streams' gevonden.");
                    return; // Of geef een foutmelding en ga verder
                }

                // Extract transfer speed from "end" -> "streams" -> "sender"
                try {
                    double transferSpeed = Math.round(jsonObject.getJSONObject("end")
                            .getJSONArray("streams")
                            .getJSONObject(0)
                            .getJSONObject("sender")
                            .getDouble("bits_per_second") / 8000000);

                    transferSpeeds[i] = transferSpeed; // Store the speed in the array

                    // Print the speed for this test to the terminal
                    System.out.println("Test " + (i + 1) + ": Transfer speed: " + transferSpeed + " MB per second");
                } catch (JSONException e) {
                    System.err.println("Fout bij het extraheren van de transfer speed: " + e.getMessage());
                    System.out.println("Test " + (i + 1) + ": Transfer speed: " + 0 + " MB per second");
                    //System.out.println("Bekijk de output voor details:\n" + output.toString());
                }

                // Update progress bar
                progressBar.setValue(i + 1);
                progressBar.setString("Running test " + (i + 1) + " of " + numTests);

                // Wait for the specified interval before the next test
                Thread.sleep(interval);
            }

            // Calculate the average speed
            double averageSpeed = calculateAverage(transferSpeeds);
            System.out.println(Arrays.toString(transferSpeeds));
            JOptionPane.showMessageDialog(null, "Average transfer speed over " + numTests + " tests: " + averageSpeed + " MB second",
                    "Average Speed Result",
                    JOptionPane.INFORMATION_MESSAGE);

            if (calculateStability(transferSpeeds, averageSpeed)) {
                System.out.println("Verbinding Stabiel.");
                stuurData();
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Error testing internet speed: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Error parsing JSON: " + e.getMessage());
        } finally {
            // Close the progress dialog
            frame.dispose();
        }
    }



    private static double calculateAverage(double[] speeds) {
        double sum = 0;
        for (double speed : speeds) {
            sum += speed;
        }
        return sum / speeds.length; // Return the average
    }

    private static boolean calculateStability(double[] speeds, double averagespeeds) {
        double averagelow = averagespeeds * 0.8;
        double averagehigh = averagespeeds * 1.2;
        for (double speed : speeds) {
            if (speed < averagelow || speed > averagehigh){
                System.out.println("Verbinding niet stabiel: " + speed + " wijkt teveel af van " + averagespeeds);
                return false;
            }
            else if (averagespeeds < 20){
                System.out.println("Verbinding niet stabiel, verbindingssnelheid is te laag: " + averagespeeds + "MB/s");
                return false;
            }
        }
        return true;
    }
}
