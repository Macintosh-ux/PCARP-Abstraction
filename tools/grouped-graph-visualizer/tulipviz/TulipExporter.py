from tulip import tlp
from tulipviz.layer_svg import push_white_groups_to_end
import cairosvg, os

class TulipExporter:
    def __init__(self, output):             
        tlp.initTulipLib()
        tlp.loadPlugins()  
              
        self._output = output
        self._outname = output.split(".")[0]
    
    def _export_graph(self, graph, type="svg", no_fix=False):
        if type == "svg":         
            self.export_svg(graph, self._output)
            if not no_fix:
                push_white_groups_to_end(self._output, self._output)
        else:
            self.export_svg(graph, "temp.svg")
            if not no_fix:
                push_white_groups_to_end("temp.svg", "temp.svg")           
            if type == "pdf":
                cairosvg.svg2pdf(url="temp.svg", write_to=f"{self._outname}.pdf")
            elif type == "png":
                cairosvg.svg2png(url="temp.svg", write_to=f"{self._outname}.png")
            os.remove("temp.svg")
        
    def export_svg(self, graph, name):
        params = tlp.getDefaultPluginParameters("SVG Export", graph)
        params['edge color interpolation'] = False
        params['edge size interpolation'] = False
        params['edge extremities'] = True
        params['no background'] = False
        params['makes SVG output human readable'] = False
        params['export edge labels'] = True
        params['export metanode labels'] = True
        tlp.exportGraph("SVG Export", graph, name, params)