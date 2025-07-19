package ForceGraphLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.concurrent.CountDownLatch;

public class StartUi extends JFrame {
    private JTextField fieldNodes;
    private JTextField fieldEdges;
    private JTextField fieldWidth;
    private JTextField fieldHeight;
    private JTextField fieldIterations;
    private JComboBox<String> modeComboBox;
    private JButton saveButton;
    private CountDownLatch latch;

    public StartUi(int width, int height, CountDownLatch latch) {
        this.latch = latch;
        setPreferredSize(new Dimension(width, height));
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                latch.countDown(); // Release the latch if the window is closed
            }
        });
    }

    public void createUI() {
        setTitle("Force Graph Configuration");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        int row = 0;

        // Nodes
        JLabel label1 = new JLabel("Number of Nodes:");
        fieldNodes = new JTextField("100"); // Default value
        fieldNodes.setToolTipText("Enter the number of nodes in the graph");

        gbc.gridx = 0; gbc.gridy = row; add(label1, gbc);
        gbc.gridx = 1; add(fieldNodes, gbc);

        // Edges
        JLabel label2 = new JLabel("Number of Edges:");
        fieldEdges = new JTextField("100"); // Default value
        fieldEdges.setToolTipText("Enter the number of edges in the graph");

        gbc.gridx = 0; gbc.gridy = ++row; add(label2, gbc);
        gbc.gridx = 1; add(fieldEdges, gbc);

        // Width
        JLabel labelWidth = new JLabel("Graph Width:");
        fieldWidth = new JTextField("800"); // Default value
        fieldWidth.setToolTipText("Enter the width of the graph area");

        gbc.gridx = 0; gbc.gridy = ++row; add(labelWidth, gbc);
        gbc.gridx = 1; add(fieldWidth, gbc);

        // Height
        JLabel labelHeight = new JLabel("Graph Height:");
        fieldHeight = new JTextField("600"); // Default value
        fieldHeight.setToolTipText("Enter the height of the graph area");

        gbc.gridx = 0; gbc.gridy = ++row; add(labelHeight, gbc);
        gbc.gridx = 1; add(fieldHeight, gbc);

        // Iterations
        JLabel labelIterations = new JLabel("Number of Iterations:");
        fieldIterations = new JTextField();
        fieldIterations.setToolTipText("Enter the number of iterations for the algorithm");

        gbc.gridx = 0; gbc.gridy = ++row; add(labelIterations, gbc);
        gbc.gridx = 1; add(fieldIterations, gbc);

        // Mode (UI / Performance)
        JLabel modeLabel = new JLabel("Mode:");
        String[] modes = {"UI", "Performance"};
        modeComboBox = new JComboBox<>(modes);
        modeComboBox.setToolTipText("Select mode to run the application");

        gbc.gridx = 0; gbc.gridy = ++row; add(modeLabel, gbc);
        gbc.gridx = 1; add(modeComboBox, gbc);

        // Save Button
        saveButton = new JButton("Save & Run");
        saveButton.addActionListener(e -> {
            try {
                // Validate input before closing
                Integer.parseInt(fieldNodes.getText());
                Integer.parseInt(fieldEdges.getText());
                Integer.parseInt(fieldWidth.getText());
                Integer.parseInt(fieldHeight.getText());
                latch.countDown(); // Release the latch when button is clicked
                dispose(); // Close the StartUi window
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Please enter valid numbers for nodes, edges, width, and height.", "Input Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        gbc.gridx = 0; gbc.gridy = ++row;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        add(saveButton, gbc);

        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    // Getter methods
    public int getNumberOfNodes() {
        return Integer.parseInt(fieldNodes.getText());
    }

    public int getNumberOfEdges() {
        return Integer.parseInt(fieldEdges.getText());
    }

    public int getGraphWidth() {
        return Integer.parseInt(fieldWidth.getText());
    }

    public int getGraphHeight() {
        return Integer.parseInt(fieldHeight.getText());
    }
    public int getNumberOfIterations() {return Integer.parseInt(fieldIterations.getText());}

    public String getSelectedMode() {
        return (String) modeComboBox.getSelectedItem();
    }

    public JButton getSaveButton() {
        return saveButton;
    }
}