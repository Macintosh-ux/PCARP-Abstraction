from call.OperationCall import OperationCall
from State import State
from generic.Operation import Operation

class ClassInfo:
    def __init__(self, file, module, name, bases):
        self._file = file
        self._module = module
        self._name = name
        self._bases = []
        self._methods = []
        self._attrs = []
        
    def add_method(self, method):
        method.module = self._module
        self._methods.append(method)
    
    def add_bases(self, bases):
        self._bases = bases
        
    def add_attr(self, attr):
        attr.module = self._module
        self._attrs.append(attr)
        
    def __repr__(self):
        res = self._module + ", " + self._name + ":\n"
        res += "methods:\n"
        for m in self._methods:
            res += "\t" + str(m) + "\n"
        res += "attrs:\n"
        for a in self._attrs:
            res += "\t" + str(a) + "\n"
        return res
    
    @property
    def name(self):
        return self._name
    
    @property
    def bases(self):
        return self._bases
    
    @property
    def methods(self):
        return self._methods
    
    @property
    def vars(self):
        return self._attrs
    
    def __eq__(self, other):
            if isinstance(other, ClassInfo):
                return self.name == other.name  
             
            if isinstance(other, OperationCall):
                if other in self._methods:
                    return True
                if self._name in other.callee.name:
                    other.update_callee_origin(self._file, self._module, State.CLASS)
                    return True
            return False
        
    def as_operation(self):
        return Operation(self._file, self._module, self._name, State.KNOWN)