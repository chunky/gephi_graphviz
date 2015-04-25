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

import java.awt.Label;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JOptionPane;
import org.gephi.graph.api.Attributes;
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

    public void initAlgo() {
        graph = graphModel.getGraphVisible();
        setConverged(false);
    }

    public void goAlgo() {
        graph = graphModel.getGraphVisible();
        String dotfile = "digraph g {\n";
        dotfile = dotfile.concat("layout = \"" + algoName + "\";\n");
        dotfile = dotfile.concat("rankdir = \"" + rankDir + "\";\n");
        dotfile = dotfile.concat("overlap = \"" + overlap + "\";\n");
        if(concentrate) dotfile = dotfile.concat("concentrate=true;\n");
        
        for(Node n : graph.getNodes()) {
            NodeData nodeData = n.getNodeData();
            float x = nodeData.x();
            float y = nodeData.y();
            String labelStr = nodeData.getLabel();
            
            String id = new Integer(n.getId()).toString();
            String pos = "pos=\"" + x + "," + y + "\"";
            String label = "label=\"" + labelStr + "\"";
            dotfile = dotfile.concat(id + " [" + pos + ", " + label + "];\n");
        }
        for(Edge e : graph.getEdges()) {
            String idSource = new Integer(e.getSource().getId()).toString();
            String idTarget = new Integer(e.getTarget().getId()).toString();
            float weight = e.getWeight();
            String edgearrow = e.isDirected()?"->":"--";
            dotfile = dotfile.concat(idSource + edgearrow + idTarget + " [weight=" + weight + "];\n");
        }
        dotfile = dotfile.concat("}\n");
        
        List<String> cmd = new ArrayList<String>();
        cmd.add(dotBinary);
        cmd.add("-Tdot");
        
        ProcessBuilder pb = new ProcessBuilder(cmd);
        
        Process dotprocess = null;
        
        String dotoutput = "";
        try {
            dotprocess = pb.start();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, new DotProcessError(ex), "Graphviz process error", JOptionPane.ERROR_MESSAGE);
            setConverged(true);
            return;
        }

        try {
            OutputStream out = dotprocess.getOutputStream();
            InputStream in = dotprocess.getInputStream();
            InputStream err = dotprocess.getErrorStream();

            out.write(dotfile.getBytes());
            out.flush();
            out.close();
            out = null;
            
            int len = 0;
            byte[] buf = new byte[1024];
            while(0 < (len = in.read(buf))) {
                dotoutput = dotoutput.concat(new String(buf, 0, len));
            }
            
            int totalErrLen = 0;
            while(0 < (len = err.read(buf))) {
                totalErrLen += len;
                System.err.print(buf.toString());
            }
            if(totalErrLen > 0) System.err.println();
            
            in.close();
            in = null;
            err.close();
            err = null;
        } catch (IOException ex) {
            Exceptions.printStackTrace(ex);
            setConverged(true);
            return;
        }
        dotprocess.destroy();
        
//        System.out.println("\n\n" + dotoutput + "\n\n");
        
        // Was thinking of trying to re-use ImporterDOT but not sure
        //    It's certainly easier to pull the stuff using a trivial regex
        
        // "Some people, when faced with a problem, think 'I know, I'll regular expressions'
        //                ... now they have two problems"
        String[] lines = dotoutput.split("\n");
        String regex = ".*?([0-9]+)\\s+\\[.*?pos=\"(\\-?\\d+\\.?\\d*?),(\\-?\\d+\\.?\\d*?)\".*?\\].*";
        Pattern pat = Pattern.compile(regex, Pattern.DOTALL);
        System.out.println("Lines returned by graphviz: " + lines.length);
//        System.out.println("regex: \"" + regex + "\"");
        for(String line : lines) {
//            System.out.println("Line: \"" + line + "\"");
            Matcher match = pat.matcher(line);
            if(match.matches()) {
                String nodeid = match.group(1);
                String x = match.group(2);
                String y = match.group(3);
                
//                System.out.println("Node: " + nodeid + " x=" + x + " y=" + y);
                
                int nodeid_i = new Integer(nodeid);
                Float x_f = new Float(x);
                Float y_f = new Float(y);
                Node n = null;
                // For some reason this one wasn't working
                // Node n = graph.getNode(nodeid);
                for(Node testnode : graph.getNodes()) {
                    if(nodeid_i == testnode.getId()) {
                        n = testnode;
                        break;
                    }
                }
                if(null == n) {
                    System.out.println("Couldn't find nodeid " + nodeid);
                    continue;
                }
                n.getNodeData().setX(x_f);
                n.getNodeData().setY(y_f);
            }
        }
        
        setConverged(true);
    }

    public void endAlgo() {
    }

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
        } catch (Exception e) {
            e.printStackTrace();
        }
        return properties.toArray(new LayoutProperty[0]);
    }

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
    
}
