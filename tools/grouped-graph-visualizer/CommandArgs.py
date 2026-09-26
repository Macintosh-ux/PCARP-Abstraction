import argparse

USAGE = "usage: python3 path/to/main.py <inputdir> <outputdir> <mode> (--no-fix)"
DESCRIPTION = "A tool compatible with the MVIS tool. Generates graph visuals grouped by common prefixes.\
                About the rendering engines: dot can be used up to small graphs, fdp medium graphs and tulip up to large graphs. Tulip is definitely the fastest of the 3.\
                if you want to skip thing up with tulip, skip the esthetic layering fix. It can take the majority of the process time for larger graphs (80% in worst cases)"
EXPORT = ["svg", "pdf", "png"]
LAYOUT = ["tulip", "fdp", "dot"]

class CommandArgs:     
    def __init__(self):     
        parser = argparse.ArgumentParser(usage=USAGE, description=DESCRIPTION)    
        parser.add_argument("-i", "--input", required=True, type=str, help="Input file")
        parser.add_argument("-o", "--output", required=True, type=str, help="Output file")
        parser.add_argument("-t", "--output-type", default="svg", type=correct_mode_type, help=enumeration(EXPORT))
        parser.add_argument("-m", "--mode", default="tulip", type=correct_mode_layout, help=enumeration(LAYOUT))
        parser.add_argument("-nf", "--no-fix", action="store_true", help="When activated, skips the layering fix for tulip")   
        ##
        parser.add_argument("--skip-edge-bundling", action="store_true",help="Render without applying Tulip edge bundling.")     
        ##     
        self.args = parser.parse_args()
          
    @property
    def input(self):
        return self.args.input

    @property
    def output(self):
        return self.args.output
    
    @property
    def output_type(self):
        return self.args.output_type
    
    @property
    def mode(self):
        return self.args.mode
    
    @property
    def no_fix(self):
        return self.args.no_fix

    @property
    def skip_edge_bundling(self):
        return self.args.skip_edge_bundling
 
def enumeration(items):
    items = [str(item) for item in items]
    if not items:
        return ""
    elif len(items) == 1:
        return items[0]
    elif len(items) == 2:
        return " or ".join(items)
    else:
        return ", ".join(items[:-1]) + " or " + items[-1]
           
def correct_mode_layout(value):
    if value not in LAYOUT:
        raise argparse.ArgumentTypeError(f"<mode> should be {enumeration(LAYOUT)}")
    return value

def correct_mode_type(value):
    if value not in EXPORT:
        raise argparse.ArgumentTypeError(f"<output-type> should be {enumeration(EXPORT)}")
    return value





    