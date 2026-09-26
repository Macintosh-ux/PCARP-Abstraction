from tulip import tlp
      
############### Hierarchy ###############
    
def print_subgraph_hierarchy(graph, indent=0, is_root=True, nodes=False):
    name = graph.getName() or "(unnamed)"
    graph_id = graph.getId()
    num_nodes = graph.numberOfNodes()
    num_edges = graph.numberOfEdges()
    indent_str = "  " * indent

    label = f"{name} [ROOT]" if is_root else name
    print(f"{indent_str}- {label:<40} (ID: {graph_id:>3})  Nodes: {num_nodes:>4}  Edges: {num_edges:>4}")

    if nodes:
        names = graph.getProperty("viewLabel")
        indent_str = "  " * (indent+1)
        for i in graph.getNodes():
            print(f"{indent_str}- {names[i].upper():<40}")

    subgraphs = graph.getSubGraphs()
    while subgraphs.hasNext():
        subgraph = subgraphs.next()
        print_subgraph_hierarchy(subgraph, indent + 1, is_root=False, nodes=nodes)      
                   
############### Properties ###############
    
def print_node_properties(graph, node_index=0):
    properties = graph.getNodePropertiesValues(graph.nodes()[node_index])
    max_key_length = max(len(key) for key in properties.getKeys())
    for key in properties.getKeys():
        print(f"{key:<{max_key_length}} : {properties[key]}")
            
def print_edge_properties(graph, edge_index=0):
    properties = graph.getEdgePropertiesValues(graph.edges()[edge_index])
    max_key_length = max(len(key) for key in properties.getKeys())
    for key in properties.getKeys():
        print(f"{key:<{max_key_length}} : {properties[key]}")
       
def print_graph_properties(graph):
    property_names = list(graph.getLocalProperties())
    max_key_length = max(len(key) for key in property_names) if property_names else 0
    for key in property_names:
        prop = graph.getProperty(key)
        print(f"{key:<{max_key_length}} : {prop}")
        
def print_graph_inherited_properties(graph):
    property_names = list(graph.getInheritedProperties())
    max_key_length = max(len(key) for key in property_names) if property_names else 0
    for key in property_names:
        prop = graph.getProperty(key)
        print(f"{key:<{max_key_length}} : {prop}")
         
def print_graph_property(graph, property):
    property_names = list(graph.getLocalProperties())
    if property not in property_names:
        print(f"No property named {property}")
        return         
    values = graph.getProperty(property)
    for node in graph.getNodes():
        print(values.getNodeValue(node))
        
def print_graph_inherited_property(graph, property):
    property_names = list(graph.getInheritedProperties())
    if property not in property_names:
        print(f"No property named {property}")
        return         
    values = graph.getProperty(property)
    for node in graph.getNodes():
        print(values.getNodeValue(node))        
        
############### Acces ###############
        
def get_subgraph(graph, target_id):
    '''get subgraph but recursive'''
    if graph.getId() == target_id:
        return graph

    subgraphs = graph.getSubGraphs()
    while subgraphs.hasNext():
        subgraph = subgraphs.next()
        result = get_subgraph(subgraph, target_id)
        if result is not None:
            return result

    return None

def get_subgraph_name(graph, target_name):
    '''get subgraph by name but recursive'''
    if graph.getName() == target_name:
        return graph

    subgraphs = graph.getSubGraphs()
    while subgraphs.hasNext():
        subgraph = subgraphs.next()
        result = get_subgraph_name(subgraph, target_name)
        if result is not None:
            return result

    return None

#######################
    
def split_graph(graph, bool_prop_name):
    bool_prop = graph.getBooleanProperty(bool_prop_name)

    true_nodes = [n for n in graph.getNodes() if bool_prop[n]]
    false_nodes = [n for n in graph.getNodes() if not bool_prop[n]]

    true_graph = graph.inducedSubGraph(true_nodes)
    false_graph = graph.inducedSubGraph(false_nodes)

    return true_graph, false_graph
