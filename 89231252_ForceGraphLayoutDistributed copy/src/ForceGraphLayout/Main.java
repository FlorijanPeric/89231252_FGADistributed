/*package ForceGraphLayout;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.CountDownLatch;

import mpi.MPI;

public class Main {
    // Definiramo AppConfig kot statični vnešeni razred
    static class AppConfig {
        int nodes;
        int edges;
        int width;
        int height;
        String mode;
        int iterations;

        public AppConfig() {
            // Privzeti konstruktor za sprejem broadcastanih podatkov
        }

        public AppConfig(int nodes, int edges, int width, int height, String mode, int iterations) {
            this.nodes = nodes;
            this.edges = edges;
            this.width = width;
            this.height = height;
            this.mode = mode;
            this.iterations = iterations;
        }
    }

    public static void main(String[] args) throws InterruptedException {
        // MPI inicializacija mora biti prva stvar v main metodi za vse procese
        MPI.Init(args);
        int me = MPI.COMM_WORLD.Rank();
        //int nodesMPI = MPI.COMM_WORLD.Size(); // To spremenljivko trenutno ne potrebujemo neposredno

        AppConfig appConfig;

        // Glavni proces (rank 0) obravnava StartUi in zbere konfiguracijo
        if (me == 0) {
            final AppConfig[] tempConfig = new AppConfig[1];
            CountDownLatch configLatch = new CountDownLatch(1);

            SwingUtilities.invokeLater(() -> {
                StartUi configUI = new StartUi(800, 600, configLatch);
                configUI.createUI();
                configUI.getSaveButton().addActionListener(e -> {
                    try {
                        tempConfig[0] = new AppConfig(
                                configUI.getNumberOfNodes(),
                                configUI.getNumberOfEdges(),
                                configUI.getGraphWidth(),
                                configUI.getGraphHeight(),
                                configUI.getSelectedMode(),
                                configUI.getNumberOfIterations()
                        );
                    } catch (NumberFormatException ex) {
                        JOptionPane.showMessageDialog(configUI, "Prosim, vnesite veljavne številke.", "Napaka vnosa", JOptionPane.ERROR_MESSAGE);
                        tempConfig[0] = null; // Signaliziraj neveljaven vnos
                    }
                    configLatch.countDown();
                });
            });

            configLatch.await(); // Počakaj, da UI zagotovi konfiguracijo

            appConfig = tempConfig[0];
            if (appConfig == null) {
                Logger.log("Konfiguracija ni bila uspešno pridobljena. Zapiram aplikacijo.", LogLevel.Error);
                MPI.COMM_WORLD.Abort(1); // Takojšen zaključek za vse procese
            }
        } else {
            // Ostali procesi čakajo na konfiguracijo od ranka 0
            appConfig = new AppConfig(); // Inicializiramo prazen objekt, ki bo napolnjen z broadcastom
        }

        // **Broadcast konfiguracije vsem procesom**
        // To je ključno za zagotavljanje, da imajo vsi procesi enake začetne podatke za Graph
        int[] configValues = new int[5]; // nodes, edges, width, height, iterations
        byte[] modeBytes = new byte[100]; // dovolj velik za "UI" ali "Performance"

        if (me == 0) {
            configValues[0] = appConfig.nodes;
            configValues[1] = appConfig.edges;
            configValues[2] = appConfig.width;
            configValues[3] = appConfig.height;
            configValues[4] = appConfig.iterations;
            byte[] tempModeBytes = appConfig.mode.getBytes();
            // Prepreči IndexOutOfBoundsException, če je dolžina niza manjša od 100
            System.arraycopy(tempModeBytes, 0, modeBytes, 0, Math.min(tempModeBytes.length, modeBytes.length));
            // Preostale bajte v modeBytes so 0, kar je ok za trim()
        }

        // Oddaj konfiguracijske vrednosti
        MPI.COMM_WORLD.Bcast(configValues, 0, 5, MPI.INT, 0);
        MPI.COMM_WORLD.Bcast(modeBytes, 0, modeBytes.length, MPI.BYTE, 0);

        // Če proces ni rank 0, sprejmi konfiguracijo
        if (me != 0) {
            appConfig.nodes = configValues[0];
            appConfig.edges = configValues[1];
            appConfig.width = configValues[2];
            appConfig.height = configValues[3];
            appConfig.iterations = configValues[4];
            appConfig.mode = new String(modeBytes).trim(); // Trim odstrani morebitne ničelne bajte
        }

        // Sedaj imajo vsi procesi enake konfiguracijske podatke
        int nodeCount = appConfig.nodes;
        int edgeCount = appConfig.edges;
        int width = appConfig.width;
        int height = appConfig.height;
        int iterations = appConfig.iterations;
        String mode = appConfig.mode;

        long seed = 30; // Lahko dodate v AppConfig, če želite, da je nastavljivo preko UI
        int seed1 = 40; // Prav tako lahko dodate v AppConfig

        Graph graph = new Graph(nodeCount, edgeCount, seed, width, height);
        // Uporabimo FRAlgorithmDistributed z zastavico 'true', da vklopi distribuirane operacije.
        FRAlgorithmDistributed alg = new FRAlgorithmDistributed(graph, iterations, width, height, seed1, true);

        // Nastavitev UI samo za rank 0 in samo v "UI" načinu
        JFrame frame = null;
        UI panel = null;
        if (me == 0 && mode.equals("UI")) {
            frame = new JFrame("Force Layout Graph");
            panel = new UI(graph);
            frame.add(panel);
            frame.setSize(width, height);
            frame.setVisible(true);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    // Poskrbi, da se MPI.Finalize pokliče tudi, če se UI zapre ročno
                    MPI.COMM_WORLD.Abort(0); // Čista zaustavitev
                }
            });
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // Pomembno za pravilno zapiranje okna
        }

        long start = System.currentTimeMillis();

        try {
            for (int i = 0; i < iterations; i++) {
                // Algoritem teče na vseh procesih
                if (!alg.run()) {
                    Logger.log("Rank " + me + ": Konvergenca dosežena ali temperatura prenizka, zapiram.", LogLevel.Success);
                    break; // Končaj zanko, če algoritem sporoči, da je končal
                } else {
                    Logger.log("Rank " + me + ": Delam (iteracija " + (i + 1) + ")", LogLevel.Info);
                    // UI posodobitve samo za rank 0 in samo v "UI" načinu
                    if (me == 0 && mode.equals("UI") && panel != null) {
                        panel.updateGraph();
                        // Dodajte majhno zakasnitev za boljši vizualni učinek v UI načinu
                        Thread.sleep(10);
                    }
                }
            }
        } finally {
            Logger.log("Rank " + me + ": Konec izvajanja simulacije.", LogLevel.Warn);
        }

        long end = System.currentTimeMillis();
        Logger.log("Rank " + me + ": Porabljen čas " + (end - start) + " ms, " + ((end - start) / 1000) + " s, "
                + (double) ((end - start) / 1000.0) / 60.0 + " min", LogLevel.Success);

        if (frame != null) { // Zapri glavno okno, če je bilo ustvarjeno na ranku 0
            frame.dispose();
        }

        MPI.Finalize(); // Pokliči MPI.Finalize na koncu za vse procese
    }
}

 */
package ForceGraphLayout;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.CountDownLatch;

import mpi.MPI;

public class Main {

    // PrintWriter za zapisovanje v datoteko (za rezultate testov)
    private static PrintWriter fileWriter;

    // Notranji razred za konfiguracijo aplikacije
    // To omogoča, da se konfiguracija prenese med MPI procesi
    static class AppConfig implements java.io.Serializable {
        int nodes;
        int edges;
        int width;
        int height;
        String mode; // "UI" ali "Performance"
        int iterations;

        public AppConfig() {
            // Privzeti konstruktor za sprejem podatkov preko MPI
        }

        public AppConfig(int nodes, int edges, int width, int height, String mode, int iterations) {
            this.nodes = nodes;
            this.edges = edges;
            this.width = width;
            this.height = height;
            this.mode = mode;
            this.iterations = iterations;
        }

        // Metoda za preverjanje, ali so parametri "prazni" (privzete vrednosti StartUi)
        public boolean isDefaultPerformanceConfig() {
            return this.mode.equals("Performance") &&
                    this.nodes == 100 && // Default value in StartUi
                    this.edges == 100 && // Default value in StartUi
                    this.iterations == 1000; // Default value in StartUi
        }
    }

    /**
     * Pomožna metoda za izvajanje enega samega testa z distribuiranim algoritmom.
     *
     * @param nodeCount Število vozlišč v grafu.
     * @param edgeCount Število povezav v grafu.
     * @param iterations Število iteracij algoritma.
     * @param width Širina simulacijskega območja.
     * @param height Višina simulacijskega območja.
     * @param testName Ime testa za izpis v terminal.
     * @param mpiRank Trenutni MPI rank.
     * @return True, če se algoritem uspešno izvede do konca, sicer false.
     * @throws InterruptedException Če je nit prekinjena med spanjem.
     */
    private static boolean runSingleDistributedPerformanceTest(int nodeCount, int edgeCount, int iterations, int width, int height, String testName, int mpiRank) throws InterruptedException {
        Graph graph = null;
        FRAlgorithmDistributed alg = null;
        long startTime = 0;
        long endTime = 0;

        // Vsi procesi morajo inicializirati isti graf z istim seedom
        graph = new Graph(nodeCount, edgeCount, 30, width, height);

        // Ustvari instanco distribuiranega algoritma
        alg = new FRAlgorithmDistributed(graph, iterations, width, height, 40, true);

        // Zabeleži začetni čas izvajanja (samo na ranku 0 za celoten test)
        if (mpiRank == 0) {
            startTime = System.nanoTime();
        }

        // Izvajaj algoritem za določeno število iteracij
        for (int i = 0; i < iterations; i++) {
            boolean continueRun = alg.run(); // Izvede eno iteracijo distribuiranega algoritma
            if (!continueRun) {
                Logger.log("Rank " + mpiRank + ": Algoritem konvergirana ali temperatura prenizka za test " + testName + ", prekinjam.", LogLevel.Success);
                break;
            }
        }

        // Zabeleži končni čas izvajanja (samo na ranku 0)
        if (mpiRank == 0) {
            endTime = System.nanoTime();
        }

        // Izračunaj in izpiši rezultate samo na ranku 0
        if (mpiRank == 0) {
            double durationMs = (endTime - startTime) / 1_000_000.0;
            double durationS = durationMs / 1000.0;

            Logger.log("--- Performance Metrics for: " + testName + " ---", LogLevel.Status);
            Logger.log("Nodes: " + nodeCount + ", Edges: " + edgeCount + ", Iterations: " + iterations, LogLevel.Status);
            Logger.log(String.format("Execution Time: %.2f ms (%.2f s)", durationMs, durationS), LogLevel.Success);
            Logger.log("--------------------------------------------------", LogLevel.Status);

            // Zapiši rezultate v datoteko
            if (fileWriter != null) {
                fileWriter.println(String.format("Test: %s, Nodes: %d, Edges: %d, Iterations: %d, Time: %.2f ms",
                        testName, nodeCount, edgeCount, iterations, durationMs));
                fileWriter.flush();
            }
        }
        return true; // Vedno vrnemo true, saj je test izveden
    }

    public static void main(String[] args) throws InterruptedException {
        // MPI inicializacija mora biti prva stvar v main metodi za vse procese
        MPI.Init(args);
        int mpiRank = MPI.COMM_WORLD.Rank();
        int mpiSize = MPI.COMM_WORLD.Size();

        AppConfig appConfig = null;

        // Glavni proces (rank 0) obravnava StartUi in zbere konfiguracijo
        if (mpiRank == 0) {
            final AppConfig[] tempConfig = new AppConfig[1];
            CountDownLatch configLatch = new CountDownLatch(1); // Uporabimo za sinhronizacijo UI niti

            SwingUtilities.invokeLater(() -> {
                StartUi ui = new StartUi(800, 800, configLatch); // Pošljemo latch v UI
                ui.createUI();

                // Dodamo listener na gumb "Save & Run"
                ui.getSaveButton().addActionListener(e -> {
                    try {
                        tempConfig[0] = new AppConfig(
                                ui.getNumberOfNodes(),
                                ui.getNumberOfEdges(),
                                ui.getGraphWidth(),
                                ui.getGraphHeight(),
                                ui.getSelectedMode(),
                                ui.getNumberOfIterations()
                        );
                    } catch (NumberFormatException ex) {
                        JOptionPane.showMessageDialog(ui, "Prosim, vnesite veljavne številke za vozlišča, povezave, širino, višino in iteracije.", "Napaka vnosa", JOptionPane.ERROR_MESSAGE);
                        tempConfig[0] = null; // Signaliziraj neveljaven vnos
                    } finally {
                        configLatch.countDown(); // Sprostimo latch ne glede na to, ali je bil vnos uspešen ali ne
                        ui.dispose(); // Zapremo okno
                    }
                });

                // Dodamo WindowListener za obravnavo zapiranja okna brez klika na Save & Run
                ui.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent e) {
                        // Če je okno zaprto in konfiguracija še ni nastavljena (npr. uporabnik zapre UI brez klika Save & Run)
                        if (tempConfig[0] == null) {
                            // Nastavimo privzete vrednosti za performance test
                            tempConfig[0] = new AppConfig(100, 100, 800, 600, "Performance", 1000);
                            Logger.log("Rank 0: StartUi zaprt brez shranjevanja. Uporabljam privzete vrednosti za Performance Mode (100 vozlišč, 100 povezav, 1000 iteracij).", LogLevel.Warn);
                        }
                        configLatch.countDown(); // Sprostimo latch
                    }
                });
            });

            try {
                configLatch.await(); // Počakamo, da uporabnik konfigurira UI
                appConfig = tempConfig[0]; // Pridobimo konfiguracijo
            } catch (InterruptedException e) {
                Logger.log("Rank 0: Prekinitev med čakanjem na konfiguracijo UI: " + e.getMessage(), LogLevel.Error);
                Thread.currentThread().interrupt();
                // Nastavi privzeto konfiguracijo v primeru prekinitve
                if (appConfig == null) {
                    appConfig = new AppConfig(100, 100, 800, 600, "Performance", 1000);
                    Logger.log("Rank 0: Napaka pri čakanju na UI, uporabljam privzete vrednosti za Performance Mode.", LogLevel.Error);
                }
            }
        }

        // Oddaj konfiguracijo iz ranka 0 vsem ostalim procesom
        // Uporabimo Object Bcast za celoten AppConfig objekt
        AppConfig[] configArray = new AppConfig[]{appConfig}; // Zapakiraj v array za Bcast
        MPI.COMM_WORLD.Bcast(configArray, 0, 1, MPI.OBJECT, 0);
        appConfig = configArray[0]; // Razpakiraj objekt po Bcastu (sedaj je appConfig enak na vseh rankih)

        // Preverimo, ali je appConfig še vedno null po Bcastu (čeprav ne bi smel biti, če je rank 0 pravilno nastavil)
        if (appConfig == null) {
            Logger.log("Rank " + mpiRank + ": Konfiguracija je null po Bcastu. Izhajam iz MPI.", LogLevel.Error);
            MPI.Finalize();
            return;
        }

        // Izlušči konfiguracijske vrednosti
        String mode = appConfig.mode;
        int nodes = appConfig.nodes;
        int edges = appConfig.edges;
        int width = appConfig.width;
        int height = appConfig.height;
        int iterations = appConfig.iterations;

        Logger.log("Rank " + mpiRank + ": Prejeta konfiguracija - Način: " + mode +
                ", Vozlišča: " + nodes + ", Povezave: " + edges +
                ", Iteracije: " + iterations + ", Širina: " + width + ", Višina: " + height, LogLevel.Info);

        // Preverimo pogoje za izvajanje hardkodiranih performance testov
        // 1. Mode je "Performance"
        // 2. In vsi parametri (nodes, edges, iterations) so prazni (t.j. imajo privzete vrednosti iz StartUi)
        boolean runHardcodedPerformanceTests = appConfig.isDefaultPerformanceConfig();

        if (runHardcodedPerformanceTests) {
            if (mpiRank == 0) {
                try {
                    fileWriter = new PrintWriter(new FileWriter("performance_metrics_distributed.txt", false));
                    fileWriter.println("Distributed Performance Test Results:");
                    fileWriter.println("-------------------------------------");
                    Logger.log("Starting Hardcoded Distributed Performance Tests...", LogLevel.Info);
                } catch (IOException e) {
                    Logger.log("Rank " + mpiRank + ": Napaka pri inicializaciji ali pisanju v datoteko performance_metrics_distributed.txt: " + e.getMessage(), LogLevel.Error);
                }
            }

            // Izvedba vseh štirih hardkodiranih testov zmogljivosti
            Logger.log("Rank " + mpiRank + ": Začenjam Easy Distributed Performance Test...", LogLevel.Info);
            runSingleDistributedPerformanceTest(100, 100, 200, 600, 400, "Easy Distributed Performance Test", mpiRank);

            Logger.log("Rank " + mpiRank + ": Začenjam Semi-Mid Distributed Performance Test...", LogLevel.Info);
            runSingleDistributedPerformanceTest(750, 750, 1000, 1000, 700, "Semi-Mid Distributed Performance Test", mpiRank);

            Logger.log("Rank " + mpiRank + ": Začenjam Max Distributed Performance Test (10,000 Nodes)...", LogLevel.Info);
            runSingleDistributedPerformanceTest(10000, 10000, 1000, 1920, 1080, "Max Distributed Performance Test", mpiRank);

            Logger.log("Rank " + mpiRank + ": Začenjam Full Distributed Performance Test (100,000 Nodes)...", LogLevel.Info);
            Logger.log("Rank " + mpiRank + ": To lahko traja dolgo časa!", LogLevel.Warn);
            runSingleDistributedPerformanceTest(100000, 100000, 1000, 4000, 3680, "Full Distributed Performance Test", mpiRank);

            if (mpiRank == 0) {
                Logger.log("\nAll Hardcoded Distributed Performance Tests Completed.", LogLevel.Success);
            }

            if (mpiRank == 0 && fileWriter != null) {
                fileWriter.close();
                System.out.println("Performance metrics saved to performance_metrics_distributed.txt");
            }

        } else { // Izvedi UI ali Performance z uporabniškimi parametri
            Graph graph = new Graph(nodes, edges, 30, width, height);
            FRAlgorithmDistributed alg = new FRAlgorithmDistributed(graph, iterations, width, height, 40, true);

            JFrame frame = null;
            UI panel = null;

            // UI elementi so ustvarjeni samo na ranku 0 in če je izbran UI način
            if (mpiRank == 0 && mode.equals("UI")) {
                frame = new JFrame("Force Layout Graph");
                panel = new UI(graph);
                frame.add(panel);
                frame.setVisible(true);
                frame.setSize(width, height);
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            }

            long start = System.currentTimeMillis();

            try {
                for (int i = 0; i < iterations; i++) {
                    if (!alg.run()) {
                        Logger.log("Rank " + mpiRank + ": Ni smiselno nadaljevati, zapiram.", LogLevel.Success);
                        break;
                    } else {
                        Logger.log("Rank " + mpiRank + ": Delam (iteracija " + (i + 1) + ")", LogLevel.Info);
                        if (mpiRank == 0 && mode.equals("UI") && panel != null) {
                            panel.updateGraph();
                            // Po želji dodajte majhno zakasnitev za boljši vizualni učinek v UI načinu
                            // Thread.sleep(10);
                        }
                    }
                }
            } finally {
                // To je ključno za pravilno delovanje in sproščanje virov
                Logger.log("Rank " + mpiRank + ": Zaključujem izvajanje algoritma.", LogLevel.Warn);
            }

            long end = System.currentTimeMillis();
            Logger.log("Rank " + mpiRank + ": Porabljen čas " + (end - start) + " ms, " + ((end - start) / 1000) + " s, "
                    + (double) ((end - start) / 1000.0) / 60.0 + " min", LogLevel.Success);

            if (frame != null) { // Zapri glavno okno, če je bilo ustvarjeno
                frame.dispose();
            }
        }
        MPI.Finalize(); // MPI finalizacija mora biti zadnja stvar v main metodi za vse procese
        Logger.log("Rank " + mpiRank + ": MPI finaliziran.", LogLevel.Status);
    }
}