import multiprocessing

from tulip import tlp
from tulipviz.TulipVisualization import TulipVisualization


def _setup():
    tlp.initTulipLib()
    tlp.loadPlugins()


def _prepare_visualization(graph):
    """
    Creates a TulipVisualization instance without running its normal __init__,
    then injects only the state required by _edge_bundling().
    """
    visualization = TulipVisualization.__new__(TulipVisualization)

    visualization._graph = graph
    visualization._view = graph.getLayoutProperty("viewLayout")

    return visualization


def _run_with_real_edge_bundling(graph):
    visualization = _prepare_visualization(graph)

    # This is the actual production method from TulipVisualization.
    visualization._edge_bundling(graph)


def _normal_edge():
    _setup()

    graph = tlp.newGraph()
    a = graph.addNode()
    b = graph.addNode()

    graph.addEdge(a, b)

    layout = graph.getLayoutProperty("viewLayout")
    size = graph.getSizeProperty("viewSize")

    layout[a] = tlp.Coord(0, 0, 0)
    layout[b] = tlp.Coord(100, 0, 0)

    size[a] = tlp.Size(10, 10, 1)
    size[b] = tlp.Size(10, 10, 1)

    _run_with_real_edge_bundling(graph)


def _self_loop_only():
    _setup()

    graph = tlp.newGraph()
    a = graph.addNode()

    graph.addEdge(a, a)

    layout = graph.getLayoutProperty("viewLayout")
    size = graph.getSizeProperty("viewSize")

    layout[a] = tlp.Coord(90, 75, 0)
    size[a] = tlp.Size(10, 10, 1)

    _run_with_real_edge_bundling(graph)


def _normal_and_self_loop():
    _setup()

    graph = tlp.newGraph()
    a = graph.addNode()
    b = graph.addNode()

    graph.addEdge(a, a)
    graph.addEdge(a, b)

    layout = graph.getLayoutProperty("viewLayout")
    size = graph.getSizeProperty("viewSize")

    layout[a] = tlp.Coord(0, 0, 0)
    layout[b] = tlp.Coord(100, 0, 0)

    size[a] = tlp.Size(10, 10, 1)
    size[b] = tlp.Size(10, 10, 1)

    _run_with_real_edge_bundling(graph)


def _two_self_loops():
    _setup()

    graph = tlp.newGraph()
    a = graph.addNode()
    b = graph.addNode()

    graph.addEdge(a, a)
    graph.addEdge(b, b)

    layout = graph.getLayoutProperty("viewLayout")
    size = graph.getSizeProperty("viewSize")

    layout[a] = tlp.Coord(0, 0, 0)
    layout[b] = tlp.Coord(100, 0, 0)

    size[a] = tlp.Size(10, 10, 1)
    size[b] = tlp.Size(10, 10, 1)

    _run_with_real_edge_bundling(graph)


def run_case(name, target):
    process = multiprocessing.Process(target=target)
    process.start()
    process.join()

    if process.exitcode == 0:
        print(f"[OK]    {name}")
    elif process.exitcode < 0:
        print(f"[CRASH] {name} (signal {-process.exitcode})")
    else:
        print(f"[FAIL]  {name} (exit code {process.exitcode})")


if __name__ == "__main__":
    run_case("normal edge only", _normal_edge)
    run_case("self-loop only", _self_loop_only)
    run_case("normal + self-loop", _normal_and_self_loop)
    run_case("two self-loops only", _two_self_loops)