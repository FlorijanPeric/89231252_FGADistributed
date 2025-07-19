package ForceGraphLayout;
import java.util.*;

import mpi.*;

public class FRAlgorithmDistributed {
    private Graph graph;
    private double area;
    private double koeficient;
    private double temperature;
    private int Iterations;
    private double constant;
    private double minMovementInt;
    private int width;
    private int height;
    private Random random;
    private boolean runProg;
    private double coolingRate;
    private double maxDispl=0;
    // private ExecutorService exec;
    private boolean isBoolean;
    private int mpiRank, mpiSize;

    public FRAlgorithmDistributed(Graph graph, int iterations, int width, int height, int seed, boolean useBool){
        this.random=new Random(seed);
        this.width=width;
        this.height=height;
        this.constant=seed;
        this.graph=graph;
        this.Iterations=iterations;
        this.area=width*height;
        this.koeficient=(Math.sqrt(area/graph.getNodes().size()));
        this.temperature= (double) Math.min(width,height)/100;
        this.coolingRate=0.97;
        //this.temperature=0.99;
        this.minMovementInt=0.1;
        this.runProg=false;
        //this.exec= Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        this.isBoolean=useBool;
        this.mpiRank=MPI.COMM_WORLD.Rank();
        this.mpiSize=MPI.COMM_WORLD.Size();

    }
    public boolean run(){
        Logger.log("I calculating Repulsive F",LogLevel.Info);

        if(isBoolean){
            calculateDistributedRepulsiveForces();
            calculateDistributedAttractiveForces();
            //syncNodeDisplacements(graph.getNodes());
            //syncEdge(graph.getEdgesUsed());
        }
        double maxUpdate=updatePosition();
        //System.out.println(maxUpdate+"Pred ite");
        double[] globalMaxUpdate = new double[1];
        MPI.COMM_WORLD.Allreduce(new double[]{maxUpdate},0, globalMaxUpdate, 1,0, MPI.DOUBLE, MPI.MAX);
        maxUpdate = globalMaxUpdate[0];
        if (MPI.COMM_WORLD.Rank()==0) {
            //System.out.println(globalMaxUpdate[0]+"Post ite");

            cooling();
            Logger.log("I am cooling",LogLevel.Debug);
        }
        double coolingDouble[] = new double[1];
        if (mpiRank==0){
            coolingDouble[0]=temperature;
        }
        Logger.log(""+coolingDouble[0],LogLevel.Info);
        MPI.COMM_WORLD.Bcast(coolingDouble, 0, 1, MPI.DOUBLE, 0);
        temperature=coolingDouble[0];
        //Logger.log("Temp post: "+temperature,LogLevel.Info);



        Logger.log("I am updating pos",LogLevel.Debug);

        return maxUpdate<=0.001&&temperature>=0.0000000001;


    }
    /*
    private void calculateSequentialRepulsiveForces(){
        for(Node v:graph.getNodes()){
            v.dispX=0;
            v.dispY=0;
            for(Node u:graph.getNodes()){
                if(u!=v){
                    double dx=v.x-u.x;
                    double dy=v.y-u.y;
                    double distance=Math.sqrt(dx * dx + dy * dy);
                    if(distance==0) distance=0.01;
                    if(distance>0) {
                        v.dispX += (dx / distance) * forceRep(distance);
                        v.dispY += (dy / distance) * forceRep(distance);
                    }
                }
            }
        }
    }

     */



    private void calculateDistributedRepulsiveForces(){
        Logger.log(""+MPI.COMM_WORLD.Rank(),LogLevel.Success);
        //Prvo poštimamo kako bomo paralelizirali.
        List <Node> nodes=graph.getNodes();
        int nodeCount=nodes.size();
        int chunk=(nodes.size()+mpiSize-1)/mpiSize;//Mormo
        double[] positionsX = new double[nodeCount];
        double[] positionsY = new double[nodeCount];
        double[] dispX = new double[nodeCount];
        double[] dispY = new double[nodeCount];
        for (int i=0;i<nodeCount;i++){
            positionsX[i]=nodes.get(i).x;
            positionsY[i]=nodes.get(i).y;

        }
        double localX[]=new double[chunk];
        double localY[]=new double[chunk];
        double LocalDispX[]=new double[chunk];
        double LocalDispY[]=new double[chunk];
        MPI.COMM_WORLD.Scatter(positionsX,0,chunk,MPI.DOUBLE,localX,0,chunk,MPI.DOUBLE,0);
        MPI.COMM_WORLD.Scatter(positionsY,0,chunk,MPI.DOUBLE,localY,0,chunk,MPI.DOUBLE,0);
        /*Tle grejo skozi vse pozicije
        for (int i = 0; i < localX.length; i++) {
            System.out.println(localX[i]);
        }

         */
        for (int i = 0; i < chunk; i++) {
            LocalDispX[i] = 0;
            LocalDispY[i] = 0;
            for (int j = 0; j < nodeCount; j++) {
                if (mpiRank *chunk+i != j) {
                    double dx = localX[i] - positionsX[j];
                    double dy = localY[i] - positionsY[j];
                    double distance = Math.sqrt((dx * dx) + (dy * dy));
                    if (distance == 0) {
                        distance = 0.1;
                    }if (distance>0) {
                        LocalDispX[i] += (dx / distance) * forceRep(distance);
                        LocalDispY[i] += (dy / distance) * forceRep(distance);
                    }
                }

            }
            /*
            MPI.COMM_WORLD.Gather(LocalDispX,0,chunk,MPI.DOUBLE,dispX,0,chunk,MPI.DOUBLE,0);
            MPI.COMM_WORLD.Gather(LocalDispY,0,chunk,MPI.DOUBLE,dispY,0,chunk,MPI.DOUBLE,0);
            for (int j = 0; j < dispX.length; j++) {
                System.out.println(dispX[j]);
            }
            if(mpiRank==0) {
                for (int z = 0; z < nodeCount; z++) {
                    nodes.get(z).dispX=dispX[z];
                    nodes.get(z).dispY=dispY[z];
                }
            }
            MPI.COMM_WORLD.Scatter(dispX,0,chunk,MPI.DOUBLE,LocalDispX,0,chunk,MPI.DOUBLE,0);
            MPI.COMM_WORLD.Scatter(dispY,0,chunk,MPI.DOUBLE,LocalDispY,0,chunk,MPI.DOUBLE,0);

             */
        }

        MPI.COMM_WORLD.Allreduce(LocalDispX, 0, dispX, 0, chunk, MPI.DOUBLE, MPI.SUM);
        MPI.COMM_WORLD.Allreduce(LocalDispY, 0, dispY, 0, chunk, MPI.DOUBLE, MPI.SUM);
        if(mpiRank==0) {
            for (int i = 0; i < nodeCount; i++) {
                nodes.get(i).dispX=dispX[i];
                nodes.get(i).dispY=dispY[i];
            }
        }
        for (int i = 0; i < nodeCount; i++) {
            nodes.get(i).dispX = dispX[i];
            nodes.get(i).dispY = dispY[i];
        }

    }
    /*for (Runnable v:task){
        exec.submit(v);
    }
    try {
        exec.shutdown();
        exec.awaitTermination(1, TimeUnit.HOURS);
    } catch (InterruptedException e) {
        e.printStackTrace();
    }


     */
    /*
    private void calculateSequentialAttractiveForces(){
        for(Edge edge : graph.getEdgesUsed()){
            double dx=edge.start.x-edge.end.x;
            double dy=edge.start.y-edge.end.y;
            double distance=Math.sqrt(dx * dx + dy * dy);
                if(distance==0) distance=0.01;
            if(distance>0) {
                edge.start.dispX -= (dx / distance) * forceAttractive(distance);
                edge.start.dispY -= (dy / distance) * forceAttractive(distance);
                edge.end.dispX += (dx / distance) * forceAttractive(distance);
                edge.end.dispY += (dy / distance) * forceAttractive(distance);
            }
        }
    }
*/
    private void calculateDistributedAttractiveForces(){
        List <Edge> edges=graph.getEdgesUsed();
        int edgeCount=edges.size();
        int chunk=(edgeCount+mpiSize-1)/mpiSize;//Mormo
        //število nodov dt na procesorje de bo usak delau neki
        double[] edgeStartX = new double[edgeCount];
        double[] edgeEndX = new double[edgeCount];
        double[] edgeStartY=new double[edgeCount];
        double[] edgeEndY=new double[edgeCount];
        double[] dispStartX = new double[edgeCount];
        double[] dispEndX = new double[edgeCount];
        double[] dispStartY = new double[edgeCount];
        double[] dispEndY = new double[edgeCount];
        for (int i=0;i<edgeCount;i++){
            edgeStartX[i]=edges.get(i).start.x;
            edgeEndX[i]=edges.get(i).end.x;
            edgeStartY[i]=edges.get(i).start.y;
            edgeEndY[i]=edges.get(i).end.y;
        }
        double []localStartX=new double[chunk];
        double []localEndX=new double[chunk];
        double []localStartY=new double[chunk];
        double []localEndY=new double[chunk];
        double []localDispStartX=new double[chunk];
        double []localDispEndX=new double[chunk];
        double []localDispStartY=new double[chunk];
        double []localDispEndY=new double[chunk];

        MPI.COMM_WORLD.Scatter(edgeStartX,0,chunk,MPI.DOUBLE,localStartX,0,chunk,MPI.DOUBLE,0);
        MPI.COMM_WORLD.Scatter(edgeEndX,0,chunk,MPI.DOUBLE,localEndX,0,chunk,MPI.DOUBLE,0);
        MPI.COMM_WORLD.Scatter(edgeStartY,0,chunk,MPI.DOUBLE,localStartY,0,chunk,MPI.DOUBLE,0);
        MPI.COMM_WORLD.Scatter(edgeEndY,0,chunk,MPI.DOUBLE,localEndY,0,chunk,MPI.DOUBLE,0);
        for(int i=0;i<chunk;i++){
            double dx=localStartX[i]-localEndX[i];
            double dy=localStartY[i]-localEndY[i];
            double distance=Math.sqrt(dx*dx+dy*dy);
            if (distance==0){distance=0.1;}
            if (distance>0) {
                localDispStartX[i] -= (dx / distance) * forceAttractive(distance);
                localDispEndX[i] += (dx / distance) * forceAttractive(distance);
                localDispStartY[i] -= (dx / distance) * forceAttractive(distance);
                localDispEndY[i] += (dx / distance) * forceAttractive(distance);
            }

        }
        /*MPI.COMM_WORLD.Gather(localDispStartX,0,chunk,MPI.DOUBLE,dispStartX,0,chunk,MPI.DOUBLE,0);
        MPI.COMM_WORLD.Gather(localDispEndX,0,chunk,MPI.DOUBLE,dispEndX,0,chunk,MPI.DOUBLE,0);
        MPI.COMM_WORLD.Gather(localDispStartY,0,chunk,MPI.DOUBLE,dispStartY,0,chunk,MPI.DOUBLE,0);
        MPI.COMM_WORLD.Gather(localDispEndY,0,chunk,MPI.DOUBLE,dispEndY,0,chunk,MPI.DOUBLE,0);
         */
        MPI.COMM_WORLD.Allreduce(localDispStartX, 0, dispStartX, 0, chunk, MPI.DOUBLE, MPI.SUM);
        MPI.COMM_WORLD.Allreduce(localDispEndX, 0, dispEndX, 0, chunk, MPI.DOUBLE, MPI.SUM);
        MPI.COMM_WORLD.Allreduce(localDispStartY, 0, dispStartY, 0, chunk, MPI.DOUBLE, MPI.SUM);
        MPI.COMM_WORLD.Allreduce(localDispEndY, 0, dispEndY, 0, chunk, MPI.DOUBLE, MPI.SUM);



        if(mpiRank==0) {
            for(int i=0;i<edgeCount;i++){
                edges.get(i).start.dispX+=dispStartX[i];
                edges.get(i).end.dispX+=dispEndX[i];
                edges.get(i).start.dispY+=dispStartY[i];
                edges.get(i).end.dispY+=dispEndY[i];
            }
        }
    }
    /*
    for (Runnable v:task){
        exec.submit(v);
    }

    try {
        exec.shutdown();
        exec.awaitTermination(1, TimeUnit.HOURS);
    } catch (InterruptedException e) {
        e.printStackTrace();
    }

     */
    /*private void syncNodeDisplacements(List<Node> nodes){
        int nodeCount=nodes.size();
        double []displacementX=new double[nodeCount];
        double []displacementY=new double[nodeCount];
        for (int i=0;i<nodeCount;i++){
            displacementX[i]=nodes.get(i).dispX;
            displacementY[i]=nodes.get(i).dispY;
        }
        MPI.COMM_WORLD.Allreduce(MPI.COMM_SELF,0,displacementX,nodeCount,0,MPI.DOUBLE,MPI.SUM);
        MPI.COMM_WORLD.Allreduce(MPI.COMM_SELF,0,displacementY,nodeCount,0,MPI.DOUBLE,MPI.SUM);
        for(int i=0;i<nodeCount;i++){
            nodes.get(i).dispX=displacementX[i];
            nodes.get(i).dispY=displacementY[i];
        }
    }
    private void syncEdge(List<Edge> edges){
        int edgeCount=edges.size();
        double[]displacementStartX=new double[edgeCount];
        double[]displacementEndX=new double[edgeCount];
        double[]displacementStartY=new double[edgeCount];
        double[]displacementEndY=new double[edgeCount];
        for(int i=0;i<edgeCount;i++){
            displacementStartX[i]=edges.get(i).start.dispX;
            displacementEndX[i]=edges.get(i).end.dispX;
            displacementStartY[i]=edges.get(i).start.dispY;
            displacementEndY[i]=edges.get(i).end.dispY;
        }
        MPI.COMM_WORLD.Allreduce(MPI.COMM_SELF,0,displacementStartX,edgeCount,0,MPI.DOUBLE,MPI.SUM);
        MPI.COMM_WORLD.Allreduce(MPI.COMM_SELF,0,displacementEndX,edgeCount,0,MPI.DOUBLE,MPI.SUM);
        MPI.COMM_WORLD.Allreduce(MPI.COMM_SELF,0,displacementStartY,edgeCount,0,MPI.DOUBLE,MPI.SUM);
        MPI.COMM_WORLD.Allreduce(MPI.COMM_SELF,0,displacementEndY,edgeCount,0,MPI.DOUBLE,MPI.SUM);
        for (int i = 0; i <edgeCount ; i++) {
            edges.get(i).start.dispX=displacementStartX[i];
            edges.get(i).end.dispX=displacementStartX[i];
            edges.get(i).start.dispY=displacementStartX[i];
            edges.get(i).end.dispY=displacementStartX[i];

        }
    }

     */
    private void syncNodeDisplacements(List<Node> nodes) {
        int nodeCount = nodes.size();
        double[] displacementX = new double[nodeCount];
        double[] displacementY = new double[nodeCount];

        // Store displacements for each node
        for (int i = 0; i < nodeCount; i++) {
            displacementX[i] = nodes.get(i).dispX;
            displacementY[i] = nodes.get(i).dispY;
        }

        // Perform Allreduce to sum displacements across all processes
        MPI.COMM_WORLD.Allreduce(displacementX,0, displacementX,0, nodeCount, MPI.DOUBLE, MPI.SUM);
        MPI.COMM_WORLD.Allreduce(displacementY,0, displacementY,0, nodeCount, MPI.DOUBLE, MPI.SUM);

        // Update the node displacements with the summed values
        for (int i = 0; i < nodeCount; i++) {
            nodes.get(i).dispX = displacementX[i];
            nodes.get(i).dispY = displacementY[i];
        }
    }

    private void syncEdge(List<Edge> edges) {
        int edgeCount = edges.size();
        double[] displacementStartX = new double[edgeCount];
        double[] displacementEndX = new double[edgeCount];
        double[] displacementStartY = new double[edgeCount];
        double[] displacementEndY = new double[edgeCount];

        // Store displacements for each edge's start and end nodes
        for (int i = 0; i < edgeCount; i++) {
            displacementStartX[i] = edges.get(i).start.dispX;
            displacementEndX[i] = edges.get(i).end.dispX;
            displacementStartY[i] = edges.get(i).start.dispY;
            displacementEndY[i] = edges.get(i).end.dispY;
        }

        // Perform Allreduce to sum displacements across all processes
        MPI.COMM_WORLD.Allreduce(displacementStartX,0, displacementStartX,0, edgeCount, MPI.DOUBLE, MPI.SUM);
        MPI.COMM_WORLD.Allreduce(displacementEndX,0, displacementEndX,0, edgeCount, MPI.DOUBLE, MPI.SUM);
        MPI.COMM_WORLD.Allreduce(displacementStartY,0, displacementStartY,0, edgeCount, MPI.DOUBLE, MPI.SUM);
        MPI.COMM_WORLD.Allreduce(displacementEndY,0, displacementEndY,0, edgeCount, MPI.DOUBLE, MPI.SUM);

        // Update edge start and end node displacements
        for (int i = 0; i < edgeCount; i++) {
            edges.get(i).start.dispX = displacementStartX[i];
            edges.get(i).start.dispY = displacementStartY[i];
            edges.get(i).end.dispX = displacementEndX[i];
            edges.get(i).end.dispY = displacementEndY[i];
        }
    }




    private double updatePosition() {

        for (Node v : graph.getNodes()) {
            double display = Math.sqrt(v.dispX * v.dispX + v.dispY * v.dispY);
            maxDispl = Math.max(maxDispl, display);
            //if (display==0) display=0.01;
            if (display > 0) {
                v.x += (v.dispX / display) * Math.min(display, temperature);
                v.y += (v.dispY / display) * Math.min(display, temperature);
            }
            //v.x = Math.max(10,Math.min(v.x,width-10));
            //v.y = Math.min((double) height / 2, Math.max(((double) (height * -1) / 2), v.y));
            //v.y=Math.max(10,Math.min(v.x,width-10));
            v.x = Math.max(5, Math.min(v.x, width -10));
            v.y = Math.max(5, Math.min(v.y, height -10));
        }
        return maxDispl;
    }
    /* public void closeExec(){
         exec.shutdown();
     }

     */
    private void cooling(){
        //temperature-=Math.max(temperature*0.95,0);
        temperature=Math.max(temperature*(coolingRate-0.0001),0);
        Logger.log("Temperature == "+temperature,LogLevel.Debug);
    }
    private double forceRep(double dist){
        return (koeficient * koeficient)/dist;
    }
    private double forceAttractive(double dist){
        return (dist * dist)/koeficient;
    }
}
