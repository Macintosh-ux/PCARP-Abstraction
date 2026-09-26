from tulip import tlp
from tulipviz.TulipClusterGrouper import TulipClusterGrouper
from tulipviz.TulipImporter import TulipImporter
from tulipviz.TulipExporter import TulipExporter
from tulipviz.TulipStyler import TulipStyler
from tulipviz.TulipBBox import TulipBBox
from tulipviz.utils import *

class TulipVisualization:
    def __init__(self, input, output, skip_edge_bundling=False):
        self._importer = TulipImporter(input)
        self._exporter = TulipExporter(output)
        self._styler = TulipStyler()
        self._grouper = TulipClusterGrouper()
        self._boxer = TulipBBox()
        self._graph = self._importer.graph
        self._skip_edge_bundling = skip_edge_bundling
            
        self._alt = self._graph.getLayoutProperty("altLayout")
        self._bbox = self._graph.getBooleanProperty("isBoundingBox")
        self._view = self._graph.getLayoutProperty("viewLayout")
        self._hoy = self._graph.getStringProperty("bboxLabel")
        
        tlp.initTulipLib()
        tlp.loadPlugins()  
               
        # group graph
        self._styler.style_graph(self._graph)
        self._grouper.group_graph(self._graph)
                   
        # layout
        self._bottom_up(self._graph)
        self._curve_edges(self._graph)

        if self._skip_edge_bundling:
            print("Skipping edge bundling by command-line option.")
        else:
            self._edge_bundling(self._graph)

        self._styler._style_bbox_labels(self._graph)
        
    def export(self, type="svg", no_fix=False):
        self._exporter._export_graph(self._graph, type, no_fix)
                                  
    def _bottom_up(self, graph):
        subgraphs = graph.getSubGraphs()
        subgraph_list = []
        while subgraphs.hasNext():
            subgraph_list.append(subgraphs.next())

        for subgraph in subgraph_list:
            self._bottom_up(subgraph)

        # lay out the graph
        self._fm3(graph, self._alt)
        self._fast_overlap_removal(graph, self._alt)
        
        # update the direct node children
        direct_children = self._grouper.direct_nodes(graph)
        for n in direct_children:
            self._view[n] = self._alt[n]
            
        # translate all subgraphs to their bbox
        for s in subgraph_list:
            if self._boxer.has_bbox(s): 
                box = self._boxer.get_bbox(s)
                diff = self._alt[box] - self._view[box]
                self._view[box] = self._alt[box]
                for n in s.getNodes():
                    self._view[n] += diff
                    
        # create bounding box   
        if graph.getSuperGraph() != graph:           
            self._boxer.create_bbox(graph)                   
                            
    def _fm3(self, graph, property):
        params = tlp.getDefaultPluginParameters('FM^3 (OGDF)', graph)
        params['new initial layout'] = False
        params['edge length measurement'] = "midpoint"
        params['allowed positions'] = "all"
        graph.applyLayoutAlgorithm('FM^3 (OGDF)', property, params)
               
    def _fast_overlap_removal(self, graph, property):
        algorithm = "Fast Overlap Removal"
        params = tlp.getDefaultPluginParameters(algorithm, graph)
        params["initial layout"] = property
        params["x border"] = 10
        params["y border"] = 10
        graph.applyLayoutAlgorithm(algorithm, property, params)

    def _edge_bundling(self, graph):
        algorithm = "Edge bundling"
        params = tlp.getDefaultPluginParameters(algorithm, graph)
        graph.applyAlgorithm(algorithm, params)
    
        
    def _curve_edges(self, graph):
        algorithm = "Curve edges"
        params = tlp.getDefaultPluginParameters(algorithm, graph)
        graph.applyAlgorithm(algorithm, params)    