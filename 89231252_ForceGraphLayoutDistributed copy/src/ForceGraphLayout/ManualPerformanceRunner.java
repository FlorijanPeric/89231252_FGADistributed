package ForceGraphLayout;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import mpi.MPI;

/**
 * Razred za ročno izvajanje testov zmogljivosti algoritma Fruchterman-Reingold.
 * Ta razred ne uporablja JUnit, temveč izvaja teste neposredno preko metode main.
 * Rezultati zmogljivosti so izpisani v terminalu in shranjeni v datoteko.
 *
 * POMEMBNO: Ta različica zagotavlja, da se algoritem izvede za celotno določeno število iteracij,
 * ne glede na to, ali se je že konvergirala.
 */
public class ManualPerformanceRunner {

    // PrintWriter za zapisovanje v datoteko
    private static PrintWriter fileWriter;

    /**
     * Pomožna metoda za izvajanje algoritma in merjenje časa.
     *
     * @param nodeCount Število vozlišč v grafu.
     * @param edgeCount Število povezav v grafu.
     * @param iterations Število iteracij algoritma.
     * @param width Širina simulacijskega območja.
     * @param height Višina simulacijskega območja.
     * @param testName Ime testa za izpis v terminal.
     * @return True, če se algoritem uspešno izvede do konca (ali se ustavi zaradi pogoja), sicer false.
     * @throws InterruptedException Če je nit prekinjena med spanjem (ni relevantno za ta primer).
     */
    private static boolean runPerformanceTest(int nodeCount, int edgeCount, int iterations, int width, int height, String testName) throws InterruptedException {
        // Ustvari nov graf z določenimi parametri
        Graph graph = new Graph(nodeCount, edgeCount, 30, width, height);
        // Ustvari instanco algoritma Fruchterman-Reingold
        FRAlgorithmDistributed alg = new FRAlgorithmDistributed(graph, iterations, width, height, 40,true);

        // Zabeleži začetni čas izvajanja
        long startTime = System.nanoTime(); // Uporabimo nanoTime za večjo natančnost

        // Izvajaj algoritem za določeno število iteracij
        // Odstranjen je pogoj za prekinitev zanke, da se zagotovi izvajanje vseh iteracij.
        for (int i = 0; i < iterations; i++) {
            alg.run(); // Kličemo algoritem, ne glede na njegovo konvergenco
        }

        // Zabeleži končni čas izvajanja
        long endTime = System.nanoTime();
        // Izračunaj trajanje v milisekundah
        double durationMs = (endTime - startTime) / 1_000_000.0;
        // Izračunaj trajanje v sekundah
        double durationS = durationMs / 1000.0;

        // Izpiši podrobne metrike zmogljivosti v terminal
        Logger.log("--- Performance Metrics for: " + testName + " ---", LogLevel.Status);
        Logger.log("Nodes: " + nodeCount + ", Edges: " + edgeCount + ", Iterations: " + iterations, LogLevel.Status);
        Logger.log(String.format("Execution Time: %.2f ms (%.2f s)", durationMs, durationS), LogLevel.Success);
        Logger.log("--------------------------------------------------", LogLevel.Status);

        // Zapiši samo želene, povzete podatke v datoteko
        if (fileWriter != null) {
            fileWriter.println(String.format("Test: %s, Nodes: %d, Iterations: %d, Time: %.2f ms",
                    testName, nodeCount, iterations, durationMs));
            fileWriter.flush(); // Takoj zapiši v datoteko, da se podatki ne izgubijo ob morebitni napaki
        }

        // Vedno vrnemo true, saj smo zagotovili izvedbo vseh iteracij
        return true;
    }

    /**
     * Glavna metoda za zagon ročnih testov zmogljivosti.
     *
     * @param args Argumenti ukazne vrstice (neuporabljeni).
     * @throws InterruptedException Če pride do napake med izvajanjem (npr. prekinitev niti).
     */
    public static void main(String[] args) throws InterruptedException {
        MPI.Init(args);
        try {
            // Inicializiraj zapisovanje v datoteko. 'false' pomeni, da bo datoteka prepisana ob vsakem zagonu.
            fileWriter = new PrintWriter(new FileWriter("performance_metrics.txt", false));
            fileWriter.println("Performance Test Results:");
            fileWriter.println("-------------------------");

            Logger.log("Starting Manual Performance Tests...", LogLevel.Info);

            // 1. Easy Performance Test
            Logger.log("\nStarting Easy Performance Test...", LogLevel.Info);
            runPerformanceTest(100, 100, 200, 600, 400, "Easy Performance Test");

            // 2. Semi-Mid Performance Test
            Logger.log("\nStarting Semi-Mid Performance Test...", LogLevel.Info);
            runPerformanceTest(750, 750, 1000, 1000, 700, "Semi-Mid Performance Test");

            // 3. Max Performance Test
            // Upoštevajte, da bo ta test z 10.000 vozlišči in 1000 iteracijami trajal ZELO dolgo in porabil veliko pomnilnika.
            // Morda boste morali prilagoditi JVM heap size (-Xmx parameter).
            Logger.log("\nStarting Max Performance Test (10,000 Nodes)...", LogLevel.Info);
            runPerformanceTest(10000, 10000, 1000, 1920, 1080, "Max Performance Test");


            Logger.log("\nAll Manual Performance Tests Completed.", LogLevel.Success);
            Logger.log("\nStarting Full Performance Test (100, 000 Nodes)",LogLevel.Info);
            Logger.log("\nThis might take a lot of time",LogLevel.Warn);
            runPerformanceTest(100000,100000,1000,4000,3680,"FullPerformanceTest");

        } catch (IOException e) {
            // Izpiši napako v terminal, če pride do težave pri zapisovanju v datoteko
            Logger.log("Error initializing or writing to performance_metrics.txt: " + e.getMessage(), LogLevel.Error);
        } finally {
            // Zapri fileWriter v vsakem primeru, da zagotoviš shranjevanje vseh podatkov
            if (fileWriter != null) {
                fileWriter.close();
                System.out.println("Performance metrics saved to performance_metrics.txt");
            }
        }
        MPI.Finalize();
    }
}
