from tulip import tlp
from tulipviz.TulipPreprocessor import TulipPreprocessor

class TulipImporter:
    def __init__(self, input):             
        tlp.initTulipLib()
        tlp.loadPlugins()  
              
        self._input = input
        self._input_type = self._input.split(".")[-1]
        self.preprocessor = TulipPreprocessor()
               
        if self._input_type == "dot":
            self._graph = self._import_dot_graph()
            self.preprocessor.preprocess_dot(self._graph)
        elif self._input_type == "graphml":
            self._graph = self._import_graphml_graph()
            self.preprocessor.preprocess_graphml(self._graph)
        else:
            raise Exception("Wrong file type")
        
    def _import_dot_graph(self):
        params = tlp.getDefaultPluginParameters('graphviz')
        params['filename'] = self._input
        return tlp.importGraph('graphviz', params)
    
    def _import_graphml_graph(self):
        params = tlp.getDefaultPluginParameters('GraphML')
        params['filename'] = self._input
        return tlp.importGraph('GraphML', params)
    
    @property
    def graph(self):
        return self._graph
    
    @property
    def is_dot(self):
        return self._input_type == "dot"
    
    @property
    def is_graphml(self):
        return self._input_type == "graphml"