from termcolor import colored
from State import State
from call.OperationCall import OperationCall

class Operation:
    def __init__(self, path, module, name, state):
        self.path = path
        self.module = module
        self.name = name
        self.state = state
   
    def __repr__(self):
        color_map = {
            State.UNKNOWN: "red",
            State.IMPORTED: "yellow",
            State.FOUND: "green",
            State.CLASS: "cyan",
            State.ITERVAR: "dark_grey",
            State.PARAM: "magenta"
        }   
        color = color_map.get(self.state, "white")
        return colored(f"(STATE: {self.state}, {self.module}, {self.name})", color)

    def export(self):
        return self.module, self.name
       
    def __eq__(self, other):
            if isinstance(other, Operation):
                return self.name == other.name and self.path == other.path and self.module == other.module 
                      
            if isinstance(other, OperationCall):
                if self.name in other.callee.name:
                    if self.state == State.ITERVAR:
                        other.update_callee_origin(self.path, self.module, State.ITERVAR)
                    else:
                        other.update_callee_origin(self.path, self.module, State.FOUND)
                    return True
            return False
        
    def __hash__(self):
        return hash((self.path, self.module, self.name))
    
    def update_origin(self, file, module, state):
        self.path = file
        self.module = module
        self.state = state
        
    def is_unresolved(self):
        return not State.isknown(self.state)

