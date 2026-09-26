import tulip as tlp

class TulipPreprocessor:
    def __init__(self):             
        pass
        
    def preprocess_dot(self, graph):
        viewLabel = graph.getProperty("viewLabel")
        
        for node in graph.getNodes():
            viewLabel[node] = viewLabel[node].replace("<<assembly component>>\n", "").strip('"').strip()
                       
    def preprocess_graphml(self, graph):
        viewLabel = graph.getProperty("viewLabel")
        name = graph.getProperty("name")
        package_name = graph.getProperty("package name")
        
        for node in graph.getNodes():
            viewLabel[node] = f"{package_name[node]}.{name[node]}"