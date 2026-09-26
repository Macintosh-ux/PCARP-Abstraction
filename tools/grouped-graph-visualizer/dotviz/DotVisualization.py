from dotviz.ClusteredDotGraph import ClusteredDotGraph

class DotVisualization:
    def __init__(self, input, output, layout):
        if layout != "dot" and layout != "fdp":
            raise Exception("Invalid layout engine. Should be 'dot' or 'fdp'")
        
        clustered = ClusteredDotGraph(input, output)
        self._graph = clustered.graph
        self._output = output
        self._outname = output.split(".")[0]
        self._layout = layout
        
    def export(self, type, no_fix=False):
        if type == "pdf":
            self._graph.write_pdf(f"{self._outname}.pdf", prog=self._layout)
        elif type == "png":
            self._graph.write_png(f"{self._outname}.png", prog=self._layout)
        else:
            self._graph.write_svg(f"{self._outname}.svg", prog=self._layout)