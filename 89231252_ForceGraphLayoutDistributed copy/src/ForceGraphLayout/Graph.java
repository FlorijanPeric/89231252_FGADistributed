package ForceGraphLayout;
import java.util.*;
public class Graph {
    private List<Node> nodes;
    private List<Edge> edgesUsed;
    int widthInGraph;
    int heightInGraph;
    private Random random;
    public Graph(int nodesToUse,int edgesToUse,long seed,int width,int height){
        this.nodes=new ArrayList<>();
        this.edgesUsed=new ArrayList<>();
        this.random=new Random(seed);
        this.widthInGraph=width;
        this.heightInGraph=height;
        for (int i = 0; i < nodesToUse; i++) {
            nodes.add(new Node(i,random.nextDouble()*width,random.nextDouble()*height));
            Logger.log("Adding nodes");
        }
        for (int i = 0; i < edgesToUse; i++) {
            Node start=nodes.get(random.nextInt(nodesToUse));
            Node end=nodes.get(random.nextInt(nodesToUse));
            edgesUsed.add(new Edge(start,end));
            Logger.log("Adding edges");

        }
    }


    public List<Node> getNodes(){
        return nodes;
    }
    public  List<Edge> getEdgesUsed(){
        return edgesUsed;
    }
}
