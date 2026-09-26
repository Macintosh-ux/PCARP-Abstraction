import os, argparse
        
class CommandArgs():
    def __init__(self):
        self.USAGE = "usage: python3 path/to/main.py <inputdir>"
        self.DESCRIPTION = "Adds @instrument to all function definitions and inserts `from otkt.tools.instrument import instrument` \
                            Places the import after existing ones if present. Preserves shebang lines.\
                            Note: Discards all comments during AST processing."       
        self.parser = argparse.ArgumentParser(usage=self.USAGE,
                                              description=self.DESCRIPTION)     
        self.parser.add_argument("-i", "--input-dir", required=True, type=_is_directory, help="Input directory of the target Python application")
        self.parser.add_argument("-v", "--verbose", action="store_true", help="Verbose output") 
        self.parser.add_argument("-l", "--log", action="store_true", help="log output")
        self.args = self.parser.parse_args()

    @property
    def input_dir(self):
        return self.args.input_dir
    
    @property
    def verbose(self):
        return self.args.verbose
  
    @property
    def log(self):
        return self.args.log

def _is_directory(value):
    if not os.path.isdir(value):
        argparse.ArgumentTypeError("<inputdir> is not a directory")
    if not value.endswith("/"):
        value += "/"
    return os.path.dirname(value)   