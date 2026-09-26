"""
Groups components into clusters in a DOT file produced by the MVIS tool.
This parses the DOT graph and organizes related components into logical clusters.
"""

import pydot
from dotviz.indent_dot import indent_dot

class ClusteredDotGraph:
    def __init__(self, input_file, output_file, color="#ffd000", colordiff=-.045):
        self._graph = pydot.graph_from_dot_file(input_file)[0]
        self._nodes = self._graph.get_nodes()
        self._clusters = {}
        self._outgraph = pydot.Dot("GroupedGraph", graph_type="digraph")
        self._outgraph.set('clusterrank', 'local')
        self._outgraph.set_overlap("prism")
        self._outgraph.set_splines("true")
        self._outgraph.set_sep("+15")
        self._outgraph.set_nodesep("0.5")
        self._outgraph.set_ranksep("1.2 equally")
        self._outgraph.set_concentrate("true")
        self._colordiff = colordiff
        self._color = color
        self.group_clusters()
        self.add_edges()
        
        self._output_file = output_file
        
    @property
    def graph(self):
        return self._outgraph

    def group_clusters(self):
        for node in self._nodes:   
            l = (node.get_label() or "").replace("<<assembly component>>\n", "").strip('"').strip()
            node.set_label(l)
            name = node.get_name().strip('"')
            parts = name.split(".")
                   
            for i in range(len(parts) - 1):
                curpart = ".".join(parts[:i+1])
                if curpart not in self._clusters:
                    level = i + 1
                    color = self.adjust_hex_color(self._color, self._colordiff * level)
                    cluster = pydot.Cluster(
                        curpart,
                        label=curpart,
                        style="filled",
                        color="black",
                        fillcolor=color,
                        fontsize="20",
                        rank="same"
                    )
                    cluster.set_graph_defaults(margin="20,20")
                    self._clusters[curpart] = cluster
                    parent_name = ".".join(parts[:i])                   
                    parent = self._clusters.get(parent_name, self._outgraph)
                    parent.add_subgraph(cluster)              
            node_key = ".".join(parts[:-1])
            parent = self._clusters.get(node_key, self._outgraph)
            if name != "":            
                parent.add_node(node)

    def add_edges(self):
        for edge in self._graph.get_edges():  
            edge.set_color("#808080")         
            self._outgraph.add_edge(edge)

    def export(self):
        self._outgraph.write(self._output_file)
        indent_dot(self._output_file)

    def adjust_hex_color(self, hex_color, alpha):
        if not hex_color.startswith('#') or len(hex_color) != 7:
            raise ValueError("Color must be in format '#RRGGBB'")
        
        r = int(hex_color[1:3], 16)
        g = int(hex_color[3:5], 16)
        b = int(hex_color[5:7], 16)

        r_new = int(alpha * r + (1 - alpha) * 255)
        g_new = int(alpha * g + (1 - alpha) * 255)
        b_new = int(alpha * b + (1 - alpha) * 255)

        return f'#{r_new:02X}{g_new:02X}{b_new:02X}'
        




