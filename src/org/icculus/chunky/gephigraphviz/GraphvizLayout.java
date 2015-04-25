/*
  Copyright (C) 2011 Gary Briggs

  This software is provided 'as-is', without any express or implied
  warranty.  In no event will the authors be held liable for any damages
  arising from the use of this software.

  Permission is granted to anyone to use this software for any purpose,
  including commercial applications, and to alter it and redistribute it
  freely, subject to the following restrictions:

  1. The origin of this software must not be misrepresented; you must not
     claim that you wrote the original software. If you use this software
     in a product, an acknowledgment in the product documentation would be
     appreciated but is not required.
  2. Altered source versions must be plainly marked as such, and must not be
     misrepresented as being the original software.
  3. This notice may not be removed or altered from any source distribution.

        Gary Briggs <chunky@icculus.org>
*/

package org.icculus.chunky.gephigraphviz;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map;
import java.util.HashMap;
import java.util.MissingResourceException;

import javax.swing.JOptionPane;

import org.gephi.graph.api.Edge;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.Node;
import org.gephi.graph.api.NodeData;
import org.gephi.layout.plugin.AbstractLayout;
import org.gephi.layout.spi.Layout;
import org.gephi.layout.spi.LayoutBuilder;
import org.gephi.layout.spi.LayoutProperty;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;

public class GraphvizLayout extends AbstractLayout implements Layout {

    // http://www.graphviz.org/doc/info/attrs.html
    private String algoName = "dot";
    private String dotBinary = "dot";
    private String rankDir = "LR";
    private String overlap = "false";
    private Boolean concentrate = false;

    private Graph graph;

    public GraphvizLayout(LayoutBuilder layoutBuilder) {
        super(layoutBuilder);
    }

    @Override
    public void initAlgo() {
        this.graph = graphModel.getGraphVisible();
        setConverged(false);
    }

    @Override
    public void goAlgo() {
        // Prepare input
        final StringBuffer dotfile = new StringBuffer();

        dotfile.append("digraph g {\n");
        dotfile.append("layout = \"").append(this.algoName).append("\";\n");
        dotfile.append("rankdir = \"").append(this.rankDir).append("\";\n");
        dotfile.append("overlap = \"").append(this.overlap).append("\";\n");
        if (this.concentrate) {
            dotfile.append("concentrate=true;\n");
        }

        for (final Node n : this.graph.getNodes()) {
            final NodeData nodeData = n.getNodeData();

            dotfile.append(n.getId());
            dotfile.append(" [");
            dotfile.append("pos=\"").append(nodeData.x()).append(',').append(nodeData.y()).append('"');
            dotfile.append(", ");
            dotfile.append("label=\"").append(nodeData.getLabel()).append('"');
            dotfile.append("];\n");
        }
        for (final Edge e : this.graph.getEdges()) {
            final String edgearrow = e.isDirected() ? "->" : "--";

            dotfile.append(e.getSource().getId());
            dotfile.append(edgearrow);
            dotfile.append(e.getTarget().getId());
            dotfile.append(" [weight=");
            dotfile.append(e.getWeight());
            dotfile.append("];\n");
        }
        dotfile.append("}\n");

        // Call Graphviz
        // we are calling it directly. However, there is also a java binding
        // http://www.graphviz.org/pdf/gv.3java.pdf
        final List<String> cmd = new ArrayList<String>();
        cmd.add(this.dotBinary);
        cmd.add("-Tdot");
        final ProcessBuilder pb = new ProcessBuilder(cmd);
        Process dotprocess = null;

        try {
            dotprocess = pb.start();

            final OutputStream out = dotprocess.getOutputStream();
            {
                final BufferedWriter inputForGraphviz = new BufferedWriter(new PrintWriter(out));
                inputForGraphviz.append(dotfile);
                inputForGraphviz.flush();
                inputForGraphviz.close();                
                out.flush();
                out.close();
            }

            processOutput(dotprocess);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, new DotProcessError(ex), "Graphviz process error", JOptionPane.ERROR_MESSAGE);
        } finally {
            if (dotprocess != null) {
                dotprocess.destroy();
            }
            setConverged(true);
        }
    }

    @Override
    public void endAlgo() {
    }

    @Override
    public LayoutProperty[] getProperties() {
        List<LayoutProperty> properties = new ArrayList<LayoutProperty>();
        try {
            properties.add(LayoutProperty.createProperty(
                    this, String.class,
                    NbBundle.getMessage(getClass(), "GraphvizLayout.algorithm.desc"),
                    null,
                    "GraphvizLayout.algorithm.name",
                    NbBundle.getMessage(getClass(), "GraphvizLayout.algorithm.name"),
                    "getAlgoName", "setAlgoName"));

            properties.add(LayoutProperty.createProperty(
                    this, String.class,
                    NbBundle.getMessage(getClass(), "GraphvizLayout.dotbinary.desc"),
                    null,
                    "GraphvizLayout.dotbinary.name",
                    NbBundle.getMessage(getClass(), "GraphvizLayout.dotbinary.name"),
                    "getDotBinary", "setDotBinary"));

            properties.add(LayoutProperty.createProperty(
                    this, String.class,
                    NbBundle.getMessage(getClass(), "GraphvizLayout.rankdir.desc"),
                    null,
                    "GraphvizLayout.rankdir.name",
                    NbBundle.getMessage(getClass(), "GraphvizLayout.rankdir.name"),
                    "getRankDir", "setRankDir"));

            properties.add(LayoutProperty.createProperty(
                    this, String.class,
                    NbBundle.getMessage(getClass(), "GraphvizLayout.overlap.desc"),
                    null,
                    "GraphvizLayout.overlap.name",
                    NbBundle.getMessage(getClass(), "GraphvizLayout.overlap.name"),
                    "getOverlap", "setOverlap"));

            properties.add(LayoutProperty.createProperty(
                    this, Boolean.class,
                    NbBundle.getMessage(getClass(), "GraphvizLayout.concentrate.desc"),
                    null,
                    "GraphvizLayout.concentrate.name",
                    NbBundle.getMessage(getClass(), "GraphvizLayout.concentrate.name"),
                    "isConcentrate", "setConcentrate"));
        } catch (MissingResourceException e) {
            Exceptions.printStackTrace(e);
        } catch (NoSuchMethodException e) {
            Exceptions.printStackTrace(e);
        }
        return properties.toArray(new LayoutProperty[0]);
    }

    @Override
    public void resetPropertiesValues() {
    }

    public String getAlgoName() {
        return algoName;
    }

    public void setAlgoName(String algoName) {
        this.algoName = algoName;
    }

    public String getDotBinary() {
        return dotBinary;
    }

    public void setDotBinary(String dotBinary) {
        this.dotBinary = dotBinary;
    }

    public String getRankDir() {
        return rankDir;
    }

    public void setRankDir(String rankDir) {
        this.rankDir = rankDir;
    }

    public boolean isConcentrate() {
        return concentrate;
    }

    public void setConcentrate(Boolean concentrate) {
        this.concentrate = concentrate;
    }

    public String getOverlap() {
        return overlap;
    }

    public void setOverlap(String overlap) {
        this.overlap = overlap;
    }

    private void processOutput(final Process dotprocess) {
        assert dotprocess != null;
        InputStream in = null;
        try {
            in = dotprocess.getInputStream();

            final BufferedReader outputFromGraphviz = new BufferedReader(new InputStreamReader(in));
            StringBuilder entireOutput = new StringBuilder();
            String line;
            while ((line = outputFromGraphviz.readLine()) != null) {
                entireOutput.append(line);
                entireOutput.append("\n");
//                System.out.println("line");
            }
            
            // For some reason this one wasn't working
            // Node n = graph.getNode(nodeid);
            // ... so we map all nodes temporarily
            final Map<Integer, Node> nodeMapper = new HashMap<Integer, Node>();
            for (final Node currentNode : this.graph.getNodes()) {
                nodeMapper.put(currentNode.getId(), currentNode);
            }

            final String regex = "^\\s*(?<nodeid>\\d+)\\s+\\[.*?[, ]?pos=\"(?<pos>[^\"]+?)\".*?\\]";
            System.out.println(entireOutput);
            final Pattern pat = Pattern.compile(regex, Pattern.MULTILINE | Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            Matcher matcher = pat.matcher(entireOutput.toString());
            while(matcher.find()) {
                Integer nodeid = Integer.valueOf(matcher.group("nodeid"));
                final Node n = nodeMapper.get(nodeid);
                if(null == n) {
                    System.err.println("Cannot find nodeid " + nodeid);
                    continue;
                }
                String pos = matcher.group("pos");
                String[] pair = pos.trim().split("[, ]");
                if(pair.length != 2) {
                    System.err.println("Don't know what to do with coordinates != 2; " + pos);
                    continue;
                }
                BigDecimal x_bd = new BigDecimal(pair[0]);
                BigDecimal y_bd = new BigDecimal(pair[1]);
//                System.out.println("Node " + nodeid + " : " + pos + " = " + x_bd.floatValue() + "," + y_bd.floatValue());
                n.getNodeData().setX(x_bd.floatValue());
                n.getNodeData().setY(y_bd.floatValue());
            }
             
       } catch (IOException e) {
            Exceptions.printStackTrace(e);            
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
            }
        }

        InputStream err = null;
        try {
            // Dump any errors
            {
                err = dotprocess.getErrorStream();
                final InputStreamReader glue = new InputStreamReader(err);
                final BufferedReader errorsFromGraphviz = new BufferedReader(glue);
                String line;
                while ((line = errorsFromGraphviz.readLine()) != null) {
                    {
                        System.err.println(line);
                    }
                }
            }
        } catch (IOException e) {
            Exceptions.printStackTrace(e);            
        } finally {
            try {
                if (err != null) {
                    err.close();
                }
            } catch (IOException e) {
            }
        }
    }
}
