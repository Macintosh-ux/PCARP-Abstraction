from tulip import tlp

class TulipStyler:
    def __init__(self):
        self._fontsize = 8
                   
    def style_graph(self, graph):       
        self._style_nodes(graph)
        self._style_edges(graph)
        
    def _style_nodes(self, graph):
        viewSize = graph.getProperty("viewSize")
        viewLabel = graph.getProperty("viewLabel")
        viewBorderWidth = graph.getProperty("viewBorderWidth")
        viewShape = graph.getProperty("viewShape")
        viewColor = graph.getProperty("viewColor")
        viewLabelColor = graph.getColorProperty("viewLabelColor")
        
        for node in graph.getNodes():
            viewSize[node] = tlp.Size((len(viewLabel[node])) * self._fontsize, 5 * self._fontsize, 0)
            viewBorderWidth[node] = 12.0
            viewShape[node] = tlp.NodeShape.Square

            # Select Fontcolor based on background brightness

            background = viewColor[node]
            brightness = (
                  0.299 * background.getR()
                + 0.587 * background.getG()
                + 0.114 * background.getB()
            )

            viewLabelColor[node] = (
                tlp.Color(237, 230, 225, 255)
                if brightness < 140
                else tlp.Color(0, 0, 0, 255)
            )

            # Preserve imported node colors for advanced color coding.
            # if viewColor[node] != tlp.Color(255, 192, 255, 255):
            #     viewColor[node] = tlp.Color(255, 255, 255, 255)
       
    def _style_edges(self, graph):
        viewColor = graph.getProperty("viewColor")
        viewBorderWidth = graph.getProperty("viewBorderWidth")
            
        for edge in graph.getEdges():
            viewBorderWidth[edge] = 5.0
            viewColor[edge] = tlp.Color.Gray
            
    def _style_bbox_labels(self, graph):
        view = graph.getLayoutProperty("viewLayout")
        viewSize = graph.getProperty("viewSize")
        viewLabel = graph.getProperty("viewLabel")
        viewShape = graph.getProperty("viewShape")
        viewColor = graph.getProperty("viewColor")
        viewLabelColor = graph.getColorProperty("viewLabelColor")
        externLabel = graph.getProperty("bboxLabel")   
        bbox = graph.getProperty("isBoundingBox")      
                      
        for node in graph.getNodes():
            if bbox[node]:
                label_node = graph.addNode()

                viewSize[label_node] = tlp.Size((len(externLabel[node])) * self._fontsize * 2, 5 * self._fontsize * 2, 0)
                viewColor[label_node] = tlp.Color(0, 0, 0, 0)
                viewShape[label_node] = tlp.NodeShape.Square
                viewLabel[label_node] = externLabel[node]
                view[label_node] = view[node] + tlp.Coord(0, viewSize[node][1]/2 - 20, 0)
                viewLabelColor[label_node] = tlp.Color(0, 0, 0, 255)