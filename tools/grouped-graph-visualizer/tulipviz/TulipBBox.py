from tulip import tlp

class TulipBBox:
    def __init__(self):
        self._boxes = {}
           
    def set_up_bbox(self, graph):
        self._bottom_up(graph)
        
    def _bottom_up(self, graph):
        subgraphs = graph.getSubGraphs()
        subgraph_list = []
        while subgraphs.hasNext():
            subgraph_list.append(subgraphs.next())

        for subgraph in subgraph_list:
            self._bottom_up(subgraph)
                       
        if graph.getSuperGraph() != graph:           
            box_node = self.create_bbox(graph)
            self._boxes[graph] = box_node
            bbox = graph.getProperty("isBoundingBox") 
            bbox[box_node] = True
                   
    def has_bbox(self, graph):
        return graph in self._boxes.keys()
    
    def get_bbox(self, graph):
        return self._boxes[graph]
                      
    def create_bbox(self, subgraph):
        if subgraph is None:
            raise ValueError("Subgraph is None — cannot create bounding box.")
        parent_graph = subgraph.getSuperGraph()
        label_name = parent_graph.getProperty("bboxLabel")
        layout = parent_graph.getLayoutProperty('viewLayout')
        size = parent_graph.getSizeProperty('viewSize')
        color = parent_graph.getColorProperty('viewColor')
        shape = parent_graph.getIntegerProperty('viewShape')
        border_width = parent_graph.getProperty("viewBorderWidth")

        coords = self.compute_bbox(subgraph)

        box_node = parent_graph.addNode()
        
        layout[box_node] = tlp.Coord(coords["center_x"], coords["center_y"], 0)
        size[box_node] = tlp.Size(coords["width"], coords["height"], 1)
        border_width[box_node] = 5
        color[box_node] = tlp.Color(148, 65, 3, 20)
        shape[box_node] = tlp.NodeShape.Square
        label_name[box_node] = subgraph.getName()
        
        self._boxes[subgraph] = box_node
        bbox = subgraph.getProperty("isBoundingBox") 
        bbox[box_node] = True 

    def size_bbox(self, subgraph):
        if not self.has_bbox(subgraph):
            return
        box_node = self.get_bbox(subgraph)
        layout = subgraph.getLayoutProperty('viewLayout')
        size = subgraph.getSizeProperty('viewSize')
        coords = self.compute_bbox(subgraph)
        layout[box_node] = tlp.Coord(coords["center_x"], coords["center_y"], 0)
        size[box_node] = tlp.Size(coords["width"], coords["height"], 1)        
        
    def compute_bbox(self, subgraph):
        if subgraph is None:
            raise ValueError("Subgraph is None — cannot compute bounding box.")

        layout = subgraph.getLayoutProperty('viewLayout')
        size = subgraph.getSizeProperty('viewSize')   

        # Compute bounding box of all nodes in the subgraph
        min_x = min_y = float('inf')
        max_x = max_y = float('-inf')
        for n in subgraph.getNodes():
            pos = layout[n]
            sz = size[n]
            x0 = pos[0] - sz[0] / 2
            x1 = pos[0] + sz[0] / 2
            y0 = pos[1] - sz[1] / 2
            y1 = pos[1] + sz[1] / 2
            min_x = min(min_x, x0)
            max_x = max(max_x, x1)
            min_y = min(min_y, y0)
            max_y = max(max_y, y1)

        # Add optional padding
        padding = 10.0
        min_x -= padding
        max_x += padding
        min_y -= padding
        max_y += padding * 4

        # Compute bounding box center and dimensions
        center_x = (min_x + max_x) / 2
        center_y = (min_y + max_y) / 2
        width = max_x - min_x
        height = max_y - min_y
        
        # Return result
        res = {"center_x": center_x,
            "center_y": center_y,
            "width": width,
            "height": height}
        return res
    
