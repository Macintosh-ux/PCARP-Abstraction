from CommandArgs import CommandArgs
from tulipviz.TulipVisualization import TulipVisualization
from dotviz.DotVisualization import DotVisualization

if __name__ == "__main__":
    args = CommandArgs()
    match args.mode:
        case "dot":
            graph = DotVisualization(args.input, args.output, "dot")
        case "fdp":
            graph = DotVisualization(args.input, args.output, "fdp")
        case "tulip":
            graph = TulipVisualization(args.input, args.output, skip_edge_bundling=args.skip_edge_bundling)
        case _:
            raise Exception("This point shouldn't be reachable")
        
    graph.export(args.output_type, args.no_fix)