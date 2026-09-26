import os, argparse
        
class CommandArgs():
    def __init__(self):
        self.USAGE = "usage: python3 path/to/pyparse.py <inputdir> <outputdir> <mode>"
        self.DESCRIPTION = "A static Python code parser compatible with the Kieker SAR (Static Architecture Recovery) tool."
        
        self.parser = argparse.ArgumentParser(usage=self.USAGE,
                                              description=self.DESCRIPTION)
        
        self.parser.add_argument("-i", "--input-dir", type=_is_directory, help="Input directory of the target Python application")
        self.parser.add_argument("-o", "--output-dir", type=_is_directory, help="Input directory of the target Python application")
        self.parser.add_argument("-m", "--mode", type=_correct_mode, help="Operation flow analysis (call), data flow analysis (dataflow) or both (both)")
        self.parser.add_argument("-e", "--external", action="store_true", help="Resolve calls from external modules")
        self.parser.add_argument("-v", "--verbose", action="store_true", help="Verbose output")
        
        self.args = self.parser.parse_args()

    @property
    def input_dir(self):
        return self.args.input_dir

    @property
    def output_dir(self):
        return self.args.output_dir
    
    @property
    def mode(self):
        return self.args.mode
    
    @property
    def external(self):
        return self.args.external

    @property
    def verbose(self):
        return self.args.verbose

def _correct_mode(value):
    if value != "call" and value != "dataflow" and value != "both":
        raise argparse.ArgumentTypeError("<mode> should be 'dataflow', 'call' or 'both'")
    return value
    
def _is_directory(value):
    if not os.path.isdir(value):
        argparse.ArgumentTypeError("<inputdir> is not a directory")
    if not value.endswith("/"):
        value += "/"
    return os.path.dirname(value)   