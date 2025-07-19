package ForceGraphLayout;
import javax.swing.*;
import java.awt.*;
import java.util.*;

public class UI extends JPanel{
    private Graph grapha;
    public UI(Graph graph){
        this.grapha=graph;
        this.setPreferredSize(new Dimension(800,800));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2=(Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Color.BLACK);
        for (Edge e:grapha.getEdgesUsed()){
            //Logger.log("Painting edge",LogLevel.Debug);
            g2.drawLine((int)e.start.x,(int)e.start.y,(int)e.end.x,(int)e.end.y);
        }
        g2.setColor(Color.BLUE);
        for (Node u:grapha.getNodes()){
            //Logger.log("Painting node",LogLevel.Debug);
            g2.fillOval((int) u.x,(int)u.y,4,4);
        }
    }
    public void updateGraph(){
        Logger.log("Updating",LogLevel.Debug);
        repaint();
    }
}
