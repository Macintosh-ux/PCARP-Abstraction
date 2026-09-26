'''
Groups a tulip graph into nested subgraphs based on common dot separated prefixes
'''

from tulip import tlp
from collections import defaultdict
from tulipviz.utils import *

class TulipClusterGrouper:
    def __init__(self):
        self._out = tlp.newGraph()
        self._clusters = {}
        self._nodes = defaultdict(list)
                                    
    def group_graph(self, graph, stringProp="viewLabel"):
        self.group_clusters(graph, stringProp)
        self.relocate_edges(graph) 
                             
    def group_clusters(self, graph, stringProp="viewLabel"):
        label_prop = graph.getStringProperty(stringProp)        
        for node in graph.getNodes():
            parts = label_prop[node].split(".")
            parent = graph
            for i in range(len(parts) - 1):
                curpart = ".".join(parts[:i+1])
                if curpart not in self._clusters:
                    cluster = parent.addSubGraph(curpart)
                    self._clusters[curpart] = cluster
                parent = self._clusters[curpart]         
            node_key = ".".join(parts[:-1])
            target_cluster = self._clusters.get(node_key, graph) 
            if target_cluster.getSuperGraph() != target_cluster:
                target_cluster.addNode(node)
            self._nodes[target_cluster].append(node)
                 
    def relocate_edges(self, graph):    
        for edge in graph.getEdges():
            ancestor_name = self.get_common_dotpath_prefix(graph, edge)
            if ancestor_name != "":   
                ancestor = graph.getDescendantGraph(ancestor_name)
                ancestor.addEdge(edge)
           
    def longest_common_dotpath_prefix(self, label1, label2):
        """Returns the longest common dot-separated prefix between two labels."""
        parts1 = label1.split(".")
        parts2 = label2.split(".")
        common_parts = []
        for p1, p2 in zip(parts1, parts2):
            if p1 == p2:
                common_parts.append(p1)
            else:
                break
        return ".".join(common_parts)
    
    def get_common_dotpath_prefix(self, graph, edge):
        """Returns the longest common dot-separated prefix between source and target node labels."""
        label_prop = graph.getStringProperty("viewLabel")
        src = graph.source(edge)
        tgt = graph.target(edge)
        label1 = ".".join(label_prop[src].split(".")[:-1])
        label2 = ".".join(label_prop[tgt].split(".")[:-1])
        prefix = self.longest_common_dotpath_prefix(label1, label2)
        return prefix
  
    def _pretty_print_dict(self, d):
        if not d:
            print("(empty dictionary)")
            return

        max_key_length = max(len(str(k)) for k in d)
        for key, value in d.items():
            print(f"{str(key):<{max_key_length}} : {value}")

    def print_clusters(self):
        self._pretty_print_dict(self._clusters)
        
    def print_nodes(self):
        self._pretty_print_dict(self._nodes)
        
    def direct_nodes(self, graph):
        return self._nodes[graph]
            
            
                