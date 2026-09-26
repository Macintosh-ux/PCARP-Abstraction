import ast
from call.OperationCall import OperationCall
from State import State
from generic.Operation import Operation

class FuncInfo:
    def __init__(self, file, module, node):
        self._file = file
        self._module = module
        self._node = node
        self._params = self.build_params()
        
    def build_params(self):
        res = []
        for el in self._node.args.args:
            res.append(el.arg)
        if self._node.args.vararg:
            res.append(self._node.args.vararg.arg)
        return res
                 
    def __repr__(self):
        res = self._module + ", " + self.name + ":\n"
        res += "params:"
        for el in self._params:
            res += "\t" + str(el) + "\n"
        return res
       
    @property
    def name(self):
        return self._node.name
        
    def as_operation(self):
        return Operation(self._file, self._module, self.name, State.KNOWN)
    
    def is_method(self):
        return self._node.args.args and self._node.args.args[0].arg == "self"

    def is_static_method(self):   
        return any(
            isinstance(decorator, ast.Name) and decorator.id == "staticmethod"
            for decorator in self._node.decorator_list
        )
        
    def __eq__(self, other):
        if isinstance(other, FuncInfo):
            return self.name == other.name and self._file == other._file and self._module == other._module           
        if isinstance(other, OperationCall):
            if self.name in other.callee.name:
                other.update_callee_origin(self._file, self._module, State.FOUND)
                return True
            elif other.callee.name in self._params:   
                other.update_callee_origin(self._file, self._module, State.PARAM) 
                return True          
        return False